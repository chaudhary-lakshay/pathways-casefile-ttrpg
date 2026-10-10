package com.pathways.rules

import com.pathways.model.Outcome
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Chapter 7.6's Spirit Vision (p. 77-78).
 *
 * The chapter had a content file and no reader: `spirit_vision.json` was the
 * one file of 79 that `ContentLoader` never named, and also the sole claimant
 * of 7.6, so `coverage.py` reported that chapter complete off a file nothing
 * loaded.
 */
class SpiritVisionTest {

    /** A die that always reads [pips], so a roll is a fact and not a chance. */
    private fun fixed(pips: Int) = RandomSource { pips }

    // ---------------------------------------------------------------
    // Who has it. The gate reads each pathway's OWN block, never 7.6's
    // section C, which lists Visionary (whose block grants none) and omits
    // Moon (whose block grants it).
    // ---------------------------------------------------------------

    @Test
    fun `the pathways granting it at Sequence 9 are the blocks and not the summary`() {
        for (pathway in listOf("Fool", "Darkness", "Death", "Hermit", "Moon")) {
            assertTrue(SpiritVision.grantedTo(pathway, 9), pathway)
        }
        // 7.6 section C prints Visionary. Its Spectator block prices Body
        // Language Analysis in FP and names no Spirit Vision at all.
        assertFalse(SpiritVision.grantedTo("Visionary", 9))
    }

    @Test
    fun `two pathways grant it one rung down, and not before`() {
        // Mother and Red Priest are absent from a SEQUENCE 9 table correctly:
        // the Doctor and the Provoker are Sequence 8.
        assertFalse(SpiritVision.grantedTo("Mother", 9))
        assertTrue(SpiritVision.grantedTo("Mother", 8))
        assertFalse(SpiritVision.grantedTo("Red Priest", 9))
        assertTrue(SpiritVision.grantedTo("Red Priest", 8))
    }

    @Test
    fun `sequences are cumulative, so a lower rung keeps what a higher one granted`() {
        // A Sequence 7 Fool drank the Seer's potion first. Sequence numbers
        // count DOWN, so "granted at 9" reaches every rung below it.
        assertTrue(SpiritVision.grantedTo("Fool", 8))
        assertTrue(SpiritVision.grantedTo("Fool", 0))
        // And a pathway that grants it nowhere never does.
        assertFalse(SpiritVision.grantedTo("Visionary", 0))
    }

    @Test
    fun `mentioning the ability is not being granted it`() {
        // The Werewolf (Chained 7) is CONCEALED from Spirit Vision, the
        // Hypnotist (Visionary 6) fools it, and the Monster (Wheel of
        // Fortune 9) sees what pathways with it cannot. A substring match
        // over the blocks hands all three an ability they do not have.
        assertFalse(SpiritVision.grantedTo("Chained", 7))
        assertFalse(SpiritVision.grantedTo("Wheel of Fortune", 9))
    }

    @Test
    fun `the Hermit's grant is under the book's own name for it`() {
        // The Mystery Pryer's block never writes "Spirit Vision" -- it writes
        // "Eyes of Mystery Prying", which 7.6 confirms is the same ability.
        assertTrue(SpiritVision.grantedTo("Hermit", 9))
        assertEquals("Eyes of Mystery Prying", SpiritVision.formFor("Hermit"))
        assertEquals("Spirit Vision", SpiritVision.formFor("Fool"))
        assertNull(SpiritVision.formFor("Visionary"))
    }

    @Test
    fun `an unprinted pathway is a question, not a false answer`() {
        assertFailsWith<IllegalArgumentException> { SpiritVision.grantedTo("Nonesuch", 9) }
    }

    // ---------------------------------------------------------------
    // The roll.
    // ---------------------------------------------------------------

    @Test
    fun `the cost is the book's, read from the table rather than typed`() {
        assertEquals(1, SpiritVision.activation())
        assertEquals(1, SpiritVision.maintenance())
    }

    @Test
    fun `the roll is Perception-based, with nothing added of its own`() {
        // "Make a Perception-based roll to interpret correctly." The chapter
        // prints no modifier of any kind, so the bare Per IS the target.
        val apothecary = Premades.byName("Apothecary").toCharacter()
        val per = Derive.of(apothecary).secondaries.per
        assertEquals(per, SpiritVision.targetFor(per))
    }

