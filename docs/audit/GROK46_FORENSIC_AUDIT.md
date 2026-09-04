# Gemini News — Grok 4.6 Forensic Engineering Audit

**Audit commit:** `b86d9b678caafdf5ef7178b948f2743200480978`  
**Branch audited:** `main`  
**Working tree at audit start:** clean  
**Audit date:** 2026-09-04 (UTC)  
**Auditor role:** Principal Android / AI / Security / Release forensic pass  
**Scope:** Source-backed + runtime/build-informed. No application rewrite performed.

---

## A. Audit Metadata

| Field | Value |
|---|---|
| Commit | `b86d9b678caafdf5ef7178b948f2743200480978` (`b86d9b6`) |
| Branch | `main` |
| Working tree | Clean at audit start (`gradlew` mode bit later fixed locally only) |
| JDK (launcher) | OpenJDK 21.0.10 |
| Gradle | 9.5.0 (wrapper); daemon toolchain requests Java 25 |
| AGP / Kotlin / KSP | 9.3.1 / 2.3.21 / 2.3.11 |
| Android SDK used | Platforms 35, 36, 37.0; build-tools 36.1.0 |
| App IDs | `applicationId` / namespace `com.aus.gemini01` |
| Version | `versionCode=1`, `versionName=1.0` |

### Baseline commands

| Command | Exit | Result |
|---|---|---|
| `./gradlew --version` | 0 | Gradle 9.5.0 / Kotlin 2.3.20 embedded |
| `./gradlew tasks` | 0 | Tasks listed |
| `./gradlew clean` | 0 | Success |
| `./gradlew testDebugUnitTest` | 0 | **61 tests, 0 failures** |
| `./gradlew lintDebug` | 0 | 12 warnings (dependency freshness + `SetJavaScriptEnabled` + label) |
| `./gradlew assembleDebug` | 0 | APK ~72 MB |
| `./gradlew assembleRelease` (no keystore) | **fail** | `validateSigningRelease`: Keystore file not set |
| `./gradlew assembleRelease` (temp audit keystore) | 0 | R8 minify+shrink succeeded; APK ~4.9 MB |

---

## B. Executive Summary

This commit is a carefully hardened **demo / early-product** Android news client with real defensive work already present (prompt fences, URL scheme filters, AI dedupe/cache, offline banner, cancellation hygiene in most paths, backup exclusions). It is **not** production-ready as a shipped consumer app.

| Risk class | Verdict |
|---|---|
| Release readiness | **Not ready** without model pinning, release process, CI, and NewsAPI threat-model decision |
| Biggest correctness risk | Successful empty NewsAPI responses wipe Room feed cache; Smart Themes discards current feed |
| Biggest security risk | Client-embedded `NEWS_API_KEY` extractable from release APK; App Check debug registrar shipped in release |
| Biggest AI risk | Hard-coded `gemini-flash-latest` alias (Firebase explicitly discourages for production) + multi-turn chat injection residual |
| Biggest data-loss risk | `fallbackToDestructiveMigration(dropAllTables=true)` still armed; only `4→5` migration exists |
| Biggest release risk | Release signing always required; no keystore → assembleRelease fails; no CI; `versionCode=1` |
| Biggest test gap | No Room migration tests, no deep-link/instrumentation coverage, no release smoke, no 50-caller AI concurrency regression beyond basic dedupe |

**Release decision:** 🟠 **NOT READY — SIGNIFICANT FIXES REQUIRED**

---

## C. Overall Scorecard

| Area | Score (0–10) |
|---|---|
| Architecture | 7.5 |
| Correctness | 6.0 |
| Kotlin/coroutines | 7.0 |
| Compose | 7.0 |
| AI integration | 6.5 |
| AI safety | 6.0 |
| Networking | 6.5 |
| Offline behavior | 6.5 |
| Room/data integrity | 5.5 |
| Security | 5.5 |
| Privacy | 7.0 |
| WorkManager | 6.5 |
| Performance | 7.0 |
| Accessibility | 5.5 |
| Testing | 5.0 |
| Release engineering | 4.0 |
| Documentation | 3.5 |

**Production readiness: 4.5 / 10**

---

## 102. Architecture Map (actual)

