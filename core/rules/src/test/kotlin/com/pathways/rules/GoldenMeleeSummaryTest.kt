package com.pathways.rules

import com.pathways.model.Difficulty
import com.pathways.rules.content.ContentLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Ch. 5's "Summary: Melee Skill Comparison" (p. 39, global 2622-2643) marking
 * the engine's homework.
 *
 * Four columns -- Skill, Damage Bonus, Parry Formula, Special Notes -- and
 * every one of them restates something [ParryBlock], [Damage] or `skills.json`
 * has implemented since M0. Nothing had ever compared them. A summary is
 * checkABLE, never authoritative (6v), so a disagreement here is a finding
 * rather than a repair: the chapter's own sections win.
 *
 * It is deliberately NOT extracted. Every number is a second printing of one
 * this repo already commits, and a content file read by nothing is the defect
 * 6ai, 6al and 6am each found in turn.
 */
class GoldenMeleeSummaryTest {

    private fun skill(name: String) =
        ContentLoader.skills().single { it.name == name && it.specialization == null }

    // ---- the Parry Formula column ----------------------------------------

    @Test
    fun `the three printed parry formulas agree with the engine`() {
        // "Brawling/2 + 3", "Boxing/2 + 3", "(Wrestling-3)/2 + 3".
        val level = 13
        assertEquals(9, ParryBlock.unarmedParry(UnarmedStyle.BRAWLING, level))
        assertEquals(9, ParryBlock.unarmedParry(UnarmedStyle.BOXING, level))
        assertEquals(8, ParryBlock.unarmedParry(UnarmedStyle.WRESTLING, level))
    }

    /**
     * Wrestling's -3 sits INSIDE the halving, and the printed formula is the
     * only thing that says so. `Wrestling/2 + 3 - 3` and `(Wrestling-3)/2 + 3`
     * differ at every odd level, which is exactly the kind of gap that looks
     * like a rounding preference and is not.
     */
    @Test
    fun `Wrestling's penalty is inside the halving, not after it`() {
        listOf(11, 13, 15).forEach { level ->
            val printed = Math.floorDiv(level - 3, 2) + 3
            val outside = Math.floorDiv(level, 2) + 3 - 3
            assertNotEquals(outside, printed, "level $level would not tell the two apart")
            assertEquals(printed, ParryBlock.unarmedParry(UnarmedStyle.WRESTLING, level))
        }
    }

    // ---- the Damage Bonus column -----------------------------------------

    @Test
    fun `only Boxing prints a damage bonus, and only Boxing has one`() {
        // "None (thr only)" / "+1 at DX+1, +2 at DX+2" / "None (grapple only)".
        val dx = 10
        assertEquals(0, Damage.boxingBonus(dx, boxingLevel = dx))
        assertEquals(1, Damage.boxingBonus(dx, boxingLevel = dx + 1))
        assertEquals(2, Damage.boxingBonus(dx, boxingLevel = dx + 2))
        // "(max +2 without Trained by a Master)" -- the cap is the book's.
        assertEquals(2, Damage.boxingBonus(dx, boxingLevel = dx + 5))
        // And it never goes negative for a boxer below their own DX.
        assertEquals(0, Damage.boxingBonus(dx, boxingLevel = dx - 3))
    }

    @Test
    fun `Wrestling's maneuvers carry the summary's damage cell and roll none`() {
        // "None (grapple only)" is this row's DAMAGE cell, and 6j read it as
        // "no attacks at all". Its own table two lines above prints Maneuver /
        // Roll / Opposed Roll / Notes, so the maneuvers are real and the cell
        // travels with them as the damage they do not roll.
        val grappling = Damage.attacksFor("Wrestling")
        assertEquals(3, grappling.size)
        // ONE cell, shared: the string itself is tied to book line 2640 by
        // extract_unarmed.SUMMARY_DAMAGE, so naming it here would be a guard
        // comparing the extractor against a literal typed in this file.
        assertEquals(1, grappling.map { it.damage }.toSet().size)
        grappling.forEach {
            assertFalse(it.wounds, "${it.attack} rolls damage")
            assertTrue(it.opposedRoll!!.startsWith("DX or "))
        }
        assertTrue(Damage.attacksFor("Brawling").isNotEmpty())
        assertTrue(Damage.attacksFor("Boxing").isNotEmpty())
    }

    // ---- the Special Notes column, and the trap in it ---------------------

    /**
     * **The summary prints a DEFAULT per skill, and two of the three disagree
     * with the difficulty-derived one.**
     *
     * | Skill | Difficulty | [SkillLadder.defaultFor] | The summary prints |
     * |---|---|---|---|
     * | Brawling | DX/E | DX-4 | **DX-2** |
     * | Boxing | DX/A | DX-5 | DX-5 |
     * | Wrestling | DX/A | DX-5 | **no default** |
     *
     * Specific beats general -- the call the PCR's critical rules and the
     * ritual CoR ladder already needed -- so a Brawling default is DX-2 and
     * Wrestling has none, whatever the difficulty ladder says.
     *
     * **[SkillLadder.defaultFor] still answers DX-4 and DX-5, and that is
     * right for a function of a [Difficulty].** It had no production caller
     * until 6et; the caller is [SkillDefaults], which holds the two overrides
     * and is what a screen asks. This test keeps the ladder's half: if the
     * ladder and the chapter ever agree, the overrides are stale.
     *
     * Deliberately not a `default` field in `skills.json`: three rows of 158
     * print one, in prose, and a column would make the book look like it
     * printed one for all of them.
     */
    @Test
    fun `the printed per-skill defaults are not the difficulty defaults`() {
        val dx = 10
        assertEquals(Difficulty.EASY, skill("Brawling").difficulty)
        assertEquals(Difficulty.AVERAGE, skill("Boxing").difficulty)
        assertEquals(Difficulty.AVERAGE, skill("Wrestling").difficulty)

        // Boxing is the one that agrees, and that agreement is what shows the
        // other two are real overrides rather than a misreading of the column.
        assertEquals(dx - 5, SkillLadder.defaultFor(Difficulty.AVERAGE, dx))

        // Brawling: the ladder says DX-4, the chapter says DX-2.
        assertEquals(dx - 4, SkillLadder.defaultFor(Difficulty.EASY, dx))
        assertNotEquals(
            dx - 2,
            SkillLadder.defaultFor(Difficulty.EASY, dx),
            "if these ever agree, chapter 5 stopped overriding and this test is stale",
        )

        // Wrestling: "No default" is a refusal, and the ladder offers DX-5.
        assertNotNull(
            SkillLadder.defaultFor(Difficulty.AVERAGE, dx),
            "the ladder still offers a default the chapter withholds",
        )
    }

    @Test
    fun `Very Hard refuses a default for a different reason than Wrestling does`() {
        // One is about a skill nobody can attempt untaught; the other is one
        // chapter 5 singles out. Recorded side by side, not merged -- the pair
        // 6an already had to keep apart for study.
        assertNull(SkillLadder.defaultFor(Difficulty.VERY_HARD, 10))
    }
}
