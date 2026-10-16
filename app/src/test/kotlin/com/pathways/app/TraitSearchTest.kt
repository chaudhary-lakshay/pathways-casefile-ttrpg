package com.pathways.app

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The trait picker's search: ~200 rows had no way through but scrolling (play session 5). */
class TraitSearchTest {

    @Test
    fun `a blank search keeps every row`() {
        assertTrue(matchesSearch("Phobia (specify)", ""))
        assertTrue(matchesSearch("Phobia (specify)", "  "))
    }

    @Test
    fun `a search matches any part of the name, in any case`() {
        assertTrue(matchesSearch("Phobia (specify)", "phob"))
        assertTrue(matchesSearch("Combat Reflexes", "REFLEX"))
        assertFalse(matchesSearch("Combat Reflexes", "phob"))
    }
}