```
UI (Compose)
  AdaptiveNewsScreen / NewsScreen / ReadingModeScreen
  NewsChatScreen / NewsMapScreen / SettingsScreen
  MarkdownText / ArticleWebView / TtsManager / VoiceRecognizer
        ↓
NewsViewModel (single God-VM; StateFlow + SharedFlow errorEvents)
        ↓
┌───────────────────┬──────────────────────┬─────────────────────┐
│ NewsRepository    │ AiRepository         │ SettingsRepository  │
│ Retrofit→NewsAPI  │ Room ai_results +    │ DataStore settings  │
│ Room cache/bkm/hx │ in-flight Deferred   │ DataStoreAiTelemetry│
└─────────┬─────────┴──────────┬───────────┴──────────┬──────────┘
          │                    │                      │
     OkHttp/Retrofit     Firebase.ai             DataStore
     newsapi.org         generativeModel         prefs
                         (GEMINI_MODEL)
Workers:
  MainActivity schedules
    NewsWorker (hourly, unique) — NewsAPI only; skips if free-tier saver ON
    ReminderWorker (24h) — bookmarks random nudge; no Gemini
```

Real class names confirmed in source. No separate `Application` class; App Check + WorkManager scheduling live in `MainActivity`.

---

## 103. README Claim vs Reality Matrix

| Claim | Reality | Verdict |
|---|---|---|
| Kotlin 2.3.21 | `libs.versions.toml` = 2.3.21 | ✅ VERIFIED |
| Compose BOM 2026.08.00 | catalog = 2026.08.00 | ✅ VERIFIED |
| Adaptive Material3 1.3.0 + `ListDetailPaneScaffold` | catalog 1.3.0; used in `AdaptiveNewsScreen` | ✅ VERIFIED |
| Target API 36 (badge/prereq) | `targetSdk = 37`, `compileSdk release(37)` | ❌ STALE / FALSE |
| Min SDK API 26 | `minSdk = 31` | ❌ STALE / FALSE |
| Room 2.7.0 | Room **2.8.4** | ❌ STALE / FALSE |
| WorkManager 2.10.0 | **2.11.2** | ❌ STALE / FALSE |
| Retrofit 2 / OkHttp 3 | Retrofit **3.0.0**; OkHttp transitive via Retrofit/Coil | ❌ STALE / FALSE |
| Firebase AI / `gemini-flash-latest` | `GEMINI_MODEL = "gemini-flash-latest"` | ✅ VERIFIED (alias exists in code; **not** production-recommended) |
| Settings “Gemini 2.5 Flash” | Hard-coded UI label ≠ model constant | ❌ STALE / FALSE |
| DataStore 1.2.1 | 1.2.1 | ✅ VERIFIED |
| Coil 3.5.0 | 3.5.0 | ✅ VERIFIED |
| Maps Compose 8.4.0 / Maps 20.0.0 | match | ✅ VERIFIED |
| Offline-first Room architecture | Network-first with Room fallback + offline banner | ⚠️ PARTIALLY TRUE |
| AI SHA-256 cache + dedupe | `AiCacheKeys` + `AiRepository.inFlight` | ✅ VERIFIED |
| Reader “strips paywalls” | Reformats NewsAPI title/description/content via Gemini; **no HTML fetch / no paywall bypass** | ❌ STALE / FALSE (marketing) |
| Personalized For You / weekly digest / map / chat / trends | Implemented in `NewsViewModel` | ✅ VERIFIED |
| Voice search / TTS / notifications / privacy controls | Present | ✅ VERIFIED |
| Unit test suite as described | 61 unit tests across claimed areas | ✅ VERIFIED |
| MIT License file | README claims MIT; **no LICENSE file** | ❌ STALE / FALSE |
| Add `google-services.json` manually | File is **already tracked** in repo | ❌ STALE / FALSE |
| JDK 17+ | Builds with JDK 21; bytecode target Java **11** | ⚠️ PARTIALLY TRUE |
| AGP / Firebase AI versions | AGP 9.3.1, firebase-ai 17.15.0 (not in README table) | ❓ UNVERIFIED in README (omitted / drifted) |

---

## 104. P0 Findings

**No confirmed P0 findings.**

Rationale: no privileged server credential in git history beyond expected Firebase client config; deep links do not execute arbitrary schemes; destructive migration is real but limited by this repo’s schema history starting at v4; App Check debug code is present in release but release init path installs Play Integrity only.

---

## 105. P1 Findings

### GN-AUD-001 — Production Gemini model uses unstable `-latest` alias

- **Severity:** P1  
- **Confidence:** High  
- **Status:** Confirmed (source + official Firebase docs)  
- **Area:** AI / Release  
- **Files:** `app/src/main/java/com/aus/gemini01/data/ai/AiCacheKeys.kt` (`GEMINI_MODEL`), `NewsViewModel.kt`, `SettingsScreen.kt`  
- **Symbols:** `GEMINI_MODEL`, `Firebase.ai.generativeModel`

