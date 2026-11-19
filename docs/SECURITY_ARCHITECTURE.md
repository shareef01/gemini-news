# Gemini News Security Architecture & Production Threat Model

## Executive Summary

This document specifies the security architecture, threat model, network hardening, credential handling, and defense-in-depth mechanisms implemented in the Gemini News application.

---

## 1. Upstream Credential Threat Model & Proxy Architecture

### 1.1 The Client-Side Secret Vulnerability
Embedding upstream API credentials (such as NewsAPI keys) into an Android client application is fundamentally insecure:
- **Obfuscation Limits**: R8/ProGuard renames class and method names, but constant strings in bytecode, `BuildConfig`, resources, or compiled `.dex` files can be extracted using tools like Jadx, apktool, or strings.
- **Transport Visibility**: Even over HTTPS, an end-user or attacker can inspect network requests using a local MITM proxy (e.g., Charles, mitmproxy) on a rooted device or emulator with user certificates.
- **Quota & Billing Abuse**: Leaked API keys allow unauthorized third parties to exhaust quotas or incur costs.

### 1.2 Production Backend Proxy Design
In production deployments, the Android application does **not** hold or transmit the raw NewsAPI secret. Instead, requests flow through an authenticated backend proxy:

```
+------------------+         App Check Token         +-----------------------+         Upstream Key         +-------------------+
|  Android Client  | ------------------------------> | Backend Proxy         | ---------------------------> | NewsAPI Upstream  |
|  (App Attested)  |   X-Firebase-AppCheck Header    | (Cloud Functions/Run) |   Secret Manager Injected    | (newsapi.org)     |
+------------------+                                 +-----------------------+                              +-------------------+
        ^                                                        |
        |                  Sanitized JSON Response               |
        +--------------------------------------------------------+
```

#### Proxy Responsibilities:
1. **Device & App Attestation**: The prepared HTTPS function enforces Firebase App Check / Play Integrity and consumes limited-use tokens, rejecting missing, invalid, or replayed requests. The Android proxy client requests a new limited-use token for each call.
2. **Secret Injection**: Attaches the server-side `NEWS_API_KEY` (stored in Google Cloud Secret Manager and bound only to the proxy function) before forwarding requests to `https://newsapi.org/`.
3. **Response Sanitization**: Strips unused metadata and filters out `[Removed]` rows before returning the provider response to the app.
4. **Scaling Guardrails & Rate Limiting**: Firestore transactions enforce per-installation limits of 30 requests per minute and 200 per UTC day, plus a global provider budget (100 requests per UTC day by default). Only an HMAC of the Firebase Installation ID is stored, and counters receive a 90-day idle-expiration timestamp. Firestore TTL deletes expired documents asynchronously when the policy is active. Function instance/concurrency caps add a separate scaling guardrail; configure the global budget to the verified NewsAPI plan quota and add billing alerts before public distribution.

### 1.3 Client Proxy-Ready Networking Layer
The client networking layer (`NewsRepository`, `NewsApiService`, and `GdeltNewsApiService`) adapts to the build configuration:
- **Optional Proxy Mode**: When `NEWS_PROXY_BASE_URL` is configured, Retrofit routes all feed and search requests to the proxy without requiring a client-side API key, and attaches a fresh limited-use Firebase App Check token in `X-Firebase-AppCheck` plus the Firebase Installation ID in `X-Firebase-Installation-Id` for rate limiting. This prepared Cloud Functions deployment requires billing and is not active in the current Spark project. App Check is installed at application startup so background worker requests are attested too.
- **Spark Default**: `NEWS_PROVIDER=gdelt` uses the keyless GDELT DOC API. The client sends search terms to GDELT, limits request frequency, backs off for one minute after HTTP 429, caches results briefly, and displays GDELT attribution. It maps only title, publisher domain, URL, and seen time; it does not copy article text or images.
- **Local NewsAPI Development**: Developers may set `NEWS_PROVIDER=newsapi` and `NEWS_API_KEY` in ignored `local.properties`. The key is only read for this debug provider; release builds clear it and use GDELT.
- **Fail-Closed NewsAPI**: When the NewsAPI provider is explicitly selected and no key is present, requests fail with a descriptive `MissingNewsApiKeyException`.

---

## 2. Network Security & Transport Hardening

