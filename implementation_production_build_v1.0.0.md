# implementation_production_build_v1.0.0

**GENSINGO — production build plan, submitted for independent audit before execution.**

Status: **DRAFT, UNEXECUTED.** Nothing in §6–§10 has been built. This document exists to be
attacked first, per EINCOL.md §5 ("hand it to an evaluator you do not control") and the
explicit instruction to audit before acting.

---

## 0. Two things I must state before anything else

### 0.1 No MCP list was supplied

The instruction was *"use the following mcps and draft 5+ ways that each mcp can be
utilised"* — but **no list followed in the message text**. Rather than invent one, §5 enumerates
the MCP servers **observably connected to this session** and works from that.

**Resolved after drafting.** The servers subsequently attached to the session are exactly:
`Adobe_for_creativity`, `Canva`, `Figma`, `Scite`, `Supabase`, `Three_js_3D_Viewer`,
`Trimble_SketchUp`, `You_com`, `github`, `Claude_Code_Remote` — the same ten §5 enumerates, no
more and no fewer. The ambiguity is closed and §5 needs no replacement. Recording it anyway,
because "I guessed the list and happened to be right" and "the list was measurable" are
different epistemic states and only the second one is what happened.

### 0.2 A true EINCOL rung-3 auditor is NOT available

EINCOL §2 rung 3 is *"an independent model, **different vendor**, shown the claim WITHOUT
your reasoning."* Measured:

```
env | grep -iE "COHERE|OPENAI|GEMINI|MISTRAL|TOGETHER|GROQ|PERPLEXITY|AI21" → no keys
only ANTHROPIC_BASE_URL is set
```

There is **no different-vendor model reachable from this environment.** This is the same wall
PRD §14 hit ("integration credits exhausted").

**What I will use instead, labelled honestly as a degraded rung 3:** a Claude subagent with a
**cold context**, shown the *claims only* and **not my reasoning**. It is weaker than rung 3
because it shares my vendor and model family, so correlated blind spots survive. It is
stronger than rung 4 (me arguing with myself) because it does not inherit the chain of
thought that made these claims feel true — which EINCOL §2 identifies as the key control.

**I will not call this "independent" without that qualifier.** Presenting a same-vendor critic
as rung 3 would be the self-witness failure this entire project has been correcting for.

---

## 1. Measured current state

Not asserted — counted, at commit `dde060b`.

| Metric | Value |
|---|---|
| Main Kotlin files / LOC | 48 / 8,001 |
| Test files / LOC | 10 / 2,050 |
| Tests passing | **106 / 0 failures** |
| Bundled assets | 19 files, 3.2 MB |
| Compose screens | 9 |
| `app-release.aab` | 59.4 MB, apksigner v2 verifies |
| Shaders | compile clean under `glslangValidator` |
| minSdk / targetSdk | 26 / 34 |
| Permissions in shipped APK | 7 (5 declared + 2 library-merged) |
| Telemetry/analytics classes in APK | **0** |

### 1.1 PRD coverage, verified by symbol presence *and* by build

| PRD feature | State |
|---|---|
| Workflow A — Habitat Reading | present |
| Workflow B — Plant Verification | present |
| Workflow C — Log Patch (CameraX) | present |
| Workflow D — My Patches | present |
| Workflow E — Stewardship Guide | present |
| Settings + provenance + export | present |
| Phase 2 — ONNX + geofence + season | present |
| **Phase 3 — offline tile pre-download** | **ABSENT** |
| Phase 3 — opt-in Firebase sync | **not implemented** (Settings states the posture; correct per PRD "never default") |
| Release signing config | present |
| **On-device verification** | **NEVER RUN** |

---

## 2. The five findings this build already rests on

Carried forward so the auditor can attack the foundations, not just the plan.

1. **ONNX graph weights slope and aspect at exactly 0.0.** The DEM slope/aspect pipeline
   PRD §8.1 mandates cannot move the score by one bit. Its two dominant inputs
   (canopy 1.2, moisture 1.0) are §8.1-defined derivations of the user's own checklist, so
   the "research-grade estimate" largely restates the digger's answers.
2. **FWS publishes no per-state season end dates.** PRD §6.2's example fills `season_end`
   from "FWS 2025" anyway, and would have been wrong (NC/VA/TN/KY run to Dec 31, not Nov 30).
   5 of 20 jurisdictions now carry a sourced closing date; 15 say "confirm with <agency>".
