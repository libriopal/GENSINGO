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

---

## Added after the second audit

**"Where am I" — on the home screen, in Field tools.** The second audit named the largest
omission in the whole project: nothing addressed being alone, on a steep slope, with a digging
tool, out of signal, hurt. This screen shows your position in **degrees and decimal minutes**
(what US dispatch and county SAR expect) *and* decimal degrees, in large monospaced type you can
read aloud. It always shows the accuracy and **the age of the fix**, because a twenty-minute-old
position displayed as current is how someone gets sent to the wrong ridge. "Send position by
text" composes an SMS, because a text needs one brief moment of marginal signal and keeps
retrying where a data request will not.

**Please actually test this one on the trail.** Read the numbers out to someone and have them
find you on a map.

## Two things the second audit changed my mind about

**The heatmap's resolution claim was overstated, and I had not noticed.** The elevation tiles are
about 3.8 m per pixel at these latitudes, but the US source beneath them is mostly ~10 m data.
So the surface renders fine structure it cannot actually know, and that structure looks
convincing. Read the heatmap at hillside scale — which slope, which side of the ridge — and never
at the scale of a single cove within it.

**The county-tier idea is dead.** The web app carries 25 North Carolina county "tiers", and I had
listed them as a possible way to finally validate the model. The audit was right that this is
invalid even with perfect provenance: county-level labels cannot validate a metre-scale
prediction, and those labels most likely encode *where people dig* rather than where ginseng
grows — which for a stewardship app points you at already-depleted ground. Discarded, and not
shipped.

## Still not built, and you should plan around it

No offline tiles. No breadcrumb trail yet. No battery figure — **carry a power bank.** Screen-on
map plus continuous GPS plus the 3D overlay will not last a full day, and a phone in direct sun
on a south slope will throttle.
