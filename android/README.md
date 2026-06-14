# SimpleSpider Android

Kotlin + Jetpack Compose client for the SimpleSpider Django API (same endpoints as the Vue frontend).

## Features

- JWT login (`POST /api/token/`)
- **Authors** tab: list, search, infinite scroll; open author detail
- **Videos** tab: grid, search, infinite scroll; open in-app player (ExoPlayer)
- **Author detail**: profile header + author’s videos grid; tap video to play

## Configure API base URL

Edit `app/build.gradle.kts` → `defaultConfig` → `buildConfigField("String", "API_BASE_URL", ...)` for the default server, or set it in the app:

**Login → Settings (API server)** or **Settings** tab after login.

- HTTPS is used by default; prefix with `http://` for plain HTTP.
- Host or host:port (default port 8000 if omitted), e.g. `192.168.1.134:8000`.

Trailing slash is added automatically in `ApiClient`.

## HTTPS with a bundled CA

For self-signed HTTPS, add your CA certificate to the project:

```
android/trust-ca/api_trust_ca.pem
```

Rebuild the app. See `android/trust-ca/README.md` for PEM format and export commands.

The CA is bundled at build time. Users only set the API host in Settings — no per-device certificate setup.

Public CA certificates (Let's Encrypt, etc.) work without `api_trust_ca.pem` via the system trust store.

## Run

Open the `android` folder in Android Studio, sync Gradle, run on an emulator or device.

Requires **minSdk 26**.

## UI

Dark theme inspired by short-video apps (dark surfaces, accent red/cyan).
