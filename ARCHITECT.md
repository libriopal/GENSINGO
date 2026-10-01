# ARCHITECT — a governed, self-prompting build loop for a lead architect and contractor agents

**Invocation:** `Reference the current goal, ARCHITECT.md and EINCOL.md, take the lead architect
role, and run the loop until the release gate passes.`

This file is self-contained. It assumes the agent reading it can read and write the repository,
run the build, and start sub-agents. It adapts [EINCOL.md](EINCOL.md) (find the real answer, with
an evaluator you do not control) into a **construction** procedure: one lead architect who
designs, a researcher who finds what already exists, an independent auditor who did not design
it, contractors who each build one audited piece, and gates that nothing passes on anyone's word.

---

## 0. What this is, and when not to use it

Use it when a change is too large to hold in one head: several files, a design choice with real
alternatives, and a release at the end. It costs coordination. **Do not use it** for a one-line
fix, a known bug with a stack trace, or a copy edit — do those directly.

The two rules everything below implements:

> **WIDEN THE GENERATOR, NEVER THE JUDGE.** (EINCOL) Many candidates, many contractors, one
> standard of evidence that none of them sets for themselves.
>
> **REUSE BEFORE YOU WRITE.** Code that exists, works and is licensed for you beats code you are
> about to write. Writing it again is a cost, not a contribution.

---

## 1. Roles and authority

Authority is split so that **no role can certify its own work.**

| Role | Does | May not |
|---|---|---|
| **Owner** (the people who will use the app) | Sets the goal and the constraints; their direction about their own data and use overrides the architect | — |
| **Lead architect** (the agent invoked) | Surveys, writes the distribution and the blueprint, writes contracts, integrates, decides what is open | Mark its own design "audited" or its own code "verified" |
| **Researcher** (sub-agent) | Finds prior art, libraries, licences, platform limits; reports facts with links, unverified marked as such | Write production code; choose the design |
| **Independent auditor** (sub-agent, fresh context) | Gets the blueprint's **claims**, not the reasoning; names the strongest objection | See the architect's chain of thought (a critic shown your reasoning reproduces it) |
| **Contractor** (sub-agent per work package) | Builds exactly one contract in an isolated workspace, with its acceptance tests and negative control | Change the contract, touch files outside it, or skip its tests |
| **Inspector** (the architect wearing a different hat, or a reviewer agent) | Reads each contractor diff against its contract before it merges | Merge a diff whose tests it has not seen fail first |
| **Release clerk** (a checklist, executed) | Builds the artifact from committed HEAD, verifies it, delivers it whole | Ship a build made from an uncommitted or mutated tree |

The architect may run several roles in sequence, but **the auditor must be a different agent**
than the one that wrote the blueprint, and **acceptance tests are written into the contract
before any contractor starts.**

---

## 2. The loop

Each iteration runs A to J. Iterate until the exit condition in §3 holds.

### A — Survey (EINCOL step 1)
State the goal as **one specific question**. Measure the actual system: files, versions, sizes,
what the libraries in use can and cannot do (read the binary or the source, not your memory).
Write the numbers down.

### B — Distribution (EINCOL steps 2-3)
5-8 candidate designs with honest probabilities. Go into the tail. Work the tail rows: what would
have to be true for each to be the right one?

### C — Reuse search
Before any design is chosen, the **researcher** answers: what already exists that does this —
in the libraries already linked, in the platform, in permissively licensed open source? For each
candidate: URL, licence, maintenance, fit. Prefer, in order: a feature of a library already in
the build → a small permissive dependency → vendoring a small permissive file with attribution →
writing it. Record what was found and why it was or was not used.

### D — Blueprint
The architect writes `docs/blueprints/<name>.md`:
- the question, the measurements, the distribution, the chosen design and **the rejected
  alternatives with reasons**;
- what is reused and from where (licence, attribution);
- **work packages**, each a contract (§5) with its own acceptance tests and negative control;
- the load-bearing claims, as a list the auditor can attack;
- the evidence each gate will need.

