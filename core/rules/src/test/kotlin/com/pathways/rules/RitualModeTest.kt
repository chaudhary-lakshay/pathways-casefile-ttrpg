package com.pathways.rules

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * The two ways Ch. 7 resolves a ritual (p. 61-63).
 *
 * The mode is not decoration: it decides which preparation table applies and
 * whether the target link is a modifier of its own or already inside one.
 */
class RitualModeTest {

    @Test
    fun `the book prints two preparation tables and they disagree`() {
        // RECORDED, never repaired. Excellent is +2 in the Quick table and +3
        // in the Full one, and until this slice the app applied the Full one
        // to every ritual -- so a quick ritual rolled a point too high.
        assertEquals(2, RitualMode.QUICK.preparationNamed("Excellent").modifier)
        assertEquals(3, RitualMode.FULL.preparationNamed("Excellent").modifier)
        assertNotEquals(
            RitualMode.QUICK.preparationNamed("Excellent").modifier,
            RitualMode.FULL.preparationNamed("Excellent").modifier,
        )
    }

    @Test
    fun `they agree on Adequate, which is what makes the difference real`() {
        // Two unrelated ladders would differ everywhere. These are two
        // printings of one idea that part company on exactly one row.
        assertEquals(0, RitualMode.QUICK.preparationNamed("Adequate").modifier)
        assertEquals(0, RitualMode.FULL.preparationNamed("Adequate").modifier)
    }

    @Test
    fun `each mode offers only its own table's rows`() {
        assertEquals(
            listOf("Excellent", "Adequate", "Poor/None"),
            RitualMode.QUICK.preparations().map { it.quality },
        )
        assertEquals(
            listOf("Excellent", "Good", "Adequate", "Poor", "None"),
            RitualMode.FULL.preparations().map { it.quality },
        )
        // "Good" is a Full-mode row. Asking the Quick table for it is a caller
        // bug, not a reason to fall back on the other mode's number.
        assertThrows<IllegalArgumentException> { RitualMode.QUICK.preparationNamed("Good") }
    }

    @Test
    fun `the target link is its own modifier in quick mode only`() {
        // Quick: "Sum the following two modifiers: 1. Preparation Quality
        // 2. Target Link". Full: the link is one of the declarations that
        // "feed into a single Preparation Modifier", and its rows say so --
        // Excellent is "perfect link", Poor is "weak link".
        assertTrue(RitualMode.QUICK.usesTargetLink)
        assertTrue(!RitualMode.FULL.usesTargetLink)
        assertEquals(4, RitualMode.linkNamed("Perfect").modifier)
    }

    /**
     * The link's mirror image. Each mode prints one modifier the other does
     * not, and the evidence has the same shape in both directions: Phase II
     * names the Flourish Bonus, and Quick's Step 3 says it "sums the following
     * two modifiers" and then names two that are not it.
     */
    @Test
    fun `the flourish bonus is a full ritual's alone`() {
        assertTrue(RitualMode.FULL.usesFlourish)
        assertTrue(!RitualMode.QUICK.usesFlourish)
        // And the two flags cross: neither mode takes both, neither neither.
        // If that stopped holding, one mode would have become a superset of
        // the other and the scoping would need re-reading rather than reusing.
        RitualMode.entries.forEach { mode ->
            assertNotEquals(
                mode.usesTargetLink,
                mode.usesFlourish,
                "$mode should print exactly one of the link and the flourish",
            )
        }
    }

    @Test
    fun `the flourish is worth exactly the printed plus one`() {
        val adequate = RitualMode.FULL.preparationNamed("Adequate")
        assertEquals(0, adequate.modifier)
        assertEquals(1, RitualMode.FLOURISH_BONUS)
        assertEquals(
            RitualMode.FLOURISH_BONUS,
            RitualMode.FULL.modifierTotal(preparation = adequate, flourish = true),
        )
        // It composes with preparation rather than replacing it.
        val excellent = RitualMode.FULL.preparationNamed("Excellent")
        assertEquals(
            4,
            RitualMode.FULL.modifierTotal(preparation = excellent, flourish = true),
            "+3 preparation and the +1 flourish",
        )
        // And an unawarded flourish moves nothing, which is what lets every
        // caller written before Phase II was read keep its old answer.
        assertEquals(3, RitualMode.FULL.modifierTotal(preparation = excellent))
    }

