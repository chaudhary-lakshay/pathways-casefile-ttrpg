package com.pathways.rules

import com.pathways.model.Difficulty
import com.pathways.rules.content.ContentLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** Appendix A's Common Modifiers (p. 159), and the one row that is not one. */
class CommonModifiersTest {

    private val untrained = "Using untrained skill (default)"
    private val routine = "Routine conditions"
    private val adverse = "Adverse conditions (dark, noisy, dangerous)"
    private val tools = "Good tools / equipment"
    private val poorTools = "Improvised / poor tools"
    private val easy = "Easy task (well-lit, calm, plenty of time)"
    private val veryAdverse = "Very adverse conditions (combat, extreme weather)"

    // ---- the row that is not a modifier ---------------------------------

    /**
     * **The warrant and the refusal, in one row.** "Attribute -4 to -6" is
     * `Difficulty.defaultPenalty`'s two ends — this table restating chapter
     * 3's untrained defaults, which `SkillLadder.defaultFor` has implemented
     * since M0. That exact agreement is the only row here the engine can be
     * checked against, and it is what licenses adopting the rest of an
     * appendix 6v records as unreliable where it summarises.
     */
    @Test
    fun `the untrained row restates the defaults the engine already implements`() {
        val printed = CommonModifiers.named(untrained).printed
        val ends = Difficulty.entries.mapNotNull { it.defaultPenalty }
        assertEquals(-4, ends.max())
        assertEquals(-6, ends.min())
        assertTrue(printed.contains("-4") && printed.contains("-6")) { printed }
        assertEquals(6, SkillLadder.defaultFor(Difficulty.EASY, 10))
        assertEquals(4, SkillLadder.defaultFor(Difficulty.HARD, 10))
    }

    /**
     * Refused, not answered with zero. A caller handed 0 would believe the
     * rule had been priced; the book's rule is that the ROLL changes, not the
     * number added to it. Applied as -4 it would also subtract from the skill
     * where the book subtracts from the attribute, and stack on a default
     * already taken.
     */
    @Test
    fun `the untrained row is refused rather than priced`() {
        val row = CommonModifiers.named(untrained)
        assertTrue(row.isDefault)
        assertNull(row.modifier, "printing no value is not printing zero")
        assertThrows<IllegalArgumentException> { CommonModifiers.valueFor(row) }
        assertThrows<IllegalArgumentException> { CommonModifiers.modifiers(listOf(row)) }
    }

    @Test
    fun `it is not offered, so no screen can reach it`() {
        assertEquals(9, CommonModifiers.all().size)
        assertEquals(8, CommonModifiers.offered().size)
        assertTrue(CommonModifiers.offered().none { it.situation == untrained })
    }

    // ---- the printed shapes ---------------------------------------------

    @Test
    fun `an exact row takes its printed value and refuses any other`() {
        val row = CommonModifiers.named(adverse)
        assertEquals(-4, CommonModifiers.valueFor(row))
        assertEquals(-4, CommonModifiers.valueFor(row, -4))
        assertThrows<IllegalArgumentException> { CommonModifiers.valueFor(row, -5) }
    }

    /** The gentle end is what a range offers; the other end is the bound. */
    @Test
    fun `a range offers the end nearer zero and validates against both`() {
        val good = CommonModifiers.named(tools)
        assertEquals(1, CommonModifiers.valueFor(good))
        assertEquals(2, CommonModifiers.valueFor(good, 2))
        assertThrows<IllegalArgumentException> { CommonModifiers.valueFor(good, 3) }

        val poor = CommonModifiers.named(poorTools)
        assertEquals(-2, CommonModifiers.valueFor(poor))
        assertEquals(-4, CommonModifiers.valueFor(poor, -4))
        assertThrows<IllegalArgumentException> { CommonModifiers.valueFor(poor, -5) }
    }

    /**
     * "+4 or more" and "-6 or worse" have one printed bound each, so a GM may
     * run further from zero and never back toward it — a +2 easy task would
     * be gentler than the book's own floor.
     */
    @Test
    fun `an open-ended row bounds only the side the book prints`() {
        val row = CommonModifiers.named(easy)
        assertEquals(4, CommonModifiers.valueFor(row))
        assertEquals(9, CommonModifiers.valueFor(row, 9))
        assertThrows<IllegalArgumentException> { CommonModifiers.valueFor(row, 2) }

        val worse = CommonModifiers.named(veryAdverse)
        assertEquals(-6, CommonModifiers.valueFor(worse))
        assertEquals(-11, CommonModifiers.valueFor(worse, -11))
        assertThrows<IllegalArgumentException> { CommonModifiers.valueFor(worse, -5) }
    }