**Evidence:** Constant is `"gemini-flash-latest"`. Firebase AI Logic docs: do **not** use `-latest` aliases in production; prefer pinned stable IDs (e.g. `gemini-3.7-flash`). Settings UI falsely labels “Gemini 2.5 Flash”. Release APK strings contain `gemini-flash-latest` and an “Unsupported Gemini model …” template.

**Trigger:** Any AI feature in a production build.  
**Impact:** Sudden behavior changes or hard failures (404/model unavailable) without an app update; cache keys tied to alias string may not invalidate when the backend target silently changes.  
**Reproduction:** Inspect `GEMINI_MODEL`; compare Firebase production checklist.  
**Root cause:** Convenience alias hard-coded.  
**Recommended fix:** Pin a stable model ID; optionally Remote Config; align Settings label; bump `AI_PROMPT_VERSION` when prompts/model semantics change.  
**Regression test:** Assert model constant matches allowlist of pinned IDs; fail CI on `-latest`.  
**Release relevance:** **BLOCKER**

---

### GN-AUD-002 — `NEWS_API_KEY` compiled into release APK (extractable)

- **Severity:** P1  
- **Confidence:** High  
- **Status:** Confirmed (release APK strings)  
- **Area:** Security / Networking / Billing  
- **Files:** `app/build.gradle.kts`, `NewsRepository.kt`, release `classes.dex`

**Evidence:** `buildConfigField("String", "NEWS_API_KEY", …)` → Retrofit query `apiKey=…`. Release APK `strings` contained the audit placeholder key value. Git secrecy of `local.properties` does **not** protect runtime.

**Threat model (NewsAPI-specific):** Mobile clients cannot secret-store this key. On developer/free plans, extracted keys enable quota theft and account abuse. NewsAPI’s production guidance historically expects server-side use for distributed apps.

**Recommended fix:** Decide consciously: (a) personal/demo distribution with monitored rotatable keys + rate limits, or (b) thin backend proxy with per-user quotas before consumer release. Do not treat “gitignore” as mitigation.  
**Regression test:** Optional CI check that release BuildConfig key is empty/placeholder in public CI artifacts.  
**Release relevance:** **BLOCKER** for consumer-scale release; **SHOULD FIX** process for private demos

---

### GN-AUD-003 — Release App Check debug provider ships in production APK

- **Severity:** P1 (hygiene / defense-in-depth) — exploitability **not** confirmed  
- **Confidence:** High (presence); Medium (exploit path)  
- **Status:** Confirmed present; Likely not auto-activated  
- **Area:** Security / Build  
- **Files:** `app/build.gradle.kts` (`implementation(libs.firebase.appcheck.debug)`), `MainActivity.installAppCheckProvider()`, release `AndroidManifest.xml`

**Evidence:**
1. Dependency is `implementation`, not `debugImplementation`.
2. Release merged manifest registers `FirebaseAppCheckDebugRegistrar`.
3. R8 seeds/mapping retain debug App Check classes.
4. Runtime init: `BuildConfig.DEBUG ? Debug… : PlayIntegrity…` — release installs Play Integrity only.

**Impact:** Unnecessary debug attestation code + registrar in production; larger attack/analysis surface; fails “debug-only” intent. Not automatically “debug tokens work in Play builds” without also installing the debug factory / registering tokens.  
**Recommended fix:** `debugImplementation` for debug App Check; release-only Play Integrity; verify registrar absent from release manifest.  
**Regression test:** APK manifest assert no `FirebaseAppCheckDebugRegistrar` in release.  
**Release relevance:** **SHOULD FIX** (treat as blocker for App Check–enforced production)

---

## 106. P2 Findings

### GN-AUD-010 — Successful empty NewsAPI payload wipes category cache

- **Severity:** P2  
- **Confidence:** High  
- **Status:** Confirmed (source)  
- **Area:** Network / Offline / Database  
- **Files:** `NewsRepository.getTopHeadlines`  
- **Evidence:** On success, always `deleteCachedArticlesByCategory` then insert filtered list — including empty. Offline fallback only runs in `catch`.  
- **Impact:** Transient empty/`[Removed]`-only responses erase previously good offline feed.  
- **Fix:** Skip cache replace when `articles.isEmpty()`; or require min count / ETag freshness.  
- **Test:** `emptySuccess_doesNotWipeNonEmptyCache()`.  
- **Release relevance:** SHOULD FIX

