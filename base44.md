# base44.md — SingNav into GENSINGO, led from Base44

**Who reads this:** the Base44 Superagent, acting as **lead architect**. Parts 0–4 and 6–7 are
yours. **Part 5 is not yours:** it is the APK step, done by Claude Code, because Base44 cannot
build an Android app.

**Invocation (the owner pastes this into the Superagent, or installs this file as a Skill
named "GENSINGO lead architect"):**

> Read base44.md from the top. Take the lead architect role it defines. Execute steps B0 to B13
> in order, exactly as written. Stop only where a step says STOP. Your final output is the
> handoff archive of step B11, gated on nothing but the APK step that Claude Code performs.

**What this run produces:**
1. An audited blueprint for a GENSINGO update that copies SingNav's written code where it serves
   the tool.
2. That update, written on a branch.
3. SingNav's web version polished inside Base44.
4. One zipped archive holding the new SingNav web codebase and the updated GENSINGO codebase.

After this run, the only thing left is building the APK.

This file adapts [ARCHITECT.md](ARCHITECT.md) (the governed build loop this repository uses)
to what a Base44 Superagent can and cannot do. Where the two differ, this file wins for this
run. Where this file is silent, ARCHITECT.md applies.

---

## Part 0 — How to execute this file

1. **In order.** Do steps B0, B1, … B13 one at a time. Do not start a step until the previous
   step's **CHECK** has passed and you have written down that it passed.
2. **Each step has three parts.** **DO** is the work. **PRODUCE** is the file or record it
   leaves. **CHECK** is the condition that must hold before you move on.
3. **STOP means stop.** When a CHECK cannot pass, write the stop report (template T1, Part 6)
   to the owner and wait. Do not work around a STOP, and do not skip ahead.
4. **Where your written work goes.** Everything you write for GENSINGO goes on the branch
   **`base44/singnav-integration`** of `github.com/libriopal/GENSINGO`, through your GitHub
   connector. That covers the inventory, the blueprint, the records and code. **Never commit to
   `main`.** If you cannot write to GitHub (B0 tells you), keep every file in your own files
   area with the path it would have had in the repository; B10 puts them into the archive.
5. **Seen, not inferred.** Write "seen" only for what you saw: a screenshot, a file you read, a
   preview you used. Everything else is "inferred" or "not checked". This applies most to
   Kotlin, which you cannot compile: every Android contract you write is recorded as
   **WRITTEN — NOT COMPILED** until Claude Code compiles and tests it in Part 5.
6. **Keep a log.** Append one line per step to `docs/base44/run-log.md`: the date, the step,
   CHECK passed or STOP, and the files produced.

---

## Part 1 — Roles in this run

Authority is split so that no role certifies its own work (ARCHITECT.md §1).

| Role | Who, in this run | May not |
|---|---|---|
| **Owner** | The people who use the app: a couple of ginseng diggers and land prospectors. Their word about their own data and use overrides everything below | — |
| **Lead architect** | **You, the Base44 Superagent** | Mark your own design "audited" or your own code "verified" |
| **Researcher** | You, using web browsing. Every fact carries a link, and anything you could not confirm is marked UNVERIFIED | Choose the design on a fact you have not linked |
| **Independent auditor** | A **new Superagent conversation with no memory of this one**, given only the claims (B6). If you cannot open one, the owner runs B6 with another assistant, or Claude Code audits at Part 5. Record which | See your reasoning, only the claims |
| **Web contractor** | Base44's app editor working on SingNav, one contract per change | Change files outside its contract |
| **Android contractor** | You, writing Kotlin on the branch, one contract per commit. If you cannot write to GitHub, the contract stays a written specification and Claude Code builds it in Part 5 | Claim it compiles |
| **Inspector** | You, re-reading each diff against its contract with checklist T6 | Pass a diff whose tests do not exist |
| **Release clerk (APK)** | **Claude Code only**, in Part 5 | — |

---

## Part 2 — Hard rules (they hold in every step)

1. **Code and schemas only, never data.** Never export, copy, paste or archive SingNav's data:
   no entity records, user locations, finds, tracks, uploads or chat logs. An entity's schema is
   code; its rows are not.
