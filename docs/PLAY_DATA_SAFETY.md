# Google Play Data safety declaration — GENSINGO

Source of truth for the Play Console **Data safety** form. Everything below is a statement
about what the shipped code does, and each row names where to verify it.

## Summary

| Question | Answer |
|---|---|
| Does the app collect or share any user data? | **No** |
| Is data encrypted in transit? | N/A — no user data is transmitted |
| Can users request data deletion? | Yes — delete a patch in-app, or uninstall |
| Committed to the Play Families policy? | N/A |
| Independent security review? | No |

GENSINGO has no account system, no backend, no analytics SDK, no advertising SDK, and no
crash-reporting SDK. Patch records, photos and habitat readings are written to the app's
private storage and are never transmitted anywhere.

## Permissions and why each is requested

| Permission | Why | Verify |
|---|---|---|
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | Centre the map, stamp a patch when the user logs one, and auto-detect which state's harvest rules apply. All processing is on-device against bundled GeoJSON. | `field/LocationProvider.kt`, `compliance/ComplianceEngine.kt` |
| `CAMERA` | Capture a patch photo in-process, written straight into `filesDir`. | `ui/screens/CameraCapture.kt` |
| `INTERNET` / `ACCESS_NETWORK_STATE` | Map tiles only (OpenFreeMap). No patch data is included in any request. | `ui/map/FieldMap.kt` |

### Merged in by libraries, not declared by this app

Verified against the merged release manifest, not against the source manifest — these two
appear in the shipped APK and must be accounted for:

| Permission | Origin | Assessment |
|---|---|---|
| `ACCESS_WIFI_STATE` | MapLibre Android SDK | Connectivity detection before tile fetches. Not used for location: the app never calls the WiFi APIs itself. |
| `com.ginsengo.steward.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | AndroidX Core | Auto-generated signature-level permission guarding runtime-registered broadcast receivers. Not a data permission. |

**Components in the shipped manifest**, in full: `androidx.camera.core.impl.MetadataHolderService`,
`androidx.room.MultiInstanceInvalidationService`, `androidx.profileinstaller.ProfileInstallReceiver`,
`androidx.core.content.FileProvider`, `androidx.startup.InitializationProvider`. All AndroidX
framework components. **No telemetry or analytics service is present** — grepping the release
APK for `telemetry|analytics` returns zero matches. (MapLibre carries no Mapbox telemetry.)

### Deliberately NOT requested

`ACCESS_BACKGROUND_LOCATION`, `FOREGROUND_SERVICE`, `POST_NOTIFICATIONS`,
`READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`, `READ_MEDIA_IMAGES`.

The app reads position only while the user is looking at the screen; it does not follow
anyone around. Photos never touch MediaStore — a patch photo in the shared gallery is a
patch location handed to every app on the phone with media permission, and to whatever backs
that gallery up.

## Location data handling — the sensitive case

A wild-ginseng patch location is the most sensitive thing this app holds. Poaching from
known patches is the primary threat to the species and to the user.

- Stored in the Room database in app-private storage. Never transmitted.
- Displayed **masked** in list view until the user explicitly taps to reveal
  (`ui/screens/PatchesScreen.kt`). Masking replaces the string rather than visually blurring
  it, because `Modifier.blur` is a no-op below API 31 and `minSdk` is 26.
- Excluded from Android cloud backup and device-transfer: `android:allowBackup="false"` plus
  explicit excludes in `res/xml/backup_rules.xml` and `res/xml/data_extraction_rules.xml`.
- Export is user-initiated only, writes to app-private storage, and hands the file to one
  app of the user's choosing via a one-time `FileProvider` grant.

## Photos

Captured via CameraX directly into `filesDir/patch_photos/`. Room stores a path **relative**
to `filesDir`, never an absolute path and never a `content://` MediaStore URI. Sharing goes
through `FileProvider` with a time-limited grant (`res/xml/file_paths.xml`).

## Third-party SDKs

| SDK | Purpose | Network |
|---|---|---|
| MapLibre Android SDK | Map rendering | Tile requests to `tiles.openfreemap.org` |
| ONNX Runtime | On-device habitat model inference | **None** — runs locally |
| Play Services Location | GPS fixes | None initiated by this app |
| AndroidX (Compose, Room, CameraX, Navigation) | Framework | None |
| Coil | Image loading from assets and local files | None |

No analytics, attribution, advertising or crash-reporting SDK is present.

## Pre-submission checklist

- [x] `minSdk 26`, `targetSdk 34`
- [x] Adaptive launcher icon with monochrome layer
- [x] Release `signingConfig` reads from `keystore.properties` or environment; keystore
      git-ignored
- [x] `allowBackup=false` with backup rules
- [x] `usesCleartextTraffic=false`
- [x] No tracking SDKs
- [ ] Build the AAB: `./gradlew :app:bundleRelease`
- [ ] Verify on a physical device — **not yet done**, see `EINCOL_REPORT.md` open item 5
- [ ] Store listing must not imply the habitat model is a validated ecological predictor
