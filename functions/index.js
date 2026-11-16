"use strict";

const { onRequest } = require("firebase-functions/v2/https");
const { defineSecret } = require("firebase-functions/params");

const NEWS_API_KEY = defineSecret("NEWS_API_KEY");
const NEWS_API_ORIGIN = "https://newsapi.org";
const ALLOWED_APP_ID = "1:33904026107:android:80ecbda32c0539eade6b5e";
const MAX_PAGE_SIZE = 20;
const MAX_PAGE = 1000;
const MAX_RESPONSE_BYTES = 1_000_000;
const CATEGORIES = new Set([
  "business",
  "entertainment",
  "general",
  "health",
  "science",
  "sports",
  "technology",
]);

class RequestError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

function setJsonHeaders(res) {
  res.set("Cache-Control", "no-store");
  res.set("Content-Type", "application/json; charset=utf-8");
  res.set("X-Content-Type-Options", "nosniff");
}

function fail(res, status, message) {
  setJsonHeaders(res);
  res.status(status).json({ status: "error", code: "proxy_error", message });
}

function queryString(req, name, { required = false, maxLength = 100, trim = false } = {}) {
  const value = req.query[name];
  if (value === undefined) {
    if (required) throw new RequestError(400, `Missing query parameter: ${name}`);
    return undefined;
  }
  if (typeof value !== "string" || value.length > maxLength || (!trim && value.trim() !== value)) {
    throw new RequestError(400, `Invalid query parameter: ${name}`);
  }
  const normalized = trim ? value.trim() : value;
  if (required && normalized.length === 0) {
    throw new RequestError(400, `Missing query parameter: ${name}`);
  }
  return normalized;
}

function queryInteger(req, name, { fallback, maximum }) {
  const value = queryString(req, name, { maxLength: 5 });
  if (value === undefined) return fallback;
  if (!/^[1-9]\d*$/.test(value)) {
    throw new RequestError(400, `Invalid query parameter: ${name}`);
  }
  const parsed = Number(value);
  if (!Number.isSafeInteger(parsed) || parsed > maximum) {
    throw new RequestError(400, `Invalid query parameter: ${name}`);
  }
  return parsed;
}

function rejectUnknownParameters(req, allowed) {
  for (const name of Object.keys(req.query)) {
    if (!allowed.has(name)) {
      throw new RequestError(400, `Unsupported query parameter: ${name}`);
    }
  }
}

function readNewsApiRequest(req) {
  const page = queryInteger(req, "page", { fallback: 1, maximum: MAX_PAGE });
  const pageSize = queryInteger(req, "pageSize", {
    fallback: MAX_PAGE_SIZE,
    maximum: MAX_PAGE_SIZE,
  });
  const params = new URLSearchParams({ page: String(page), pageSize: String(pageSize) });

  if (req.path === "/v2/top-headlines") {
    rejectUnknownParameters(req, new Set(["country", "category", "page", "pageSize"]));
    const country = queryString(req, "country", { maxLength: 2 }) ?? "us";
    if (country !== "us") throw new RequestError(400, "Unsupported country");

    const category = queryString(req, "category", { maxLength: 20 });
    if (category !== undefined && !CATEGORIES.has(category)) {
      throw new RequestError(400, "Unsupported category");
    }
    params.set("country", country);
    if (category !== undefined) params.set("category", category);
    return { url: `${NEWS_API_ORIGIN}/v2/top-headlines?${params}` };
  }

  if (req.path === "/v2/everything") {
    rejectUnknownParameters(req, new Set(["q", "page", "pageSize"]));
    const query = queryString(req, "q", { required: true, maxLength: 500, trim: true });
    params.set("q", query);
    return { url: `${NEWS_API_ORIGIN}/v2/everything?${params}` };
  }

  throw new RequestError(404, "Unknown endpoint");
}

function sanitizeArticle(article) {
  if (!article || typeof article !== "object") return null;
  const title = typeof article.title === "string" ? article.title : "";
  const url = typeof article.url === "string" ? article.url : "";
  if (!title.trim() || title === "[Removed]" || !url.trim() || url === "https://removed.com") {
    return null;
  }

  const source = article.source && typeof article.source === "object" ? article.source : {};
  return {
    source: {
      id: typeof source.id === "string" ? source.id : null,
      name: typeof source.name === "string" ? source.name : "Unknown",
    },
    author: typeof article.author === "string" ? article.author : null,
    title,
    description: typeof article.description === "string" ? article.description : null,
    url,
    urlToImage: typeof article.urlToImage === "string" ? article.urlToImage : null,
    publishedAt: typeof article.publishedAt === "string" ? article.publishedAt : "",
    content: typeof article.content === "string" ? article.content : null,
  };
}

function sanitizeSuccess(payload) {
  const sourceArticles = Array.isArray(payload.articles) ? payload.articles : [];
  const articles = sourceArticles.map(sanitizeArticle).filter(Boolean);
  const totalResults = Number(payload.totalResults);
  return {
    status: "ok",
    totalResults: Number.isSafeInteger(totalResults) && totalResults >= 0 ? totalResults : articles.length,
    articles,
  };
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

      const body = await upstream.text();
      if (Buffer.byteLength(body, "utf8") > MAX_RESPONSE_BYTES) {
        fail(res, 502, "News provider response is too large");
        return;
      }

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
      console.error("News proxy request failed", error && error.name);
      fail(res, 502, "News provider request failed");
    }
  }
);
