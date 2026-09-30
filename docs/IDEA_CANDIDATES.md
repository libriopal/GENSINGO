# Idea candidates — Phase 7 (minimal 3D field app with a live research model)

EINCOL steps 1–3 for the goal:

> An APK for a minimal 3D app with a live LLM that gives research-based suggestions for the
> current GPS 10-mile radius, real-time constant heatmapping, persistent memory, a custom
> heatmap of verified ginseng finds that the app learns from, tracking of where you have been
> and where you are, fully offline and battery-optimised, with a minimal but functional UI —
> and with every prior recommendation entered as an idea candidate.

This file is the idea pool. `EINCOL_REPORT.md` Phase 7 records what was built, what the
evaluators found, and what is still open.

---

## Step 1 — the cue, and what was measured

**Cue (one specific question):**

> In an app that tells a digger where to walk inside a 10-mile radius, which pin, pixel or
> sentence can reach the screen without anything having checked it: against the terrain,
> against a source, or against the user's own verified ground?

Measured on `HEAD` (`7a3656d`) before any change. See "Baseline" in `EINCOL_REPORT.md`
Phase 7 for the commands that produced each number.

| Measured | Value |
|---|---|
| Can a clean clone build an APK? | **No.** `validateSigningDebug` fails: the debug signing config points at `debug.keystore`, which is gitignored and not in the repo |
| Unit tests | **206 / 206 pass**, including the ones pinning the engines below |
| UI reachable at launch | **None.** One `@Composable` exists (`Terrain3DOverlay`) and nothing calls it; `MainActivity` never calls `setContent` |
| Suggested "hotspots" per research call | **4**, at **fixed** offsets from the caller (`lat + 0.0482`, `lng − 0.0354`, …) |
| Hotspot names at any coordinate | Hard-coded ("Cataloochee Northern Hollow Bench", "Spring Creek Limestone Contact Zone", …) |
| Hotspot clamp | `lat.coerceIn(34, 37)`, `lng.coerceIn(−85, −81)`: a caller at 40.0 N, −80.0 W gets pins at 37.0, −81.0, **≈ 345 km away**, labelled "inside the 10-mile radius" |
| `isLiveGemini = true` when a key is set | The model's text replaces one paragraph. The **coordinates stay the fabricated ones** and the report is still labelled live. |
| NC season in the "research" text | "Sept 1 – Nov 30". The app's own sourced `state_regulations.json` says **Dec 31**, `season_end_verified: true` |
| Heatmap default mode | TPI and TWI are **computed and discarded**. Canopy is "estimated" as `if (aspect is N/E) 82 else 68`, so aspect is counted twice. The six-factor published model that README, tests and the non-monotonic wetness guard describe is **no longer called by the rasteriser**. |
| Room migration | `fallbackToDestructiveMigration()` — every schema bump **deletes** the "persistent memory" |
| `List<String>` converter | `joinToString(" ")` / `split(" ")` — "Black Cohosh" is stored and read back as **two species** |
| EINCOL "negative control" in `EincolAuditEngine` | `val inertDelta = 0.0` — a literal. It **cannot fail**. |
| Monte Carlo "witness pin" | Deterministic baseline and MC mean are computed from the same inputs through different arithmetic, so it is a self-witness |
| Tour "steps" | `distance / 0.76` — invented from distance, not counted |
| Battery hours | `15 Wh × pct / assumed watts` — an assumption presented as an estimate |
| API key | Baked into `BuildConfig` from the build machine's env (extractable from any APK), and sent as `?key=` in the URL |
| `gradlew` mode | `100644` — a fresh clone cannot run `./gradlew` |

## Step 2 — distribution, not an answer

For the cue: *which unchecked claim is the one that matters?*

| p | Candidate |
|---|---|
| 0.30 | There is no UI, so nothing reaches the screen, checked or not. |
| 0.20 | The "LLM research" pins are fabricated at fixed offsets, and a live reply launders them as live. |
| 0.15 | The heatmap no longer runs the model it is documented as running; the tests pin a model the map does not call. |
| 0.12 | "Persistent memory" is not persistent (destructive migration, lossy converter). |
| 0.10 | The verification layer cannot fail (literal negative control, self-witness Monte Carlo). |
| **0.08** | **Any design that lets a language model emit coordinates cannot be checked against terrain. Locations must come only from computation, and the model may only annotate them by ID.** |
| **0.05** | **"Learning from verified finds" is unfalsifiable unless held-out finds judge it. Otherwise every new find "improves" the model by construction.** |

