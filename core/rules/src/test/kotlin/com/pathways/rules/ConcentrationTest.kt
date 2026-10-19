package com.pathways.rules

import com.pathways.model.Outcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** Ch. 5's Concentrate maneuver: "Roll Will-3 if injured or defending." */
class ConcentrationTest {

    /** [RandomSource] yields one FACE per call; 3d6 asks three times. */
    private fun scripted(vararg faces: Int): RandomSource {
        var next = 0
        return RandomSource { faces[next++.coerceAtMost(faces.size - 1)] }
    }

    /** A 3d6 total, split into three legal faces. */
    private fun rolling(total: Int): RandomSource {
        require(total in 3..18)
        val low = (total - 3) / 3
        return scripted(1 + low, 1 + low, total - 2 - 2 * low)
    }

    @Test
    fun `the target is the digested Will less three`() {
        assertEquals(7, Concentration.targetFor(10))
        assertEquals(-3, Concentration.PENALTY)
        // A Beyonder whose potions raised Will concentrates better, and one
        // the Abyss lowered concentrates worse: the caller passes the derived
        // value, exactly as Corruption.maxFor takes the digested Will.
        assertEquals(11, Concentration.targetFor(14))
    }

    /**
     * **The roll is CONDITIONAL, and that is the whole reading.**
     *
     * The sentence asks for it only when the character is injured or
     * defending. Rolling it unconditionally would tax every act of focus in
     * the book -- a ritual's channelling, the gauntlet's aura, the Astral
     * projection -- with a penalty that names a circumstance none of them is
     * in. Refused rather than answered, the call `Detection.attributeFor`
     * (6ah) needed.
     */
    @Test
    fun `an undisturbed concentration is refused rather than rolled`() {
        val ex = assertThrows<IllegalArgumentException> {
            Concentration.attempt(will = 10, disturbed = false, source = rolling(10))
        }
        assertTrue(ex.message!!.contains("if injured or defending"), ex.message)
    }

    @Test
    fun `a disturbed concentration rolls against Will less three`() {
        val outcome = Concentration.attempt(will = 10, disturbed = true, source = rolling(7))
        assertEquals(7, outcome.target)
        assertTrue(outcome.held)
        assertEquals(Outcome.SUCCESS, outcome.roll.outcome)

        val lost = Concentration.attempt(will = 10, disturbed = true, source = rolling(8))
        assertTrue(!lost.held)
    }

    /**
     * No printed override, so [SuccessRoll]'s critical rules stand.
     *
     * A rolled 3 holds against any target and an 18 fails one. Reading
     * `== SUCCESS` alone would report the book's best roll as a lost focus --
     * the slip [FrightCheck], [RitualResolution] and [Wounds] each made once.
     */
    @Test
    fun `a critical success holds and a critical failure does not`() {
        // Target 1: hopeless on any ordinary roll.
        val crit = Concentration.attempt(will = 4, disturbed = true, source = rolling(3))
        assertEquals(Outcome.CRITICAL_SUCCESS, crit.roll.outcome)
        assertTrue(crit.held)

        // Target 15: an 18 fails it anyway.
        val fumble = Concentration.attempt(will = 18, disturbed = true, source = rolling(18))
        assertEquals(Outcome.CRITICAL_FAILURE, fumble.roll.outcome)
        assertTrue(!fumble.held)
    }

    /**
     * The cell is the book's own, and it still prints both halves.
     *
     * The number lives in Kotlin and the sentence is pinned in the extractor;
     * this is the Kotlin half of that pin, and it reads `ContentLoader`, which
     * is the copy production quotes on screen.
     */
    @Test
    fun `the printed cell carries the penalty and the condition`() {
        val printed = Concentration.printed()
        assertEquals(
            "Channel powers or skills requiring focus. Roll Will-3 if injured or defending.",
            printed,
        )
        assertTrue(printed.contains("Will${Concentration.PENALTY}"), printed)
        assertTrue(printed.contains("if injured or defending"), printed)
    }

    /**
     * No consequence is invented for a failure.
     *
     * The book prints the roll and stops -- no row says what a lost focus
     * costs -- so the outcome carries whether it held and nothing else. The
     * call ch. 4's two sense tables needed when they printed no failure row
     * (6aj), and the reason `RitualCritical.corGained` distinguishes charging
     * nothing from charging zero.
     */
    @Test
    fun `the book prints no consequence, so none is carried`() {
        val lost = Concentration.attempt(will = 10, disturbed = true, source = rolling(15))
        assertTrue(!lost.held)
        // Nothing on the outcome describes an effect: if a consequence row is
        // ever printed, this is where the decision has to be made again.
        val fields = ConcentrationOutcome::class.java.declaredFields.map { it.name }.toSet()
        assertEquals(setOf("target", "roll"), fields)
    }

    /**
     * A screen that prints "3d6 <= N" and then rolls must roll against N.
     * Composing N a second time inside the roll is two call sites answering
     * one question (6eg, 6eh), so the roll takes the composed target and
     * the older entry point is that roll with the target composed first.
     */
    @Test
    fun `a roll against a composed target is the attempt that composed it`() {
        val target = Concentration.targetFor(will = 12, shock = -2, hurt = -1, fromTraits = 1)
        val rolled = Concentration.rollAgainst(target, disturbed = true, source = rolling(7))
        assertEquals(7, rolled.target)
        assertEquals(7, rolled.roll.target, "judged against the target it was handed")
        assertEquals(
            Concentration.attempt(12, disturbed = true, source = rolling(7), shock = -2, hurt = -1, fromTraits = 1),
            rolled,
        )
        // The book calls for no roll while nothing disturbs the focus, and
        // that is refused on the path the screen takes, not only on attempt.
        // Refused BEFORE the dice: a guard placed after the roll throws the
        // same exception and has already drawn from the source.
        var draws = 0
        assertThrows<IllegalArgumentException> {
            Concentration.rollAgainst(target, disturbed = false, source = RandomSource { draws++; 3 })
        }
        assertEquals(0, draws)
    }
}