    @Test
    fun `a quick ritual refuses a flourish rather than inventing a step`() {
        val ex = assertThrows<IllegalArgumentException> {
            RitualMode.QUICK.modifierTotal(
                preparation = RitualMode.QUICK.preparationNamed("Adequate"),
                flourish = true,
            )
        }
        assertTrue(ex.message!!.contains("Flourish"), ex.message)
        // Refused, not dropped: a GM who described a performance and watched
        // the target stay put has no way to tell which of the two happened.
        assertThrows<IllegalArgumentException> {
            RitualMode.QUICK.modifierTotal(preparation = null, flourish = true)
        }
    }

    @Test
    fun `a full ritual refuses a link rather than paying it twice`() {
        // The whole reason the flag exists. A perfect link is +4, and the Full
        // table's Excellent row already includes one, so adding it again is +8
        // on a 3d6 roll -- a near-certain success bought by a parsing slip.
        val ex = assertThrows<IllegalArgumentException> {
            RitualMode.FULL.modifierTotal(
                preparation = RitualMode.FULL.preparationNamed("Excellent"),
                link = RitualMode.linkNamed("Perfect"),
            )
        }
        assertTrue(ex.message!!.contains("already"))
    }

    /**
     * **The two tables are not interchangeable, and this is 6y's own defect
     * reachable through the API.** Excellent is +3 in a Full ritual and +2 in
     * a Quick one; the app applied +3 to both for a whole milestone. A row
     * borrowed across modes puts that number back, and it looks perfectly
     * ordinary on screen -- nothing about "+3" says which table it came from.
     *
     * Unreachable from the dialog, which holds `preparation` in a
     * `remember(mode)` cleared on every switch. That is the reason to refuse
     * it here rather than there (6l): nothing can reach it yet, which is what
     * stops the next caller finding it.
     */
    @Test
    fun `a preparation row from the other mode's table is refused`() {
        val full = RitualMode.FULL.preparationNamed("Excellent")
        val quick = RitualMode.QUICK.preparationNamed("Excellent")
        assertEquals(3, full.modifier)
        assertEquals(2, quick.modifier, "the same word, a different number")

        val ex = assertThrows<IllegalArgumentException> {
            RitualMode.QUICK.modifierTotal(preparation = full)
        }
        assertTrue(ex.message!!.contains("not a row"), ex.message)

        // And the other way round, so neither table is privileged.
        assertThrows<IllegalArgumentException> {
            RitualMode.FULL.modifierTotal(preparation = quick)
        }
        // Each mode still accepts its own.
        assertEquals(3, RitualMode.FULL.modifierTotal(preparation = full))
        assertEquals(2, RitualMode.QUICK.modifierTotal(preparation = quick))
    }

    /**
     * Containment is exact only because the two tables share no row. Adequate
     * is +0 in both and is the one place a borrowed row could slip through --
     * it does not, because the printed `looksLike` cells differ. If they ever
     * stop differing, this guard needs a test other than equality, so the
     * difference is pinned rather than assumed.
     */
    @Test
    fun `the two tables share no row, which is what makes the guard exact`() {
        val full = RitualMode.FULL.preparations()
        val quick = RitualMode.QUICK.preparations()
        assertTrue(full.intersect(quick.toSet()).isEmpty())

        val bothAdequate = listOf(full, quick).map { rows ->
            rows.single { it.quality.equals("Adequate", ignoreCase = true) }
        }
        assertEquals(
            bothAdequate[0].modifier,
            bothAdequate[1].modifier,
            "the two tables agree that Adequate is +0 -- 6y's own warrant",
        )
        assertTrue(
            bothAdequate[0] != bothAdequate[1],
            "and still differ elsewhere in the row, which is what equality catches",
        )
    }

