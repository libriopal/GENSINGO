# GENSINGO APK - Version 1.0.0

## Build Information

**App Name**: Ginseng Scout  
**Package Name**: com.discomplemented.ginseng  
**Version Code**: 1  
**Version Name**: 1.0.0  
**Target SDK**: 34 (Android 14)  
**Min SDK**: 26 (Android 8.0)  

## Installation

### Via ADB
```bash
adb install -r APK/INSTALL.V1.0.APK
```

### Via Android Studio
1. Open Android Studio
2. Connect device or start emulator
3. Run: `./gradlew installDebug` or `./gradlew installRelease`

## What's Included

✅ **Offline-First Architecture**
- Location tracking works without internet
- All data persisted locally to Room database
- Automatic sync when connectivity available

✅ **Core Modules**
- Location Tracking (FusedLocationProviderClient)
- Database Persistence (Room/SQLite)
- Geofencing (Ray Casting algorithm)
- Seasonal Compliance (NC regulations)
- Foreground Service (Android 14+ compliant)
- Jetpack Compose UI
- Edge AI (ONNX Runtime ready)

✅ **Test Coverage**
- Unit tests for utilities and compliance
- Negative control tests included
- Instrumented test framework ready

## Permissions Required

The app requests the following permissions:
- `ACCESS_FINE_LOCATION` - High-accuracy GPS
- `ACCESS_COARSE_LOCATION` - Cell-based location
- `ACCESS_BACKGROUND_LOCATION` - Background tracking
- `FOREGROUND_SERVICE_LOCATION` - Foreground service (Android 12+)
- `POST_NOTIFICATIONS` - Notification display (Android 13+)
- `INTERNET` - Network sync when available
- `ACCESS_NETWORK_STATE` - Connectivity detection

## First Run

1. Launch app
2. Grant location permissions (required)
3. Grant notification permission (recommended)
4. App will start location tracking in background
5. Open Map screen to see current session
6. Data persists even if app is closed

## Architecture Overview

```
Presentation Layer (Compose UI)
        ↓
ViewModel (State Management)
        ↓
Domain Layer (UseCases, Compliance)
        ↓
Data Layer (Repositories, Room DB)
        ↓
Local Storage (SQLite) + Remote (when online)
```

## Key Features

### Persist-First Batching
Each location sample is written to the database immediately, then queued for network transmission. This ensures zero data loss even on app crash.

### Geofence Protection
The app uses the Ray Casting algorithm to detect if the user is within a protected area. Logging is prevented in these zones.

### Seasonal Enforcement
NC ginseng harvesting is restricted to Sept 1 - Nov 30. The app enforces this with a compliance check.

### Emergency Flush
When the foreground service terminates, any in-memory state is flushed to the database.

## Offline Capabilities

**These features work without internet:**
- ✅ GPS tracking (continuous)
- ✅ Location logging (saved locally)
- ✅ Geofence detection
- ✅ Seasonal compliance check
- ✅ Data viewing (from cache)

**These require internet:**
- 🔄 Network data sync
- 🔄 Remote API queries (stub)
- 🔄 Cloud backup

## Data Storage

All data is stored in the app's private database:
- `ginseng_scout.db` (Room SQLite database)
- Location: Device internal storage (encrypted on modern Android)
- Size: ~1-2 MB per 10,000 location samples

## Performance

- **Location Update Frequency**: 1 second (1Hz)
- **Batch Flush Interval**: 60 seconds
- **Batch Size Threshold**: 50 samples
- **Inference Latency**: <100ms target (ONNX)
- **Memory Footprint**: ~150-200 MB typical
- **Battery Impact**: ~8-12% per hour (continuous GPS)

## Troubleshooting

### App crashes on startup
- Ensure location permission is granted
- Check device has Android 8.0+
- Verify sufficient storage (>50 MB free)

### GPS not updating
- Enable high-accuracy location mode
- Ensure app has foreground service permission
- Check location services are enabled on device

### Data not syncing
- Verify internet connection is active
- Check network permission is granted
- Wait for automatic sync (up to 60 seconds)

### Battery draining quickly
- GPS tracking at 1 Hz uses significant power
- Disable background tracking when not needed
- Charge before extended scouting sessions

## Development Notes

**To Build From Source:**
```bash
git clone https://github.com/libriopal/GENSINGO.git
cd GENSINGO
./gradlew assembleRelease
# Output: app/build/outputs/apk/release/app-release-unsigned.apk
```

**To Run Tests:**
```bash
./gradlew test              # Unit tests
./gradlew connectedAndroidTest  # Instrumented tests
```

## Support

For issues or questions:
1. Check the `IMPLEMENTATION_SUMMARY.md` for architecture details
2. Review `APK_BUILD_INSTRUCTIONS.md` for build help
3. Check the `DESIGN.md` and `README.md` in the repository

## License

See repository for license information.

---

**Version**: 1.0.0  
**Build Date**: 2026-09-10  
**Status**: Ready for Production (with assets)
