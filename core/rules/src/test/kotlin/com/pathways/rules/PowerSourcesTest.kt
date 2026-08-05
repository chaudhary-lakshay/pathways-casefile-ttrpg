package com.pathways.rules

import com.pathways.rules.content.ContentLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PowerSourcesTest {

    private val light = PowerSources.complexityNamed("Light")
    private val heavy = PowerSources.complexityNamed("Heavy")
    private val major = PowerSources.complexityNamed("Major")

    @Test
    fun `the ladder prints four rungs where the weight table prints three`() {
        assertEquals(
            listOf("Light", "Moderate", "Heavy", "Major"),
            PowerSources.complexities().map { it.complexity },
        )
        assertEquals(3, RitualResolution.weights().size)
        assertTrue(RitualResolution.weights().none { it.weight == "Major" }) {
            "Major has no base difficulty modifier, which is why it is a complexity"
        }
    }

    /**
     * The disagreement this slice exists to keep. Section V prices a Light
     * ritual at a flat 2 and a Heavy one at 8+; the ladder says 2-3 and 7-10,
     * and the book's own worked rituals charge 3 and 7. Repairing either table
     * from the other is the failure mode.
     */
    @Test
    fun `the two SPI tables disagree at both ends and agree in the middle`() {
        val weights = RitualResolution.weights().associateBy { it.weight }
        assertEquals(2, weights.getValue("Light").spi)
        assertNull(weights.getValue("Light").spiMax)
        assertEquals(2 to 3, light.spi to light.spiMax)

        assertEquals(8, weights.getValue("Heavy").spi)
        assertEquals(7 to 10, heavy.spi to heavy.spiMax)

        val moderateWeight = weights.getValue("Moderate")
        val moderate = PowerSources.complexityNamed("Moderate")
        assertEquals(moderateWeight.spi to moderateWeight.spiMax, moderate.spi to moderate.spiMax)
    }

    @Test
    fun `the book's own worked rituals fall inside the ladder and outside the weight column`() {
        // Ritual 2: a Light divination charged 3 SPI.
        assertEquals(3, PowerSources.spiFor(light, chosen = 3))
        // Ritual 3: a Heavy summoning charged 7, below the weight column's floor of 8.
        assertEquals(7, PowerSources.spiFor(heavy, chosen = 7))
        assertTrue(7 < RitualResolution.weights().single { it.weight == "Heavy" }.spi)
    }

    @Test
    fun `a cost outside the printed cell is refused rather than clamped`() {
        assertThrows<IllegalArgumentException> { PowerSources.spiFor(light, chosen = 4) }
        assertThrows<IllegalArgumentException> { PowerSources.spiFor(light, chosen = 1) }
    }

    /** "11-15+" prints a ceiling AND a plus, so the plus wins and there is none. */
    @Test
    fun `the Major rung has an open top and no ceiling to exceed`() {
        assertEquals("11-15+ SPI", major.printedCost)
        assertEquals(40, PowerSources.spiFor(major, chosen = 40))
        assertThrows<IllegalArgumentException> { PowerSources.spiFor(major, chosen = 10) }
    }

    /** Burning is a second use of SPI, cumulative with the fuel. */
    @Test
    fun `burned SPI adds to the fuel rather than replacing it`() {
        assertEquals(7, PowerSources.spiFor(light, chosen = 3, burned = 4))
        assertThrows<IllegalArgumentException> { PowerSources.spiFor(light, burned = -1) }
    }

    @Test
    fun `an open cell cannot overflow into a negative cost`() {
        assertThrows<IllegalArgumentException> {
            PowerSources.spiFor(major, chosen = Int.MAX_VALUE, burned = 1)
        }
    }

    // ---- B. HP sacrifice -------------------------------------------------

    @Test
    fun `two HP buys one SPI and the odd point buys nothing`() {
        assertEquals(4, PowerSources.spiOwed(cost = 6, hpSacrificed = 4))
        // 5 HP is 2 SPI, not 2.5 rounded up: the odd point is spent for nothing.
        assertEquals(4, PowerSources.spiOwed(cost = 6, hpSacrificed = 5))
        assertEquals(0, PowerSources.spiOwed(cost = 3, hpSacrificed = 6))
        assertEquals(6, PowerSources.hpFor(3))
    }

    @Test
    fun `HP beyond the ritual's cost is refused rather than wasted silently`() {
        assertThrows<IllegalArgumentException> { PowerSources.spiOwed(cost = 2, hpSacrificed = 8) }
        assertThrows<IllegalArgumentException> { PowerSources.spiOwed(cost = 2, hpSacrificed = -1) }
    }

    /**
     * The sentence the extractor pins, asserted here as behaviour: HP is fuel,
     * so no amount of it moves the roll. A bonus appearing here is the defect
     * this whole reading exists to prevent.
     */
    @Test
    fun `HP sacrifice grants no bonus to the roll`() {
        assertEquals(0, PowerSources.modifierTotal(heavy, PowerSourceDeclaration()))
        // There is deliberately no HP field on the declaration to pass at all,
        // so no caller can route fuel into the modifier by mistake.
        assertTrue(
            PowerSourceDeclaration::class.java.declaredFields.none {
                "hp" in it.name.lowercase()
            },
        ) { "HP reached the modifier declaration; the book says it only provides fuel" }
    }

    // ---- C. Multiple participants ----------------------------------------

    /**
     * Ritual 3 prints "Two trained assistants ... +2". Read as "beyond the
     * first ASSISTANT" that pair is +1, and every ritual with helpers rolls a
     * point low.
     */
    @Test
    fun `two assistants are worth two, as the book's own ritual prints`() {
        assertEquals(0, PowerSources.participantBonus(0))
        assertEquals(1, PowerSources.participantBonus(1))
        assertEquals(2, PowerSources.participantBonus(2))
    }

    @Test
    fun `the assistant bonus caps at three however many turn up`() {
        assertEquals(3, PowerSources.participantBonus(3))
        assertEquals(3, PowerSources.participantBonus(400))
        assertThrows<IllegalArgumentException> { PowerSources.participantBonus(-1) }
    }

    // ---- D. External sacrifice -------------------------------------------

    @Test
    fun `the three tiers price as printed and no sacrifice is worth nothing`() {
        assertEquals(
            listOf("Ordinary" to 0, "Meaningful" to 2, "Precious" to 4),
            PowerSources.sacrificeTiers().map { it.tier to it.modifier },
        )
        assertEquals(0, PowerSources.sacrificeBonus(null))
    }

    /** Two consecutive sentences, both applied: the tier AND the per-being bonus. */
    @Test
    fun `a sentient offering adds to its tier rather than replacing it`() {
        val precious = PowerSources.sacrificeNamed("Precious")
        assertEquals(6, PowerSources.sacrificeBonus(precious, sentientBeings = 1))
        assertEquals(8, PowerSources.sacrificeBonus(precious, sentientBeings = 2))
    }

    @Test
    fun `a sentient offering with no sacrifice declared is refused, not ignored`() {
        assertThrows<IllegalArgumentException> {
            PowerSources.sacrificeBonus(null, sentientBeings = 1)
        }
    }

    // ---- E. Catalysts ----------------------------------------------------

    @Test
    fun `a catalyst is validated against both printed ends`() {
        assertEquals(0, PowerSources.catalystBonus(0))
        assertEquals(2, PowerSources.catalystBonus(2))
        assertThrows<IllegalArgumentException> { PowerSources.catalystBonus(3) }
        assertThrows<IllegalArgumentException> { PowerSources.catalystBonus(-1) }
    }

    // ---- Pacing and the total --------------------------------------------

    @Test
    fun `rushing costs and taking longer pays, per rung`() {
        assertEquals(-3, PowerSources.pacingModifier(light, RitualPacing.RUSHED))
        assertEquals(1, PowerSources.pacingModifier(light, RitualPacing.EXTENDED))
        assertEquals(-5, PowerSources.pacingModifier(major, RitualPacing.RUSHED))
        assertEquals(2, PowerSources.pacingModifier(major, RitualPacing.EXTENDED))
    }

    /** The Base Time column prints no roll value, so taking it moves nothing. */
    @Test
    fun `the base time is neither rushed nor extended`() {
        PowerSources.complexities().forEach {
            assertEquals(0, PowerSources.pacingModifier(it, RitualPacing.NORMAL))
        }
    }

    @Test
    fun `suspending a ritual costs two off the effective skill`() {
        assertEquals(
            -2,
            PowerSources.modifierTotal(heavy, PowerSourceDeclaration(suspended = true)),
        )
    }

    @Test
    fun `the declared sources sum into one modifier`() {
        val total = PowerSources.modifierTotal(
            heavy,
            PowerSourceDeclaration(
                pacing = RitualPacing.EXTENDED,
                assistants = 2,
                sacrifice = PowerSources.sacrificeNamed("Meaningful"),
                catalyst = 1,
            ),
        )
        // +2 extended, +2 for two assistants, +2 Meaningful, +1 catalyst.
        assertEquals(7, total)
    }

    @Test
    fun `an unprinted complexity or tier is refused rather than defaulted`() {
        assertThrows<IllegalArgumentException> { PowerSources.complexityNamed("Trivial") }
        assertThrows<IllegalArgumentException> { PowerSources.sacrificeNamed("Priceless") }
    }

    // ---- Reaching the roll -----------------------------------------------

    /**
     * The regression guard: before this slice `targetFor` had no way to hear
     * about assistants, a sacrifice or a rush, so every one of these rolled at
     * the bare skill. Passing nothing must still land exactly where it did.
     */
    @Test
    fun `declaring nothing leaves the ritual target where it was`() {
        val moderate = RitualResolution.weights().single { it.weight == "Moderate" }
        assertEquals(
            RitualResolution.targetFor(skillLevel = 12, weight = moderate, cor = 0),
            RitualResolution.targetFor(
                skillLevel = 12,
                weight = moderate,
                cor = 0,
                complexity = heavy,
                powerSources = PowerSourceDeclaration(),
            ),
        )
    }

    @Test
    fun `the declared power sources reach the ritual target`() {
        val moderate = RitualResolution.weights().single { it.weight == "Moderate" }
        val bare = RitualResolution.targetFor(skillLevel = 12, weight = moderate, cor = 0)
        val declared = RitualResolution.targetFor(
            skillLevel = 12,
            weight = moderate,
            cor = 0,
            complexity = heavy,
            powerSources = PowerSourceDeclaration(
                pacing = RitualPacing.RUSHED,
                assistants = 2,
                sacrifice = PowerSources.sacrificeNamed("Precious"),
                catalyst = 2,
                suspended = true,
            ),
        )
        // -4 rushed at Heavy, +2 assistants, +4 Precious, +2 catalyst, -2 suspended.
        assertEquals(bare + 2, declared)
    }

    /** Rushed is -3 at one rung and -5 at another; there is no safe default. */
    @Test
    fun `a rushed ritual with no complexity declared is refused, not dropped`() {
        val moderate = RitualResolution.weights().single { it.weight == "Moderate" }
        assertThrows<IllegalArgumentException> {
            RitualResolution.targetFor(
                skillLevel = 12,
                weight = moderate,
                cor = 0,
                powerSources = PowerSourceDeclaration(pacing = RitualPacing.RUSHED),
            )
        }
        // The four rung-independent sources still apply without one.
        assertEquals(
            2,
            PowerSources.modifierTotal(null, PowerSourceDeclaration(assistants = 2)),
        )
    }

    @Test
    fun `every rung prints a base time and a look`() {
        ContentLoader.ritualComplexity().forEach {
            assertTrue(it.baseTime.isNotBlank()) { "${it.complexity} lost its base time" }
            assertTrue(it.description.isNotBlank()) { "${it.complexity} lost its description" }
            assertTrue(it.looksLike.contains("(")) { "${it.complexity} lost its examples" }
        }
    }
}