    @Test
    fun `quick mode sums preparation and link`() {
        assertEquals(
            6,
            RitualMode.QUICK.modifierTotal(
                preparation = RitualMode.QUICK.preparationNamed("Excellent"),
                link = RitualMode.linkNamed("Perfect"),
            ),
            "+2 preparation and +4 link",
        )
        assertEquals(
            -6,
            RitualMode.QUICK.modifierTotal(
                preparation = RitualMode.QUICK.preparationNamed("Poor/None"),
                link = RitualMode.linkNamed("None"),
            ),
            "-2 at the gentle end of its range, and -4",
        )
    }

    @Test
    fun `a range is stored as both its ends, nearer zero first`() {
        // This comment used to call the near end "the gentle reading the
        // darkness row, the fright triggers and the Phase I preparations
        // already settled". None of the three is settled that way any more:
        // darkness has had a picker since 6dz, the fright trigger a stepper
        // since M4, and the preparations since 6ef. The near end is the
        // DEFAULT, which is this app's policy and not the book's rule.
        assertEquals(-2, RitualMode.QUICK.preparationNamed("Poor/None").modifier)
        assertEquals(-4, RitualMode.QUICK.preparationNamed("Poor/None").best)
        assertEquals(0, RitualMode.linkNamed("Weak").modifier)
        assertEquals(-1, RitualMode.linkNamed("Weak").best)
    }

    /**
     * "The GM determines the Preparation Modifier from the player's
     * declarations" (p. 63, line 4119), and three of the cells they determine
     * it from print a span.
     *
     * The picks are chosen so that ignoring either one, or both, gives a
     * different sum: -4 with +3 is -1, where the two defaults give 0, the
     * preparation pick alone -2 and the link pick alone +1.
     */
    @Test
    fun `a GM's pick inside a printed range is what the roll takes`() {
        val good = RitualMode.FULL.preparationNamed("Good")
        assertEquals(1, RitualMode.FULL.modifierTotal(good), "unpicked, the printed near end")
        assertEquals(2, RitualMode.FULL.modifierTotal(good, preparationValue = 2))

        assertEquals(
            -1,
            RitualMode.QUICK.modifierTotal(
                preparation = RitualMode.QUICK.preparationNamed("Poor/None"),
                link = RitualMode.linkNamed("Strong"),
                preparationValue = -4,
                linkValue = 3,
            ),
        )
        // "-2 to -4" prints -3 as surely as it prints its two ends.
        assertEquals(-3, RitualMode.valueFor(RitualMode.QUICK.preparationNamed("Poor/None"), -3))
    }

    /**
     * "+0 to -1" runs DOWN from its default, and Kotlin's `0..-1` is empty --
     * a bound written in the row's own order would refuse every pick in it.
     */
    @Test
    fun `a range that runs downward from zero still takes its far end`() {
        val weak = RitualMode.linkNamed("Weak")
        assertEquals(0, RitualMode.valueFor(weak))
        assertEquals(-1, RitualMode.valueFor(weak, -1))
        val refused = assertThrows<IllegalArgumentException> { RitualMode.valueFor(weak, 1) }
        assertTrue("+0 to -1" in refused.message!!, refused.message)
    }

    /**
     * +3 is Excellent's cell and +0 is Adequate's. A pick that could borrow a
     * neighbour's number would let "Good" mean anything on the table.
     */
    @Test
    fun `a pick outside its own cell is refused, even where another row prints it`() {
        val good = RitualMode.FULL.preparationNamed("Good")
        listOf(3, 0).forEach { outside ->
            val refused = assertThrows<IllegalArgumentException> { RitualMode.valueFor(good, outside) }
            assertTrue("+1 to +2" in refused.message!!, refused.message)
        }
        val strong = RitualMode.linkNamed("Strong")
        val refused = assertThrows<IllegalArgumentException> { RitualMode.valueFor(strong, 4) }
        assertTrue("+2 to +3" in refused.message!!, refused.message)
    }

