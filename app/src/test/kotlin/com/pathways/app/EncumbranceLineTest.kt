package com.pathways.app

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** The Vitals tab's carried-weight line and the Dodge penalty Defend takes from it. */
class EncumbranceLineTest {

    @Test
    fun `the line names the level and its penalties, and Defend takes the dodge half`() {
        // ST 10: BL 20 lbs.
        assertEquals("BL 20 lbs · none", encumbranceLine(null, 20.0))
        assertEquals("BL 20 lbs · None, no penalty", encumbranceLine(15.0, 20.0))
        assertEquals("BL 20 lbs · Light: Move -1, Dodge -1", encumbranceLine(35.0, 20.0))
        assertEquals("BL 20 lbs · Overload: Move -5, Dodge -5, cannot run", encumbranceLine(300.0, 20.0))
        assertEquals("BL 20 lbs · over 15 × BL: the table prints no level, so it cannot be carried", encumbranceLine(301.0, 20.0))
        assertEquals(0, encumbranceDodge(null, 20.0))
        assertEquals(-2, encumbranceDodge(50.0, 20.0))
        assertEquals(0, encumbranceDodge(301.0, 20.0))
    }
}
