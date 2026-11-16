# Gemini News Current Audit

## Metadata

- Date: 2026-11-17
- Branch: `codex/project-audit-hardening`
- Starting revision: `785cf41`
- Scope: static source, configuration, and documentation review

## Summary

The current branch is Room v7. This review found and fixed three issues: proxy requests did not attach the App Check token required by the documented backend, the Settings screen exposed regions that the configured NewsAPI plan does not serve, and the document previously named `AUDIT_REPORT_CURRENT.md` described a September 5 / Room v6 snapshot.

## Changes made

- App Check provider installation now runs from the application class, covering activity and WorkManager entry points. It initializes Firebase from packaged configuration when needed and skips App Check when no default app is configured. Proxy Retrofit requests attach `X-Firebase-AppCheck` and fail closed if a token cannot be obtained.
- Settings now offers only the enabled United States region, and unsupported country codes are normalized when saved. Production notes describe the currently enabled region and the Room schema's future multi-region capability.
- The September 5 report is retained as `AUDIT_REPORT_2026-09-05.md`; this file records the current review.

## Release readiness follow-up — 2026-11-17

- Authenticated access to the Firebase project was confirmed. The Cloud Functions API was enabled, but project billing is disabled and no billing account is available to the authenticated account. There is no proxy URL or backend source in this repository, so a proxy cannot yet be deployed.
- Firebase App Check and Firebase Vertex AI APIs are enabled. App Check reports `firebaseml.googleapis.com` (Firebase AI Logic) as **ENFORCED**; a Play Integrity configuration and one SHA-256 certificate are registered for the Android app. The fingerprint could not be matched because no production signing keystore is present locally.
- The Maps Android API is not enabled. No Maps API key or production signing keystore is configured locally. Android Maps key restrictions require the production certificate's SHA-1 fingerprint and package name.
- NewsAPI's public terms restrict the free Developer plan to development/testing and state that third-party content rights remain the owners' responsibility. The configured key's plan and any publisher-specific rights are not known.
- `keystore.properties.example` documents the expected local signing fields; no signing key or passwords were generated.
- Kotlin Gradle Plugin and KSP were updated to compatible current releases. The AGP 9.5.1 advisory remains deferred: Android requires Gradle 9.6.0 for AGP 9.5.x, and Kotlin's fully supported AGP range currently ends at 9.3.1. This project remains on AGP 9.3.1 / Gradle 9.5.0 rather than crossing that compatibility boundary.

## Validation and limitations

| Command | Result |
|---|---|
| `./gradlew.bat testDebugUnitTest` | PASS — 151 tests |
| `./gradlew.bat lintDebug` | PASS — reports an AGP update advisory |
| `./gradlew.bat assembleDebug` | PASS |
| `./gradlew.bat connectedDebugAndroidTest` | PASS — 6 tests on API 36 emulator |
| `git diff --check` | PASS — no whitespace errors |

Firebase AI Logic App Check enforcement was verified as active. The production signing fingerprint, Maps key restrictions, NewsAPI plan and publisher rights, and deployed proxy remain unverified or incomplete; the release blockers listed in `PRODUCTION_NOTES.md` therefore remain.
