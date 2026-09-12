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
