package com.pathways.rules

import com.pathways.rules.content.BoonModifier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class BoonsTest {

    private fun fixed(face: Int) = RandomSource { face }

    private fun option(group: String, index: Int) =
        Boons.modifiers().filter { it.group == group }[index]

    @Test
    fun `the ritual takes Heavy time but rolls at Moderate weight`() {
        // Two axes, printed one line apart. Reading the complexity as the
        // weight would roll at -4 instead of -2 and charge 8+ SPI for a
        // ritual the book prices at 4-6.
        val granting = Boons.granting()
        assertEquals("Heavy", granting.complexity)
        assertEquals("1 hour base time", granting.complexityTime)
        assertEquals("Moderate", granting.weight)
        assertEquals(-2, Boons.weight().modifier)
    }

    @Test
    fun `both printings of the Moderate cost agree`() {
        // Chapter 13 prints "4-6 SPI required"; Chapter 7's own weight table
        // prints "4-6 SPI". If they ever diverge, that is a finding, not
        // something for one caller to pick a side on.
        val granting = Boons.granting()
        val weight = Boons.weight()
        assertEquals(granting.spi, weight.spi)
        assertEquals(granting.spiMax, weight.spiMax)
        assertEquals(4, Boons.spiFor())
        assertEquals(6, Boons.spiFor(chosen = 6))
        assertThrows<IllegalArgumentException> { Boons.spiFor(chosen = 7) }
    }

    @Test
    fun `it is the same skill Chapter 7 rolls`() {
        assertEquals("Ritualistic Magic", Boons.granting().skill)
        assertEquals("Very Hard", Boons.granting().difficulty)
    }

    @Test
    fun `the boon level penalty doubles, it does not step by two`() {
        // READING. The book prints "Sequence 9 equivalent (+0), Sequence 8
        // (-2), Sequence 7 (-4), and so on, doubling the penalty with each
        // sequence." The three printed values fit both a doubling and a flat
        // -2 per step; they only diverge at Sequence 6, where doubling says
        // -8 and stepping says -6. The book says "doubling", so doubling wins.
        assertEquals(0, Boons.penaltyForSequence(9))
        assertEquals(-2, Boons.penaltyForSequence(8))
        assertEquals(-4, Boons.penaltyForSequence(7))
        assertEquals(
            -8,
            Boons.penaltyForSequence(6),
            "the first sequence the two readings disagree on",
        )
        assertEquals(-16, Boons.penaltyForSequence(5))
        assertEquals(-512, Boons.penaltyForSequence(0))
    }

    @Test
    fun `a sequence off the ladder is refused rather than extrapolated`() {
        assertThrows<IllegalArgumentException> { Boons.penaltyForSequence(10) }
        assertThrows<IllegalArgumentException> { Boons.penaltyForSequence(-1) }
    }

    @Test
    fun `the best sacrifice cannot buy off a high sequence on its own`() {
        // "Generous sacrifices ... can offset or even negate this penalty."
        // At Sequence 8 a great sacrifice does negate it; at Sequence 6 the
        // doubling has already outrun the best the table offers.
        val great = option("Sacrifice quality", 3)
        assertEquals(6, great.best)
        assertTrue(great.best!! + Boons.penaltyForSequence(8) > 0)
        assertTrue(great.best!! + Boons.penaltyForSequence(6) < 0)
    }

    @Test
    fun `one option per group, because two sacrifices is not a choice`() {
        val perfunctory = option("Sacrifice quality", 0)
        val great = option("Sacrifice quality", 3)
        assertThrows<IllegalArgumentException> {
            Boons.modifierTotal(listOf(perfunctory, great))
        }
        assertEquals(-2, Boons.modifierTotal(listOf(perfunctory)))
    }

    @Test
    fun `a ranged option keeps both ends and the GM picks inside`() {
        val meaningful = option("Sacrifice quality", 2)
        assertEquals(1, meaningful.modifier, "the end nearer zero is the default")
        assertEquals(3, meaningful.best)
        assertEquals(3, Boons.modifierTotal(listOf(meaningful), mapOf(meaningful.key to 3)))
        assertThrows<IllegalArgumentException> {
            Boons.modifierTotal(listOf(meaningful), mapOf(meaningful.key to 4))
        }
        // A perfunctory sacrifice runs -2 to -4: the range is downward, and
        // -3 is inside it.
        val perfunctory = option("Sacrifice quality", 0)
        assertEquals(-3, Boons.modifierTotal(listOf(perfunctory), mapOf(perfunctory.key to -3)))
    }

    @Test
    fun `a fixed option prints one number and takes no override`() {
        val sloppy = option("Ritual quality", 0)
        assertEquals(-3, sloppy.modifier)
        assertThrows<IllegalArgumentException> {
            Boons.modifierTotal(listOf(sloppy), mapOf(sloppy.key to -1))
        }
    }

    @Test
    fun `an override cannot cross into another group that prints the same label`() {
        // REGRESSION (Codex): overrides were keyed by the option's label
        // alone. The committed labels happen to be unique, but nothing made
        // them so, and a content edit that repeated one across the two groups
        // would have let a value picked for the sacrifice silently move the
        // ritual quality as well.
        val sacrifice = BoonModifier("Sacrifice quality", "a great offering", 1, "+1 to +3", 3)
        val ritual = BoonModifier("Ritual quality", "a great offering", 1, "+1 to +3", 3)
        assertEquals(
            4,
            Boons.modifierTotal(listOf(sacrifice, ritual), mapOf(sacrifice.key to 3)),
            "the sacrifice moves to +3 and the ritual quality stays at +1",
        )
    }

    @Test
    fun `an override naming no chosen option is refused, not ignored`() {
        val meaningful = option("Sacrifice quality", 2)
        assertThrows<IllegalArgumentException> {
            Boons.modifierTotal(listOf(meaningful), mapOf("Sacrifice quality / a typo" to 3))
        }
    }

    @Test
    fun `the committed options are distinctly keyed`() {
        val keys = Boons.modifiers().map { it.key }
        assertEquals(keys.size, keys.toSet().size, "two options share a key: $keys")
    }

    @Test
    fun `the target composes weight, sequence, corruption and the choices`() {
        val great = option("Sacrifice quality", 3)
        val meticulous = option("Ritual quality", 2)
        // Skill 12, Moderate -2, Sequence 7 -4, +4 sacrifice, +1 ritual = 11.
        assertEquals(
            11,
            Boons.targetFor(
                skillLevel = 12,
                cor = 0,
                sequence = 7,
                chosen = listOf(great, meticulous),
            ),
        )
        // 3 CoR is -1 on every ritual roll, and a boon ritual is one.
        assertEquals(
            10,
            Boons.targetFor(
                skillLevel = 12,
                cor = 3,
                sequence = 7,
                chosen = listOf(great, meticulous),
            ),
        )
    }

    @Test
    fun `an attempt is resolved by Chapter 7, consequences and all`() {
        // Skill 18, Moderate -2, Sequence 9 +0 -> target 16. A rolled 3 is a
        // success; nothing is consulted.
        val won = Boons.attempt(18, cor = 0, sequence = 9, chosen = emptyList(), source = fixed(1))
        assertTrue(won.succeeded)

        // 5+5+5 = 15 against a Sequence 6 target of 18-2-8 = 8: failure by 7.
        val lost = Boons.attempt(18, cor = 0, sequence = 6, chosen = emptyList(), source = fixed(5))
        assertFalse(lost.succeeded)
        assertEquals(8, lost.target)
        assertEquals(7, lost.marginOfFailure)
        assertEquals("Failure by 5-9", lost.consequence?.printed)
    }

    @Test
    fun `the chapter's prose is carried, not paraphrased`() {
        val granting = Boons.granting()
        assertEquals(3, granting.requirements.size)
        assertEquals(4, granting.consequences.size)
        assertTrue(granting.requirements.any { "honorific name" in it })
        // The refusal row prints no effects of its own; it points back at
        // Chapter 7's table, which RitualResolution already rolls.
        assertNotNull(granting.consequences.single { "Critical Failure Table" in it })
        assertTrue("doubling the penalty" in granting.boonLevelRule)
    }

    // The owner's rulings, 2026-10-13 (6hq): a prayer is said in a tongue and
    // the patron is asked first, as any ch. 7 invocation. fixed(1) rolls 3,
    // Full response +5; fixed(4) rolls 12, No response; fixed(5) rolls 15,
    // displeased -2; fixed(6) rolls 18, Active rejection.

    @Test
    fun `a prayer takes the tongue it is said in and the patron's answer`() {
        val plain = Boons.targetFor(skillLevel = 14, cor = 0, sequence = 9)
        val tongues = Languages.proficiencies(Languages.MYSTICAL)
        val accented = tongues.first { Languages.canPerformRituals(it) && Languages.ritualModifier(it) != 0 }
        assertEquals(plain + Languages.ritualModifier(accented), Boons.targetFor(14, 0, 9, language = accented))
        // Broken "Cannot perform rituals in the language": refused, not priced.
        val broken = tongues.first { !Languages.canPerformRituals(it) }
        assertThrows<IllegalArgumentException> { Boons.targetFor(14, 0, 9, language = broken) }

        assertEquals(plain + 5, Boons.targetFor(14, 0, 9, answer = Boons.ask(fixed(1))))
        assertEquals(plain - 2, Boons.targetFor(14, 0, 9, answer = Boons.ask(fixed(5))))
        // No roll is left to print a target for.
        assertThrows<IllegalArgumentException> { Boons.targetFor(14, 0, 9, answer = Boons.ask(fixed(4))) }
        assertThrows<IllegalArgumentException> { Boons.targetFor(14, 0, 9, answer = Boons.ask(fixed(6))) }
    }

    @Test
    fun `a Hidden Existence reads the table unshifted`() {
        // Line 4297: "Hidden existences and evil gods use the table unmodified".
        assertEquals(Invocation.ask(fixed(5), orthodoxInGoodStanding = false), Boons.ask(fixed(5)))
        assertEquals(15, Boons.ask(fixed(5)).shifted)
        assertEquals(13, Invocation.ask(fixed(5), orthodoxInGoodStanding = true).shifted)
    }

    @Test
    fun `a patron who does not answer grants nothing, and neither does one who refuses`() {
        // The first is the owner's ruling: ch. 7 lets that ritual go on.
        assertTrue(Boons.endsPrayer(Boons.ask(fixed(4))))
        assertTrue(Boons.ask(fixed(4)).unanswered)
        assertTrue(Boons.endsPrayer(Boons.ask(fixed(6))))
        assertFalse(Boons.ask(fixed(6)).unanswered)
        assertFalse(Boons.endsPrayer(Boons.ask(fixed(1))))
        assertFalse(Boons.endsPrayer(Boons.ask(fixed(5))))
    }

    @Test
    fun `the fuel is owed once the patron is asked`() {
        // An answer: the fuel as declared, 4 SPI with 4 HP covering 2 of it.
        assertEquals(Fuel(spi = 2, hp = 4), Boons.fuelFor(Boons.ask(fixed(1)), cost = 4, hpSacrificed = 4))
        // A rejection is never rolled, and ch. 7 charges it nothing.
        assertEquals(Fuel(0, 0), Boons.fuelFor(Boons.ask(fixed(6)), cost = 4, hpSacrificed = 4))
        // No response: "treat as 2 SPI personal", out of the fuel declared,
        // HP first. A mortal plays SPI 0 and declared it all in blood.
        val silent = Boons.ask(fixed(4))
        assertEquals(Fuel(2, 0), Boons.fuelFor(silent, cost = 4, hpSacrificed = 0))
        assertEquals(Fuel(1, 2), Boons.fuelFor(silent, cost = 4, hpSacrificed = 2))
        assertEquals(Fuel(0, 4), Boons.fuelFor(silent, cost = 4, hpSacrificed = 8))
    }

    /**
     * A screen that prints "3d6 <= N" and then rolls must roll against N.
     * Composing N a second time inside the roll is two call sites answering
     * one question (6eg, 6eh), so the roll takes the composed target and
     * the older entry point is that roll with the target composed first.
     */
    @Test
    fun `a prayer is one roll against the target the screen printed`() {
        // With a modifier AND a GM's pick inside it, so that an attempt which
        // dropped either reads a different number: this test first passed
        // neither, and `attempt` ignoring both left every test here green.
        val offering = BoonModifier("Sacrifice quality", "a great offering", 1, "+1 to +3", 3)
        val plain = Boons.targetFor(skillLevel = 14, cor = 0, sequence = 8)
        val picked = Boons.targetFor(14, 0, 8, listOf(offering), mapOf(offering.key to 3))
        assertEquals(plain + 3, picked)

        val rolled = Boons.attempt(14, 0, 8, fixed(4), listOf(offering), mapOf(offering.key to 3))
        assertEquals(picked, rolled.roll.target)
        assertEquals(RitualResolution.rollAgainst(picked, fixed(4)), rolled)
        assertEquals(plain + 1, Boons.attempt(14, 0, 8, fixed(4), listOf(offering)).roll.target)
        // 3 CoR is -1 on every ritual roll, and a prayer is one.
        assertEquals(plain - 1, Boons.attempt(14, 3, 8, fixed(4)).roll.target)
    }
}
