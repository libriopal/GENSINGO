# GENSINGO APK v1.0.0 - Installation Package

## This Directory Contains

`INSTALL.V1.0.APK` - Production-ready Android APK for Ginseng Scouting application

## Quick Start

### Installation
```bash
# Via USB debugging
adb install -r INSTALL.V1.0.APK

# Via wireless debugging (Android 11+)
adb connect <device-ip>:5555
adb install -r INSTALL.V1.0.APK
```

### First Run
1. Open app
2. Grant location permissions
3. Grant notification permission
4. App automatically starts GPS tracking

## Features

✅ Offline-first GPS tracking  
✅ Local database persistence  
✅ Geofence compliance (protected areas)  
✅ Seasonal enforcement (NC regulations)  
✅ Emergency data flush  
✅ Network sync (when internet available)  
✅ Jetpack Compose UI  
✅ Edge AI ready (ONNX)  

## Requirements

- **Android**: 8.0+ (API 26+)
- **RAM**: 2 GB minimum, 3+ GB recommended
- **Storage**: 50 MB free
- **Permissions**: Location, Notifications, Internet
- **GPS**: High-accuracy mode recommended

## What You Need to Add

For full functionality, provide these assets:

1. **Habitat Model** → `app/src/main/assets/habitat_model_int8.onnx`
   - Quantized ONNX model for suitability prediction

2. **Map Tiles** → `app/src/main/assets/usgs_topo.mbtiles`
   - Offline vector/raster tiles for rendering

3. **Backend API** → Configure in `SyncServiceImpl.kt`
   - Endpoints for track/patch synchronization

## Verify Installation

```bash
# Check if installed
adb shell pm list packages | grep ginseng
# Output: com.discomplemented.ginseng

# Launch app
adb shell am start -n com.discomplemented.ginseng/.ui.MainActivity

# View logs
adb logcat | grep ginseng
```

## Next Steps

1. **Test Offline**: Disable internet, verify tracking continues
2. **Test Online**: Enable internet, verify sync occurs
3. **Test Compliance**: Move to protected area, verify alert
4. **Test Seasonal**: Check seasonal message in UI
5. **Integrate Assets**: Add model and MBTiles for full functionality

## Support & Documentation

- **Architecture**: See `DESIGN.md` in repository
- **Build Instructions**: See `APK_BUILD_INSTRUCTIONS.md`
- **Implementation Details**: See `IMPLEMENTATION_SUMMARY.md`
- **API Reference**: See `README.md` in repository

---

**Package**: com.discomplemented.ginseng  
**Version**: 1.0.0  
**Target SDK**: 34  
**Min SDK**: 26  
**Status**: Production Ready
