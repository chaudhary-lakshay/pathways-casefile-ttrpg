package com.pathways.rules

import com.pathways.model.BeyonderState
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
        val outcome = PotionConsumption.attempt(BeyonderState("Fool", 9, digestion = 0), fixed(1))

        assertEquals(1, outcome.target)
        assertTrue(outcome.lost)
        assertFalse(outcome.integrated)
    }

    @Test
    fun `a fully digested potion can never be failed`() {
        // "19 - always succeeds", and 3d6 cannot exceed 18.
        val outcome = PotionConsumption.attempt(BeyonderState("Fool", 9, digestion = 100), fixed(6))

        assertEquals(19, outcome.target)
        assertTrue(outcome.integrated)
        assertEquals(0, outcome.minimumCorGained)
    }

    @Test
    fun `advancing before full digestion costs at least ten corruption`() {
        // p. 45 prices this at "10+ CoR (catastrophic)" whatever the roll
        // does, so it is charged on the attempt, not on the failure.
        val survived = PotionConsumption.attempt(BeyonderState("Fool", 9, digestion = 90), fixed(1))

        assertTrue(survived.integrated)
        assertEquals(10, survived.minimumCorGained)
    }

    @Test
    fun `what advancing costs can be asked before the roll is made`() {
        // The screen prints this beside the Drink button, so it is the
        // state's answer and not something only a rolled outcome knows.
        assertEquals(10, PotionConsumption.corCostFor(BeyonderState("Fool", 9, digestion = 0)))
        assertEquals(10, PotionConsumption.corCostFor(BeyonderState("Fool", 9, digestion = 95)))
        assertEquals(0, PotionConsumption.corCostFor(BeyonderState("Fool", 9, digestion = 100)))
    }

    @Test
    fun `the PCR overrides the general critical rules at both ends`() {
        // The generic success roll is a GURPS one: 3-4 always succeed and
        // 17-18 always fail, whatever the target. The PCR table says
        // otherwise -- "1 - always fails" and "19 - always succeeds" are only
        // true if a 3 loses at target 1 and an 18 survives at target 19. The
        // specific rule wins; this pins the disagreement so neither side gets
        // quietly normalised.
        val three = PotionConsumption.attempt(BeyonderState("Fool", 9, digestion = 0), fixed(1))
        assertEquals(3, three.roll.total)
        assertEquals(Outcome.CRITICAL_SUCCESS, three.roll.outcome)
        assertTrue(three.lost, "a natural 3 must still fail at target 1")

        val eighteen = PotionConsumption.attempt(BeyonderState("Fool", 9, digestion = 100), fixed(6))
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
}
