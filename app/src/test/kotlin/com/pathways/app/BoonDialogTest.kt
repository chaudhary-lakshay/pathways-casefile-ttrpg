package com.pathways.app

import com.pathways.rules.Boons
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BoonDialogTest {

    @Test
    fun `an ordinary target is printed plainly`() {
        assertEquals("3d6 ≤ 8", targetLine(8))
        assertFalse("still succeeds" in targetLine(4))
    }

    @Test
    fun `a hopeless-looking target still says what can beat it`() {
        // REGRESSION (Codex): the boon-level doubling drives the target far
        // below 3 at the low sequences -- Sequence 0 is -512 -- and the line
        // read "3d6 <= -512", which says impossible. It is not: SuccessRoll
        // treats a rolled 3 or 4 as a critical success whatever the target,
        // and this chapter prints no override of that.
        val line = targetLine(Boons.penaltyForSequence(0))
        assertTrue("-512" in line)
        assertTrue("3 or 4 still succeeds" in line)
        assertTrue("still succeeds" in targetLine(3))
    }
}
