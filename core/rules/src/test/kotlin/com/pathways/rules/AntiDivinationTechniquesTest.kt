package com.pathways.rules

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** Ch. 6.5 Sec. 3 -- what the target pays to be hard to find. */
class AntiDivinationTechniquesTest {

    private val ward = AntiDivinationTechniques.named("3A")
    private val concealment = AntiDivinationTechniques.named("3B")

    @Test
    fun `the book prints six, in order`() {
        assertEquals(
            listOf("3A", "3B", "3C", "3D", "3E", "3F"),
            AntiDivinationTechniques.all().map { it.id },
        )
    }

    @Test
    fun `a technique answers to its id or its printed name`() {
        assertEquals("3B", AntiDivinationTechniques.named("Concealment").id)
        assertThrows<IllegalArgumentException> { AntiDivinationTechniques.named("3G") }
    }

    // ---- stacking, and the two halvings ---------------------------------

    /** "All active techniques stack -- their penalties add together." */
    @Test
    fun `penalties add`() {
        assertEquals(-6, AntiDivinationTechniques.penaltyTotal(listOf(ward, concealment)))
    }

    /** A target has one ward; naming it twice is not two wards. */
    @Test
    fun `naming a technique twice pays once`() {
        assertEquals(-2, AntiDivinationTechniques.penaltyTotal(listOf(ward, ward)))
    }

    /**
     * **Sum, then halve** -- the reading, stated where it can be checked.
     *
     * Sec. 3 makes the techniques one penalty "when a divination is
     * attempted", which is the moment Sec. 6's two halvings act. The book's
     * own numbers cannot distinguish that from halving each and then summing:
     * only two rows print a penalty and BOTH ARE EVEN, so the orders agree on
     * every combination this book can produce. `extract_detection.verify()`
     * fails on the first odd one, which is when this decision has to be made
     * again rather than inherited.
     */
    @Test
    fun `every printed penalty is even, so the two stacking orders agree`() {
        val printed = AntiDivinationTechniques.all().mapNotNull { it.divinationPenalty }
        assertEquals(listOf(-2, -4), printed)
        assertTrue(printed.all { it % 2 == 0 })

        val summedThenHalved = AntiDivination.halvedMagnitude(
            AntiDivinationTechniques.penaltyTotal(listOf(ward, concealment)),
            roundUp = true,
        )
        val halvedThenSummed =
            printed.sumOf { AntiDivination.halvedMagnitude(it, roundUp = true) }
        assertEquals(summedThenHalved, halvedThenSummed)
        // and the same again in the other rounding direction (Direct Link)
        assertEquals(
            AntiDivination.halvedMagnitude(-6, roundUp = false),
            printed.sumOf { AntiDivination.halvedMagnitude(it, roundUp = false) },
        )
    }

    /**
     * The loop this closes: `against` has taken a technique total since the
     * diviner's half of the chapter shipped, and nothing produced one.
     */
    @Test
    fun `a technique total reaches the diviner through against`() {
        val result = AntiDivination.against(
            targetSequence = 6,
            techniquePenalty = AntiDivinationTechniques.penaltyTotal(listOf(ward)),
        )
        assertEquals(-2, result.penalty)
        assertTrue(result.parts.any { it.contains("active techniques") })
    }

    // ---- the four that price nothing -------------------------------------

    /**
     * 3C diverts, 3D redirects, 3E answers as someone else and 3F answers
     * falsely. None is a penalty on the roll, so none carries a zero -- the
     * distinction `RitualCritical.corGained` draws between charging nothing
     * and charging zero.
     */
    @Test
    fun `four techniques print no penalty at all`() {
        val silent = AntiDivinationTechniques.all().filter { it.divinationPenalty == null }
        assertEquals(listOf("3C", "3D", "3E", "3F"), silent.map { it.id })
        assertEquals(0, AntiDivinationTechniques.penaltyTotal(silent))
    }

    // ---- the SPI ---------------------------------------------------------

    @Test
    fun `SPI costs add`() {
        val misdirection = AntiDivinationTechniques.named("3D")
        assertEquals(5, AntiDivinationTechniques.spiFor(listOf(ward, misdirection)))
    }

    /**
     * 3F prices itself as the divination's own cost, which is a rule and not
     * a number. Refused rather than read as free: the strongest effect in the
     * section would otherwise be the cheapest.
     */
    @Test
    fun `the technique with no printed number is refused, not free`() {
        val echo = AntiDivinationTechniques.named("3F")
        assertNull(echo.spi)
        val thrown = assertThrows<IllegalArgumentException> {
            AntiDivinationTechniques.spiFor(listOf(echo))
        }
        assertTrue(thrown.message!!.contains("prints no SPI cost"))
    }

    // ---- 3A reaches both rolls -------------------------------------------

    /**
     * The ward is the only row that touches the target's own detection roll,
     * and it is a separate field from the penalty because they are opposite
     * signs on two different rolls.
     */
    @Test
    fun `only the ward also helps the target notice`() {
        assertEquals(2, ward.detectionBonus)
        assertEquals(-2, ward.divinationPenalty)
        assertEquals(
            2,
            AntiDivinationTechniques.detectionBonus(AntiDivinationTechniques.all()),
        )
    }

    /** Durations are printed and deliberately not tracked -- no clock here. */
    @Test
    fun `every technique keeps its printed duration`() {
        AntiDivinationTechniques.all().forEach {
            assertNotNull(it.bullet("Duration"), "${it.id} lost its duration")
        }
    }
}
