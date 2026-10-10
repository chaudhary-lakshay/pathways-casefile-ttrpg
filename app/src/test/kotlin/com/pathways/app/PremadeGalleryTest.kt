package com.pathways.app

import com.pathways.model.BeyonderState
import com.pathways.rules.Premades
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Who the gallery calls a Beyonder (6ft). No sheet prints a pathway, so the
 * answer is the save's: a character is a Beyonder once walked onto one.
 */
class PremadeGalleryTest {

    private val people = Premades.all().filterNot { "Blank" in it.name }
    private val error = BeyonderState("Error", 9, 0)
    private val saved = mapOf(
        "Journalist" to SavedSession(beyonder = error),
        // Opened and edited, never walked.
        "Church Healer" to SavedSession(),
    )

    @Test
    fun `a walked character is a Beyonder whatever SPI its sheet prints`() {
        assertEquals(error, walkOf(Premades.byName("Journalist"), saved))
        assertNull(walkOf(Premades.byName("Church Healer"), saved))
    }

    @Test
    fun `a sheet that prints SPI 9 and has drunk nothing is a mortal`() {
        // Barmaid Brawler prints SPI 9 and no pathway; the old tab read that
        // cell and filed her under Beyonders.
        assertEquals(9, Premades.byName("Barmaid Brawler").published.spi)
        assertNull(walkOf(Premades.byName("Barmaid Brawler"), saved))
    }

    @Test
    fun `the tabs hold the walked and the rest, and nobody twice`() {
        assertEquals(listOf("Journalist"), rosterOf(Roster.BEYONDER, people, saved).map { it.name })
        assertEquals(people.size - 1, rosterOf(Roster.MORTAL, people, saved).size)
        assertEquals(people, rosterOf(Roster.ALL, people, saved))
    }

    @Test
    fun `with nothing saved every character is a mortal`() {
        assertEquals(emptyList<String>(), rosterOf(Roster.BEYONDER, people, emptyMap()).map { it.name })
    }
}
