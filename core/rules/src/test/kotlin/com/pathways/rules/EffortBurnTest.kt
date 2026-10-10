package com.pathways.rules

import com.pathways.model.Attr
import com.pathways.model.ControllingAttribute
import com.pathways.model.Difficulty
import com.pathways.model.Outcome
import com.pathways.model.SkillKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** Ch. 2's burn (p. 9) -- spending a pool to push a roll already made. */
class EffortBurnTest {

    private fun key(attr: ControllingAttribute) =
        SkillKey("Whatever", null, attr, Difficulty.AVERAGE)

    @Test
    fun `the book prints two pools, and only one of them has a cap`() {
        assertEquals(listOf("FP", "SPI"), EffortBurn.all().map { it.pool })
        assertEquals(5, EffortBurn.maxFor(BurnKind.PHYSICAL))
        // Absent, not a large number: "There is no cap -- you may spend as
        // much SPI as you have". A ceiling here would be one this app made up.
        assertNull(EffortBurn.maxFor(BurnKind.MYSTICAL))
    }

    /**
     * ST, DX and HT are the FP row's own list; SPI-based rolls are the SPI
     * row's. IQ, Will and Per appear in neither, so they get NULL rather than
     * a default -- a caller must not push a Research roll with fatigue on the
     * strength of the book saying nothing. The refusal
     * `Languages.ritualModifier` makes about a common tongue's silence (6x).
     */
    @Test
    fun `the mental attributes fall in the gap and are refused`() {
        assertEquals(
            BurnKind.PHYSICAL,
            EffortBurn.kindFor(key(ControllingAttribute.Buyable(Attr.ST))),
        )
        assertEquals(
            BurnKind.PHYSICAL,
            EffortBurn.kindFor(key(ControllingAttribute.Buyable(Attr.DX))),
        )
        assertEquals(
            BurnKind.PHYSICAL,
            EffortBurn.kindFor(key(ControllingAttribute.Buyable(Attr.HT))),
        )
        assertEquals(BurnKind.MYSTICAL, EffortBurn.kindFor(key(ControllingAttribute.Spi)))
        assertNull(EffortBurn.kindFor(key(ControllingAttribute.Buyable(Attr.IQ))))
        assertNull(EffortBurn.kindFor(key(ControllingAttribute.Will)))
        assertNull(EffortBurn.kindFor(key(ControllingAttribute.Per)))
    }

    @Test
    fun `one point buys one, up to the printed cap`() {
        assertEquals(3, EffortBurn.bonusFor(BurnKind.PHYSICAL, 3))
        assertEquals(5, EffortBurn.bonusFor(BurnKind.PHYSICAL, 5))
        assertThrows<IllegalArgumentException> { EffortBurn.bonusFor(BurnKind.PHYSICAL, 6) }
        assertThrows<IllegalArgumentException> { EffortBurn.bonusFor(BurnKind.PHYSICAL, -1) }
        // No cap on the mystical side, so a large spend is the character's
        // problem and the tracker's, not this object's.
        assertEquals(40, EffortBurn.bonusFor(BurnKind.MYSTICAL, 40))
    }

    /**
     * **"Max +5 per ROLL", not per spend.** Five separate one-point pushes
     * reach the ceiling exactly as one five-point push does, so the running
     * total has to be part of the check -- a per-call guard lets a stepper
     * walk past the book by being clicked twice. Enforced here rather than by
     * the screen, because a UI computing its own ceiling is a second copy of
     * one rule, and two copies is how these numbers stop reconciling (6ai).
     */
    @Test
    fun `the cap counts everything already spent on the same roll`() {
        assertEquals(2, EffortBurn.bonusFor(BurnKind.PHYSICAL, 2, alreadyBurned = 3))
        val thrown = assertThrows<IllegalArgumentException> {
            EffortBurn.bonusFor(BurnKind.PHYSICAL, 3, alreadyBurned = 3)
        }
        assertTrue(thrown.message!!.contains("already spent"), thrown.message)
        assertEquals(5, EffortBurn.remaining(BurnKind.PHYSICAL, 0))
        assertEquals(2, EffortBurn.remaining(BurnKind.PHYSICAL, 3))
        assertEquals(0, EffortBurn.remaining(BurnKind.PHYSICAL, 5))
        // Uncapped stays uncapped however much has gone already.
        assertNull(EffortBurn.remaining(BurnKind.MYSTICAL, 40))
        assertEquals(9, EffortBurn.bonusFor(BurnKind.MYSTICAL, 9, alreadyBurned = 40))
    }

