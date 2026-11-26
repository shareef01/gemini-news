"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const _internal = require("./proxyPolicy.js");

test("readNewsApiRequest allowlists US top-headlines and rejects unknown params", () => {
  const ok = _internal.readNewsApiRequest({
    path: "/v2/top-headlines",
    query: { country: "us", category: "technology", page: "2", pageSize: "20" },
  });
  assert.equal(
    ok.url,
    "https://newsapi.org/v2/top-headlines?page=2&pageSize=20&country=us&category=technology"
  );

  assert.throws(
    () => _internal.readNewsApiRequest({ path: "/v2/top-headlines", query: { country: "gb" } }),
    /Unsupported country/
  );
  assert.throws(
    () => _internal.readNewsApiRequest({ path: "/v2/top-headlines", query: { category: "smart" } }),
    /Unsupported category/
  );
  assert.throws(
    () => _internal.readNewsApiRequest({ path: "/v2/top-headlines", query: { apiKey: "leak" } }),
    /Unsupported query parameter/
  );
});

test("readNewsApiRequest trims search query and rejects blank or oversized queries", () => {
  const ok = _internal.readNewsApiRequest({
    path: "/v2/everything",
    query: { q: "  semiconductors  " },
  });
  assert.equal(ok.url, "https://newsapi.org/v2/everything?page=1&pageSize=20&q=semiconductors");

  assert.throws(
    () => _internal.readNewsApiRequest({ path: "/v2/everything", query: { q: "   " } }),
    /Missing query parameter: q/
  );
});

test("sanitizeSuccess upgrades http URLs, rejects unsafe schemes, and deduplicates URLs", () => {
  const sanitized = _internal.sanitizeSuccess({
    totalResults: 5,
    articles: [
      { title: "Story A", url: "http://example.com/a", source: { name: "Example" } },
      { title: "Duplicate Story A", url: "https://example.com/a", source: { name: "Example" } },
      { title: "[Removed]", url: "https://removed.com" },
      { title: "Script URL", url: "javascript:alert(1)" },
      { title: "Credential URL", url: "https://user:pass@example.com/b" },
    ],
  });

  assert.equal(sanitized.status, "ok");
  assert.equal(sanitized.articles.length, 1);
  assert.equal(sanitized.articles[0].url, "https://example.com/a");
  assert.equal(sanitized.articles[0].title, "Story A");
});
