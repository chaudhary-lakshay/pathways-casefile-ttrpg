package com.pathways.rules

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
 * gets wrong: the cross-reference row that is not a modifier, the capped
 * support bonus, and the clarity table's two doors.
 */
class DivinationTest {

    private fun difficulty(prefix: String) =
        Divination.difficulties().first { it.band.startsWith(prefix) }

    private fun modifier(prefix: String) =
        Divination.modifiers().first { it.source.startsWith(prefix) }

    private fun fixed(face: Int) = RandomSource { face }

    /**
     * "Divination Arts defaults to Ritualistic Magic-2 and vice versa. A
     * character with one skill at 12+ can attempt the other at -2." (p. 54)
     *
     * The point of the whole slice: 25 potion blocks grant Ritualistic Magic
     * and 5 grant Divination Arts, so most casters this app can produce hold
     * the sibling and not the skill.
     */
    @Test
    fun `the sibling skill grants a default of its level minus two`() {
        assertEquals(-2, Divination.CROSS_DEFAULT_PENALTY)
        assertEquals(10, Divination.crossDefaultFor(12))
        assertEquals(12, Divination.crossDefaultFor(14))
    }

    @Test
    fun `the twelve is a gate, and below it the book grants nothing`() {
        assertEquals(12, Divination.CROSS_DEFAULT_THRESHOLD)
        assertNull(Divination.crossDefaultFor(11))
        assertNull(Divination.crossDefaultFor(0))
        // The boundary is >=, not >: the sentence says "at 12+".
        assertEquals(10, Divination.crossDefaultFor(12))
    }

    /**
     * Null is a refusal, not a fallback. A caster with the sibling at 11 is
     * not a caster with no skill at all, and answering the general SPI/Hard
     * default for them would settle a question the book left open.
     */
    @Test
    fun `the printed default beats the general one, and is not it`() {
        val general = SkillLadder.defaultFor(Difficulty.HARD, attributeLevel = 12)
        assertEquals(6, general, "SPI/Hard defaults at -6 for someone with neither skill")
        assertEquals(
            10,
            Divination.crossDefaultFor(12),
            "and the sibling skill prints -2, which is four better",
        )
        // Specific beats general only where the specific rule speaks -- so
        // below the gate it does NOT fall back to the general answer.
        assertNull(Divination.crossDefaultFor(11))
    }

    @Test
    fun `the default runs one way, because ch 7 forbids the other`() {
        assertEquals(Conditions.RITUAL_SKILL, Divination.SIBLING_SKILL)
        assertEquals(11, Divination.crossDefaultFor(13))
    }

    /**
     * **Ch. 6.5's "and vice versa" loses to ch. 7 II p. 61**: "Ritualistic
     * Magic (IQ/Very Hard) -- your core skill. It has no default and cannot
     * be substituted."
     *
     * The warrant is not that ch. 7 defines the skill. "cannot be
     * substituted" appears nowhere else in this book, so it is emphasis about
     * this skill rather than boilerplate under every Very Hard blurb; and
     * ch. 2 p. 10 agrees independently, "Very Hard skills ... have no default
     * at all", which is why [Difficulty.VERY_HARD] has carried a null default
     * since M0. Two places against one trailing clause -- the clause loses
     * (4.1), the way ch. 3's own "+9" ceiling lost to chapter 9's data (6ao).
     *
     * Nor is the asymmetry arbitrary. The direction that survives is exactly
     * the one whose difficulty permits a default at all: Divination Arts is
     * SPI/Hard and defaults at -6 generally, which p. 54 improves to -2.
     *
     * The case below is reachable from the app: Ritualistic Magic bought at
     * one point is level 7 on IQ 10 (Very Hard), and a Fool Sequence 9 potion
     * grants Divination Arts at SPI+3 = 12. Before this was read, that caster
     * rolled their rituals at 10.
     */
    @Test
    fun `a ritual is never rolled at the diviner's default`() {
        val caster = Divination.levelsFor(ownDivination = 12, ownRitual = 7, defaultBonus = 0)
        assertEquals(
            7, caster.ritual!!.level,
            "their own Ritualistic Magic, not Divination Arts 12 lending 10",
        )
        assertFalse(caster.ritual!!.isDefault)
        // And the same caster's DIVINATION does default, from the same pair
        // of numbers. One call, two chapters, and they disagree.
        assertEquals(12, caster.divination!!.level)
    }

