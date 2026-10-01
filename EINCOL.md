# EINCOL — a protocol for finding real answers to hard problems

**Invocation:** `Reference the current goal and EINCOL.md and follow the protocol exactly.`

This file is self-contained. It assumes nothing about the project it is dropped
into. If you are an agent reading this because someone pointed you here, the
whole protocol is below and you are expected to execute it, not summarise it.

---

## 0. What this is, and when NOT to use it

EINCOL is a procedure for the case where **the obvious answer is not good
enough**: an architectural decision with no clean precedent, a problem that has
resisted a first attempt, a "why does this keep going wrong" question, a search
for what a system is missing rather than what it got wrong.

It is named for the thing it imitates. Einstein's use of reverie, Kekulé's
snake, Loewi's two-in-the-morning frog experiment — the folklore says *let the
mind drift and brilliance falls out*. **The folklore is wrong, and the science
says why.** Sleep-onset (N1) research finds that fifteen seconds in the twilight
state *tripled* insight on a hidden-rule maths problem — but N1 is specifically
**semilucid**: "freely watch the mind wander *while maintaining a logical
ability to identify creative sparks*." And a 2025 incubation study found **no
significant effect of mind-wandering on divergent thinking**, with conscious
reflection beating uncontrolled drift.

The insight does not come from the wandering. It comes from wandering **with the
evaluator still on**.

The LLM literature arrives at the identical sentence from the other side.
RLHF-aligned models collapse toward typical outputs (in one ideation study, ~5%
of generated domains were unique). Temperature does not fix it — it adds noise
*inside* the mode. Persona prompting, contextualised prompting and CoT give no
significant novelty gain. What works is asking for a **distribution** instead of
an answer, and importing **structure from a distant domain**. Meanwhile models
judging their own ideas are *less reliable than human reviewers*.

> **The one rule this whole protocol implements: WIDEN THE GENERATOR, NEVER THE
> JUDGE.**

**Do NOT use EINCOL for:** a task with a known correct method, a bug with a
stack trace, a routine refactor, anything where you already know what to do.
It costs real time and its output is a small number of load-bearing findings.
Running it on an easy problem produces confident elaboration of the obvious,
which is worse than just doing the task.

---

## 1. The five steps

Execute in order. Do not skip step 5. Step 5 is the protocol.

### Step 1 — Load the cue with real material

Targeted incubation needs a **theme**, or the dream is about nothing.

- State the problem as **one specific question**, not "be creative" and not "find
  improvements". Bad: *"how could this be better?"* Good: *"where in this system
  is a claim believed without anything checking it?"*
- **Survey the actual corpus, not your memory of it.** List the files, run the
  counts, read the headers. If you are working from a summary of the material,
  you will generate about the summary.
- Write down what you measured. Numbers, not impressions.

### Step 2 — Verbalize a distribution, not an answer

This is the mechanical fix for mode collapse and it is not optional.

Produce **5–8 candidate answers with your honest probability that each is the
real one**, as a table. Generate deliberately down into the tail — the last two
rows should feel unlikely, not merely less likely.

| p | Candidate |
|---|---|
| 0.30 | …the obvious one |
| … | … |
| 0.05 | …the one you would never have said if asked for "the answer" |

**Why:** mode collapse traces to *typicality bias* in human preference data —
annotators prefer the familiar, so alignment sharpens toward the typical. Asking
for a distribution routes around it. Asking for "an answer" gets you row one.

### Step 3 — Take the tail seriously

Row one is usually true, generic, and closes nothing. **Work the tail rows.**

For each tail candidate, ask what would have to be true for it to matter. If a
candidate is a restatement of row one, discard it and generate another.

