# EINCOL run log (exe.md §7)

One row per wave, plus the bootstrap. Raw output lives in `docs/eincol/evidence/`; this file links
to it and never paraphrases a result it does not link.

| Wave | Candidates | Branch | Commits | Build / lint / test | Mutants rejected | Device | Evidence |
|---|---|---|---|---|---|---|---|
| BOOT | — (bootstrap; drafted J1–J9; registered I17) | `claude/minimal-3d-llm-location-app-yk0lid` | the bootstrap commit | test **green** · lint **red, pre-existing** (1 error, 80 warnings, listed by name) · build not run (not required by §1) | — | **none usable**: the emulator boots, but its package service dies on every APK install | `BOOT-01-toolchain.txt`, `BOOT-03-test.txt`, `BOOT-03-lint.txt`, `BOOT-03-lint-offline-toolfail.txt`, `BOOT-04-locate-before-worktree-cleanup.txt`, `BOOT-lint-baseline.tsv`, `BOOT-06-device.txt` |
| A.1 | A1, A2, A3, A4 · I2, I3 (=Q1), I4 (=Q2), I17 · drafted J10–J14 · A5, A6 re-queued to A.2 | `eincol/A.1` (+ fast-forward of the session branch) | see `docs/eincol/waves/A.1.md` | build **exit 0** · lint **exit 0** (0 errors, 78 warnings; I17 gone) · test **exit 0**, 355 per variant | Q1, Q2 (after the fix), Q3, Q4, Q5: **5/5 killed** | not required (non-visual wave, §13); A3's device check joins A.2 | `A.1-01…09`, `A.1-05-structural.txt` |
| A.2 | A7, A8, A9, A10 (+ A11 at 50°; its 60° shot blocked(device)) · A5+A6, A12 blocked(owner) · registered I18–I21 · drafted J15–J19 | `eincol/A.2` (+ fast-forward of the session branch) | see `docs/eincol/waves/A.2.md` | build **exit 0** · lint **exit 0** (0 errors, 80 warnings, none above baseline; copied file exempt by name) · test **exit 0**, 367 per variant | S1–S10: **10/10 killed** (S4 after its oracle was retargeted) | aosp AVD, API 34 x86_64, swiftshader: order check, warming (12 min of usable map), ready log ×3, **the fade in recorded** (flat terrain aligned with the map mid-fade), risen, return; tilt injection failed | `A.2-01…08`, `A.2-device-01…15`, `A.2-device-warming.mp4`, `A.2-device-fade-in.mp4`, `A.2-device-fade-out.mp4` |

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
