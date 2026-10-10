package com.pathways.rules

import com.pathways.model.Attributes
import com.pathways.model.SecondaryDelta
import com.pathways.model.Trait
import com.pathways.model.TraitKind
import com.pathways.rules.content.ContentLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * The Appearance ladder (Ch. 3, p. 13-14).
 *
 * The interesting cases are all about what this table does NOT share with
 * [Wealth], which it otherwise looks exactly like: chapter 3's own trait lists
 * print none of these levels, there is no zero-cost middle row, and the
 * reaction column cannot be derived from the cost.
 */
class AppearanceTest {

    @Test
    fun `the eight printed levels, best first`() {
        assertEquals(
            listOf(
                "Attractive",
                "Handsome / Beautiful",
                "Very Handsome / Very Beautiful",
                "Unattractive",
                "Ugly",
                "Hideous",
                "Monstrous",
                "Horrific",
            ),
            Appearance.all().map { it.level },
        )
    }

    @Test
    fun `the book prints no ordinary level, so ordinary is the absence of a trait`() {
        // Wealth prints Average at 0 pts, so choosing it is a choice that bills
        // nothing. This table prints no such row: the cheapest thing it sells
        // is Attractive at 1 point. Ordinary looks are therefore not a level at
        // all, and every level here produces a trait -- traitFor never answers
        // null the way Wealth.traitFor does for Average.
        assertTrue(Appearance.all().none { it.points == 0 }, "no level is free")
        assertNull(Appearance.levelOf(emptyList()), "no trait means no level, not a middle one")
    }

    @Test
    fun `the sign lives in the kind, and this table is the place the two meet`() {
        val attractive = Appearance.traitFor(Appearance.named("Attractive"))
        assertEquals(Trait("Attractive", 1, TraitKind.ADVANTAGE), attractive)

        // -24 pts becomes a magnitude of 24 with the direction in the kind.
        // Carrying the book's sign through as well would pay it twice.
        val horrific = Appearance.traitFor(Appearance.named("Horrific"))
        assertEquals(Trait("Horrific", 24, TraitKind.DISADVANTAGE), horrific)
    }

    @Test
    fun `the reaction modifier is not derivable from the cost`() {
        // If it were, the second column would be redundant and this test would
        // be measuring arithmetic. It is not: the good half doubles its cost
        // per step (1/4/8) while its modifier climbs by one, and the bad half
        // skips -12 entirely, so -8 pts gives -2 and -16 gives -4.
        fun cost(level: String) = Appearance.named(level).points
        fun reaction(level: String) = Appearance.named(level).reactionModifier

        assertEquals(1 to 1, cost("Attractive") to reaction("Attractive"))
        assertEquals(4 to 2, cost("Handsome / Beautiful") to reaction("Handsome / Beautiful"))
        assertEquals(-8 to -2, cost("Ugly") to reaction("Ugly"))
        assertEquals(-16 to -4, cost("Hideous") to reaction("Hideous"))
    }

    @Test
    fun `chapter 3's trait lists print none of these levels`() {
        // Wealth was half-printed there, which is what made "Wealth: Poor" the
        // spelling to match on. Nothing here is printed there at all, so the
        // trait carries the level's own name and nothing else -- and this
        // disjointness is what makes that name unambiguous.
        val printed = (ContentLoader.advantages() + ContentLoader.disadvantages()).map { it.name }
        Appearance.all().forEach {
            assertFalse(it.level in printed, "${it.level} is now a chapter 3 trait too")
        }
    }

    @Test
    fun `Unsettling Appearance is a potion's doing, not a level`() {
        // The one chapter 3 trait whose name says "Appearance": 4 points, -1 to
        // all reaction rolls, "a common consequence of Abyss and Chained
        // Pathway potions". Treating it as this table's ninth row would price a
        // disfigurement suffered in play as a choice made at creation.
        assertThrows<IllegalArgumentException> { Appearance.named("Unsettling Appearance") }
        assertNull(
            Appearance.levelOf(listOf(Trait("Unsettling Appearance", 4, TraitKind.DISADVANTAGE))),
        )
    }

    @Test
    fun `an unprinted level is refused, not defaulted`() {
        // Answering the cheapest level for a name the book does not print would
        // hand back a character worth up to 25 points less than the caller
        // asked for, silently.
        val ex = assertThrows<IllegalArgumentException> { Appearance.named("Comely") }
        assertTrue(ex.message!!.contains("Comely"))
    }