### GN-AUD-011 — `fallbackToDestructiveMigration(dropAllTables=true)` still enabled

- **Severity:** P2  
- **Confidence:** High  
- **Status:** Confirmed  
- **Area:** Database  
- **Files:** `AppDatabase.kt`  
- **Evidence:** Only `MIGRATION_4_5` registered. Commit `0c91630` removed destructive migration; commit `839fa51` re-added it with v5. Repo history starts at **version 4** (no v1–v3 schemas in git).  
- **Impact:** Unknown/corrupt version → silent wipe of bookmarks, history, cache, AI results with **no UI disclosure**. 4→5 preserves data.  
- **Fix:** Remove destructive fallback for release, or limit to debug; add migration tests; enable `exportSchema=true`.  
- **Release relevance:** SHOULD FIX

### GN-AUD-012 — `loadNextPage` swallows `CancellationException`

- **Severity:** P2  
- **Confidence:** High  
- **Status:** Confirmed  
- **Area:** Concurrency  
- **Files:** `NewsViewModel.loadNextPage`  
- **Evidence:** `catch (e: Exception)` with empty body; Kotlin `CancellationException` extends `Exception`.  
- **Impact:** Cancelled pagination can clear loading flags incorrectly and violate structured concurrency.  
- **Fix:** Rethrow `CancellationException` like other methods.  
- **Test:** cancel during `loadNextPage` asserts cooperative cancel.  
- **Release relevance:** SHOULD FIX

### GN-AUD-013 — Smart Themes always drops in-memory feed then refetches `general`

- **Severity:** P2  
- **Confidence:** High  
- **Status:** Confirmed  
- **Area:** AI / Correctness / Billing (NewsAPI)  
- **Files:** `NewsViewModel.generateSmartThemes`  
- **Evidence:** Sets `_uiState = Loading` **before** reading Success articles → list always empty → always network fetch of `general`, ignoring user’s current category.  
- **Impact:** Wrong theme corpus + extra NewsAPI call every cold Smart Themes run.  
- **Fix:** Snapshot articles **before** Loading; use current category.  
- **Release relevance:** SHOULD FIX

### GN-AUD-014 — AI result cache has no TTL / prune

- **Severity:** P2  
- **Confidence:** High  
- **Status:** Confirmed  
- **Area:** AI / Database  
- **Files:** `AiResultEntity`, `AiResultDao` (only `clearAll`)  
- **Impact:** Trends/locations/for-you keywords can remain forever; DB growth unbounded across years; stale “global narratives”. Summaries more stable; trends are not.  
- **Fix:** Per-kind TTL + prune worker; include TTL in cache read path.  
- **Release relevance:** SHOULD FIX

### GN-AUD-015 — Multi-turn chat drops injection fence after first message

- **Severity:** P2  
- **Confidence:** Medium–High  
- **Status:** Confirmed (source)  
- **Area:** AI safety  
- **Files:** `NewsViewModel.sendChatMessage`  
- **Evidence:** Guard + `[[DATA]]` only on first turn; subsequent user turns send raw `query` into existing `chatSession`. Model prior outputs become trusted context (second-order injection).  
- **Impact:** Hostile headline content or user paste can steer later replies; residual risk after good first-turn fencing.  
- **Fix:** System instruction once; keep data fences; consider resetting session periodically; never treat model output as privileged.  
- **Release relevance:** SHOULD FIX

### GN-AUD-016 — Reader mode / cancelAnalysis race; concurrent reader jobs

- **Severity:** P2  
- **Confidence:** High  
- **Status:** Confirmed  
- **Area:** Concurrency / AI cost  
- **Files:** `fetchReaderView`, `cancelAnalysis`  
- **Evidence:** Reader uses unstructured `viewModelScope.launch` **not** `analysisJob`; cancel overlay does not cancel reader; rapid article switches can overlap (dedupe helps same URL only).  
- **Impact:** Stale reader result can land on newer article if URLs differ and responses reorder; wasted quota.  
- **Fix:** Track `readerJob`; cancel previous; ignore results for non-current URL.  
- **Release relevance:** SHOULD FIX

### GN-AUD-017 — HTTP 429 presented as “offline cached” in foreground

