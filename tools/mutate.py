#!/usr/bin/env python3
"""
EINCOL rung 1: break each property the app claims and check that a test notices.

Every mutation is a literal (old -> new) edit to one source file, plus the test classes
expected to kill it. The harness:
  1. refuses a mutation whose `old` text is not found exactly once (a vacuous mutation
     that edits nothing is the "vacuous control" failure mode, not a pass),
  2. applies it, runs the named test classes, records KILLED (tests failed) or SURVIVED,
  3. restores the file byte-for-byte, and verifies the restore.

A SURVIVED row is a candidate defect, not a verdict: it may be an equivalent mutant.
Survivors are triaged by hand in EINCOL_REPORT.md Phase 7.

Usage: tools/mutate.py [ID ...]      (no IDs = all)
"""
import subprocess
import sys
import time

M = "app/src/main/java/com/ginsengo/steward/"
T = "com.ginsengo.steward."

MUTATIONS = [
    # --- research: the witness layer
    ("V1", "validator accepts unknown candidate IDs", M + "research/ResearchValidator.kt",
     "if (id !in candidateIds) { idsRejected++; continue }", "if (false) { idsRejected++; continue }",
     [T + "research.ResearchValidatorTest", T + "research.ResearchRepositoryTest"]),
    ("V2", "validator accepts citations that were never retrieved", M + "research/ResearchValidator.kt",
     "Witness.EXACT -> exact[n]", "Witness.EXACT -> exact[n] ?: SourceRef(raw, raw)",
     [T + "research.ResearchValidatorTest", T + "research.ResearchRepositoryTest"]),
    ("V3", "coordinates in model prose are kept", M + "research/ResearchValidator.kt",
     'var t = COORDS.replace(text) { n++; "[location removed]" }', "var t = text",
     [T + "research.ResearchValidatorTest"]),
    ("V4", "'legal to dig' sentences are kept", M + "research/ResearchValidator.kt",
     't = LEGALITY.replace(t) { n++; "" }', "t = t",
     [T + "research.ResearchValidatorTest"]),
    ("V5", "duplicates are kept", M + "research/ResearchValidator.kt",
     "if (!seen.add(id)) { dupes++; continue }", "seen.add(id)",
     [T + "research.ResearchValidatorTest"]),
    # --- research: positions and privacy
    ("P1", "a model item is joined to the wrong candidate", M + "research/SuggestionAssembler.kt",
     "val c = byId[item.candidateId] ?: return@forEach", "val c = candidates.firstOrNull() ?: return@forEach",
     [T + "research.SuggestionAssemblyTest", T + "research.ResearchRepositoryTest"]),
    ("P2", "the prompt carries the exact fix", M + "research/SuggestionAssembler.kt",
     "fun cell(v: Double) = floor(v * 10.0) / 10.0 + 0.05", "fun cell(v: Double) = v",
     [T + "research.PromptPrivacyTest", T + "research.ResearchRepositoryTest"]),
    ("P3", "consent is not checked before sending", M + "research/ResearchRepository.kt",
     "!settings.researchConsent -> \"Research model is off. These are computed on your phone.\"",
     "false -> \"Research model is off. These are computed on your phone.\"",
     [T + "research.ResearchRepositoryTest"]),
    ("P4", "offline is not checked before sending", M + "research/ResearchRepository.kt",
     '!isOnline() -> "Offline.', 'false -> "Offline.',
     [T + "research.ResearchRepositoryTest"]),
    ("P8", "an automatic refresh calls the model without approval", M + "research/ResearchRepository.kt",
     '!allowModel -> "Updated on this phone', 'false -> "Updated on this phone',
     [T + "research.ResearchRepositoryTest"]),
    ("P9", "a stale fix starts research", M + "research/ResearchTrigger.kt",
     "if (now - fixTime > FRESH_MS) return false", "", [T + "research.ResearchTriggerTest"]),
    ("P5", "protected land is suggested", M + "research/RadiusScan.kt",
     "if (exclude(lat, lng)) continue", "",
     [T + "research.RadiusScanRealTerrainTest"]),
    ("P6", "candidates outside the radius", M + "research/RadiusScan.kt",
     "if (!inRadius(lat, lngOfCol(x.toDouble()))) continue", "",
     [T + "research.RadiusScanRealTerrainTest"]),
    ("P7", "candidates are not separated", M + "research/RadiusScan.kt",
     "if (picked.any { Prospects.distanceMetres(it.lat, it.lng, lat, lng) < minSeparationM }) continue", "",
     [T + "research.RadiusScanRealTerrainTest"]),
    # --- providers
    ("C1", "pause_turn is not resumed", M + "research/ClaudeResearchClient.kt",
     "StopReason.PAUSE_TURN -> history += msg.toParam()", "StopReason.PAUSE_TURN -> throw ResearchFailure(ResearchFailure.Status.NO_SUBMISSION, \"x\")",
     [T + "research.ClaudeWireTest"]),
    ("C2", "the submit tool is not strict", M + "research/ClaudeResearchClient.kt",
     ".strict(true)", ".strict(false)", [T + "research.ClaudeWireTest"]),
    ("C3", "a refusal reads as no submission", M + "research/ClaudeResearchClient.kt",
     "ResearchFailure.Status.REFUSED,", "ResearchFailure.Status.NO_SUBMISSION,", [T + "research.ClaudeWireTest"]),
    ("G1", "the Gemini key goes back into the URL", M + "research/GeminiResearchClient.kt",
     'post("$baseUrl/v1beta/models/$model:generateContent", body)', 'post("$baseUrl/v1beta/models/$model:generateContent?key=$apiKey", body)',
     [T + "research.GeminiWireTest"]),
    # --- learning
    ("L1", "adopt without a significance test", M + "learn/FindLearner.kt",
     "return (clusterGains.average() >= MARGIN && p < ALPHA) to p", "return (clusterGains.average() >= MARGIN) to p",
     [T + "learn.FindLearnerTest", T + "learn.LearnerGateTest"]),
    ("L2", "judge the learner on its own training data", M + "learn/FindLearner.kt",
     "val w = fitter(train, bgTrain, prior)", "val w = fitter(finds.map { it.factors }, bgTrain, prior)",
     [T + "learn.FindLearnerTest", T + "learn.LearnerGateTest"]),
    ("L3", "every find is its own cluster", M + "learn/FindLearner.kt",
     "const val CLUSTER_M = 200.0", "const val CLUSTER_M = 0.0", [T + "learn.FindLearnerTest"]),
    ("L4", "learning starts from one find", M + "learn/FindLearner.kt",
     "const val MIN_FINDS = 5", "const val MIN_FINDS = 1", [T + "learn.FindLearnerTest"]),
    ("L5", "held-out blocking falls back to a fixed 200 m (the critic's leak)", M + "research/ResearchRepository.kt",
     "linkM = s.correlationRangeM(),", "linkM = 200.0,", [T + "research.ResearchRepositoryTest"]),
    ("L6", "leakage test: range blocking replaced by 200 m", M + "learn/FindLearner.kt",
     "val cl = clusters(finds, linkM.coerceAtLeast(CLUSTER_M))", "val cl = clusters(finds, CLUSTER_M)",
     [T + "learn.LearnerLeakageTest"]),
    # The user's field data is ground truth (learn/UserFinds.kt): each of these quietly
    # demotes some of it again, and a test must notice.
    ("F1", "a find with a poor GPS fix is saved as less than confirmed", M + "memory/FieldMemoryRepository.kt",
     "verification = UserFinds.CONFIRMED,",
     "verification = if (fix.accuracyM <= 20f) UserFinds.CONFIRMED else \"UNVERIFIED\",",
     [T + "research.ResearchRepositoryTest"]),
    ("F2", "imprecise finds are kept off the learner", M + "research/ResearchRepository.kt",
     "samples += FindLearner.Sample(factors, f.lat, f.lng)",
     "if ((f.accuracyM ?: 99f) <= 20f) samples += FindLearner.Sample(factors, f.lat, f.lng)",
     [T + "research.ResearchRepositoryTest"]),
    ("F3", "patches imported from the older app are kept off the learner", M + "research/ResearchRepository.kt",
     "samples += FindLearner.Sample(factors, f.lat, f.lng)",
     "if (f.sourcePatchId == null) samples += FindLearner.Sample(factors, f.lat, f.lng)",
     [T + "research.ResearchRepositoryTest"]),
    ("F4", "a track 500 m away 'visits' a suggestion", M + "memory/FieldMemoryRepository.kt",
     "const val VISIT_M = 40.0", "const val VISIT_M = 4_000.0", [T + "research.ResearchRepositoryTest"]),
    # --- tracking and power
    ("T1", "the original accuracy/2 noise floor", M + "field/TrackFilter.kt",
     "val noise = fix.accuracyM.toDouble() * if (still) 3.0 else 1.0", "val noise = fix.accuracyM.toDouble() / 2.0",
     [T + "field.TrackFilterTest"]),
    ("T2", "no centroid when speed is missing", M + "field/TrackFilter.kt",
     "if (window.size < MIN_WINDOW) return null", "if (window.size < 1) return null",
     [T + "field.TrackFilterTest"]),
    ("T3", "spikes are stored", M + "field/TrackFilter.kt",
     "> maxSpeedMps) return null", "> 1e12) return null", [T + "field.TrackFilterTest"]),
    ("W1", "screen-off does not batch", M + "field/PowerPolicy.kt",
     "maxDelayMs = if (batch) 60_000L else 0L,", "maxDelayMs = 0L,", [T + "field.PowerPolicyTest"]),
    ("W2", "low battery still auto-researches", M + "field/PowerPolicy.kt",
     "                minDistanceM = 5f,\n                allowAutoResearch = false,\n            )\n        }\n        if (!tracking)",
     "                minDistanceM = 5f,\n                allowAutoResearch = true,\n            )\n        }\n        if (!tracking)",
     [T + "field.PowerPolicyTest"]),
    # --- heatmap and memory
    ("H1", "the 7a3656d regression: TPI and TWI dropped from the drawn surface", "app/src/main/java/com/ginsengo/steward/terrain/SuitabilityRasterizer.kt",
     "                tpiMeters = tpi,\n                twi = wet,", "                tpiMeters = -4.0,\n                twi = 8.0,",
     [T + "terrain.DrawnSurfaceTest"]),
    ("H2", "the 3D ramp drifts from the 2D ramp", M + "terrain3d/TerrainShaders.kt",
     "vec3 c2 = vec3(0.078, 0.561, 0.357);  // #148F5B", "vec3 c2 = vec3(0.000, 1.000, 0.533);  // #148F5B",
     [T + "terrain.ShaderRampParityTest"]),
    ("D1", "the separator becomes a space again", M + "data/db/Entities.kt",
     'const val SEPARATOR = "\\u001F"', 'const val SEPARATOR = " "', [T + "terrain.ConvertersTest", T + "data.MigrationTest"]),
    ("D2", "no migration from the field-tested build", M + "data/db/AppDatabase.kt",
     "val MIGRATIONS = arrayOf(MIGRATION_1_5, MIGRATION_4_5)", "val MIGRATIONS = arrayOf(MIGRATION_4_5)",
     [T + "data.MigrationTest"]),
    ("H9", "the heatmap memo reuses the wrong cell's score", M + "terrain/SuitabilityRasterizer.kt",
     "(gx.toInt().coerceIn(1, g.w - 2) - ix0)", "(gx.toInt().coerceIn(1, g.w - 2) - ix0) / 2 * 2",
     [T + "terrain.DrawnSurfaceTest"]),
    ("D3", "imported patches are demoted to LEGACY", M + "data/db/AppDatabase.kt",
     "0, 'VERIFIED', `id` ", "0, 'LEGACY', `id` ", [T + "data.MigrationTest"]),
]


