package com.pathways.rules

import com.pathways.model.Attr
import com.pathways.model.ControllingAttribute
import com.pathways.model.Difficulty
import com.pathways.model.Outcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Asking the world a question (Ch. 6.5).
 *
 * The cases worth pinning are the ones a positional or margin-first reading
 * gets wrong: the cross-reference row that is not a modifier, and the
 * clarity table's two doors.
 */
class DivinationTest {

    private fun difficulty(prefix: String) =
        Divination.difficulties().first { it.band.startsWith(prefix) }

    private fun modifier(prefix: String) =
        Divination.modifiers().first { it.source.startsWith(prefix) }

    private fun fixed(face: Int) = RandomSource { face }

    /**
     * v6.9i ch. 8 I: "Divination Arts (IQ/Hard)" and "Default: Divination
     * Arts defaults to SPI-4 (unskilled)." The skill's own line beats the
     * IQ/Hard table's IQ-6, and Ritualistic Magic lends it nothing: "a
     * Beyonder cannot substitute one for the other".
     */
    @Test
    fun `Divination Arts defaults to SPI minus four`() {
        val unskilled = Divination.divinationLevelFor(own = null, spi = 12, defaultBonus = 0)
        assertEquals(8, unskilled.level)
        assertTrue(unskilled.isDefault)
        // A mortal plays SPI 0 and may still ask, at -4.
        assertEquals(-4, Divination.divinationLevelFor(own = null, spi = 0, defaultBonus = 0).level)
        assertEquals(-4, Divination.DEFAULT_PENALTY)
    }

    @Test
    fun `the better of the owned skill and the default wins, a tie keeping the skill`() {
        val owned = Divination.divinationLevelFor(own = 11, spi = 12, defaultBonus = 0)
        assertEquals(11, owned.level)
        assertFalse(owned.isDefault)
        val lent = Divination.divinationLevelFor(own = 7, spi = 14, defaultBonus = 0)
        assertEquals(10, lent.level)
        assertTrue(lent.isDefault)
        assertEquals(7, lent.own)
        val tied = Divination.divinationLevelFor(own = 8, spi = 12, defaultBonus = 0)
        assertFalse(tied.isDefault)
    }

    @Test
    fun `the two defaulting cases say different things`() {
        val hasNone = Divination.sourceLine(Divination.divinationLevelFor(null, spi = 14, defaultBonus = 0))!!
        assertTrue("SPI 14" in hasNone && "no ${Divination.SKILL} of their own" in hasNone, hasNone)
        val hasWorse = Divination.sourceLine(Divination.divinationLevelFor(7, spi = 14, defaultBonus = 0))!!
        assertTrue("${Divination.SKILL} 7" in hasWorse, hasWorse)
        assertNull(Divination.sourceLine(Divination.divinationLevelFor(12, spi = 10, defaultBonus = 0)))
    }

    /**
     * Spiritual Attrition is "-3 to all SPI-based skill rolls", and v6.9i
     * prints no SPI-controlled skill at all: Divination Arts is IQ/Hard. The
     * SPI-4 default is the one roll here made OFF SPI, so it is SPI-based and
     * the owned IQ skill is not. A reading of "SPI-based", recorded as one.
     */
    @Test
    fun `only the SPI default is an SPI-based roll`() {
        val defaulted = Divination.keyFor(Divination.divinationLevelFor(null, spi = 12, defaultBonus = 0))
        val owned = Divination.keyFor(Divination.divinationLevelFor(12, spi = 12, defaultBonus = 0))
        assertEquals(ControllingAttribute.Spi, defaulted.attr)
        assertEquals(ControllingAttribute.Buyable(Attr.IQ), owned.attr)
        assertEquals(Divination.SKILL, owned.name)
        val drained = Pools.spi(current = 2, max = 12)
        val ok = Pools.hp(10, 10)
        val fp = Pools.fp(10, 10)
        assertTrue(Conditions.forSkill(defaulted, ok, fp, drained).isNotEmpty())
        assertTrue(Conditions.forSkill(owned, ok, fp, drained).isEmpty())
    }

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
    fun `clarity is read from the margin of a success`() {
        assertEquals("Crystal clear", Divination.clarityFor(SuccessRoll.evaluate(5, 12)).clarity)
        assertEquals("Clear", Divination.clarityFor(SuccessRoll.evaluate(9, 12)).clarity)
        assertEquals("Vague", Divination.clarityFor(SuccessRoll.evaluate(11, 12)).clarity)
        assertEquals(6, Divination.clarities().size)
    }

