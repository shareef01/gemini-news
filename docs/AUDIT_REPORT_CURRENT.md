# Gemini News Current Audit

## Metadata

- Date: 2026-11-17
- Branch: `codex/project-audit-hardening`
- Starting revision: `785cf41`
- Scope: static source, configuration, and documentation review

## Summary

The current branch is Room v7. This review fixed the proxy-token gap, restricted Settings to the enabled US region, corrected the stale audit snapshot, and prepared an optional Firebase HTTPS proxy with Firestore-backed usage limits. The project remains on Firebase Spark; the active build does not use the proxy or Google Maps billing services.

## Changes made

- App Check provider installation now runs from the application class, covering activity and WorkManager entry points. It initializes Firebase from packaged configuration when needed and skips App Check when no default app is configured. Proxy Retrofit requests attach a fresh limited-use `X-Firebase-AppCheck` token and fail closed if a token cannot be obtained.
- Proxy Retrofit requests also include a Firebase Installation ID for throttling; the backend stores only an HMAC of that installation ID.
- A Firebase Functions v2 HTTPS proxy now allowlists the app's US headlines and search endpoints, checks the configured Firebase app ID, sends the NewsAPI secret in an `X-Api-Key` header, sanitizes responses, enforces/consumes App Check tokens, and caps function instances/concurrency. Firestore transactions enforce per-installation limits and a configurable global daily provider cap; only an HMAC of the Firebase Installation ID is stored.
- The Android proxy client now sends a Firebase Installation ID header for rate limiting. Firestore rules default to denying mobile/web SDK access; only the dedicated function runtime service account can access the counter data through IAM.
- Settings now offers only the enabled United States region, and unsupported country codes are normalized when saved. Production notes describe the currently enabled region and the Room schema's future multi-region capability.
- Builds without a Maps API key show inferred locations and article links in a list instead of initializing Google Maps, keeping the Spark build usable without Maps billing.
- The September 5 report is retained as `AUDIT_REPORT_2026-09-05.md`; this file records the current review.

## Release readiness follow-up — 2026-11-17

- Authenticated access to the Firebase project was confirmed. Cloud Functions, Firebase App Check, Firebase Vertex AI, Firestore, and Maps Android APIs are enabled. A delete-protected Standard Firestore default database was created in `europe-west3`, with default-deny rules deployed; the dedicated runtime service account has App Check Token Verifier and Datastore User roles. The project remains on Spark per the user's instruction, so no function is deployed and the NewsAPI secret is not set.
- Firebase App Check and Firebase Vertex AI APIs are enabled. App Check reports `firebaseml.googleapis.com` (Firebase AI Logic) as **ENFORCED**; a Play Integrity configuration and one SHA-256 certificate are registered for the Android app. The proxy enforces App Check and consumes limited-use tokens to reject replay. The registered fingerprint could not be matched because no production signing keystore is present locally.
- Google Maps is intentionally not configured for the Spark build; the map screen uses a location list fallback.
- Optional proxy source allowlists the two client endpoints, US region, supported categories, and page sizes up to 20; it stores the upstream API key as a bound Secret Manager secret and strips unused response fields. Its per-installation and global request limits are inactive because the function is not deployed. Firestore TTL is not enabled. Proxy-specific privacy disclosures apply only if this optional backend is deployed later.
- NewsAPI's public terms restrict the free Developer plan to development/testing and state that third-party content rights remain the owners' responsibility. The configured key's plan and any publisher-specific rights are not known.
- `keystore.properties.example` documents the expected local signing fields; no signing key or passwords were generated.
- Kotlin Gradle Plugin and KSP were updated to compatible current releases. The AGP 9.5.1 advisory remains deferred: Android requires Gradle 9.6.0 for AGP 9.5.x, and Kotlin's fully supported AGP range currently ends at 9.3.1. This project remains on AGP 9.3.1 / Gradle 9.5.0 rather than crossing that compatibility boundary.

## Validation and limitations

The unit, lint, and instrumented results below are from the earlier baseline. The Android debug build also passed after adding the limited-use token and installation-ID headers and again after adding the Spark-mode Maps fallback. The Firebase function received a Node syntax check but was not deployed or exercised against NewsAPI.

| Command | Result |
|---|---|
| `./gradlew.bat testDebugUnitTest` | PASS — 151 tests |
| `./gradlew.bat lintDebug` | PASS — reports an AGP update advisory |
| `./gradlew.bat :app:assembleDebug` | PASS — current Spark mode and Maps list fallback |
| `./gradlew.bat connectedDebugAndroidTest` | PASS — 6 tests on API 36 emulator |
| `node --check functions/index.js` | PASS |
| `firebase functions:list --project com-aus-gemini01-d91c9` | PASS — no functions deployed |
| `firebase deploy --only firestore:rules --project com-aus-gemini01-d91c9` | PASS — default-deny rules deployed |
| `git diff --check` | PASS — no whitespace errors |

Firebase AI Logic App Check enforcement was verified as active. Under the Spark constraint, billing, Maps rendering, the proxy, and its durable rate limits intentionally remain inactive. The NewsAPI plan and publisher rights, production signing identity, and an appropriate provider path for public distribution remain unresolved; see `PRODUCTION_NOTES.md`.