2. **No secrets anywhere you write.** No API keys, tokens, passwords, personal access tokens or
   keystores in code, blueprints, logs or the archive. Base44 secrets stay in Base44's secrets
   settings. GENSINGO keeps the user's own model keys on the phone (`research/KeyVault.kt`).
3. **The owners' finds are true.** Never demote, filter, down-weight or drop a find the owners
   recorded, whatever its GPS accuracy (README, "Find"; `memory/FieldMemoryRepository.kt`).
4. **Terrain, never legality.** Suggestions say where terrain looks right, **never** where
   digging is allowed. Land ownership, protected areas and state season rules stay visible.
5. **Location stays on the device unless the owner consents.** A request to a language model
   carries the ~11 km grid cell, never a GPS fix, track or find location (mirror
   `research/ResearchPrompt.kt` exactly). If SingNav sends precise location anywhere, that is a
   finding to fix in B9, not code to copy.
6. **Never log a coordinate or a map tile URL** (a tile URL encodes where the user is).
7. **Branch only.** Never commit to `main`, never force-push, never delete a branch.
8. **No claim without evidence.** No "tests pass" unless you saw them run, and no "works"
   without a screenshot.
9. **Minimal.** One screen, few buttons, works offline in the woods, easy on the battery. When
   in doubt, cut it and put it on the open list.
10. **Copy before you write, in this order:**
    1. SingNav's own code (the owner's, so no licence question);
    2. GENSINGO's own code;
    3. a permissively licensed open-source file, with attribution in `THIRD_PARTY_NOTICES.md`;
    4. new code.

    Never copy GPL code into either app.

---

## Part 3 — Facts you can rely on

### GENSINGO (Android), as of the commit this file was added in