    /**
     * "You cannot spend both FP and SPI on the same roll." Refused rather than
     * silently preferring one: a caller handed a quietly dropped pool would
     * charge for it and not receive it.
     */
    @Test
    fun `both pools on one roll is refused`() {
        EffortBurn.requireOnePool(fp = 3, spi = 0)
        EffortBurn.requireOnePool(fp = 0, spi = 3)
        EffortBurn.requireOnePool(fp = 0, spi = 0)
        val thrown = assertThrows<IllegalArgumentException> {
            EffortBurn.requireOnePool(fp = 1, spi = 1)
        }
        assertTrue(thrown.message!!.contains("not both"), thrown.message)
    }

    // ---- pushing a roll already made -------------------------------------

    /** Three faces that sum to [total]. RandomSource rolls one die at a time. */
    private fun facesFor(total: Int): RandomSource {
        val each = total / 3
        val faces = intArrayOf(
            each + if (total % 3 > 0) 1 else 0,
            each + if (total % 3 > 1) 1 else 0,
            each,
        )
        var next = 0
        return RandomSource { faces[next++] }
    }

    private fun rolled(total: Int, target: Int) = SkillRoll.at(
        label = "Stealth",
        base = target,
        key = key(ControllingAttribute.Buyable(Attr.DX)),
        source = facesFor(total),
    )

    /**
     * **The same dice, read against a higher number.** The book allows this
     * after the result is seen, so re-ROLLING would answer a different
     * question -- and on a seeded source it would desynchronise every roll
     * after it. The total is asserted unchanged, which is what proves it.
     */
    @Test
    fun `burning re-reads the existing total rather than rolling again`() {
        val before = rolled(total = 12, target = 10)
        assertEquals(Outcome.FAILURE, before.roll!!.outcome)

        val after = EffortBurn.applied(before, BurnKind.PHYSICAL, points = 2)
        assertEquals(12, after.roll!!.total, "the dice were rolled a second time")
        assertEquals(12, after.target)
        assertEquals(Outcome.SUCCESS, after.roll!!.outcome)
        assertEquals(0, after.roll!!.margin)
    }

    /** Zero points changes nothing, and does not pretend to. */
    @Test
    fun `burning nothing leaves the roll exactly as it was`() {
        val before = rolled(total = 9, target = 10)
        assertEquals(before, EffortBurn.applied(before, BurnKind.PHYSICAL, points = 0))
    }

    /**
     * A pushed roll can cross a DEGREE boundary, not only the pass line --
     * `SkillRollOutcome.degree` reads the margin, so it moves with the target.
     */
    @Test
    fun `pushing deepens the degree as well as the outcome`() {
        val before = rolled(total = 8, target = 10)
        assertEquals(SuccessDegree.PLAIN, before.degree)
        val after = EffortBurn.applied(before, BurnKind.PHYSICAL, points = 3)
        assertEquals(SuccessDegree.EXCELLENT, after.degree)
    }

    /**
     * A forbidden roll has no total to re-read. Returned untouched rather
     * than given an invented one -- the same reason `SkillRollOutcome.roll` is
     * null there instead of holding a hidden roll.
     */
    @Test
    fun `a roll that never happened cannot be pushed`() {
        val forbidden = SkillRollOutcome(
            label = "Ritual",
            baseTarget = 9,
            target = 9,
            applied = emptyList(),
            forbidden = true,
            requiresCritical = false,
            roll = null,
        )
        assertEquals(forbidden, EffortBurn.applied(forbidden, BurnKind.MYSTICAL, points = 4))
    }

    /**
     * **The breakdown must still sum to the target.** `applied()` moves the
     * target, so the burn joins the contribution list -- leaving it out would
     * print a modifier list that no longer adds up to the number above it,
     * the arithmetic-that-does-not-reconcile defect 6ag added its own
     * 300-case test for. Checked here across every legal spend.
     */
    @Test
    fun `the contributions always sum to the target the burn produced`() {
        for (points in 0..5) {
            val before = rolled(total = 10, target = 11)
            val after = EffortBurn.applied(before, BurnKind.PHYSICAL, points)
            assertEquals(
                after.target - after.baseTarget,
                after.applied.sumOf { it.delta },
                "$points burned: the breakdown no longer sums to the target",
            )
        }
    }

