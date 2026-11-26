# Gemini News Current Audit

## Metadata

- Date: 2026-11-17
- Branch: `codex/project-audit-hardening`
- Starting revision: `785cf41`
- Scope: static source, configuration, and documentation review

## Summary

The current branch is Room v7. This review fixed the proxy-token gap, restricted Settings to the enabled US region, corrected the stale audit snapshot, and prepared an optional Firebase HTTPS proxy with Firestore-backed usage limits. Per the user's plan constraint, the app now defaults to GDELT's keyless news metadata API and remains on Firebase Spark; the proxy and Google Maps billing services are inactive.

## Changes made

- App Check provider installation now runs from the application class, covering activity and WorkManager entry points. It initializes Firebase from packaged configuration when needed and skips App Check when no default app is configured. Proxy Retrofit requests attach a fresh limited-use `X-Firebase-AppCheck` token and fail closed if a token cannot be obtained.
- Proxy Retrofit requests also include a Firebase Installation ID for throttling; the backend stores only an HMAC of that installation ID.
- A Firebase Functions v2 HTTPS proxy now allowlists the app's US headlines and search endpoints, checks the configured Firebase app ID, sends the NewsAPI secret in an `X-Api-Key` header, sanitizes responses, enforces/consumes App Check tokens, and caps function instances/concurrency. Firestore transactions enforce per-installation limits and a configurable global daily provider cap; only an HMAC of the Firebase Installation ID is stored.
- The Android proxy client now sends a Firebase Installation ID header for rate limiting. Firestore rules default to denying mobile/web SDK access; only the dedicated function runtime service account can access the counter data through IAM.
- Settings now offers only the enabled United States region, and unsupported country codes are normalized when saved. Production notes describe the currently enabled region and the Room schema's future multi-region capability.
- Builds without a Maps API key show inferred locations and article links in a list instead of initializing Google Maps, keeping the Spark build usable without Maps billing.
- Builds default to GDELT, serialize outbound requests at least five seconds apart, cache query results for five minutes, cap each query at 75 records, display required GDELT attribution, and omit article descriptions, body text, and publisher images. Release variants clear local NewsAPI and Maps keys.
- The September 5 report is retained as `AUDIT_REPORT_2026-09-05.md`; this file records the current review.

## Release readiness follow-up — 2026-11-17

- Authenticated access to the Firebase project was confirmed. Cloud Functions, Firebase App Check, Firebase Vertex AI, Firestore, and Maps Android APIs are enabled. A delete-protected Standard Firestore default database was created in `europe-west3`, with default-deny rules deployed; the dedicated runtime service account has App Check Token Verifier and Datastore User roles. The project remains on Spark per the user's instruction, so no function is deployed and the NewsAPI secret is not set.
- Firebase App Check and Firebase Vertex AI APIs are enabled. App Check reports `firebaseml.googleapis.com` (Firebase AI Logic) as **ENFORCED**; a Play Integrity configuration and one SHA-256 certificate are registered for the Android app. The proxy enforces App Check and consumes limited-use tokens to reject replay. The registered fingerprint could not be matched because no production signing keystore is present locally.
- Google Maps is intentionally not configured for the Spark build; the map screen uses a location list fallback.
- Optional proxy source allowlists the two client endpoints, US region, supported categories, and page sizes up to 20; it stores the upstream API key as a bound Secret Manager secret and strips unused response fields. Its per-installation and global request limits are inactive because the function is not deployed. Firestore TTL is not enabled. Proxy-specific privacy disclosures apply only if this optional backend is deployed later.
- The GDELT Project permits use of its datasets with citation and a link to the project; the app displays metadata and publisher links only. Publisher page content remains subject to publisher rights. The optional NewsAPI Developer provider is limited to development/testing.
- `keystore.properties.example` documents the expected local signing fields; no signing key or passwords were generated.
- Kotlin Gradle Plugin and KSP were updated to compatible current releases. The AGP 9.5.1 advisory remains deferred: Android requires Gradle 9.6.0 for AGP 9.5.x, and Kotlin's fully supported AGP range currently ends at 9.3.1. This project remains on AGP 9.3.1 / Gradle 9.5.0 rather than crossing that compatibility boundary.

## Audit follow-up — 2026-11-19