    @Test
    fun `conditions reach the roll like every other bare attribute roll`() {
        // Reeling and fatigue are "-1 to all rolls" and this is a roll; shock
        // is the same shape. They arrive through the same two parameters
        // Concentration and Detection already take.
        assertEquals(9, SpiritVision.targetFor(10, hurt = -1))
        assertEquals(6, SpiritVision.targetFor(10, shock = -4))
        assertEquals(5, SpiritVision.targetFor(10, shock = -4, hurt = -1))
    }

    @Test
    fun `attempt returns the same breakdown partsFor captions`() {
        // The dialog prints what moved the target above the button that rolls
        // it. Two sites building that list is the drift this repo has
        // produced three times, so `partsFor` answers once and `attempt`
        // returns the same list it captioned.
        assertEquals(listOf("Per 10"), SpiritVision.partsFor(10))
        assertEquals(
            listOf("Per 10", "shock -4", "hurt or tired -1"),
            SpiritVision.partsFor(10, shock = -4, hurt = -1),
        )
        val rolled = SpiritVision.attempt(10, fixed(3), shock = -4, hurt = -1)
        assertEquals(SpiritVision.partsFor(10, shock = -4, hurt = -1), rolled.parts)
        assertEquals(SpiritVision.targetFor(10, shock = -4, hurt = -1), rolled.roll.target)
    }

    @Test
    fun `the margin bands read the table's own rows`() {
        assertEquals("Identify primary emotion or general health", SpiritVision.rowFor(0).effect)
        assertEquals("Detect specific feelings", SpiritVision.rowFor(3).effect)
        assertEquals("Sense recent events", SpiritVision.rowFor(5).effect)
        // A margin between bands takes the band below it.
        assertEquals("Detect specific feelings", SpiritVision.rowFor(4).effect)
    }

    @Test
    fun `a failure is a reading, not an absence`() {
        // This table prints a Failure row where chapter 4's siblings print
        // none: "Incorrect reading". The character gets a confident WRONG
        // answer, so the row must exist rather than resolving to null.
        val row = SpiritVision.rowForOutcome(Outcome.FAILURE)
        assertEquals("Incorrect reading", row.effect)
        assertTrue(SpiritVision.isMisleading(row))
        assertFalse(SpiritVision.isMisleading(SpiritVision.rowFor(0)))
    }

    @Test
    fun `a critical is the table's own row and is spelled as the book spells it`() {
        val row = SpiritVision.rowForOutcome(Outcome.CRITICAL_SUCCESS)
        assertEquals("Full reading", row.effect)
        // Every other table in the book writes "Critical Success"; this one
        // writes "Critical". The book's spelling is the book's.
        assertEquals("Critical", row.result)
    }

    // ---------------------------------------------------------------
    // The reference tables, which are the rest of the file.
    // ---------------------------------------------------------------

    @Test
    fun `both colour tables load and are kept apart`() {
        assertEquals(8, SpiritVision.astralColors().size)
        assertEquals(8, SpiritVision.etherBody().size)
        // Purple is madness in one table and the brain in the other. Nothing
        // may join them on the colour.
        assertEquals(
            "Spirituality, madness",
            SpiritVision.astralColors().first { it.color == "Purple" }.meaning,
        )
        assertEquals(
            "Brain",
            SpiritVision.etherBody().first { it.color == "Purple" }.region,
        )
    }

    @Test
    fun `section C and the grants are the same size and disagree both ways`() {
        // The reason this table must never be a gate, stated as the set
        // difference rather than as a sentence. It is loaded, because the
        // disagreement it carries is worth showing on screen; it decides
        // nothing, because it is wrong in both directions at once.
        //
        // Named for what it asserts. It does NOT prove the UI avoids
        // pathwayDifferences() -- no test in this repo can reach a
        // composable -- and the guard for that is the mutation: pointing
        // grantedTo at this table reddens four tests, including this one.
        val listed = SpiritVision.pathwayDifferences()
            .map { it.name.substringAfter("(").substringBefore(")") }.toSet()
        val grantedAtNine = SpiritVision.grants()
            .filter { it.sequence == 9 }.map { it.pathway }.toSet()

        assertEquals(listed.size, grantedAtNine.size)
        assertEquals(setOf("Visionary"), listed - grantedAtNine)
        assertEquals(setOf("Moon"), grantedAtNine - listed)
    }