Row one is true and closes nothing: building a UI would make the fabricated pins more
convincing, not less.

## Step 3 — working the tail

Rows 0.08 and 0.05 are the same shape as the sovereignty classifier: a location is trustworthy
only if it is pinned **against a witness** (the terrain computed it), and a learned weight
only if it is pinned **in time** (it predicted finds it had not yet seen).

That gives the architecture:

1. **Compute.** Candidates inside the radius come only from the terrain model, run on the
   device over cached elevation tiles.
2. **Annotate.** The language model sees candidate IDs and their terrain factors. It may
   rank them and explain them. It cannot add a location. Unknown IDs are dropped.
3. **Witness.** A cited source counts only if the model's own search tool retrieved that URL
   in the same call. A citation that was not retrieved is removed and counted.
4. **Earn.** Learned weights replace the published prior only when they rank held-out
   verified finds better than the prior does. Held-out means the whole spatial cluster
   (≤ 200 m), not just the point. The evaluator decides, not the learner.

A sixth row surfaced while working the tail. It is outside the cue but load-bearing:

| p | Candidate |
|---|---|
| 0.06 | A live model with persistent memory would ship the user's exact position and verified patches to a third party. That contradicts the README's first promise ("your honey holes stay yours"). |

## Step 4 — attacking my own claims (both versions kept)

| First claim | What broke it | Corrected claim |
|---|---|---|
| "The model should never see a location." | It then cannot say anything regional. State rules, local geology and county context all need *where*. | Send a **coarse** region (the 0.1° cell centre and the state), never the fix, never a find. Candidates go as factor profiles with a distance band, not coordinates. |
| "A find is verified when the fix is ≤ 15 m." | Ginseng grows in coves under closed canopy, exactly where phone fixes are worst (10–30 m). The rule would reject most real finds. | Average fixes for up to 20 s. Verified = averaged accuracy ≤ 20 m, fix fresh, and a 3-item identification checklist. Accuracy also sets the kernel radius in the finds heatmap. |
| "Adopt learned weights when leave-one-out AUC beats the prior." | With 1–3 finds, LOO AUC is noise. Finds also cluster, so a held-out find's neighbour leaks it back in. | Require ≥ 5 verified finds, hold out whole ≤ 200 m clusters, and require a margin (+0.02). Below that the app shows the finds heatmap and says it is not learning yet, and why. |

---

## The idea pool — 124 new candidates

`p` is my honest probability that the goal **fails without** the idea (load-bearing), not
how nice it would be. Disposition: **BUILD** (this iteration) · **DEFER** (real, not now) ·
**REJECT** (with the reason).

### A. Live research model (the 10-mile suggestions)

