package com.pathways.rules

import com.pathways.model.Trait
import com.pathways.model.TraitKind
import com.pathways.rules.content.ContentLoader
import com.pathways.rules.content.SelfControlRoll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.assertThrows

/** Ch. 3's self-control rolls (6am). */
class SelfControlTest {

    private fun dis(name: String) = Trait(name, 5, TraitKind.DISADVANTAGE)

    /**
     * **A roll this app shipped and one premade could not make.** Church
     * Healer's sheet stores `Charity` with the book's own effect cell glued to
     * the name, and `checksFor` matched the raw string against the catalogue --
     * so the character whose defining drawback is a Will roll had no Will roll.
     *
     * The resolution is [TraitNames.catalogueName], shared with the applied
     * and reported paths rather than repeated here (6ag, 6ai, 6be, 6bp).
     */
    @Test
    fun `a name printed beside the book's own effect still finds its roll`() {
        val charity = ContentLoader.disadvantages().first { it.name == "Charity" }
        assertNotNull(charity.selfControl, "Charity no longer prints a self-control roll")
        val glued = dis("Charity - ${charity.notes}")
        val checks = SelfControl.checksFor(listOf(glued))
        assertEquals(1, checks.size, "$checks")
        // The name the screen prints drops the effect cell the row shows
        // underneath it, and keeps everything else the sheet said.
        assertEquals("Charity", checks.single().trait)
        assertEquals(charity.notes, checks.single().printed)
    }
    /** [RandomSource] yields one FACE per call; 3d6 asks three times. */
    private fun scripted(vararg faces: Int): RandomSource {
        var next = 0
        return RandomSource { faces[next++.coerceAtMost(faces.size - 1)] }
    }

    /** A 3d6 total, split into three legal faces. */
    private fun rolling(total: Int): RandomSource {
        require(total in 3..18)
        val low = (total - 3) / 3
        return scripted(1 + low, 1 + low, total - 2 - 2 * low)
    }

    private fun check(name: String) =
        SelfControl.checksFor(listOf(dis(name))).single()

    @Test
    fun `the number printed only in the name is read`() {
        // "Overconfidence (12)". Its effect cell -- "Believes they can handle
        // situations they cannot" -- names no roll at all, so the name is the
        // only place this number exists, and `strip_self_control_number` had
        // deleted it since M0. This assertion fails against the old content
        // file, where no disadvantage carries a selfControl at all.
        val spec = check("Overconfidence").spec
        assertEquals(12, spec.target)
        assertEquals("name", spec.source)
    }

    @Test
    fun `the four name-only rows are on real sheets, which is why it mattered`() {
        val carrying = Premades.all().count { sheet ->
            sheet.disadvantages.any { it.name == "Overconfidence" }
        }
        // Eleven of the twenty-three. Not an edge case that went unread --
        // the most common self-control disadvantage in the book's own cast.
        assertEquals(11, carrying)
    }

    @Test
    fun `five rows print it twice and agreeing is the warrant for the other four`() {
        // If this ever stops holding, the bare "(12)" on Overconfidence,
        // Obsession, Nightmares and Hallucinations loses its evidence and the
        // reading has to be made again rather than inherited.
        listOf("Curious", "Greed", "Bad Temper", "Bully", "Paranoia").forEach { name ->
            val spec = check(name).spec
            assertEquals(12, spec.target, name)
            assertEquals("both", spec.source, name)
        }
    }

    @Test
    fun `a flat target does not move with Will`() {
        // The whole reason the two shapes are kept apart. A potion that raises
        // Will must not make Curious easier to resist, and a low Will must not
        // make it harder -- the book prints a flat 12.
        val curious = check("Curious")
        assertEquals(12, SelfControl.targetFor(curious.spec, will = 8, iq = 10))
        assertEquals(12, SelfControl.targetFor(curious.spec, will = 16, iq = 10))
    }