| What | Value |
|---|---|
| Repository | `github.com/libriopal/GENSINGO` (public), default branch `main` |
| Stack | Kotlin 2.2.10, Jetpack Compose, AGP 8.11.2, `compileSdk`/`targetSdk` 36, `minSdk` 26, package `com.ginsengo.steward` |
| Map | MapLibre Android 13.6.1 (no terrain API); its own GLES 3 renderer for 3D (`terrain3d/`) |
| Data | Room 2.7.1. Tables `track_points`, `finds`, `suggestions`, `research_runs` (`data/db/FieldMemory.kt`) |
| Language models | `research/ClaudeResearchClient.kt` (Anthropic Java SDK 2.65.0) and `research/GeminiResearchClient.kt`. The model's answers are fenced by `ResearchValidator.kt` and `SuggestionAssembler.kt`: it can rank the phone's own candidates, never add places |
| Habitat model | `terrain/GinsengSuitability.kt`: six published factors (heat load, slope position, wetness, slope angle, curvature, elevation), with weights `PRIOR_WEIGHTS`. Optimum bands, deliberately not monotonic. Learned weights are adopted only after a held-out test (`learn/FindLearner.kt`) |
| Water, contours | `terrain/Hydrology.kt` (pits filled, D8 routing, channels by drainage area), `terrain/Isolines.kt` + `ContourLines.kt` (maplibre-contour port) |
| One map | `ui/MainScreen.kt`, `ui/map/FieldMap.kt` (2D), `ui/Terrain3DView.kt` (3D). The two share one camera (`terrain3d/CameraMath.kt`); see `docs/blueprints/one-map.md` |
| Source layout | `app/src/main/java/com/ginsengo/steward/{compliance,core,data,field,geo,habitat,learn,memory,prospect,research,soil,terrain,terrain3d,ui,verify}` |
| Tests | JUnit 4 (+ Robolectric) in `app/src/test/java/com/ginsengo/steward/…`. 339 pass at this commit. Mutation harness: `tools/mutate.py` |
| Build | `./gradlew :app:testDebugUnitTest`, then `./gradlew :app:assembleField` (the shrunk build, about 21 MB, delivery limit 30 MiB) |
| Governance | `ARCHITECT.md`, `EINCOL.md`, `docs/blueprints/*.md`, `EINCOL_REPORT.md` (the project record; the latest Phase's "Open" list is the backlog), `THIRD_PARTY_NOTICES.md` |
| Doctrine | Every feature serves one of: **find good ground → get there → record what you found** (ARCHITECT.md §8) |

### SingNav (Base44)

- **Not known to this file.** Step B2 measures it.
- The GitHub repository `libriopal/singnav2.0` holds only a stock Google AI Studio README (one
  commit, checked 2026-10-01). **It is not SingNav's code.** Do not inventory it as SingNav.
- **Base44 facts** used below, checked 2026-10-01 against Base44's documentation. Re-check them
  in B0, since products change:
  - code export is a ZIP from the code view's download icon, or a two-way GitHub sync, both on
    the Builder plan or higher;
  - an export API exists, `GET /api/apps/{app_id}/coding/export-to-zip`, in beta and requiring a
    personal access token;
  - a Superagent can work on Base44 apps from chat, create files, browse the web, and use a
    GitHub connector.

---

## Part 4 — The steps

### B0 — Access check

**DO:** Confirm, by trying each one:
- (a) you can open SingNav in Base44 and read its code: pages, components, entities, backend
  functions, integrations;
- (b) your GitHub connector can read `libriopal/GENSINGO`;
- (c) it can create and push the branch `base44/singnav-integration` from `main`;
- (d) SingNav's code can be exported, by ZIP download, GitHub sync or the export API;
- (e) you can create a ZIP file yourself.

**PRODUCE:** An access table with five rows, (a)–(e), each saying yes, no or partly, plus how
you checked it.

**CHECK:** (a), (b) and (d) are **required**: if any is "no", STOP (T1). (c) and (e) are
optional. If (c) is "no", every Android contract becomes a specification for Part 5. If (e) is
"no", the owner assembles the ZIP in B11.

### B1 — Read the governance

**DO:** Read, in this order:
1. `ARCHITECT.md`, all of it;
2. `EINCOL.md` §1–5;
3. `docs/blueprints/one-map.md`, the model of a finished blueprint: question, measurements,
   distribution, reuse table, design, contracts with tests, claims, audit with both versions
   kept, log;
4. `README.md`;
5. `THIRD_PARTY_NOTICES.md`;
6. the "Open" list of the latest Phase in `EINCOL_REPORT.md`.

**PRODUCE:** In `docs/base44/run-log.md`, five lines in your own words:
- the doctrine;
- the eight gates;
- the exit condition;
- the open-list items that SingNav might close;
- the release checklist's items 1 to 7.

**CHECK:** Each of the five lines names something concrete from the files: a gate name, a file
name, an open-list number.

### B2 — Inventory SingNav (measure, do not remember)

**DO:** Walk every file in SingNav's code view, and every entity, backend function, integration
call and npm package.

**PRODUCE:** `docs/base44/singnav-inventory.md` with one table per kind, using row template T2:
- **Pages and components:** path, purpose in one line, lines of code, what it depends on, whether
  it touches location (Y/N).
- **Entities:** name, the field list with types, who writes and who reads it. **Schema only,
  never rows.**
- **Backend functions:** name, what triggers it, what it calls outside Base44, which secrets it
  needs (names only).
- **Integrations and language-model calls:** where they are called, the **prompt text verbatim**
  (a prompt is code), what location data goes in.
- **npm packages:** name, version, licence (from each package's own page, linked).
- **Totals:** file count and lines of code per kind.

**CHECK:**
- Every file in the code view appears in exactly one row, and the totals add up.
- No row contains a data value from an entity.
- Every call that sends location is flagged.

### B3 — One question, and a distribution

**DO:** Write the iteration's one question, with the B2 numbers beside it. Default question,
unless B2 shows a better one:

> *Which of SingNav's written code should GENSINGO copy, and how does SingNav on the web become
> the same tool for planning at home that GENSINGO is in the field, without breaking either
> app's doctrine or GENSINGO's gates?*

Then write 5 to 8 candidate designs, each with an honest probability that it is the right one.
Include the tail. Some rows to consider:
- port SingNav's features into GENSINGO in Kotlin;
- SingNav as the web companion for planning at home, sharing with GENSINGO through a file
  format (GPX or GeoJSON import and export of waypoints and plans, **owner-initiated**, never
  automatic);
- embed SingNav in GENSINGO through a WebView. Probably rejected: offline, battery and privacy,
  but work the reasons out;
- port GENSINGO's habitat model to SingNav's web code;
- leave the two apart.

For each row, write what would have to be true for it to be right.

**PRODUCE:** Sections A (question and measurements) and B (distribution) of
`docs/blueprints/singnav-integration.md`.

**CHECK:**
- The question is a single question.
- Each candidate has a probability and a "what would have to be true".
- The least likely row has been worked, not just listed.

### B4 — Reuse map (copy before you write)

**DO:** Give every B2 row exactly one decision, using row template T3:
- **COPY-WEB:** it stays in SingNav, polished in B9.
- **PORT-KOTLIN:** rewritten in Kotlin inside GENSINGO. Say which doctrine step it serves and
  which GENSINGO file it joins.
- **USE-GENSINGO:** GENSINGO already does this better. Name the file, and say why.
- **DROP:** the reason. It goes on the open list if the owners might want it later.

Then do the reverse: list the GENSINGO code worth copying into SingNav's web version. For
example:
- the habitat scoring of `GinsengSuitability.kt`, ported to JavaScript and checked against
  test vectors computed from the Kotlin code;
- the privacy wording of the Layers sheet;
- the "terrain, never legality" disclaimer;
- the colour ramp of `SuitabilityRasterizer.RAMP`, so a strong-habitat colour means the same
  thing in both apps.

For any open-source code either way, record the URL, the licence, and why it fits.

**PRODUCE:** Section C (reuse) of the blueprint, as a decision table.

**CHECK:**
- Every inventory row has a decision.
- Every PORT-KOTLIN row traces to find, get there or record.
- No GPL code appears.

### B5 — Blueprint and contracts

**DO:** Write the rest of `docs/blueprints/singnav-integration.md`, with the same sections as
`one-map.md`:
- **D, design:** the chosen design and the rejected alternatives, each with its reason.
- **E, work packages:** every Android package uses contract template T4.
  - Its acceptance tests are written **into the contract text**, as JUnit test names and the
    property each test measures.
  - Each has a negative control: the change that must make a named test fail.
  - Files you may touch are listed explicitly; everything else is off limits.
  - Budgets: the APK stays at or under 30 MiB; no new Android permission without the owners'
    written yes; no new network call that carries location; any new Gradle dependency is listed
    with its version and licence.
  - Every web package uses template T5. Its acceptance checks are steps in the Base44 preview
    with the expected result, run on test data you made up, never real data.
- **F, claims:** the load-bearing claims, as a numbered list the auditor can attack.
- **G, audit:** left empty for B6.
- **H, log.**

**CHECK:** Gates G0 (scope), G1 (reuse) and G2 (blueprint) of ARCHITECT.md §3 hold. In
particular, every contract has its tests and its negative control **before** any code is
written.

### B6 — Independent audit

**DO:** Open a **new Superagent conversation with no memory of this one**. If you cannot, ask
the owner to run this step with another assistant. Paste **only** section D's design, section
F's claims, and the auditor prompt template T7, filled in. Never paste your reasoning, B3, or
this file.

**PRODUCE:** Section G of the blueprint:
- the auditor's objection, verbatim;
- your investigation of it;
- for each attacked claim, the first version and the corrected version **side by side**. Keep
  both.

**CHECK:** Gate G3 holds: each objection is fixed in the design or answered in writing. If the
objection breaks the design, return to B5 and run B6 again with a fresh auditor.

### B7 — Build the Android contracts (branch only)

**DO:**
- If B0(c) is "yes": create `base44/singnav-integration` from the current `main` (record the
  `main` commit hash as `base_commit`). Then, for each Android contract, in dependency order:
  1. write the tests first, exactly as the contract names them, in
     `app/src/test/java/com/ginsengo/steward/…`;
  2. write the code;
  3. touch only the contract's files;
  4. commit once per contract, with the message `WP-<id>: <one line> — WRITTEN, NOT COMPILED`.
- Follow the conventions of the files you join:
  - the package `com.ginsengo.steward.<area>`;
  - Kotlin, not Java;
  - Compose patterns as in `ui/`;
  - no state written during composition;
  - comment density like the surrounding code;
  - no logging of coordinates;
  - `minSdk` 26 APIs only.
- If any open-source code is copied, add its notice to `THIRD_PARTY_NOTICES.md` in the same
  commit.
- If B0(c) is "no": do not write code. Each contract stays a complete specification, and Claude
  Code builds it in Part 5.

**PRODUCE:** The branch, with one commit per contract, or the specifications.

**CHECK:**
- Every contract's tests exist under the names the contract gave.
- No file outside a contract changed (compare the branch to `base_commit`).
- Every commit message says NOT COMPILED.

### B8 — Inspect every diff

**DO:** Read each contract's diff against its contract, with checklist T6.

**PRODUCE:** One line per contract in the blueprint log: merge, fix-then-merge, or reject, with
the reasons.

**CHECK:** No contract is left at "reject". Every "fix-then-merge" is fixed and inspected again.

### B9 — Polish SingNav's web version in Base44 (right before the APK step)

**DO:** Execute the web contracts of B5 in Base44. Then go through this polish list in the
Base44 preview, on test data only. Take a screenshot as evidence of each item.

1. **Phone first:** usable at 360 px wide, nothing cut off, tap targets at least 44 px.
2. **Minimal:** remove dead pages and buttons nobody uses (B2 tells you which). One main screen.
3. **States:** every list and map has a loading state, an empty state ("nothing yet: here's how
   to start") and an error state that says what to do.
4. **Offline and slow networks:** what works without a connection is said on screen. Use
   offline-first behaviour where Base44 supports it, and say plainly what needs a connection.
5. **Same meaning as GENSINGO:**
   - the same habitat colour ramp and legend wording;
   - the same "terrain, never legality" disclaimer;
   - the same privacy wording (what is sent to a model, and what never is);
   - finds treated as true.
6. **Privacy fixes from B2:**
   - every model call that carried precise location is changed to the ~11 km grid cell, or
     removed;
   - no secret is reachable from the browser;
   - model calls go through backend functions.
7. **Hand-off between the apps** (if the design chose it): export and import of waypoints and
   plans in the agreed file format, started only by the owner, with a round-trip test on made-up
   points.
8. **Speed:** the first screen is usable within a few seconds on a phone connection. Remove
   heavy unused packages.
9. **Accessibility:** text contrast, labels on icon buttons, readable font sizes.

**PRODUCE:** `docs/base44/web-polish.md`, one row per item: done (with the screenshot's file
name), or open (with the reason).

**CHECK:** Every item is done with a screenshot, or on the open list with its reason. Nothing is
marked done without a screenshot.

### B10 — Export both codebases

**DO:**
1. **SingNav:** export the current saved code as a ZIP, from the code view's download icon or
   from GitHub sync. Unzip it.
2. **GENSINGO:**
   - If the branch exists, download the branch `base44/singnav-integration` as a ZIP (on GitHub:
     Code → Download ZIP, on that branch). Record the branch's head commit hash.
   - Otherwise: download `main` as a ZIP and add your B2–B9 documents and specifications at
     their repository paths.
   - Unzip it.

**CHECK:**
- The SingNav folder has a `package.json` and source files.
- The GENSINGO folder has `settings.gradle.kts`, `gradlew`, `app/`, `ARCHITECT.md`, this file,
  and your blueprint.
- **Neither folder** contains any of these:
  - `node_modules/`, build output, `.gradle/`;
  - a keystore, `keystore.properties`, `local.properties`, a `.env` file other than
    `.env.example`;
  - an APK or AAB;
  - data exports (CSV, SQLite, JSON dumps of entity rows).

### B11 — Assemble the handoff archive

**DO:** Create exactly this layout and ZIP it. If B0(e) was "no", give the owner this layout to
assemble with their computer's "compress" command.

```
gensingo-singnav-handoff-YYYYMMDD.zip
├── HANDOFF.json      (template T8, filled in)
├── singnav-web/      (the SingNav export, unzipped, as exported)
└── gensingo/         (the GENSINGO codebase from B10, unzipped)
```

The ZIP may also wrap these three in one top-level folder. Nothing else goes at the top level.

**CHECK:** Go through B12 by hand. Every line must hold.

### B12 — Self-check (exactly what `tools/verify_handoff.py` will check)

1. The archive opens. Its top level is `HANDOFF.json`, `singnav-web/` and `gensingo/`, possibly
   inside one wrapping folder.
2. `HANDOFF.json` parses as JSON and has every field of T8 with the right type, with
   `handoff_version` equal to `1`.
3. `gensingo/` contains `settings.gradle.kts`, `gradlew`, `app/build.gradle.kts`,
   `app/src/main/AndroidManifest.xml`, `ARCHITECT.md`, `base44.md`, and the blueprint path
   named in `HANDOFF.json`.
4. `singnav-web/` contains a `package.json` that parses, and at least one source file (`.js`,
   `.jsx`, `.ts` or `.tsx`).
5. **None of these** is anywhere in the archive:
   - `node_modules/`, `build/` output, `.gradle/`;
   - `*.jks`, `*.keystore`, `keystore.properties`, `local.properties`;
   - `.env` files other than `.env.example`;
   - `*.apk`, `*.aab`;
   - in `singnav-web/`, any `*.csv`, `*.sqlite` or `*.db` file, or a JSON file holding rows
     with latitude and longitude.
6. No text file contains a secret: an Anthropic, Google or GitHub key, or a private key block.

### B13 — Hand off to Claude Code

**DO:** Give the owner the archive and this message to send to Claude Code with it:

> Here is the Base44 handoff archive for GENSINGO. Read base44.md Part 5 and run it exactly:
> verify the archive, put the GENSINGO update on its branch, compile, run every gate, fix only
> what fails to compile or test (recording each fix against its contract), build the APK, and
> deliver it. Then give me the zipped final codebases.

**PRODUCE:** In the run log, the archive's file name and its size, the list of contracts, the
open list, and the B6 auditor and B8 inspector results.

**CHECK:** The owner has the archive. Your part ends here.

---

## Part 5 — The APK step (Claude Code only; the one gate the archive waits on)

Run this in a Claude Code session on `libriopal/GENSINGO`, in order.

1. **Verify:** put the archive in the working directory and run
   `python3 tools/verify_handoff.py <archive>`. It must print `PASS`. On `FAIL`, send the owner
   its output and stop.
2. **Branch:**
   - If `HANDOFF.json` names a `gensingo.head_commit` on GitHub: fetch
     `base44/singnav-integration`, check out that commit, and check that its tree equals the
     archive's `gensingo/` (`git diff --no-index`). They must be identical; if not, stop and say
     what differs.
   - Otherwise: create `base44/singnav-integration` from `gensingo.base_commit`, copy
     `gensingo/` over it, and commit `Base44 handoff YYYYMMDD`.
   - Never `main`.
3. **Build the contracts that are only specifications:** use ARCHITECT.md §2 F–G (contractors,
   inspector).
4. **Compile and test (G4, G5):**
   - `./gradlew :app:testDebugUnitTest`.
   - Fix only what fails to compile or test. Record each fix against its contract as a finding
     ("Base44 contract WP-x: …").
   - Show each contract's negative control failing, by adding it to `tools/mutate.py` with a
     new, unused ID and running it.
   - Run `bash tools/validate_shaders.sh` if shaders changed.
   - Never commit while the harness runs.
5. **Device (G6):** run on an emulator or device if one is available. Otherwise put it on the
   open list with the reason.
6. **Release (G7, ARCHITECT.md §7):**
   - clean tree;
   - `./gradlew :app:assembleField` from committed HEAD;
   - `apksigner verify`;
   - size at or under 30 MiB;
   - a class only the new code has is present in the dex;
   - deliver the APK file itself;
   - push the branch;
   - merge to `main` only when the owner says so.
7. **Give back:** a ZIP of the final GENSINGO codebase (`git archive` of the release commit) and
   the `singnav-web/` folder exactly as received, plus the APK.
8. **Record:** a new Phase in `EINCOL_REPORT.md`, "Base44 handoff". Include what Base44 built,
   what failed to compile and how it was fixed, the gates, and the open list.

---

## Part 6 — Templates

**T1 — Stop report**
```
STOP at B<n>. CHECK that failed: <quote it>. What I tried: <steps>. What I need from you:
<one concrete thing>. Nothing after B<n> has been started.
```

**T2 — Inventory row (B2)**
```
| path or name | kind | purpose (one line) | LOC | depends on | location? Y/N | external service / secret names |
```

**T3 — Reuse decision (B4)**
```
| inventory row | decision COPY-WEB / PORT-KOTLIN / USE-GENSINGO / DROP | doctrine step | target file | reason | licence (if OSS) |
```

**T4 — Android contract** (ARCHITECT.md §5, unchanged)
```
WORK PACKAGE <id>: <name>
Purpose: <one sentence, traced to the B3 question and to find / get there / record>
Files you may create or change: <explicit list>   Files you must not touch: everything else
Inputs you can rely on: <GENSINGO APIs and SingNav code, with paths>
Deliverable: <functions/classes/behaviour, exact Kotlin signatures where they meet other packages>
Copied from: <SingNav path(s) / GENSINGO path(s) / OSS URL + licence; attribution yes/no>
Acceptance tests (written first, in app/src/test/java/com/ginsengo/steward/<area>/<Name>Test.kt):
  1. <testName> — <the property it measures, and how>
Negative control: <the edit that must make test N fail>
Budgets: APK ≤ 30 MiB; no new permission; no location in any network call; new deps listed
Status: WRITTEN — NOT COMPILED   (Claude Code changes this in Part 5)
```

**T5 — Web contract**
```
WEB PACKAGE <id>: <name>
Purpose: <one sentence, traced to the B3 question>
Files / pages / entities / functions you may change: <list>
Copied from: <GENSINGO path or SingNav path>
Acceptance checks (Base44 preview, made-up data only):
  1. <action> → <expected result> — screenshot <file name>
Negative check: <what must visibly break if the change is undone>
```

**T6 — Inspector checklist (B8)**
```
Files outside the contract? Tests present and named as the contract says? Negative control
perturbs something the code actually reads? Any coordinate logged? Any location sent off the
device? Any secret? Any data row? Any find demoted? Verdict: merge / fix-then-merge / reject.
```

**T7 — Auditor prompt (B6; paste into a fresh conversation with section D and F only)**
```
You are an independent auditor. You did not write this and have no stake in it.
<section D: the chosen design>  <section F: the numbered claims>
Name the single strongest technical objection, and one cheaper instrument that catches the
same class of defect. Be brief and concrete.
```

**T8 — `HANDOFF.json`**
```json
{
  "handoff_version": 1,
  "created": "YYYY-MM-DD",
  "singnav": {
    "base44_app_name": "SingNav",
    "export_method": "zip-download | github-sync | export-api",
    "exported_at": "YYYY-MM-DD"
  },
  "gensingo": {
    "base_commit": "<main commit hash the branch started from>",
    "branch": "base44/singnav-integration",
    "head_commit": "<branch head hash, or null if no GitHub write>",
    "contracts": ["WP-…"],
    "tests_added": ["app/src/test/java/com/ginsengo/steward/…Test.kt"],
    "compiled": false
  },
  "blueprint": "docs/blueprints/singnav-integration.md",
  "audit": { "done": true, "by": "fresh Superagent conversation | other assistant | deferred to Claude Code" },
  "web_polish": { "done": ["…"], "open": ["…"] },
  "open": ["…"],
  "attest_no_data_no_secrets": true
}
```

---

## Part 7 — Failure modes to expect here

- **Kotlin written blind.** Base44 cannot compile Android code. Keep contracts small, copy the
  shapes of neighbouring GENSINGO code, and label everything NOT COMPILED. Part 5 is where it
  becomes true or false.
- **The decorative test:** a test that cannot fail. Every contract names the edit that breaks
  it.
- **Data in the export:** entity rows, uploads or logs travelling with the code. Rule 1 and
  checks B10 and B12.
- **A secret in the bundle:** a key pasted into a function or a `.env` file exported with the
  code. Rule 2 and check B12.6.
- **Two-way GitHub sync overwriting the branch.** Never sync SingNav into the GENSINGO
  repository. They are separate codebases, joined only in the archive.
- **The self-certifying architect:** you auditing your own blueprint. B6 must be a different
  conversation.
- **Inferred, not seen:** "the page works" without a screenshot.
- **Scope creep:** a SingNav feature that serves none of find, get there or record. DROP it, or
  put it on the open list.
- **Colour and wording drift:** the same habitat colour meaning something different in the two
  apps. B4 and B9.5.
- **Reinventing:** writing what SingNav or GENSINGO already has. B4 comes before B5 for that
  reason.
