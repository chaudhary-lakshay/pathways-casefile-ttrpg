package com.pathways.rules

import com.pathways.model.ControllingAttribute
import com.pathways.model.Difficulty
import com.pathways.model.ModEffect
import com.pathways.model.Outcome
import com.pathways.model.SkillKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** Ch. 6.5's Sequence Immunity (Sec. 5) and its counters (Sec. 6). */
class AntiDivinationTest {

    // ---- the two rules a row can print ------------------------------------

    /**
     * **The slice's centre.** A penalty and a critical-only clause are not
     * interchangeable. At skill 20 a -10 leaves target 10, and an ordinary 10
     * succeeds -- which is precisely what "the penalty cannot be offset by
     * skill alone" forbids. So the clause has to travel separately.
     */
    @Test
    fun `a demigod's penalty and its critical-only rule are separate`() {
        val row = AntiDivination.rowFor(4)
        assertEquals("Seq 4 (Demigod)", row.label)
        assertEquals(-10, row.penalty)
        assertTrue(row.requiresCritical)

        val result = AntiDivination.against(targetSequence = 4, divinerSequence = 8)
        assertEquals(-10, result.penalty)
        assertTrue(result.requiresCritical)

        val mods = AntiDivination.modifiers(result)
        assertEquals(2, mods.size)
        assertTrue(mods.any { it.effect == ModEffect.Flat(-10) })
        assertTrue(mods.any { it.effect == ModEffect.RequireCrit })
    }

    /**
     * The same claim proved through the ENGINE rather than by assertion: a
     * diviner good enough to beat the penalty on the dice still fails, because
     * `RequireCrit` demotes the success [SkillRoll] would otherwise report.
     * Without the second modifier this rolls a plain success.
     */
    @Test
    fun `skill alone cannot buy a success the book forbids`() {
        val key = SkillKey(
            name = Divination.SKILL,
            attr = ControllingAttribute.Spi,
            difficulty = Difficulty.HARD,
        )
        val result = AntiDivination.against(targetSequence = 4, divinerSequence = 8)
        // Skill 20 against -10 leaves a target of 10, and three 4s roll 12...
        // so pick a roll that beats it: three 3s make 9, an ordinary success.
        val out = SkillRoll.at(
            label = "Divination Arts",
            base = 20,
            key = key,
            source = RandomSource { 3 },
            mods = AntiDivination.modifiers(result),
        )
        assertEquals(10, out.target)
        assertTrue(out.requiresCritical)
        assertEquals(Outcome.SUCCESS, out.roll?.outcome) { "the dice did beat it" }
        assertEquals(Outcome.FAILURE, out.effectiveOutcome) { "the book forbids it" }
    }

    /**
     * **Recorded, not repaired.** Seq 2 prints -10 and no critical clause while
     * the two weaker rows print one, so an Angel is the easiest of the three
     * for a skilled diviner. That reads like a slip; the book wins (4.1).
     */
    @Test
    fun `the angel row forbids nothing, and that asymmetry is the book's`() {
        val angel = AntiDivination.rowFor(2)
        assertEquals("Seq 2 (Angel)", angel.label)
        assertEquals(-10, angel.penalty)
        assertFalse(angel.requiresCritical) { "the book prints no clause here" }

        assertTrue(AntiDivination.rowFor(3).requiresCritical)
        assertTrue(AntiDivination.rowFor(4).requiresCritical)
        assertEquals(
            setOf("Seq 4 (Demigod)", "Seq 3 (Saint)"),
            AntiDivination.rows().filter { it.requiresCritical }.map { it.label }.toSet(),
        )
    }

    // ---- the table's edges -------------------------------------------------

    @Test
    fun `a weak target carries no penalty at all`() {
        val row = AntiDivination.rowFor(9)
        assertEquals("Seq 6–9", row.label)
        assertNull(row.penalty) { "printing no penalty is not printing zero" }
        assertEquals(row, AntiDivination.rowFor(6))

        val result = AntiDivination.against(targetSequence = 7, divinerSequence = 9)
        assertEquals(0, result.penalty)
        assertFalse(result.requiresCritical)
        assertTrue(AntiDivination.modifiers(result).isEmpty())
    }