    /**
     * "Routine conditions / +0" is a real printed row that adds nothing. It is
     * a choice, not a gap — the distinction the untrained row sits on the
     * other side of.
     */
    @Test
    fun `routine conditions print zero and reach no ledger`() {
        val row = CommonModifiers.named(routine)
        assertEquals(0, CommonModifiers.valueFor(row))
        assertTrue(CommonModifiers.modifiers(listOf(row)).isEmpty())
    }

    // ---- composing them --------------------------------------------------

    @Test
    fun `chosen rows reach a skill roll and move the target`() {
        val chosen = listOf(CommonModifiers.named(adverse), CommonModifiers.named(poorTools))
        val mods = CommonModifiers.modifiers(chosen)
        assertEquals(2, mods.size)

        val sheet = Premades.byName("Occult Scholar")
        val skill = sheet.skills.first()
        val out = SkillRoll.of(sheet, skill, RandomSource { 4 }, mods = mods)

        assertEquals(sheet.levelFor(skill), out.baseTarget)
        assertEquals(out.baseTarget - 6, out.target, "-4 adverse and -2 improvised tools")
        assertNotNull(out.roll)
    }

    /**
     * A value naming a row nobody chose is refused rather than ignored — the
     * stale-state shape the boon dialog had to be fixed for, where a leftover
     * pick silently moved a different row.
     */
    @Test
    fun `a value for an unchosen row is refused`() {
        assertThrows<IllegalArgumentException> {
            CommonModifiers.modifiers(
                chosen = listOf(CommonModifiers.named(adverse)),
                gmValue = mapOf(tools to 2),
            )
        }
    }

    @Test
    fun `a situation the book never prints is refused`() {
        assertThrows<IllegalStateException> { CommonModifiers.named("Raining a bit") }
    }

    // ---- scope ------------------------------------------------------------

    /**
     * This general table and chapter 5's ranged one price darkness twice, so
     * they are never offered together. This pins that the collision is real —
     * the day chapter 5 stops printing darkness is the day the scoping stops
     * being needed, and someone should notice that rather than inherit it.
     */
    @Test
    fun `every value inside a printed range is accepted`() {
        // The screen offered a range's two ENDS only, so "Improvised / poor
        // tools -2 to -4" could not produce -3 -- a value the row prints as
        // surely as its ends, and one this object has always accepted. The
        // chips are the whole range now, and this is the contract they lean
        // on: offering a chip that throws on Roll is not offering a choice
        // (the lesson the two Aim rows taught in 6t).
        val ranged = CommonModifiers.offered().filter { it.otherEnd != null }
        assertTrue(ranged.isNotEmpty(), "the table still prints ranges")
        ranged.forEach { row ->
            val printed = row.modifier!!
            val other = row.otherEnd!!
            (minOf(printed, other)..maxOf(printed, other)).forEach { value ->
                assertEquals(value, CommonModifiers.valueFor(row, value))
            }
        }
    }

    @Test
    fun `an open end is bounded on the side the book prints and open on the other`() {
        // "-6 or worse" names no ceiling, so the UI offers a few steps past
        // it; what it must NOT do is offer something gentler than the printed
        // end, which would be kinder than the book's own floor.
        val open = CommonModifiers.offered().filter { it.isOpenEnded }
        assertTrue(open.isNotEmpty(), "the table still prints an open end")
        open.forEach { row ->
            val printed = row.modifier!!
            val away = if (printed < 0) printed - 3 else printed + 3
            assertEquals(away, CommonModifiers.valueFor(row, away))
            val toward = if (printed < 0) printed + 1 else printed - 1
            assertThrows<IllegalArgumentException> { CommonModifiers.valueFor(row, toward) }
        }
    }

    @Test
    fun `chapter 5 prices the same darkness this table does`() {
        assertTrue(CommonModifiers.all().any { it.situation == adverse })
        assertTrue(
            ContentLoader.rangedModifiers().any { it.condition.contains("Darkness") },
        ) { "the reason this table is kept off the ranged dialog" }
    }
}