- GDELT search fallback now preserves the original fetch timestamp after HTTP 429 and marks stale in-memory results in the UI. The fallback no longer renews its own five-minute freshness window.
- Clearing the saved headline and AI cache no longer triggers an immediate feed request that repopulates the saved headline cache. The settings copy now explains that visible stories remain until the user refreshes.
- Article image URLs receive structural HTTPS validation, and Coil uses a dedicated OkHttp DNS guard that rejects private/local destinations for each host resolution, including redirects.
- CI now grants the workflow token `contents: read` only. AI evidence documentation now accurately states that generated summary/reader text is unverified and the lexical parser is not connected to those flows.
- `npm audit` reported no Node dependency advisories. OSV queries against 252 resolved Maven runtime coordinates returned no advisories. This does not replace future scans or review of external Firebase, Play Integrity, and GitHub settings.
- No unit tests, lint, instrumentation, or app build were rerun after this follow-up. Gradle dependency graph resolution succeeded; AGP emitted its existing warning that `android.disallowKotlinSourceSets=false` is experimental.
- Release signing, Play Integrity certificate matching, privacy disclosures, and publisher/source rights remain external gates for distributing the Android app binary; they do not block publishing the source repository on GitHub.

## Audit & remediation follow-up — 2026-11-26

- Deduplicated article URLs across GDELT, NewsAPI/proxy feed & search paths, and the Cloud Function proxy (`proxyPolicy.js`) so duplicate provider URLs cannot trigger `LazyColumn` key collisions.
- Fixed "For You" and short-keyword GDELT search queries (`gdeltSearchQuery` / `formatMultiTopicSearchQuery`), upgraded GDELT `http://` links to validated `https://`, and surfaced actionable "For You" error states instead of silently switching tabs to `"general"`.
- Guarded `"smart"` (Smart Themes) and partial first pages in `FeedManager.loadMore` so small theme buckets no longer trigger rogue page-2 fetches.
- Unified external publisher links on Android Custom Tabs (`ArticleNavigator`), defaulted metadata-only GDELT cards to Custom Tabs (`Open Story`), restored Reader Mode across process death, and replaced hardcoded NewsAPI labels in `ReadingModeScreen`.
- Narrowed `gdeltCacheMutex` to in-memory reads/writes with a `feedCacheGeneration` barrier so 5s–60s GDELT throttles and HTTP calls never block cache hits or `clearCache()`.
- Upgraded Room to v8 (`MIGRATION_7_8`) with `feedOrder` on `cached_articles`, `savedAt` on `articles`, and an `(kind, createdAt)` index on `ai_results`; synced `6.json`–`8.json` test assets and extended Robolectric + `MigrationTestHelper` coverage.
- Added `preferredLanguage` to `AiCacheKeys.themes`, retained loaded feed headlines across multi-turn `ChatManager` conversations, isolated per-feature jobs/generations in `InsightsManager`, lazy-started single-flight jobs in `AiRepository`, guarded `DataStoreAiTelemetry` writes with `runCatching`, and hardened `sanitizeForPrompt` against nested/case-variant `[[DATA]]` delimiters.
- Shared process-scoped repositories in `GeminiNewsApplication`, gated hourly `NewsWorker` scheduling on `!newsApiFreeTier`, fixed `TtsManager.speak` state reporting, added TTS and Custom Tabs `<queries>` to `AndroidManifest.xml`, extracted testable `functions/proxyPolicy.js` with `node:test` coverage, and aligned GitHub Actions CI on JDK 25 + a Node 22 Cloud Functions job.

## Validation and limitations

The unit, lint, and APK build checks below were executed after the 2026-11-26 remediation pass (`connectedDebugAndroidTest` remains covered by CI / attached hardware).

| Command | Result |
|---|---|
| `./gradlew.bat testDebugUnitTest` | PASS — 162 tests, 0 failures |
| `./gradlew.bat compileDebugAndroidTestKotlin` | PASS |
| `./gradlew.bat lintDebug` | PASS — 0 non-dependency code issues |
| `./gradlew.bat :app:assembleDebug :app:assembleRelease` | PASS — R8 release APK built |
| `npm --prefix functions run lint && npm --prefix functions test` | PASS — 3/3 Node tests |
| `npm --prefix functions audit` | PASS — 0 vulnerabilities |
| `git diff --check` | PASS — no whitespace errors |

Firebase AI Logic App Check enforcement was verified as active. Under the Spark constraint, billing, Maps rendering, the proxy, and its durable rate limits intentionally remain inactive. The GDELT public API is rate-limited and did not return a successful response in the live check; its availability needs monitoring. Production signing identity and the publisher rights for any source content remain unresolved; see `PRODUCTION_NOTES.md`.