    @Test
    fun `an attribute row moves with the digested attribute`() {
        val stubborn = check("Stubborn")
        assertEquals("Will", stubborn.spec.attribute)
        assertEquals(-3, stubborn.spec.offset)
        assertEquals(7, SelfControl.targetFor(stubborn.spec, will = 10, iq = 10))
        assertEquals(11, SelfControl.targetFor(stubborn.spec, will = 14, iq = 10))

        val impulsive = check("Impulsive")
        assertEquals("IQ", impulsive.spec.attribute)
        assertEquals(-2, impulsive.spec.offset)
        // Reads IQ, not Will -- a row keyed on the wrong attribute would still
        // produce a perfectly plausible number.
        assertEquals(10, SelfControl.targetFor(impulsive.spec, will = 18, iq = 12))
    }

    @Test
    fun `a bare attribute carries offset zero, not a missing offset`() {
        val loner = check("Loner")
        assertEquals(0, loner.spec.offset)
        assertEquals(11, SelfControl.targetFor(loner.spec, will = 11, iq = 10))
    }

    @Test
    fun `the third spelling is the roll itself and is not merged with the others`() {
        val row = ContentLoader.disadvantages()
            .single { it.name.contains("Indulge Evil Desires") }
        // "roll 3d6 <= 6 or indulge fully" -- neither "(6)" nor "roll vs. 6".
        assertEquals(6, row.selfControl?.target)
        assertEquals("effect", row.selfControl?.source)
    }

    @Test
    fun `a critical success holds against any target`() {
        // No printed override here, so SuccessRoll's general rules stand: the
        // FrightCheck reading (6c), not the PCR's. Reading `== SUCCESS` alone
        // would report the book's best roll as a loss of control.
        val stubborn = check("Stubborn")
        val out = SelfControl.attempt(stubborn, will = 5, iq = 10, source = rolling(4))
        assertEquals(2, out.target)
        assertTrue(out.held, "a rolled 4 holds even against a target of 2")
    }

    @Test
    fun `a critical failure never holds`() {
        val curious = check("Curious")
        val out = SelfControl.attempt(curious, will = 10, iq = 10, source = rolling(18))
        assertFalse(out.held)
    }

    @Test
    fun `success is the compulsion held, and the consequence is quoted not generated`() {
        val curious = check("Curious")
        val out = SelfControl.attempt(curious, will = 10, iq = 10, source = rolling(7))
        assertTrue(out.held)
        // The book's own cell, verbatim. Three sentence shapes ("or
        // investigate", "to avoid", "to undertake") mean a paraphrase would
        // get one of them backwards.
        assertEquals(
            "Must roll vs. 12 or investigate any mystery encountered",
            out.check.printed,
        )
    }

    @Test
    fun `the GM modifier reaches both shapes`() {
        assertEquals(10, SelfControl.targetFor(check("Curious").spec, 10, 10, modifier = -2))
        assertEquals(5, SelfControl.targetFor(check("Stubborn").spec, 10, 10, modifier = -2))
    }

    @Test
    fun `rows that print no self-control roll get none`() {
        // Absent means absent, never a target of zero -- the distinction
        // RitualCritical.corGained draws.
        assertTrue(SelfControl.checksFor(listOf(dis("Skinny"))).isEmpty())
        assertNull(ContentLoader.disadvantages().single { it.name == "Skinny" }.selfControl)
    }

    @Test
    fun `the two refused phrasings stay refused`() {
        // Duty's "(roll 9 or less)" is how often the duty comes up; Phobia's
        // "Fright Check at -2" is a join to chapter 5. Both print a number to
        // roll under and neither is a self-control roll.
        assertTrue(SelfControl.checksFor(listOf(dis("Duty (specify)"))).isEmpty())
        assertTrue(SelfControl.checksFor(listOf(dis("Phobia (specify)"))).isEmpty())
    }

