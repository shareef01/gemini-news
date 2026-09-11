# Production Notes & Release Readiness Checklist

> [!CAUTION]
> **PROJECT STATUS: IN DEVELOPMENT / PRE-RELEASE — NOT RELEASE READY**
> This application is currently an active engineering prototype and portfolio project. It is **NOT** release-ready for public consumer distribution or Google Play Store publication. The hardening described in this document represents developer-preview quality; the checklist below must be completed before any production release.

## 🛑 Production Release Readiness Blockers

The following mandatory steps must be completed before this app can be published for production:

1. **Backend Proxy Deployment:** Client builds must NOT communicate directly with NewsAPI. A secure backend proxy must be deployed that validates Play Integrity / Firebase App Check tokens and holds the upstream provider secret server-side.
2. **Firebase App Check Enforcement:** Play Integrity provider must be registered in the Firebase Console with the production release signing key, and App Check must be switched from monitoring to **Enforcement** for Google Cloud Vertex AI / Gemini.
3. **Google Maps API Key Restrictions:** Restrict the Cloud Maps API key to the Android package `com.aus.gemini01` and the release keystore SHA-256 fingerprint in Google Cloud Console.
4. **Production Keystore Signing:** Configure Google Play App Signing and manage release upload keys outside source control.
5. **License & Terms of Service Compliance:** Validate NewsAPI and publisher syndication licensing for commercial / consumer distribution.

## NewsAPI key model & Backend Proxy Architecture

For production deployments, client-side secret embedding is strictly avoided:
* `NEWS_PROXY_BASE_URL` can be specified in `local.properties` or as an environment variable (e.g., `https://news-proxy.yourdomain.com/`). When configured, `NewsRepository` routes all requests to this backend proxy without embedding or sending any client API key.
* The backend proxy holds the actual provider secret, validates Firebase App Check / Play Integrity tokens, enforces rate limiting, and protects upstream quotas. See `docs/SECURITY_ARCHITECTURE.md` for details.
* **Local Development Path:** For personal development without a running proxy, `NEWS_API_KEY` in `local.properties` remains supported as a direct fallback. When no proxy is configured, an empty `NEWS_API_KEY` fails closed (`MissingNewsApiKeyException`).
* Secrets must never be committed to Git.

## Network Security Configuration

Cleartext HTTP traffic is disabled across production builds:
* `android:usesCleartextTraffic="false"` is set in `AndroidManifest.xml`.
* `network_security_config.xml` enforces HTTPS exclusively for production.
* Debug builds use a debug-scoped network security config permitting loopback traffic (`10.0.2.2`, `localhost`, `127.0.0.1`) for local proxy development and emulator mock servers.

## Multi-Region News Support

* The app supports multiple top-headline regions: United States (`us`), United Kingdom (`gb`), Canada (`ca`), Australia (`au`), India (`in`), Germany (`de`), and France (`fr`).
* Persistent preference is stored via DataStore and selectable in Settings.
* Room caching uses composite primary key `(url, category, country)` with automated schema Migration 6→7 to ensure complete cache isolation between regions without collisions or data loss.

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
