# SimpleSpider Android

Kotlin + Jetpack Compose client for the SimpleSpider Django API (same endpoints as the Vue frontend).

## Features

- JWT login (`POST /api/token/`)
- **Authors** tab: list, search, infinite scroll; open author detail
- **Videos** tab: grid, search, infinite scroll; open in-app player (ExoPlayer)
- **Author detail**: profile header + author’s videos grid; tap video to play

## Configure API base URL

Edit `app/build.gradle.kts` → `defaultConfig` → `buildConfigField("String", "API_BASE_URL", ...)`:

- **Android Emulator** (host machine): default is `http://10.0.2.2:8000/api` (maps to `localhost:8000` on your PC).
- **Physical device**: use your machine’s LAN IP, e.g. `http://192.168.1.134:8003/api` (match `frontend/src/config/index.js`).

Trailing slash is added automatically in `ApiClient`.

## Run

Open the `android` folder in Android Studio, sync Gradle, run on an emulator or device.

Requires **minSdk 26**, cleartext HTTP allowed for development (`usesCleartextTraffic`).

## UI

Dark theme inspired by short-video apps (dark surfaces, accent red/cyan).
