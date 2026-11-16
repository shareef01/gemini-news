# Gemini News Current Audit

## Metadata

- Date: 2026-11-16
- Branch: `main`
- Starting revision: `785cf41`
- Scope: static source, configuration, and documentation review

## Summary

The current branch is Room v7. This review found and fixed three issues: proxy requests did not attach the App Check token required by the documented backend, the Settings screen exposed regions that the configured NewsAPI plan does not serve, and the document previously named `AUDIT_REPORT_CURRENT.md` described a September 5 / Room v6 snapshot.

## Changes made

- App Check provider installation now runs from the application class, covering activity and WorkManager entry points. It initializes Firebase from packaged configuration when needed and skips App Check when no default app is configured. Proxy Retrofit requests attach `X-Firebase-AppCheck` and fail closed if a token cannot be obtained.
- Settings now offers only the enabled United States region, and unsupported country codes are normalized when saved. Production notes describe the currently enabled region and the Room schema's future multi-region capability.
- The September 5 report is retained as `AUDIT_REPORT_2026-09-05.md`; this file records the current review.

## Release readiness follow-up — 2026-11-17

- Firebase CLI can see the Firebase project, but listing functions returns `403 SERVICE_DISABLED` because the Cloud Functions API is disabled. There is no active `gcloud` account or configured proxy URL, so a backend cannot be deployed from this workspace yet.
- No Maps API key or production signing keystore is configured locally.
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

Firebase Console App Check enforcement, API key restrictions, and a deployed proxy were not available for live verification. This does not clear the release blockers listed in `PRODUCTION_NOTES.md`.
