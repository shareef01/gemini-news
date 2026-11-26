"use strict";

const { createHmac } = require("node:crypto");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, Timestamp } = require("firebase-admin/firestore");
const { onRequest } = require("firebase-functions/v2/https");
const { defineInt, defineSecret } = require("firebase-functions/params");
const {
  RequestError,
  readNewsApiRequest,
  sanitizeSuccess,
  secondsUntilNextUtcDay,
  secondsUntilNextUtcMinute,
} = require("./proxyPolicy");

initializeApp();
const firestore = getFirestore();
const NEWS_API_KEY = defineSecret("NEWS_API_KEY");
const NEWS_API_DAILY_REQUEST_LIMIT = defineInt("NEWS_API_DAILY_REQUEST_LIMIT", { default: 100 });
const ALLOWED_APP_ID = "1:33904026107:android:80ecbda32c0539eade6b5e";
const MAX_RESPONSE_BYTES = 1_000_000;
const INSTALLATION_REQUESTS_PER_MINUTE = 30;
const INSTALLATION_REQUESTS_PER_DAY = 200;
const INSTALLATION_COUNTER_TTL_DAYS = 90;

function setJsonHeaders(res) {
  res.set("Cache-Control", "no-store");
  res.set("Content-Type", "application/json; charset=utf-8");
  res.set("X-Content-Type-Options", "nosniff");
}

function fail(res, status, message) {
  setJsonHeaders(res);
  res.status(status).json({ status: "error", code: "proxy_error", message });
}

async function reserveRequestQuota(req) {
  const installationId = req.get("X-Firebase-Installation-Id");
  if (typeof installationId !== "string" || !/^[A-Za-z0-9_-]{11,128}$/.test(installationId)) {
    throw new RequestError(400, "Missing or invalid Firebase installation ID");
  }

  const dailyLimit = NEWS_API_DAILY_REQUEST_LIMIT.value();
  if (!Number.isSafeInteger(dailyLimit) || dailyLimit < 1 || dailyLimit > 1_000_000) {
    throw new Error("NEWS_API_DAILY_REQUEST_LIMIT must be an integer between 1 and 1000000");
  }

  const nowMs = Date.now();
  const utcDay = new Date(nowMs).toISOString().slice(0, 10);
  const minuteBucket = Math.floor(nowMs / 60_000);
  const hmacKey = process.env.NEWS_PROXY_HMAC_KEY || NEWS_API_KEY.value();
  const installationHash = createHmac("sha256", hmacKey)
    .update(`${req.app.appId}:${installationId}`)
    .digest("hex");
  const installationRef = firestore.collection("newsProxyRateLimits").doc(installationHash);
  const quotaRef = firestore.collection("newsProxyControl").doc("providerQuota");
  const nowTimestamp = Timestamp.fromMillis(nowMs);
  const expiresAt = Timestamp.fromMillis(
    nowMs + INSTALLATION_COUNTER_TTL_DAYS * 24 * 60 * 60 * 1000
  );

  return firestore.runTransaction(async (transaction) => {
    const installationSnapshot = await transaction.get(installationRef);
    const quotaSnapshot = await transaction.get(quotaRef);
    const installation = installationSnapshot.exists ? installationSnapshot.data() : {};
    const quota = quotaSnapshot.exists ? quotaSnapshot.data() : {};

    const installationDayCount = installation.utcDay === utcDay
      ? (Number.isSafeInteger(installation.dayCount) ? installation.dayCount : 0)
      : 0;
    const installationMinuteCount = installation.minuteBucket === minuteBucket
      ? (Number.isSafeInteger(installation.minuteCount) ? installation.minuteCount : 0)
      : 0;
    const providerDayCount = quota.utcDay === utcDay
      ? (Number.isSafeInteger(quota.dayCount) ? quota.dayCount : 0)
      : 0;

    if (installationMinuteCount >= INSTALLATION_REQUESTS_PER_MINUTE) {
      return { allowed: false, retryAfterSeconds: secondsUntilNextUtcMinute(nowMs) };
    }
    if (installationDayCount >= INSTALLATION_REQUESTS_PER_DAY) {
      return { allowed: false, retryAfterSeconds: secondsUntilNextUtcDay(nowMs) };
    }
    if (providerDayCount >= dailyLimit) {
      return { allowed: false, retryAfterSeconds: secondsUntilNextUtcDay(nowMs) };
    }

    transaction.set(installationRef, {
      utcDay,
      dayCount: installationDayCount + 1,
      minuteBucket,
      minuteCount: installationMinuteCount + 1,
      updatedAt: nowTimestamp,
      expiresAt,
    });
    transaction.set(quotaRef, {
      utcDay,
      dayCount: providerDayCount + 1,
      updatedAt: nowTimestamp,
    });
    return { allowed: true };
  });
}