    /**
     * The gate half of the same rule: Divination Arts at any level opens no
     * Ritual button. A refusal, not a fallback to the general Very Hard
     * "no default at all" -- both answer null, and only one of them is a
     * number this app could have printed.
     */
    @Test
    fun `Divination Arts lends a ritual nothing, at any level`() {
        assertNull(Divination.levelsFor(ownDivination = 20, ownRitual = null, defaultBonus = 0).ritual)
        // The arithmetic still exists and still answers, which is what shows
        // the refusal is a scoping decision rather than a missing feature.
        assertEquals(18, Divination.crossDefaultFor(20))
    }

    /**
     * The general form, because three spot checks pass for a great many wrong
     * implementations. Across the whole grid a ritual level is a function of
     * the caster's own Ritualistic Magic ALONE -- the sibling cannot reach
     * it -- while the divination level demonstrably moves with the sibling.
     *
     * The second half is what stops this passing vacuously: an engine that
     * refused both directions would satisfy the first assertion perfectly.
     */
    @Test
    fun `the sibling reaches divination and never a ritual`() {
        var siblingMoved = 0
        for (own in 1..20) {
            for (sibling in 1..20) {
                val caster = Divination.levelsFor(ownDivination = own, ownRitual = sibling, defaultBonus = 0)
                assertEquals(
                    sibling, caster.ritual!!.level,
                    "Divination Arts $own must not reach a ritual rolled at $sibling",
                )
                if (caster.divination!!.level != own) siblingMoved++
            }
        }
        assertTrue(siblingMoved > 0, "the divination half must still take the default")
    }

    /**
     * **Owning the skill is not enough to settle which number to roll**
     * (Codex). The sentence conditions on the SOURCE skill's level and says
     * nothing about lacking the target, so a caster who has both may still
     * default from the better one.
     *
     * The example is reachable from the book's own data: a Sequence 9 Hermit
     * is granted Ritualistic Magic at IQ+4 and Divination Arts at SPI+2, so
     * at IQ 10 / SPI 9 they own Divination Arts 11 while their Ritualistic
     * Magic 14 lends 12.
     */
    @Test
    fun `the better of the owned skill and the sibling's default wins`() {
        val hermit = Divination.divinationLevelFor(own = 11, sibling = 14, defaultBonus = 0)
        assertEquals(12, hermit!!.level, "Ritualistic Magic 14 lends 12, beating Divination 11")
        assertTrue(hermit.isDefault, "and the screen must say the number is not their own")
        // The other way round, the owned skill is simply better.
        val owned = Divination.divinationLevelFor(own = 15, sibling = 14, defaultBonus = 0)
        assertEquals(15, owned!!.level)
        assertFalse(owned.isDefault)
    }

    @Test
    fun `a tie keeps the owned skill, because the default adds nothing there`() {
        val tied = Divination.divinationLevelFor(own = 12, sibling = 14, defaultBonus = 0)
        assertEquals(12, tied!!.level)
        assertFalse(tied.isDefault, "same number; a caster who has the skill is shown as having it")
    }

    @Test
    fun `divinationLevelFor covers the three ways a caster can be short of a skill`() {
        // Neither skill: no roll at all.
        assertNull(Divination.divinationLevelFor(own = null, sibling = null, defaultBonus = 0))
        // Sibling below the gate, and nothing owned: still no roll. The
        // general SPI/Hard default is NOT substituted here.
        assertNull(Divination.divinationLevelFor(own = null, sibling = 11, defaultBonus = 0))
        // Sibling at the gate, nothing owned: the default is the whole point.
        val lent = Divination.divinationLevelFor(own = null, sibling = 12, defaultBonus = 0)
        assertEquals(10, lent!!.level)
        assertTrue(lent.isDefault)
        // Owned but no sibling at all.
        val alone = Divination.divinationLevelFor(own = 9, sibling = null, defaultBonus = 0)
        assertEquals(9, alone!!.level)
        assertFalse(alone.isDefault)
    }