3. **The terrain forecast must not be monotonic.** "Lower = wetter = better" paints creek
   bottoms as the best ground; extension guidance says ginseng will not grow in waterlogged
   soil or leaf-filled depressions. Wetness/slope/position are optimum **bands**.
4. **MapLibre Android exposes no terrain API at any version**, so 3D is a GLSurfaceView
   overlay reconstructing MapLibre's camera, gated by a runtime alignment witness.
5. **`project()` used the float32 matrix** — a constant ~1.9 m ground error at every zoom,
   which meant the alignment *instrument* was spending a fifth of its own tolerance on
   rounding.

---

## 3. Known-open items inherited (21)

Items 1–21 in `EINCOL_REPORT.md`. The load-bearing ones for a production build:

- **#5 / #15 / #19 — nothing has ever run on a device.** The single largest gap.
- **#4 — R8 minification off for release.**
- **#1 — protected-area polygons are approximate bounding boxes** (NPS service 404'd).
- **#2 — 14 of 19 state closing dates unsourced.**
- **#3 — PRD §5.2 fonts (Plus Jakarta Sans / Inter) not bundled.**
- **#8 — universal APK 133 MB** (AAB splits to ~a quarter).
- **#12 — forecast is expert-weighted, never validated against occurrence data.**
- **#18 — 3D overlay draws above patch pins.**
- **#20 — rebuild granularity is whole-mesh.**

---

## 4. What "finish as the implementation plan directed" actually means

PRD §13 steps 1–12. Steps 1–9 and 11 are done. Outstanding:

- **§13.10 / §13.12 — build the release AAB and validate on emulator + physical device.**
  The AAB builds and signs. **Validation has never happened.**
- **PRD Phase 3** — offline tile caching, data export (done), UI polish.

So the honest reading: the implementation plan is **code-complete and verification-incomplete.**
A production build that has never executed a frame is not a production build.

---

## 5. MCP utilisation matrix — 10 servers, 5+ uses each

Observably connected. Each entry is scored:
**S** = would genuinely ship in v1.0.0 · **L** = useful later · **✗ CONFLICT** = would
violate the app's privacy model and must not ship.

### 5.1 `Figma` — design system round-trip
1. **S** Export the §5.1 palette + §5.2 type scale as Figma variables, making the design
   system reviewable outside code.
2. **S** `generate_diagram` (FigJam) for the Workflow A→E state machine, as onboarding doc.
3. **S** Mock the 3D/heatmap layer panel at 400 px width to catch the small-screen crowding
   the PRD §5.5 thumb-bar risks.
4. **L** `get_code_connect_map` to bind `GenCard`/`StatusPill`/`ProvenanceTag` to Figma
   components so drift is detectable.
5. **L** Generate Play Store feature graphic + screenshot frames from real UI.
6. **S** Contrast-audit the palette against WCAG in Figma rather than trusting PRD §5.1's
   claimed ratios — *those ratios have never been verified and should be* (see §6.2).

### 5.2 `Canva` — store presence and field print
1. **L** Play Store feature graphic (1024×500) in the emerald/obsidian palette.
2. **L** Screenshot frames with captions for the 8 required Play listing images.
3. **S** A printable one-page field card: prong/scar aging + the 9 stewardship rules, as a
   PDF the app can ship in `assets/` for offline printing.
4. **L** State-regulation summary card per jurisdiction.
5. **L** Social/education graphics for United Plant Savers-style outreach.
6. **✗ CONFLICT** Anything that ingests user patch photos. Canva is a cloud service; patch
   photos must never leave the device (§8.3).

### 5.3 `Adobe_for_creativity` — asset pipeline
1. **S** `image_crop_and_resize` + `image_apply_auto_tone` on the 12 companion-plant photos:
   currently 3.2 MB of unoptimised Commons originals at inconsistent exposure.
2. **S** `image_remove_background` to produce clean prong-count diagrams (1/2/3/4-prong)
   for Workflow B, which currently has **no illustrations at all** — a real PRD §4.3 gap.
3. **S** `image_vectorize` the launcher mark for crisp adaptive-icon layers.
4. **S** `font_search` / `font_recommend` to find a *bundleable, freely-licensed* substitute
   for Plus Jakarta Sans / Inter, closing open item #3 honestly.
5. **L** `document_render_layout` for the printable field card.
6. **✗ CONFLICT** Any round-trip of user photos through Creative Cloud.