async function readBoundedBody(response, maxBytes) {
  if (!response.body) return "";

  const declaredLength = Number(response.headers.get("content-length"));
  if (Number.isFinite(declaredLength) && declaredLength > maxBytes) {
    await response.body.cancel();
    throw new RequestError(502, "News provider response is too large");
  }

  const reader = response.body.getReader();
  const chunks = [];
  let totalBytes = 0;
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      totalBytes += value.byteLength;
      if (totalBytes > maxBytes) {
        await reader.cancel();
        throw new RequestError(502, "News provider response is too large");
      }
      chunks.push(Buffer.from(value));
    }
  } finally {
    reader.releaseLock();
  }
  return Buffer.concat(chunks, totalBytes).toString("utf8");
}

exports.newsProxy = onRequest(
  {
    region: "europe-west3",
    timeoutSeconds: 15,
    memory: "256MiB",
    maxInstances: 3,
    concurrency: 10,
    serviceAccount: "gemini-news-proxy-runtime@com-aus-gemini01-d91c9.iam.gserviceaccount.com",
    enforceAppCheck: true,
    consumeAppCheckToken: true,
    invoker: "public",
    secrets: [NEWS_API_KEY],
  },
  async (req, res) => {
    setJsonHeaders(res);

    if (req.method !== "GET") {
      res.set("Allow", "GET");
      fail(res, 405, "Method not allowed");
      return;
    }
    if (!req.app || req.app.appId !== ALLOWED_APP_ID || req.app.alreadyConsumed) {
      fail(res, 401, "App Check rejected the request");
      return;
    }

    try {
      const { url } = readNewsApiRequest(req);
      const reservation = await reserveRequestQuota(req);
      if (!reservation.allowed) {
        res.set("Retry-After", String(reservation.retryAfterSeconds));
        fail(res, 429, "News request quota exceeded");
        return;
      }

      const upstream = await fetch(url, {
        method: "GET",
        headers: { "X-Api-Key": NEWS_API_KEY.value() },
        redirect: "manual",
        signal: AbortSignal.timeout(10_000),
      });

      if (upstream.status >= 300 && upstream.status < 400) {
        fail(res, 502, "News provider redirect rejected");
        return;
      }

      const body = await readBoundedBody(upstream, MAX_RESPONSE_BYTES);

      let payload;
      try {
        payload = JSON.parse(body);
      } catch {
        fail(res, 502, "Invalid response from news provider");
        return;
      }

      if (!upstream.ok || payload.status !== "ok") {
        // Keep provider-level `status: error` responses as HTTP 200 so the
        // Android repository reports NewsProviderException instead of stale cache.
        const status = upstream.ok ? 200 : upstream.status;
        const code = typeof payload.code === "string" ? payload.code.slice(0, 80) : "provider_error";
        const message = typeof payload.message === "string"
          ? payload.message.slice(0, 300)
          : "News provider request failed";
        res.status(status).json({ status: "error", code, message });
        return;
      }

      res.status(200).json(sanitizeSuccess(payload));
    } catch (error) {
      if (error instanceof RequestError) {
        fail(res, error.status, error.message);
        return;
      }
      if (error && error.name === "TimeoutError") {
        fail(res, 504, "News provider timed out");
        return;
      }
      if (error && (error.code === 10 || error.code === "aborted")) {
        res.set("Retry-After", "2");
        fail(res, 503, "Rate limit service busy, please retry");
        return;
      }
      console.error("News proxy request failed", error && error.name);
      fail(res, 502, "News provider request failed");
    }
  }
);