    /**
     * **A fourth printed spelling, and Workaholic is on two premades.** The
     * book calls for the same roll in five word orders and none of them uses
     * "roll vs.", so the anchor that reads sixteen rows could not see any of
     * them — Factory Foreman and Private Investigator printed the cell and
     * offered no roll. Found by 6bt's design review while blocking Workaholic
     * out of that slice.
     *
     * The consumer needed no change at all: `checksFor` reads `selfControl`
     * off the catalogue, so widening the extractor's anchor made five rows
     * rollable on their own.
     */
    @Test
    fun `a roll required without the words roll vs is still a self-control roll`() {
        val expected = mapOf(
            "Workaholic" to (-3 to "Will"),          // "rolls to take a day off at Will-3"
            "Compulsive Gambling" to (-2 to "Will"), // "requires Will roll at -2 to leave a game"
            "Bloodlust" to (0 to "IQ"),              // "IQ roll necessary to accept a surrender"
            "Coldblooded" to (0 to "Will"),          // "Roll Will when suppressing evil desires"
            "Reluctant Killer" to (0 to "Will"),     // "Will rolls required to use violence again"
        )
        for ((name, pair) in expected) {
            val (offset, attr) = pair
            val check = SelfControl.checksFor(listOf(dis(name))).single()
            assertEquals(offset, check.spec.offset, name)
            assertNull(check.spec.target, "$name is an attribute roll, not a flat target")
            assertTrue(attr in check.spec.toString(), "$name: ${check.spec}")
            // ...and none of them prints B's phrasing, which is why a fourth
            // anchor had to exist rather than the third being loosened.
            val cell = ContentLoader.disadvantages().single { it.name == name }.notes
            assertTrue("roll vs." !in cell.lowercase(), "$name now prints 'roll vs.': $cell")
        }
    }

    /**
     * **Success is the character holding out, exactly as it is for the sixteen
     * rows that say "roll vs."** — so there is no second outcome model.
     *
     * A design review's blocking finding, and it was right: I read Workaholic's
     * "rolls to take a day off" as inverting the direction and proposed a
     * polarity field for it. Five existing rows already make success perform
     * the action that resists the trait — Stubborn "to reverse your stated
     * position", Laziness "to undertake any sustained effort", Charity "to
     * avoid helping", Reckless "to back down", Bad Temper "to avoid angry
     * outbursts" — and [SelfControlOutcome.held]'s own KDoc has said so since
     * 6am. A second field would have duplicated a decision this file already
     * documents, which is the failure the design proposing it was quoting.
     */
    @Test
    fun `a required roll grades like the rows that print roll vs`() {
        for (name in listOf("Workaholic", "Stubborn", "Laziness")) {
            val check = SelfControl.checksFor(listOf(dis(name))).single()
            val held = SelfControl.attempt(check, will = 10, iq = 10, source = rolling(4))
            val gaveIn = SelfControl.attempt(check, will = 10, iq = 10, source = rolling(18))
            assertTrue(held.held, name)
            assertTrue(!gaveIn.held, name)
        }
    }

    /**
     * **Three of these were invisible to every sweep this repo has run**, and
     * not through a regex slip: a Fright Check is a roll the book NAMES rather
     * than describes, so no filter keyed on the word "roll" can see it. Only
     * `Phobia (specify)` was ever pinned, and only because its cell happens to
     * also say "-1 to all rolls".
     *
     * Grief-Stricken is the discriminating case for the fourth anchor: its
     * offset is attached TO a roll ("Will rolls to act clearly **are at -2**"),
     * where every parsed row binds the offset to the roll's TARGET ("at
     * Will-3"). That is Ether Body Damage's shape, so it is a modifier and
     * `TraitEffects`' business.
     */
    @Test
    fun `a mechanic the book names separately is not a self-control roll`() {
        val refused = listOf(
            "Phobia: Sacred Symbols",   // "Fright Check at -2 on direct exposure"
            "Chronophobia (Temporal)",  // "immediate Fright Check"
            "Soul Scar",                // "Fright Check failure by 5+" -- an outcome, not a roll
            "Grief-Stricken",           // "Will rolls to act clearly are at -2" -- a modifier
        )
        for (name in refused) {
            assertNull(
                ContentLoader.disadvantages().single { it.name == name }.selfControl,
                name,
            )
            assertTrue(SelfControl.checksFor(listOf(dis(name))).isEmpty(), name)
        }
    }

    @Test
    fun `an advantage sharing a name is not a self-control roll`() {
        assertTrue(SelfControl.checksFor(listOf(Trait("Curious", 5, TraitKind.ADVANTAGE))).isEmpty())
    }

