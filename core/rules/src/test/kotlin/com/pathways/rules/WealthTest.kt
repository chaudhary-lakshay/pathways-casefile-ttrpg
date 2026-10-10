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

    // ---------------------------------------------------------------
    // The sheets' own spelling. Nine of the 23 print "Poverty: Struggling"
    // where the book prints "Wealth: Struggling", so nine characters' economic
    // status was invisible to a picker keyed on the book's prefix -- and the
    // replace that prefix performs left the sheet's row standing beside the
    // chosen one, which is the double count [Wealth.applied]'s own KDoc says
    // it prevents.
    // ---------------------------------------------------------------

    /** The nine that price it as the book does. */
    private val poverty = Trait("Poverty: Struggling", 10, TraitKind.DISADVANTAGE)

    @Test
    fun `the sheets' own spelling for a printed level resolves to that level`() {
        assertEquals("Struggling", Wealth.levelOf(listOf(poverty))?.status)
    }

    @Test
    fun `nine premades print it, and every one of them agrees on the price`() {
        // The warrant, over the real data rather than over an example. The
        // book prints "Poverty" as an economic status NOWHERE -- its only two
        // occurrences are a Vow's example list and setting prose -- and
        // "Struggling" only ever as a wealth level. So the qualifier names one
        // thing, and the price is the second, independent agreement that makes
        // this a resolution rather than a resemblance (the standard slice 6bw
        // set, and 6am's "Absent-Minded" is why it takes two).
        val printed = Premades.all().flatMap { sheet ->
            sheet.disadvantages.filter { it.name == "Poverty: Struggling" }
                .map { sheet.name to it.cost }
        }
        assertEquals(10, printed.size, "ten sheets print this spelling")
        assertEquals(
            9,
            printed.count { it.second == -Wealth.named("Struggling").points },
            "nine of the ten pay the book's own 10",
        )
    }

    @Test
    fun `a spelling whose price disagrees is recorded, not resolved`() {
        // University Librarian pays 5 for the same words. Its disadvantage
        // column is already a recorded arithmetic error (GoldenSheetTest's
        // disadvantagesExclusions), so the only evidence left is that the
        // words resemble -- which is exactly what 4.1 refuses. The level stays
        // unresolved and the disagreement stays visible.
        val cheap = Trait("Poverty: Struggling", 5, TraitKind.DISADVANTAGE)
        assertNull(Wealth.levelOf(listOf(cheap)))
    }

    @Test
    fun `the kind must agree too, not just the magnitude`() {
        // Comfortable is +5 and Struggling -10, so a magnitude alone could let
        // a row of the wrong sign resolve to a level that costs the same the
        // other way. The sign is what makes it the same trait.
        val wrongWay = Trait("Poverty: Struggling", 10, TraitKind.ADVANTAGE)
        assertNull(Wealth.levelOf(listOf(wrongWay)))
    }

    @Test
    fun `the book's own spelling resolves whatever the sheet charged for it`() {
        // Fortune Teller prints "Wealth: Comfortable" at 10 where the book says
        // 5 -- a recorded cost conflict, and still plainly a statement of which
        // level the character is. Requiring price agreement of EVERY row would
        // have stopped resolving a row that has always resolved. Caught by a
        // design review before this was written.
        val dear = Trait("Wealth: Comfortable", 10, TraitKind.ADVANTAGE)
        assertEquals("Comfortable", Wealth.levelOf(listOf(dear))?.status)
    }

    @Test
    fun `a row naming an economic status is replaced even when it does not resolve`() {
        // Deriving a level and being replaced by the picker are two different
        // questions. University Librarian's row does not say WHICH level the
        // book would give them, but it plainly names one -- so it must not
        // survive beside a chosen level, or the character holds two economic
        // statuses and the budget counts both.
        val sheet = listOf(
            Trait("Skinny", 5, TraitKind.DISADVANTAGE),
            Trait("Poverty: Struggling", 5, TraitKind.DISADVANTAGE),
        )
        assertNull(Wealth.levelOf(sheet), "the price disagrees, so no level is claimed")
        assertEquals(
            listOf("Skinny", "Wealth: Wealthy"),
            Wealth.applied(sheet, Wealth.named("Wealthy")).map { it.name },
        )
    }

    @Test
    fun `no premade can hold two economic statuses after a pick`() {
        // The general guard, not four spot checks: every fixture against every
        // printed level. This is the defect itself -- before the fix, picking
        // any level on any of the ten left the sheet's own poverty row behind.
        Premades.all().forEach { sheet ->
            val traits = sheet.toCharacter().traits
            Wealth.all().forEach { level ->
                val after = Wealth.applied(traits, level)
                // Average is 0 pts and leaves no trait at all, so the
                // invariant is "never two", not "always one".
                assertEquals(
                    if (level.points == 0) 0 else 1,
                    after.count { Wealth.namesEconomicStatus(it) },
                    "${sheet.name} picking ${level.status}: ${after.map { it.name }}",
                )
            }
        }
    }

    @Test
    fun `a qualifier that names no printed level is left alone`() {
        // "Paranoia: Bodies of water" is the same shape and names no level, so
        // the rule cannot reach it. Neither can a Vow OF poverty, which is the
        // one other place the book writes the word at all -- and it is a
        // promise, priced as a Vow, not an economic status.
        val others = listOf(
            Trait("Paranoia: Bodies of water", 5, TraitKind.DISADVANTAGE),
            Trait("Vow (Poverty)", 10, TraitKind.DISADVANTAGE),
            Trait("Social Stigma (Criminal)", 5, TraitKind.DISADVANTAGE),
            Trait("Blacklisted: Specific Industry org or church", 10, TraitKind.DISADVANTAGE),
        )
        assertNull(Wealth.levelOf(others))
        others.forEach { assertEquals(false, Wealth.namesEconomicStatus(it), it.name) }
        assertEquals(
            others.map { it.name } + "Wealth: Poor",
            Wealth.applied(others, Wealth.named("Poor")).map { it.name },
        )
    }

    @Test
    fun `a head word neither printing prints cannot claim a status`() {
        // A rule that ignored the head entirely would read this as the
        // character's economic status the moment its qualifier and price lined
        // up -- and then DELETE it on the next pick, because applied() removes
        // whatever names a status. No fixture can reach it, which is the
        // reason to close it rather than leave it for the next caller (the
        // call slice 6l made about a permissive damage-type lookup). Codex
        // found it in review.
        val notWealth = Trait("Reputation: Wealthy", 15, TraitKind.ADVANTAGE)
        assertNull(Wealth.levelOf(listOf(notWealth)))
        assertEquals(false, Wealth.namesEconomicStatus(notWealth))
        assertEquals(
            listOf("Reputation: Wealthy", "Wealth: Poor"),
            Wealth.applied(listOf(notWealth), Wealth.named("Poor")).map { it.name },
            "a row this object does not understand is left where it was",
        )
    }

    /**
     * A held wealth level prints a cell (6fr). The trait list prints four
     * levels as rows; Wealthy and Very Wealthy are printed only in the table
     * (4965-4972), so their cell is its three columns, the reaction column
     * worded as 4973 words it. Nothing applies any of it.
     */
    @Test
    fun `a held wealth level prints its cell, whatever the sheet called it`() {
        // Copied from the table, never computed.
        val printed = mapOf(
            "Wealthy" to "Starting money £50. +2 to reaction rolls. Nice townhouse; fine clothes; a servant",
            "Very Wealthy" to "Starting money £100. +3 to reaction rolls. Large house; multiple servants; carriage",
        )
        for ((status, cell) in printed) {
            val held = TraitReport.held(listOf(Wealth.traitFor(Wealth.named(status))!!), TraitKind.ADVANTAGE).single()
            assertEquals(cell, held.cell, status)
            assertEquals(listOf(TraitReport.UNREAD), held.notes.map { it.reason }, status)
        }
        // Nine sheets' "Poverty: Struggling" IS the book's Struggling
        // ([Wealth.levelOf]), so it prints that row's cell and notes.
        val book = TraitReport.held(listOf(Trait("Wealth: Struggling", 10, TraitKind.DISADVANTAGE)), TraitKind.DISADVANTAGE).single()
        val sheet = TraitReport.held(listOf(Trait("Poverty: Struggling", 10, TraitKind.DISADVANTAGE)), TraitKind.DISADVANTAGE).single()
        assertEquals("Start with 15 soli; modest room; occasional luxuries", sheet.cell)
        assertEquals(book.cell, sheet.cell)
        assertEquals(book.notes.map { it.printed to it.reason }, sheet.notes.map { it.printed to it.reason })
        assertEquals(listOf("Poverty: Struggling"), sheet.notes.map { it.trait })
        // The tenth charges 5, so it names no level and stays recorded (4.1).
        val librarian = Premades.byName("University Librarian").toCharacter().traits
        assertNull(TraitReport.held(librarian, TraitKind.DISADVANTAGE).single { it.shown == "Poverty: Struggling" }.cell)
    }
}