    /**
     * **The join, as a general invariant rather than five spot checks.**
     * `clarityFor` used to hold 5 and 3 as Kotlin literals and reach its rows
     * by array position -- the only margin-reader in this codebase that did,
     * where `MajorWound.failBy`, `RitualFailure.from` and
     * `SpiritualSenseRow.margin` all carry the number ON the row. Both sides
     * were pinned -- the extractor pins the printed strings, the tests above
     * pin the behaviour -- and NOTHING JOINED THEM: re-read a band as "Success
     * by 4+" and the extractor's pin would fail loudly while Kotlin's `>= 5`
     * sat there silently, selecting a row whose own text contradicted it.
     *
     * This walks every value of every printed band and asserts the row the
     * engine picks is the row whose cell names that margin -- the general
     * guard 6ag's parts-sum test is, rather than three patches.
     */
    @Test
    fun `every band selects the row whose own printed cell names it`() {
        val band = Regex("""^(Success|Failure) by (\d+)(?:[–-](\d+))?""")
        val rows = Divination.clarities().filterNot { it.isCritical }
        assertEquals(5, rows.size, "five rows are named by margin, one by outcome")

        rows.forEach { row ->
            val m = band.find(row.result) ?: error("unparsed Result cell: ${row.result}")
            val isFailure = m.groupValues[1] == "Failure"
            val lower = m.groupValues[2].toInt()
            assertEquals(isFailure, row.isFailure, "${row.result} sits on the wrong side")
            assertEquals(lower, row.margin, "${row.result} parsed the wrong bound")

            val target = if (isFailure) 8 else 12
            // An open end ("5+") is walked four steps past its printed bound.
            // On the failure side it stops before a rolled 17, where the
            // book's own always-fails rule takes the roll critical and this
            // table's outcome-first door opens instead -- the deepest margin
            // band is simply unreachable past there.
            val printedUpper = m.groupValues[3].takeIf { it.isNotEmpty() }?.toInt()
            val reachable =
                if (isFailure) SuccessRoll.ALWAYS_FAILS_AT_OR_OVER - 1 - target else lower + 4
            val upper = printedUpper ?: minOf(lower + 4, reachable)
            (lower..upper).forEach { by ->
                val total = if (isFailure) target + by else target - by
                val roll = SuccessRoll.evaluate(total, target)
                assertEquals(
                    row.clarity,
                    Divination.clarityFor(roll).clarity,
                    "a margin of $by should land on ${row.result}",
                )
            }
        }
    }

    @Test
    fun `the row named by outcome prints no margin at all`() {
        // Printing no number is not printing zero -- at zero it would be the
        // mildest success band and could outrank the row that really is.
        val critical = Divination.clarities().first { it.isCritical }
        assertNull(critical.margin)
        assertFalse(critical.isFailure)
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

    /**
     * v6.9i line 3671: "Burning SPI to improve the roll: As with rituals, you
     * may spend additional SPI (1 SPI = +1, no cap) before rolling." v6.9d
     * said "after seeing your result"; the push that re-read cast dice is gone.
     */
    @Test
    fun `SPI burned before rolling raises the number you roll under`() {
        val trivial = difficulty("Trivial")
        assertEquals(12, Divination.targetFor(10, trivial))
        assertEquals(15, Divination.targetFor(10, trivial, burned = 3))
        assertThrows<IllegalArgumentException> { Divination.targetFor(10, trivial, burned = -1) }
        // 12 against a target of 12 is a bare success; 5 burned beforehand
        // makes the same dice a margin of 5, read on the clarity table.
        val burned = Divination.attempt(10, trivial, fixed(4), burned = 5)
        assertEquals(5, burned.roll.margin)
        assertEquals("Crystal clear", burned.clarity.clarity)
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

    /**
     * The unapplied rule is named, and the bonus it withholds is the book's.
     *
     * A guard on discoverability rather than on behaviour, and the KDoc on
     * [Divination.UNAPPLIED] says so: nothing here fails if the bonus is later
     * wired up. What it does catch is the entry drifting from the constant it
     * explains -- the pairing 6cq found held together by a comment alone.
     */
    @Test
    fun `the specialisation bonus is recorded as unapplied, with its reason`() {
        assertEquals(setOf("Specialisation"), Divination.UNAPPLIED.keys)
        assertEquals(1, Divination.SPECIALISATION_BONUS)
        assertTrue(
            "technique" in Divination.UNAPPLIED.getValue("Specialisation"),
            "the reason no longer names what the book prices it as",
        )
    }

    /**
     * A screen that prints "3d6 <= N" and then rolls must roll against N.
     * Composing N a second time inside the roll is two call sites answering
     * one question (6eg, 6eh), so the roll takes the composed target and
     * the older entry point is that roll with the target composed first.
     */
    @Test
    fun `a roll against a composed target is the attempt that composed it`() {
        val band = Divination.difficulties().first()
        val target = Divination.targetFor(12, band, extras = -3)
        val rolled = Divination.rollAgainst(target, fixed(3))
        assertEquals(target, rolled.roll.target, "judged against the target it was handed")
        assertEquals(Divination.attempt(12, band, fixed(3), extras = -3), rolled)
        // Sequence Immunity's critical-only rule rides with the roll, not
        // with the inputs that composed the number.
        assertEquals(
            Divination.attempt(12, band, fixed(3), extras = -3, requiresCritical = true),
            Divination.rollAgainst(target, fixed(3), requiresCritical = true),
        )
        assertNotEquals(rolled, Divination.rollAgainst(target, fixed(3), requiresCritical = true))
    }

    // ---- Versatile, on the one roll this app makes at a default -----------

    /**
     * "+1 to defaults skill rolls -- any time you use a skill at default, you
     * are effectively one level better" (Versatile). SPI-4 is a default in
     * the book's own word, so the bonus is counted INSIDE the pick: own 8
     * against SPI 12 is a tie the skill keeps, and with Versatile the
     * default is 9 and wins.
     */
    @Test
    fun `a bonus to defaults is counted before the better number is picked`() {
        assertFalse(Divination.divinationLevelFor(own = 8, spi = 12, defaultBonus = 0).isDefault)
        val versatile = Divination.divinationLevelFor(own = 8, spi = 12, defaultBonus = 1)
        assertEquals(9, versatile.level)
        assertTrue(versatile.isDefault)
        val line = Divination.sourceLine(versatile)!!
        assertTrue(line.contains("SPI 12") && line.contains("+1") && line.contains(TraitEffects.VERSATILE), line)
        val bare = Divination.sourceLine(Divination.divinationLevelFor(null, spi = 12, defaultBonus = 0))!!
        assertFalse(bare.contains(TraitEffects.VERSATILE), bare)
    }
}