### 5.4 `Three_js_3D_Viewer` — terrain verification surrogate
1. **S** **The most valuable MCP here.** Render the exact `TerrainMesh` vertex/index buffers
   in Three.js to *see* the mesh — the first visual check of geometry that has never been
   drawn. Directly attacks open item #15.
2. **S** Verify skirt geometry and winding visually; a flipped triangle is obvious in a
   render and invisible in a unit test.
3. **S** Confirm normals by lighting the mesh — inverted normals look black.
4. **S** Reproduce the MVP in Three.js and compare a projected point set against
   `MapCamera.project()` — a **second implementation of the camera**, i.e. a genuine witness
   for the one thing currently pinned only by invariants.
5. **L** Visualise the suitability tint on real terrain to sanity-check the colour ramp.
6. **L** Show TPI/TWI fields as surfaces to check for tile-seam artefacts.

### 5.5 `Trimble_SketchUp` — marginal
1. **L** Model a reference hillside with known slope/aspect to cross-check terrain indices.
2. **L** Build a synthetic cove for test fixtures with exactly-known geometry.
3. **L** Visualise slope-position bands physically for the guide.
4. **L** Export a scale figure for the prong-height guide.
5. **L** Terrain block diagram for onboarding.
   **Honest assessment: weakest of the ten for this app.** A CAD modeller has little to offer
   a 2D-map field companion, and §5.4 covers 3D verification better. I will say so rather
   than pad the list.

### 5.6 `Scite` — the scientific backbone
1. **S** Validate the six terrain-index weights against occurrence literature — open item
   **#12**, the forecast's largest scientific weakness.
