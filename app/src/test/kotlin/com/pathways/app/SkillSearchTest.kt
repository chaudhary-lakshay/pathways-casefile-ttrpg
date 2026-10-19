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
    fun `an empty or blank query matches everything`() {
        assertEquals(skills.size, found("").size)
        assertEquals(skills.size, found("  ").size)
    }
}
