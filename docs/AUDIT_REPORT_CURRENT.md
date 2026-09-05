# Gemini News Current Audit

## Metadata

- Date: 2026-09-05
- Branch: `main`
- Starting revision: `80e04e3`
- Environment: Windows workspace; Android SDK/Gradle available; one connected Android device available for instrumentation

## Executive summary

The repository was clean at the start and already contained focused hardening from earlier audit work: fail-closed NewsAPI configuration, explicit Room 4→5 migration, AI single-flight requests, notification permission handling, deep-link parsing, and certificate-error rejection in WebView. The continued pass adds Room 5→6 cache timestamps, safe article notification deep links, and stable concurrent telemetry behavior. The highest-value improvements make AI cache identity follow actual article evidence, constrain deep links, remove unnecessary WebView JavaScript, sanitize chat turns and bound prompt fields, prevent raw network exception text from reaching users, and clarify that generated reader content, tone, trends, map locations, and chat answers are interpretations rather than publisher reporting.

No production architecture or dependency stack was replaced. Changes are intentionally small and covered by unit tests.

## Current architecture verified

- Compose UI with an adaptive `ListDetailPaneScaffold` shell in `AdaptiveNewsScreen`.
- `NewsViewModel` owns feed, search, AI feature state, chat, voice, TTS, bookmarks, history, and background-facing settings.
- Retrofit/OkHttp calls NewsAPI through an injectable `NewsApiService` boundary; `NewsRepository` filters unusable `[Removed]`/blank URL rows and falls back to Room only for page-one network/5xx failures.
- Room v6 stores bookmarks, history, cached feeds with fetch timestamps, and `ai_results`; shipped migrations are explicit 4→5 and 5→6.
- `AiRepository` provides persistent TTL-based results and per-key in-flight sharing. Chat is deliberately uncached.
- DataStore stores region, language, notification/reminder toggles, free-tier saver state, and last-notified URL.
- WorkManager schedules unique hourly breaking-news work and daily reminders.

## Baseline

| Command | Result | Notes |
|---|---|---|
| `./gradlew.bat testDebugUnitTest` | PASS | Existing suite was green before edits; Gradle reported the task up to date |
| `git status --short` | PASS | Clean at start |
| `git branch --show-current` | PASS | `main` |
| `git log --oneline -10` | PASS | Starting revision recorded above |

Lint, assemble, and device instrumentation are recorded in Final validation after the changes.

## Findings summary

| ID | Severity | Area | Finding | Status |
|---|---|---|---|---|
| NEWS-001 | P1 | Provenance | Reader and tone UI could make generated transformations look like source reporting. | FIXED |
| NEWS-002 | P2 | Freshness | AI cache keys used URL identity for article-specific results; changed feed fields could reuse old prose. | FIXED |
| NET-001 | P2 | Errors | Generic network/HTTP failures exposed localized exception text or HTTP detail. | FIXED |
| AI-001 | P1 | Prompt safety | Later chat turns inserted raw user text into the model prompt. | FIXED |
| AI-002 | P2 | Context size | Unbounded source fields could consume model context. | FIXED |
| AI-003 | P2 | Telemetry | Concurrent cache misses could race a non-thread-safe telemetry sink and overcount misses. | FIXED |
| AI-004 | P2 | Lifecycle | Repeated summary/reader requests could let an older job clear the newer job's loading state. | FIXED |
| SEC-001 | P2 | WebView | JavaScript was enabled for publisher pages without an app feature requiring it. | FIXED |
| SEC-002 | P2 | Deep links | Arbitrary category names and oversized search values were accepted. | FIXED |
| MAP-001 | P2 | Map | Coordinates are model-inferred and should not be read as verified event coordinates. | FIXED |
| DATA-001 | P2 | Cache | Feed cache pruning and freshness were based on publisher `publishedAt` strings, not a dedicated fetch timestamp. | FIXED |
| VOICE-001 | P2 | Voice | Runtime speech-recognizer behavior requires device/emulator validation. | BLOCKED |
| VOICE-002 | P2 | Voice | Rapid microphone taps could issue duplicate recognizer starts. | FIXED |

## News integrity / provenance

Feed cards show publisher name, publication age, title, description, and a Web action. `[Removed]`, blank titles, and blank URLs are filtered. Page-one cache replacement refuses empty usable payloads, and offline results are labeled in the feed as cached headlines. A future publisher timestamp is now labeled `Future timestamp` instead of appearing current.

Reader Mode is a Gemini transformation of NewsAPI title/description/content, not downloaded publisher HTML and not a paywall bypass. The reader subtitle and footer now state that origin. Sentiment is rendered as `AI-assessed tone`, reducing the chance that a probabilistic interpretation is mistaken for an editorial fact.