- **Severity:** P2  
- **Confidence:** High  
- **Status:** Confirmed (reasoning over Retrofit + repository)  
- **Area:** Network / UX  
- **Evidence:** Any `Exception` (incl. `HttpException` 429) → offline fallback when cache exists → `_isServingCached=true` banner “Offline — showing cached headlines”.  
- **Impact:** Users misdiagnosed; no rate-limit messaging. WorkManager path correctly `Result.failure()` on 4xx (good — no retry storm).  
- **Fix:** Classify HTTP status before fallback; distinct UI for quota vs offline.  
- **Release relevance:** SHOULD FIX

### GN-AUD-018 — Breaking-news worker inert under default free-tier saver

- **Severity:** P2  
- **Confidence:** High  
- **Status:** Confirmed  
- **Area:** WorkManager / Product  
- **Files:** `NewsWorker`, Settings default `newsApiFreeTier=true`  
- **Impact:** User enables “Breaking News Alerts” but worker returns success without fetching while free-tier saver is on — silent no-op. Not spam; **false product promise**.  
- **Fix:** Gate toggle UX; or fetch with strict budget; disclose dependency.  
- **Release relevance:** SHOULD FIX

### GN-AUD-019 — Release signing config always attached; missing keystore fails build

- **Severity:** P2  
- **Confidence:** High  
- **Status:** Confirmed (runtime)  
- **Area:** Release engineering  
- **Evidence:** `signingConfig = signingConfigs.getByName("release")` even when `keystore.properties` absent → `validateSigningRelease` fails. Good that it does **not** silently debug-sign; bad that process/docs don’t make this clear.  
- **Release relevance:** SHOULD FIX (docs + optional unsigned minify task for CI)

---

## 107. P3 / P4 Findings (grouped)

| ID | Sev | Finding |
|---|---|---|
| GN-AUD-030 | P3 | README / badge / Getting Started heavily drifted (SDK levels, Room, WM, Retrofit, LICENSE, google-services instructions) |
| GN-AUD-031 | P3 | No `.github/workflows` CI |
| GN-AUD-032 | P3 | No LICENSE file despite MIT claim |
| GN-AUD-033 | P3 | `exportSchema = false` weakens migration confidence |
| GN-AUD-034 | P3 | Reminders toggle does not request `POST_NOTIFICATIONS` |
| GN-AUD-035 | P3 | TTS speaks raw Markdown (headers/`**`) — no strip |
| GN-AUD-036 | P3 | TTS chunker can enqueue oversized sentence chunks (`chunks.add(s)` when `s.length > max`) |
| GN-AUD-037 | P3 | Hard-coded English UI strings; `supportsRtl=true` but limited mirrored validation |
| GN-AUD-038 | P3 | Accessibility: many icons `contentDescription=null`; fixed search bar height may clip large fonts |
| GN-AUD-039 | P3 | `firebase-appcheck-debug` also inflates debug APK unnecessarily via same `implementation` |
| GN-AUD-040 | P3 | Maps merge adds `glEsVersion=0x20000 required=true` — may filter devices |
| GN-AUD-041 | P3 | Clear History dialog claims weekly analysis reset; only clears `history_articles` (AI stats cache keys remain until Clear Cache) |
| GN-AUD-042 | P4 | `versionCode=1` / no release train |
| GN-AUD-043 | P4 | Custom `newsapp://` scheme remains useful for shortcuts; low exploitability after decode harden |
| GN-AUD-044 | P4 | Java 11 bytecode with JDK 21/25 toolchain — valid; document intent |
| GN-AUD-045 | P4 | Lint `SetJavaScriptEnabled` on WebView — intentional for publisher pages; schemes restricted |

---

## 108. AI / LLM Audit Section

### Feature matrix

| Feature | Model | Input | Cache | Parser | Failure UI |
|---|---|---|---|---|---|
| Summary | `gemini-flash-latest` | title/desc/content + language | Room SHA key | MarkdownText | AIDialog Failure |
| Reader | same | NewsAPI fields only | Room | MarkdownText | ReadingMode error + retry |
| For You | same | bookmark/history titles → keywords → NewsAPI search | keywords cached | comma text | Error state |
| Stats / digest | same | history titles + language | Room | MarkdownText | AIDialog |
| Trends | same | up to 15 titles + language | Room | MarkdownText | AIDialog |
| Smart Themes | same | indexed titles | Room | `parseSmartThemesResponse` | Error state |
| Locations / Map | same | title\|url lines | Room | `parseLocationsResponse` | snackbar / empty map |
| Chat | same | headlines (turn1) + user text | **uncached** | plain text bubbles | chat error bubble |