2. **S** Source the **14 missing state closing dates** where peer-reviewed or agency
   literature carries them (item #2).
3. **S** Check the fern-allelopathy claim now shipping in the companion data — it came from
   an extension PDF, not a primary source.
4. **S** Verify Burkhart's 3,360 kg/ha calcium threshold against the primary paper rather
   than a secondary summary.
5. **S** `editorialNotices` check on every cited paper — a retracted source behind a shipped
   claim is exactly the failure this project keeps finding.
6. **L** Seed a Collection as the app's citation appendix.

### 5.7 `github` — release engineering
1. **S** CI workflow: `assembleDebug` + `testDebugUnitTest` + `validate_shaders.sh` on push,
   so "it builds" stops being a claim I make by hand.
2. **S** Open issues for all 21 open items so they outlive this session.
3. **S** PR with the full audit trail for review.
4. **L** Release with the AAB attached and `EINCOL_REPORT.md` as notes.
5. **L** `run_secret_scanning` before publishing (keystore hygiene).
6. **S** Branch protection requiring the test job — the negative controls only matter if
   something enforces them.

### 5.8 `Supabase` — ✗ mostly CONFLICT
1. **✗ CONFLICT** Patch sync. PRD §8.3 is explicit: local-first, no server, opt-in only.
   Wiring a backend would break the app's central promise.
2. **L** *Aggregate, anonymised* stewardship statistics — only with genuine opt-in and
   k-anonymity, and even then patch coordinates must never transit.
3. **L** Server-side canonical state-regulation table so law changes ship without an app
   update — **regulations are public data, not user data**, so this is the one clean fit.
4. **L** Crash/diagnostic sink — conflicts with "no analytics in local-only mode".
5. **L** Auth for a future Phase 3 opt-in sync.
   **Honest assessment: the single most dangerous MCP here.** Its natural use directly
   violates §8.3. Listed with the conflict stated rather than omitted.

### 5.9 `You_com` — research breadth
1. **S** Source the 14 missing state closing dates from agency sites (item #2).
2. **S** Find real NPS/USFS boundary data to replace approximate boxes (item #1).
3. **S** Check current-season regulation changes before shipping legal content.
4. **L** Survey competing apps for feature gaps.
5. **L** Verify Play Store policy specifics for location/camera declarations.
6. **S** `you-contents` to extract agency regulation pages that resisted PDF extraction.

### 5.10 `Claude_Code_Remote` — orchestration
1. **S** Spawn the audit subagent for the degraded rung 3 in §0.2.
2. **S** Parallel sibling sessions per verification track (assets / science / build).
3. **L** Scheduled re-check of regulation sources each season.
4. **L** Watch the PR and drive CI to green.
5. **L** Long-running asset optimisation off the critical path.

**Matrix summary: 3 servers are load-bearing for v1.0.0** (Scite, Three.js, github),
**4 are useful** (Figma, Adobe, You.com, Claude_Code_Remote), **1 is marginal** (SketchUp),
**2 carry real conflicts** (Supabase, Canva) that must be stated in the build record rather
than quietly skipped.

---

## 6. Proposed work, in dependency order

### 6.1 Research (Scite + You.com)
- 14 state closing dates; verify fern allelopathy; verify calcium threshold; retraction check;
  weight validation against occurrence literature.
- **Acceptance:** every shipped claim traces to a retrieved source, or is downgraded from
  VERIFIED. No new claim ships unsourced.

### 6.2 Design and visual polish
- **Verify PRD §5.1's contrast ratios.** The PRD asserts 8.2:1, 11.4:1, 6.1:1. **These have
  never been computed.** If any is wrong, the palette ships an accessibility claim it fails.
- Prong/berry/scar illustrations for Workflow B (currently text-only — a PRD §4.3 gap).
- Bundleable font substitute, or an explicit decision to stay on the platform grotesk.
- Companion photos optimised: 3.2 MB → target < 1 MB.
- 400 px-width pass on every screen.

### 6.3 Verification
- Three.js mesh render (geometry, normals, winding, skirts).
- Three.js camera cross-check vs `MapCamera.project()`.
- Robolectric smoke tests: app launches, nav graph resolves, DB migrates, assets parse.
- Instrumented tests written and committed even though unrunnable here.

### 6.4 Code health
- `/code-review` at high, then `/simplify`.
- `/security-review` on the diff.
- Dead-code sweep (`bilinear` in `SuitabilityRasterizer` is already unused).
- Re-evaluate R8 with keep rules + a smoke test.

### 6.5 Build
- Iterate until `assembleRelease` + `bundleRelease` are green with everything above.

---

## 7. What I am NOT going to do

Stated so the auditor can object to the boundary itself:

- **Not implementing Firebase sync.** PRD says opt-in, never default, Phase 3.
- **Not wiring Supabase for patch data.** Violates §8.3.
- **Not claiming device verification.** No device exists here. A Three.js render is a
  surrogate for *geometry*, not proof the app runs.
- **Not enabling R8 unless a smoke test passes with it on.**
- **Not fabricating the 14 missing closing dates** if sources don't yield them.
- **Not calling the subagent audit "independent"** without the §0.2 qualifier.

---

## 8. Questions the auditor should press hardest

1. Is a same-vendor cold-context subagent worth anything at all, or is §0.2 self-deception
   with extra steps?
2. Is "code-complete, verification-incomplete" an honest description, or is shipping a
   59 MB AAB that has never rendered a frame simply not a production build?
3. Does the Three.js cross-check actually constitute a witness, or does it just move my own
   misunderstanding of MapLibre's transform into a second file I also wrote?
4. Is the expert-weighted forecast defensible to ship at all, given it has never been
   validated against a single known ginseng location?
5. Are the approximate protected-area boxes safe, given a false negative could contribute to
   a federal offence?
6. Does §5's MCP matrix contain padding dressed as utility?

---

## 9. Acceptance criteria for v1.0.0

1. `assembleDebug` + `assembleRelease` + `bundleRelease` green.
2. All tests pass; every new behaviour has a negative control **seen to fail first**.
3. Shaders compile under glslang.
4. Zero telemetry classes; permission set unchanged or reduced.
5. Every VERIFIED label traces to a retrieved source.
6. Contrast ratios computed, not asserted.
7. `EINCOL_REPORT.md` open list updated — **including what is still unfixed**.

---

## 10. Submission

Handed to the degraded-rung-3 auditor of §0.2 before any code changes.

Instruction to the auditor: *you did not write this and have no stake in it. Name the single
strongest technical objection, and one cheaper instrument that catches the same class of
defect.* Findings recorded verbatim in §11, including those I disagree with.

## 11. Auditor findings

*(to be filled by the auditor — empty at submission)*

Recorded verbatim, 2026-09-12, from the degraded rung-3 auditor of §0.2 (cold context, claims
only, no code access, no access to my reasoning). **28 findings. Nothing is paraphrased and
nothing is omitted, including the findings I believe are wrong** — my responses are in §12, kept
separate so the auditor's text stands unedited.

F1. **The single strongest technical objection: the plan's release gate is defined entirely in terms of instruments that are structurally incapable of observing the failure class that dominates this codebase.** "Green build + 106 JVM tests + glslangValidator + apksigner" are all static or host-side. Every subsystem where an 8,001-LOC Compose/GL/Room app actually fails is runtime-only: `Application.onCreate`, EGL config selection and context loss, `GLSurfaceView` lifecycle vs. Compose recomposition, Room migration on a real SQLite, asset parsing from the packaged APK rather than the source tree, runtime permission flow, and GPU driver-specific shader behaviour. Step 6 ("iterate until release build and bundle are green, then declare v1.0.0") therefore cannot terminate in anything meaningful — it is a loop whose exit condition is blind to the failure mode with the highest prior. The remaining work is not "verification-incomplete"; its *size is unmeasured*, which is a different and worse epistemic state.

F2. **The strongest architectural objection: the alignment witness — the single most load-bearing piece of evidence in the entire plan — is dead code.** It is a runtime instrument in a program that has no runtime. Its tolerance (2.0 px) and probe count (9) have never produced a number. Presenting "we have an alignment witness" as reassurance is presenting an unexecuted assertion as a measurement.

F3. **Reconstructing MapLibre's camera "from scratch in application code" is probably self-inflicted, and the claim that justifies it is a universal you cannot have verified.** MapLibre Native (inherited from the Mapbox GL Native lineage) ships a custom-layer mechanism — `CustomLayer` / `CustomDrawableLayer` — that invokes your GL code *inside the map's own render pass* and hands you the map's current projection matrix. "No terrain API at any version" is a much weaker claim than "no way to draw GL content in the map's own coordinate frame," and only the latter justifies the shadow camera. If the custom-layer path exists in your pinned version, then the camera reconstruction, the alignment witness, the pin-ordering defect (F4), the temporal-desync defect (F5), and plan step 3 all evaporate simultaneously. Verify this before writing one more line of matrix code; it is the highest-leverage question in the project.

F4. **"The overlay draws above the map's patch pins" is not a z-order cosmetic issue; it is a symptom of the overlay living outside the map's compositing and input pipeline.** A transparent `GLSurfaceView` stacked above a `MapView` also intercepts touch. Either the map receives no gestures, or you forward them — and forwarded gestures mean the map's transform and your overlay's transform are updated on different frames.

F5. **The dominant *perceived* defect will be temporal, and neither the alignment witness nor Three.js can see it.** Two independent render loops (MapLibre's and the `GLSurfaceView`'s) driven from the same gesture stream desynchronise by one or more frames during every pan, fling and pinch. The terrain will visibly slide relative to the basemap. A static 9-point, sub-2px witness at a fixed camera measures exactly the property that is not the problem.

F6. **The witness's parameterisation is wrong in kind.** Nine ground points at one camera state fits a near-affine perturbation trivially. The errors that matter are nonlinear in camera state: FOV-vs-altitude compensation (two wrong parameters that cancel at low pitch), Mercator scale-by-latitude, and horizon behaviour as pitch → max. Sweep the *camera parameter space* (zoom × pitch × bearing × latitude, including pitch at its maximum and latitudes at the ends of your 19 jurisdictions) and place probes at viewport corners and near the horizon, not near centre. A witness that passes at pitch 0 and fails at pitch 60 is the expected outcome.

F7. **The witness validates CPU float64 math; the artifact will be GPU float32 precision loss.** If absolute web-Mercator coordinates reach the shader as `float`, you get sub-pixel wobble and then visible crawling above roughly zoom 14 — MapLibre avoids this by translating to tile-local coordinates before the GPU. A double-precision CPU comparison is constitutionally unable to detect this.

F8. **"GLSL shaders compile clean under glslangValidator" is close to zero evidence for Android.** glslangValidator does not model Adreno/Mali/PowerVR driver behaviour, and the real ES failure modes are invisible to it: vertex/fragment precision-qualifier mismatch on varyings, `mediump` range overflow on Mercator-scale values (directly coupled to F7), `highp` unavailability in ES2 fragment stages, and extension availability (depth textures, NPOT, `OES_standard_derivatives`). Shader validation on Android means compiling on at least one Adreno and one Mali device, or you have not validated shaders.

F9. **One cheaper instrument, replacing step 3 entirely: a headless emulator in CI via Gradle Managed Devices (SwiftShader / `-gpu swiftshader_indirect`).** This costs roughly an hour of configuration and catches, in one shot, strictly more than steps 3 and 4 combined: first-launch crash, nav-graph resolution, Room migration, asset parsing, EGL context creation, shader compilation on a real ES driver, *and* it makes the alignment witness produce actual numbers across a swept camera grid. It also lets you diff your projection against `Projection.toScreenLocation()` — MapLibre's own implementation, which is the only genuinely independent oracle available. Cheaper still, and available today with no runtime: algebraic invariant tests on your matrix (project∘unproject = identity; screen centre maps to camera centre; horizontal scale doubles per zoom level; horizon y-position matches the closed-form for a given pitch/FOV).

F10. **"Instrumented tests that cannot be run here" (step 4) is very likely false, and it is the assumption that makes the whole plan degrade.** Containerised x86-64 emulators with software rasterisation run without KVM, slowly but adequately, and Gradle Managed Devices download and provision them automatically. Test the impossibility claim before designing around it; if it holds, say *which* specific failure produced it.

F11. **(f) Step 3 is the padding dressed as utility.** It is the most expensive item, it validates mesh geometry (the lowest-risk part of the overlay), and its "second independent implementation of the same projection math" is not independent in any sense that matters — same author, same priors, same misreading of the same documentation, and now with no shared runtime to arbitrate the disagreement. Worse: if the two implementations agree, you will read that as confirmation, which converts a correlated error into false confidence. This is precisely the failure mode EINCOL's vendor-diversity rule exists to prevent, reproduced inside a single file tree. Secondary padding: within step 1, retraction-checking McCune & Keon (2002), Beven & Kirkby (1979), Horn (1981) and Zevenbergen & Thorne is theatre — these are 40-year-old canonical method papers and retraction is not their risk; *applicability* is (F13, F14). Within step 5, "dead-code sweep" on 8k LOC and "re-evaluate R8" are both premature: enabling R8 introduces an entire new failure class (missing keep rules for Room, reflection, ONNX Runtime, serialization) immediately before a release that has never executed unminified.

F12. **(c) A Three.js cross-check is not a witness; it is a second copy of the author's belief.** A witness requires a source of truth the author did not author. There are exactly three such sources here: MapLibre's own `Projection` API at runtime, the `maplibre-gl-js` reference implementation (a different codebase by different people implementing the same documented transform), and pixels on a screen. Re-deriving the transform in JavaScript is none of them. The general rule the plan should adopt: stop seeking an independent *opinion* and seek an independent *measurement* — machine disagreement is vendor-independent and prior-independent in a way no critic is.

F13. **The six weights are applied to non-orthogonal predictors, so they do not mean what they appear to mean.** Heat load (0.28) is an analytic function of slope and aspect; slope angle carries a further 0.14; TWI (0.18) contains tan β; TPI (0.24) and elevation (0.06) are both neighbourhood functions of the same elevation surface. Slope enters the score three times, and its effective weight is nowhere near 0.14. A linear weighted sum over collinear derivatives of one DEM is not an expert elicitation — it is an unidentified model whose stated coefficients are uninterpretable. Two-significant-figure precision (0.28 vs 0.24) asserts discriminative power the elicitation cannot support.

F14. **The heaviest-weighted term is blind to the mechanism that creates the habitat.** McCune & Keon's heat load index is a function of latitude, slope and folded aspect only; it does not model cast shadow from surrounding terrain. Cool, moist, shaded coves — the exact microsites *Panax quinquefolius* occupies — are produced largely by adjacent-ridge shading, which the index cannot see. Also confirm the boring things, because they are the usual bugs: latitude domain (the equations are fitted for roughly 0–60° N), radians vs. degrees, and aspect *folded* about the N–S axis rather than raw azimuth.

F15. **TWI on a windowed DEM is not TWI.** Beven & Kirkby's ln(a/tan β) requires upslope contributing area, which requires flow accumulation over a hydrologically connected catchment that does not respect your tile or viewport boundary. Computing it on a clipped window truncates contributing area at the edge, systematically depressing wetness near window borders, and — worse — makes the value at a fixed geographic point *depend on the window extent*, i.e. non-deterministic across pans and zooms. Test for this directly: score the same coordinate from three different window origins and assert agreement. I expect it to fail. Similarly, Weiss TPI is meaningless without a declared neighbourhood radius, and the relevant ginseng landform (bench, cove, toe-slope) is a ~50–300 m phenomenon; if the radius is undeclared or tied to zoom, the index is unreproducible.

F16. **Internal inconsistency: "19 bundled asset files, 3.2 MB" versus "terrain forecast scores ground from a public-domain DEM" and "no network calls in local-only mode."** 3.2 MB cannot hold a DEM at any resolution useful for TPI/TWI over 19 jurisdictions — a single county at 30 m is tens of MB. Either the DEM is fetched at runtime (contradicting the local-only constraint and the local-first posture) or the forecast cannot operate offline. The same contradiction hits the product's headline feature from the other side: "offline map-tile pre-download: absent" plus "no network calls in local-only mode" means the heatmap-over-MapLibre screen renders on a blank basemap in the mode the privacy constraint describes. Pick one: the app is local-first, or it has a terrain heatmap on a slippy map. Currently the plan claims both.

F17. **Internal inconsistency: "USFWS publishes no per-state harvest season END dates" versus next-step 1, "research the 14 missing closing dates."** If the dates are genuinely unpublished, step 1 cannot succeed and the 14 must be permanently "unknown." If step 1 can succeed, the availability claim is wrong — and it is wrong, because closing dates live in state administrative code and state DNR/agriculture regulations, not in a federal aggregator. One source returning nothing is a search failure, not a fact about the world. The same reasoning applies to the NPS 404: a single 404 from one endpoint is not evidence that authoritative boundaries are unavailable. USGS PAD-US is a single downloadable dataset covering federal, state and local protected areas.

F18. **(e) The bounding-box question is the wrong question, and asking it reveals a deeper design error.** A bbox around a convex unit over-covers, which is fail-safe. The dangerous cases are specific and enumerable: multipart units where one bbox was computed from one part; long linear units (a parkway) whose bbox covers thousands of unrelated square miles; units absent from the dataset entirely; and checkerboarded national forests with private inholdings. But none of that is the real hazard. The real hazard is that the app has an affirmative state at all. Absence of a federal boundary is not presence of permission — state parks, WMAs, municipal watershed land, tribal land and private property are all separately prohibited or require consent, and none of them are in your dataset. **Remove any UI state that means "you may harvest here."** Then bbox precision becomes low-stakes, because the geofence is only ever used to *add* a prohibition, never to clear one. If you keep polygons, simplify them with Douglas–Peucker *plus a positive outward buffer* so simplification error is one-sided and provably over-inclusive, and store them as an S2/quadtree cell cover — small, local, and fail-safe by construction.

F19. **The legality module needs three-valued logic, and the current shape is fail-deadly.** "Start date known, end date unknown" is the most dangerous possible state: a user who checks in September sees "open" and will not re-check in December. Encode permitted / prohibited / **unknown**, and render unknown *identically to prohibited* in every go/no-go affordance — differing only in explanatory text. Separately, "harvest-legality guidance" overstates what a date table can deliver: licensing, plant-age minimums (three prongs / leaf-scar counts), seed-replanting requirements, county-level closures, weight/wet-vs-dry rules, landowner permission and CITES export certification are all load-bearing and none are dates.

F20. **The ONNX model is a tautology engine and it is currently shipping.** Slope and aspect at exactly 0.0 with two dominant inputs derived from the user's own questionnaire answers means the model re-reports the user's input as a prediction. If its score is displayed anywhere near the terrain heatmap, the user perceives two independent systems agreeing when there is one source and no terrain information in it at all. Delete it from the bundle now rather than scheduling a fix — and note that with R8 off it is not even being pruned. Its continued presence is also evidence that bundle contents are not curated, which weakens the "0 telemetry classes in the APK" claim as a statement about what ships.

F21. **"0 telemetry/analytics classes in the built APK" is insufficient evidence for the privacy constraint, because the largest exfiltration path on Android requires no class.** Android Auto Backup is on by default and will push your Room DB — a georeferenced inventory of a poached species — to Google's servers. Set `android:allowBackup="false"` or exclude the DB and photo directory explicitly. The other paths a class-name count cannot see: EXIF GPS surviving the share sheet (app-private storage protects the file, not what the user shares out of it), coordinates in logcat, and the recents-screen thumbnail. "GPS masked in list views until tapped" is UI theatre next to these. Also: a class-name scan cannot establish "no network calls" — that is a runtime property, testable cheaply with `StrictMode.setVmPolicy` plus a deny-all network interceptor in a smoke test, and statically with a build-time assertion that no transitive dependency pulls in an HTTP client.

F22. **"apksigner verifies it" is probably a mis-stated claim.** apksigner does not verify `.aab` files — AABs carry JAR signatures (`jarsigner -verify`) and Play re-signs the delivered APKs anyway. If you actually ran `bundletool build-apks` and verified the extracted APKs, say that, because it is a materially stronger claim. Also, the headline "59.4 MB AAB" is not a number any user experiences: report per-ABI download and install size from `bundletool get-size total`. Given MapLibre's and ONNX Runtime's native libraries across four ABIs, the real per-device figure is likely a third of that, and the 59.4 MB framing invites you to fix a non-problem.

F23. **targetSdk 34 cannot reach Play in 2026, so "bundle is green" and "shippable" are not the same claim.** Play's rolling target-API requirement has been past 34 for new submissions since August 2025. Raising it is not a version-number edit: API 35 enforces edge-to-edge by default, which will break a full-screen GL overlay's insets and layout — a runtime regression, in a program with no runtime. Discover this now, not after declaring v1.0.0.

F24. **(a) A same-vendor cold-context critic is worth something real, but it is degraded on two axes, not one, and the plan appears to acknowledge only one.** It genuinely catches claim-level incoherence, missing or mismatched instruments, unit/domain errors, and "the stated evidence does not support the stated conclusion" — F13, F16, F17, F22 are all of that kind and none required independence of priors. It cannot catch shared-prior error: if your model of MapLibre's transform is wrong in the way the documentation invites, mine is wrong identically and I will nod. And the second, unacknowledged degradation is larger than vendor: **you selected the claims.** Cold context controls for reasoning contamination; it does nothing about selection. "Degraded rung 3" is an honest label for the vendor axis and silent on the axis that matters more. The fix is not a better critic — it is to replace opinion with oracles (F9, F12): a real runtime, MapLibre's own projection API, a third-party reference implementation, published occurrence data. Those are decorrelated from you by construction.

F25. **(b) "Code-complete, verification-incomplete" is a euphemism. The accurate word is "unexecuted."** "Code-complete" carries the implication that the remaining work is small and enumerated; here it is neither, and the honest statement is that the binary's behaviour is unknown, with a low prior on first-run success given a GL overlay, a Room DB, a Compose nav graph and bundled asset parsing. A build that has never rendered a frame is not a production build in the only sense that matters — provenance plus evidence of function — and `v1.0.0` should be unreachable by definition until at least one frame exists. Ship it `0.x`, and make "one frame rendered on one real device" a named, non-negotiable gate ahead of steps 5 and 6.

F26. **(d) An expert-weighted, never-validated forecast is defensible to *compute* and not defensible to *call a forecast*.** The word implies probabilistic semantics the artifact does not have, and a continuous colour ramp implies calibration it has never been tested for. Two things make this worse than the usual unvalidated-heuristic case. First, F13–F15 mean the weights are not even the parameters they claim to be, so "expert-assigned" is not the actual provenance of the output. Second, true holdout validation is *unobtainable for this species by design*: iNaturalist and GBIF obscure *P. quinquefolius* coordinates to tens of kilometres precisely to deter poaching, which defeats a 30 m model — so "we will validate later" is a promise you probably cannot keep, and you should stop implying otherwise. What you can and must do cheaply: a weight-sensitivity sweep (perturb each weight ±50%, measure Spearman correlation of the output raster). If the ranking barely moves, the weights are decorative and you can say so honestly; if it moves a lot, you are shipping a number no one can defend. Either result is more informative than the current silence, and the sweep costs an afternoon. Relabel the output as an unvalidated terrain heuristic, drop the continuous ramp in favour of coarse ordinal bands, and never present it adjacent to anything that looks like a second corroborating opinion (F20).

F27. **Step 2's framing is too narrow to be worth doing as stated.** Computing the three asserted ratios verifies three assertions; the instrument you want is an automated WCAG sweep over the whole theme (light and dark, disabled and pressed states, text over the map) wired into CI. More importantly, contrast ratio is the wrong metric for the artifact that carries the most information: a habitat heatmap's colour ramp must be checked for colour-vision deficiency and perceptual uniformity. A red-to-green ramp at 8.2:1 still fails roughly 8% of male users completely, and contrast ratio will never tell you that. Use a perceptually uniform sequential ramp, verify under a deuteranope simulation, and carry a redundant non-colour encoding.

F28. **Step 4 writes unexecuted tests to verify unexecuted code, which compounds rather than reduces uncertainty.** A test that has never failed has never been shown capable of failing. Require every new Robolectric and instrumented test to be demonstrated *red* — by breaking the thing it tests — before it is allowed to count as evidence. And publish the one number that would make this whole discussion concrete: JaCoCo branch coverage restricted to the camera, projection, mesh and geo packages. It is free, you already have the plugin, and I predict it is near zero — at which point "106 tests pass" stops being reassuring and starts being the finding.