    /**
     * The table stops at Seq 2. Answering "no penalty" for a god would invent
     * the most consequential row in it -- the distinction [Wounds.multiplierFor]
     * draws between a silent cell and a key that is not in the table.
     */
    @Test
    fun `a sequence the table never prints is refused`() {
        assertThrows<IllegalStateException> { AntiDivination.rowFor(1) }
        assertThrows<IllegalStateException> { AntiDivination.rowFor(0) }
        assertThrows<IllegalStateException> { AntiDivination.against(targetSequence = 0) }
    }

    /** Equal-Sequence is printed by exactly one row, and only it uses one. */
    @Test
    fun `only the demigod row prices an equal-sequence diviner`() {
        assertEquals(-3, AntiDivination.rowFor(4).equalPenalty)
        assertEquals(
            -3,
            AntiDivination.against(targetSequence = 4, divinerSequence = 4).penalty,
        )
        assertEquals(1, AntiDivination.rows().count { it.equalPenalty != null })
        // Seq 3 prints none, so an equal-Sequence diviner takes the headline.
        assertNull(AntiDivination.rowFor(3).equalPenalty)
        assertEquals(
            -10,
            AntiDivination.against(targetSequence = 3, divinerSequence = 3).penalty,
        )
    }

    // ---- the counters ------------------------------------------------------

    /**
     * Lower Sequence is stronger, so "2+ Sequences stronger" counts DOWN.
     * Reversed, the advantage lands on the weaker party and still looks right.
     */
    @Test
    fun `sequence advantage counts down, and takes the critical rule with it`() {
        // Seq 2 diviner against a Seq 4 target: two stronger.
        val strong = AntiDivination.against(targetSequence = 4, divinerSequence = 2)
        assertEquals(0, strong.penalty)
        assertFalse(strong.requiresCritical) { "the clause names the penalty it drops" }

        // Seq 6 diviner against a Seq 4 target is WEAKER, not stronger.
        val weak = AntiDivination.against(targetSequence = 4, divinerSequence = 6)
        assertEquals(-10, weak.penalty)
        assertTrue(weak.requiresCritical)

        // One Sequence stronger is not two.
        assertEquals(
            -10,
            AntiDivination.against(targetSequence = 4, divinerSequence = 3).penalty,
        )
    }

    /**
     * **The warrant for treating Sections 5 and 6 as one system.** Seq 5 prints
     * its own direct-link value (-3) and Sec. 6 prints a general halving; they
     * agree, which is why the general rule may be applied to a row that does
     * not restate it.
     */
    @Test
    fun `the seq 5 row's own link value equals the general halving`() {
        val row = AntiDivination.rowFor(5)
        assertEquals(-6, row.penalty)
        assertEquals(-3, row.linkedPenalty)
        assertEquals(
            row.linkedPenalty,
            AntiDivination.halvedMagnitude(-6, roundUp = false),
        )
        assertEquals(
            -3,
            AntiDivination.against(
                targetSequence = 5, divinerSequence = 8, directLink = true,
            ).penalty,
        )
    }

    /**
     * The halvings act on the MAGNITUDE. Read numerically, "round down" on -5
     * gives -3 -- harsher than the -5 it was meant to soften, in a section
     * where every bullet helps the diviner. The book's only worked halving
     * (-6 to -3) is even and settles nothing, so this is a reading.
     */
    @Test
    fun `halving rounds by magnitude, in both printed directions`() {
        assertEquals(-2, AntiDivination.halvedMagnitude(-5, roundUp = false))
        assertEquals(-3, AntiDivination.halvedMagnitude(-5, roundUp = true))
        // Even values agree, which is why the book's example proves neither.
        assertEquals(-3, AntiDivination.halvedMagnitude(-6, roundUp = false))
        assertEquals(-3, AntiDivination.halvedMagnitude(-6, roundUp = true))
        assertEquals(0, AntiDivination.halvedMagnitude(0, roundUp = true))
        // Never numerically: -3 would be harsher than the -5 it softens.
        assertTrue(AntiDivination.halvedMagnitude(-5, roundUp = false) > -5)
        assertThrows<IllegalArgumentException> {
            AntiDivination.halvedMagnitude(2, roundUp = false)
        }
    }

