package com.ginsengo.steward.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * J23: no animations. Each one is frames drawn while nothing is learned; with the cross-fade gone
 * none remain, and this keeps it so.
 */
class NoAnimationTest {

    private val pattern = Regex(
        "\\bAnimatable\\(|\\banimate\\w*AsState\\b|\\bAnimatedVisibility\\b|\\bAnimatedContent\\b|\\bCrossfade\\b|" +
            "\\brememberInfiniteTransition\\b|\\bupdateTransition\\b|\\bValueAnimator\\b|\\bObjectAnimator\\b|\\.animate\\(\\)"
    )

    @Test
    fun theUiSourcesHoldNoAnimation() {
        val root = File("src/main/java/com/ginsengo/steward")
        val files = root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue("${files.size} sources", files.size > 50)
        val hits = files.flatMap { f ->
            f.readLines().mapIndexedNotNull { i, line -> if (pattern.containsMatchIn(line)) "${f.path}:${i + 1}: ${line.trim()}" else null }
        }
        assertEquals(emptyList<String>(), hits)
    }

    /** NEGATIVE CONTROL: the scan finds the forms it is meant to find. */
    @Test
    fun theScanSeesAnAnimation() {
        for (s in listOf(
            "val a = remember { Animatable(0f) }",
            "val alpha by animateFloatAsState(1f)",
            "AnimatedVisibility(visible) {",
            "Crossfade(targetState = x) {",
            "view.animate().alpha(0f)",
        )) assertTrue(s, pattern.containsMatchIn(s))
        assertTrue(!pattern.containsMatchIn("val animated = false // a comment about Animation"))
    }
}
