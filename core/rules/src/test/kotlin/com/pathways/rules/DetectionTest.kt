package com.pathways.rules

import com.pathways.model.Outcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** Ch. 6.5 Sec. 1, 2 and 4 -- the TARGET's side of anti-divination. */
class DetectionTest {

    /** Three faces summing to [total] -- RandomSource is asked once per die. */
    private fun rolling(total: Int): RandomSource {
        require(total in 3..18) { "3d6 cannot total $total" }
        var left = total
        val faces = (3 downTo 1).map { dice ->
            val face = (left - (dice - 1)).coerceIn(1, 6)
            left -= face
            face
        }
        val dice = faces.iterator()
        return RandomSource { dice.next() }
    }

    private val charm = Detection.GENERIC_CHARM
    private val pendant = "Concealment Pendant (Darkness charm)"
    private val candle = "Mystical Candle of Obscurity"

    // ---- the roll --------------------------------------------------------

    /** "a SPI or Per roll (whichever is higher)" -- never their sum. */
    @Test
    fun `the roll takes the higher of SPI and Per`() {
        assertEquals(11, Detection.attributeFor(spi = 11, per = 9))
        assertEquals(11, Detection.attributeFor(spi = 9, per = 11))
        assertEquals(9, Detection.attributeFor(spi = 9, per = 9))
    }

    /**
     * A mortal has SPI 0 and a Per, so the roll is defined for them. The
     * section scopes itself to Beyonders in prose; who suspects they are
     * watched is the GM's call and nothing here gates it (6af).
     */
    @Test
    fun `a mortal rolls on Per`() {
        assertEquals(10, Detection.targetFor(spi = 0, per = 10))
    }

    // ---- the two doors ---------------------------------------------------

    /**
     * **Outcome first, margin second.** A rolled 3 is a critical success at
     * any target, and reading its margin instead lands on an ordinary success
     * row -- the slip `FrightCheck`, `RitualResolution` and `Wounds` each made
     * once. This table prints no override of its own, so `SuccessRoll`'s
     * 3-4/17-18 stand (the FrightCheck reading, 6c).
     */
    @Test
    fun `a rolled 3 reads the critical row whatever its margin`() {
        val result = Detection.attempt(spi = 0, per = 4, source = rolling(3))
        assertEquals(Outcome.CRITICAL_SUCCESS, result.roll.outcome)
        assertEquals("Critical Success", result.row.result)
    }

    @Test
    fun `a rolled 18 reads the critical failure row`() {
        val result = Detection.attempt(spi = 20, per = 0, source = rolling(18))
        assertEquals(Outcome.CRITICAL_FAILURE, result.roll.outcome)
        assertEquals("Critical Failure", result.row.result)
    }

    /**
     * **The success bands overlap, so the LAST match wins.** A margin of 3
     * satisfies "Success" and "Success by 3+" alike, and the book prints them
     * least-demanding first. Taking the first match leaves the second row
     * loaded, tested and unreachable -- the reading `DegreeOfSuccess` needed
     * (6ad).
     */
    @Test
    fun `the deeper success band wins where both fit`() {
        val plain = Detection.attempt(spi = 10, per = 0, source = rolling(8))
        assertEquals(2, plain.roll.margin)
        assertEquals("Success", plain.row.result)

        val deep = Detection.attempt(spi = 10, per = 0, source = rolling(7))
        assertEquals(3, deep.roll.margin)
        assertEquals("Success by 3+", deep.row.result)

        val deeper = Detection.attempt(spi = 10, per = 0, source = rolling(5))
        assertEquals("Success by 3+", deeper.row.result)
    }

    @Test
    fun `an ordinary failure reads the failure row`() {
        val result = Detection.attempt(spi = 8, per = 0, source = rolling(12))
        assertEquals(Outcome.FAILURE, result.roll.outcome)
        assertEquals("Failure", result.row.result)
    }

    // ---- four sources, one roll -----------------------------------------

    /**
     * Sec. 1's ability bonus, Sec. 2's situation, Sec. 3A's ward and Sec. 4's
     * pendant all move the same roll -- the shape `Conditions`, `Attacks`,
     * `Ranged` and `CommonModifiers` have on a skill roll.
     */
    @Test
    fun `all four sources compose`() {
        val warded = Detection.modifierNamed("Target is in a warded or sacred space")
        val ward = AntiDivinationTechniques.named("3A")
        val total = Detection.modifierTotal(
            situations = listOf(warded),
            techniques = listOf(ward),
            items = listOf(Detection.itemNamed(pendant)),
            awarenessBonus = 2,
        )
        assertEquals(8, total)
        assertEquals(18, Detection.targetFor(spi = 10, per = 3, modifier = total))
    }

