# Production notes (NewsAPI / Maps / Firebase)

**Distribution intent:** personal / portfolio demo — not a consumer-scale Play Store
product. The hardening below matches that threat model.

## NewsAPI key model

`NEWS_API_KEY` is read from `local.properties` (or the `NEWS_API_KEY` env var) and
compiled into `BuildConfig`. That means:

* Git secrecy does **not** protect the key at runtime.
* Anyone with the APK can extract the key from the binary.
* **Acceptable for personal/portfolio builds** with a rotatable key and an eye on
  NewsAPI usage. A backend proxy is optional unless you publish widely.
* An empty `NEWS_API_KEY` fails closed at runtime (`MissingNewsApiKeyException`)
  with a clear Settings-facing message — no bogus NewsAPI call is made.
* Breaking-news worker does not advance `lastNotifiedUrl` when
  `POST_NOTIFICATIONS` was denied, and fails permanently on a missing API key.

## Maps API key

`MAPS_API_KEY` is injected into the manifest. Restrict it in Google Cloud Console
to this Android package (`com.aus.gemini01`) and your signing certificate SHA-1/256.

## Firebase App Check

* Debug builds use the App Check **debug** provider (`debugImplementation` only).
* Release builds use **Play Integrity** only.
* Enable App Check enforcement in Firebase for Vertex AI / Gemini once Play
  Integrity is registered for the release signing cert.

## Gemini model

Production builds pin `gemini-2.5-flash` (see `GEMINI_MODEL`). Do not switch back
to a `-latest` alias for shipped releases.

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

Instrumented coverage today: package smoke + Room 4→5 migration.
