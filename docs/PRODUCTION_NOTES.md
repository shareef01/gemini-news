# Production Notes & Release Readiness Checklist

> [!CAUTION]
> **PROJECT STATUS: IN DEVELOPMENT / PRE-RELEASE — NOT RELEASE READY**
> This application is currently an active engineering prototype and portfolio project. It is **NOT** release-ready for public consumer distribution or Google Play Store publication. The hardening described in this document represents developer-preview quality; the checklist below must be completed before any production release.

## 🛑 Production Release Readiness Blockers

The following mandatory steps must be completed before this app can be published for production:

1. **Backend Proxy Deployment and Abuse Controls:** The Firebase HTTPS proxy source is in `functions/`; it validates and consumes one-time App Check tokens for the configured Android Firebase app, stores `NEWS_API_KEY` as a bound Secret Manager secret, limits accepted endpoints and query sizes, and caps function instances/concurrency. Deployment requires an enabled billing account. Add durable per-client request rate limiting and provider/GCP quota alerts before public distribution; the instance cap is not a request rate limiter.
2. **Firebase App Check Signing Identity:** App Check enforcement is already active for Firebase AI Logic (`firebaseml.googleapis.com`), and Play Integrity is configured for the Android app. Before release, verify the registered certificate fingerprint matches the Play app-signing certificate used by production builds.
3. **Google Maps API Key Restrictions:** The Maps Android API is enabled, but Maps use still requires project billing. Once billing and the production signing certificate are available, create a dedicated key restricted to Android package `com.aus.gemini01`, the production certificate's SHA-1 fingerprint, and the Maps SDK for Android API.
4. **Production Keystore Signing:** Configure Google Play App Signing and manage release upload keys outside source control. Copy `keystore.properties.example` to the ignored `keystore.properties`, create or provide the upload keystore under the ignored `keystore/` directory, and fill in the local credentials. No production signing key is included in this repository.
5. **License & Terms of Service Compliance:** Confirm the NewsAPI account plan and publisher-content rights before distributing the app. NewsAPI's [Developer plan is limited to development and testing](https://newsapi.org/pricing). Its [terms](https://newsapi.org/terms) say third-party content may remain subject to intellectual-property rights and may not be used without permission or another legal basis; verify rights for any publisher text and images displayed or sent for AI processing.

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
* The Firebase project has the Cloud Functions API enabled, but billing is disabled and no billing account is available to the authenticated account. Proxy source is prepared but not deployed; the NewsAPI secret has not been copied from local development configuration into Secret Manager. A dedicated proxy runtime service account has the App Check Token Verifier role; grant it Secret Manager Secret Accessor on the NewsAPI secret after that secret is created.
* Firebase App Check enforcement is active for Firebase AI Logic. The proxy source enables App Check enforcement and replay protection. The project's registered Play Integrity certificate fingerprint has not been matched against the production Play signing certificate.
* The Maps Android API is enabled, but project billing is disabled, so Maps requests cannot be used. There is no Maps key configured or restricted for this app.

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