| ID | Idea | p | Disposition |
|---|---|---|---|
| N001 | Locations come only from on-device terrain computation; the model may only rank/annotate candidate IDs | 0.90 | BUILD |
| N002 | Model output through a strict JSON-schema tool; unknown IDs dropped, counts recorded | 0.70 | BUILD |
| N003 | Citation witness: a URL counts only if this call's search tool retrieved it | 0.60 | BUILD |
| N004 | Web search (Claude) / Google Search grounding (Gemini) so rationale can carry real sources | 0.50 | BUILD |
| N005 | Send a coarse region (0.1° cell + state), never the fix, never find coordinates | 0.70 | BUILD |
| N006 | Explicit consent switch before any model call; off by default | 0.60 | BUILD |
| N007 | Bring-your-own-key stored with Android Keystore AES-GCM; nothing in `BuildConfig` | 0.60 | BUILD |
| N008 | Gemini key in the `x-goog-api-key` header, not the URL | 0.40 | BUILD |
| N009 | Offline: on-device rationale written from the factor numbers, labelled "computed on device" | 0.80 | BUILD |
| N010 | Persist every research run (model, time, citations, rejected counts), readable offline | 0.60 | BUILD |
| N011 | Auto-refresh only after moving > 5 km, ≥ 30 min apart, battery > 30 %, consent on | 0.40 | BUILD |
| N012 | `pause_turn` continuation, capped at 3 | 0.40 | BUILD |
| N013 | `refusal` / `max_tokens` shown as errors, never as an empty "no suggestions" | 0.40 | BUILD |
| N014 | Memory in the prompt as aggregates (visited / found / not-found by factor profile), no coordinates | 0.50 | BUILD |
| N015 | State rules injected verbatim from sourced data; the model is told not to restate dates | 0.50 | BUILD |
| N016 | Strip coordinate-looking numbers from model prose before display | 0.30 | BUILD |
| N017 | Every line carries its provenance: computed, or model + source count | 0.30 | BUILD |
| N018 | Retry/backoff on 429/529 (SDK default retries) | 0.30 | BUILD |
| N019 | Stream the model's text into the UI | 0.10 | REJECT — the structured result only exists at the end |
| N020 | Let the model propose new points and snap them to the nearest candidate | 0.10 | REJECT — reintroduces unchecked coordinates through a side door |
| N021 | On-device LLM (Gemma via MediaPipe) for offline commentary | 0.15 | DEFER — 1–4 GB, battery, conflicts with "minimal" |
| N022 | Prompt caching of the system prompt | 0.10 | DEFER — prompt is near the cacheable minimum |
| N023 | Per-candidate "look for on foot" field (indicator species) | 0.30 | BUILD |
| N024 | Provider choice: Claude (default) or Gemini (the previous integration) | 0.30 | BUILD |
| N025 | Server-side refusal fallback | 0.20 | BUILD |
| N026 | Batch API for research | 0.05 | REJECT — interactive use |

### B. Heatmapping