    /**
     * **A roll only a critical can win cannot be pushed, and that is
     * arithmetic rather than a reading.** `requiresCritical` demotes any
     * ordinary success, and whether a roll is critical depends on the TOTAL
     * (3 or 4), which a burn never touches. So spending here buys nothing --
     * and on the mystical side it would buy permanent Soul Debt CoR for it
     * (6m). Refused in rules, so a screen that forgets to ask still cannot
     * charge for it.
     */
    @Test
    fun `a roll only a critical can win is not pushable`() {
        val demoted = rolled(total = 9, target = 8).copy(requiresCritical = true)
        assertEquals(Outcome.FAILURE, demoted.effectiveOutcome)
        assertTrue(EffortBurn.canPush(rolled(total = 9, target = 8)))
        assertTrue(!EffortBurn.canPush(demoted))
        // ...and applying anyway is a no-op rather than a silent charge.
        assertEquals(demoted, EffortBurn.applied(demoted, BurnKind.PHYSICAL, points = 5))
    }

    @Test
    fun `the book's own first example of a mystical push can be pushed`() {
        // "any SPI-based or supernatural skill roll (Ritualistic Magic, ...)".
        // Ritualistic Magic is IQ/Very Hard, so reading only the SPI-based
        // half left the row's FIRST example unable to use the rule it names.
        val ritual = SkillKey(
            Conditions.RITUAL_SKILL,
            null,
            ControllingAttribute.Buyable(Attr.IQ),
            Difficulty.VERY_HARD,
        )
        // Mystical, not physical: the FP row lists ST, DX and HT only.
        assertEquals(BurnKind.MYSTICAL, EffortBurn.kindFor(ritual))
    }

    @Test
    fun `an ordinary IQ skill still gets no push`() {
        // The gap the refusal exists for: neither row mentions the mental
        // attributes, so Research is pushed by nothing.
        val research = SkillKey(
            "Research",
            null,
            ControllingAttribute.Buyable(Attr.IQ),
            Difficulty.AVERAGE,
        )
        assertNull(EffortBurn.kindFor(research))
    }

    @Test
    fun `Occultism is reported rather than pushed`() {
        // Its entry is qualified by what is rolled AGAINST -- "vs. Beyonder
        // phenomena" -- and a SkillKey carries no subject matter. Granting it
        // always pushes rolls the book does not; refusing it in silence hides
        // a printed rule. So it is named to the GM instead.
        val occultism = SkillKey(
            EffortBurn.CONDITIONAL_SKILL,
            null,
            ControllingAttribute.Buyable(Attr.IQ),
            Difficulty.HARD,
        )
        assertNull(EffortBurn.kindFor(occultism))
        assertTrue(EffortBurn.conditionalScope(occultism)!!.contains("Beyonder phenomena"))
        assertNull(EffortBurn.conditionalScope(SkillKey(
            "Research",
            null,
            ControllingAttribute.Buyable(Attr.IQ),
            Difficulty.AVERAGE,
        )))
    }

    @Test
    fun `the named skills are read from the row's own sentence`() {
        // The set lives in Kotlin because the names are inside prose; this
        // pins the cell they came from, so a re-extraction that dropped an
        // example fails here rather than leaving the set floating.
        val scope = EffortBurn.pool(BurnKind.MYSTICAL).scope
        EffortBurn.SUPERNATURAL_SKILLS.forEach {
            assertTrue(scope.contains(it), "the SPI row no longer names $it: $scope")
        }
        assertTrue(scope.contains(EffortBurn.CONDITIONAL_SKILL))
        assertTrue(scope.contains("supernatural"))
    }