    /**
     * A screen that prints the target and its breakdown and then rolls must
     * roll THAT target and carry THAT breakdown. Composing either a second
     * time inside the roll is two call sites answering one question (6eh).
     */
    @Test
    fun `a roll against a composed target is the attempt that composed it`() {
        val target = SpiritVision.targetFor(10, shock = -4, hurt = -1)
        val parts = SpiritVision.partsFor(10, shock = -4, hurt = -1)
        val rolled = SpiritVision.rollAgainst(target, fixed(3), parts)
        assertEquals(5, rolled.roll.target, "judged against the target it was handed")
        assertEquals(parts, rolled.parts)
        assertEquals(SpiritVision.attempt(10, fixed(3), shock = -4, hurt = -1), rolled)
        // The parts are carried, not rebuilt: what the screen printed is what
        // the result holds, even where no input could have produced it.
        assertEquals(listOf("as printed"), SpiritVision.rollAgainst(target, fixed(3), listOf("as printed")).parts)

        // A success is read by its MARGIN, a critical or a failure by its
        // outcome. 2+2+2 against 11 succeeds by 5, and that is not the row a
        // bare success names -- which is what makes the difference visible.
        assertNotEquals(SpiritVision.rowForOutcome(Outcome.SUCCESS), SpiritVision.rowFor(5))
        assertEquals(SpiritVision.rowFor(5), SpiritVision.rollAgainst(11, fixed(2), parts).row)
    }

    /**
     * Alertness, "+1 per level to all Per rolls", and Spirit Vision's
     * "Perception-based roll to interpret" (book 4840; 6fe). Detection took
     * it on the ground that it was the one bare Per roll; this one came later.
     */
    @Test
    fun `Alertness reaches the Perception roll that reads Spirit Vision`() {
        assertEquals(12, SpiritVision.targetFor(10, alertness = 2))
        assertEquals(listOf("Per 10", "+2 Alertness"), SpiritVision.partsFor(10, alertness = 2))
        assertEquals(listOf("Per 10"), SpiritVision.partsFor(10))
        // At 0 the equality below could not see a dropped argument.
        val composed = SpiritVision.rollAgainst(
            SpiritVision.targetFor(10, shock = -4, alertness = 2),
            fixed(3),
            SpiritVision.partsFor(10, shock = -4, alertness = 2),
        )
        assertEquals(composed, SpiritVision.attempt(10, fixed(3), shock = -4, alertness = 2))
        assertFailsWith<IllegalArgumentException> { SpiritVision.targetFor(10, alertness = -1) }
    }

    /**
     * Acute Vision's "+2 to Vision rolls", declared by the GM (6fh): the
     * roll is "Perception-based" (4840) and a sight by its own name, so the
     * Vision clause is offered and the Hearing one never is.
     */
    @Test
    fun `a declared Acute Vision reaches the Spirit Vision roll`() {
        val held = listOf(
            com.pathways.model.Trait("Acute Vision", 2, com.pathways.model.TraitKind.ADVANTAGE),
            com.pathways.model.Trait("Acute Hearing", 2, com.pathways.model.TraitKind.ADVANTAGE),
        )
        val vision = TraitClauses.visionClause(held)!!
        assertEquals("Acute Vision", vision.trait)
        assertNull(TraitClauses.visionClause(held.drop(1)))
        assertEquals(14, SpiritVision.targetFor(10, alertness = 2, vision = vision))
        assertEquals(
            listOf("Per 10", "+2 Alertness", "+2 Acute Vision"),
            SpiritVision.partsFor(10, alertness = 2, vision = vision),
        )
        val composed = SpiritVision.rollAgainst(
            SpiritVision.targetFor(10, vision = vision),
            fixed(3),
            SpiritVision.partsFor(10, vision = vision),
        )
        assertEquals(composed, SpiritVision.attempt(10, fixed(3), vision = vision))
    }
}