### Cost / amplification

| Action | Amplification |
|---|---|
| Summary / reader same URL concurrent | LOW (deduped + cached) |
| Rapid different articles reader | MEDIUM–HIGH (no readerJob cancel) |
| Smart Themes open | MEDIUM (extra NewsAPI + Gemini) |
| Chat multi-turn | MEDIUM (uncached every send) |
| Map / trends re-tap same feed | LOW after cache |
| WorkManager | **None** for Gemini |
| Recomposition alone | LOW (user/event driven; not LaunchedEffect spam) |

### Dedupe semantics (confirmed by tests + code)

- Key = cache key string; `ConcurrentHashMap` + synchronized create; shared `Deferred` on `SupervisorJob` scope.  
- UI cancel abandons await; underlying fetch continues (by design).  
- Failures not cached; removed from in-flight in `finally`.  
- Missing tests: first-caller cancel isolation, 50-caller stress, failed-key immediate retry (behavior OK in code).

### Prompt injection

- First-order: `PROMPT_INJECTION_GUARD` + `[[DATA]]` fences on article-fed features — good.  
- Second-order: chat session + cached reader/summary text later shown only in UI (Markdown **non-clickable** — good). Map uses Gemini coords; article open resolves URL against local list (safe).  
- Structured output: **prompt-only** line formats; parsers are defensive (skip bad lines) — good. Not schema-constrained.

### Provenance

- Summaries/dialogs labeled “Generated by Gemini AI”; reader “Formatted by Gemini AI”; WebView path to source exists — good.  
- Reader does **not** bypass paywalls — documentation must be corrected.

### App Check

- Debug vs Play Integrity selected in `MainActivity`.  
- Ordering: App Check install before UI; AI calls happen later on user action — acceptable.  
- Debug dependency incorrectly in release artifact (GN-AUD-003).

---

## 109. Database Section

### Schema (v5)

| Table | PK | Indices | Growth | Retention |
|---|---|---|---|---|
| `articles` (bookmarks) | `url` | none beyond PK | user-driven | until delete |
| `cached_articles` | `(url, category)` | none | per category page1 | 7-day prune by **publishedAt** string + replace-on-fetch |
| `history_articles` | `url` | none | capped | `trimHistory(50)` |
| `ai_results` | `cacheKey` | none | unbounded | only manual clear |

`exportSchema = false`.

### Migration matrix

| Upgrade | Data preserved? | Tested? |
|---|---|---|
| 1→5 | N/A in this repo (never shipped); would destructive-wipe if encountered | No |
| 2→5 | same | No |
| 3→5 | same | No |
| 4→5 | Yes — `MIGRATION_4_5` creates `ai_results` | **No automated test** |

Destructive fallback impact if triggered: **bookmarks + history + cache + AI cache all dropped**, no UI disclosure. Regenerable: news cache, AI cache. User-created: bookmarks/history — serious if hit.

---

## 110. Security Section

| Topic | Assessment |
|---|---|
| Firebase `google-services.json` | Tracked; client API key `AIzaSyBJ…ON_E` (redacted). Expected client config; restrict keys in Google Cloud (API/App restrictions). Package `com.aus.gemini01` matches. |
| NewsAPI key | BuildConfig → APK extractable (GN-AUD-002) |
| Maps key | Manifest placeholder → APK meta-data; must use Android package + SHA restrictions |
| App Check | Play Integrity in release runtime; debug code still packaged (GN-AUD-003) |
| Deep link `newsapp://` | Hosts `category`/`search` only; decode hardened; no arbitrary navigation to attacker URLs |
| Exported `MainActivity` | No privileged extras API; data URI handled narrowly |
| Backup | `allowBackup=true` but DB + DataStore excluded from cloud backup **and** device transfer |
| Permissions | INTERNET; POST_NOTIFICATIONS (runtime for alerts); RECORD_AUDIO only after mic tap |
| Logging | No `Log`/`println`/`Timber` in app source — good |
| Git secrets | Firebase client key in history from initial commit; no keystores/service accounts found. Rotate only if key was unrestricted and abused. |
| `.gitignore` | Protects `local.properties`, `keystore.properties`, `/keystore/` |

---

## 111. Offline Failure Matrix

