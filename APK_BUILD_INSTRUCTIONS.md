# Implementation notes for GENSINGO APK

## Build Instructions

### Prerequisites
- Android Studio (Ladybug or newer)
- Android SDK 34 (UpsideDownCake)
- Android NDK (for ONNX Runtime)
- Gradle 8.2+

### Building Release APK

```bash
# Clean build
./gradlew clean

# Build release APK (unsigned)
./gradlew assembleRelease

# Output: app/build/outputs/apk/release/app-release-unsigned.apk

# Sign APK (requires keystore)
jarsigner -verbose -sigalg SHA1withRSA -digestalg SHA1 \
  -keystore my-key.keystore \
  app/build/outputs/apk/release/app-release-unsigned.apk \
  alias_name

# Align APK
zipalign -v 4 app-release-unsigned.apk app-release-aligned.apk
```

### Building Debug APK

```bash
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk
```

## Installation

```bash
adb install -r app-release-aligned.apk
```

## Running Tests

```bash
# Unit tests
./gradlew test

# Instrumented tests
./gradlew connectedAndroidTest
```

## Key Features Implemented

✅ **Phase 1 - Core Stability**
- Location tracking with FusedLocationProviderClient
- Persist-first batching strategy (Room database)
- Room database setup with proper DAOs
- Foreground Service with emergency flush
- Jetpack Compose navigation

✅ **Phase 2 - Polish**
- Network availability detection
- Geofence compliance (Ray Casting algorithm)
- Seasonal harvesting restrictions (NC rules)
- ONNX inference stub (ready for model integration)
- Comprehensive error handling

✅ **Phase 3 - Validation**
- Unit test suite (GeoUtils, Compliance)
- Instrumented tests
- Negative control tests

## Offline-First & Internet Fallback

The app operates in three modes:

1. **Offline-Only** (no internet):
   - Location tracking continues
   - All data persisted to Room database
   - UI displays locally cached data
   - Compliance checks execute locally

2. **Intermittent Connectivity**:
   - App detects when internet becomes available
   - Syncs unsynced data in background
   - Marks records as synced in database

3. **Always Online**:
   - Real-time sync possible
   - Remote data sources can be utilized

## Architecture Notes

**Persist-First Pattern**: Each location sample is written to Room immediately (with `synced=false`), then batched for network transmission. On service crash/termination, emergency flush ensures data is not lost.

**Clean Architecture Layers**:
- Presentation: Compose UI + ViewModels
- Domain: UseCases, pure business logic, models
- Data: Room, repositories, remote sync

**Compliance Module**: 
- Geofencing uses Ray Casting algorithm
- Seasonal rules hardcoded for NC (Sept 1 - Nov 30)
- Both checks run locally; no network required

## Missing Assets (To Be Provided)

1. **Maps**: `app/src/main/assets/usgs_topo.mbtiles`
   - Local vector/raster tiles for offline rendering
   - See MapLibre documentation for format

2. **AI Model**: `app/src/main/assets/habitat_model_int8.onnx`
   - Quantized ONNX model for habitat prediction
   - Input shape: [latitude, longitude, elevation, slope]
   - Output: suitability score (0.0-1.0)

3. **API Key** (if using online tiles):
   - Configure in `MapLibreComposeView.kt`
   - Only needed if online tiles are desired

## Next Steps

1. **Integrate Real ONNX Model**:
   - Replace stub in `HabitatInferenceManager.kt`
   - Add model loading from assets
   - Implement feature extraction pipeline

2. **Connect Backend Sync API**:
   - Implement HTTP endpoints in `SyncServiceImpl.kt`
   - Define API contract (JSON schema)
   - Add authentication if needed

3. **Full MapLibre Integration**:
   - Load local MBTiles in `MapLibreComposeView.kt`
   - Render track history and patch overlays
   - Add camera controls and zoom listeners

4. **Comprehensive Testing**:
   - Run full test suite
   - Manual device testing (GPS, offline mode, sync)
   - Performance profiling (battery, memory)

5. **Prepare for Release**:
   - Create signing keystore
   - Obfuscate sensitive data
   - Test on Android 14+ devices
   - Verify FOREGROUND_SERVICE_LOCATION compliance
