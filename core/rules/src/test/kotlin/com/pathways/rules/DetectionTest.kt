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
     * **A pool balance is not an attribute, and the device proved it.** The
     * dialog was first wired to `maxSpi - spiSpent`, so the breakdown read
     * "the higher of SPI -2 and Per 10" for a mortal who had spent SPI into
     * Soul Debt. Two defects in one: a caster who cast a ritual got worse at
     * noticing, and below zero the number was nonsense. Refused rather than
     * quietly losing to Per, because losing to Per is exactly what hid it.
     */
    @Test
    fun `a negative attribute is refused, not silently beaten by the other`() {
        assertThrows<IllegalArgumentException> { Detection.attributeFor(spi = -2, per = 10) }
        assertThrows<IllegalArgumentException> { Detection.targetFor(spi = -1, per = 10) }
        // Zero is fine: that is a mortal, not a debt.
        assertEquals(10, Detection.attributeFor(spi = 0, per = 10))
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

    /**
     * **A row that names its own attribute beats "whichever is higher".**
     *
     * Sec. 2 says "the GM calls for a SPI or Per roll (whichever is higher)"
     * and scopes that sentence to "a Beyonder with passive awareness" -- which
     * is Sec. 1's own population. Then four Sec. 1 rows name an attribute, and
     * they do NOT all name the same one: Fool and Fool/Error/Door grant a free
     * PER roll, Darkness and Hermit an automatic SPI roll. Naming different
     * attributes per pathway is a choice, not loose phrasing, so the row wins
     * for the roll the row grants. The two sentences disagree and neither is
     * repaired -- Sec. 2 still governs the roll a Beyonder makes on suspicion
     * alone, where no row has spoken.
     *
     * Found on the emulator: a Fool Seq 8 with SPI 11 and Per 10 read
     * "Free Per roll at +2" one line above "SPI 11 ..., +2" for a target of
     * 13, where the row grants Per 10 +2 = 12.
     */
    @Test
    fun `a row that names its attribute rolls that one, not the higher`() {
        // The Fool's Per is the LOWER here, which is the only case that tells
        // the two readings apart.
        assertEquals(10, Detection.attributeFor(spi = 11, per = 10, freeRoll = "PER"))
        // and the other direction, so this is not a Fool special case
        assertEquals(9, Detection.attributeFor(spi = 9, per = 12, freeRoll = "SPI"))
        // With no row named, Sec. 2's general rule still stands.
        assertEquals(11, Detection.attributeFor(spi = 11, per = 10))
    }

    /**
     * **The sentence and the number must have one author.** The screen prints
     * [Detection.attributeLine] directly above a target built from
     * [Detection.attributeFor], and while the dialog hand-wrote its own copy of
     * the choice it kept saying "the higher of SPI and Per" after the rule
     * learned to honour a row's named attribute -- a Fool read "SPI 11" over a
     * target of 12. Asserted across every combination, not just the row that
     * showed it, because the next second copy will be somewhere else.
     */
    @Test
    fun `the attribute line always names the attribute the target used`() {
        for (spi in 0..12) {
            for (per in 0..12) {
                for (free in listOf(null, "PER", "SPI")) {
                    val used = Detection.attributeFor(spi, per, free)
                    val line = Detection.attributeLine(spi, per, free)
                    assertTrue(
                        line.startsWith("SPI $used ") || line.startsWith("Per $used "),
                        "spi=$spi per=$per freeRoll=$free used=$used but said '$line'",
                    )
                }
            }
        }
    }

    /** The book names two attributes; anything else is a caller's bug. */
    @Test
    fun `an attribute the table never names is refused`() {
        assertThrows<IllegalArgumentException> {
            Detection.attributeFor(spi = 11, per = 10, freeRoll = "IQ")
        }
    }

    /**
     * End to end, and the breakdown says WHY the lower attribute won -- a
     * number that silently disagrees with the sentence above it is the defect
     * class this dialog already produced once (6ag).
     */
    @Test
    fun `the free roll reaches the target and names its reason`() {
        val fool = Detection.awarenessFor("Fool (Seer)")
        assertEquals("PER", fool.freeRoll)
        val result = Detection.attempt(
            spi = 11,
            per = 10,
            awarenessBonus = fool.bonus!!,
            awarenessLabel = fool.pathway,
            freeRoll = fool.freeRoll,
            source = rolling(9),
        )
        assertEquals(12, result.roll.target)
        assertTrue(result.parts.first().contains("Per 10"), result.parts.first())
        assertTrue(result.parts.first().contains("grants"), result.parts.first())
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

    /**
     * **Alertness lifts this roll too, and it is the one bare Per roll the
     * app makes.** "+1 per level to all Per rolls" -- not "all Per-controlled
     * SKILL rolls", so a detection roll that takes Per is covered by the
     * sentence. `TraitEffects.modifiers` cannot reach it, because this roll
     * never goes through a `SkillKey`; it is threaded explicitly instead of
     * left a silent gap (found in review).
     */
    @Test
    fun `Alertness lifts a detection roll that takes Per`() {
        assertEquals(12, Detection.attributeFor(spi = 9, per = 10, alertness = 2))
        assertEquals(11, Detection.attributeFor(spi = 9, per = 10, freeRoll = "PER", alertness = 1))
        // ...and not one that takes SPI: that is not a Per roll.
        assertEquals(11, Detection.attributeFor(spi = 11, per = 10, alertness = 2))
        assertEquals(9, Detection.attributeFor(spi = 9, per = 10, freeRoll = "SPI", alertness = 2))
    }

    /**
     * **A trait may not decide WHICH attribute is rolled.** "The higher of SPI
     * or Per" is a comparison the book makes between two attributes; letting
     * Alertness tip it would be a claim the book never makes. Choose on the
     * raw values, then apply -- the smaller reading, so it is pinned.
     */
    @Test
    fun `Alertness cannot tip the choice of attribute`() {
        // SPI 10 vs Per 10: SPI wins on the tie, and stays winning at +5.
        assertEquals(10, Detection.attributeFor(spi = 10, per = 10, alertness = 5))
        assertTrue(Detection.attributeLine(spi = 10, per = 10, alertness = 5).startsWith("SPI 10"))
    }

    /** The sentence always names the value the target used -- 6ai's guard. */
    @Test
    fun `the attribute line still names the value the target used, with Alertness`() {
        for (spi in 0..12) for (p in 0..12) for (a in 0..3) {
            val used = Detection.attributeFor(spi, p, alertness = a)
            val line = Detection.attributeLine(spi, p, alertness = a)
            assertTrue(
                line.startsWith("SPI $used ") || line.startsWith("Per $used "),
                "spi=$spi per=$p alertness=$a used=$used line=$line",
            )
        }
    }
}
