package com.pathways.app

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CaseChipTest {

    // A chip in a FlowRow that widened when picked could push the next chip
    // onto a new line and move every row under the finger.
    @Test
    fun `a chip is as wide picked as not`() {
        assertEquals(chipInsets(selected = false).total, chipInsets(selected = true).total)
    }

    @Test
    fun `only a picked chip leaves room for the check`() {
        assertEquals(CHIP_CHECK, chipInsets(selected = true).check)
        assertEquals(0f, chipInsets(selected = false).check.value)
    }

    @Test
    fun `an unpicked chip centres its label`() {
        val insets = chipInsets(selected = false)
        assertEquals(insets.start, insets.end)
    }
}
