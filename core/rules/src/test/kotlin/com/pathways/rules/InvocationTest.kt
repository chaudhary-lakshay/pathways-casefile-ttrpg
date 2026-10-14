package com.pathways.rules

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Borrowing power from an entity (Ch. 7, Section IV.F, p. 66).
 *
 * The interesting cases are the direction of the orthodox shift, and the two
 * rows that print no modifier for two different reasons.
 */
class InvocationTest {

    private fun fixed(each: Int) = RandomSource { _ -> each }

    @Test
    fun `the six bands tile a 3d6 exactly`() {
        assertEquals(
            listOf(3 to 5, 6 to 8, 9 to 11, 12 to 14, 15 to 17, 18 to 18),
            Invocation.responses().map { it.low to it.high },
        )
        (3..18).forEach { Invocation.responseFor(it) }
    }

    @Test
    fun `a good response helps the ritual and a displeased one hurts it`() {
        assertEquals(5, Invocation.ritualModifier(Invocation.responseFor(3)))
        assertEquals(3, Invocation.ritualModifier(Invocation.responseFor(7)))
        assertEquals(1, Invocation.ritualModifier(Invocation.responseFor(10)))
        assertEquals(-2, Invocation.ritualModifier(Invocation.responseFor(16)))
    }

    @Test
    fun `an indifferent entity adds nothing rather than failing the ritual`() {
        // "No response. Ritual draws only on caster's residual power." The
        // ritual still happens; it just gets no help.
        val row = Invocation.responseFor(13)
        assertNull(row.ritualModifier, "the book prints no number here")
        assertFalse(row.automaticFailure)
        assertEquals(0, Invocation.ritualModifier(row), "unaided is not penalised")
    }

    @Test
    fun `an 18 is a refusal, not a penalty`() {
        // "Active rejection. Ritual fails automatically." Same shape as a
        // Broken mystical language: the attempt does not happen, and asking
        // for its modifier is a caller bug rather than a large negative.
        val row = Invocation.responseFor(18)
        assertTrue(row.automaticFailure)
        assertNull(row.ritualModifier)
        val ex = assertThrows<IllegalArgumentException> { Invocation.ritualModifier(row) }
        assertTrue(ex.message!!.contains("automatically"))
    }

    @Test
    fun `the orthodox shift moves the TABLE roll, and it moves it toward favour`() {
        // The trap. "Orthodox deities shift the table result by -2" -- on the
        // GM's 3d6, not on the ritual roll. This table is ordered best-first,
        // so subtracting 2 walks the caster UP toward Full response. Applied
        // to the ritual roll it would penalise the caster in good standing.
        assertEquals(-2, Invocation.ORTHODOX_SHIFT)
        assertEquals(6, Invocation.shiftedRoll(8, orthodoxInGoodStanding = true))

        // A 9 is Minimal (+1); shifted to 7 it is Partial (+3). Two points of
        // ritual roll bought by standing, in the right direction.
        assertEquals(1, Invocation.ritualModifier(Invocation.responseFor(9)))
        assertEquals(3, Invocation.ritualModifier(Invocation.responseFor(7)))

        // The property that matters, over every roll rather than one example:
        // favour never moves a caster to a WORSE band. The bands are ordered
        // best-first, so a shifted band's index can only fall. (Modifiers are
        // not compared directly because the 18 row has none to compare.)
        val bands = Invocation.responses()
        (3..18).forEach { raw ->
            val shifted = Invocation.shiftedRoll(raw, orthodoxInGoodStanding = true)
            assertTrue(
                bands.indexOf(Invocation.responseFor(shifted)) <=
                    bands.indexOf(Invocation.responseFor(raw)),
                "a favoured caster is never worse off; $raw became $shifted",
            )
        }
    }

    @Test
    fun `hidden existences and evil gods use the table unmodified`() {
        assertEquals(11, Invocation.shiftedRoll(11, orthodoxInGoodStanding = false))
    }

    @Test
    fun `the shift cannot walk the roll off the top of the table`() {
        // A rolled 3 is already the best row; shifting it to 1 would land on
        // no row at all. The table's own floor holds.
        assertEquals(3, Invocation.shiftedRoll(3, orthodoxInGoodStanding = true))
        assertEquals(3, Invocation.shiftedRoll(4, orthodoxInGoodStanding = true))
    }

    @Test
    fun `a roll outside 3 to 18 is refused rather than clamped`() {
        assertThrows<IllegalArgumentException> { Invocation.responseFor(2) }
        assertThrows<IllegalArgumentException> { Invocation.responseFor(19) }
    }

    @Test
    fun `asking an entity rolls the table and reports what it gave`() {
        // Three 2s is a 6: Partial response, +3.
        val answer = Invocation.ask(fixed(2), orthodoxInGoodStanding = false)
        assertEquals(6, answer.roll)
        assertEquals(6, answer.shifted)
        assertEquals(3, answer.ritualModifier)
        assertFalse(answer.failsRitual)
    }

