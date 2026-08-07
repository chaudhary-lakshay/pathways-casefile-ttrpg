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