    /**
     * **A better default is not the same as having no skill** (Codex). Both
     * defaulting cases set `isDefault`, and a screen given only that boolean
     * told a Hermit -- who has Divination Arts 11 -- that they had none.
     *
     * The sentence is authored here rather than in the dialog, the call
     * [Detection] already had to make for its attribute line (6ai).
     */
    @Test
    fun `the two defaulting cases say different things`() {
        val hasNone = Divination.sourceLine(
            Divination.divinationLevelFor(own = null, sibling = 14, defaultBonus = 0)!!,
            Divination.SKILL,
            Divination.SIBLING_SKILL,
        )!!
        assertTrue(hasNone.contains("has no ${Divination.SKILL}"), hasNone)

        val hasWorse = Divination.sourceLine(
            Divination.divinationLevelFor(own = 11, sibling = 14, defaultBonus = 0)!!,
            Divination.SKILL,
            Divination.SIBLING_SKILL,
        )!!
        assertFalse(
            hasWorse.contains("has no"),
            "a Hermit HAS Divination Arts 11; saying otherwise is false: $hasWorse",
        )
        assertTrue(hasWorse.contains("11"), "and their own level is named: $hasWorse")
        // Both name where the number came from, which is the whole point.
        assertTrue(hasNone.contains("14") && hasWorse.contains("14"))
    }