### E — Independent audit (EINCOL step 5, rung 3)
A fresh sub-agent receives **the claims only** and one sharp question (§6). Its objection is
probably right: investigate before defending. The blueprint is revised and the audit result,
including what it found that the architect did not, is recorded in the blueprint.

### F — Contract build
One contractor per work package, each in an isolated workspace (a git worktree), in parallel
when their files do not overlap. A contractor's job is done when its acceptance tests pass
**and** it has shown each negative control failing.

### G — Inspection and integration
The inspector reads each diff against its contract: files touched, tests present, negative
control real (it perturbs something the code actually reads). Merge in dependency order. Resolve
conflicts by re-reading both contracts, not by picking a side.

### H — Evaluate (EINCOL evaluator ladder)
Highest rung available, negative control mandatory:
1. **Fault injection** — the mutation harness gets one mutant per new claim; every mutant must be
   killed or triaged as equivalent by hand.
2. **Execution against reality** — the full test suite; real data fixtures; timings measured;
   the app run on a device or emulator, and what was **seen on screen** written down separately
   from what was inferred from logs.
3. **Independent model** — the auditor again, on the built state.
4. **Written adversarial case** — only when nothing above is possible, and labelled so.

### I — Release
The release clerk's checklist (§7). The artifact is delivered whole.

### J — Record, and re-loop
Append to the project report: what was built, what each evaluator found that the architect did
not, the architect's own errors, and **what remains open**. If the exit condition does not hold,
the open list seeds the next iteration's question.

---

## 3. Gates and the exit condition

| Gate | Passes when | Evidence kept |
|---|---|---|
| G0 Scope | The goal is one question; every work package traces to it; anything else goes to the open list | The question, in the blueprint |
| G1 Reuse | The researcher's report exists; every "we wrote it" has a reason no reuse fits | Report + decision table |
| G2 Blueprint | Contracts have acceptance tests and negative controls written **before** building | `docs/blueprints/` |
| G3 Audit | An independent agent attacked the claims; each objection is fixed or answered in writing | Audit section of the blueprint |
| G4 Contracts | Each package's tests pass and its negative control was seen to fail | Contractor reports, diffs |
| G5 Integration | Full suite green; new mutants killed; shaders compile; nothing outside the contracts changed | Test counts, mutant table |
| G6 Device | Seen on a device/emulator, or explicitly listed as open with the reason | Screenshots, logs, open list |
| G7 Release | §7 checklist passes | Artifact hash, size, signature |

**Exit condition:** G0-G5 and G7 pass, and G6 passes **or** every unseen item is on the open
list. The owner may waive a gate ("skip test this time"); a waived gate is recorded as waived,
never as passed.

---

## 4. The architect's self-prompt

At the start of every iteration, and whenever stuck, the architect asks itself — in writing, in
the blueprint's log — and answers before acting:

1. What is the one question this iteration answers, and what did I **measure** about it today?
2. Which candidate in my distribution would embarrass me if it turned out right? Have I worked it?
3. What already exists that does this? Have I looked, or am I remembering?
4. Which of my claims has no witness and no time pin (EINCOL §3)? That is the next test to write.
5. What would make each contractor's tests pass while the feature is still broken? Is that case
   in the negative controls?
6. What can I cut and still serve the owner? (§8)
7. What is the cheapest evaluator that could prove me wrong right now? Run it first.

---

## 5. Contract template (one work package)

```
WORK PACKAGE <id>: <name>
Purpose: <one sentence, traced to the iteration question>
Files you may create or change: <explicit list>   Files you must not touch: everything else
Inputs you can rely on: <APIs, data, fixtures, with paths>
Deliverable: <functions/classes/behaviour, exact signatures where they meet other packages>
Reuse: <library feature or OSS file to use, with licence; attribution required: yes/no>
Acceptance tests (write these first, in <test file>):
  1. <property> — <how measured>
  2. ...
Negative control: <the perturbation that must make test N fail; show it failing, then revert>
Budgets: <time, memory, size limits that are part of "done">
Report back: files changed, test names and results, the negative control's failing output, open issues.
```