| Scenario | Expected (true offline-first) | Actual |
|---|---|---|
| Fresh install offline | Empty/error with clear message | Error (no cache) — OK |
| Cached feed offline | Show cache, labeled stale | Shows cache + “Offline — showing cached headlines” — OK |
| NewsAPI 429 | Rate-limit message; keep cache | Treated as offline cache if present — misleading |
| NewsAPI 500 | Keep cache / error | Same as offline fallback |
| Timeout | Keep cache / error | Offline fallback if cache |
| Malformed JSON | Error or keep cache | Exception → fallback if cache; else error |
| Gemini unavailable | Feature error; feed works | Friendly `AiError` paths — OK |
| Gemini quota | Quota message | `QuotaExceeded` friendly copy — OK |
| Empty success list | Keep prior cache | **Wipes cache** — bad |
| Search offline | Cache or error | **Always network**; errors — no offline search |

---

## 112. Concurrency Matrix

| Scenario | Behavior |
|---|---|
| Simultaneous same AI cache key | Single remote fetch (tested) |
| First caller cancel | Underlying job continues; cache still filled |
| Network refresh overlap | `fetchJob` cancel + stale guards — mostly solid |
| Rapid navigation / reader | Reader job not cancelled — risk |
| WorkManager + foreground | Separate; free-tier skip avoids double NewsAPI from worker |
| TTS lifecycle | Released in `onCleared`; stop on reader clear; race on rapid toggle possible but bounded |
| Pagination cancel | **Swallowed** (GN-AUD-012) |

---

## 113. Release Audit

| Gate | Result |
|---|---|
| Debug builds? | ✅ `assembleDebug` success |
| Release compiles? | ✅ with temp keystore |
| R8 runs? | ✅ `minifyReleaseWithR8` + resource shrink |
| Release signs? | ✅ only when `keystore.properties` present; ❌ clear fail otherwise |
| APK inspected? | ✅ aapt2/apksigner/strings/mapping |
| Release installs? | ❓ not on device in this environment |
| Release launches? | ❓ not exercised |
| Core workflow on device? | ❓ not exercised |

APK facts: `applicationId=com.aus.gemini01`, `minSdk=31`, `targetSdk=37`, `versionCode=1`, not debuggable (attribute absent), `allowBackup=true`, `MainActivity` exported + `newsapp` scheme, Maps key placeholder embedded, NEWS key embedded, Debug App Check registrar present.

---

## 114. Test Gap Report

**Existing:** 61 unit tests, 0 failures; 1 placeholder instrumentation test.

**Priority additions**

1. Room `4→5` migration + destructive-path documentation test  
2. `emptySuccess_doesNotWipeNonEmptyCache`  
3. `loadNextPage_rethrowsCancellation`  
4. `generateSmartThemes_usesSnapshotBeforeLoading`  
5. AI: 50 concurrent callers; cancel-other; failed inflight removed (extend `AiRepositoryTest`)  
6. Deep link decode / unknown host fuzz  
7. Release APK assertion: no Debug App Check registrar; model pin allowlist  
8. Adversarial NewsAPI fixtures + parser fuzz already partially covered — extend repository layer  

---

## 115. Top 10 Fixes (order)

1. Pin stable Gemini model ID; remove `-latest`; fix Settings label; consider Remote Config  
2. Resolve NewsAPI key threat model (proxy vs accepted demo risk + monitoring)  
3. Move `firebase-appcheck-debug` to `debugImplementation`; verify release APK  
4. Stop empty successful feeds from wiping Room cache  
5. Remove or strictly gate `fallbackToDestructiveMigration`; add migration tests + `exportSchema`  
6. Fix Smart Themes snapshot-before-Loading bug  
7. Rethrow `CancellationException` in `loadNextPage`; bind reader to cancellable job  
8. Add TTL/prune for `ai_results` (especially trends/locations/for_you)  
9. Harden chat multi-turn instructions / session policy  
10. Add CI: unit tests + lint + assembleDebug (+ optional minifyRelease with dummy keystore)

---

## 116. Patch Plan (suggested; **not implemented** in this audit)

**Patch 1 — Pin Gemini model**  
Files: `AiCacheKeys.kt`, `SettingsScreen.kt`, tests  
Risk: low; may invalidate caches via model string change (desired)

**Patch 2 — App Check debugImplementation**  
Files: `app/build.gradle.kts`  
Risk: low; verify debug builds still get debug provider

**Patch 3 — Preserve cache on empty success**  
Files: `NewsRepository.kt` + unit test  
Risk: low

**Patch 4 — Smart Themes snapshot**  
Files: `NewsViewModel.kt` + test  
Risk: low

**Patch 5 — Cancellation + reader job**  
Files: `NewsViewModel.kt`  
Risk: low–medium