    @Test
    fun `a caster rolling their own skill is told nothing extra`() {
        val owned = Divination.divinationLevelFor(own = 15, sibling = 14, defaultBonus = 0)!!
        assertNull(
            Divination.sourceLine(owned, Divination.SKILL, Divination.SIBLING_SKILL),
            "no sentence at all, rather than one saying the number is their own",
        )
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
    fun `supporting skills cap at plus two however many you have`() {
        val all = Divination.supportingSkills()
        assertEquals(4, all.size, "four skills at +1 each would be +4 uncapped")
        assertEquals(Divination.MAX_SUPPORT_BONUS, Divination.supportBonus(all))
        assertEquals(1, Divination.supportBonus(all.take(1)))
        assertEquals(0, Divination.supportBonus(emptyList()))
        // Named twice pays once: a character has one Occultism.
        assertEquals(1, Divination.supportBonus(listOf(all.first(), all.first())))
    }

    /**
     * **The bracket is the book asking for a domain, not part of a name.**
     *
     * Ch. 6.5 prints "Hidden Lore (relevant)" and no sheet spells it that way,
     * so keyed on the printed string the row is unreachable — 6am's finding
     * for ch. 7's own list, which that slice fixed on the ritual side only.
     * Only the ROW's bracket is stripped, so a character's specialization
     * still matches: relevance is the GM's call, not a spelling.
     */
    @Test
    fun `a supporting skill matches whatever domain the sheet spells`() {
        val owned = listOf("Hidden Lore", "Occultism", "Camouflage")
        val got = Divination.supportSkillsOwnedBy(owned).map { it.skill }.toSet()
        assertEquals(setOf("Occultism", "Hidden Lore (relevant)"), got)
        assertEquals(2, Divination.supportBonus(Divination.supportSkillsOwnedBy(owned)))

        // The sheet's own specialization, which is how every premade prints it.
        assertEquals(
            listOf("Hidden Lore (relevant)"),
            Divination.supportSkillsOwnedBy(listOf("Hidden Lore (Fae)")).map { it.skill },
        )
        // ...and a diviner with none of the four gets nothing rather than a
        // default: the empty branch the screen names the four skills for.
        assertEquals(
            emptyList<String>(),
            Divination.supportSkillsOwnedBy(listOf("Brawling")).map { it.skill },
        )
    }

    /**
     * **Divination caps at +2 and rituals cap at +3, and both are printed.**
     *
     * Ch. 6.5 line 3580 prints "cumulative but cap at +2 total from supporting
     * skills"; ch. 7 prints +3 for its own five. Two printed rules, the shape
     * 6y's two preparation tables already have — straightening them is the
     * 4.1 sin, and 6at named this pair while sweeping code back to the book.
     */
    @Test
    fun `the two support caps are different printed rules`() {
        assertEquals(2, Divination.MAX_SUPPORT_BONUS)
        assertEquals(3, RitualMode.SUPPORT_CAP)
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

    @Test
    fun `burning SPI raises the number you roll under, on the dice already cast`() {
        // "You may spend additional SPI (1 SPI = +1, no cap) AFTER seeing your
        // result" (p. 55). So the same total is re-read against a higher
        // target; nothing is rolled twice.
        val trivial = difficulty("Trivial")
        val plain = Divination.attempt(10, trivial, fixed(4))
        val pushed = Divination.pushed(plain, 3)
        assertEquals(12, plain.roll.target)
        assertEquals(15, pushed.roll.target)
        assertEquals(plain.roll.total, pushed.roll.total)
        assertEquals(3, pushed.spiBurned)
        assertThrows<IllegalArgumentException> { Divination.pushed(plain, -1) }
    }

    @Test
    fun `pushes accumulate on one question`() {
        val plain = Divination.attempt(10, difficulty("Trivial"), fixed(4))
        val twice = Divination.pushed(Divination.pushed(plain, 1), 2)
        assertEquals(15, twice.roll.target)
        assertEquals(3, twice.spiBurned)
    }

    @Test
    fun `a push re-reads the clarity band, not just the target`() {
        // 12 against a target of 12 is a bare success -- the mildest band.
        // One SPI moves the margin and the row it lands on with it.
        val plain = Divination.attempt(10, difficulty("Trivial"), fixed(4))
        assertEquals(Outcome.SUCCESS, plain.roll.outcome)
        val pushed = Divination.pushed(plain, 5)
        assertEquals(5, pushed.roll.margin)
        assertNotEquals(plain.clarity.clarity, pushed.clarity.clarity)
    }

    @Test
    fun `a push is offered on a critical success, which still has a band to improve`() {
        // EffortBurn refuses a roll under a critical-only rule because nothing
        // can move it. A critical success is the opposite case: the book grades
        // it by margin like any other, so a push still buys a better reading.
        //
        // Skill 1 on a Trivial (+2) question is a target of 3, and 1s on 3d6
        // is a 3 -- a critical success by a margin of 0, which is the MILDEST
        // band. A fixture already at "Crystal clear" would pass this test
        // while the push did nothing.
        val crit = Divination.attempt(1, difficulty("Trivial"), fixed(1))
        assertEquals(Outcome.CRITICAL_SUCCESS, crit.roll.outcome)
        assertEquals("Vague", crit.clarity.clarity)
        assertTrue(Divination.canPush(crit))
        val pushed = Divination.pushed(crit, 5)
        assertEquals(Outcome.CRITICAL_SUCCESS, pushed.roll.outcome)
        assertEquals(8, pushed.roll.target)
        assertEquals("Crystal clear", pushed.clarity.clarity)
    }

    /**
     * A push buys a clearer answer and nothing else, and "Crystal clear" has
     * none above it: play session 3 was offered +1 to +4 SPI there.
     */
    @Test
    fun `a crystal clear answer has nothing left to push for`() {
        val clear = Divination.attempt(12, difficulty("Trivial"), fixed(1))
        assertEquals("Crystal clear", clear.clarity.clarity)
        assertFalse(Divination.canPush(clear))
        assertEquals(clear, Divination.pushed(clear, 3))
    }

    @Test
    fun `the critical-only rule survives a push, and refuses one`() {
        // Sequence Immunity demotes a plain success. A burn moves the target
        // and never the total, so no amount of it reaches a 3 or 4 -- the
        // arithmetic EffortBurn.canPush already refuses.
        //
        // Asserted as the WHOLE outcome coming back: checking only the demoted
        // verdict passes on a push that happened, because a higher target
        // still demotes (Codex).
        val warded = Divination.attempt(
            10, difficulty("Trivial"), fixed(4), requiresCritical = true,
        )
        assertFalse(Divination.canPush(warded))
        val pushed = Divination.pushed(warded, 4)
        assertEquals(warded, pushed)
        assertEquals(0, pushed.spiBurned)
        assertEquals(Outcome.FAILURE, pushed.effectiveOutcome)
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
     * "Complex divination ... Requires Ritualistic Magic or a very high
     * Divination Arts roll (-4 penalty)." (Sec. I, p. 54)
     *
     * One of three scopes and the only one that prices anything: the other
     * two say which skill suffices and charge nothing for saying it. A row
     * that priced all three would be a table this book does not print.
     */
    @Test
    fun `only the complex scope charges for the skill it substitutes`() {
        assertEquals(
            listOf("Simple", "Moderate", "Complex"),
            Divination.scopes().map { it.scope },
        )
        assertEquals(0, Divination.penaltyFor(Divination.scopeNamed("Simple")))
        assertEquals(0, Divination.penaltyFor(Divination.scopeNamed("Moderate")))
        assertEquals(-4, Divination.penaltyFor(Divination.scopeNamed("Complex")))
    }

    /**
     * The composition the book does not print, pinned at the boundary that
     * would hide a reversal.
     *
     * The scope penalty prices the SKILL SUBSTITUTION and the band prices the
     * QUESTION, so they add -- the reading that expired the old refusal. They
     * arrive through different parameters, so a later reader who made them
     * exclusive would still satisfy every assertion that checks one alone;
     * what fails is this total.
     */
    @Test
    fun `a complex question asked of a hard subject is charged both times`() {
        val hard = difficulty("Hard")
        val scope = Divination.penaltyFor(Divination.scopeNamed("Complex"))
        // The input this test composes, pinned here. The band's own -4 is
        // pinned by the difficulty test above; asserting it again would be a
        // second copy of that guard rather than a guard on this one.
        assertEquals(-4, scope)
        assertEquals(6, Divination.targetFor(14, hard, scope))
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
     * are effectively one level better" (Versatile, p. 13), and p. 54 calls
     * the Ritualistic Magic-2 number a default in its own word.
     *
     * The bonus is INSIDE the pick. Own 10 against a sibling of 12 lends 10,
     * a tie the owned skill keeps; with Versatile the default is 11 and wins.
     * Choosing first and adding afterwards rolls 10 for that caster.
     */
    @Test
    fun `a bonus to defaults is counted before the better number is picked`() {
        val plain = Divination.divinationLevelFor(own = 10, sibling = 12, defaultBonus = 0)!!
        assertEquals(10, plain.level)
        assertFalse(plain.isDefault)
        val versatile = Divination.divinationLevelFor(own = 10, sibling = 12, defaultBonus = 1)!!
        assertEquals(11, versatile.level)
        assertTrue(versatile.isDefault)
        // A tie still keeps the owned skill, with the bonus counted.
        val tied = Divination.divinationLevelFor(own = 13, sibling = 14, defaultBonus = 1)!!
        assertEquals(13, tied.level)
        assertFalse(tied.isDefault)
        // And it reaches the caster through the one entry point the app calls.
        val caster = Divination.levelsFor(ownDivination = null, ownRitual = 14, defaultBonus = 1)
        assertEquals(13, caster.divination!!.level)
        assertEquals(14, caster.ritual!!.level, "a ritual is never rolled at a default")
    }

    /** "A character with one skill at 12+": the gate reads the SIBLING's level. */
    @Test
    fun `a bonus to defaults does not open the gate at eleven`() {
        assertNull(Divination.divinationLevelFor(own = null, sibling = 11, defaultBonus = 1))
    }

    /**
     * The line reconciles: the sibling's true level, the bonus, and its name.
     * It used to recover the sibling's level from the chosen number, which a
     * bonus would have printed one too high.
     */
    @Test
    fun `the source line names the sibling's own level and the bonus`() {
        val lent = Divination.divinationLevelFor(own = null, sibling = 14, defaultBonus = 1)!!
        val line = Divination.sourceLine(lent, Divination.SKILL, Divination.SIBLING_SKILL)!!
        assertTrue(line.contains("${Divination.SIBLING_SKILL} 14"), line)
        assertFalse(line.contains("15"), line)
        assertTrue(line.contains("+1") && line.contains(TraitEffects.VERSATILE), line)
        // Without the bonus the trait is not named at all.
        val plain = Divination.divinationLevelFor(own = null, sibling = 14, defaultBonus = 0)!!
        val bare = Divination.sourceLine(plain, Divination.SKILL, Divination.SIBLING_SKILL)!!
        assertFalse(bare.contains(TraitEffects.VERSATILE), bare)
    }
}
