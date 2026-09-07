# Gemini News Remediation Audit — 2026-09-07

## Metadata

- Audit date: 2026-09-07
- Inspected starting commit: `7f9b2487811c838222e152831ebf0f363d6dd082` (`main`)
- Working tree at start: clean
- Environment: Windows local workspace; Gradle 9.5.0

## Executive findings addressed

| ID | Severity | Finding | Status |
|---|---|---|---|
| AI-TTL-001 | P1 | Global AI prune used shortest TTL and deleted still-valid longer-TTL rows | FIXED |
| AI-CLR-001 | P1 | Clear Cache could be undone by late in-flight Room inserts | FIXED |
| AI-TEL-001 | P2 | Single-flight waiters each recorded AI errors | FIXED |
| WRK-CAN-001 | P2 | `NewsWorker` swallowed `CancellationException` into retry/failure | FIXED |
| DL-001 | P2 | Public `BROWSABLE` `newsapp://` intent filter | FIXED (removed; Option A) |
| DL-002 | P2 | Search path double-decoded via `URLDecoder` | FIXED |
| NET-FB-001 | P2 | Broad non-HTTP exception fallback treated local failures as offline | FIXED |
| MAP-ID-001 | P3 | Location protocol trusted model URL/title fields | FIXED |
| REG-001 | P3 | README implied multi-region while UI is US-only | FIXED (docs/copy honesty) |
| AI-LIFE-001 | P3 | `AiRepository` owns an ad-hoc `CoroutineScope` | DOCUMENTED follow-up |
| SEC-KEY-001 | Info | Client-embedded NewsAPI key extractable from APK | DOCUMENTED (portfolio + production blocker) |
| WEB-001 | P2 | WebView hardening | VERIFIED already strict |
| CI-001 | Low | Mutable GitHub Actions tags | FIXED (SHA pins) |

## Implementation summary

### Per-feature AI TTL pruning
- `AiResultDao.deleteOlderThanForKind(kind, cutoffEpochMs)` replaces global `deleteOlderThan`.
- `AiRepository.pruneExpired()` walks cacheable `AiFeature`s and applies each feature’s own TTL.
- Lookup TTL checks remain authoritative; prune failures are swallowed via `runCatching`.
- CHAT TTL remains `0` and is never persisted.

### Clear-cache invalidation barrier
- `AtomicLong cacheGeneration` + `Mutex persistMutex`.
- `clearCache()` increments generation then clears Room under the mutex.
- Single-flight owners capture generation before fetch; persist only if generation unchanged.
- Callers may still receive the live response; Room is not repopulated after clear.

### Single-flight failure telemetry
- Owner records miss / request / classified error exactly once.
- Waiters rethrow existing `AiRequestException` without re-recording.
- Cancellation is never classified as an AI error.

### Worker cancellation
- `NewsWorker` / `ReminderWorker` rethrow `CancellationException`.
- Pure `newsWorkerResultForFailure` seam for unit tests.
- Breaking-news periodic work uses exponential backoff (10 minutes).

### Deep links (Option A)
- Removed public `VIEW`/`BROWSABLE` `newsapp://` filter from the manifest.
- Shortcuts/notifications continue using explicit `MainActivity` intents.
- Parser no longer applies `URLDecoder`; expects Android Uri single-decode semantics.

### Offline fallback taxonomy
- Eligible: HTTP 5xx, genuine `IOException` (except missing key).
- Ineligible: 4xx, missing key, `NewsProviderException`, serialization, programmer/runtime errors.
- Provider `status: "error"` throws `NewsProviderException` (not `IOException`).

### Map location identity
- Prompt/protocol: `ARTICLE_INDEX|LOCATION_NAME|LAT|LONG` (0-based).
- Title/URL taken only from `sourceArticles[index]`.
- `AI_PROMPT_VERSION` bumped to `5`.

### Region
- Kept US-only; Settings/README/production notes no longer imply multi-country tuning.
- Multi-country cache identity deferred (would require Room migration).

### WebView (verified)
- JS off, file/content access off, mixed content blocked, SSL cancel, HTTPS navigation only,
  `safeArticleUrl` gate, no `JavascriptInterface`, Safe Browsing enabled where available.

## Regression tests added / extended

- `AiRepositoryTest.perFeaturePrune_preservesValidLongTtlRows`
- `AiRepositoryTest.clearCache_blocksInFlightPersist_TEST_A` … `TEST_D`
- `AiRepositoryTest.failedSharedRequest_50Waiters_recordsOneErrorAndCleansInflight`
- `AiRepositoryTest.cancellationIsNotRecordedAsAiError`
- `NewsWorkerPolicyTest` cancellation classification seam
- `DeepLinkParseTest` plus/`%25`/unicode/emoji/oversize/host cases
- `FeedCachePolicyTest` / `NewsRepositoryTest` offline taxonomy
- `AiResponseParsersTest` index-based location protocol cases
- `AiCacheKeysTest` prompt version `5`

## Verification commands

Recorded during this remediation (placeholders only; no real credentials):

| Command | Result |
|---|---|
| Pre-edit `./gradlew.bat testDebugUnitTest` at `7f9b248` | PASS (baseline; clean tree) |
| Post-edit `./gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleRelease` | PASS — **142** unit tests, **0** failures/errors; lintDebug PASS; assembleDebug PASS; assembleRelease PASS |
| `connectedDebugAndroidTest` | NOT RUN — no reliable emulator/device attached in this session; CI instrumented job remains the device gate |

## Known limitations

- Live Gemini / NewsAPI / Maps calls not exercised.
- Instrumentation / emulator may be unavailable on this host; CI covers API 34 emulator.
- TalkBack / foldable / voice runtime remain environment-blocked.
- NewsAPI key remains client-embedded under the portfolio threat model.

## Deferred improvements

- Inject application/process scope into `AiRepository` instead of constructing `SupervisorJob`+`IO`.
- Multi-country feed cache identity if product expands beyond US.
- Broader repository cancellation/malformed-payload integration matrix.
- Dependabot for SHA-pinned Actions; optional Gradle dependency verification.
- Decompose `NewsViewModel` (non-blocking maintainability).

## Production threat-model notes

- Portfolio/personal distribution: client `NEWS_API_KEY` extractability is accepted and documented.
- Consumer/public distribution blocker: server-side NewsAPI proxy with auth/attestation, rate limits,
  abuse controls, secret storage, and observability is required before treating the key as protected.
- Maps keys: Android package + signing-cert restrictions required; App Check does not cover NewsAPI.
- Firebase `google-services.json` client identifiers are not private server secrets.
