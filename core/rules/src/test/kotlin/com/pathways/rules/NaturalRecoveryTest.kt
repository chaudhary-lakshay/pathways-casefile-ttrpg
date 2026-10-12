package com.pathways.rules

import com.pathways.model.Trait
import com.pathways.model.TraitKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The daily HT roll of the HP recovery table (Ch. 5, p. 42).
 *
 * The table was refused whole as "clocks, and this app holds none". Its
 * first row is also a roll, and a roll is what this app is for; the day is
 * the player's to declare, and what a success restores is the GM's to apply.
 */
class NaturalRecoveryTest {

    private fun trait(name: String) = Trait(name, 5, TraitKind.ADVANTAGE)

    @Test
    fun `the roll is offered to a hurt character who is not dead`() {
        assertFalse(NaturalRecovery.isOffered(Pools.hp(12, 12)), "nothing to recover")
        assertTrue(NaturalRecovery.isOffered(Pools.hp(11, 12)))
        assertTrue(NaturalRecovery.isOffered(Pools.hp(-3, 12)), "the book prints no bar below 0 HP")
        // -5xHP: "Dead -- no roll required". A day's rest must not walk back out of it.
        assertFalse(NaturalRecovery.isOffered(Pools.hp(-60, 12)))
    }

    @Test
    fun `the target is HT, the pool penalty and what the GM adds`() {
        assertEquals(10, NaturalRecovery.targetFor(ht = 10, traits = emptyList(), poolPenalty = 0, gmModifier = 0))
        // Reeling's -1, and Chained Sequence 8's Rapid Healing entered by hand.
        assertEquals(14, NaturalRecovery.targetFor(ht = 10, traits = emptyList(), poolPenalty = -1, gmModifier = 5))
    }

    @Test
    fun `a trait that reaches every HT roll reaches this one`() {
        assertEquals(1, NaturalRecovery.bonusFor(listOf(trait("Fit"))))
        assertEquals(11, NaturalRecovery.targetFor(10, listOf(trait("Fit")), poolPenalty = 0, gmModifier = 0))
    }

    @Test
    fun `the bonuses scoped to the two threshold rolls stay with them`() {
        // Hard to Kill is "to avoid death" and Hard to Subdue "to stay
        // conscious": VitalRolls' two, and neither is this roll.
        val scoped = listOf(trait("Hard to Kill"), trait("Hard to Subdue"))
        assertEquals(0, NaturalRecovery.bonusFor(scoped))
    }

    @Test
    fun `the book's three rows are printed and none is computed`() {
        val rows = NaturalRecovery.rows()
        assertEquals(3, rows.size)
        assertTrue("HT roll" in rows.first().method, "the row that makes this a roll")
        assertEquals(listOf("2× base rate", "3× base rate"), rows.drop(1).map { it.recovered })
    }

    @Test
    fun `the rest of the recovery section is refused by name`() {
        // The table's other three methods, and the Regeneration table under
        // it. Each has its own reason; only the last is a clock.
        assertEquals(
            setOf("First Aid", "Physician", "Healing advantage", "Regeneration"),
            NaturalRecovery.UNMODELLED.keys,
        )
        assertTrue(NaturalRecovery.UNMODELLED.values.all { it.isNotBlank() })
        assertTrue("clock" in NaturalRecovery.UNMODELLED.getValue("Regeneration"))
        assertFalse("clock" in NaturalRecovery.UNMODELLED.getValue("First Aid"))
    }

    /**
     * Fast Healer: "Recover 1 additional HP per day of rest" (book line 894;
     * 6fc). On a success both readings give one more HP, so the line says so.
     * On a failure they part -- "additional" to a recovery that did not
     * happen, or one HP for every day of rest -- and the GM decides.
     */
    @Test
    fun `a Fast Healer's extra point is said after the roll`() {
        assertEquals(
            "Restore what the rows above print, with +1 on the HP row. Which band applies is the GM's.",
            NaturalRecovery.dayLine(success = true, traits = emptyList()),
        )
        assertEquals("Nothing is restored for this day.", NaturalRecovery.dayLine(success = false, traits = emptyList()))
        // Holding something else is not holding Fast Healer (the diff review).
        val other = listOf(trait("Fit"))
        for (success in listOf(true, false)) {
            assertEquals(
                NaturalRecovery.dayLine(success, emptyList()),
                NaturalRecovery.dayLine(success, other),
            )
        }
        // A sheet's trailing level ("Alertness 1") still holds it.
        assertTrue("1 more for Fast Healer" in NaturalRecovery.dayLine(true, listOf(trait("Fast Healer 1"))))

        val healer = listOf(trait(TraitEffects.FAST_HEALER))
        val cell = com.pathways.rules.content.ContentLoader.advantages()
            .single { it.name == TraitEffects.FAST_HEALER }.notes
        val clause = "Recover 1 additional HP per day of rest"
        assertTrue(clause in cell, cell)

        val won = NaturalRecovery.dayLine(success = true, traits = healer)
        assertTrue("+1 on the HP row, and 1 more for Fast Healer" in won, won)
        assertTrue(clause in won, won)
        val lost = NaturalRecovery.dayLine(success = false, traits = healer)
        assertTrue("Nothing is restored by the roll" in lost, lost)
        assertTrue("GM" in lost && "Fast Healer" in lost, lost)
    }

    /** The daily HT roll takes shock as its own term too (6gs). */
    @Test
    fun `shock reaches the daily recovery roll as its own term`() {
        assertEquals(
            NaturalRecovery.targetFor(10, emptyList(), -1, 0) - 4,
            NaturalRecovery.targetFor(10, emptyList(), -1, 0, shock = -4),
        )
    }
}