    @Test
    fun `a cell that prints one number takes no pick but its own`() {
        val excellent = RitualMode.FULL.preparationNamed("Excellent")
        assertEquals(3, RitualMode.valueFor(excellent, 3))
        val refused = assertThrows<IllegalArgumentException> { RitualMode.valueFor(excellent, 2) }
        assertTrue("exactly +3" in refused.message!!, refused.message)

        val perfect = RitualMode.linkNamed("Perfect")
        val refusedLink = assertThrows<IllegalArgumentException> { RitualMode.valueFor(perfect, 3) }
        assertTrue("exactly +4" in refusedLink.message!!, refusedLink.message)
    }

    /** "-4 or worse" (line 4138): at or below -4, with no floor the book names. */
    @Test
    fun `or worse runs away from zero and names no floor`() {
        val none = RitualMode.FULL.preparationNamed("None")
        assertEquals(-4, RitualMode.valueFor(none))
        assertEquals(-4, RitualMode.valueFor(none, -4))
        assertEquals(-9, RitualMode.valueFor(none, -9))
        val refused = assertThrows<IllegalArgumentException> { RitualMode.valueFor(none, -3) }
        assertTrue("-4 or worse" in refused.message!!, refused.message)
    }

    /**
     * Refused rather than dropped, for the reason a link given to a Full
     * ritual is: a GM who picked a number must not be left believing it
     * counted.
     */
    @Test
    fun `a pick with no row to pick inside is refused`() {
        val noPreparation = assertThrows<IllegalArgumentException> {
            RitualMode.QUICK.modifierTotal(preparation = null, preparationValue = -3)
        }
        assertTrue("no preparation row" in noPreparation.message!!, noPreparation.message)
        val noLink = assertThrows<IllegalArgumentException> {
            RitualMode.QUICK.modifierTotal(
                preparation = RitualMode.QUICK.preparationNamed("Adequate"),
                linkValue = 3,
            )
        }
        assertTrue("no target link" in noLink.message!!, noLink.message)
    }

    @Test
    fun `five supporting skills grant five points and the book allows three`() {
        // "These bonuses are cumulative but cap at +3 total from supporting
        // skills." Without the cap a scholar with the lot rolls at +5.
        assertEquals(5, RitualMode.supportSkills().size)
        assertTrue(RitualMode.supportSkills().all { it.bonus == 1 })
        assertEquals(3, RitualMode.supportBonus(RitualMode.supportSkills().map { it.skill }))
        assertEquals(2, RitualMode.supportBonus(listOf("Occultism", "Thaumatology")))
        assertEquals(0, RitualMode.supportBonus(emptyList()))
    }

    @Test
    fun `a skill the table does not print grants nothing, and is refused`() {
        // Silently ignoring it would let a typo cost a caster a point they
        // thought they had.
        assertThrows<IllegalArgumentException> { RitualMode.supportBonus(listOf("Brawling")) }
    }

    @Test
    fun `naming one supporting skill twice does not pay twice`() {
        assertEquals(1, RitualMode.supportBonus(listOf("Occultism", "Occultism")))
    }

    @Test
    fun `the row printed with a bracket is reachable from a real sheet`() {
        // "Hidden Lore (relevant domain)" is the book telling the caster to
        // pick a domain, not a skill's name -- the shape "Duty (specify)"
        // already has one list over. No sheet spells it that way, so keyed on
        // the printed string the row was unreachable and its +1 vanished in
        // silence. Four premades carry Hidden Lore.
        assertEquals(1, RitualMode.supportBonus(listOf("Hidden Lore")))
        assertEquals(2, RitualMode.supportBonus(listOf("Hidden Lore", "Occultism")))
        // ...and the printed spelling still answers, so the book's own string
        // is not the thing that broke.
        assertEquals(1, RitualMode.supportBonus(listOf("Hidden Lore (relevant domain)")))
        // One skill, one point: the two spellings are the same row.
        assertEquals(
            1,
            RitualMode.supportBonus(listOf("Hidden Lore", "Hidden Lore (relevant domain)")),
        )
    }