## 6. Prompt templates

**Researcher**
```
You are the RESEARCHER. Report facts with links; mark anything unverified UNVERIFIED. No code.
Context: <stack, versions, constraints>. Question(s): <numbered>. For each candidate give URL,
licence, maintenance, fit. End with a 3-line recommendation. At most <N> words.
```

**Independent auditor** (fresh agent; claims only, never the reasoning)
```
You are an independent auditor. You did not write this and have no stake in it.
<the blueprint's load-bearing claims and the chosen design, nothing else>
Name the single strongest technical objection, and one cheaper instrument that catches the same
class of defect. Be brief and concrete.
```

**Contractor**
```
You are a CONTRACTOR. Build exactly this contract, in your isolated workspace, and nothing else.
<the contract, verbatim>
Write the acceptance tests first. Run them; show each negative control failing, then passing
after the fix. Do not edit files outside the list. Report as the contract asks.
```

**Inspector**
```
Review this diff against its contract. Answer: files outside the contract? tests present and
named for the property? negative control perturbs something the code reads? Any behaviour the
tests would not notice breaking? Verdict: merge / fix-then-merge / reject, with reasons.
```

---

## 7. Release checklist (the "untruncated APK")

1. Working tree clean; **no mutation harness or other tool has a file modified** (check for its
   process; a commit made while a mutant is applied ships the mutant).
2. Build from committed HEAD; record the commit hash.
3. Full unit suite green (or the waiver recorded).
4. Signature verified (`apksigner verify`); ABIs listed; size recorded and under the delivery
   limit (a file the channel cannot carry is not delivered).
5. A string or class only the newest code contains is present in the artifact's dex.
6. Deliver the file itself; confirm the delivery tool reports it delivered, not just sent.
7. Push the branch; local HEAD equals the remote's.

---

## 8. Product doctrine (this repository)

The owners are **a couple of ginseng diggers and land prospectors**, using it themselves.
Every feature must serve one of: **find good ground → get there → record what you found.**

- Minimal: one screen, few buttons, works offline in the woods, easy on the battery.
- The owners' own records are true and accurate (their direction): never demote their finds.
- Suggestions say where terrain looks right, **never** where digging is legal; land ownership,
  protected areas and state season rules stay visible.
- Nothing that locates a find or a fix leaves the phone without consent.
- When in doubt, cut it and put it on the open list.

---

## 9. Named failure modes

From EINCOL, plus what happened in this repository:

- **The self-certifying architect** — the designer marks the design audited. Use another agent.
- **Contract drift** — a contractor "improves" outside its file list. Reject the diff.
- **The decorative test** — a test that never failed. Show the negative control failing.
- **Committing a live mutant** — `git add -A` while the mutation harness has a file mutated.
  Check for the harness process before every commit (this happened, Phase 8 log).
- **The self-matching process check** — `pgrep -f pattern` matches its own command line. Use a
  bracketed pattern (`[q]emu`).
- **The falsy report** — a test-report script that treats an empty XML element as "no failure".
  Check `is None`, not truthiness.
- **Contended timing** — a measurement taken while something else used the CPU, reported as a
  result. Record the conditions or do not report the number.
- **Inferred, not seen** — "it rendered" from a log line. Only a screenshot is seen.
- **The waived gate reported as passed.** Record waivers as waivers.
- **Reinventing the wheel** — writing what a linked library already does. Search first (§2 C).

---

## 10. The short version

1. One question, measured today.
2. A distribution of designs; work the tail.
3. Search for what exists before writing anything.
4. Blueprint with contracts whose tests and negative controls exist before the code.
5. An auditor who never saw your reasoning attacks the claims.
6. Contractors build one contract each, in isolation; the inspector merges.
7. Mutants, tests, device: watch every check fail before you believe it passes.
8. Release from a clean committed tree; deliver the whole file; publish what is open.
