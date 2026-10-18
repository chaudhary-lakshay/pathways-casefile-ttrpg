package com.pathways.app

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The credits screen names the people who made the game, as the rulebook's
 * own SUPPLEMENT CREDITS page prints them (book.txt lines 10241-10251).
 */
class CreditsTest {

    private fun nameFor(role: String) = CREDITS.single { it.role == role }.name

    @Test
    fun `the rulebook's author and scribes are credited as the book prints them`() {
        assertEquals("Earvin Salonoy", nameFor("Supplement designer and author"))
        assertEquals("Mr.Worm's LOM TTRPG Discord Server", nameFor("Rulebook scribes"))
    }

    @Test
    fun `the novel and the game system are credited to their owners`() {
        assertTrue("Cuttlefish That Loves Diving" in nameFor("Source material"))
        assertTrue("Steve Jackson Games" in nameFor("Game system"))
    }

    @Test
    fun `the game's makers come before the app`() {
        val roles = CREDITS.map { it.role }
        assertEquals(0, roles.indexOf("Supplement designer and author"))
        assertEquals(CREDITS.lastIndex, roles.indexOf("App"))
    }
}
