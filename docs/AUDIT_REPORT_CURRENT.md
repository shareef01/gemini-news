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

## Validation and limitations

| Command | Result |
|---|---|
| `./gradlew.bat testDebugUnitTest` | PASS — 151 tests |
| `./gradlew.bat lintDebug` | PASS — reports an AGP update advisory |
| `./gradlew.bat assembleDebug` | PASS |
| `./gradlew.bat connectedDebugAndroidTest` | PASS — 6 tests on API 36 emulator |
| `git diff --check` | PASS — no whitespace errors |

Firebase Console App Check enforcement, API key restrictions, and a deployed proxy were not available for live verification. This does not clear the release blockers listed in `PRODUCTION_NOTES.md`.
