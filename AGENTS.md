# AGENTS.md — Base44 setup notes for GENSINGO

## What this project is

GENSINGO is a **native Android app** (Kotlin + Jetpack Compose, package `com.ginsengo.steward`) that builds to an APK. It is not a web app and cannot run in a browser. The Base44 preview serves a **build & test status page** on port 3000 — it compiles the project and runs the 339 JVM unit tests inside Docker, then shows the results.

## Stack

- Kotlin 2.2.10, Jetpack Compose, AGP 8.11.2
- compileSdk/targetSdk 36, minSdk 26
- Room 2.7.1, MapLibre Android 13.6.1, Anthropic Java SDK 2.65.0
- Gradle 8.13 (wrapper), JDK 17
- Android SDK: platform 36, build-tools 35.0.0

## How the Docker environment works

- `Dockerfile.base44` — based on `eclipse-temurin:17-jdk`, installs Android command-line tools + platform 36 + build-tools 35.0.0
- `docker-compose.base44.yml` — bind-mounts the source at `/workspace`, caches Gradle deps in a volume, runs `base44_dev.py`
- `base44_dev.py` — creates `local.properties`, runs `./gradlew :app:testDebugUnitTest --no-daemon` in a background thread, serves a live HTML status page on port 3000 that auto-refreshes while building

## Build & test commands

```bash
# Inside the container:
./gradlew :app:testDebugUnitTest     # JVM unit tests (339, 1 skipped without fixture)
./gradlew :app:assembleDebug          # debug APK (~80 MB universal)
./gradlew :app:assembleField          # shrunk sideload APK (R8, arm64, ~21 MB)
```

## Secrets

- `GEMINI_API_KEY` — optional, entered at runtime in the app (Layers → Research model), not needed for build or tests. Unit tests use MockWebServer with mock keys.
- No other external secrets required for build or tests.

## Quirks

- `local.properties` (pointing to the Android SDK) is created automatically by `base44_dev.py` and is in `.gitignore`.
- The RadiusScanTimingTest is skipped unless `GENSINGO_SCAN_FIXTURE` env var points to a pre-built fixture file.
- The Gradle cache volume (`gradle-cache`) persists across container restarts, so subsequent builds are faster.
- First build takes ~3-4 minutes (downloading Gradle distribution + all dependencies); subsequent builds with warm cache take ~30 seconds.
