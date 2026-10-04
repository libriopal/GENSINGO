# Wave A.3 — one map, finished (exe.md §11 A13–A18) + the field defects A.2 found

Candidates claimed: **A13, A15, A16, A17, A18**, and the three field defects §12 put before F.1:
**I18, I19, I21**. **A14 stays blocked(owner)** (its re-scope is decision 2 in §12). Eight
candidates, within §8's limit. Branch `eincol/A.3`, off the session branch at `f2d8d9c` (A.2's
archive).

## 1 — Load (measured from the tree)

| What | Measured |
|---|---|
| Memory (A13) | Four app caches, no shared ceiling, no `onTrimMemory` anywhere: `DemTileStore.memory` (an `LruCache` of 24 decoded tiles × 256 KB ≈ 6 MB, read by both views), `Terrain3D.Scene.baked` (two 2048² textures, 16 MB each), the draped map snapshot in `Terrain3DView` (16 MB), the habitat raster handed to MapLibre (≈2.4 MB). Since A.2 the map stays alive under the 3D view, so the two paths' memory now coexists |
| Hand-off continuity (A15) | The sink's pitch easing is inline arithmetic in `MainScreen`'s effect: untested. `landFlat` follows it under the cover |
| Rotation (A16) | `MainActivity` has no `configChanges`: rotation recreates it. Camera, layers and the 3D switch survive (the ViewModel); the fade (`reveal`, a plain `remember`), the 3D square and its GL objects do not: a rotation in 3D re-warms under the map and rebuilds the square (12 min on the emulator) |
| Gestures (A17) | MapLibre 13.6.1, read from its bytecode (`javap -c`, evidence file): tilt `tilt − 0.1·Δy` (`SHOVE_PIXEL_CHANGE_FACTOR`), clamped 0–60; pinch `zoom + ln(s)/ln(π/2) · 0.65 · zoomRate`; rotate `bearing + Δangle`, 1:1. The 3D view: tilt `pitch − 0.15·Δy` (**50 % more sensitive**), pinch `log₂ s` (within 0.23 % of MapLibre's), rotate 1:1 in the same direction |
| Occlusion (A18) | No layer can declare `OCCLUDED` (A.2): the 3D markers are a canvas over the GL surface, so a suggestion behind a ridge is drawn as if on the slope in front |
| First fix (I18) | `FieldViewModel` feeds `lastKnown` into `onFix`; the first fix of any age consumes the one jump (`CameraStart.shouldJumpToFix`). A Play-services phone's `lastLocation` can be hours old and far away (opened at the trailhead, last fixed at home), and the live fix then never lands. **Re-diagnosed during this wave:** the emulator symptom A.1 and A.2 reported (a California landing) was not this defect. The AOSP image has no Play services, so `lastKnown` returns nothing; the California fix was the emulator's default GPS position, delivered live and fresh by the platform-GPS fallback, and the app was right not to jump again. The defect is real on Play-services phones; its device half cannot be reproduced here |
| Logging (I19) | `DemTileStore`: `Log.w(TAG, "DEM tile $z/$x/$y unavailable", it)` with a full stack trace on every failed fetch; MapLibre's native log does the same (`Mbgl: Failed to load tile 2/1/1…`). Logcat readable by any debugger locates the user to a z15 tile (~1 km). The same flood made logd prune the app's own lines in A.2 |
| Position dot (I21) | `FieldMap`'s data pushes run only from `AndroidView.update`. That lambda captures `layers`, not the data; with Kotlin 2.2's strong skipping (on by default) Compose keeps the same lambda instance while `layers` is unchanged, so `update` does **not** re-run when only the fix (or track, finds, suggestions) changes. A fix that arrives after the style has loaded is never pushed: the dot shows only when the fix came first, hence "intermittent". The same blind spot leaves the habitat raster on old weights until the camera moves |
| Device | The container restarted between sessions: the emulator was cold-booted again (same AVD) |

## 2 — Distribution (tail first)

| p | Reading of "A.3" |
|---|---|
| 0.22 | Field defects first: I21 (no dot on the map) is the most visible wrong thing in the app; I18 lands the map in the wrong state; I19 is a privacy invariant |
| 0.20 | A13: one budget is real work now that both paths live at once |
| 0.15 | A17 and A15: small, testable, and they finish "two sets of gestures" and "a jump on transition" |
| 0.13 | A18's occlusion: a marker behind a ridge read as on the near slope misleads a digger |
| 0.10 | A16: rotation mid-3D costs a full rebuild |
| **0.12** | **(tail) A.3 is polish on a 3D view a digger rarely opens; the time belongs to the top-N engine (F)** |
| **0.08** | **(tail) "One gesture handler" is unreachable with two surfaces: MapLibre consumes its own touches** |

### Working the tail

**"Polish on a view rarely opened": half right.** Three of the eight are not 3D at all: I21 and
I18 are the 2D map's position dot and landing, and I19 is a privacy leak on every screen. They are
why A.3 runs before F. For the 3D half the honest test is cost: A15, A17 are small; A13 protects the
2D map's memory too (the DEM cache is shared); A16 and A18 are the two that are 3D-only, and both are
bounded.

**"One gesture handler is unreachable": true as worded.** MapLibre handles its own touches, and
replacing them means driving MapLibre from a Compose gesture layer (A5's architecture, blocked on the
owner). What is reachable is one gesture *mapping*: the same finger movement producing the same
camera change in both views, with MapLibre's constants (measured) as the reference. That is what is
built; the register's wording is recorded as not met.

## 3 — Falsify (what would break each claim)

| Claim | What could break it | Check |
|---|---|---|
| I21's cause is the memoized `update` lambda | the dot missing for another reason (layer order, visibility) | A.2 checked the order on device (`layers: style order matches`); the fix pushes from an effect keyed on the data; device: the dot appears after "Centre on me" |
| Strong skipping is on | it could be off in this build | Kotlin 2.2.10's Compose compiler enables it by default (since 2.0.20); the device behaviour (dot only when the fix came first) matches |
| One ceiling, not several | caches each keeping their own cap | a test filling one cache to the ceiling and then adding to another must evict from the first |
| Hand-off continuity | the pitch clamp at `landFlat` (75° → 60°) jumping | a continuity test sampling the projected centre across the sink and the landing, with a mutant that removes the easing |
| Gesture parity | constants copied wrong | constants pinned to the `javap` output; a mutant restoring 0.15 must fail |
| Occlusion | a ray test that is always false (nothing hidden) or always true | analytic: a wall between eye and marker hides it, the same marker in front of the wall is visible, and tilting to look down over the wall reveals it |
| Redaction | a tile id surviving in some message form (`z/x/y`, a URL) | a test over the message shapes seen on device (DemTileStore, Mbgl, URLs) |
| First fix | a fresh fix ignored after a stale one, or a fresh fix yanking a user who already landed | a test over the sequence stale → fresh → fresh |

**Reuse.** No new library code is copied this wave: every change extends the app's own code. MapLibre
is read (bytecode) for its gesture constants, and its own `Logger.setLoggerDefinition` hook is used
for redaction.

## 4 — Evaluate (chosen designs, rejected alternatives)

- **I21: push from a `LaunchedEffect` keyed on the data.** Rejected: re-reading State inside
  `update` (it would still depend on recomposition timing); disabling strong skipping for the module
  (a global switch for one defect, and the next lambda would hide the same bug).
- **A13: one process-wide `MemoryBudget` with one LRU across owners, pinned entries for what is on
  screen, and `onTrimMemory` lowering the ceiling.** Rejected: a cap per cache (the register's point
  is one ceiling); `largeHeap` (hides the problem, and a field phone is often low-end).
- **A15: pull the sink's easing into `Handoff.sinkPitch` and test continuity there.** Rejected: an
  instrumented screen-diff test (the emulator draws 2–4 fps: a jump between frames is invisible).
- **A16: hold the built square in the ViewModel for the 3D session; `reveal` saved across
  recreation and restored only when the square is held.** Rejected: `configChanges` in the manifest
  (the app would have to re-lay out itself by hand on every rotation, and MapLibre's view recreation
  is the tested path); keeping the square after leaving 3D (that is J17, awaiting scope approval).
- **A17: one `GestureMath` used by the 3D view, MapLibre's constants as the reference.** Rejected:
  driving MapLibre from Compose (A5's architecture; blocked on the owner).
- **A18: occluded markers drawn faint, not removed.** A suggestion behind a ridge is still a place
  to walk to; dimming says "behind the ridge" without losing it. Rejected: hiding them (loses
  information); a GPU depth test (the markers are text and circles on a Compose canvas; moving them
  into GL is a renderer rewrite for the same answer).
- **I18: a fix older than 2 minutes lands provisionally** (the map opens near it, but the first fresh
  fix still lands). Rejected: ignoring `lastKnown` (the map would open on the fallback for the
  seconds before the GPS answers, often minutes in a hollow).
- **I19: redact tile ids in the app's and MapLibre's logs, and drop stack traces from the tile log.**
  Rejected: silencing MapLibre's log entirely (loses real errors).

## 5 — Design (files, contracts, witnesses, negative controls)

| Id | Change | Observable | Negative control |
|---|---|---|---|
| I21 | `FieldMap`: `LaunchedEffect(me, track, finds, suggestions, radiusCenter)` pushes; `LaunchedEffect(weights)` refreshes the raster | device: the dot at the centre after "Centre on me" (`A.2-device-12` is the before) | device before/after |
| I19 | `util/LogRedaction.kt` (`redact`), used by `DemTileStore` and by a `LoggerDefinition` installed in `GensingoApp` | `LogRedactionTest`: no tile id survives in any shape seen on device; zoom kept | U1 the redactor passes ids through |
| I18 | `CameraStart.landing(fixAgeMs, landed)`; `FieldViewModel.onFix` | `CameraStartTest`: stale → provisional; then fresh → final; then no jump | U2 a stale fix lands final |
| A17 | `terrain3d/GestureMath.kt`; `Terrain3DView` uses it | `GestureMathTest` against MapLibre's measured constants | U3 tilt factor back to 0.15 |
| A15 | `Handoff.sinkPitch`; `MainScreen` uses it | `HandoffTest`: across sink + landing the projected centre and corners never move more than 2 dp between samples; no jump at the landing | U4 no easing (the clamp at landing) |
| A13 | `perf/MemoryBudget.kt`; `DemTileStore`, `Scene` textures, the drape; `GensingoApp.onTrimMemory`; counters logged | `MemoryBudgetTest`: one LRU across owners, pinned never evicted, trim lowers the ceiling. Device: `am send-trim-memory` → counters under the lowered ceiling | U5 evict most-recent; U6 per-owner ceilings |
| A18 | `terrain3d/Occlusion.kt`; FINDS, SUGGESTIONS, ME declare `OCCLUDED`, drawn by `MeshDraw.CANVAS_OCCLUDED` (faint when hidden) | `OcclusionTest` (wall in front hides; in front of the wall visible; looking down over the wall reveals); `SceneLayersTest` with OCCLUDED honoured | U7 occlusion never true; U8 an OCCLUDED layer drawn as ON_TOP |
| A16 | `FieldViewModel.meshSession` (the built square for the current 3D session); `Terrain3DView` uses it; `reveal` in `rememberSaveable`, restored only with a held square | device: rotate in 3D → the terrain is back with no "Tracing creeks…" | device before/after |

## 6 — Implement (as built)

- **I19** `geo/LogRedaction.kt` (`redact`, `describe`); `DemTileStore` logs `DEM tile unavailable at
  zoom 15: SSLHandshakeException…` (no x/y, no stack trace); `ui/map/MapLogging.kt` installs a
  redacting `LoggerDefinition` for MapLibre in `GensingoApp.onCreate`, before the first map.
- **I21** `FieldMap`: `LaunchedEffect(me, track, finds, suggestions, radiusCenter)` pushes the data;
  `LaunchedEffect(weights)` refreshes the raster. `update` still pushes on layer changes.
- **I18** `CameraStart.landing` (`FRESH_FIX_MS` 2 min, `Landing` NONE/PROVISIONAL/FINAL);
  `FieldViewModel.onFix` uses it.
- **A17** `terrain3d/GestureMath.kt`; the 3D view's pinch, twist and two-finger tilt use it. Tilt
  is now 0.1 °/px (MapLibre's), not 0.15.
- **A15** `Handoff.sinkPitch`; `MainScreen`'s sink uses it.
- **A13** `perf/MemoryBudget.kt`; `AppContainer.memoryBudget` (a third of the heap class, ≥ 48 MB);
  `DemTileStore` (its 24-tile cap gone), `Terrain3D.Scene` (`useBudget`, `release`; the texture on
  screen pinned), the drape (`MeshSession.keepDrape`); `GensingoApp.onTrimMemory` → `trim` + a
  counters line under `GensingoMemory`; `MainActivity.onStart` → `restore`.
- **A16** `terrain3d/MeshSession.kt` held by `FieldViewModel` (ended on the way back to the map and
  in `onCleared`); `Terrain3DView` picks up a held square (no rebuild, no refit, the mesh re-submitted
  to the new GL surface); `reveal` saved with `rememberSaveable`, restored risen only with a square.
  After device run 1 (phase 8): `MainActivity` handles `orientation|screenSize|screenLayout|
  smallestScreenSize` in place, so a rotation recreates nothing; the session covers the rest.
- **A18** `terrain3d/Occlusion.kt` and `MapCamera.eyeWorld`; FINDS, SUGGESTIONS and ME declare
  `OCCLUDED`, drawn by `MeshDraw.CANVAS_OCCLUDED`: faint (35 %) when the terrain hides them.
- **Found on the way:** the same trap as A.2's S4. Two landing tests were appended to the end of
  `CameraStartTest.kt`, whose last class is `MapLayerDefaultsTest`, so the harness entry naming
  `CameraStartTest` never ran them and mutant U2 survived. Moved into the right class, they fail on
  U2 (killed). `HandoffTest`, `SceneGeometryTest` and `Terrain3DTest` hold a single class each
  (checked).

## 7 — Verify (raw output in `docs/eincol/evidence/A.3-*`)

| Check | Result |
|---|---|
| Build | `assembleDebug assembleRelease` **exit 0** on the final code, manifest included (`A.3-02-build.txt`) |
| Tests | `:app:test` **exit 0**: 388 per variant (debug, release, field), 0 failures, 1 skipped (`A.3-04-test.txt`; A.2: 367, +21). Run on the final Kotlin; the manifest change after it touches no unit test |
| Lint | `:app:lint` **exit 0**: 0 errors, 79 warnings, none above the bootstrap baseline by (id, file) (`A.3-03-lint.txt`). Exempt by name: `LogNotTimber` on `MapLogging` (a log adapter) and two log lines (`//noinspection`, `@SuppressLint` on the out-of-memory helper) |
| Witnesses | MapLibre's bytecode (A17); the line-of-sight formula E·s/L (A18); the eye on unproject's rays (A18); a hand-run LRU ledger (A13); a frame-by-frame camera walk across the sink and landing (A15); message shapes captured from the device (I19) |
| Negative controls | **U1–U10: 10/10 killed** (`A.3-07-mutants.txt`, `A.3-08-rerun-U2.txt`; U2 after its tests were moved into the class the harness names) |
| Device (aosp AVD, Android 14 / API 34, x86_64, swiftshader; three runs) | **I21:** your position dot at the screen centre after the first fix (`A.3-device-01`, `-02`; missing in A.2's `-05`, `-10`, `-12`). **I19:** 0 lines with a z/x/y shape in the app's log, three runs; the elevation store logs `DEM tile unavailable at zoom 9: SSLHandshakeException…`. **A11 (blocked since A.2):** a two-finger tilt injected through the emulator console tilted the map to MapLibre's 60° limit, the 3D view opened at it (`ready: … at pitch 60`), and `-04` shows habitat, contours and creeks following the ridges at 60°. **A18:** in that view "You" is drawn faint (its north-facing slope, seen from the south, is steeper than the 30° sight line under ×1.5 relief) and suggestion ① bright. **A13:** `ready: … · memory 37.9/38.0 MB (pinned 28.9) · dem 9.0 · scene 19.9 · textures 9.0 · evicted dem 10`; `am send-trim-memory RUNNING_CRITICAL` → `memory 28.9/9.5 MB (pinned 28.9) · … · evicted dem 46` (runs 1 and 3 identical). **A7 again:** hand-off relief 0.051 and 0.047 at pitch 60. **A16:** see phase 8: not verified on this emulator |

## 8 — Re-evaluate

**What the device found that the design did not:**
1. **A13's first ceiling ran the app out of memory** (run 0, `A.3-06`). A third of the heap let the
   elevation cache fill with the 10-mile scan's tiles while a 3D build needed its arrays: an
   `OutOfMemoryError` at 183 of 192 MB. Fixed with a fifth of the heap and a trim-and-retry on
   out-of-memory; the next two runs built the square with the cache at its ceiling.
2. **A16's first design got the app killed** (run 1, `A.3-10`). The held square did come back after
   the recreation in 45 s, with no rebuild; then MapLibre's renderer finalizer, for the map the
   recreation destroyed, exceeded Android's 10 s watchdog. Phase 4 had rejected `configChanges`
   on a wrong premise (Compose re-lays out by itself). Changed to in-place rotation.
3. **In-place rotation crashed the emulator itself** (run 3, `A.3-11`, `A.3-12`): the qemu process
   died as MapLibre recreated its Vulkan device for the resize under gfxstream/SwiftShader. A guest
   app cannot crash the host on a phone; this emulator cannot witness rotation. **A16 is
   blocked(device)**: its rotation check needs a real phone or a GPU-backed emulator.
4. **I18's emulator symptom was not I18** (re-diagnosed in phase 1): no Play services, so the
   California landing was a fresh default fix.
5. **The same test-placement trap as A.2's S4** (U2): a test appended to a file whose last class is
   another one. Two waves running: the harness should check it (added to I20).
6. **Gesture injection works** through the emulator console (`adb emu event send` with
   `EV_ABS:ABS_MT_*` codes and `EV_SYN:0:0`): `sendevent` on `/dev/input/event2` did not (A.2).
   J18 is half done; the script is in the scratchpad and belongs in `tools/` once J18 is approved.
7. **Two of my own gate runs interfered** (run 1's script outlived the app and tapped "Show flat
   map" in run 2's session). Run 2 was discarded; run 3 ran alone.

**Sovereignty classifier over this wave's claims:**

| Claim | In time | Against a witness | Verdict |
|---|---|---|---|
| Your position is drawn on the map (I21) | ✅ | ✅ device, before and after | sound |
| No tile id reaches the log (I19) | ✅ | ✅ device-captured shapes + three device runs | sound |
| A stale fix cannot steal the landing (I18) | ✅ | ✅ JVM sequence (U2 killed) | sound as a rule; **open** on a Play-services phone |
| The 3D view's gestures match the map's (A17) | ✅ | ✅ MapLibre's bytecode (U3 killed) | sound for the mapping; the device comparison of deltas is **open** |
| No jump on the way back (A15) | ✅ | ✅ frame-by-frame walk (U4 killed) | sound |
| One memory ceiling governs both paths (A13) | ✅ | ✅ ledger tests (U5, U6, U10) + device counters + an OOM fixed | sound (after the fix the device found) |
| Labels are occluded correctly (A18) | ✅ | ✅ sight-line formula (U7, U9) + device | sound for markers; lines stay on top by design |
| Overlays follow the terrain at 60° (A11) | — | ✅ device at a logged 60° | sound |
| The square survives rotation (A16) | ✅ | ❌ the emulator cannot rotate this app | **open**: blocked(device) |

**A18's four statements:** (1) tilt without a jump: A.2's recorded fade + A15's walk; (2) overlays
draped: `-04` at 60°; (3) labels occluded: `-04` ("You" faint, ① bright); (4) every control means
something: `SceneLayersTest` + A.2's device order check, no device toggle yet. **A18 is partial**
until statement 4 has its device run.

**Counter-candidate (one per wave): "A.3 is polish on a view rarely opened"** — answered in phase
2: three of eight candidates were 2D field defects (the missing dot among them), and the device
runs found an out-of-memory crash and a rotation kill that no unit test could have.
