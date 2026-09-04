# Cleanup report

**Base:** `main` @ `6d9b69d`  
**Cleanup branch:** `cursor/cleanup-unused-files-e91d`  
**Backup branch:** `cursor/backup-pre-cleanup-e91d` (identical to pre-cleanup `main`)  
**Date:** 2026-09-04 UTC

## Assumed inputs (user did not supply explicit lists)

| Input | Assumed value |
|---|---|
| Repo root | `/workspace` (`github.com/shareef01/gemini-news`) |
| Dependencies to preserve | Everything in `gradle/libs.versions.toml` + AGP/KSP/Google Services plugins — **none removed** |
| Avoid list | `.git/`, `.github/`, `LICENSE`, `README.md`, `docs/PRODUCTION_NOTES.md`, `gradle*/`, `app/src/main/**`, `app/schemas/**`, `app/google-services.json`, Room test schema assets, CI workflows |
| Policy | Conservative: prefer keeping unused files over false-positive deletes |

## Summary

| Metric | Count |
|---|---|
| Tracked files scanned | 131 |
| Removed | **2** |
| Manual review (kept) | 6 groups |
| Dependencies removed | 0 |

## Removed files

### 1. `docs/screenshots/04_news_map.png`

- **Reason:** Orphan documentation asset — not referenced by README, docs, code, tests, or CI.
- **Heuristics:** filename / path string search; README screenshot inventory.
- **Evidence:**

```text
$ rg -n '04_news_map|news_map\.png'   # → no matches

$ rg -n 'screenshots/' README.md
# embeds: 01_home_feed, 02_ai_summary, 03_reader_mode,
#         05_trending_topics, 04_ai_chat, 06_settings
# (no 04_news_map.png)
```

- **Commit:** `d746e77`

### 2. `app/src/test/java/com/aus/gemini01/ExampleUnitTest.kt`

- **Reason:** Android Studio template test (`assertEquals(4, 2 + 2)`); no product assertions; not cited in docs/CI by name.
- **Heuristics:** content inspection; reference search; test-suite value.
- **Evidence:**

```text
$ rg -n 'ExampleUnitTest|addition_isCorrect'
# hits only inside ExampleUnitTest.kt itself

# File body is the default AS template (2+2).
```

- **Note:** `ExampleInstrumentedTest` was **kept** — it is the package-name smoke test referenced in `docs/PRODUCTION_NOTES.md` and run by the GHA `instrumented` job.
- **Commit:** `d746e77`

## Manual review required (not deleted)

| Path | Why uncertain |
|---|---|
| `.githooks/pre-commit` | Secret-scan hook; not wired via `core.hooksPath` in tracked config, but useful if developers enable it locally |
| `.idea/planningMode.xml` | Cursor IDE planning state; may be accidental local noise |
| `.idea/deploymentTargetSelector.xml` | Per-machine device selection UI state |
| `.idea/appInsightsSettings.xml` | Android Studio App Insights UI prefs |
| Remaining tracked `.idea/*` | Common shared Android Studio project settings — deleting changes clone UX |
| Empty/future docs assets | No further orphan screenshots after this cleanup |

## Tests / build after cleanup

Commands run on this branch:

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

**Result:** `BUILD SUCCESSFUL` (unit tests + debug APK).
## How to revert

```bash
# Restore everything from backup tip
git checkout main
git reset --hard origin/cursor/backup-pre-cleanup-e91d

# Or revert only the cleanup commit on this branch
git revert d746e77

# Or restore individual files from backup
git checkout origin/cursor/backup-pre-cleanup-e91d -- \
  docs/screenshots/04_news_map.png \
  app/src/test/java/com/aus/gemini01/ExampleUnitTest.kt
```

## Machine-readable summary

See `docs/cleanup_deletions.json`.