    @Test
    fun `changing appearance replaces the level rather than adding one`() {
        // A character has one face. Ugly left beside Attractive would refund 8
        // points and spend 1 in the same breath, and both totals would still
        // look plausible.
        val ugly = listOf(Trait("Ugly", 8, TraitKind.DISADVANTAGE))
        val handsome = Appearance.applied(ugly, Appearance.named("Handsome / Beautiful"))

        assertEquals(listOf("Handsome / Beautiful"), handsome.map { it.name })
        assertEquals("Handsome / Beautiful", Appearance.levelOf(handsome)!!.level)
    }

    @Test
    fun `unrelated traits survive the swap`() {
        val traits = listOf(
            Trait("Wealth: Poor", 15, TraitKind.DISADVANTAGE),
            Trait("Ugly", 8, TraitKind.DISADVANTAGE),
        )
        val swapped = Appearance.applied(traits, Appearance.named("Attractive"))
        assertEquals(listOf("Wealth: Poor", "Attractive"), swapped.map { it.name })
    }

    @Test
    fun `the budget answers back on both sides of the swap`() {
        fun report(traits: List<Trait>) = PointBudget.report(
            Attributes(), SecondaryDelta(), traits, emptyList(),
        )

        val ugly = listOf(Trait("Ugly", 8, TraitKind.DISADVANTAGE))
        val handsome = Appearance.applied(ugly, Appearance.named("Handsome / Beautiful"))

        // Ugly hands the budget 8 points; Handsome spends 4 of the base 70.
        assertEquals(PointBudget.STARTING_TOTAL + 8, report(ugly).budget)
        assertEquals(0, report(ugly).spent)
        assertEquals(PointBudget.STARTING_TOTAL, report(handsome).budget)
        assertEquals(4, report(handsome).spent)
    }

    /**
     * A character has one face, and the better one is played (6fn): a potion
     * that "perfects the Witch's features" never lowers a bought face, so the
     * comparison is the reaction column, the one thing the rows order on.
     */
    @Test
    fun `the better face is the one played`() {
        fun face(level: String) = Appearance.traitFor(Appearance.named(level))
        val ugly = face("Ugly")
        val beautiful = face("Handsome / Beautiful")
        val very = face("Very Handsome / Very Beautiful")
        assertNull(Appearance.played(listOf(Trait("Luck", 15, TraitKind.ADVANTAGE))))
        assertEquals("Handsome / Beautiful", Appearance.played(listOf(ugly, beautiful))!!.level)
        assertEquals("Very Handsome / Very Beautiful", Appearance.played(listOf(very, beautiful))!!.level)
        assertEquals("Ugly", Appearance.played(listOf(ugly))!!.level)
        // The face held and not played, which the Traits tab says of it.
        assertTrue(Appearance.isSuperseded(ugly, listOf(ugly, beautiful)))
        assertFalse(Appearance.isSuperseded(beautiful, listOf(ugly, beautiful)))
        assertFalse(Appearance.isSuperseded(ugly, listOf(ugly)))
        // Matched as played() matches: a face spelt in another case is the same face.
        assertFalse(Appearance.isSuperseded(ugly.copy(name = "ugly"), listOf(ugly.copy(name = "ugly"))))
        assertFalse(Appearance.isSuperseded(Trait("Luck", 15, TraitKind.ADVANTAGE), listOf(ugly, beautiful)))
    }

    /**
     * A face has no catalogue row, so the Traits tab printed its name alone
     * (6fq). Its cell is the table's own reaction column, worded as the book
     * words every row ("-2 to reaction rolls", 722-743), and nothing applies
     * it, so it carries [TraitEffects.UNREAD] like any row nothing reads.
     */
    @Test
    fun `a held face prints its reaction cell and says it is not applied`() {
        // The book's own column, copied from lines 722-743, never computed.
        val printed = listOf(
            "+1 to reaction rolls", "+2 to reaction rolls", "+3 to reaction rolls",
            "-1 to reaction rolls", "-2 to reaction rolls", "-4 to reaction rolls",
            "-5 to reaction rolls", "-6 to reaction rolls",
        )
        assertEquals(printed.size, Appearance.all().size)
        for ((level, cell) in Appearance.all().zip(printed)) {
            val face = Appearance.traitFor(level)
            val held = TraitEffects.held(listOf(face), face.kind).single()
            assertEquals(cell, held.cell, level.level)
            assertEquals(listOf(TraitEffects.UNREAD), held.notes.map { it.reason }, level.level)
        }
        // A catalogue row is untouched.
        val luck = TraitEffects.held(listOf(Trait("Luck", 15, TraitKind.ADVANTAGE)), TraitKind.ADVANTAGE).single()
        assertTrue(luck.cell!!.isNotBlank())
    }
}