    @Test
    fun `a caster is offered exactly the supporting skills they own`() {
        // The Academic Researcher's own list. Three of the five, and the one
        // that used to be dropped is in it.
        val owned = listOf(
            "Research", "Writing", "History", "Occultism",
            "Hidden Lore", "Literature", "Mathematics",
        )
        assertEquals(
            listOf("Occultism", "Hidden Lore (relevant domain)", "Research"),
            RitualMode.supportSkillsOwnedBy(owned),
        )
        // A character with none of them is offered nothing rather than all
        // five -- the book means the skills the caster brings.
        assertTrue(RitualMode.supportSkillsOwnedBy(listOf("Brawling")).isEmpty())
    }

    @Test
    fun `the support bonus applies in both modes`() {
        // Section II sits above the mode split and says nothing about either,
        // so it is not scoped to one -- unlike the link, which each mode
        // handles its own way.
        assertEquals(3, RitualMode.QUICK.supportCap)
        assertEquals(3, RitualMode.FULL.supportCap)
    }

    /**
     * "For Praying to Oneself, replace the four-part structure with the
     * three-line self-description" (p. 65).
     *
     * The book's own selection rule, and it keys on the power source the
     * dialog's two-chip pair already sets. A caster using HP, assistants or a
     * catalyst while not invoking is still praying to oneself: p. 62 labels
     * that choice "Personal SPI (Praying to Oneself)", and Section IV.B-E are
     * fuel and bonuses stacked ON a source rather than sources of their own.
     */
    @Test
    fun `the power source chooses which structure the caster recites`() {
        assertEquals(
            "Four-part Hermes",
            Incantations.forPowerSource(isInvoking = true).structure,
        )
        assertEquals(
            "Three-line self-description",
            Incantations.forPowerSource(isInvoking = false).structure,
        )
    }

    /**
     * The four parts, which are the whole point of showing the structure.
     *
     * Two of them wrap onto a second line carrying their worked example, and
     * a part without its example is a part a player cannot use.
     */
    @Test
    fun `the standard incantation keeps all four parts and both examples`() {
        val hermes = Incantations.forPowerSource(isInvoking = true)
        assertEquals(4, hermes.parts.size)
        assertTrue(hermes.parts[0].endsWith("controls fate...\""))
        assertTrue(hermes.parts[3].endsWith("powers to my incantation.\""))
        // The paragraph printed AFTER the parts, which the screen renders
        // last. Read from the committed file rather than from the parse: a
        // failing extractor leaves its old JSON in place, and this field is
        // exactly what a stale file was missing when Codex found it.
        assertTrue(
            hermes.note!!.startsWith("As long as the four-part structure"),
            "the note the page prints after the list is gone",
        )
    }

    /**
     * Chapter 7.5's is REFERENCE, because nothing here can say a ritual is a
     * summoning.
     *
     * The weight is picked and its category list is display text, so gating on
     * Heavy would claim "summoning" of binding, affliction and four others.
     * It is shown, never selected -- so [forPowerSource] must not be able to
     * return it, which is what this asserts rather than the flag alone.
     */
    @Test
    fun `the summoning structure is shown and never chosen for the caster`() {
        val summoning = Incantations.all().single { it.referenceOnly }
        assertEquals("Summoning a spiritual creature", summoning.scope)
        // What the screen actually prints for it, read through the loader.
        // The scope and the flag survived a stale content file once; the TEXT
        // is the field a re-extraction would move, and it is chapter 7.5's
        // only claimed content.
        assertTrue(
            "resident realm of the creature" in summoning.text,
            "ch. 7.5's incantation rule is gone from the row that claims it",
        )
        assertTrue(
            listOf(true, false).none { Incantations.forPowerSource(it) == summoning },
            "a reference-only structure was selectable by power source",
        )
    }
}