    /**
     * Sequence Advantage drops the passive penalty but leaves active techniques
     * at half, rounded UP -- the opposite rounding to Direct Link, on the same
     * kind of number.
     */
    @Test
    fun `techniques survive sequence advantage at half, rounded up`() {
        val result = AntiDivination.against(
            targetSequence = 4, divinerSequence = 2, techniquePenalty = -5,
        )
        assertEquals(-3, result.penalty) { "half of 5 rounded up is 3" }
        assertFalse(result.requiresCritical)
        assertThrows<IllegalArgumentException> {
            AntiDivination.against(targetSequence = 4, techniquePenalty = 5)
        }
    }

    /**
     * **Order matters and the book prints none, so printed order is the
     * reading.** Halve then forgive gives -3; forgive then halve gives -4.
     */
    @Test
    fun `direct link applies before the hp sacrifice`() {
        val result = AntiDivination.against(
            targetSequence = 4,
            divinerSequence = 8,
            directLink = true,
            hpSacrificed = 4,
        )
        // -10 halved is -5; 4 HP forgives 2; -3, not the -4 the other order gives.
        assertEquals(-3, result.penalty)
        assertTrue(result.requiresCritical) { "halving is not skill" }
    }

    @Test
    fun `the hp sacrifice needs the printed four and never becomes a bonus`() {
        val sacrifice = AntiDivination.counterNamed("HP Sacrifice")
        assertEquals(4, sacrifice.hpRequired)
        assertEquals(-2, sacrifice.ignoresPenalty) { "the sign is the book's" }

        assertEquals(
            -6,
            AntiDivination.against(targetSequence = 5, divinerSequence = 8).penalty,
        )
        assertEquals(
            -6,
            AntiDivination.against(
                targetSequence = 5, divinerSequence = 8, hpSacrificed = 3,
            ).penalty,
        ) { "three HP is not four" }
        assertEquals(
            -4,
            AntiDivination.against(
                targetSequence = 5, divinerSequence = 8, hpSacrificed = 4,
            ).penalty,
        )
        // Against an unprotected target it cannot buy a bonus.
        assertEquals(
            0,
            AntiDivination.against(
                targetSequence = 8, divinerSequence = 9, hpSacrificed = 20,
            ).penalty,
        )
    }

    /**
     * **The HP is only charged where it bought something.** Against a target
     * with no penalty to forgive there is nothing to ignore, so offering the
     * sacrifice must cost nothing -- otherwise the app invents a price the book
     * attaches to a benefit, and a GM burns 4 HP for no change at all.
     */
    @Test
    fun `the hp is charged only when it actually forgave a penalty`() {
        assertEquals(
            4,
            AntiDivination.against(
                targetSequence = 5, divinerSequence = 8, hpSacrificed = 4,
            ).hpSpent,
        )
        // No penalty to forgive: the offer is free.
        val unprotected =
            AntiDivination.against(targetSequence = 8, divinerSequence = 9, hpSacrificed = 4)
        assertEquals(0, unprotected.penalty)
        assertEquals(0, unprotected.hpSpent)
        // Too little HP put up: refused, and not charged either.
        val short =
            AntiDivination.against(targetSequence = 5, divinerSequence = 8, hpSacrificed = 3)
        assertEquals(-6, short.penalty)
        assertEquals(0, short.hpSpent)
        // Sequence Advantage zeroes the penalty, so the HP buys nothing.
        val outclassed =
            AntiDivination.against(targetSequence = 4, divinerSequence = 2, hpSacrificed = 4)
        assertEquals(0, outclassed.penalty)
        assertEquals(0, outclassed.hpSpent)
    }

    /**
     * A mortal diviner has no Sequence to compare, so neither Sequence
     * Advantage nor the equal-Sequence row can apply. Assuming one would
     * silently pick a side.
     */
    @Test
    fun `an unknown diviner sequence takes the headline penalty`() {
        val result = AntiDivination.against(targetSequence = 4)
        assertEquals(-10, result.penalty)
        assertTrue(result.requiresCritical)
    }

