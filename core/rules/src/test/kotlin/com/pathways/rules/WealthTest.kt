package com.pathways.rules

import com.pathways.model.Attributes
import com.pathways.model.SecondaryDelta
import com.pathways.model.Trait
import com.pathways.model.TraitKind
import com.pathways.rules.content.ContentLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Starting Wealth by Economic Status (Ch. 8, p. 80).
 *
 * Two things are worth pinning: that the reaction ladder is NOT symmetric
 * around Average, and that changing a character's wealth level replaces the
 * one their sheet printed rather than adding a second -- a character has one
 * economic status, and two wealth traits would bill twice.
 */
class WealthTest {

    @Test
    fun `the seven levels load with the columns the book prints`() {
        assertEquals(
            listOf(
                "Dead Broke", "Poor", "Struggling", "Average", "Comfortable",
                "Wealthy", "Very Wealthy",
            ),
            Wealth.all().map { it.status },
        )
        val broke = Wealth.named("Dead Broke")
        assertEquals(-25, broke.points)
        assertEquals("£0", broke.startingMoney)
        assertEquals(-2, broke.reactionModifier)
        assertEquals("No home; beg or steal for every meal", broke.lifestyle)
    }

    @Test
    fun `three of the seven levels are printed only in chapter 8`() {
        // Chapter 3's advantage and disadvantage lists carry four wealth
        // levels. Average, Wealthy and Very Wealthy are not among them, so
        // until this table was read they could not be bought at all.
        val chapterThree = (ContentLoader.advantages() + ContentLoader.disadvantages())
            .map { it.name }
            .filter { it.startsWith(Wealth.TRAIT_PREFIX) }
            .map { it.removePrefix(Wealth.TRAIT_PREFIX) }
            .toSet()
        assertEquals(
            setOf("Average", "Wealthy", "Very Wealthy"),
            Wealth.all().map { it.status }.toSet() - chapterThree,
        )
    }

    @Test
    fun `the reaction ladder is not symmetric around Average`() {
        // The paragraph under the table says "+1 to reaction rolls per Status
        // level above Average ... and -1 per level below Average". Above
        // Average the column agrees. Below it, it does not: Struggling is one
        // level down and prints 0, and the paragraph's own example puts Poor
        // -- two levels down -- at -1. The printed column wins (CLAUDE.md
        // 4.1), so this asymmetry is the answer, not a parse to straighten.
        assertEquals(1, Wealth.named("Comfortable").reactionModifier)
        assertEquals(2, Wealth.named("Wealthy").reactionModifier)
        assertEquals(0, Wealth.named("Struggling").reactionModifier)
        assertEquals(-1, Wealth.named("Poor").reactionModifier)
    }

    @Test
    fun `a level's sign becomes a kind, and its cost a magnitude`() {
        // Trait.cost is always positive and the kind carries the direction;
        // this table signs the number instead. Applying both would double it.
        assertEquals(
            Trait("Wealth: Poor", 15, TraitKind.DISADVANTAGE),
            Wealth.traitFor(Wealth.named("Poor")),
        )
        assertEquals(
            Trait("Wealth: Very Wealthy", 25, TraitKind.ADVANTAGE),
            Wealth.traitFor(Wealth.named("Very Wealthy")),
        )
    }

    @Test
    fun `Average is priced at nothing and so buys nothing`() {
        // 0 pts is neither an advantage nor a disadvantage. A zero-cost trait
        // of either kind would be a row on the sheet that bills nothing --
        // the same distinction RitualCritical draws between charging no
        // corruption and charging zero.
        assertNull(Wealth.traitFor(Wealth.named("Average")))
    }

    @Test
    fun `a level the book does not print is refused, not defaulted`() {
        assertThrows<IllegalArgumentException> { Wealth.named("Fabulously Rich") }
    }

    @Test
    fun `changing wealth replaces the printed level rather than adding one`() {
        val printed = listOf(
            Trait("Skinny", 5, TraitKind.DISADVANTAGE),
            Trait("Wealth: Poor", 15, TraitKind.DISADVANTAGE),
        )
        assertEquals("Poor", Wealth.levelOf(printed)?.status)

        val richer = Wealth.applied(printed, Wealth.named("Wealthy"))
        assertEquals(
            listOf("Skinny", "Wealth: Wealthy"),
            richer.map { it.name },
            "the old wealth trait is gone, and the rest of the sheet is untouched",
        )
        assertEquals("Wealthy", Wealth.levelOf(richer)?.status)

        // And Average leaves no trait behind at all.
        assertEquals(
            listOf("Skinny"),
            Wealth.applied(printed, Wealth.named("Average")).map { it.name },
        )
    }

    @Test
    fun `a sheet that prints no wealth level has none`() {
        assertNull(Wealth.levelOf(listOf(Trait("Skinny", 5, TraitKind.DISADVANTAGE))))
    }

    @Test
    fun `the budget answers back on both sides of the swap`() {
        // The caller this table was extracted for. Poor GIVES 15 points;
        // Wealthy COSTS 15. Swapping one for the other is a 30-point move,
        // which is the whole reason a wealth picker belongs beside a budget.
        fun report(traits: List<Trait>) = PointBudget.report(
            Attributes(), SecondaryDelta(), traits, emptyList(),
        )

        val poor = listOf(Trait("Wealth: Poor", 15, TraitKind.DISADVANTAGE))
        val wealthy = Wealth.applied(poor, Wealth.named("Wealthy"))

        assertEquals(PointBudget.STARTING_TOTAL + 15, report(poor).budget)
        assertEquals(0, report(poor).spent)
        assertEquals(PointBudget.STARTING_TOTAL, report(wealthy).budget)
        assertEquals(15, report(wealthy).spent)
        assertEquals(report(poor).surplus - 30, report(wealthy).surplus)
    }
}
