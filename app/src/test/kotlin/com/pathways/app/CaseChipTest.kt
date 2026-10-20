package com.pathways.app

import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CaseChipTest {

    // Issue #2: a picked chip that cannot be changed drew its label in the
    // disabled ink colour on the ink fill, and read as a blank black box.
    @Test
    fun `a picked chip that cannot change is still readable`() {
        val label = chipContent(selected = true, enabled = false, CaseFile)
        val fill = CaseFile.secondaryContainer
        val shown = label.compositeOver(fill)
        val (hi, lo) = listOf(shown.luminance(), fill.luminance()).sortedDescending()
        assertTrue((hi + 0.05f) / (lo + 0.05f) >= 3f, "contrast ${(hi + 0.05f) / (lo + 0.05f)}")
    }

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