    /**
     * **One charm, priced twice.** Sec. 2 prices any protective item at +1 and
     * Sec. 4 prices this one at +2. Summing them charges the same pendant
     * twice; specific beats general, and the pair is REFUSED rather than
     * silently reduced, so a caller cannot believe the general row was
     * counted. Same scoping as Appendix A's adverse conditions against chapter
     * 5's darkness (6ae).
     */
    @Test
    fun `the generic charm row and a named charm cannot both apply`() {
        val thrown = assertThrows<IllegalArgumentException> {
            Detection.modifierTotal(
                situations = listOf(Detection.modifierNamed(charm)),
                items = listOf(Detection.itemNamed(pendant)),
            )
        }
        assertTrue(thrown.message!!.contains("never both"))
        // Either alone is fine, and they really do differ.
        assertEquals(
            1,
            Detection.modifierTotal(situations = listOf(Detection.modifierNamed(charm))),
        )
        assertEquals(2, Detection.modifierTotal(items = listOf(Detection.itemNamed(pendant))))
    }

    /**
     * The candle's -3 is "detection of that ritual", which is sensing a ritual
     * being performed rather than the Sec. 2 roll. Carrying it in its own
     * field is what stops a caller summing the nearest number into a detection
     * total.
     */
    @Test
    fun `the candle never reaches the detection roll`() {
        val row = Detection.itemNamed(candle)
        assertEquals(-3, row.ritualDetectionPenalty)
        assertNull(row.detectionBonus)
        assertEquals(0, Detection.modifierTotal(items = listOf(row)))
    }

    // ---- Sec. 1 is a gate ------------------------------------------------

    /**
     * The universal row is automatic only against a WEAKER diviner; against
     * an equal or stronger one the book calls for a Quick Contest, which this
     * app does not model because it holds one side's sheet.
     */
    @Test
    fun `the Seq 4+ row is automatic and says where that stops`() {
        val row = Detection.awarenessFor("Any pathway")
        assertTrue(row.isAutomatic)
        assertNull(row.freeRoll)
        assertTrue(row.detection.contains("Quick Contest of SPI"))
    }

    /** Two rows grant a free roll and name which attribute it uses. */
    @Test
    fun `a free roll names its own attribute`() {
        assertEquals("PER", Detection.awarenessFor("Fool (Seer)").freeRoll)
        assertEquals("SPI", Detection.awarenessFor("Hermit (Mystery Pryer)").freeRoll)
    }

    /**
     * **A row in the wrong table.** Wheel of Fortune grants no awareness at
     * all -- it penalises the DIVINER, at the GM's discretion, from a passive
     * source. It is recorded and applied nowhere: routing it through
     * `techniquePenalty` would subject a passive penalty to two halvings that
     * both name "active techniques". Same family as divination's "See Step 1
     * table" row (6p).
     */
    @Test
    fun `the Monster row penalises the diviner and grants no awareness`() {
        val row = Detection.awarenessFor("Wheel of Fortune (Monster)")
        assertEquals(-1, row.divinerPenalty)
        assertNull(row.bonus)
        assertNull(row.freeRoll)
        assertFalse(row.isAutomatic)
        // and it is the only such row, so nothing else can leak that way
        assertEquals(1, Detection.awareness().count { it.divinerPenalty != null })
    }

    /**
     * Three of the four bonuses are scoped to what the divination is ABOUT,
     * which this app cannot test -- so the sentence travels with the number
     * and the GM reads it, the call a wealth level's reaction modifier takes
     * (6u).
     */
    @Test
    fun `every bonus carries the sentence that scopes it`() {
        val bonuses = Detection.awareness().filter { it.bonus != null }
        assertEquals(4, bonuses.size)
        bonuses.forEach { assertNotNull(it.bonusText, "${it.pathway} lost its scope") }
        assertTrue(
            Detection.awarenessFor("Hanged Man (Secrets Supplicant)")
                .bonusText!!.contains("hidden knowledge"),
        )
    }

    // ---- the parts reconcile --------------------------------------------

    /**
     * Every signed number on screen must sum to the target above it. The
     * divination dialog printed a penalty twice before this rule was made
     * general (6ag), so it is asserted here rather than eyeballed.
     */
    @Test
    fun `the printed parts sum to what moved the roll`() {
        val stronger = Detection.modifierNamed(
            "Diviner is 2+ Sequences stronger than the target",
        )
        val ward = AntiDivinationTechniques.named("3A")
        val result = Detection.attempt(
            spi = 10,
            per = 8,
            situations = listOf(stronger),
            techniques = listOf(ward),
            awarenessBonus = 2,
            awarenessLabel = "Fool (Seer)",
            source = rolling(9),
        )
        assertEquals(12, result.roll.target)
        val signed = Regex("[+-]\\d+")
        val summed = result.parts.drop(1).sumOf { signed.find(it)!!.value.toInt() }
        assertEquals(result.roll.target - Detection.attributeFor(10, 8), summed)
    }
}
