# Production Notes & Release Readiness Checklist

> [!CAUTION]
> **PROJECT STATUS: IN DEVELOPMENT / PRE-RELEASE — NOT RELEASE READY**
> This application is currently an active engineering prototype and portfolio project. It is **NOT** release-ready for public consumer distribution or Google Play Store publication. The hardening described in this document represents developer-preview quality; the checklist below must be completed before any production release.

## Active Plan Constraint: Firebase Spark

Keep this Firebase project on the Spark plan; do not link a Cloud Billing account. The Firebase AI Logic client uses the default Gemini Developer API backend, and the selected Gemini Flash model has a no-billing free tier subject to provider quotas and model availability. The default news source is the keyless GDELT DOC API. Cloud Functions and the Secret Manager-backed NewsAPI proxy are unavailable on Spark, so the prepared `functions/` code is not the active news path. Google Maps rendering is disabled without a billing-backed Maps key; the app shows AI-inferred locations in a list instead. A local `NEWS_API_KEY` can be selected for development only and is never included in a release build.

The GDELT Project permits use of its datasets without charge, including commercial use, provided use includes a citation and link to the GDELT Project ([terms](https://gdeltproject.org/about.html#termsofuse)). The DOC API returns article metadata and links; underlying publisher pages remain subject to their own content rights. This build shows GDELT attribution and does not copy article text, descriptions, or images. Search phrases are sent to GDELT's API. Its public API is rate-limited, so the app serializes calls, spaces them by at least five seconds, waits a minute after HTTP 429, caches query results for five minutes, reuses recent search results during throttling, and caps each query at 75 records.

While staying on Spark, NewsAPI remains a development-only option. Its Developer plan is restricted to development and testing ([pricing](https://newsapi.org/pricing)); it cannot be used in a staging or production app. The release build defaults to GDELT and never includes the NewsAPI key.

## 🛑 Production Release Readiness Blockers

The following mandatory steps must be completed before this app can be published for production:

1. **News Provider for Distribution:** The release build uses GDELT's keyless metadata API with required attribution and links to publisher pages. Confirm current GDELT API availability and publisher-content rights for the planned presentation. Do not switch to the NewsAPI Developer plan for distribution; its terms limit that plan to development and testing.
2. **Firebase App Check Signing Identity:** App Check enforcement is already active for Firebase AI Logic (`firebaseml.googleapis.com`), and Play Integrity is configured for the Android app. Before release, verify the registered certificate fingerprint matches the Play app-signing certificate used by production builds.
3. **Maps Feature:** Google Maps rendering is intentionally disabled while the project remains on Spark. The map screen falls back to a list of inferred locations and article links, so a Maps API key is not needed for this mode.
4. **Production Keystore Signing:** Configure Google Play App Signing and manage release upload keys outside source control. Copy `keystore.properties.example` to the ignored `keystore.properties`, create or provide the upload keystore under the ignored `keystore/` directory, and fill in the local credentials. No production signing key is included in this repository.
5. **License & Terms of Service Compliance:** Confirm the NewsAPI account plan and publisher-content rights before distributing the app. NewsAPI's [Developer plan is limited to development and testing](https://newsapi.org/pricing). Its [terms](https://newsapi.org/terms) say third-party content may remain subject to intellectual-property rights and may not be used without permission or another legal basis; verify rights for any publisher text and images displayed or sent for AI processing.
6. **Privacy Disclosure:** Before distribution, prepare the app privacy notice and Google Play Data safety disclosure for the actual services enabled in the released build, including that search phrases are sent to GDELT and prompts/headline metadata are sent to Gemini. The optional Firebase proxy's installation-ID rate-limit data applies only if that backend is deployed later.

## NewsAPI key model & Backend Proxy Architecture

For production deployments, client-side secret embedding is strictly avoided:
* `NEWS_PROXY_BASE_URL` can be specified in `local.properties` or as an environment variable (e.g., `https://news-proxy.yourdomain.com/`). When configured, `NewsRepository` routes all requests to this backend proxy without embedding or sending any client API key.
* The prepared backend holds the provider secret in Secret Manager, validates and consumes Firebase App Check / Play Integrity tokens, and restricts upstream requests. Durable request rate limiting and quota alerts are still required before public distribution. The Android client installs App Check at application startup and sends a fresh limited-use token in `X-Firebase-AppCheck` for each proxy request. See `docs/SECURITY_ARCHITECTURE.md` for details.
* **Local Development Path:** For personal development without a running proxy, `NEWS_API_KEY` in `local.properties` remains supported as a direct fallback. When no proxy is configured, an empty `NEWS_API_KEY` fails closed (`MissingNewsApiKeyException`).
* Secrets must never be committed to Git.

## Network Security Configuration

Cleartext HTTP traffic is disabled across production builds:
* `android:usesCleartextTraffic="false"` is set in `AndroidManifest.xml`.
* `network_security_config.xml` enforces HTTPS exclusively for production.
* Debug builds use a debug-scoped network security config permitting loopback traffic (`10.0.2.2`, `localhost`, `127.0.0.1`) for local proxy development and emulator mock servers.

## News Region Availability

* This build currently enables United States (`us`) top headlines because that is the only region confirmed for the configured NewsAPI plan.
* Settings shows only regions enabled for the build; unsupported region values are normalized to the default.
* Room v7 stores country in the cached-feed primary key `(url, category, country)`, so additional regions can be enabled later without cross-region cache collisions.

## Current Local Release Configuration

* No `NEWS_PROXY_BASE_URL` or `MAPS_API_KEY` is configured in this workspace.
* No `keystore.properties` or upload keystore is present. The example file documents the expected local format only.
* The locally configured NewsAPI key's account plan and publisher-specific rights have not been verified.
* The Firebase project remains on Spark. The prepared Cloud Function and its Firestore rate limiter are not deployed; the NewsAPI secret is not in Secret Manager. Firestore's default database and deny-all rules remain provisioned but are unused by the Spark news path.
* The Maps API has no key configured. In this free-plan build the map screen lists inferred locations and article links without requesting Google Maps tiles.
* Firebase App Check enforcement is active for Firebase AI Logic. The proxy source enables App Check enforcement and replay protection. The project's registered Play Integrity certificate fingerprint has not been matched against the production Play signing certificate.
* The project must not be linked to billing under the current plan constraint. Do not enable the optional proxy's TTL cleanup or deploy its function.
* The optional proxy's `NEWS_API_DAILY_REQUEST_LIMIT` defaults to 100 and must be set within the verified NewsAPI plan quota if it is ever deployed on a billing-enabled project. This setting is inactive in Spark mode.

## External Article Navigation (Android Custom Tabs)

* Publisher articles are opened via **Android Custom Tabs** (`ArticleNavigator`), ensuring sandboxed execution in the user's default browser, HTTPS enforcement, visual origin visibility, and SSL padlock indicators.
* Deep link schemes and non-HTTP/HTTPS protocols (`javascript:`, `file:`, `content:`) are strictly filtered before launching.
* In-app WebView remains isolated with JavaScript disabled and safe URL routing.

## AI Structured Insights & Evidence Attribution

* Gemini smart summaries are parsed into typed models (`ArticleInsight`, `Takeaway`, `SentimentAnalysis`, `ArticleEvidence`).
* Evidence claims are rigorously validated against source text (article title, description, content) to prevent hallucinations and detect prompt injection.
* Coroutine cancellation is preserved throughout all AI error classification paths (`CancellationException` is always rethrown).

## Device smoke (local Pixel)

This Cloud Agent VM cannot reach a USB-attached phone, and nested-virt Android
emulators here often stay `adb offline`. Prefer either:

1. **Your Pixel** (USB debugging authorized):

```bash
adb devices
./gradlew :app:installDebug
./gradlew :app:connectedDebugAndroidTest
```

2. **GitHub Actions** `instrumented` job (API 34 emulator + KVM) on this PR.

Instrumented coverage today: package smoke + Room migration + deep-link launch.
