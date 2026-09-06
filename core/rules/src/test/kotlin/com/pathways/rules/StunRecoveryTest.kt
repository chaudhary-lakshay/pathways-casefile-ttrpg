package com.pathways.rules

import com.pathways.model.Trait
import com.pathways.model.TraitKind
import com.pathways.rules.content.ContentLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The Major Wound table's own recovery roll, and who reaches it.
 *
 * `TraitEffects.PARTLY_APPLIED` refused Combat Reflexes' printed clause
 * "+6 to recover from stun" saying "this app models no stun recovery roll to
 * make". The Failure row prints one — "Stunned for 1d6 seconds (roll HT each
 * turn to recover early)" — and `DamageDialog` has rendered that sentence
 * since 6n. What is under test is the SCOPE, because the composition is what
 * a single-total assertion would not notice.
 */
class StunRecoveryTest {

    private fun trait(name: String) = Trait(name, 0, TraitKind.ADVANTAGE)

    private val reflexes = listOf(trait("Combat Reflexes"))
    private val fit = listOf(trait("Fit"))
    private val painThreshold = listOf(trait("High Pain Threshold"))

    @Test
    fun `only the Failure row stuns, and the row's own name says which`() {
        val rows = ContentLoader.majorWound()
        val stunning = rows.filter { StunRecovery.stunsOn(it) }
        assertEquals(1, stunning.size, "exactly one row of ${rows.size} stuns: $rows")
        assertTrue(
            stunning.single().effect.contains("Stunned"),
            "the row this object identifies does not print a stun: ${stunning.single()}",
        )
        // The success row causes shock instead, and the fail-by-5 row knocks
        // the character out. Neither offers a recovery roll of this kind.
        assertFalse(rows.any { Shock.causedBy(it) && StunRecovery.stunsOn(it) })
        assertFalse(rows.any { it.failBy != null && StunRecovery.stunsOn(it) })
    }

    @Test
    fun `the row's own sentence is quoted, never paraphrased`() {
        val printed = StunRecovery.printed()
        assertEquals(
            ContentLoader.majorWound().first { StunRecovery.stunsOn(it) }.effect,
            printed,
        )
        assertTrue(printed.contains("roll HT each turn to recover early"), printed)
    }

    @Test
    fun `Combat Reflexes reaches this roll, and it is the clause's first reader`() {
        assertEquals(6, StunRecovery.bonusFor(reflexes) - StunRecovery.bonusFor(emptyList()))
        assertEquals(0, StunRecovery.bonusFor(emptyList()))
    }

    @Test
    fun `Fit composes because this recovery roll is an HT roll`() {
        assertEquals(1, StunRecovery.bonusFor(fit))
        assertEquals(7, StunRecovery.bonusFor(reflexes + fit))
    }

    @Test
    fun `High Pain Threshold does not, because its clause names knockdown`() {
        // Its +3 reaches the Major Wound roll itself (TraitEffects.knockdownBonus),
        // whose fail-by-5 row IS a knockdown. Recovering from the stun that row
        // above it avoids no knockdown, so widening the clause would be 6ck's
        // refused move.
        assertEquals(0, StunRecovery.bonusFor(painThreshold))
        assertEquals(3, TraitEffects.knockdownBonus(painThreshold))
    }

    @Test
    fun `the target is the attribute, the traits and what the trackers cost`() {
        assertEquals(10 + 6 - 2, StunRecovery.targetFor(ht = 10, traits = reflexes, poolPenalty = -2))
        assertEquals(10, StunRecovery.targetFor(ht = 10, traits = emptyList(), poolPenalty = 0))
    }

    @Test
    fun `the credit names every trait in the number, and nothing else`() {
        val credit = TraitEffects.stunRecoveryHt(reflexes + fit)
        assertEquals(StunRecovery.bonusFor(reflexes + fit), credit.bonus)
        assertTrue(credit.line!!.contains("Combat Reflexes"), credit.line)
        assertTrue(credit.line!!.contains("Fit"), credit.line)
        // High Pain Threshold moves the table's OWN roll and not this one, so
        // it must not appear in this roll's credit while it does in that one.
        val painCredit = TraitEffects.stunRecoveryHt(painThreshold)
        assertEquals(0, painCredit.bonus)
        assertEquals(null, painCredit.line)
        assertTrue(TraitEffects.majorWoundHt(painThreshold).line!!.contains("High Pain Threshold"))
    }

    @Test
    fun `no credit is printed for a roll nothing moved`() {
        val none = TraitEffects.stunRecoveryHt(emptyList())
        assertEquals(0, none.bonus)
        assertEquals(null, none.line)
    }
}