**Patch 6 — Migration safety**  
Files: `AppDatabase.kt`, schema export, migration test  
Risk: medium (behavior change on corrupt DBs)

**Patch 7 — README / LICENSE / CI truth**  
Docs + workflow only  
Risk: none to runtime

---

## 117. Release Decision

### 🟠 NOT READY — SIGNIFICANT FIXES REQUIRED

**Minimum to advance to 🟡 READY AFTER MINOR FIXES:**

1. Pin production Gemini model (no `-latest`)  
2. App Check debug not in release artifact  
3. Empty-feed cache wipe fixed  
4. Destructive migration policy decided + 4→5 tested  
5. Documented NewsAPI key posture for the intended distribution channel  
6. Basic CI green on test + assembleDebug  
7. README corrected (SDK levels, reader-mode claim, LICENSE)

**Minimum to claim ✅ READY FOR PRODUCTION:** above plus Maps/NewsAPI/Firebase key restrictions verified in cloud consoles, release signing runbook, on-device release smoke of feed + one AI path + bookmark survive restart, and chat/reader concurrency fixes.

---

## Threat Model (condensed)

| Asset | Threat | Path | Mitigation now | Weakness | Fix |
|---|---|---|---|---|---|
| NewsAPI quota | Key theft | APK strings | gitignore only | Extractable | Proxy / rotate / monitor |
| Firebase AI quota | Abuse / injection loops | Client SDK | App Check + cache/dedupe | Debug registrar in release; `-latest` instability | Pin model; clean App Check |
| Bookmarks/history | Silent wipe | Destructive migration | 4→5 migration | Fallback still on | Remove fallback; tests |
| User trust | Hallucinated news | Gemini UI | “Generated by Gemini” labels + source WebView | Reader marketed as paywall strip | Honest docs |
| Offline UX | Cache clobber | Empty 200 | Partial | Empty wipe | Guard replace |

---

## Completeness Checklist

- [x] README  
- [x] Root / app Gradle / version catalog / wrapper  
- [x] Manifest / backup XML / google-services (redacted)  
- [x] `.gitignore` / `.githooks`  
- [x] ProGuard/R8 + release APK inspection  
- [x] Production Kotlin packages (data/ai/local/ui/worker)  
- [x] ViewModels / Compose screens / repos / Retrofit / models  
- [x] Room entities/DAOs/migrations  
- [x] DataStore / Firebase AI / App Check / AI cache / telemetry  
- [x] TTS / speech / maps / workers / notifications  
- [x] Unit + instrumentation inventory  
- [x] Git history for migrations + secrets  
- [ ] On-device install/launch (environment limitation)  
- [ ] Live Firebase/NewsAPI quota dashboards (external)

### Significant files not deep-inspected

- Binary mipmaps / screenshot PNGs (presence only)  
- `.idea/*` IDE metadata  
- Full R8 `mapping.txt` beyond App Check / seeds sampling  
- Third-party AAR consumer ProGuard contents (inferred via successful R8 + app rules)

### Files Audited (primary)

`README.md`, `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `gradle/wrapper/*`, `app/build.gradle.kts`, `app/google-services.json` (metadata), `app/src/main/AndroidManifest.xml`, `app/src/main/proguard/proguard-rules.pro`, `res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml`, `res/xml/shortcuts.xml`, `.gitignore`, `.githooks/pre-commit`,  
`MainActivity.kt`, `NewsViewModel.kt`, `NewsRepository.kt`, `NewsApiService.kt`, `NewsModels.kt`, `SettingsRepository.kt`,  
`AppDatabase.kt`, `*Entity.kt`, `NewsDao.kt`, `AiResultDao.kt`,  
`AiRepository.kt`, `AiCacheKeys.kt`, `AiError.kt`, `AiTelemetry.kt`, `AiResult.kt`,  
`AdaptiveNewsScreen.kt`, `NewsScreen.kt`, `ReadingModeScreen.kt`, `NewsChatScreen.kt`, `NewsMapScreen.kt`, `SettingsScreen.kt`, `MarkdownText.kt`, `AiResponseParsers.kt`, `ArticleWebView.kt`, `ImageUrl.kt`, `TtsManager.kt`, `VoiceRecognizer.kt`, `NotificationHelper.kt`,  
`NewsWorker.kt`, `ReminderWorker.kt`,  
all `app/src/test/**`, `ExampleInstrumentedTest.kt`

---

*End of forensic audit for commit `b86d9b6`.*
