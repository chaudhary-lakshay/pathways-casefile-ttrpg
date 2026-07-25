package com.pathways.model

import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test

/**
 * KNOWN_ISSUES: "Derive.skillLevels is untested and collapses duplicates".
 * A character buys a given skill once; a second purchase of the same
 * [SkillKey] should have been combined into one entry before it ever
 * reaches [Character] - two separate entries billed separately by
 * [com.pathways.rules.PointBudget] while collapsing to one entry in
 * [com.pathways.rules.Derive.of]'s `skillLevels` map is exactly the defect
 * this guards against. Rejecting at construction (rather than silently
 * summing) matches the rulebook's purchase model: points for the same
 * skill are always tracked as a single running total, never as several
 * independent purchases of the same line.
 */
class CharacterTest {

    private fun skill(points: Int) =
        PurchasedSkill(SkillKey("Occultism", null, ControllingAttribute.Buyable(Attr.IQ), Difficulty.AVERAGE), points)

    @Test
    fun `two purchases of the same skill are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            Character(name = "dup", skills = listOf(skill(8), skill(4)))
        }
    }

    @Test
    fun `distinct skills - even with the same name under a different specialization - are fine`() {
        val history = SkillKey("History", "Occult", ControllingAttribute.Buyable(Attr.IQ), Difficulty.HARD)
        val historyModern = SkillKey("History", "Modern", ControllingAttribute.Buyable(Attr.IQ), Difficulty.HARD)
        Character(name = "ok", skills = listOf(PurchasedSkill(history, 8), PurchasedSkill(historyModern, 4)))
    }
}