**Optional amplifier — structural analogy.** If the tail is thin, import a
*relational structure* from a genuinely distant field and ask what maps.
Distance is not achieved by asking for weirdness; it is achieved by borrowing a
mechanism. (Certificate Transparency → binding rules to a commitment.
Epidemiology's negative controls → testing whether a test can fail.)

### Step 4 — Try to falsify your own finding, in writing

State the candidate as a **general claim**, then attack it yourself.

- Find a case in the material that the claim predicts and check it.
- Find a case the claim would get wrong. **Look for this one hardest.**
- If it breaks, **do not discard it — correct it and record both versions.** The
  corrected claim is usually the valuable one, and the first version is evidence
  that the process is real rather than decorative.

A finding that survived no attack has not been tested; it has been asserted.

### Step 5 — Hand it to an evaluator you do not control

**This is the step that makes the other four honest.** Your judgement is inside
the generator. It cannot also be the judge.

See §2 for how to build one. Then:

1. Run the evaluator.
2. **When it disagrees with you, it is probably right.** Investigate before
   defending.
3. Record what it found that you did not.

If the evaluator agrees with everything, **the evaluator is broken.** An
instrument that cannot return a negative result is not measuring. Prove it can
fail before you believe that it passed.

---

## 2. The evaluator ladder

Ranked by strength. **Use the highest rung the problem allows.** Do not report a
finding as verified using rung 4 when rung 1 was available.

| Rung | Evaluator | Use when |
|---|---|---|
| **1** | **A program that breaks the thing and checks whether anyone notices** (mutation testing, fault injection, chaos runs) | Code, config, infrastructure |
| **2** | **Execution against reality** — run it, measure it, reproduce the claimed number from raw inputs | Anything with data or output |
| **3** | **An independent model**, different vendor, shown the claim WITHOUT your reasoning | Design, prose, architecture, plans |
| **4** | **A written adversarial case** you commit to before checking | Nothing else is available |

**Three rules for every rung:**

- **The negative control is mandatory.** Before trusting that the evaluator
  passed something, show it FAILING on input with the property removed. *A check
  that has only ever been seen to pass is decoration.*
- **Grep is not an evaluator.** Searching for a phrase measures vocabulary, not
  the property. If you find yourself counting occurrences of a word as evidence,
  you are on rung 5 and there is no rung 5.
- **Never let the subject be its own witness.** Comparing a claim against a
  restatement of the claim always passes.

### On rung 3 specifically

Give the independent model **the claim, not the chain of thought that made the
claim feel true** — a critic shown your reasoning reproduces your reasoning.
Ask **one sharp question**; multi-part audit prompts are a documented
degeneration mode (reasoning models loop and spend the whole budget without
answering). If it runs out of budget mid-thought, **read the reasoning trace** —
it is usually legible and converged, and extracting it is cheaper than paying
twice.

---

## 3. The sovereignty classifier

A portable tool that came out of running this protocol, useful whenever the
question is *"is this claim actually established?"*

> A truth is trustworthy only if it is pinned **IN TIME** (it existed before the
> party it constrains could have chosen it) or **AGAINST A WITNESS** (a second
> independent thing computes it and agrees). **Pinned by neither, it is a
> preference wearing a measurement's clothes.**

Apply it as a table over every load-bearing claim in the system:

| Claim | Time | Witness | Verdict |
|---|---|---|---|
| … | ✅ published first | ✅ recomputed independently | sound |
| … | ❌ | ✅ second implementation agrees | sound |
| … | ❌ | ❌ | **this is the finding** |

The rows with two ✗ are where to look. In practice they are never empty, and
they are usually the claims everyone is most confident about — confidence is
what stopped anyone checking.

---

## 4. Named failure modes

Check yourself against these. Each has occurred in real use of this protocol.

- **The vocabulary proxy.** Measuring whether a property is described rather
  than whether it holds. *(Grepping for "negative control" counted two controls
  that were broken.)*
- **The self-witness.** A check that compares an artifact to itself and always
  passes. *(A digest verified against the digest.)*
- **The vacuous control.** A negative control that perturbs something the system
  correctly ignores — comments, whitespace, dead code — so it tests nothing
  while looking rigorous. **Always assert that your perturbation changed
  something the system actually reads.**
- **The mode answer.** Stopping at row one of the distribution because it is
  true. True and generic is not a finding.
- **The equivalent mutant.** In rung-1 evaluation, a change that alters source
  without altering behaviour. Survivors are **candidates, not defects** — triage
  them by hand and never gate a build on a generated-mutant count.
- **Confusing a dropped connection with a refusal.** A timeout, a proxy reset
  and a genuine negative look identical in a log. Distinguish them explicitly.
- **Widening the scope.** The protocol finds real problems adjacent to the goal.
  Fix what you found; do not redesign the system around it.

---

## 5. Delivery — the protocol does not end at the finding

A finding that is not built is a hypothesis. Complete the cycle:

1. **Design** — state what closes the gap, and name the alternative you
   rejected and why. *Record rejected alternatives in the code or doc itself*,
   so the next person does not re-derive them.
2. **Code** — implement it, minimally. Do not widen scope.
3. **Verify** — the new work is subject to the same rule. Build its negative
   control. Watch it fail before you believe it passes.
4. **Re-run the evaluator** on the new state. New code creates new gaps; the
   second pass routinely finds them.
5. **Record honestly** — what you found, what broke, what the evaluator caught
   that you missed, and **what remains open and unfixed**. The open list is part
   of the deliverable, not an admission.

---

## 6. Worked example (real, compressed)

Applied to a deterministic-simulation game with a staking model, 35 verification
oracles, ~14k lines.

| Step | What happened |
|---|---|
| 1 | Cue: *"where is a truth here pinned by neither time nor witness?"* Surveyed all 35 oracles and measured them. |
| 2 | 7 candidates. Mode (p=0.30): "CI is editable by the party it constrains" — true, generic, closes nothing. |
| 3 | Tail row at **p=0.08**: *"commit–reveal binds the seed but not the rules the seed is fed into."* |
| 4 | First formulation — *"everything that works is time-pinning"* — **was falsified by its own author**: cross-executor parity is a witness pin, not a time pin. Corrected to the two-axis classifier in §3. |
| 5 | Built a rung-1 evaluator (mutation harness). **It disagreed three times and was right each time.** |

What the evaluator found that the author did not:

- The author's first measurement was a **phrase-grep** — the vocabulary proxy,
  committed while holding the classifier that forbids it.
- An oracle named for preventing a loaded die **did not notice a loaded die**.
  It checked that weights were not *re-derived mid-round*; a constant loaded
  before the round is not re-derived. The property proven and the property
  assumed were different sentences.
- Three branches of the author's brand-new code **had never executed**, in the
  function deciding what counts as code for a security hash.

Findings: 4 shipped fixes, each with a negative control seen to fail first.
Two items left open and stated as open.

---

## 7. Invocation templates

**Point an agent at it:**
```
Reference the current goal and EINCOL.md and follow the protocol exactly.
```

**Scoped to one question:**
```
Follow EINCOL.md exactly. Cue for step 1: "<one specific question>".
Use the highest evaluator rung available and report what it found that you did not.
```

**Audit an existing artifact:**
```
Follow EINCOL.md against <artifact>. Apply the §3 classifier to every
load-bearing claim in it and report the rows with two crosses.
```

**As the independent critic (rung 3) — send this to the OTHER model:**
```
You are an independent auditor. You did not write this and have no stake in it.
<the claim only — not the reasoning behind it>
Name the single strongest technical objection, and one cheaper instrument that
catches the same class of defect. Be brief and concrete.
```

**Continuous use:**
```
Follow EINCOL.md. Re-run the evaluator against the current state, work any new
finding, and report what is still open.
```

---

## 8. The short version

1. Ask one specific question, against material you actually measured.
2. Answer with a **distribution**, not an answer. Go into the tail.
3. Work the tail. Row one closes nothing.
4. **Attack your own claim in writing.** Correct it; keep both versions.
5. **Hand it to something you do not control.** Prove that thing can fail.
6. When it disagrees with you, it is probably right.
7. Build the fix, with a negative control seen to fail first.
8. Publish what is still open.

---

## Sources

- [Lacaux et al., *Sleep onset is a creative sweet spot*, Science Advances 2021](https://www.science.org/doi/10.1126/sciadv.abj5866)
- [*Targeted dream incubation at sleep onset increases post-sleep creative performance*, Scientific Reports 2023](https://www.nature.com/articles/s41598-023-31361-w)
- [*The Role of Mind Wandering During Incubation in Divergent and Convergent Creative Thinking*, Brain Sciences 15:595, 2025](https://www.mdpi.com/2076-3425/15/6/595)
- [*Verbalized Sampling: How to Mitigate Mode Collapse and Unlock LLM Diversity*](https://www.alphaxiv.org/abs/2510.01171v2)
- [Si, Yang & Hashimoto, *Can LLMs Generate Novel Research Ideas?*](https://arxiv.org/abs/2409.04109)
- [*Is Temperature the Creativity Parameter of Large Language Models?*, ICCC24](https://computationalcreativity.net/iccc24/papers/ICCC24_paper_70.pdf)
- [*Unlocking LLM Creativity in Science through Analogical Reasoning*](https://arxiv.org/abs/2605.11258)