def run_tests(classes):
    args = ["./gradlew", "--no-daemon", "--max-workers=2", "-q", ":app:testDebugUnitTest"]
    for c in classes:
        args += ["--tests", c]
    t0 = time.time()
    p = subprocess.run(args, capture_output=True, text=True)
    out = p.stdout + p.stderr
    compile_error = "Compilation error" in out or "e: file://" in out
    return p.returncode, compile_error, time.time() - t0


def main():
    want = set(sys.argv[1:])
    rows = []
    for mid, what, path, old, new, tests in MUTATIONS:
        if want and mid not in want:
            continue
        src = open(path, encoding="utf-8").read()
        count = src.count(old)
        if count != 1:
            rows.append((mid, what, f"INVALID (old text found {count}x)", 0))
            print(rows[-1], flush=True)
            continue
        try:
            open(path, "w", encoding="utf-8").write(src.replace(old, new, 1))
            code, cerr, secs = run_tests(tests)
        finally:
            open(path, "w", encoding="utf-8").write(src)
        assert open(path, encoding="utf-8").read() == src, f"restore failed for {path}"
        verdict = "COMPILE-ERROR" if cerr else ("KILLED" if code != 0 else "SURVIVED")
        rows.append((mid, what, verdict, round(secs)))
        print(rows[-1], flush=True)
    print("\n| ID | Mutation | Result |\n|---|---|---|")
    for mid, what, verdict, _ in rows:
        print(f"| {mid} | {what} | {verdict} |")
    killed = sum(1 for r in rows if r[2] == "KILLED")
    print(f"\nkilled {killed} / {len(rows)}")


if __name__ == "__main__":
    main()
