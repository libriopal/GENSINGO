# GENSINGO — field test build

**Status: PROTOTYPE. Version 0.x, not 1.0.** It has run on an emulator and never on a phone.
You are the first real device it will ever touch.

## Which file

| File | Size | Use |
|---|---|---|
| **`app-arm64-v8a-release.apk`** | **48.9 MB** | **Any phone made since roughly 2017. Take this one.** |
| `app-armeabi-v7a-release.apk` | 40.2 MB | Older 32-bit handsets |
| `app-universal-release.apk` | 140.4 MB | Installs on anything, if the above fails |

Signed with `CN=GENSINGO Build Verification`, APK Signature Scheme v2. Sideload it: enable
"install unknown apps" for your browser or file manager, then open the APK.

It installs alongside nothing — package `com.ginsengo.steward`. There is no Play listing and no
auto-update; to update, install the new APK over the top.

## Before you leave the house

1. **Open it on wifi first and let the map load.** Tiles are fetched from the network. There is
   no offline tile cache yet — this is the single largest known gap for field use.
2. Grant location. The app asks for precise location and camera only. It does not ask for
   background location and cannot follow you around.
3. Check the season card reads what you expect for your state.

## What will not work in a hollow

**No signal means no map and no heatmap.** The basemap, the satellite layer and the elevation
data behind the habitat forecast are all fetched live. Offline pre-download is not built yet.
Everything else — logging a patch, photos, the stewardship guide, the plant verification
workflow — is on-device and works with the radio off.

## What to be sceptical of

- **The habitat heatmap is an unvalidated terrain heuristic, not a forecast.** It reads slope,
  aspect, position, wetness and curvature from an elevation model. It has never been checked
  against a single known ginseng location. Measured: its ranking survives a 50% error in its
  largest coefficient at rho 0.88, which means it points at broadly promising hillsides and
  cannot rank one cove against another. Treat a bright patch as "worth walking", never as "dig
  here".
- **It cannot see soil calcium**, which is one of the strongest real predictors. The app says so
  on the reading screen and names the indicator species that do reveal it.
- **The season and legality data is incomplete by design.** 5 of 19 jurisdictions have a sourced
  closing date. The other 14 now say SEASON UNKNOWN and tell you which agency to ring. That is
  deliberate: an earlier build said SEASON OPEN in those states on any date past 1 September,
  including December. **Ring the agency. Do not trust this app on closing dates.**
- **Protected-area boundaries are approximate bounding boxes**, not real polygons, and they only
  cover federal land. State parks, wildlife management areas, watershed land, tribal land and
  private property are not in the dataset at all. **Absence of a warning is not permission.**

## What I want to know from you

1. Does the map draw at all on a real phone? (It shows grey in my test environment, which I
   believe is a sandbox TLS proxy rather than an app bug — you are the test.)
2. Does it survive a full day in a pocket without crashing or eating the battery?
3. Is the dark theme readable in direct sun under a canopy?
4. Can you log a patch one-handed, quickly, with the phone at chest height?
5. Does the habitat reading agree with ground you already know is good? That is the only
   validation signal that exists.
6. Anything that makes you put the phone away and use your eyes instead.

## Known open items

Tracked in `EINCOL_REPORT.md` (items 27–36) and `implementation_production_build_v1.0.0.md`.
The load-bearing ones: no offline tiles, the 3D overlay has never been exercised on real
hardware, the alignment witness has never produced a number, and the app fetches elevation at
runtime which conflicts with the original offline-only instruction.
