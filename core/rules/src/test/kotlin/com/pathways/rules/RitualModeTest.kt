package com.pathways.rules

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * The two ways Ch. 7 resolves a ritual (p. 61-63).
 *
 * The mode is not decoration: it decides which preparation table applies and
 * whether the target link is a modifier of its own or already inside one.
 */
class RitualModeTest {

    @Test
    fun `the book prints two preparation tables and they disagree`() {
        // RECORDED, never repaired. Excellent is +2 in the Quick table and +3
        // in the Full one, and until this slice the app applied the Full one
        // to every ritual -- so a quick ritual rolled a point too high.
        assertEquals(2, RitualMode.QUICK.preparationNamed("Excellent").modifier)
        assertEquals(3, RitualMode.FULL.preparationNamed("Excellent").modifier)
        assertNotEquals(
            RitualMode.QUICK.preparationNamed("Excellent").modifier,
            RitualMode.FULL.preparationNamed("Excellent").modifier,
        )
    }

    @Test
    fun `they agree on Adequate, which is what makes the difference real`() {
        // Two unrelated ladders would differ everywhere. These are two
        // printings of one idea that part company on exactly one row.
        assertEquals(0, RitualMode.QUICK.preparationNamed("Adequate").modifier)
        assertEquals(0, RitualMode.FULL.preparationNamed("Adequate").modifier)
    }

    @Test
    fun `each mode offers only its own table's rows`() {
        assertEquals(
            listOf("Excellent", "Adequate", "Poor / None"),
            RitualMode.QUICK.preparations().map { it.quality },
        )
        assertEquals(
            listOf("Excellent", "Good", "Adequate", "Poor", "None"),
            RitualMode.FULL.preparations().map { it.quality },
        )
        // "Good" is a Full-mode row. Asking the Quick table for it is a caller
        // bug, not a reason to fall back on the other mode's number.
        assertThrows<IllegalArgumentException> { RitualMode.QUICK.preparationNamed("Good") }
    }

    @Test
    fun `the target link is its own modifier in quick mode only`() {
        // Quick: "Sum the following two modifiers: 1. Preparation Quality
        // 2. Target Link". Full: the link is one of the declarations that
        // "feed into a single Preparation Modifier", and its rows say so --
        // Excellent is "perfect link", Poor is "weak link".
        assertTrue(RitualMode.QUICK.usesTargetLink)
        assertTrue(!RitualMode.FULL.usesTargetLink)
        assertEquals(4, RitualMode.linkNamed("Perfect").modifier)
    }

    @Test
    fun `a full ritual refuses a link rather than paying it twice`() {
        // The whole reason the flag exists. A perfect link is +4, and the Full
        // table's Excellent row already includes one, so adding it again is +8
        // on a 3d6 roll -- a near-certain success bought by a parsing slip.
        val ex = assertThrows<IllegalArgumentException> {
            RitualMode.FULL.modifierTotal(
                preparation = RitualMode.FULL.preparationNamed("Excellent"),
                link = RitualMode.linkNamed("Perfect"),
            )
        }
        assertTrue(ex.message!!.contains("already"))
    }

    @Test
    fun `quick mode sums preparation and link`() {
        assertEquals(
            6,
            RitualMode.QUICK.modifierTotal(
                preparation = RitualMode.QUICK.preparationNamed("Excellent"),
                link = RitualMode.linkNamed("Perfect"),
            ),
            "+2 preparation and +4 link",
        )
        assertEquals(
            -6,
            RitualMode.QUICK.modifierTotal(
                preparation = RitualMode.QUICK.preparationNamed("Poor / None"),
                link = RitualMode.linkNamed("None"),
            ),
            "-2 at the gentle end of its range, and -4",
        )
    }

    @Test
    fun `a range is applied at the end nearer zero`() {
        // The gentle reading the darkness row, the fright triggers and the
        // Phase I preparations already settled. "Poor / None" prints
        // "-2 to -4" and "+0 to -1" is a link that barely hurts.
        assertEquals(-2, RitualMode.QUICK.preparationNamed("Poor / None").modifier)
        assertEquals(-4, RitualMode.QUICK.preparationNamed("Poor / None").best)
        assertEquals(0, RitualMode.linkNamed("Weak").modifier)
        assertEquals(-1, RitualMode.linkNamed("Weak").best)
    }

    @Test
    fun `five supporting skills grant five points and the book allows three`() {
        // "These bonuses are cumulative but cap at +3 total from supporting
        // skills." Without the cap a scholar with the lot rolls at +5.
        assertEquals(5, RitualMode.supportSkills().size)
        assertTrue(RitualMode.supportSkills().all { it.bonus == 1 })
        assertEquals(3, RitualMode.supportBonus(RitualMode.supportSkills().map { it.skill }))
        assertEquals(2, RitualMode.supportBonus(listOf("Occultism", "Thaumatology")))
        assertEquals(0, RitualMode.supportBonus(emptyList()))
    }

    @Test
    fun `a skill the table does not print grants nothing, and is refused`() {
        // Silently ignoring it would let a typo cost a caster a point they
        // thought they had.
        assertThrows<IllegalArgumentException> { RitualMode.supportBonus(listOf("Brawling")) }
    }

    @Test
    fun `naming one supporting skill twice does not pay twice`() {
        assertEquals(1, RitualMode.supportBonus(listOf("Occultism", "Occultism")))
    }

    @Test
    fun `the support bonus applies in both modes`() {
        // Section II sits above the mode split and says nothing about either,
        // so it is not scoped to one -- unlike the link, which each mode
        // handles its own way.
        assertEquals(3, RitualMode.QUICK.supportCap)
        assertEquals(3, RitualMode.FULL.supportCap)
    }
}
