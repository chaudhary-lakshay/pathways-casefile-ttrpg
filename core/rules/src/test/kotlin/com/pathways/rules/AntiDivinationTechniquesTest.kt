package com.pathways.rules

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** Ch. 8 IV Sec. 3 -- what the target pays to be hard to find. */
class AntiDivinationTechniquesTest {

    private val ward = AntiDivinationTechniques.named("3A")
    private val misdirection = AntiDivinationTechniques.named("3B")

    @Test
    fun `the book prints two, in order`() {
        assertEquals(
            listOf("3A", "3B"),
            AntiDivinationTechniques.all().map { it.id },
        )
    }

    @Test
    fun `a technique answers to its id or its printed name`() {
        assertEquals("3B", AntiDivinationTechniques.named("Door Misdirection").id)
        assertThrows<IllegalArgumentException> { AntiDivinationTechniques.named("3C") }
    }

    // ---- stacking, and the two halvings ---------------------------------

    /**
     * "All active techniques stack -- their penalties add together." Only the
     * ward prints one, so misdirection adds nothing to it.
     */
    @Test
    fun `penalties add`() {
        assertEquals(-2, AntiDivinationTechniques.penaltyTotal(listOf(ward, misdirection)))
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
     * only one row prints a penalty and IT IS EVEN, so the orders agree on
     * every combination this book can produce. `extract_detection.verify()`
     * fails on the first odd one, which is when this decision has to be made
     * again rather than inherited.
     */
    @Test
    fun `every printed penalty is even, so the two stacking orders agree`() {
        val printed = AntiDivinationTechniques.all().mapNotNull { it.divinationPenalty }
        assertEquals(listOf(-2), printed)
        assertTrue(printed.all { it % 2 == 0 })

        val summedThenHalved = AntiDivination.halvedMagnitude(
            AntiDivinationTechniques.penaltyTotal(listOf(ward, misdirection)),
            roundUp = true,
        )
        val halvedThenSummed =
            printed.sumOf { AntiDivination.halvedMagnitude(it, roundUp = true) }
        assertEquals(summedThenHalved, halvedThenSummed)
        // and the same again in the other rounding direction (Direct Link)
        assertEquals(
            AntiDivination.halvedMagnitude(-2, roundUp = false),
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

    // ---- the one that prices nothing --------------------------------------

    /**
     * 3B redirects the divination. That is not a penalty on the roll, so it
     * carries no zero -- the distinction `RitualCritical.corGained` draws
     * between charging nothing and charging zero.
     */
    @Test
    fun `misdirection prints no penalty at all`() {
        val silent = AntiDivinationTechniques.all().filter { it.divinationPenalty == null }
        assertEquals(listOf("3B"), silent.map { it.id })
        assertEquals(0, AntiDivinationTechniques.penaltyTotal(silent))
    }

    // ---- the SPI ---------------------------------------------------------

    @Test
    fun `SPI costs add`() {
        assertEquals(5, AntiDivinationTechniques.spiFor(listOf(ward, misdirection)))
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

    /**
     * **Every printed line survives to a reader.** Each technique names who
     * may use it ("Door Pathway, Seq 5 -- Traveler") and prints its own
     * caveats beside the Effect -- 3A's ward breaking on a wound over HP/3,
     * 3B failing against a diviner 2+ Sequences stronger. The screen showed the Effect alone for a whole slice, so
     * a GM ticking a Darkness-only technique for a Justiciar saw nothing to
     * stop them. None of it can be enforced here (the target has no sheet),
     * which is exactly why it has to be shown -- 6af's call for the Sealed
     * Artifact grade table.
     */
    @Test
    fun `every technique keeps its availability and all its printed lines`() {
        AntiDivinationTechniques.all().forEach {
            assertTrue(
                it.availability.isNotBlank(),
                "${it.id} lost the pathway and Sequence that may use it",
            )
            assertTrue(
                it.bullets.size >= 4,
                "${it.id} prints ${it.bullets.size} lines; the book prints 4-5",
            )
        }
        // The caveats specifically, since they are the ones the Effect hides.
        assertEquals(
            "Limitation",
            AntiDivinationTechniques.named("3B").bullets.last().label,
        )
    }

    /** Durations are printed and deliberately not tracked -- no clock here. */
    @Test
    fun `every technique keeps its printed duration`() {
        AntiDivinationTechniques.all().forEach {
            assertNotNull(it.bullet("Duration"), "${it.id} lost its duration")
        }
    }

    /**
     * The ward chip names what raising it costs, so the set the screen offers
     * and the set that prints a price have to be one set. Resolved once in
     * [AntiDivinationTechniques.onDetectionRoll] rather than filtered a second
     * time in the dialog -- the same predicate at two call sites is the defect,
     * not the drift it produces.
     */
    @Test
    fun `every ward the detection screen offers prints the price its chip shows`() {
        val offered = AntiDivinationTechniques.onDetectionRoll()
        assertTrue(offered.isNotEmpty(), "the detection screen would offer no ward at all")
        // The chip prints this STRING, so presence is not enough: `spi` is
        // parsed off the front of it and would still read 2 if the line grew
        // a clause the chip would then print whole.
        assertEquals("2 SPI", ward.bullet("Cost"))
        offered.forEach {
            assertNotNull(
                it.bullet("Cost"),
                "${it.id} ${it.name} moves the detection roll and prints no Cost to show",
            )
        }
    }
}
