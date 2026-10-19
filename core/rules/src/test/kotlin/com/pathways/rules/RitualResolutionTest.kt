package com.pathways.rules

import com.pathways.model.Outcome
import com.pathways.rules.content.ContentLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RitualResolutionTest {

    private fun fixed(face: Int) = RandomSource { face }

    private fun scripted(vararg faces: Int): RandomSource {
        var next = 0
        return RandomSource { faces[next++.coerceAtMost(faces.size - 1)] }
    }

    private val heavy get() = RitualResolution.weights().single { it.weight == "Heavy" }
    private val light get() = RitualResolution.weights().single { it.weight == "Light" }

    @Test
    fun `the ritual's weight moves the target`() {
        // p. 67: Light +0, Moderate -2, Heavy -4.
        assertEquals(14, RitualResolution.targetFor(skillLevel = 14, weight = light, cor = 0))
        assertEquals(10, RitualResolution.targetFor(skillLevel = 14, weight = heavy, cor = 0))
    }

    @Test
    fun `corruption penalises the ritual roll and this is its first caller`() {
        // p. 68: "3 CoR -- All ritual rolls at -1 from spiritual instability."
        assertEquals(14, RitualResolution.targetFor(skillLevel = 14, weight = light, cor = 2))
        assertEquals(13, RitualResolution.targetFor(skillLevel = 14, weight = light, cor = 3))
        assertEquals(9, RitualResolution.targetFor(skillLevel = 14, weight = heavy, cor = 9))
    }

    @Test
    fun `a success costs nothing and consults no table`() {
        val outcome = RitualResolution.attempt(14, light, cor = 0, source = fixed(1))
        assertTrue(outcome.succeeded)
        assertNull(outcome.consequence)
        assertNull(outcome.critical)
        assertEquals(0, outcome.marginOfFailure)
    }

    /**
     * Ch. 7 Step 4 (p. 62) is the only place this book grades a ritual that
     * worked. Section VI is "Failure & Consequences" and prints no success
     * row, so before this table was read a ritual that surpassed its intent
     * and one that scraped in were the same event.
     *
     * It governs BOTH modes: Phase III of the full ritual says "Then resolve
     * using the same outcome table as Quick Ritual (Step 4)".
     */
    @Test
    fun `a success is graded, and by how far it beat the target`() {
        // 2+2+2 = 6 against 14: made it by 8, the deeper band.
        val wide = RitualResolution.attempt(14, light, cor = 0, source = fixed(2))
        assertTrue(wide.succeeded)
        assertEquals("Effect surpasses intent. Entity pleased (if invoked).", wide.success?.outcome)

        // 4+4+4 = 12 against 14: made it by 2, the milder band.
        val narrow = RitualResolution.attempt(14, light, cor = 0, source = fixed(4))
        assertTrue(narrow.succeeded)
        assertEquals("Effect works as intended.", narrow.success?.outcome)

        // Both modes, because Phase III sends the full ritual here too.
        val full = RitualResolution.attempt(
            14, light, cor = 0, source = fixed(4), mode = RitualMode.FULL,
        )
        val quick = RitualResolution.attempt(
            14, light, cor = 0, source = fixed(4), mode = RitualMode.QUICK,
        )
        assertEquals(full.success, quick.success)
    }

    @Test
    fun `a failed ritual is graded by its consequence and never by a success band`() {
        val outcome = RitualResolution.attempt(8, light, cor = 0, source = fixed(3))
        assertFalse(outcome.succeeded)
        assertNull(
            outcome.success,
            "a success row on a failed ritual is the slip SkillRollOutcome.degree avoids",
        )
    }

    /**
     * The table prints no critical row, and a rolled 3 succeeds against any
     * target however hopeless -- so its margin can be NEGATIVE and no printed
     * band covers it. The mildest row, rather than a crash or an invented
     * "surpasses intent" the book never granted.
     */
    @Test
    fun `a hopeless target still grades, because a rolled 3 always succeeds`() {
        val outcome = RitualResolution.attempt(2, light, cor = 0, source = fixed(1))
        assertTrue(outcome.succeeded)
        assertEquals("Effect works as intended.", outcome.success?.outcome)
    }

    @Test
    fun `every margin a success can have lands on exactly one printed band`() {
        val bands = ContentLoader.ritualSuccess()
        for (margin in 0..40) {
            val matched = bands.filter { margin >= it.from && (it.to == null || margin <= it.to) }
            assertEquals(1, matched.size, "margin $margin matched $matched")
            assertEquals(matched.single(), RitualResolution.successFor(margin))
        }
    }

    @Test
    fun `a small miss is the mildest consequence`() {
        // 3+3+3 = 9 against a target of 8: failure by 1.
        val outcome = RitualResolution.attempt(8, light, cor = 0, source = fixed(3))
        assertFalse(outcome.succeeded)
        assertEquals(1, outcome.marginOfFailure)
        assertEquals("Failure by 1-4", outcome.consequence?.printed)
        assertNull(outcome.critical, "only a severe misfire reaches the critical table")
    }

    @Test
    fun `failure by five to nine is a wrong activation`() {
        // 5+5+5 = 15 against 8: failure by 7.
        val outcome = RitualResolution.attempt(8, light, cor = 0, source = fixed(5))
        assertEquals(7, outcome.marginOfFailure)
        assertEquals("Failure by 5-9", outcome.consequence?.printed)
        assertNull(outcome.critical)
    }

    @Test
    fun `a severe misfire rolls the critical table at minus two`() {
        // Check 5+5+5 = 15 vs target 4 -> failure by 11. Critical roll
        // 4+4+4 = 12, shifted to 10 -> the 9-10 band, not 11-12.
        val outcome = RitualResolution.attempt(4, light, cor = 0, source = scripted(5, 5, 5, 4, 4, 4))
        assertEquals(11, outcome.marginOfFailure)
        assertEquals("Failure by 10+", outcome.consequence?.printed)
        assertEquals(10, outcome.criticalRoll)
        assertEquals("9-10", outcome.critical?.printed)
    }

    @Test
    fun `a rolled seventeen consults the table with no shift`() {
        // The Quick table names this row by the ROLL -- "Critical Failure
        // (17-18)" -- not by a margin, so it is entered before the by-10+ band even
        // though GURPS would call both a critical failure.
        // Check 6+6+5 = 17 vs 16 (margin 1). Critical roll 4+4+4 = 12, unshifted.
        val outcome = RitualResolution.attempt(16, light, cor = 0, source = scripted(6, 6, 5, 4, 4, 4))
        assertEquals("Critical Failure", outcome.consequence?.printed)
        assertEquals(12, outcome.criticalRoll)
        assertEquals("11-12", outcome.critical?.printed)
    }

    @Test
    fun `a rolled eighteen beats the by-ten band even when both apply`() {
        // 18 vs a target of 4 is a failure by 14 AND a rolled 18. The book's
        // own row is named for the roll, so it wins and the -2 is not applied.
        val outcome = RitualResolution.attempt(4, light, cor = 0, source = scripted(6, 6, 6, 4, 4, 4))
        assertEquals("Critical Failure", outcome.consequence?.printed)
        assertEquals(12, outcome.criticalRoll, "no -2 shift on this row")
    }

    @Test
    fun `a rolled eighteen fails even a target it did not overshoot`() {
        // REGRESSION (Codex): a master ritualist at skill 22 has a target of
        // 22, so an 18 comes in "under" it. Deciding success by margin
        // reported the book's own Critical Failure row as a success.
        val outcome = RitualResolution.attempt(22, light, cor = 0, source = scripted(6, 6, 6, 4, 4, 4))
        assertFalse(outcome.succeeded)
        assertEquals("Critical Failure", outcome.consequence?.printed)
        assertEquals(0, outcome.marginOfFailure, "nothing was overshot")
        assertEquals("11-12", outcome.critical?.printed)
    }

    @Test
    fun `the shift cannot push the roll off the bottom of the table`() {
        // 3d6 of 3, less 2, is 1 -- and the table starts at 3.
        val outcome = RitualResolution.attempt(4, light, cor = 0, source = scripted(5, 5, 5, 1, 1, 1))
        assertEquals(3, outcome.criticalRoll)
        assertEquals("3-4", outcome.critical?.printed)
    }

    @Test
    fun `every 3d6 result finds exactly one critical row`() {
        (3..18).forEach { roll ->
            val row = RitualResolution.criticalFor(roll)
            assertTrue(roll >= row.low && roll <= row.high, "roll $roll got ${row.printed}")
        }
    }

    @Test
    fun `a roll outside 3d6 is a bad call`() {
        assertThrows<IllegalArgumentException> { RitualResolution.criticalFor(2) }
        assertThrows<IllegalArgumentException> { RitualResolution.criticalFor(19) }
    }

    @Test
    fun `three critical rows charge corruption and the rest charge none`() {
        assertEquals(1, RitualResolution.criticalFor(13).corGained)
        assertEquals(1, RitualResolution.criticalFor(15).corGained)
        assertEquals(2, RitualResolution.criticalFor(17).corGained)
        // Catastrophe is the worst row and charges no CoR at all -- what it
        // costs is the GM's invention, which is not a number.
        assertNull(RitualResolution.criticalFor(18).corGained)
        assertNull(RitualResolution.criticalFor(3).corGained)
    }

    @Test
    fun `the critical table still says what the rules read off it`() {
        // Guard against silent repair: one row charges 1 CoR and one charges
        // two. If the content stops saying so, the data changed.
        assertTrue("1 CoR" in RitualResolution.criticalFor(13).outcome)
        assertTrue("2 CoR" in RitualResolution.criticalFor(17).outcome)
    }

    // --- preparation and cost ----------------------------------------------

    private fun weight(name: String) = RitualResolution.weights().first { it.weight == name }

    @Test
    fun `every ritual costs SPI, and two of the cells are not one number`() {
        // The app charged nothing for a ritual until this slice, which made
        // one repeatable forever -- the opposite of what SPI is here for.
        assertEquals(2, RitualResolution.spiFor(weight("Light")))
        assertEquals(4, RitualResolution.spiFor(weight("Moderate")))
        assertEquals(6, RitualResolution.spiFor(weight("Moderate"), chosen = 6))
        assertThrows<IllegalArgumentException> {
            RitualResolution.spiFor(weight("Moderate"), chosen = 7)
        }
        assertThrows<IllegalArgumentException> {
            RitualResolution.spiFor(weight("Light"), chosen = 1)
        }
    }

    @Test
    fun `a heavy ritual's cost is open at the top`() {
        // "8+ SPI": the printed number is a floor, so 20 is legal and 7 is not.
        val heavy = weight("Heavy")
        assertTrue(heavy.spiOpen)
        assertEquals(8, RitualResolution.spiFor(heavy))
        assertEquals(20, RitualResolution.spiFor(heavy, chosen = 20))
        assertThrows<IllegalArgumentException> { RitualResolution.spiFor(heavy, chosen = 7) }
    }

    /**
     * v6.9i's Quick table adds a Major row: -6, 3 hr, "12+ SPI". The weight
     * is what charges the fuel now -- the worked rituals that once argued for
     * the complexity ladder are gone, and the ladder prints no SPI column.
     */
    @Test
    fun `a major ritual is minus six and its fuel is open from twelve`() {
        val major = weight("Major")
        assertEquals(-6, major.modifier)
        assertEquals("3 hr", major.baseTime)
        assertEquals("12+ SPI", major.printedCost)
        assertEquals(12, RitualResolution.spiFor(major))
        assertThrows<IllegalArgumentException> { RitualResolution.spiFor(major, chosen = 11) }
        assertEquals(
            listOf("Light", "Moderate", "Heavy", "Major"),
            RitualResolution.weights().map { it.weight },
        )
    }

    @Test
    fun `an open cost cannot be pushed past what an Int holds`() {
        // REGRESSION (Codex): the open cell used Int.MAX_VALUE as its ceiling,
        // so chosen + burned wrapped NEGATIVE -- and a negative cost would
        // have been paid back as SPI rather than charged.
        val heavy = weight("Heavy")
        assertThrows<IllegalArgumentException> {
            RitualResolution.spiFor(heavy, chosen = Int.MAX_VALUE, burned = 1)
        }
        assertThrows<IllegalArgumentException> { RitualResolution.spiFor(heavy, burned = -1) }
    }

    @Test
    fun `preparation moves the target, and none of it is required`() {
        val light = weight("Light")
        val excellent = RitualResolution.preparationNamed("Excellent")
        assertEquals(3, excellent.modifier)
        assertEquals(14, RitualResolution.targetFor(14, light, cor = 0))
        assertEquals(17, RitualResolution.targetFor(14, light, cor = 0, preparation = excellent))
        // The quick mode has no Phase I at all, so null is a real answer.
        assertEquals(14, RitualResolution.targetFor(14, light, cor = 0, preparation = null))
    }

    @Test
    fun `the ranged and open-ended preparation rows keep both ends`() {
        val good = RitualResolution.preparationNamed("Good")
        assertEquals(1, good.modifier)
        assertEquals(2, good.best)
        val none = RitualResolution.preparationNamed("None")
        assertEquals(-4, none.modifier)
        assertTrue(none.openEnded, "'-4 or worse' is a floor, not a value")
        assertThrows<IllegalArgumentException> { RitualResolution.preparationNamed("Perfect") }
    }

    /**
     * The pick has to survive three hops -- attempt, targetFor, modifierTotal
     * -- and a dropped argument at any of them still compiles and still
     * rolls. So the pick is read back off the outcome of a real attempt, and
     * not only off the function that computes the number.
     */
    @Test
    fun `a pick reaches the target and the roll that is made against it`() {
        val good = RitualResolution.preparationNamed("Good")
        assertEquals(15, RitualResolution.targetFor(14, light, cor = 0, preparation = good))
        assertEquals(
            16,
            RitualResolution.targetFor(14, light, cor = 0, preparation = good, preparationValue = 2),
        )
        // Three fives are 15: a success by exactly 1 against 16, by 0 against 15.
        val unpicked = RitualResolution.attempt(14, light, 0, fixed(5), good)
        val picked = RitualResolution.attempt(14, light, 0, fixed(5), good, preparationValue = 2)
        assertEquals(15, unpicked.target)
        assertEquals(16, picked.target)
        assertEquals(unpicked.roll.total, picked.roll.total, "the same dice, a different target")
        // The target REPORTED and the target ROLLED AGAINST are two values,
        // and a pick that reached only the first would print 16 over dice
        // judged against 15.
        assertEquals(picked.target, picked.roll.target)

        // And the pick changes what happened, not just what is printed: three
        // fives against 14 miss by one, and against 15 they land.
        val missed = RitualResolution.attempt(13, light, 0, fixed(5), good)
        val landed = RitualResolution.attempt(13, light, 0, fixed(5), good, preparationValue = 2)
        assertTrue(!missed.roll.outcome.isSuccess, "15 against 14")
        assertTrue(landed.roll.outcome.isSuccess, "15 against 15")

        val strong = RitualMode.linkNamed("Strong")
        val adequate = RitualMode.QUICK.preparationNamed("Adequate")
        assertEquals(
            17,
            RitualResolution.targetFor(
                14, light, cor = 0, preparation = adequate, mode = RitualMode.QUICK,
                link = strong, linkValue = 3,
            ),
        )
        val linked = RitualResolution.attempt(
            14, light, 0, fixed(5), adequate, mode = RitualMode.QUICK,
            link = strong, linkValue = 3,
        )
        assertEquals(17, linked.target)
        assertEquals(17, linked.roll.target)
    }

    /**
     * The dialog prints "3d6 <= N" and then rolls. Handing the same fifteen
     * inputs to a second call is two sites answering one question, and the
     * second runs in a click handler that can read a row the first never saw
     * (6ef). So the roll is made against the number that was PRINTED, and
     * [RitualResolution.attempt] is that roll with the target composed first.
     */
    @Test
    fun `a roll against a composed target is the attempt that composed it`() {
        val good = RitualResolution.preparationNamed("Good")
        val target = RitualResolution.targetFor(14, light, cor = 0, preparation = good, preparationValue = 2)
        val rolled = RitualResolution.rollAgainst(target, fixed(5))
        assertEquals(16, rolled.target)
        assertEquals(16, rolled.roll.target, "judged against the target it was handed")
        assertEquals(
            RitualResolution.attempt(14, light, 0, fixed(5), good, preparationValue = 2),
            rolled,
        )

        // The table's second sample comes off the same source, in the same
        // order: 5+5+5 against 4 misses by 11, then 4+4+3 shifted by -2. The
        // last face differs on purpose -- `scripted` repeats its final face,
        // so 4+4+4 reads the same with a draw taken and thrown away between.
        val misfire = RitualResolution.rollAgainst(4, scripted(5, 5, 5, 4, 4, 3))
        assertEquals(9, misfire.criticalRoll)
        assertEquals(
            RitualResolution.attempt(4, light, cor = 0, source = scripted(5, 5, 5, 4, 4, 3)),
            misfire,
        )
    }

    @Test
    fun `a botched preparation can cost more than the ritual's own weight`() {
        // -4 preparation against a Heavy ritual's -4 is -8 before corruption:
        // worth pinning, because it is the case a GM will not expect.
        val target = RitualResolution.targetFor(
            skillLevel = 14,
            weight = weight("Heavy"),
            cor = 0,
            preparation = RitualResolution.preparationNamed("None"),
        )
        assertEquals(6, target)
    }

    @Test
    fun `a quick ritual's excellent preparation is worth one less`() {
        // The two modes print different tables and the app used the Full one
        // for everything. Same declared preparation, one point apart.
        fun target(mode: RitualMode) = RitualResolution.targetFor(
            skillLevel = 12,
            weight = weight("Light"),
            cor = 0,
            preparation = mode.preparationNamed("Excellent"),
            mode = mode,
        )
        assertEquals(15, target(RitualMode.FULL))
        assertEquals(14, target(RitualMode.QUICK))
    }

    @Test
    fun `a quick ritual adds the target link and a full one refuses it`() {
        assertEquals(
            18,
            RitualResolution.targetFor(
                skillLevel = 12,
                weight = weight("Light"),
                cor = 0,
                preparation = RitualMode.QUICK.preparationNamed("Excellent"),
                mode = RitualMode.QUICK,
                link = RitualMode.linkNamed("Perfect"),
            ),
            "12 + 2 preparation + 4 link",
        )
        assertThrows<IllegalArgumentException> {
            RitualResolution.targetFor(
                skillLevel = 12,
                weight = weight("Light"),
                cor = 0,
                preparation = RitualMode.FULL.preparationNamed("Excellent"),
                mode = RitualMode.FULL,
                link = RitualMode.linkNamed("Perfect"),
            )
        }
    }

    /**
     * The mirror of the test above, and the reason this slice exists: the +1
     * was printed on p. 63 and reached the target through nothing. The book
     * prints its own formula for what it lands on -- "Effective skill =
     * Ritualistic Magic + Preparation Mod + Flourish Bonus" -- which is why
     * this is the roll it moves and not, say, the fuel.
     */
    @Test
    fun `a full ritual adds the flourish bonus and a quick one refuses it`() {
        assertEquals(
            16,
            RitualResolution.targetFor(
                skillLevel = 12,
                weight = weight("Light"),
                cor = 0,
                preparation = RitualMode.FULL.preparationNamed("Excellent"),
                mode = RitualMode.FULL,
                flourish = true,
            ),
            "12 + 3 preparation + 1 flourish",
        )
        assertThrows<IllegalArgumentException> {
            RitualResolution.targetFor(
                skillLevel = 12,
                weight = weight("Light"),
                cor = 0,
                preparation = RitualMode.QUICK.preparationNamed("Excellent"),
                mode = RitualMode.QUICK,
                flourish = true,
            )
        }
    }

    @Test
    fun `the flourish composes with weight and corruption rather than replacing them`() {
        // A GM's award does not buy off the ritual's own difficulty. Heavy is
        // -4 and 8 CoR is -1, so a well-delivered heavy ritual by a corrupt
        // caster is still worse off than a plain light one.
        assertEquals(
            8,
            RitualResolution.targetFor(
                skillLevel = 12,
                weight = weight("Heavy"),
                cor = 8,
                preparation = RitualMode.FULL.preparationNamed("Adequate"),
                mode = RitualMode.FULL,
                flourish = true,
            ),
            "12 - 4 heavy - 1 for 8 CoR + 0 preparation + 1 flourish",
        )
        // And every caller that does not award one is where it was before.
        assertEquals(
            7,
            RitualResolution.targetFor(
                skillLevel = 12,
                weight = weight("Heavy"),
                cor = 8,
                preparation = RitualMode.FULL.preparationNamed("Adequate"),
                mode = RitualMode.FULL,
            ),
        )
    }

    @Test
    fun `speaking the ritual in an accented mystical tongue costs two`() {
        // Ch. 3, p. 14: "Rituals using the language at -2". The clause had
        // been printed in a table nothing read until the Languages slice.
        fun target(language: com.pathways.rules.content.LanguageProficiency?) =
            RitualResolution.targetFor(
                skillLevel = 12, weight = weight("Light"), cor = 0, language = language,
            )

        assertEquals(12, target(null), "no language stated leaves the target alone")
        assertEquals(12, target(Languages.proficiency(Languages.MYSTICAL, "Native")))
        assertEquals(10, target(Languages.proficiency(Languages.MYSTICAL, "Accented")))
    }

    @Test
    fun `a caster who cannot speak the language does not roll at a penalty`() {
        // "Cannot perform rituals in the language" is a refusal, not a
        // modifier. Rolling anyway at some invented penalty would let a
        // character do what the book forbids, and succeed at it.
        assertThrows<IllegalArgumentException> {
            RitualResolution.targetFor(
                skillLevel = 12, weight = weight("Light"), cor = 0,
                language = Languages.proficiency(Languages.MYSTICAL, "Broken"),
            )
        }
        // A common language is refused for the other reason: its table says
        // nothing about rituals at all.
        assertThrows<IllegalArgumentException> {
            RitualResolution.targetFor(
                skillLevel = 12, weight = weight("Light"), cor = 0,
                language = Languages.proficiency(Languages.COMMON, "Native"),
            )
        }
    }

    @Test
    fun `the language penalty stacks with weight, corruption and preparation`() {
        // Four independent sources on one target, which is the case a GM will
        // not add up by hand: 12 - 4 (Heavy) - 1 (CoR 3) + 3 (Excellent)
        // - 2 (Accented).
        val target = RitualResolution.targetFor(
            skillLevel = 12,
            weight = weight("Heavy"),
            cor = 3,
            preparation = RitualResolution.preparationNamed("Excellent"),
            language = Languages.proficiency(Languages.MYSTICAL, "Accented"),
        )
        assertEquals(8, target)
    }

    @Test
    fun `burning SPI re-reads the dice already cast against a higher target`() {
        // p. 66: "you may spend additional SPI after seeing your roll to boost
        // it (+1 per SPI, no cap)". The total never moves -- rolling again
        // would be a different ritual.
        val plain = RitualResolution.attempt(14, light, cor = 0, source = fixed(4))
        val pushed = RitualResolution.pushed(plain, 2)
        assertEquals(plain.roll.total, pushed.roll.total)
        assertEquals(plain.target + 2, pushed.target)
        assertEquals(plain.target + 2, pushed.roll.target)
        assertThrows<IllegalArgumentException> { RitualResolution.pushed(plain, -1) }
    }

    @Test
    fun `a push can carry a failed ritual over the line`() {
        // 3d6 of 5s is 15 against a Heavy target of 10: a failure by 5.
        val failed = RitualResolution.attempt(14, heavy, cor = 0, source = fixed(5))
        assertFalse(failed.succeeded)
        val saved = RitualResolution.pushed(failed, 5)
        assertTrue(saved.succeeded)
        assertNull(saved.consequence)
        assertEquals(0, saved.marginOfFailure)
        assertNotNull(saved.success)
    }

    /**
     * A severe misfire, reached by MARGIN rather than by a rolled 17-18.
     *
     * Skill 6 on a Heavy ritual is a target of 2, and 5s on 3d6 is a 15 --
     * missed by 13, which is the "Failure by 10+" row, while the total stays
     * under the 17 that would take the other one. The distinction is the whole
     * point: only the by-margin row can be pushed off the table.
     */
    private val severeMisfire
        get() = RitualResolution.attempt(6, heavy, cor = 0, source = fixed(5))

    @Test
    fun `a push that clears the Critical Failure Table clears its roll too`() {
        // Pushing shrinks the margin, and a consequence row that no longer
        // reaches the table must not keep a roll the GM would still read --
        // nor the CoR that row charges.
        val misfire = severeMisfire
        assertEquals(13, misfire.marginOfFailure)
        assertNotNull(misfire.criticalRoll)
        assertNotNull(misfire.critical)
        val pushed = RitualResolution.pushed(misfire, 4)
        assertEquals(9, pushed.marginOfFailure)
        assertNull(pushed.criticalRoll)
        assertNull(pushed.critical)
    }

    @Test
    fun `a push that stays on the table keeps the roll it already made`() {
        // Re-rolling here would hand the caster a fresh chance at a milder
        // row that the book never offered.
        val misfire = severeMisfire
        val pushed = RitualResolution.pushed(misfire, 1)
        assertEquals(12, pushed.marginOfFailure)
        assertEquals(misfire.criticalRoll, pushed.criticalRoll)
        assertEquals(misfire.critical, pushed.critical)
    }

    @Test
    fun `a rolled 17 or 18 takes no push, because nothing can move it`() {
        // Its consequence row is named for the ROLL, not the margin, and 17-18
        // fails against any target -- so a point spent here buys nothing.
        // EffortBurn.canPush refuses a critical-only rule for the same reason.
        //
        // BOTH totals, because the row names both: a threshold that slipped to
        // 18 would let a 17 be pushed with only the 18 under test (Codex).
        val eighteen = RitualResolution.attempt(14, light, cor = 0, source = fixed(6))
        val seventeen = RitualResolution.attempt(
            14, light, cor = 0, source = scripted(6, 6, 5),
        )
        assertEquals(18, eighteen.roll.total)
        assertEquals(17, seventeen.roll.total)
        for (disaster in listOf(seventeen, eighteen)) {
            assertEquals(Outcome.CRITICAL_FAILURE, disaster.roll.outcome)
            assertFalse(RitualResolution.canPush(disaster))
            assertEquals(disaster, RitualResolution.pushed(disaster, 5))
        }
    }

    @Test
    fun `a push improves the success band it lands on`() {
        // Named rather than merely different: a pushed outcome that dropped
        // its band to null is also "not equal" (Codex).
        val plain = RitualResolution.attempt(14, light, cor = 0, source = fixed(4))
        assertTrue(plain.succeeded)
        // By the band's own lower bound rather than its printed text: the
        // book sets that with an en-dash and a hand-typed hyphen would fail
        // for a reason that has nothing to do with the rule.
        assertEquals(0, plain.success?.from)
        val pushed = RitualResolution.pushed(plain, 6)
        assertEquals(3, pushed.success?.from)
    }

    /**
     * Step 4 prints nothing above "Success by 3+" (line 4094), so a point
     * spent there buys nothing: play session 4 was offered +1 to +4 at a
     * margin of 11. Divination.canPush refuses its clearest row the same way
     * (6hw). One below the band stays pushable.
     */
    @Test
    fun `a success already in the top band takes no push`() {
        val surpassing = RitualResolution.attempt(14, light, cor = 0, source = fixed(3))
        assertEquals(null, surpassing.success?.to)
        assertFalse(RitualResolution.canPush(surpassing))
        assertFalse(Boons.canPush(surpassing))
        val asIntended = RitualResolution.attempt(14, light, cor = 0, source = fixed(4))
        assertTrue(RitualResolution.canPush(asIntended))
    }
}
