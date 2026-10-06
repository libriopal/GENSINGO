# EINCOL run log (exe.md §7)

One row per wave, plus the bootstrap. Raw output lives in `docs/eincol/evidence/`; this file links
to it and never paraphrases a result it does not link.

| Wave | Candidates | Branch | Commits | Build / lint / test | Mutants rejected | Device | Evidence |
|---|---|---|---|---|---|---|---|
| BOOT | — (bootstrap; drafted J1–J9; registered I17) | `claude/minimal-3d-llm-location-app-yk0lid` | the bootstrap commit | test **green** · lint **red, pre-existing** (1 error, 80 warnings, listed by name) · build not run (not required by §1) | — | **none usable**: the emulator boots, but its package service dies on every APK install | `BOOT-01-toolchain.txt`, `BOOT-03-test.txt`, `BOOT-03-lint.txt`, `BOOT-03-lint-offline-toolfail.txt`, `BOOT-04-locate-before-worktree-cleanup.txt`, `BOOT-lint-baseline.tsv`, `BOOT-06-device.txt` |
| A.1 | A1, A2, A3, A4 · I2, I3 (=Q1), I4 (=Q2), I17 · drafted J10–J14 · A5, A6 re-queued to A.2 | `eincol/A.1` (+ fast-forward of the session branch) | see `docs/eincol/waves/A.1.md` | build **exit 0** · lint **exit 0** (0 errors, 78 warnings; I17 gone) · test **exit 0**, 355 per variant | Q1, Q2 (after the fix), Q3, Q4, Q5: **5/5 killed** | not required (non-visual wave, §13); A3's device check joins A.2 | `A.1-01…09`, `A.1-05-structural.txt` |
| A.2 | A7, A8, A9, A10 (+ A11 at 50°; its 60° shot blocked(device)) · A5+A6, A12 blocked(owner) · registered I18–I21 · drafted J15–J19 | `eincol/A.2` (+ fast-forward of the session branch) | see `docs/eincol/waves/A.2.md` | build **exit 0** · lint **exit 0** (0 errors, 80 warnings, none above baseline; copied file exempt by name) · test **exit 0**, 367 per variant | S1–S10: **10/10 killed** (S4 after its oracle was retargeted) | aosp AVD, API 34 x86_64, swiftshader: order check, warming (12 min of usable map), ready log ×3, **the fade in recorded** (flat terrain aligned with the map mid-fade), risen, return; tilt injection failed | `A.2-01…08`, `A.2-device-01…15`, `A.2-device-warming.mp4`, `A.2-device-fade-in.mp4`, `A.2-device-fade-out.mp4` |
| A.3 | A13, A15, A17, A11 (from A.2) · I18, I19, I21 · A18 partial · A16 blocked(device) · A14 blocked(owner) · registered I22 · drafted J20–J24 | `eincol/A.3` (+ fast-forward of the session branch) | see `docs/eincol/waves/A.3.md` | build **exit 0** · lint **exit 0** (0 errors, 79 warnings, none above baseline) · test **exit 0**, 388 per variant | U1–U10: **10/10 killed** (U2 after its tests were moved into the named class) | aosp AVD, API 34 x86_64, three runs: the dot back (I21), no tile ids (I19), 3D at a logged 60° with an occluded marker (A11, A18), budget counters and trim (A13); found and fixed an out-of-memory crash and a rotation kill; the emulator cannot rotate this app | `A.3-01…12`, `A.3-device-01…04`, `A.3-device-run1-04`, `-05` |
| B.1 | B1, B2, B4, B5, B6, B7 · I10, I11 · I20 partial · B3 struck · registered I23 · drafted J25–J29 · recorded the owner's approval of J20, J21, J23, J24 | `eincol/B.1` (+ fast-forward of the session branch) | see `docs/eincol/waves/B.1.md` | build **exit 0** · lint **exit 0** (0 errors, 79 warnings, none above baseline) · test **exit 0**, 403 per variant | X1–X12: **12/12 killed** (X6 retargeted, X12 added after the device run) | aosp AVD, API 34 x86_64, a cached tile removed: before (A.3 build) the flat slab with an invented creek and two walls culled; after, the hole, the hatch, the legend rows, 80° clean; run 1 found the north and west walls dropped by a missing halo (fixed); run 2 (final code) all four walls drawn from every side | `B.1-02…05`, `B.1-07…11`, `B.1-device-before-02…05`, `B.1-device-after-01…07`, `B.1-device-after2-02…05` |
| M.1 | the owner's directive J30, J31, J32 + J.2 (J20, J21, J23, J24) · A6 by J30 · A5, A12, A14, I22 struck by it · I20 more (`--check`) · B12 reworded · drafted J33–J37 | `eincol/M.1` (+ fast-forward of the session branch) | see `docs/eincol/waves/M.1.md` | build **exit 0** · lint **exit 0** (0 errors, 54 warnings, none above baseline) · test **exit 0**, 437 per variant | Y1–Y23 + 32 earlier mutants on changed files: **55/55 killed** | aosp AVD, API 34 x86_64: upgrade 5 → 6 over the B.1 build, and on its own file put back to 5 with 310 track points (144/144 cells); opens in 3D, relief at 52–88 s, habitat at 471 s; the 48 km square with the ring and ten suggestions; a walk's wash; "Back to start: 199 m S"; battery mode at 15 %; 0 tile ids, 0 coordinates in the log. Found three defects (a wrong first landing never corrected, the wash in the creeks' blue, drains covering the 48 km square): fixed (their device re-check deferred by the owner for the APK) | `M.1-01…11`, `M.1-device-01…07` |

---

## BOOT — 2026-10-03, at HEAD `c95815f`

### §1 steps, as run

1. **Build entry point.** `gradlew` is present. `settings.gradle.kts` includes only `:app`
   (rootProject "GinsengTerra Field Map"). The §7 commands stand as written.
2. **Toolchain.** Gradle 8.13 (wrapper), Kotlin 2.0.21 in the Gradle runtime, JDK 21.0.10.
   `$ANDROID_HOME` is unset; `local.properties` has `sdk.dir=/opt/android-sdk`. The build runs.
3. **Baseline on the untouched tree.** The only addition at the time was the untracked `exe.md`,
   which is outside the build.
   - `./gradlew :app:test`: **BUILD SUCCESSFUL in 4m 22s.** 339 tests in each of
     `testDebugUnitTest`, `testReleaseUnitTest` and `testFieldUnitTest`, 0 failures, 1 skipped each
     (the full-scan timing fixture).
   - `./gradlew :app:lint`: **BUILD FAILED, pre-existing: 1 error, 80 warnings.**
   - My first lint run added `--offline`, which §1 does not use. It failed in
     `:app:generateDebugAndroidTestLintModel` because four declared androidTest dependencies were
     not in the offline cache (`androidx.test.ext:junit:1.2.1`, `espresso-core:3.6.1`,
     `fragment:1.1.0`, `collection-jvm:1.4.2`). That is a tooling failure, not a lint verdict
     (EINCOL §4: a dropped connection is not a refusal). Re-run exactly as §1 states, online; both
     outputs are kept.
4. **§4 files.** All 14 found at the expected paths, with the line counts the prior inventory
   gave (it was written from this HEAD). §4 is rewritten in place with the paths, the terrain3d
   breakdown, and three facts the prior table lacked:
   - **A6's "mode enum" does not exist.** The mode is `FieldViewModel.view3d: Boolean` plus the
     switch in `MainScreen`.
   - **A shared camera value already exists** (`ViewCamera`, from ARCHITECT iteration 1), but each
     view still keeps its own live camera.
   - **1,636 lines of engines have no caller.**
5. **Corpus.** 113 Kotlin files: 70 main, 42 unit test, 1 instrumented. 12,120 main lines, 7,278
   test lines.
   - The raw `find` count was 428, because three contractor worktrees from ARCHITECT iteration 1
     (127 MB) were still checked out in `.claude/worktrees/`.
   - All three were merged into HEAD with no tracked changes, so they were removed (`git worktree
     remove`, `git branch -d`), together with an old scratch worktree.
   - The locate output taken before the cleanup is kept as evidence.
6. **Device.** Nothing was attached (`adb devices -l` was empty). The `field` AVD was booted:
   `emulator-5554`, Android 14, `sdk_gphone64_x86_64`, GLES 3.0 through SwiftShader (software
   rendering, no `/dev/kvm`). Its package, activity and window services came up. **But installing
   the shipped build failed both times:**
   - `cmd: Can't find service: activity` during the streamed install;
   - then, with the services back, `cmd: Failure calling service package: Broken pipe (32)`.
   - The same failure stopped the device gate in EINCOL_REPORT Phase 9 (Open 0).

   The log shows `system_server` monitor contention of 1–2.5 s and a Bluetooth stack abort around
   the install: an emulator starved of CPU, not an app defect. The app never ran (0 packages
   installed). **Device status: none usable → every visual candidate is blocked(device).**
   One remedy is untried: the `aosp` AVD at 4 GB and 4 cores, the configuration that completed the
   Phase 8 device run. The next visual wave tries it first. Even working, this emulator could
   never back a performance claim (software GL), so C.1–C.3 also need a real phone (C18). The
   emulator was stopped to free the CPU.
7. **Write-back.** §4 is rewritten in place; this log and `docs/eincol/evidence/` are created; §12
   is updated.

### Pre-existing failures, exempt by name (§1.3, §7.2)

**Lint error (1).** This is a real defect, and it is ARCHITECT iteration 1's code: the 3D view is
handed the camera by reading `vm.camera.value` during composition.

| Severity | Id | Location |
|---|---|---|
| Error | `StateFlowValueCalledInComposition` | `app/src/main/java/com/ginsengo/steward/ui/MainScreen.kt:120` |

Per §7 it is not fixed inside bootstrap. It is registered as candidate **I17**. Wave A.1 (one
`CameraState`) replaces that exact line, so A.1's lint must show it gone. If it does not, I17 runs
on its own.

**Lint warnings (80).** Exempt by id and location, as listed in `BOOT-lint-baseline.tsv`:

| Id | Count |
|---|---|
| `LogNotTimber` | 34 |
| `GradleDependency` | 13 |
| `UseKtx` | 10 |
| `UnusedResources` | 9 |
| `NewerVersionAvailable` | 8 |
| `AndroidGradlePluginVersion` | 2 |
| `ObsoleteSdkInt` | 2 |
| `ChromeOsAbiSupport` | 1 |
| `RedundantLabel` | 1 |

**Tests:** none failing.

### Findings (Load phase), and what they change

| # | Finding | Consequence |
|---|---|---|
| L1 | **exe.md §0 and §1.8 disagree.** §0 says to bootstrap and then take a wave; §1.8 says to stop after bootstrap and start the first wave in the next session | Resolved in favour of the more specific §1.8. This session stops after bootstrap |
| L2 | **No mode enum** (see §4) | A6 is re-targeted at the `view3d` flag and its switch |
| L3 | **One camera value exists; two live cameras remain.** `ViewCamera` crosses the switch, but MapLibre's camera and the 3D view's local `cam` both stay authoritative while on screen | A1's real work is removing the two live cameras, not creating a data class |
| L4 | **1,636 lines of unwired engines.** `HabitatChecklist`, `SoilSuitability`, `AstroEphemerisEngine` + `EsiMatrixModel`, `HabitatEngine` + `HabitatModel` + `OnnxGraphWeights` (bundled ONNX), `PlantVerification`, `DemGrid` | The cheapest information gain in the repository: J1 (wire the checklist), J6 (fix the radiation engine's constant sky-view factor), J7 (wire or strike each one) |
| L5 | **The radiation engine scores "nocturnal moonlight exposure" with no cited evidence**, and its sky-view factor is the constant 0.85 | Wiring it as-is would break invariants 2 and 5; J6 and J7 |
| L6 | **The branch rule.** §8 says `eincol/<wave-id>` off the default branch; this session's push target is `claude/minimal-3d-llm-location-app-yk0lid` | The bootstrap lands on the session branch (not the default branch; not a wave). Waves follow §8 |
| L7 | **The lint baseline is debug only.** `:app:lint` runs `lintDebug` on AGP 8.11 | §7.2's "no new findings" is measured against `lintDebug`; a wave touching release-only code runs `:app:lintRelease` as well |

### Candidates drafted this iteration (owner directive: at least five per iteration)

J1–J9 are in exe.md §11, group J, status `proposed`.

- **Research behind them:**
  - McGraw 2001, *Biological Conservation*: plant stature declined under harvest pressure → J3, J5.
  - Burkhart 2013, *Agroforestry Systems*: calcium indicator species → J2.
  - USDA Forest Service 2011: 70–90 % shade → J6.
- **Open-source code to copy:**
  - RVT_py (Apache-2.0) for sky-view factor → J6.
  - Tobler's hiking function, a formula → J5.
- **Unwired code to reuse:** J1, J7, J8.

**Owner decision needed:** approve J1–J4 as wave J.1 (recommended right after F.1), and decide J8
(a new network destination) and J9 (moving finds between phones).

### Sovereignty classifier over the bootstrap's claims (§3)

| Claim | In time | Against a witness | Verdict |
|---|---|---|---|
| All §4 paths exist with the stated sizes | ✅ measured at `c95815f` before any change | ✅ `BOOT-04-locate…txt` is re-runnable | sound |
| The unit suite is green on the untouched tree | ✅ run before any source change | ✅ `BOOT-03-test.txt` (raw) | sound |
| Lint has exactly 1 error and 80 warnings, pre-existing | ✅ untouched tree | ✅ `BOOT-lint-baseline.tsv`, re-runnable | sound |
| The 1,636 engine lines have no caller | ✅ | ✅ a symbol search with the defining file excluded, re-runnable. A reflection or ServiceLoader call would evade it: none exists in this codebase (no `Class.forName` on these names) | sound |
| No usable device: the emulator cannot install the app | ✅ two attempts this session, plus Phase 9 | ✅ `BOOT-06-device.txt` (raw adb output) | sound, for the `field` AVD at 3 GB / 2 cores only; the `aosp` AVD at 4 GB / 4 cores is untried |
| An emulator could not back a performance claim even if it worked | ✅ | ❌ an engineering judgement (software GL, no KVM), not a measurement | **open**, stated |

---

## A.1 — 2026-10-03 · the one camera

Full record (all eight EINCOL phases, the distribution with the tail worked first, rejected
alternatives, contracts, and the sovereignty classifier): `docs/eincol/waves/A.1.md`. In brief:

- **Built.**
  - **One `SharedCamera`**, owned by the view model, seeded from `CameraStart` (dead until now).
    Both views mirror it under an epoch rule: gestures report, app actions move, and each move is
    applied once.
  - **One `Projection`** replaces five Web Mercator copies and fixes a linear-latitude defect in
    `latAtRow`.
  - **A conformance test** shows the GL mesh and the projection agreeing within 0.009 px; its
    stale-camera control fails by 534–2,755 px.
- **Found by the evaluators:**
  - Mutant **Q2 survived**: the app's Terrarium decoder had never been tested, because the
    fixtures were Python-decoded. It was fixed with a spec-vector test, after which Q2 was killed.
  - **Bootstrap's §4 claimed the opposite**, and is corrected.
  - The harness overwrote the debug test results after `:app:test`; the verdict is taken from exit
    codes and the untouched variants.
- **Counter-candidate I2 (the merge is a rewrite):** no. One structural change remains (GL in a
  `TextureView`, A.2), and **A14 (one frame clock) needs NDK as written: a replacement is
  proposed to the owner.**
- **Drafted this iteration (owner directive):**
  - J10 deer-browse refuges, as a hypothesis layer (McGraw & Furedi 2005, *Science*);
  - J11 walk-the-band contour route;
  - J12 honey-hole detector over your own finds (prong-age relation);
  - J13 look-alike guard;
  - J14 seed-planting record with a maturity clock.

```
conformance: zoom 15.41 bearing  59.9 pitch 68.9 -> worst 0.0038 px over 25 points
conformance: zoom 14.34 bearing   5.3 pitch  4.8 -> worst 0.0003 px over 25 points
conformance: zoom 14.55 bearing 306.3 pitch 20.8 -> worst 0.0011 px over 25 points
conformance: zoom 14.65 bearing 235.4 pitch  7.6 -> worst 0.0010 px over 25 points
conformance: zoom 14.09 bearing 167.3 pitch  2.0 -> worst 0.0002 px over 25 points
conformance: zoom 16.39 bearing 123.2 pitch 53.6 -> worst 0.0070 px over 21 points
conformance: zoom 15.82 bearing 154.7 pitch 53.8 -> worst 0.0047 px over 25 points
conformance: zoom 15.58 bearing 140.0 pitch 55.3 -> worst 0.0029 px over 25 points
conformance: zoom 16.36 bearing 318.1 pitch 21.4 -> worst 0.0089 px over 23 points
conformance: zoom 15.43 bearing 141.8 pitch  3.5 -> worst 0.0018 px over 25 points
conformance (stale camera, must fail): state 1..9 -> worst 534.2 .. 2754.9 px
```

## A.2 — 2026-10-04 · one surface

Full record (eight phases, the tail worked first, seven rejections, contracts, the classifier):
`docs/eincol/waves/A.2.md`. In brief:

- **Built.**
  - **A measured hand-off (A7).** The largest relief scale at which the mesh agrees with the
    flat map within 2 dp, by bisection; pinned to the pinhole formula and to the real Boone tile.
    The register asked for a tilt; on real terrain no tilt agrees (22 px straight down, 101 px at
    60°), so the hand-off is a relief.
  - **One surface (A8).** The 3D view moved to a `TextureView` host (GPUImage's `GLTextureView`,
    Apache-2.0, copied with a render loop removed) and joined the map in one stack: it warms
    under the map, fades in at the hand-off relief, then rises; back, it sinks, lands the camera
    flat under the cover and fades out. The map stays usable while the terrain builds.
  - **A scene description with a depth policy (A9, A10).** One `SceneLayer` registry; both
    backends implement it through exhaustive `when`s; every layer declares `Depth`; the sheet,
    the map's visibility and the 3D bake read it. Hillshade and Heat opacity now mean something
    in 3D; the scan ring is drawn there.
- **Found by the evaluators:** the tilt hand-off is ill-posed (measured); the copied GL host's
  render loop; a stale-move replay on the way back (my review, fixed before verification); S4's
  oracle in the wrong class (a test that had never run); your position dot missing on the 2D map
  (**I21**, pre-existing); two defects in the device gate itself; the harness's duplicate ids and
  SIGTERM hazard (**I20**).
- **Blocked on the owner:** A5 + A6 (tilt into 3D vs. keep the button: the 50° default and the
  60° limit collide) and A12 (approve OkHttp as a direct dependency, already inside the APK).
- **Drafted this iteration (owner directive):** J15 canopy gate (NLCD tree canopy, public domain;
  §9 decision); J16 a cited slope band (its figure **unverified** until a primary source is read);
  J17 keep the last 3D square warm; J18 gesture injection for device gates; J19 a paper backup of
  the day's plan.

```
Boone z15 full-relief disagreement by pitch: 0°=22.1px, 15°=50.6px, 30°=73.4px, 45°=90.1px, 60°=101.1px
Boone z15, pitch 50: hand-off relief 0.0216 @2px, 0.0567 @5.25px, 0.3437 @32px
device: Terrain3D: ready: hand-off relief 0.056 at pitch 50 (tolerance 5.3 px)   (00:22:33, 01:09:18, 01:22:25)
device: fade in, frames #9 map -> #10-#11 3D arriving flat over the map -> #12 covered -> #13-#14 relief rising -> #15 risen (2 s)
device: GensingoMap: layers: style order matches the scene (11 layers)
mutants: S1 S2 S3 S4 S5 S6 S7 S8 S9 S10 -> killed 10 / 10
```

## A.3 — 2026-10-04 · one map, finished + the field defects

Full record: `docs/eincol/waves/A.3.md`. In brief:

- **Field defects fixed first.** Your position dot was missing from the 2D map whenever the GPS
  answered after the style (I21: the data push lived in a memoized `update` lambda); tile ids were
  in logcat from the app and from MapLibre's native log (I19: redacted at both); a stale cached fix
  could steal the first landing on Play-services phones (I18: provisional landing).
- **One map, finished where it can be.** One memory ceiling across both views' caches with trim
  handling (A13); the way back to the map proved jump-free frame by frame (A15); the 3D view's
  gestures on MapLibre's own constants, read from its bytecode (A17: tilt was 50 % too fast);
  markers hidden by a ridge drawn faint (A18's occlusion); overlays shown following the terrain at
  a logged 60° (A11, open since A.2).
- **What the device caught:** the first memory ceiling ran a 3D build out of memory (fixed: a fifth
  of the heap, trim and retry); recreating the activity on rotation got the app killed by MapLibre's
  finalizer watchdog (fixed: rotation handled in place); the emulator itself then crashed rotating
  this app (A16 blocked on a real device). Two-finger gesture injection now works.
- **Drafted this iteration (owner directive):** J20 strong ground you have not walked; J21 the way
  back to the car; J22 find photos with GPS tags stripped; J23 honour "remove animations"; J24 a
  battery field mode.

```
device: Terrain3D: ready: hand-off relief 0.051 at pitch 60 (tolerance 5.3 px) · memory 37.9/38.0 MB (pinned 28.9) · dem 9.0 · scene 19.9 · textures 9.0 · evicted dem 10
device: GensingoMemory: trim level 15: memory 28.9/9.5 MB (pinned 28.9) · scene 19.9 · textures 9.0 · evicted dem 46
device: lines with a z/x/y shape in the app's log: 0 (three runs)
device: run 0  OutOfMemoryError: Failed to allocate a 13107216 byte allocation (183 of 192 MB) -> ceiling a fifth, trim and retry
device: run 1  FinalizerWatchdogDaemon: MapRendererFactory$1.finalize() timed out after 10 seconds -> rotation in place
jvm:    way back from 75°: worst per-frame step 5.18 px, landing 0.0000 px
mutants: U1 U2 U3 U4 U5 U6 U7 U8 U9 U10 -> killed 10 / 10
```

## B.1 — 2026-10-06 · a mesh that can be believed

Full record: `docs/eincol/waves/B.1.md`. In brief:

- **The owner approved J20, J21, J23 and J24** (2026-10-06); they are wave J.2, the next cursor.
- **The edge of the 3D model** (B1, B2): two of its four walls faced inward, so seen from the north
  or the east the model had no near wall and the sky showed under its edge (seen on the device
  before the fix). All four now face outward, held by an edge-use crack detector and a projection
  check from eight bearings. B3 (stitched LOD) is struck: the view draws one uniform grid.
- **Missing elevation** (B4, B5): a tile that could not be loaded used to be drawn as a flat slab,
  painted as habitat, crossed by invented creeks, and could be ranked. It is now a hole in 3D and a
  hatch on the 2D map, with a legend line saying so, and the ranking, the learner's background and
  the creek search never use it.
- **One slope** (B6): the 3D lighting now uses the habitat score's own Horn stencil.
- **Depth** (B7): a near plane from the ground in view; a 16-bit depth buffer now resolves
  0.11–1.70 m at the far ground instead of 13.5–17.5 m.
- **What the device caught:** B4's first rule dropped the north and west walls when the halo tiles
  there were not cached (fixed, with a test); the 2D raster takes 8 minutes on this emulator; the
  host's memory cgroup killed the emulator when Gradle ran alongside it.
- **Drafted this iteration (owner directive):** J25 no holes on the ridge; J26 light left in this
  hollow; J27 the climb before you walk it; J28 what your own patches have in common; J29 buzz on
  arrival.

```
device before: HD 3D · 3.9 m elevation · 3.0 × 3.0 km · 42 km of creeks & drains · 1 tile(s) missing, flattened
device after:  HD 3D · 3.9 m elevation · 3.0 × 3.0 km · 35 km of creeks & drains · 1 tile(s) without elevation, left as holes
device after:  Terrain3D: ready: hand-off relief 0.056 at pitch 50 (tolerance 5.3 px) · depth 24 bits · holes (no elevation) · memory 37.8/38.0 MB (pinned 30.0) · dem 7.8 · scene 21.0 · textures 9.0 · evicted dem 49
device after:  7.8 m cells · 19/24 tiles · hatched: no elevation data · contours 20 m
device:        habitat: rasterised 768x768 in 494172 ms (terrain analysis 205574 ms, scoring 280321 ms)
device:        lines with a z/x/y shape in the app's log: 0 (every run)
host:          Memory cgroup out of memory: Killed process 461 (qemu-system-x86)
jvm:           16-bit depth step at the far ground, pitch 15-80: 0.11-1.70 m (terrain-aware) vs 13.5-17.5 m (fixed 48 px)
mutants:       X1 … X12 -> killed 12 / 12
```

## M.1 — 2026-10-06 · one map, in 3D: travel memory, battery

Full record: `docs/eincol/waves/M.1.md`. In brief:

- **The owner's directive:** *"add a persistent gps travel memory layer that shows everywhere you
  have been and remove the 2d map make 3d the main map with full battery optimisation"*, run with
  the approved J.2 (J20, J21, J23, J24).
- **One map** (J30): the flat map, its switch and the cross-fade are deleted (709 + 127 + 89 lines
  and their tests). The 3D view opens first and builds its square at the zoom the camera needs:
  3 km at 3.9 m cells down to 48 km at 62 m cells, which holds the whole 10-mile radius. Relief
  shows first, the habitat colour after, on the same mesh.
- **Travel memory** (J31): every fix the app receives (map open, or Track recording) is kept as a
  ~10 m cell in a new table (schema 6); recorded tracks are folded in at upgrade with one SQL
  statement whose key is proved equal to the app's own. Drawn by the GPU from a small mask, so a
  new cell does not re-bake the ground texture. No background location: the memory fills only
  from fixes the app already receives.
- **Battery** (J32, J24): the on-screen GPS request now follows the real battery, charger and
  stillness (it was a high-accuracy fix every 3 s for as long as the map was open; still, it is
  now one every 15 s); frames paced to 30 a second; memory writes batched; a battery mode at the
  owner's threshold (default 25 %) with half the mesh, a quarter of the texture, no map snapshot
  and 20 frames a second, and a chip saying so. No saving is claimed in numbers (C18).
- **J20** greys ground within 15 m of where you have been; **J21** "Back to start: 1.2 km NE"
  while recording; **J23** nothing animates any more, kept so by a source scan.
- **What the device caught:** a wrong first landing is never corrected (a cell-tower first fix now
  lands only provisionally); the wash was the creeks' blue (now pale); the 48 km square was covered
  in drains drawn 11× too wide (lines under a quarter texel are left out).
- **The mutation harness** gained `--check`, which found three mutants (P6, H2, H4) silently stale
  since earlier waves.
- **Drafted this iteration (owner directive):** J33 when you last walked it; J34 how much of this
  cove you have walked; J35 ground on screen at once after a restart; J36 walk the gaps; J37
  battery left at today's real rate.

```
device:  schema before 5 · track points 0 · schema after open 6
device:  relief: level 15 in 51803 ms (run 1, after "Centre on me") · relief: level 15 in 88067 ms (run 2)
device:  habitat: level 15 in 470710 ms · ready: level 15 (3.0 km) at pitch 50 · depth 24 bits · holes (no elevation)
device:  walk of 50 fixes -> 56 cells · "● REC 0.20 km" · "Back to start: 199 m S"
device:  pinch out -> relief: level 11 in 26407 ms · habitat: level 11 in 138624 ms (47.8 x 47.8 km)
device:  15 %, discharging -> rebuilt in 32 s · "Battery saver: lighter 3D (15 %)" · relief 18765 ms · habitat 91385 ms
device:  upgrade with 310 track points (308 within 30 m) -> 144 cells = SQLite's count; 0 missing; first/last 144/144; synthetic 136 = Python's 136
device:  lines with a z/x/y shape: 0 · lines with a coordinate shape: 0
mutants: 55 run (Y1-Y23 + every earlier mutant on a changed file) -> killed 55 / 55; --check: all 114 apply
```
