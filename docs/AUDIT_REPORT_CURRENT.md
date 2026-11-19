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

## Validation and limitations

The unit, lint, and instrumented results below are from the earlier baseline. The Android debug build passed after adding the Spark-mode Maps fallback and again after making GDELT the keyless default and clearing development keys from release variants. A raw GDELT request returned valid JSON, then a subsequent request returned HTTP 429; the new Retrofit path was not exercised against the live endpoint. The Firebase function received a Node syntax check but was not deployed.

| Command | Result |
|---|---|
| `./gradlew.bat testDebugUnitTest` | PASS — 151 tests |
| `./gradlew.bat lintDebug` | PASS — reports an AGP update advisory |
| `./gradlew.bat :app:assembleDebug` | PASS — current Spark mode and keyless GDELT default |
| `./gradlew.bat connectedDebugAndroidTest` | PASS — 6 tests on API 36 emulator |
| `node --check functions/index.js` | PASS |
| `firebase functions:list --project com-aus-gemini01-d91c9` | PASS — no functions deployed |
| `firebase deploy --only firestore:rules --project com-aus-gemini01-d91c9` | PASS — default-deny rules deployed |
| `git diff --check` | PASS — no whitespace errors |

Firebase AI Logic App Check enforcement was verified as active. Under the Spark constraint, billing, Maps rendering, the proxy, and its durable rate limits intentionally remain inactive. The GDELT public API is rate-limited and did not return a successful response in the live check; its availability needs monitoring. Production signing identity and the publisher rights for any source content remain unresolved; see `PRODUCTION_NOTES.md`.