    @Test
    fun `a drained Beyonder cannot buy a mystical push`() {
        // The fifth SPI spend, and the one 6ar missed: the burn is not a
        // dialog of its own, so a sweep of the dialogs never reached it. The
        // screen's only gate was `maxSpi > 0`, which excludes a MORTAL and not
        // a Beyonder who has spent everything -- who was still offered chips,
        // still spent below 0, and still bought permanent Soul Debt CoR.
        assertEquals(0, EffortBurn.affordable(BurnKind.MYSTICAL, 0, spiAvailable = 0))
        // Already in debt from spirit-body damage: nothing more may be bought,
        // and the answer is 0 rather than a negative a caller might spend.
        assertEquals(0, EffortBurn.affordable(BurnKind.MYSTICAL, 0, spiAvailable = -4))
        // What is left is what may be bought, and the book caps this bonus at
        // nothing -- so the pool is the only ceiling on this side.
        assertEquals(3, EffortBurn.affordable(BurnKind.MYSTICAL, 0, spiAvailable = 3))
        assertNull(EffortBurn.maxFor(BurnKind.MYSTICAL))
    }

    @Test
    fun `the physical push keeps its unbounded reach`() {
        // p. 42 PRICES fatigue below 0 at 1 HP per point instead of forbidding
        // it, while ch. 7 forbids the same for SPI outright. That contrast was
        // 6ar's whole evidence, so capping FP by its balance here would erase
        // a distinction the book draws in two directions at once.
        assertEquals(5, EffortBurn.affordable(BurnKind.PHYSICAL, 0, spiAvailable = 0))
        // The +5 cap is per ROLL and still wins on this side.
        assertEquals(2, EffortBurn.affordable(BurnKind.PHYSICAL, 3, spiAvailable = 0))
    }

    @Test
    fun `the smaller of the two ceilings wins`() {
        // Two different limits: what the book allows as a bonus, and what the
        // character can pay. Neither subsumes the other.
        assertEquals(1, EffortBurn.affordable(BurnKind.PHYSICAL, 4, spiAvailable = 99))
        assertEquals(1, EffortBurn.affordable(BurnKind.MYSTICAL, 9, spiAvailable = 1))
    }

    /**
     * Two taps a few milliseconds apart both reach the handler of the row
     * that was drawn before the first (6ei), so the second asks for a chip
     * the row would no longer offer. The skill and attack dialogs handed
     * that to [EffortBurn.applied], whose cap check throws: +3 twice took the
     * app down with the first three FP already charged.
     */
    @Test
    fun `a second push past the cap buys nothing instead of throwing`() {
        val first = EffortBurn.pushed(rolled(total = 14, target = 10), BurnKind.PHYSICAL, 3, alreadyBurned = 0, spiAvailable = 0)
        assertEquals(13, first!!.target)
        // And the push says what it was, in the line the result prints.
        assertEquals(LedgerLine("Pushed with FP", 3, com.pathways.model.SourceKind.SITUATIONAL), first.lines().last())
        assertNull(EffortBurn.pushed(first, BurnKind.PHYSICAL, 3, alreadyBurned = 3, spiAvailable = 0))
        // What the cap still allows is still sold, and lands on the first.
        assertEquals(15, EffortBurn.pushed(first, BurnKind.PHYSICAL, 2, alreadyBurned = 3, spiAvailable = 0)!!.target)
    }

    /**
     * The same second tap on the uncapped row. Nothing threw there, so it
     * spent: 3 SPI left, +3 twice, and the second three were Soul Debt,
     * which is permanent CoR. [EffortBurn.affordable] is what offered the
     * chip; it is asked again of the pool as it stands.
     */
    @Test
    fun `a mystical push the pool no longer holds buys nothing`() {
        val before = rolled(total = 14, target = 10)
        assertEquals(13, EffortBurn.pushed(before, BurnKind.MYSTICAL, 3, alreadyBurned = 0, spiAvailable = 3)!!.target)
        assertNull(EffortBurn.pushed(before, BurnKind.MYSTICAL, 3, alreadyBurned = 3, spiAvailable = 0))
        assertNull(EffortBurn.pushed(before, BurnKind.MYSTICAL, 0, alreadyBurned = 0, spiAvailable = 3))
    }

    /**
     * **The two chapters that already burn SPI agree with this one exactly**,
     * and that agreement is the whole warrant for adopting the general rule
     * (6ad's per-table standard). If either ever printed a different rate,
     * this general object would have to be re-argued rather than inherited.
     */
    @Test
    fun `chapter 6_5's own burn rate matches this one`() {
        assertEquals(
            Divination.BURN_PER_SPI,
            EffortBurn.pool(BurnKind.MYSTICAL).perPoint,
        )
    }
}