    @Test
    fun `good standing is applied before the row is read`() {
        // Three 3s is a 9 -- Minimal response, +1 -- but a favoured caster
        // reads row 7, which is Partial and +3. Reading the row first and
        // shifting afterwards would give the wrong row entirely.
        val plain = Invocation.ask(fixed(3), orthodoxInGoodStanding = false)
        val favoured = Invocation.ask(fixed(3), orthodoxInGoodStanding = true)
        assertEquals(9 to 1, plain.shifted to plain.ritualModifier)
        assertEquals(7 to 3, favoured.shifted to favoured.ritualModifier)
    }

    @Test
    fun `an unanswered invocation costs the printed 2 SPI, not the ritual's own`() {
        // Three 4s is a 12: No response. A Heavy ritual declared at 8 SPI is
        // charged 2, because that row prints its own price. The app spent the
        // declared cost on every row until this was applied, so an unanswered
        // Heavy invocation took 8+ where the book prints 2 -- and on a caster
        // with no pool the excess bought permanent Soul Debt CoR.
        val answer = Invocation.ask(fixed(4), orthodoxInGoodStanding = false)
        assertEquals(12, answer.shifted)
        assertEquals(0, answer.ritualModifier)
        assertEquals(Fuel(Invocation.RESIDUAL_SPI, 0), Invocation.fuelFor(answer, cost = 8, hpSacrificed = 0))
        assertEquals(2, Invocation.RESIDUAL_SPI)
    }

    @Test
    fun `a row that answers keeps the ritual's own cost`() {
        // The book prices none of the five answering rows, so they are left
        // alone. Reading "you borrow power from someone higher" as "those rows
        // are free" would author a price on five rows the book never prices.
        // Faces 1, 2, 3 and 5 roll 3, 6, 9 and 15.
        listOf(1, 2, 3, 5).forEach { face ->
            val answer = Invocation.ask(fixed(face), orthodoxInGoodStanding = false)
            assertEquals(Fuel(8, 0), Invocation.fuelFor(answer, cost = 8, hpSacrificed = 0), "face $face")
            // 4 HP covers 2 of the 8, and is charged beside the 6 SPI.
            assertEquals(Fuel(6, 4), Invocation.fuelFor(answer, cost = 8, hpSacrificed = 4), "face $face")
        }
    }

    @Test
    fun `the best outcome costs the most, and that disagreement is recorded`() {
        // A Full response charges a Heavy ritual's whole 8 while No response
        // charges 2. The book prints both and reconciles neither. This test
        // exists so a later reading cannot quietly straighten it.
        val full = Invocation.fuelFor(Invocation.ask(fixed(1), false), cost = 8, hpSacrificed = 0)
        val none = Invocation.fuelFor(Invocation.ask(fixed(4), false), cost = 8, hpSacrificed = 0)
        assertTrue(full.spi > none.spi, "the printed prices really do run backwards")
    }

    @Test
    fun `no response takes its 2 SPI out of the fuel declared, HP first`() {
        // The owner's ruling (6hq for a prayer, 6hr for a ritual): a mortal
        // plays SPI 0 and declares the fuel in blood, so the residual 2 is
        // paid the way the fuel was, 2 HP for 1 SPI, and the rest is dropped.
        val silent = Invocation.ask(fixed(4), orthodoxInGoodStanding = false)
        assertEquals(Fuel(2, 0), Invocation.fuelFor(silent, cost = 4, hpSacrificed = 0))
        assertEquals(Fuel(1, 2), Invocation.fuelFor(silent, cost = 4, hpSacrificed = 2))
        assertEquals(Fuel(0, 4), Invocation.fuelFor(silent, cost = 4, hpSacrificed = 8))
    }

    @Test
    fun `the 2 SPI is read from the row's own sentence`() {
        // The number lives in Kotlin because it is inside prose; this pins the
        // sentence it came from, so a re-extraction that dropped the
        // parenthetical fails here rather than leaving RESIDUAL_SPI floating.
        val row = Invocation.responseFor(12)
        assertTrue(
            row.response.contains("treat as ${Invocation.RESIDUAL_SPI} SPI personal"),
            "the 12-14 row no longer prints its own price: ${row.response}",
        )
        assertTrue(row.response.contains("residual power"))
    }

    @Test
    fun `a rejected ritual has no fuel to spend`() {
        // The ritual fails automatically and is never rolled, so it charges
        // nothing: a caller asked for the fuel at the asking cannot be
        // charged for a roll that will not happen.
        val rejection = Invocation.ask(fixed(6), orthodoxInGoodStanding = false)
        assertEquals(Fuel(0, 0), Invocation.fuelFor(rejection, cost = 8, hpSacrificed = 4))
    }

    @Test
    fun `a rejected ritual reports zero modifier and its own refusal`() {
        // Three 6s is an 18. The modifier is 0 because there is nothing to
        // add -- the failure is carried by failsRitual, not smuggled into a
        // number a caller might add to a target and then roll against.
        val answer = Invocation.ask(fixed(6), orthodoxInGoodStanding = false)
        assertTrue(answer.failsRitual)
        assertEquals(0, answer.ritualModifier)
    }
}
