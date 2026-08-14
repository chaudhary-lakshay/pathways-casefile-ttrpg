package com.pathways.rules

import com.pathways.model.Attributes
import com.pathways.model.SecondaryDelta
import com.pathways.model.Trait
import com.pathways.model.TraitKind
import com.pathways.rules.content.ContentLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Buying a trait in play: which rows, at what price, and which are withheld. */
class PurchasableTraitsTest {

    @Test
    fun `the trait this whole slice exists for is buyable`() {
        // No premade carries Ether Body Damage and no potion grants a trait,
        // so before this there was no route to it at all -- the "unreachable
        // feature looks exactly like a missing one" shape (6bv).
        val row = PurchasableTraits.named(TraitEffects.ETHER_BODY_DAMAGE)
        assertNotNull(row)
        assertEquals(10, row!!.cost)
        assertFalse(row.advantage, "it is a disadvantage, and the budget refunds it")
        assertEquals(TraitKind.DISADVANTAGE, row.toTrait().kind)
        assertEquals(10, row.toTrait().cost, "Trait carries a magnitude; the kind is the sign")
    }

    /**
     * **Every printed row is either offered or named as unofferable.** The
     * total is not asserted as a NUMBER: a count in prose is a finding (6as),
     * and one in a test goes stale the first time the catalogue grows.
     */
    @Test
    fun `every printed row is either offered or explained`() {
        val printed = (ContentLoader.advantages() + ContentLoader.disadvantages())
            .map { it.name }.toSortedSet()
        val accounted = (
            PurchasableTraits.offered().map { it.name } +
                PurchasableTraits.unoffered().flatMap { it.names }
            ).toSortedSet()
        assertEquals(printed, accounted, "a printed row is neither offered nor explained")
    }

    @Test
    fun `only a row printing a single cost is offered`() {
        val byName = (ContentLoader.advantages() + ContentLoader.disadvantages())
            .associateBy { it.name }
        for (row in PurchasableTraits.offered()) {
            assertEquals(
                byName.getValue(row.name).cost,
                row.cost,
                "${row.name} is offered at a price the book does not print",
            )
        }
    }

    /**
     * **Economic status is one choice and [Wealth] owns it.** A general picker
     * offering "Wealth: Poor" would let a character hold two statuses, or
     * fight the wealth row for the same points -- and one slice ago that
     * double count was live (6bx). The exclusion is here rather than in the
     * screen so a hand-edited save cannot get round it either.
     */
    @Test
    fun `wealth is never offered as an ordinary trait`() {
        assertTrue(
            PurchasableTraits.offered().none { it.name.startsWith(Wealth.TRAIT_PREFIX) },
            "the trait picker offers an economic status",
        )
        assertNull(PurchasableTraits.named("Wealth: Poor"))
        // ...and the four rows are NAMED rather than silently missing.
        assertTrue(
            PurchasableTraits.unoffered().flatMap { it.names }
                .any { it.startsWith(Wealth.TRAIT_PREFIX) },
            "the wealth rows vanished without being explained",
        )
    }

    @Test
    fun `a row the book prices by its instance is withheld and said so`() {
        val withheld = PurchasableTraits.unoffered().flatMap { it.names }
        // A cost range: the instance settles the price, so there is nothing to
        // bill yet.
        assertTrue("Sense of Duty" in withheld)
        assertTrue("Secret" in withheld)
        // A placeholder: the book asks the player to name the instance.
        assertTrue("Duty (specify)" in withheld)
        for (group in PurchasableTraits.unoffered()) {
            assertTrue(group.reason.isNotBlank(), "a withheld group with no reason")
            assertTrue(group.names.isNotEmpty(), "a reason with nothing under it")
        }
    }

    /**
     * **A duplicate charges points and changes nothing.** [PointBudget] bills
     * every row it is handed, while [TraitEffects] reads one canonical match
     * per trait -- so a second Fit costs 5 points and lifts no roll further.
     */
    @Test
    fun `a trait the character already holds cannot be bought again`() {
        val fit = PurchasableTraits.named("Fit")!!
        assertTrue(PurchasableTraits.canBuy(fit, emptyList()))
        assertFalse(PurchasableTraits.canBuy(fit, listOf(Trait("Fit", 5, TraitKind.ADVANTAGE))))
    }

    /**
     * **The sheet's own spelling counts as holding it.** Two premades print
     * "Clueless" with the book's whole effect cell glued to the name, and
     * offering them a second Clueless because the strings differ would be the
     * two-matchers failure this repo has produced six times. Resolved through
     * the one matcher production already uses.
     */
    @Test
    fun `a glued sheet spelling still blocks a second copy`() {
        val clueless = PurchasableTraits.named("Clueless")!!
        val printed = Premades.all()
            .flatMap { it.toCharacter().traits }
            .first { it.name.startsWith("Clueless") && it.name.length > "Clueless".length }
        assertFalse(
            PurchasableTraits.canBuy(clueless, listOf(printed)),
            "a glued spelling of the same trait let it be bought twice",
        )
    }

    @Test
    fun `a name the book no longer prints resolves to nothing`() {
        assertNull(PurchasableTraits.named("Gumption"))
    }

    /**
     * **The budget bills a bought trait exactly as it bills a printed one**,
     * which is why this feature needed no billing code: an advantage spends
     * and a disadvantage refunds, both through the magnitude and kind
     * [PurchasableTrait.toTrait] produces.
     */
    @Test
    fun `buying an advantage spends and buying a drawback refunds`() {
        val ether = PurchasableTraits.named(TraitEffects.ETHER_BODY_DAMAGE)!!.toTrait()
        val fit = PurchasableTraits.named("Fit")!!.toTrait()
        fun report(traits: List<Trait>) =
            PointBudget.report(Attributes(), SecondaryDelta(), traits, emptyList())

        assertEquals(PointBudget.STARTING_TOTAL + 10, report(listOf(ether)).budget)
        assertEquals(0, report(listOf(ether)).spent)
        assertEquals(5, report(listOf(fit)).spent)
    }
}
