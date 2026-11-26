"use strict";

const NEWS_API_ORIGIN = "https://newsapi.org";
const MAX_PAGE_SIZE = 20;
const MAX_PAGE = 1000;
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

function secondsUntilNextUtcMinute(nowMs) {
  return Math.max(1, Math.ceil(((Math.floor(nowMs / 60_000) + 1) * 60_000 - nowMs) / 1000));
}

function secondsUntilNextUtcDay(nowMs) {
  const now = new Date(nowMs);
  const nextUtcMidnight = Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate() + 1);
  return Math.max(1, Math.ceil((nextUtcMidnight - nowMs) / 1000));
}

function normalizeHttpsUrl(raw) {
  if (typeof raw !== "string") return null;
  const trimmed = raw.trim();
  if (!trimmed || trimmed === "https://removed.com") return null;
  const upgraded = /^http:\/\//i.test(trimmed)
    ? `https://${trimmed.slice(7)}`
    : trimmed;
  if (!/^https:\/\//i.test(upgraded)) return null;
  try {
    const parsed = new URL(upgraded);
    if (parsed.protocol !== "https:" || !parsed.hostname || parsed.username || parsed.password) {
      return null;
    }
    return upgraded;
  } catch {
    return null;
  }
}

function sanitizeArticle(article) {
  if (!article || typeof article !== "object") return null;
  const title = typeof article.title === "string" ? article.title : "";
  const url = normalizeHttpsUrl(article.url);
  if (!title.trim() || title === "[Removed]" || !url) {
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
    urlToImage: typeof article.urlToImage === "string" ? normalizeHttpsUrl(article.urlToImage) : null,
    publishedAt: typeof article.publishedAt === "string" ? article.publishedAt : "",
    content: typeof article.content === "string" ? article.content : null,
  };
}

function sanitizeSuccess(payload) {
  const sourceArticles = Array.isArray(payload.articles) ? payload.articles : [];
  const seenUrls = new Set();
  const articles = [];
  for (const item of sourceArticles) {
    const sanitized = sanitizeArticle(item);
    if (sanitized && !seenUrls.has(sanitized.url)) {
      seenUrls.add(sanitized.url);
      articles.push(sanitized);
    }
  }
  const totalResults = Number(payload.totalResults);
  return {
    status: "ok",
    totalResults: Number.isSafeInteger(totalResults) && totalResults >= 0 ? totalResults : articles.length,
    articles,
  };
}

module.exports = {
  RequestError,
  normalizeHttpsUrl,
  readNewsApiRequest,
  sanitizeArticle,
  sanitizeSuccess,
  secondsUntilNextUtcMinute,
  secondsUntilNextUtcDay,
};
