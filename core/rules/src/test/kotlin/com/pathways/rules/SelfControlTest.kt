package com.pathways.rules

import com.pathways.model.Trait
import com.pathways.model.TraitKind
import com.pathways.rules.content.ContentLoader
import com.pathways.rules.content.SelfControlRoll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** Ch. 3's self-control rolls (6am). */
class SelfControlTest {

    private fun dis(name: String) = Trait(name, 5, TraitKind.DISADVANTAGE)
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
}
