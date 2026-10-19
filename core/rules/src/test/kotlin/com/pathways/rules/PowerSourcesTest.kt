package com.pathways.rules

import com.pathways.rules.content.ContentLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** Ch. 9 IV, v6.9i: the power sources and what each adds to the roll. */
class PowerSourcesTest {

    private val light = PowerSources.complexityNamed("Light")
    private val heavy = PowerSources.complexityNamed("Heavy")
    private val major = PowerSources.complexityNamed("Major")

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
     * "Each assistant beyond the first gives +1". The first is the caster:
     * the owner's ruling (2026-10-19), since v6.9i deleted the worked Ritual
     * 3 that used to settle it.
     */
    @Test
    fun `two assistants are worth two, the caster being the first`() {
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

    // ---- External Sacrifice/Catalysts -------------------------------------

    /**
     * "Sacrifice quality ranges from -40 to +40 depending on quality and
     * domain relevance at GM's discretion." One GM number: v6.9i deleted the
     * three tiers, the +2 per sentient being and the separate catalyst.
     */
    @Test
    fun `a sacrifice is the GM's number between -40 and +40`() {
        assertEquals(0, PowerSources.sacrificeBonus(0))
        assertEquals(-40, PowerSources.sacrificeBonus(-40))
        assertEquals(40, PowerSources.sacrificeBonus(40))
        assertThrows<IllegalArgumentException> { PowerSources.sacrificeBonus(41) }
        assertThrows<IllegalArgumentException> { PowerSources.sacrificeBonus(-41) }
    }

    // ---- Invocation --------------------------------------------------------

    /**
     * "Adds +1 to +5 to the ritual roll (GM discretion ...)". Praying to
     * oneself (null) adds nothing; an orthodox deity in good standing is the
     * +2 inside that range (owner's ruling, 2026-10-19).
     */
    @Test
    fun `an invocation adds the GM's +1 to +5 and praying to oneself adds nothing`() {
        assertEquals(0, PowerSources.invocationBonus(null))
        assertEquals(1, PowerSources.invocationBonus(1))
        assertEquals(5, PowerSources.invocationBonus(5))
        assertEquals(2, PowerSources.ORTHODOX_BONUS)
        assertThrows<IllegalArgumentException> { PowerSources.invocationBonus(0) }
        assertThrows<IllegalArgumentException> { PowerSources.invocationBonus(6) }
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
                sacrifice = 3,
                invocation = 4,
            ),
        )
        // +2 extended, +2 for two assistants, +3 sacrifice, +4 invoked.
        assertEquals(11, total)
    }

    @Test
    fun `an unprinted complexity is refused rather than defaulted`() {
        assertThrows<IllegalArgumentException> { PowerSources.complexityNamed("Trivial") }
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
                sacrifice = -6,
                suspended = true,
            ),
        )
        // -4 rushed at Heavy, +2 assistants, -6 sacrifice, -2 suspended.
        assertEquals(bare - 10, declared)
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
        // The rung-independent sources still apply without one.
        assertEquals(
            2,
            PowerSources.modifierTotal(null, PowerSourceDeclaration(assistants = 2)),
        )
    }

    @Test
    fun `every rung prints a base time and both pacing cells`() {
        ContentLoader.ritualComplexity().forEach {
            assertTrue(it.baseTime.isNotBlank()) { "${it.complexity} lost its base time" }
            assertTrue(it.rushed < 0 && it.extended > 0) { it.complexity }
        }
    }

    /** v6.9i prints the time table and the weight table on the same four names. */
    @Test
    fun `each weight reads its pacing off the complexity of the same name`() {
        RitualResolution.weights().forEach { weight ->
            val rung = PowerSources.complexityFor(weight)
            assertEquals(weight.weight, rung.complexity)
            assertEquals(weight.baseTime, rung.baseTime)
        }
    }

    // ---- Revelation penalties (Ch. 9, v6.9i 4008-4024) -------------------

    @Test
    fun `petitioning the Seven Lights costs -20, less 5 per sacrifice, never a bonus`() {
        val lights = PowerSources.revelationNamed("Seven Lights of the Divine Throne")
        assertEquals(-20, PowerSources.revelationPenalty(lights, sacrifices = 0))
        assertEquals(-10, PowerSources.revelationPenalty(lights, sacrifices = 2))
        assertEquals(0, PowerSources.revelationPenalty(lights, sacrifices = 6))
        assertThrows<IllegalArgumentException> { PowerSources.revelationPenalty(lights, sacrifices = -1) }
        val declared = PowerSourceDeclaration(revelation = Revelation(lights, sacrifices = 1))
        assertEquals(-15, PowerSources.modifierTotal(null, declared))
    }

    @Test
    fun `a deity costs -40, and no sacrifice reduces it`() {
        // "Reducible to -10 if the caster possesses the Blessed by Deity
        // advantage": no advantage table prints that row, so no character
        // holds it and nothing here reads it.
        val deity = PowerSources.revelationNamed("Deities (Orthodox or Hidden)")
        assertEquals(-40, PowerSources.revelationPenalty(deity, sacrifices = 0))
        assertThrows<IllegalArgumentException> { PowerSources.revelationPenalty(deity, sacrifices = 1) }
        assertTrue(ContentLoader.advantages().none { it.name.startsWith("Blessed by") })
    }
}
