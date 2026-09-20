package com.pathways.rules

import com.pathways.model.Attributes
import com.pathways.model.MAX_SECONDARY_DELTA_MAGNITUDE
import com.pathways.model.SecondaryDelta
import com.pathways.rules.content.ContentLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Buying a secondary characteristic by the book's own row key (Ch. 2, pp. 8-9).
 *
 * [SecondaryCharacteristics.costOf] has billed a [SecondaryDelta] since M0 and
 * nothing in production ever built one: the six priced rows could not be
 * bought. The key is the one the book's table prints, so the mapping from a
 * row to the field it moves lives HERE and not at each caller.
 *
 * The expectations are written as whole [SecondaryDelta] literals on purpose.
 * Four of the six rows cost 5 points, so a mapping that sent Will, Per, Basic
 * Speed and Basic Move to one field would satisfy `SecondaryCostPinTest`'s
 * price loop unchanged (Codex, on the design) -- only a field-by-field
 * assertion can see it, and that test keeps its own independent `when` as the
 * oracle rather than calling this mapping.
 */
class SecondaryPurchaseTest {

    private val none = SecondaryDelta()

    @Test
    @DisplayName("each priced row moves its own field and no other")
    fun `a step lands on the field the book's row names`() {
        assertEquals(SecondaryDelta(hp = 1), SecondaryCharacteristics.withSteps(none, "hp", 1))
        assertEquals(SecondaryDelta(fp = 1), SecondaryCharacteristics.withSteps(none, "fp", 1))
        assertEquals(SecondaryDelta(will = 1), SecondaryCharacteristics.withSteps(none, "will", 1))
        assertEquals(SecondaryDelta(per = 1), SecondaryCharacteristics.withSteps(none, "per", 1))
        assertEquals(
            SecondaryDelta(basicSpeedQuarters = 1),
            SecondaryCharacteristics.withSteps(none, "basicSpeed", 1),
            "the book prices Basic Speed per 0.25, so one step is one quarter",
        )
        assertEquals(
            SecondaryDelta(basicMove = 1),
            SecondaryCharacteristics.withSteps(none, "basicMove", 1),
        )
    }

    @Test
    @DisplayName("a step is read back as the number that was set")
    fun `stepsOf answers what withSteps wrote`() {
        val bought = SecondaryCharacteristics.withSteps(none, "will", -2)
        assertEquals(-2, SecondaryCharacteristics.stepsOf(bought, "will"))
        assertEquals(0, SecondaryCharacteristics.stepsOf(bought, "per"), "Per was never touched")
    }

    @Test
    @DisplayName("a row the table refuses to price cannot be bought, and nor can a typo")
    fun `an unpriced or unknown key is a no-op`() {
        for (key in listOf("spi", "cor", "digestion", "basicSpeeed", "")) {
            assertEquals(none, SecondaryCharacteristics.withSteps(none, key, 3), key)
            assertEquals(0, SecondaryCharacteristics.stepsOf(none, key), key)
        }
    }

    @Test
    @DisplayName("bought steps add to what the character already carries")
    fun `plusSteps adds rather than replaces`() {
        // A potion grant sits in this field too (PotionDigestion), so a sparse
        // map naming one row must not zero the rest.
        val granted = SecondaryDelta(will = 1)
        val merged = SecondaryCharacteristics.plusSteps(granted, mapOf("hp" to 2, "will" to 1))
        assertEquals(SecondaryDelta(hp = 2, will = 2), merged)
    }

    @Test
    @DisplayName("a hand-edited save cannot push a field past the model's bound")
    fun `steps are coerced rather than thrown on`() {
        val huge = SecondaryCharacteristics.plusSteps(none, mapOf("hp" to 10_000))
        assertEquals(MAX_SECONDARY_DELTA_MAGNITUDE, huge.hp)
        val tiny = SecondaryCharacteristics.plusSteps(none, mapOf("hp" to -10_000))
        assertEquals(-MAX_SECONDARY_DELTA_MAGNITUDE, tiny.hp)
    }

    @Test
    @DisplayName("every row the book prices is buyable and has a value to show")
    fun `the offered set is the content file's own priced rows`() {
        val priced = ContentLoader.secondaryCosts().filter { it.pointsPerStep != null }
        assertEquals(6, priced.size)
        for (row in priced) {
            assertEquals(
                1,
                SecondaryCharacteristics.stepsOf(
                    SecondaryCharacteristics.withSteps(none, row.key, 1),
                    row.key,
                ),
                "${row.characteristic} is priced and nothing moves when it is bought",
            )
            assertNotNull(
                SecondaryCharacteristics.valueLabelOf(distinct, row.key),
                "${row.characteristic} is priced and has no value to show beside its stepper",
            )
        }
        for (row in ContentLoader.secondaryCosts().filter { it.pointsPerStep == null }) {
            assertNull(
                SecondaryCharacteristics.valueLabelOf(distinct, row.key),
                "${row.characteristic} is not priced and must not offer a stepper",
            )
        }
    }

    /**
     * Six characteristics whose values are all DIFFERENT, so a label reading
     * the wrong field is visible.
     *
     * A default character has HP 9 and FP 9, which is what made the first
     * version of the test below survive reading HP out of FP (Codex).
     */
    private val distinct = SecondaryCharacteristics.of(
        Attributes(st = 10, dx = 13, iq = 14, ht = 11),
        SecondaryDelta(hp = 1, fp = 2, will = 3, per = 5, basicSpeedQuarters = 2, basicMove = 6),
    )

    @Test
    @DisplayName("each row shows its own value, and Basic Speed shows the book's two decimals")
    fun `a value label reads the field its row names`() {
        assertEquals("11", SecondaryCharacteristics.valueLabelOf(distinct, "hp"))
        assertEquals("13", SecondaryCharacteristics.valueLabelOf(distinct, "fp"))
        assertEquals("17", SecondaryCharacteristics.valueLabelOf(distinct, "will"))
        assertEquals("19", SecondaryCharacteristics.valueLabelOf(distinct, "per"))
        // (HT 11 + DX 13) quarters + 2 = 26 quarters = 6.50, and Basic Move is
        // the whole speed plus its own six.
        assertEquals("6.50", SecondaryCharacteristics.valueLabelOf(distinct, "basicSpeed"))
        assertEquals("12", SecondaryCharacteristics.valueLabelOf(distinct, "basicMove"))
    }
}