| ID | Idea | p | Disposition |
|---|---|---|---|
| N030 | Restore the published six-factor model in the rendered heatmap (TPI and TWI used again) | 0.80 | BUILD |
| N031 | Test the **rasteriser**, not only the model, for the wet-end turnover | 0.70 | BUILD |
| N032 | "Where I've been" heatmap from track points, GPU `HeatmapLayer` | 0.60 | BUILD |
| N033 | Verified-finds heatmap, GPU `HeatmapLayer`, radius from fix accuracy | 0.60 | BUILD |
| N034 | Learned-habitat layer: same raster, learned weights, shown only when the gate passes | 0.50 | BUILD |
| N035 | Recompute the habitat raster on camera idle only, keyed by DEM tiles | 0.50 | BUILD (exists; kept) |
| N036 | Live position + heat refresh per fix, GeoJSON pushes throttled to ≤ 1 per 3 s | 0.50 | BUILD |
| N037 | Draw the 10-mile ring | 0.40 | BUILD |
| N038 | Heat transparency by model uncertainty (registry #96) | 0.15 | DEFER |
| N039 | Legend with a provenance label per layer | 0.40 | BUILD |
| N040 | Keep the CVD-checked monotonic-L* ramp (Phase 5 #23) | 0.30 | BUILD (reuse) |
| N041 | Candidates inside approximate PROHIBITED areas left out and counted (first written as "ranked last"; last is still a recommendation) | 0.50 | BUILD |
| N042 | Older track points decay | 0.15 | DEFER |
| N043 | Suitability in a GPU shader | 0.10 | REJECT — TPI and MFD are whole-mosaic operators (registry #100 same) |
| N044 | "Visited, nothing found" as a negative layer / learner absences (registry #97) | 0.20 | DEFER — a walk-by is weak absence evidence |

### C. Persistent memory

| ID | Idea | p | Disposition |
|---|---|---|---|
| N050 | Remove destructive migration; explicit `Migration(4, 5)` | 0.80 | BUILD |
| N051 | JSON list converter that still reads legacy space-joined rows | 0.60 | BUILD |
| N052 | Export the schema and test the migration | 0.50 | BUILD |
| N053 | Track-point table, indexed by time | 0.70 | BUILD |
| N054 | Suggestion lifecycle NEW → VISITED (track within 40 m) → FOUND / NOT_FOUND | 0.60 | BUILD |
| N055 | Research-run table | 0.50 | BUILD |
| N056 | Downsample old tracks (Douglas–Peucker) after 90 days | 0.20 | DEFER |
| N057 | Export / import memory (GPX / JSON) | 0.30 | DEFER — backup is off on purpose; needs a privacy design |
| N058 | Encrypt the database (SQLCipher; registry #104) | 0.20 | DEFER |
| N059 | Batched inserts, WAL | 0.30 | BUILD (Room default WAL; batch track writes) |

### D. Verified finds and learning

| ID | Idea | p | Disposition |
|---|---|---|---|
| N060 | Verified = averaged accuracy ≤ 20 m + fresh fix + 3-item ID checklist; otherwise saved unverified | 0.70 | REJECT — built, then removed at the user's direction: their finds are treated as true and accurate (Phase 7) |
| N061 | Average fixes for up to 20 s before marking | 0.50 | BUILD |
| N062 | Six-factor terrain snapshot per find, from cached DEM, filled in later if offline | 0.70 | BUILD |
| N063 | Presence-background learner: L2-regularised logistic regression centred on the prior | 0.60 | BUILD |
| N064 | Adopt learned weights only if held-out AUC beats the prior by ≥ 0.02 | 0.70 | BUILD |
| N065 | ≥ 5 finds before learning may be adopted | 0.60 | BUILD |
| N066 | Negative control: random points as "finds" must be rejected by the gate (registry #93) | 0.70 | BUILD |
| N067 | Hold out whole ≤ 200 m clusters, not single finds (registry #95, reduced) | 0.40 | BUILD |
| N068 | Background points sampled across the radius from the same DEM | 0.50 | BUILD |
| N069 | Photo evidence per find, EXIF stripped (registry #103) | 0.30 | DEFER |
| N070 | Plant count and prong count on each find | 0.30 | BUILD |
| N071 | Coordinates hidden in lists until tapped | 0.30 | BUILD |
| N072 | Prior vs learned weight table, with the held-out AUCs | 0.40 | BUILD |
| N073 | Stewardship prompt on a find (plant the berries) | 0.30 | DEFER |
| N074 | Learn from unverified finds at lower weight | 0.10 | REJECT — superseded: at the user's direction every find is learned from at full weight (N060) |

### E. Tracking

| ID | Idea | p | Disposition |
|---|---|---|---|
| N080 | Foreground service (type `location`) started only by an explicit tap; stop from the notification | 0.80 | BUILD |
| N081 | Adaptive interval policy: moving / stationary / low battery | 0.60 | BUILD |
| N082 | Fused-provider batching (`maxUpdateDelay`) so the CPU can sleep between batches | 0.50 | BUILD |
| N083 | Drop fixes worse than 50 m; store only after moving max(5 m, accuracy / 2) | 0.60 | BUILD |
| N084 | Track line layer | 0.60 | BUILD |
| N085 | Distance from filtered points; no invented step counts | 0.40 | BUILD |
| N086 | Auto-mark a suggestion VISITED when the track passes within 40 m | 0.50 | BUILD |
| N087 | Restart tracking after process death | 0.20 | DEFER — location FGS cannot start from the background on API 34+ |
| N088 | Step count | 0.05 | REJECT — the previous code invented steps from distance |
| N089 | Notification with Stop, and no coordinates in it | 0.50 | BUILD |
| N090 | `ACCESS_BACKGROUND_LOCATION` | 0.10 | REJECT — an FGS started in the foreground does not need it |

### F. Offline

| ID | Idea | p | Disposition |
|---|---|---|---|
| N095 | "Save area offline": DEM z11–z13 over the radius + basemap offline region z8–z14 | 0.70 | BUILD |
| N096 | Scoring, candidates and learner all run on the device | 0.90 | BUILD |
| N097 | Last research results readable offline | 0.50 | BUILD |
| N098 | Offline "Suggest" returns computed results immediately and says the model is unreachable | 0.40 | BUILD |
| N099 | Fall back to the bundled 0.1° DEM for elevation when no tiles are cached | 0.20 | DEFER |
| N100 | Map style cached by the offline region so the map loads with no network | 0.50 | BUILD |
| N101 | Air-gapped build flavour (registry #101) | 0.20 | DEFER |

### G. Battery

| ID | Idea | p | Disposition |
|---|---|---|---|
| N105 | No GPS when backgrounded and not tracking | 0.60 | BUILD |
| N106 | Map capped at 30 fps | 0.30 | BUILD |
| N107 | Radius scan only after moving > 3 km, cached per 0.05° cell | 0.50 | BUILD |
| N108 | Dark UI throughout (registry #106) | 0.30 | BUILD |
| N109 | 3D view renders only when dirty | 0.40 | BUILD |
| N110 | Below 15 %: 2-minute interval, no automatic model calls | 0.40 | BUILD |
| N111 | Thermal throttling guard (registry #105) | 0.15 | DEFER |
| N112 | Measured drain on a real handset | 0.50 | OPEN — no physical device here |

### H. Minimal UI and 3D

| ID | Idea | p | Disposition |
|---|---|---|---|
| N115 | One screen: map + four buttons (Track, Find, Suggest, Layers) | 0.70 | BUILD |
| N116 | Pitched map (50°) with hillshade as the default 2.5D view | 0.50 | BUILD |
| N117 | Real 3D terrain view in its own GL surface, coloured by habitat, markers on top | 0.50 | BUILD |
| N118 | The 3D mesh over the map | 0.10 | REJECT — GLSurfaceView over the TextureView map blacks the screen (Phase 5, measured) |
| N119 | Status line: fix ±m, tracking, online/offline, battery policy | 0.50 | BUILD |
| N120 | Suggestion list with distance and bearing; tap to fly there | 0.50 | BUILD |
| N121 | Settings: provider, key, consent, power | 0.50 | BUILD |
| N122 | Edge-to-edge insets for targetSdk 36 (registry #108) | 0.50 | BUILD |
| N123 | Compass arrow to the selected suggestion | 0.30 | DEFER |
| N124 | Onboarding carousel | 0.10 | REJECT — minimal; one-line hints instead |
| N125 | Content descriptions on every control | 0.30 | BUILD |

### I. Safety, legal, privacy

| ID | Idea | p | Disposition |
|---|---|---|---|
| N130 | Never suggest ground inside a PROHIBITED area; say how many were left out | 0.60 | BUILD |
| N131 | Season status from sourced data in the Suggest sheet | 0.40 | BUILD |
| N132 | Never describe any place as legal to dig | 0.60 | BUILD (constraint on prompts and UI) |
| N133 | State notes (e.g. NC written landowner permission) shown with suggestions | 0.30 | BUILD |
| N134 | No coordinates in logcat | 0.40 | BUILD |
| N135 | Replace approximate boxes with PAD-US (registry #107) | 0.30 | DEFER |
| N136 | Update the Play data-safety declaration: model calls now exist, opt-in | 0.40 | BUILD |

### J. Verification and engineering

| ID | Idea | p | Disposition |
|---|---|---|---|
| N140 | Delete the fabricating engines and the tests that pinned them | 0.70 | BUILD |
| N141 | Restore `gradlew`'s executable bit | 0.50 | BUILD |
| N142 | Remove the `BuildConfig` key | 0.50 | BUILD |
| N143 | Mock-server tests of both providers' wire formats, incl. `pause_turn`, refusal, junk | 0.60 | BUILD |
| N144 | Mutation harness over validator, learner gate, power policy, track filter | 0.60 | BUILD |
| N145 | Run the APK on the emulator with a mock location and a mock model server | 0.70 | BUILD |
| N146 | Migration test from a real v4 database | 0.50 | BUILD |
| N147 | Time the radius scan on real terrain | 0.50 | BUILD |
| N148 | Cold-context critic shown claims, not reasoning | 0.40 | BUILD |
| N149 | R8 shrinking | 0.20 | DEFER (Phase 1 open item) |
| N150 | Target a real handset | 0.60 | OPEN — none here |

### K. Found by the evaluators while building (added after Step 5 ran)

| ID | Idea | p | Disposition |
|---|---|---|---|
| N151 | Local fallback style so the heatmaps, track and suggestions draw when the basemap cannot load offline (device run: blank map) | 0.70 | BUILD |
| N152 | Platform-GPS fallback when Play services' fused provider delivers nothing (device run: no fix, Play services down; verified on an image with none) | 0.70 | BUILD |
| N153 | Only fresh fixes start research; refresh on-device after 3 km (device run: stale fix 3,500 km away; list never followed the user offline) | 0.60 | BUILD |
| N154 | Block held-out finds at the terrain's measured correlation range, not 200 m (independent critic; measured 2/30 vs 0/30) | 0.50 | BUILD |
| N155 | Primitive, order-identical sort for wetness and ranking (measured heap 138 -> ~104 MB) | 0.30 | BUILD |
| N156 | Tile the radius scan to bound peak memory on low-end phones | 0.30 | DEFER |
| N157 | arm64-only release with R8 and keep rules (APK 81 MB universal debug) | 0.30 | DEFER |

---

## The imported pool — 186 earlier recommendations

The user asked that every earlier recommendation enter as a candidate. They are triaged here
as groups, because nearly all of them improve the **prior** (what the terrain model knows)
while this goal is about the **loop** (compute → annotate → witness → learn). The loop makes
any future prior improvement measurable, because the held-out-find gate will score it.

| Source | Items | Disposition for this goal | Why |
|---|---|---|---|
| `docs/UPGRADES.md` A (geology, calcium) | A1–A17 | 6 DONE earlier; rest **DEFER** | Needs SSURGO/SGMC at runtime; not offline-bundleable for a 10-mile radius anywhere |
| `docs/UPGRADES.md` B (canopy, light) | B1–B12 | **DEFER** | External rasters; no offline path |
| `docs/UPGRADES.md` C (terrain) | C1–C4 DONE; C5–C12 | **DEFER**, except C9 multi-scale TPI noted for the learner's next feature set | The learner can only weigh factors that exist |
| `docs/UPGRADES.md` rest (D–…) | 76 FIND, 8 HAVE, 11 NO | **DEFER** / keep NO | Data-layer research, not app features |
| Registry 37–48 (astronomical) | 12 | **DEFER**; 39, 40, 46, 47 **REJECT** | Sub-metre or sub-lux effects below the DEM's own error |
| Registry 49–59 (geomorphology) | 11 | **DEFER** (49 ≈ C9) | Prior improvements |
| Registry 60–61 | 2 | Already built (MFD TWI, wet-end turnover); **BUILD** N030/N031 restores them to the map | They had silently fallen out of the rasteriser |
| Registry 62–68 (hydrology) | 7 | **DEFER** | Prior improvements |
| Registry 69–78 (soils) | 10 | **DEFER** | External data |
| Registry 79–88 (canopy, lidar) | 10 | **DEFER** | External data |
| Registry 89–92 (Monte Carlo, dual engine) | 4 | **REJECT** | The Monte Carlo engine is removed (self-witness) |
| Registry 93 (negative-control permutation) | 1 | **BUILD** as N066 | |
| Registry 94–95 (spatial autocorrelation, blocked CV) | 2 | **BUILD** reduced, as N067 | |
| Registry 96–98 | 3 | **DEFER** | |
| Registry 99–100 (SIMD, compute shaders) | 2 | **REJECT** | Not the bottleneck; measured scan time is in the report |
| Registry 101 | 1 | **DEFER** | |
| Registry 102 (GPS obfuscation) | 1 | **BUILD** as N005 | |
| Registry 103–105 | 3 | **DEFER** | |
| Registry 106 (AMOLED) | 1 | **BUILD** as N108 | |
| Registry 107 (PAD-US) | 1 | **DEFER** as N135 | |
| Registry 108 (edge-to-edge) | 1 | **BUILD** as N122 | |

Totals: **128 new + 186 imported = 314 candidates.** Of the new ones: **97 BUILD, 19 DEFER,
10 REJECT, 2 OPEN** (both OPEN items need a physical handset; none here). Seven of the new
ones (K) were found by the evaluators after Step 5 ran. One BUILD (N060, the find-verification
gate) became REJECT after delivery, at the user's direction.

These totals were first typed in by hand as "124 new, 88 BUILD, 18 DEFER, 14 REJECT", and
every one of those numbers was wrong. They are now counted from the tables by a script
(`EINCOL_REPORT.md` Phase 7, "Counting the pool"), which is the check that caught it.