    @Test
    fun `the five printed counters are all there and only one carries numbers`() {
        assertEquals(
            listOf(
                "Sequence Advantage", "Direct Link", "HP Sacrifice",
                "Anti-Anti-Divination Ritual", "Divine Intervention",
            ),
            AntiDivination.counters().map { it.name },
        )
        assertEquals(
            setOf("HP Sacrifice"),
            AntiDivination.counters()
                .filter { it.ignoresPenalty != null || it.hpRequired != null }
                .map { it.name }.toSet(),
        ) { "the ritual and the deity are the GM's" }
        assertThrows<IllegalStateException> { AntiDivination.counterNamed("Bribery") }
    }

    // ---- what it does to a divination -------------------------------------

    /**
     * A demoted success must not keep a SUCCESS clarity row. "Clear" printed
     * beside the word Failure is the same defect `SkillRollOutcome.degree`
     * avoids by reading the effective outcome; its margin is positive, so it
     * takes the mildest failure row rather than a success one.
     */
    @Test
    fun `a divination the book forbids reports a failure clarity`() {
        val trivial = Divination.difficulties().first()
        val plain = Divination.attempt(
            skill = 20, difficulty = trivial, source = RandomSource { 3 },
        )
        assertEquals(Outcome.SUCCESS, plain.effectiveOutcome)
        assertFalse(plain.clarity.result.contains("Failure", ignoreCase = true)) {
            plain.clarity.result
        }

        val forbidden = Divination.attempt(
            skill = 20,
            difficulty = trivial,
            source = RandomSource { 3 },
            requiresCritical = true,
        )
        assertEquals(Outcome.SUCCESS, forbidden.roll.outcome) { "the dice beat it" }
        assertEquals(Outcome.FAILURE, forbidden.effectiveOutcome)
        assertTrue(forbidden.clarity.result.contains("Failure", ignoreCase = true)) {
            forbidden.clarity.result
        }
    }

    /** A critical success is exactly what the rule still allows through. */
    @Test
    fun `a critical success still gets through the critical-only rule`() {
        val out = Divination.attempt(
            skill = 20,
            difficulty = Divination.difficulties().first(),
            source = RandomSource { 1 },
            requiresCritical = true,
        )
        assertEquals(Outcome.CRITICAL_SUCCESS, out.roll.outcome)
        assertEquals(Outcome.CRITICAL_SUCCESS, out.effectiveOutcome)
    }

    /**
     * **The signed numbers printed in [AntiDivinationResult.parts] must sum to
     * the penalty they explain.** A GM adds up what is on screen. An earlier
     * version listed the pre-halved penalty, said "Direct Link halves them"
     * with no number, and printed the HP relief with the book's stored negative
     * sign — so the screen read -10, -2 for a total of -3. Checked across every
     * combination rather than one case, because each part was correct alone.
     */
    @Test
    fun `the printed parts always sum to the penalty`() {
        val sequences = listOf(2, 3, 4, 5, 7)
        val diviners = listOf<Int?>(null, 2, 4, 6, 9)
        for (target in sequences) {
            for (diviner in diviners) {
                for (technique in listOf(0, -3, -5)) {
                    for (link in listOf(false, true)) {
                        for (hp in listOf(0, 4)) {
                            val r = AntiDivination.against(
                                targetSequence = target,
                                divinerSequence = diviner,
                                techniquePenalty = technique,
                                directLink = link,
                                hpSacrificed = hp,
                            )
                            val shown = r.parts.sumOf { part ->
                                Regex("[+-]\\d+").findAll(part)
                                    .map { it.value.toInt() }.sum()
                            }
                            assertEquals(r.penalty, shown) {
                                "target $target, diviner $diviner, technique " +
                                    "$technique, link $link, hp $hp -> ${r.parts}"
                            }
                        }
                    }
                }
            }
        }
    }

    /** The screen quotes these, so an empty explanation is a defect. */
    @Test
    fun `every applied rule names itself`() {
        val result = AntiDivination.against(
            targetSequence = 4,
            divinerSequence = 8,
            techniquePenalty = -4,
            directLink = true,
            hpSacrificed = 4,
        )
        assertTrue(result.parts.any { it.contains("Seq 4") })
        assertTrue(result.parts.any { it.contains("Direct Link") })
        assertTrue(result.parts.any { it.contains("HP forgives") })
        assertTrue(AntiDivination.against(targetSequence = 9).parts.isEmpty())
    }
}
