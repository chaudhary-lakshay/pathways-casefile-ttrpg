package com.pathways.rules

import com.pathways.model.ModEffect
import com.pathways.model.SourceKind
import com.pathways.rules.content.ContentLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Distance and conditions on a shot.
 *
 * The interesting cases are all places where one printed cell means more than
 * one thing: the overlapping bands, the row with a melee value of its own, the
 * range the GM picks inside, and the two rows whose number this book never
 * prints.
 */
class RangedTest {

    private fun row(condition: String) =
        ContentLoader.rangedModifiers().first { it.condition == condition }

    private fun flats(mods: List<com.pathways.model.Modifier>) =
        mods.map { (it.effect as ModEffect.Flat).n }

    @Test
    fun `the penalty grows with distance`() {
        assertEquals(0, Ranged.penaltyFor(1))
        assertEquals(-1, Ranged.penaltyFor(4))
        assertEquals(-2, Ranged.penaltyFor(10))
        assertEquals(-3, Ranged.penaltyFor(20))
        assertEquals(-4, Ranged.penaltyFor(50))
        assertEquals(-5, Ranged.penaltyFor(75))
        assertEquals(-6, Ranged.penaltyFor(400))
    }

    @Test
    fun `point blank is the first band, not a missing one`() {
        assertEquals(0, Ranged.penaltyFor(0))
        assertTrue(Ranged.bandFor(0).printed.startsWith("Up to 2 meters"))
    }

    @Test
    fun `the nearer band wins where the book's bands overlap`() {
        // "51-100 meters" is -5 and "100+ meters" is -6, so exactly 100
        // matches both. The closed band wins -- if the open one did, "51-100"
        // would silently stop at 99.
        assertEquals(-5, Ranged.penaltyFor(100))
        assertTrue(Ranged.bandFor(100).printed.startsWith("51"))
        assertEquals(-6, Ranged.penaltyFor(101))
    }

    @Test
    fun `the overlap really is in the book - guard against silent repair`() {
        // If someone "tidies" range_penalties.json so the bands abut, the test
        // above would still pass while testing nothing.
        val bands = ContentLoader.rangePenalties()
        val closed = bands.first { it.printed.startsWith("51") }
        val open = bands.first { it.to == null }
        assertEquals(100, closed.to)
        assertEquals(100, open.from)
    }

    @Test
    fun `the open band keeps its own penalty, not the one its floor resolves to`() {
        // The overlap bites here: bandFor(100) is the -5 row, so a caller
        // that stored the "100+" band and passed back its lower bound would
        // show -6 and roll -5 (Codex). Bands are passed whole for that reason.
        val open = ContentLoader.rangePenalties().single { it.to == null }
        assertEquals(100, open.from)
        assertEquals(-5, Ranged.penaltyFor(open.from))
        assertEquals(listOf(-6), flats(Ranged.modifiers(band = open)))
    }

    @Test
    fun `a negative distance is refused`() {
        assertThrows<IllegalArgumentException> { Ranged.penaltyFor(-1) }
    }

    @Test
    fun `the open band has no upper bound at all`() {
        val open = ContentLoader.rangePenalties().single { it.to == null }
        assertTrue(open.gmDiscretion, "'100+' prints '-6 or worse (GM discretion)'")
        assertEquals(-6, Ranged.penaltyFor(10_000))
    }

    @Test
    fun `distance and conditions stack as separate modifiers`() {
        val mods = Ranged.modifiers(
            band = Ranged.bandFor(30),
            conditions = listOf(row("Target running (4+ meters/sec)"), row("Shooter moved this turn")),
        )
        // 30 metres is the 21-50 band, so -4, then -2 and -2 beside it.
        assertEquals(listOf(-4, -2, -2), flats(mods))
    }

    @Test
    fun `a condition worth nothing produces no modifier`() {
        // "Target is stationary: +0" is a real row that changes no roll.
        assertTrue(Ranged.modifiers(conditions = listOf(row("Target is stationary"))).isEmpty())
    }

    @Test
    fun `a prone target carries a different number in reach`() {
        // "-2 ranged (but -4 in melee)". Dropping the melee value would leave
        // the app applying -2 to a kick: right sign, wrong rule.
        val prone = row("Target is prone")
        assertEquals(-2, prone.modifier)
        assertEquals(-4, prone.melee)
    }

    @Test
    fun `darkness takes the GM's number, inside the range the book prints`() {
        val fog = row("Darkness or heavy fog")
        assertEquals(-3, fog.modifier)
        assertEquals(-9, fog.worstCase)
        val worst = Ranged.modifiers(
            conditions = listOf(fog),
            gmValue = mapOf(fog.condition to -9),
        )
        assertEquals(listOf(-9), flats(worst))
        // Harsher than the book's worst, or gentler than its best, is refused.
        assertThrows<IllegalArgumentException> {
            Ranged.modifiers(conditions = listOf(fog), gmValue = mapOf(fog.condition to -10))
        }
        assertThrows<IllegalArgumentException> {
            Ranged.modifiers(conditions = listOf(fog), gmValue = mapOf(fog.condition to -1))
        }
    }

    @Test
    fun `an Aim row is refused, because no weapon in this book has an Acc`() {
        val aim = row("Shooter used Aim last turn")
        assertTrue(aim.weaponAcc)
        assertNull(aim.modifier)
        val ex = assertThrows<IllegalArgumentException> {
            Ranged.modifiers(conditions = listOf(aim))
        }
        assertTrue(ex.message!!.contains("no weapon table"))
    }

    @Test
    fun `the second Aim row is not read as plus two`() {
        // "+ weapon's Acc +2 (max Acc +2 total)" -- the first signed integer
        // in that cell is +2, and it is not the modifier.
        val aim = row("Shooter used Aim for 3+ turns")
        assertTrue(aim.weaponAcc)
        assertNull(aim.modifier)
    }

    @Test
    fun `cover reaches the roll but its DR does not`() {
        // "-2 to hit, +2 DR on covered areas": only the first is a to-hit
        // modifier. DR belongs to the damage side, which is not modelled.
        val cover = row("Target has cover (half body)")
        assertEquals(-2, cover.modifier)
        assertEquals(listOf(-2), flats(Ranged.modifiers(conditions = listOf(cover))))
    }

    @Test
    fun `a shot's circumstances are situational, never a state threshold`() {
        val mods = Ranged.modifiers(band = Ranged.bandFor(30))
        assertEquals(listOf(SourceKind.SITUATIONAL), mods.map { it.source.kind })
    }
}
