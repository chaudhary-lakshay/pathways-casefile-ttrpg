package com.pathways.rules

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Asking the world a question (Ch. 6.5).
 *
 * The cases worth pinning are the ones a positional or margin-first reading
 * gets wrong: the cross-reference row that is not a modifier, the capped
 * support bonus, and the clarity table's two doors.
 */
class DivinationTest {

    private fun difficulty(prefix: String) =
        Divination.difficulties().first { it.band.startsWith(prefix) }

    private fun modifier(prefix: String) =
        Divination.modifiers().first { it.source.startsWith(prefix) }

    private fun fixed(face: Int) = RandomSource { face }

    @Test
    fun `the question's difficulty moves the target`() {
        assertEquals(2, difficulty("Trivial").modifier)
        assertEquals(-4, difficulty("Hard").modifier)
        assertEquals(14, Divination.targetFor(skill = 12, difficulty = difficulty("Trivial")))
        assertEquals(8, Divination.targetFor(skill = 12, difficulty = difficulty("Hard")))
    }

    @Test
    fun `the hardest band is a floor, not a value`() {
        // "-6 or worse", like the "100+ meters" range band: the printed number
        // is where it starts, not where it stops.
        val extreme = difficulty("Extreme")
        assertEquals(-6, extreme.modifier)
        assertTrue(extreme.openEnded)
        assertEquals(1, Divination.difficulties().count { it.openEnded })
    }

    @Test
    fun `the difficulty row is refused as a modifier, because it is Step 1`() {
        // "Question difficulty / See Step 1 table" is a pointer, not a value.
        // Applying it as zero would silently drop the whole of Step 1; adding
        // it beside targetFor would charge it twice.
        val row = modifier("Question difficulty")
        assertTrue(row.crossReference)
        val ex = assertThrows<IllegalArgumentException> { Divination.modifierTotal(listOf(row)) }
        assertTrue(ex.message!!.contains("Step 1"))
    }

    @Test
    fun `a modifier that prints a range takes the GM's number, inside it`() {
        val link = modifier("Target link")
        assertEquals(0, link.modifier)
        assertEquals(4, link.best)
        assertEquals(4, Divination.modifierTotal(listOf(link), mapOf(link.source to 4)))
        assertThrows<IllegalArgumentException> {
            Divination.modifierTotal(listOf(link), mapOf(link.source to 5))
        }
    }

    @Test
    fun `a negative range is bounded at both ends too`() {
        // "-1 to -2": the harsher end is the LOWER bound, so an in-range check
        // written for positive rows would reject every legal value here.
        val timing = modifier("Unfavourable timing")
        assertEquals(-1, timing.modifier)
        assertEquals(-2, timing.best)
        assertEquals(-2, Divination.modifierTotal(listOf(timing), mapOf(timing.source to -2)))
        assertThrows<IllegalArgumentException> {
            Divination.modifierTotal(listOf(timing), mapOf(timing.source to -3))
        }
    }

    @Test
    fun `asking the same question again stacks against you`() {
        val repeat = modifier("Previous divination")
        assertTrue(repeat.perRepeat)
        assertEquals(-2, Divination.modifierTotal(listOf(repeat)))
        assertEquals(-6, Divination.modifierTotal(listOf(repeat), repeats = 3))
        assertThrows<IllegalArgumentException> {
            Divination.modifierTotal(listOf(repeat), repeats = 0)
        }
    }

    @Test
    fun `supporting skills cap at plus two however many you have`() {
        val all = Divination.supportingSkills()
        assertEquals(4, all.size, "four skills at +1 each would be +4 uncapped")
        assertEquals(Divination.MAX_SUPPORT_BONUS, Divination.supportBonus(all))
        assertEquals(1, Divination.supportBonus(all.take(1)))
        assertEquals(0, Divination.supportBonus(emptyList()))
    }

    @Test
    fun `clarity is read from the margin of a success`() {
        assertEquals("Crystal clear", Divination.clarityFor(SuccessRoll.evaluate(5, 12)).clarity)
        assertEquals("Clear", Divination.clarityFor(SuccessRoll.evaluate(9, 12)).clarity)
        assertEquals("Vague", Divination.clarityFor(SuccessRoll.evaluate(11, 12)).clarity)
        assertEquals(6, Divination.clarities().size)
    }

    @Test
    fun `a bad miss is distorted, not merely murky`() {
        assertEquals("Murky", Divination.clarityFor(SuccessRoll.evaluate(13, 12)).clarity)
        assertEquals("Distorted", Divination.clarityFor(SuccessRoll.evaluate(16, 11)).clarity)
    }

    @Test
    fun `a critical failure is read as an outcome, not as a margin`() {
        // A 17 against 18 has a POSITIVE margin and is still critical. Reading
        // the margin first would report the book's own backlash row as a
        // success -- the slip FrightCheck and RitualResolution each made.
        val roll = SuccessRoll.evaluate(total = 17, target = 18)
        assertTrue(roll.margin > 0)
        val clarity = Divination.clarityFor(roll)
        assertTrue(clarity.isCritical)
        assertTrue(clarity.effect.contains("Critical Failure Table"))
    }

    @Test
    fun `a critical success is read by its margin, because no row prints one`() {
        // The book gives critical FAILURE a row and critical success none.
        // A 4 against a target of 5 is a critical success by margin 1, and
        // "Vague" is what the table actually says about that.
        val roll = SuccessRoll.evaluate(total = 4, target = 5)
        val clarity = Divination.clarityFor(roll)
        assertEquals("Vague", clarity.clarity)
        assertFalse(clarity.isCritical)
    }

    @Test
    fun `burning SPI raises the number you roll under`() {
        val trivial = difficulty("Trivial")
        val plain = Divination.attempt(10, trivial, fixed(4), burned = 0)
        val pushed = Divination.attempt(10, trivial, fixed(4), burned = 3)
        assertEquals(12, plain.roll.target)
        assertEquals(15, pushed.roll.target)
        assertEquals(3, pushed.spiBurned)
        assertThrows<IllegalArgumentException> {
            Divination.attempt(10, trivial, fixed(4), burned = -1)
        }
    }

    @Test
    fun `a method costs what it prints, plus anything burned`() {
        val pendulum = Divination.methodNamed("Pendulum")
        assertEquals(1, pendulum.spi)
        assertEquals("1 min", pendulum.time)
        assertEquals(4, Divination.spiFor(pendulum, burned = 3))
        assertThrows<IllegalArgumentException> { Divination.methodNamed("Tea Leaves") }
    }

    @Test
    fun `the one method with a range charges the end the GM picked`() {
        // REGRESSION (Codex): Dowsing Rods prints "1-2" and the screen showed
        // the range while always charging 1. SPI below zero is Soul Debt, and
        // that corruption is permanent, so undercharging is not a rounding
        // difference.
        val rods = Divination.methodNamed("Dowsing Rods")
        assertEquals(2, rods.spiMax)
        assertEquals(1, Divination.spiFor(rods))
        assertEquals(2, Divination.spiFor(rods, chosen = 2))
        assertThrows<IllegalArgumentException> { Divination.spiFor(rods, chosen = 3) }
        // A method with no range refuses anything but its printed cost.
        assertThrows<IllegalArgumentException> {
            Divination.spiFor(Divination.methodNamed("Pendulum"), chosen = 2)
        }
    }

    @Test
    fun `the overnight method survived being split mid-word`() {
        // The PDF cut "Overnight" into "Overnig" and "ht"; no cell-shaped rule
        // can see that, so the extractor joins on the times the book prints.
        assertEquals("Overnight", Divination.methodNamed("Dream Interpretation").time)
    }
}
