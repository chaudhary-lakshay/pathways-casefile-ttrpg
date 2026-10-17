package com.pathways.app

import com.pathways.rules.content.ContentLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The Add a skill picker's search (play session 2: 158 rows, Guns about
 * sixty down, and nothing to type into).
 */
class SkillSearchTest {

    private val skills = ContentLoader.skills()

    private fun found(query: String) =
        skills.filter { skillMatches(it, query) }.map { it.specialization?.let { s -> "${it.name} ($s)" } ?: it.name }

    @Test
    fun `a query finds a skill by any part of its name, ignoring case`() {
        assertEquals(listOf("Guns (Pistol)", "Guns (Revolver)", "Guns (Rifle)", "Guns (Shotgun)"), found("guns ("))
    }

    @Test
    fun `a query finds a skill by its specialization`() {
        assertEquals(true, "Guns (Revolver)" in found("revolver"))
    }

    @Test
    fun `a query naming a withheld SPI skill finds it, so the picker can say why it is not offered`() {
        // Play session 6: "Divination" returned an empty list and no reason.
        assertEquals(listOf("Divination Arts"), withheldMatching("divination").map { it.name })
        assertEquals(emptyList<String>(), withheldMatching("guns").map { it.name })
    }

    @Test
    fun `a blank query names no withheld skill`() {
        assertEquals(emptyList<String>(), withheldMatching("  ").map { it.name })
    }

    @Test
    fun `an empty or blank query matches everything`() {
        assertEquals(skills.size, found("").size)
        assertEquals(skills.size, found("  ").size)
    }
}