### 2.1 Cleartext Traffic Elimination
- `AndroidManifest.xml` explicitly enforces `android:usesCleartextTraffic="false"`.
- Production builds use `app/src/main/res/xml/network_security_config.xml` specifying `cleartextTrafficPermitted="false"` with system trust anchors.
- Article and image URLs are normalized to HTTPS or rejected. Image loads also use a dedicated OkHttp DNS guard that rejects private/local addresses for each resolution, including redirect destinations.

### 2.2 Local Development Loopback Isolation
To support local development against mock servers or local proxies, `app/src/debug/res/xml/network_security_config.xml` scopes cleartext traffic strictly to `10.0.2.2` (Android emulator host loopback), `localhost`, and `127.0.0.1` without compromising release builds.

---

## 3. Web Navigation & Publisher Content Isolation

### 3.1 Android Custom Tabs
Directly embedding untrusted third-party publisher websites in an in-app `WebView` introduces significant risks:
- **Phishing & Origin Spoofing**: Users cannot verify the genuine domain or SSL padlock of the publisher page.
- **JavaScript & DOM Vulnerabilities**: Untrusted publisher scripts run within the application's context.

Gemini News routes external publisher pages through **Android Custom Tabs** (`ArticleNavigator`):
- **Process Isolation**: Publisher content runs in the system browser process, isolating cookies, credentials, and memory from the host app.
- **Origin Visibility**: Displays the genuine domain name, SSL certificate status, and browser security controls.
- **Strict URL Validation**: URLs are validated through `safeArticleUrl` before launching, ensuring HTTPS-only schemes.
- **Safe Fallback**: If Custom Tabs is unavailable on the device, it falls back cleanly to external browser intents or the locked-down in-app WebView.

---

## 4. Region-Aware Cache Schema & Database Migrations

### 4.1 Cross-Region Cache Bleed Prevention
This build currently enables only United States headlines. Earlier schemas keyed cached articles by `(url, category)`; Room v7 adds a country dimension so additional regions can be enabled later without mixing cached feeds.

The cache schema isolates partitions across both category and country:
- **Entity Composite Key**: `(url, category, country)` in `CachedArticleEntity`.
- **Partitioned Queries**: Room queries filter by both `category` and `country`.
- **Migration Discipline**: Room version bumped from 6 to 7 with explicit `MIGRATION_6_7`. In SQLite, modifying primary keys requires recreating the table; `MIGRATION_6_7` migrates all cached articles, backfills existing records with `'us'`, and preserves bookmarks, history, and AI results with zero data loss.

---

## 5. Gemini AI Prompt Handling & Limitations

### 5.1 Untrusted Article Content Delimitation
Headlines, summaries, and full text from external RSS/API feeds are treated as untrusted data:
- **Prompt Injection Guard**: Every prompt embeds instructions commanding the model to treat content within `[[DATA]] ... [[/DATA]]` blocks strictly as data and to ignore embedded instructions.
- **Delimiter Stripping**: Incoming text has delimiter tokens stripped to prevent escaping the data block.
- **Context Bounds**: Input fields are capped at 12,000 characters to prevent context window exhaustion or denial-of-service.

### 5.2 Structured Insights & Evidence Attribution
Summary and Reader screens currently display model-generated text directly. The `parseArticleInsight` utility is not connected to those production flows, and its lexical overlap heuristic is not reliable evidence verification. Treat AI output as unverified; the prompt guard is a best-effort instruction and does not prevent hallucinations or prompt injection.

---

## 6. Privacy & Backup Policy

- **Excluded from Cloud Backup**: `data_extraction_rules.xml` and `backup_rules.xml` explicitly exclude `database`, `datastore/`, and `sharedpref`.
- **Local History & Bookmarks**: User reading history, bookmarks, and Gemini cache remain on the local device and are not uploaded to cloud backups or device-to-device migration transfers.
- **Ephemeral AI Conversations**: AI chat turns remain strictly in-memory and are never written to disk.
- **Proxy Rate-Limit Data**: Proxy requests include the Firebase Installation ID for rate limiting. The backend stores an HMAC of that ID, not the raw ID, and sets an expiration timestamp 90 days after the last update. Firestore TTL deletes expired documents asynchronously when enabled. Disclose this purpose and retention in the app privacy notice and Google Play Data safety form.
