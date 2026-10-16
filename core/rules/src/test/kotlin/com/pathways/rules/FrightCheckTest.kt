package com.pathways.rules

import com.pathways.rules.content.ContentLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class FrightCheckTest {

    /** Every die shows [face], so the two rolls are decisions, not chance. */
    private fun fixed(face: Int) = RandomSource { face }

    /** Hands out [faces] in order, then repeats the last one. */
    private fun scripted(vararg faces: Int): RandomSource {
        var next = 0
        return RandomSource { faces[next++.coerceAtMost(faces.size - 1)] }
    }

    @Test
    fun `a passed check has no effect at all`() {
        // 3d6 of 1s is 3, under any Will worth having.
        val outcome = FrightCheck.against(will = 12, modifier = 0, source = fixed(1))
        assertTrue(outcome.passed)
        assertNull(outcome.effect)
        assertNull(outcome.effectRoll)
    }

    @Test
    fun `the trigger modifier moves the target, not the roll`() {
        // p. 43: "roll 3d6 vs. Will with the modifier listed below".
        assertEquals(14, FrightCheck.against(will = 12, modifier = 2, source = fixed(1)).target)
        assertEquals(4, FrightCheck.against(will = 12, modifier = -8, source = fixed(1)).target)
    }

    @Test
    fun `a failed check rolls again and adds the margin of failure`() {
        // p. 43: "roll 3d6 + margin of failure and consult the table".
        // Check: 6+6+6 = 18 against Will 10 -> margin 8. Effect: 1+1+1 = 3, +8 = 11.
        val outcome = FrightCheck.against(will = 10, modifier = 0, source = scripted(6, 6, 6, 1, 1, 1))
        assertFalse(outcome.passed)
        assertEquals(8, outcome.margin)
        assertEquals(11, outcome.effectRoll)
        assertEquals("10–11", outcome.effect?.printed)
    }

    @Test
    fun `the same roll reads differently down each column`() {
        // p. 43: "All three use the same roll; only the effects differ."
        val outcome = FrightCheck.against(will = 10, modifier = 0, source = scripted(6, 6, 6, 1, 1, 1))
        val effect = outcome.effect!!
        assertTrue("Will" in FrightCheck.text(effect, FrightKind.FRIGHT))
        assertTrue("Will" in FrightCheck.text(effect, FrightKind.AWE))
        assertTrue("IQ" in FrightCheck.text(effect, FrightKind.CONFUSION))
    }

    @Test
    fun `every roll from four upward lands on exactly one band`() {
        // The lowest possible effect roll is 3d6 with a margin of 1, and the
        // table starts at 4. Anything the table cannot answer is a gap.
        val bands = ContentLoader.frightEffects()
        (4..60).forEach { roll ->
            val matches = bands.filter { roll >= it.low && (it.high == null || roll <= it.high) }
            assertEquals(1, matches.size, "roll $roll matched ${matches.map { it.printed }}")
        }
    }

    @Test
    fun `a roll below the table is a bad call, not a silent nothing`() {
        // A margin of failure is at least 1, so an effect roll under 4 means
        // the caller has computed something wrong.
        assertThrows<IllegalArgumentException> { FrightCheck.effectFor(3) }
    }

    @Test
    fun `the worst band has no ceiling`() {
        assertEquals("35+", FrightCheck.effectFor(35).printed)
        assertEquals("35+", FrightCheck.effectFor(500).printed)
    }

    @Test
    fun `triggers keep the range the GM chooses inside`() {
        val monster = FrightCheck.triggers().single { it.trigger.startsWith("Encountering a monster") }
        assertEquals(-2, monster.modifier)
        assertEquals(-6, monster.worstCase)
        val outerGod = FrightCheck.triggers().single { it.trigger.startsWith("Outer God") }
        assertTrue(outerGod.openEnded)
        assertNull(outerGod.worstCase)
    }

    @Test
    fun `two bands hand out a new disadvantage, in every column`() {
        // Play session 5: 16-21 named a -10 disadvantage and pointed nowhere,
        // while the quirk band beside it records one.
        val granting = ContentLoader.frightEffects().filter { effect ->
            FrightKind.entries.all { FrightCheck.grantsDisadvantage(effect, it) }
        }.map { it.printed }
        assertEquals(listOf("16–21", "22–23"), granting)
        val none = ContentLoader.frightEffects().filter { effect ->
            FrightKind.entries.none { FrightCheck.grantsDisadvantage(effect, it) }
        }
        assertEquals(ContentLoader.frightEffects().size - 2, none.size)
    }

    @Test
    fun `the effect roll is thrown with its own dice, so the log can name it`() {
        // Play session 5: the check and its effect roll both read "Fright
        // Check" in the roll log.
        val effectThrows = mutableListOf<List<Int>>()
        val effect = object : RandomSource {
            override fun roll(sides: Int) = 2
            override fun thrown(faces: List<Int>, target: Int?) { effectThrows += faces }
        }
        val outcome = FrightCheck.rollAgainst(target = 5, source = fixed(6), effectSource = effect)
        assertEquals(listOf(listOf(2, 2, 2)), effectThrows)
        assertEquals(6 + 13, outcome.effectRoll)
    }

    @Test
    fun `a critical failure under its own target still lands on the table`() {
        // 18 always fails, even against a target of 18 -- and then nothing was
        // overshot, so a bare 3 on the effect roll would fall off the bottom
        // of a table that starts at 4.
        val outcome = FrightCheck.against(will = 18, modifier = 0, source = scripted(6, 6, 6, 1, 1, 1))
        assertFalse(outcome.passed)
        assertEquals(0, outcome.margin)
        assertEquals(FrightCheck.LOWEST_EFFECT_ROLL, outcome.effectRoll)
        assertEquals("4–9", outcome.effect?.printed)
    }

    @Test
    fun `a critical success passes however bad the modifier`() {
        // SuccessRoll owns the GURPS critical rules, and the Fright Check does
        // not override them the way the Potion Consumption Roll does -- so a 3
        // passes even at a target of 4 or below.
        val outcome = FrightCheck.against(will = 6, modifier = -8, source = fixed(1))
        assertTrue(outcome.passed, "3 on 3d6 is a critical success")
    }

    /**
     * A screen that prints "3d6 <= N" and then rolls must roll against N.
     * Composing N a second time inside the roll is two call sites answering
     * one question (6eg, 6eh), so the roll takes the composed target and
     * the older entry point is that roll with the target composed first.
     */
    @Test
    fun `a roll against a composed target is the check that composed it`() {
        assertEquals(8, FrightCheck.targetFor(will = 10, modifier = -2))
        // 6+6+6 against 8 fails by 10; the table roll is 1+1+2 plus that 10.
        val rolled = FrightCheck.rollAgainst(8, scripted(6, 6, 6, 1, 1, 2))
        assertEquals(8, rolled.target)
        assertEquals(8, rolled.roll.target, "judged against the target it was handed")
        assertEquals(10, rolled.margin)
        assertEquals(14, rolled.effectRoll)
        assertEquals(FrightCheck.against(10, -2, scripted(6, 6, 6, 1, 1, 2)), rolled)
    }

    /**
     * The band that hands out a quirk, which play session 2 reached and had
     * nowhere to write down. Read from every cell rather than from the one
     * row, so a second band printing a quirk is a failing test.
     */
    @Test
    fun `only the 13-15 band grants a quirk, in all three columns`() {
        for (effect in ContentLoader.frightEffects()) {
            for (kind in FrightKind.entries) {
                assertEquals(effect.printed == "13–15", FrightCheck.grantsQuirk(effect, kind), "${effect.printed} $kind")
            }
        }
    }
}