    @Test
    fun `the sheets and the book disagree on two spellings - guard against silent repair`() {
        // 4.1: the book wins and the disagreement is RECORDED. These sheet
        // names match nothing, and that is the finding, not a bug to fix by
        // fuzzy-matching -- which would guess, and guessing is repair.
        val printed = ContentLoader.disadvantages().map { it.name }.toSet()
        // "Wierdness Magnet" (Journalist) was found on the DEVICE, rendering
        // with no effect line, after this test already pinned the other two.
        // Pinned here for the same reason: an unpinned recorded disagreement
        // is the one a later session repairs by fuzzy-matching. It differs
        // from the two below in that the row it reaches for prints no
        // self-control roll, so only the no-match half is asserted.
        listOf("Wierdness Magnet", "Absent-Minded", "Paranoia: Bodies of water").forEach {
            assertFalse(it in printed, "the book's list now prints '$it'")
            assertTrue(SelfControl.checksFor(listOf(dis(it))).isEmpty())
        }
        // ...and the rows they are almost certainly meant to be DO print one,
        // which is what makes the mismatch cost something.
        assertTrue(
            ContentLoader.disadvantages()
                .single { it.name == "Absent-Mindedness" }.selfControl != null,
        )
        assertTrue(
            ContentLoader.disadvantages().single { it.name == "Paranoia" }.selfControl != null,
        )
    }

    @Test
    fun `an attribute this book never keys on is refused at LOAD, not at render`() {
        // The sheet screen prints this target for every disadvantage it
        // lists, so a row keyed on an attribute the book never uses would
        // take the whole screen down rather than a dismissable dialog.
        // Refused where the content is built, so it can never get that far.
        assertThrows<IllegalArgumentException> {
            SelfControlRoll(attribute = "HT", offset = 0, source = "effect")
        }
        // ...and the two spellings the rules object keys on are the two the
        // content type admits, so neither can drift from the other.
        assertEquals(setOf(SelfControl.WILL, SelfControl.IQ), SelfControlRoll.ATTRIBUTES)
    }

    @Test
    fun `every printed row survives the loader, so no sheet row can throw`() {
        // The guard above is only worth having if the committed content
        // passes it -- and this is what would fail first if a later extract
        // wrote an attribute the book does not key on.
        val specs = ContentLoader.disadvantages().mapNotNull { it.selfControl }
        assertTrue(specs.size >= 20, "expected the ~24 printed rows, got ${specs.size}")
        specs.forEach { SelfControl.targetFor(it, will = 10, iq = 10) }
    }

    @Test
    fun `a flat target and an attribute are exclusive by construction`() {
        assertThrows<IllegalArgumentException> {
            SelfControlRoll(target = 12, attribute = "Will", source = "effect")
        }
        assertThrows<IllegalArgumentException> { SelfControlRoll(source = "effect") }
    }

    /**
     * A screen that prints "3d6 <= N" and then rolls must roll against N.
     * Composing N a second time inside the roll is two call sites answering
     * one question (6eg, 6eh), so the roll takes the composed target and
     * the older entry point is that roll with the target composed first.
     */
    @Test
    fun `a roll against a composed target is the attempt that composed it`() {
        val curious = check("Curious")
        val target = SelfControl.targetFor(curious.spec, will = 10, iq = 10, modifier = -3)
        val rolled = SelfControl.rollAgainst(curious, target, rolling(9))
        assertEquals(target, rolled.target)
        assertEquals(target, rolled.roll.target, "judged against the target it was handed")
        assertEquals(curious, rolled.check)
        assertEquals(SelfControl.attempt(curious, will = 10, iq = 10, source = rolling(9), modifier = -3), rolled)
        // A different number is a different verdict off the same dice.
        assertNotEquals(SelfControl.attempt(curious, will = 10, iq = 10, source = rolling(9)), rolled)

        // Curious prints a flat target, so it cannot tell Will from IQ.
        // Impulsive is keyed on IQ-2, and the two attributes differ here.
        val impulsive = check("Impulsive")
        assertEquals(10, SelfControl.attempt(impulsive, will = 7, iq = 12, source = rolling(9)).target)
    }
}
