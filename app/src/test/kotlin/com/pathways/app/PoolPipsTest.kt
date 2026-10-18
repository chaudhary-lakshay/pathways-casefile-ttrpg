package com.pathways.app

import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PoolPipsTest {

    @Test
    fun `a small pool's pips stop at the largest size`() {
        assertEquals(14.dp, pipSize(max = 9, width = 330.dp))
    }

    @Test
    fun `pips shrink to fill a narrow cell, gaps counted`() {
        // 15 pips and 14 gaps of 2 dp in 176 dp: (176 - 28) / 15.
        assertEquals((148f / 15).dp, pipSize(max = 15, width = 176.dp))
    }

    @Test
    fun `a pool too large for readable pips falls back to the bar`() {
        assertNull(pipSize(max = 30, width = 176.dp))
    }

    @Test
    fun `an empty pool draws no pips`() {
        assertNull(pipSize(max = 0, width = 330.dp))
    }

    @Test
    fun `a narrow cell's segments fill its width, where square pips fall back to the bar`() {
        // Frame 04: 15 HP in a third of the strip, flex-1 segments 2 dp apart.
        assertNull(pipSize(max = 15, width = 94.dp, largest = 10.dp))
        assertEquals((66f / 15).dp, segmentWidth(max = 15, width = 94.dp))
    }

    @Test
    fun `segments too thin to count fall back to the bar`() {
        // 30 segments and 29 gaps of 2 dp in 60 dp leave 2 dp for all thirty.
        assertNull(segmentWidth(max = 30, width = 60.dp))
        assertNull(segmentWidth(max = 0, width = 94.dp))
    }

    @Test
    fun `the Vitals tab's larger pips stop at their own size, with wider gaps`() {
        assertEquals(18.dp, pipSize(max = 9, width = 330.dp, largest = 18.dp, gap = 4.dp))
        // 15 pips and 14 gaps of 4 dp in 300 dp: (300 - 56) / 15.
        assertEquals((244f / 15).dp, pipSize(max = 15, width = 300.dp, largest = 18.dp, gap = 4.dp))
    }
}
