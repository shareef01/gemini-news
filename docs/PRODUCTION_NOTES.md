# Production notes (NewsAPI / Maps / Firebase)

## NewsAPI key model

`NEWS_API_KEY` is read from `local.properties` (or the `NEWS_API_KEY` env var) and
compiled into `BuildConfig`. That means:

* Git secrecy does **not** protect the key at runtime.
* Anyone with the APK can extract the key from the binary.
* For personal demos this may be acceptable with monitoring and rotation.
* For consumer distribution, prefer a backend proxy with per-user quotas.
* An empty `NEWS_API_KEY` fails closed at runtime (`MissingNewsApiKeyException`)
  with a clear Settings-facing message — no bogus NewsAPI call is made.

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
