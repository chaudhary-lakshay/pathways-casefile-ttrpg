package com.pathways.rules

import com.pathways.model.Outcome
import com.pathways.rules.content.ContentLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Ch. 2's "A critical success on an attack roll deals double damage", which
 * `core_mechanic.json` loaded and nothing applied (play session 5).
 */
class CriticalHitTest {

    private val thrown = DamageResult(listOf(4, 2), 6, "cut")

    @Test
    fun `a critical success doubles the rolled damage`() {
        val hit = Damage.onAttack(thrown, Outcome.CRITICAL_SUCCESS)
        assertEquals(12, hit.total)
        assertTrue(hit.doubled)
        assertEquals(thrown.faces, hit.faces)
    }

    @Test
    fun `a plain success rolls damage as thrown`() {
        val hit = Damage.onAttack(thrown, Outcome.SUCCESS)
        assertEquals(thrown, hit)
        assertFalse(hit.doubled)
    }

    @Test
    fun `the rule doubled is the one the book prints`() {
        val row = ContentLoader.coreMechanic().single { it.outcome == "Critical Success in Combat" }
        assertTrue(row.meaning.contains("double damage"), row.meaning)
    }
}
