package com.pathways.rules

import com.pathways.model.BeyonderState
import com.pathways.model.DiceExpr
import com.pathways.model.Outcome
import com.pathways.rules.content.ContentLoader
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PotionConsumptionTest {

    /** A 3d6 that always shows [face] on every die. */
    private fun fixed(face: Int) = RandomSource { face }

    @Test
    fun `the modifier table is the book's, not a formula`() {
        // -9, -7, -5, -4, -2, 0, +2, +4, +5, +7, +9: the steps are
        // 2,2,1,2,2,2,2,1,2,2. Anything that interpolates gets 30% and 80%
        // wrong, which is the thrust/swing trap in a new costume (spec 7.3).
        val rows = ContentLoader.potionConsumption()
        assertEquals((0..100 step 10).toList(), rows.map { it.digestion })
        assertEquals(
            listOf(-9, -7, -5, -4, -2, 0, 2, 4, 5, 7, 9),
            rows.map { it.modifier },
        )
    }

    @Test
    fun `the printed target agrees with 10 plus the modifier on every row`() {
        // Both columns are extracted rather than one derived, so that a
        // disagreement would show up here instead of being smoothed away.
        ContentLoader.potionConsumption().forEach { row ->
            assertEquals(10 + row.modifier, row.effectiveTarget, "at ${row.digestion}%")
        }
    }

    @Test
    fun `a potion drunk at zero digestion can never be survived`() {
        // The book labels the 0% row "1 - always fails", and 3d6 cannot roll
        // below 3. The lowest possible roll must still be a loss.
        val outcome = PotionConsumption.attempt(BeyonderState("Fool", 9, digestion = 0), cor = 0, fixed(1))

        assertEquals(1, outcome.target)
        assertTrue(outcome.lost)
        assertFalse(outcome.integrated)
    }

    @Test
    fun `a fully digested potion can never be failed`() {
        // "19 - always succeeds", and 3d6 cannot exceed 18.
        val outcome = PotionConsumption.attempt(BeyonderState("Fool", 9, digestion = 100), cor = 0, fixed(6))

        assertEquals(19, outcome.target)
        assertTrue(outcome.integrated)
        assertEquals(0, outcome.corGained)
    }

    @Test
    fun `advancing before full digestion rolls 1d6 corruption`() {
        // v6.9i line 3188-3189: "Advancing sequence without full digestion --
        // 1d6 CoR" (v6.9d printed "10+"). Rolled after the PCR succeeds:
        // fixed(2) throws 6 on the 3d6 and then 2 on the d6.
        val survived = PotionConsumption.attempt(BeyonderState("Fool", 9, digestion = 90), cor = 0, fixed(2))

        assertTrue(survived.integrated)
        assertEquals(2, survived.corGained)
    }

    @Test
    fun `a failed or fully digested potion rolls no corruption die`() {
        // A failure ends the character; a full digestion costs nothing. In
        // both cases the three PCR dice are the only dice thrown.
        for ((digestion, face) in listOf(0 to 6, 100 to 1)) {
            var thrown = 0
            val outcome = PotionConsumption.attempt(
                BeyonderState("Fool", 9, digestion = digestion), cor = 0,
                RandomSource { thrown++; face },
            )
            assertEquals(0, outcome.corGained, "at $digestion%")
            assertEquals(3, thrown, "at $digestion%")
        }
    }

    @Test
    fun `what advancing costs can be asked before the roll is made`() {
        // The screen prints this beside the Drink button, so it is the
        // state's answer and not something only a rolled outcome knows.
        assertEquals(DiceExpr(1), PotionConsumption.corDiceFor(BeyonderState("Fool", 9, digestion = 0)))
        assertEquals(DiceExpr(1), PotionConsumption.corDiceFor(BeyonderState("Fool", 9, digestion = 95)))
        assertEquals(null, PotionConsumption.corDiceFor(BeyonderState("Fool", 9, digestion = 100)))
    }

    @Test
    fun `current corruption lowers the target one per point`() {
        // Line 3203-3204: "-1 per point of CoR (a Beyonder with CoR 6 rolls
        // against 4 + Digestion Modifier)". 50% is the +0 row.
        assertEquals(4, PotionConsumption.targetFor(50, cor = 6))
        assertEquals(10, PotionConsumption.targetFor(50, cor = 0))
        assertEquals(16, PotionConsumption.attempt(BeyonderState("Fool", 9, digestion = 100), cor = 3, fixed(1)).target)
        assertFailsWith<IllegalArgumentException> { PotionConsumption.targetFor(50, cor = -1) }
    }

    @Test
    fun `the PCR overrides the general critical rules at both ends`() {
        // The generic success roll is a GURPS one: 3-4 always succeed and
        // 17-18 always fail, whatever the target. The PCR table says
        // otherwise -- "1 - always fails" and "19 - always succeeds" are only
        // true if a 3 loses at target 1 and an 18 survives at target 19. The
        // specific rule wins; this pins the disagreement so neither side gets
        // quietly normalised.
        val three = PotionConsumption.attempt(BeyonderState("Fool", 9, digestion = 0), cor = 0, fixed(1))
        assertEquals(3, three.roll.total)
        assertEquals(Outcome.CRITICAL_SUCCESS, three.roll.outcome)
        assertTrue(three.lost, "a natural 3 must still fail at target 1")

        val eighteen = PotionConsumption.attempt(BeyonderState("Fool", 9, digestion = 100), cor = 0, fixed(6))
        assertEquals(18, eighteen.roll.total)
        assertEquals(Outcome.CRITICAL_FAILURE, eighteen.roll.outcome)
        assertTrue(eighteen.integrated, "a natural 18 must still succeed at target 19")
    }

    @Test
    fun `a percentage between two rows uses the lower one`() {
        // 47% has not reached the 50% bracket, so it rolls against 40%'s -2.
        assertEquals(PotionConsumption.rowFor(40).modifier, PotionConsumption.rowFor(47).modifier)
        assertEquals(-2, PotionConsumption.rowFor(47).modifier)
        assertEquals(0, PotionConsumption.rowFor(50).modifier)
    }

    @Test
    fun `a digestion outside nought to a hundred is rejected`() {
        assertFailsWith<IllegalArgumentException> { PotionConsumption.rowFor(101) }
        assertFailsWith<IllegalArgumentException> { PotionConsumption.rowFor(-1) }
        assertFailsWith<IllegalArgumentException> { BeyonderState("Fool", 9, digestion = 101) }
    }

    @Test
    fun `a session of acting moves digestion within the book's range`() {
        val exemplary = ContentLoader.actingMethod().single { it.quality == "Exemplary" }
        assertEquals(15, exemplary.minGain)
        assertEquals(20, exemplary.maxGain)

        val state = BeyonderState("Fool", 9, digestion = 90)
        assertEquals(100, state.digested(exemplary.maxGain!!).digestion)
        assertTrue(state.digested(exemplary.minGain!!).isFullyDigested)
    }

    @Test
    fun `ignoring the pathway carries no gain figure at all`() {
        // "0% or loses progress" is not a number; storing 0 would invent a
        // floor the book does not give, so the row says only that progress
        // can be lost.
        val none = ContentLoader.actingMethod().single { it.quality == "None" }
        assertEquals(null, none.minGain)
        assertTrue(none.losesProgress)
    }

    @Test
    fun `the next potion starts its own digestion from zero`() {
        val digested = BeyonderState("Fool", 9, digestion = 100)
        assertEquals(BeyonderState("Fool", 8, digestion = 0), digested.advanced())
    }

    /** A spirit hit's "-1 per point ... any roll" reaches the PCR too (v6.9i 2819). */
    @Test
    fun `a modifier moves the PCR target`() {
        val state = com.pathways.model.BeyonderState("Fool", 9, digestion = 100)
        val base = PotionConsumption.targetFor(100, cor = 0)
        assertEquals(base - 3, PotionConsumption.targetFor(100, cor = 0, modifier = -3))
        assertEquals(base - 3, PotionConsumption.attempt(state, cor = 0, source = { 1 }, modifier = -3).target)
    }
}