Cached feed rows now store a separate fetch timestamp through Room migration 5→6. The offline banner can distinguish cached origin from publication time and the repository prunes by fetch age rather than lexicographic publisher timestamps.

## NewsAPI / networking

The repository uses bounded OkHttp timeouts, fails closed when `NEWS_API_KEY` is blank, preserves cache on empty usable responses, and does not use offline fallback for 4xx responses (including 429). Search is debounced in the ViewModel but is not persisted for offline use. JSON tolerance is intentionally described as tolerance, not a guarantee that malformed payloads are trustworthy.

## AI / Gemini

Article prompts delimit third-party fields as untrusted data, explicitly prohibit following embedded instructions, and prohibit inventing facts. Prompt fields are bounded to 12,000 characters and visibly marked as truncated. Subsequent chat turns now sanitize user input before prompt construction; chat UI explains that answers may combine loaded-feed context with general Gemini knowledge.

Article summary, reader, trend, theme, and location cache keys now include a SHA-256 fingerprint of the source title/description/content in addition to model, prompt version, feature, URL, and language where applicable. This prevents same-URL content updates from reusing stale generated output.

AI failures are classified and never persisted as successful results. Model calls are time-bounded and cancellation-aware. A connected runtime test of live Gemini behavior was not possible without treating external credentials and billing as test dependencies.

## AI caching / deduplication

`AiRepository` uses Room TTLs and a per-key `ConcurrentHashMap` of shared Deferred requests. Chat bypasses the cache. Existing concurrency tests cover 50 identical callers and failed-request cleanup; concurrent misses now record one diagnostic miss. Summary and Reader generation tokens prevent older jobs from clearing newer loading state. Cache pruning is opportunistic and feature TTLs are shorter for trends and locations.

## Feed / navigation

The adaptive shell keeps list/detail navigation in Compose state and guards stale reader responses by article URL. Category/search requests cancel competing work. Deep-link parsing now accepts only supported categories and caps decoded search input at 200 characters; malformed schemes and encodings are ignored.

## Reader / Markdown

Markdown rendering supports headings, lists, quotes, dividers, bold spans, and sentiment blocks. Reader typography has a max reading measure and font-size control. TTS strips common Markdown markers and chunks long text. Full large-font and device TTS synchronization remain runtime validation items.

## Chat

Chat keeps a bounded first-turn headline context (15 headlines) and does not claim that every response is current reporting. The UI now labels the source boundary as feed context plus Gemini knowledge and points users toward publisher sources for important claims. Conversation history is in-memory and is reset when the preferred language changes.

## Trends

Trends are generated from the currently loaded headlines, capped at 15 articles, cached for six hours, and now keyed by evidence fingerprints. The prompt describes the output as narratives represented in the available feed rather than objective global importance.

## Map

Location parsing validates coordinate ranges and only opens an article when the returned URL matches an article in the loaded feed. The map UI labels results as “AI-inferred mentions • approximate locations,” reducing the chance that inferred coordinates are mistaken for verified event locations. Runtime behavior with missing Maps configuration remains unverified.

## Personalization

For You is derived from local bookmarks/history and Gemini-generated keywords, with a cold-start empty state. No account or remote reading-history sync was found. The weekly personality feature is local-history based and its prompt now prohibits political-identity or sensitive-trait inference.

## Voice / TTS

Permission request and recognizer availability paths are present; callbacks reset listening state, rapid starts are suppressed, and failures surface through a snackbar event. TTS requests audio focus, stops on focus loss, chunks long content, and releases on ViewModel clearance. Device behavior, locale voice availability, and accessibility announcements remain blocked without dedicated runtime coverage.

## Database / offline

Room v6 has explicit additive 4→5 and 5→6 migrations and no destructive fallback. Bookmarks/history are local and history is trimmed to 50 entries transactionally. Feed page-one network/5xx failures can show cached rows with an explicit offline banner and cache age; page-two failures do not fabricate or replace the visible page. Search, uncached AI, live WebView content, and map data are unavailable offline unless already represented by local state/cache.

## Workers / notifications

Breaking-news work is unique, network-constrained, quota-saver aware, and does not advance `lastNotifiedUrl` when notification permission prevents delivery. Notifications use immutable, update-current PendingIntents with an ID and now carry a validated article URL deep link. The app resolves that URL against loaded NewsAPI/Room data before opening the publisher WebView, preventing arbitrary notification URLs from being loaded.

## Security

