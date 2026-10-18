package com.pathways.app

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RollFooterTest {

    // The arrangement is the window's, never the caption's: a pick that
    // lengthens "3d6 ≤ 9" to "3d6 ≤ 16, 17–18 fail" must not move Roll to
    // another line (Codex, 6ii).
    @Test
    fun `a phone at large text stacks the button under the target`() {
        assertTrue(footerStacks(screenWidthDp = 390, fontScale = 1.3f))
    }

    @Test
    fun `a phone at normal text stacks too`() {
        assertTrue(footerStacks(screenWidthDp = 390, fontScale = 1.0f))
    }

    @Test
    fun `the tablet in portrait keeps one line`() {
        assertFalse(footerStacks(screenWidthDp = 600, fontScale = 1.0f))
    }

    @Test
    fun `the tablet at large text stacks`() {
        assertTrue(footerStacks(screenWidthDp = 600, fontScale = 1.3f))
    }
}
