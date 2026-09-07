# Production notes (NewsAPI / Maps / Firebase)

**Distribution intent:** personal / portfolio demo — not a consumer-scale Play Store
product. The hardening below matches that threat model.

## NewsAPI key model

`NEWS_API_KEY` is read from `local.properties` (or the `NEWS_API_KEY` env var) and
compiled into `BuildConfig`. That means:

* Git secrecy does **not** protect the key at runtime.
* Anyone with the APK can extract the key from the binary.
* R8 / obfuscation does **not** make a NewsAPI credential secret.
* **Acceptable for personal/portfolio builds** with a rotatable key and an eye on
  NewsAPI usage.
* **Production / consumer requirement:** if this app is ever distributed as a real
  public service, NewsAPI requests must go through a controlled backend/proxy where
  the provider credential remains server-side, with authenticated or attested clients,
  rate limiting, abuse controls, upstream quota protection, server-side secret storage,
  observability, and minimal response proxying/caching where licensing permits.
* An empty `NEWS_API_KEY` fails closed at runtime (`MissingNewsApiKeyException`)
  with a clear Settings-facing message — no bogus NewsAPI call is made.
* Breaking-news worker does not advance `lastNotifiedUrl` when
  `POST_NOTIFICATIONS` was denied, and fails permanently on a missing API key.

## Maps API key

`MAPS_API_KEY` is injected into the manifest. Client visibility is expected for
the Maps SDK. Restrict it in Google Cloud Console to this Android package
(`com.aus.gemini01`) and your production signing certificate SHA-1/256 fingerprints.
Firebase App Check does **not** protect the unrelated NewsAPI credential.

## Firebase App Check

* Debug builds use the App Check **debug** provider (`debugImplementation` only).
* Release builds use **Play Integrity** only.
* Enable App Check enforcement in Firebase for Vertex AI / Gemini once Play
  Integrity is registered for the release signing cert.
* `google-services.json` contains Firebase client configuration identifiers, not
  private server secrets; do not treat those client keys as equivalent to a
  NewsAPI server credential.

## Deep links

There is no public `BROWSABLE` `newsapp://` intent filter. Shortcuts and
notifications use explicit `MainActivity` intents. External apps cannot open
custom-scheme routes via URI alone.

## Gemini model

Production builds pin `gemini-3.8-flash` (see `GEMINI_MODEL`). Do not switch back
to a `-latest` alias for shipped releases.

## Feed region

This build serves United States top headlines only. Settings exposes a single
US region option; multi-country cache identity is not implemented.

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