NewsAPI and Maps keys are sourced from ignored local properties/environment and are still extractable from an APK by design under the documented personal/portfolio threat model. Release App Check uses Play Integrity; debug uses the debug provider. WebView now loads only structurally valid HTTP(S) article URLs upgraded to HTTPS, rejects credential-bearing URLs, disables JavaScript and DOM storage, rejects mixed content, and cancels SSL errors.

## Privacy

Reading history, bookmarks, and AI diagnostic counters are local. No analytics or account/cloud sync was added. Settings exposes clear-cache and clear-history actions separately; user-facing diagnostics should be read as local counters, not remote telemetry.

## UI/UX

The feed remains visually primary: article cards lead with headline/source/time and keep AI actions secondary. Generated surfaces use restrained AI labels. Offline status is explicit. Chat and Reader now state their source boundaries.

## Accessibility

Interactive icons have content descriptions in the inspected surfaces, touch targets use shared sizing, and voice listening has a stop action plus snackbar errors. Full TalkBack, 2x font scaling, contrast, and reduced-motion validation require a device/emulator and remain unverified.

## Adaptive layouts

The code uses Material 3 Adaptive list/detail navigation and constrains reader measure. Instrumentation ran on a connected Pixel 7; foldable/tablet runtime was not available, so split-screen, fold transitions, and large-font layout behavior are not claimed as validated.

## Performance

Feed pagination uses stable URL keys and deduplicates overlapping pages. AI requests are shared per cache key, large prompt fields are bounded, and AI cache pruning is opportunistic. Map marker count is capped at the 15 articles analyzed by the ViewModel.

## Tests added

- URL validation rejects malformed and credential-bearing URLs while preserving intentional HTTP→HTTPS normalization.
- Deep links reject unknown categories and oversized search input.
- AI evidence fingerprints prove summary/reader cache invalidation when source fields change.
- Prompt-field sanitization proves oversized source input is bounded.
- Room migration tests prove 5→6 cache timestamps are added and backfilled.
- AI single-flight tests prove concurrent waiters produce one request and one cache-miss diagnostic.
- NewsAPI repository tests use the injected service boundary to verify query/key forwarding, unusable-row filtering, and user-facing error sanitization without network access.

## Final validation

| Command | Result | Notes |
|---|---|---|
| `./gradlew.bat testDebugUnitTest` | PASS | 113 unit tests passed after final changes |
| `./gradlew.bat lintDebug` | PASS | Debug lint completed successfully |
| `./gradlew.bat assembleDebug` | PASS | Debug APK packaged successfully |
| `./gradlew.bat connectedDebugAndroidTest` | PASS | 2 instrumented tests passed on connected Pixel 7 (API 36) |
| Debug APK install + launch smoke | PASS | Installed on connected Pixel 7; `MainActivity` resumed and the UI accessibility tree was available; no app crash was logged |

## Remaining P0/P1 issues

No P0 issue was verified. No unresolved P1 implementation defect was confirmed in the inspected code. Live AI, Maps, notification delivery, and adaptive runtime behavior remain environment-blocked rather than proven correct.

## Environment limitations

- No authenticated live Gemini or NewsAPI calls were made.
- Instrumentation and a debug APK launch smoke ran on a connected Pixel 7; the suite currently covers package smoke and Room migration. TalkBack, WebView, TTS, voice, Maps, notification delivery, and foldable/tablet behavior were not covered by existing instrumented tests.
- The documented personal/portfolio key threat model remains unchanged; APK key extraction is still possible.

## Deferred improvements

- Extend the now-injectable network/AI boundaries with broader repository-level malformed-payload, offline fallback, and cancellation integration tests.

## Before / After Assessment

Scores are code-and-test confidence estimates, not claims of production readiness.

| Dimension | Before | After | Movement |
|---|---:|---:|---:|
| News trust/provenance | 6 | 8 | Clearer generated/source boundaries and cache-age labeling |
| Product UX | 7 | 8 | Better offline age, chat context, map, reader, and notification flows |
| Visual design | 7 | 8 | More restrained AI and sentiment language |
| Accessibility | 6 | 6 | Reviewed, but broad TalkBack/large-font validation remains limited |
| Reliability | 7 | 8 | Additive migration, deep-link, and concurrency coverage |
| Offline resilience | 6 | 8 | Durable cache timestamps and explicit cached-age messaging |
| AI correctness | 6 | 8 | Evidence-aware cache invalidation and bounded/sanitized prompts |
| Security/privacy confidence | 7 | 8 | Reduced WebView surface and validated external inputs |
| Performance | 7 | 8 | Shared AI work no longer races telemetry or inflates miss counts |
| Test confidence | 7 | 8 | 113 unit tests plus 2 connected-device tests pass |
