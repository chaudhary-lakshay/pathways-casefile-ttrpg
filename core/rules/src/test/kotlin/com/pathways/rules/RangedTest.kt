package com.pathways.rules

import com.pathways.model.ModEffect
import com.pathways.model.ModifierSource
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

    /**
     * The melee scope is the rows that print one, and today there is one.
     *
     * Asserted as the WHOLE list rather than as "prone is in it": the claim
     * being pinned is that this table prices exactly one condition for reach.
     * A re-extraction that gave a second row a melee value would be a finding,
     * not a silent widening -- the book's own table (global lines 2665-2685)
     * writes "melee" on one line only.
     */
    @Test
    fun `the melee scope is exactly the rows that print a melee value`() {
        assertEquals(listOf("Target is prone"), Ranged.meleeConditions().map { it.condition })
    }

    @Test
    fun `a prone target in reach is worth the melee number, not the ranged one`() {
        assertEquals(listOf(-4), flats(Ranged.meleeModifiers(Ranged.meleeConditions())))
    }

    @Test
    fun `a row that prints no melee value cannot be applied in reach`() {
        // -3 is its RANGED number. Falling back to it would put a shooting
        // penalty on a punch, which is the defect this scope exists to stop.
        assertThrows<IllegalArgumentException> {
            Ranged.meleeModifiers(listOf(row("Darkness or heavy fog")))
        }
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

    // ---- Night Eyes ------------------------------------------------------

    /**
     * **The relief lands AFTER the book's own bounds check, never through it.**
     *
     * A design review caught this before a line was written: `gmValue` is
     * validated against the printed -9..-3, and the test above pins that -1 is
     * REFUSED there. So feeding a relieved value in through that door would
     * throw on Roll -- a chip that throws is not a choice (6t), and it is the
     * lock-up 6bk's picker was fixed for. The GM's severity stays raw and the
     * trait moves the result.
     */
    @Test
    fun `Night Eyes reduces the darkness penalty after the GM's severity`() {
        val fog = row("Darkness or heavy fog")
        val at = { value: Int ->
            flats(
                Ranged.modifiers(
                    conditions = listOf(fog),
                    gmValue = mapOf(fog.condition to value),
                    darknessCause = DarknessCause.DARKNESS,
                    darknessReduction = TraitEffects.NIGHT_EYES_REDUCTION,
                ),
            )
        }
        assertEquals(listOf(-1), at(-3))
        assertEquals(listOf(-4), at(-6))
        assertEquals(listOf(-7), at(-9))
        // The GM's own number is still checked against the printed range.
        assertThrows<IllegalArgumentException> {
            Ranged.modifiers(
                conditions = listOf(fog),
                gmValue = mapOf(fog.condition to -10),
                darknessCause = DarknessCause.DARKNESS,
                darknessReduction = TraitEffects.NIGHT_EYES_REDUCTION,
            )
        }
    }

    /**
     * **Fog is not darkness, and the trait never writes the word.**
     *
     * The row prices two causes with one number -- "Darkness **or heavy fog**"
     * -- while the trait says "all **darkness** penalties" and names only
     * light levels (gaslight, moonlight). Eyes that see in the dark do not see
     * through fog. Extending it would author a rule from a resemblance, the
     * reasoning 6aj refused when ch. 4 printed none of Appendix A's words.
     */
    @Test
    fun `Night Eyes does nothing against fog`() {
        val fog = row("Darkness or heavy fog")
        assertEquals(
            listOf(-3),
            flats(
                Ranged.modifiers(
                    conditions = listOf(fog),
                    darknessCause = DarknessCause.HEAVY_FOG,
                    darknessReduction = TraitEffects.NIGHT_EYES_REDUCTION,
                ),
            ),
        )
        // ...and the row is untouched for anyone who lacks the trait, whatever
        // they declare the cause to be.
        assertEquals(
            listOf(-3),
            flats(
                Ranged.modifiers(
                    conditions = listOf(fog),
                    darknessCause = DarknessCause.DARKNESS,
                    darknessReduction = 0,
                ),
            ),
        )
    }

    /**
     * **The second clause cannot be derived from the first**, which is why it
     * is a state of its own. "in dim gaslight or moonlight suffer no penalty
     * at all" -- subtracting 2 from the gentlest printed severity gives -1,
     * and the book says 0. Specific beats general.
     *
     * The row then produces NO modifier at all, which is the existing
     * behaviour for a value of zero rather than anything new.
     */
    @Test
    fun `dim gaslight or moonlight cancels the penalty outright`() {
        val fog = row("Darkness or heavy fog")
        for (severity in -9..-3) {
            assertTrue(
                Ranged.modifiers(
                    conditions = listOf(fog),
                    gmValue = mapOf(fog.condition to severity),
                    darknessCause = DarknessCause.DIM_LIGHT,
                    darknessReduction = TraitEffects.NIGHT_EYES_REDUCTION,
                ).isEmpty(),
                "a severity of $severity should still cancel under dim light",
            )
        }
        // The general clause would leave -1 at the gentlest end, so the two
        // clauses really do differ and neither is the other restated.
        assertEquals(
            listOf(-1),
            flats(
                Ranged.modifiers(
                    conditions = listOf(fog),
                    darknessCause = DarknessCause.DARKNESS,
                    darknessReduction = TraitEffects.NIGHT_EYES_REDUCTION,
                ),
            ),
        )
    }

    /**
     * A relief can reach zero and must never overshoot into a bonus. -9 + 2 is
     * -7 today, so nothing printed can reach the floor -- it is the sign-flip
     * class 4.3 exists for, guarded before a caller finds it (6l).
     */
    @Test
    fun `the relief floors at zero rather than becoming a bonus`() {
        val fog = row("Darkness or heavy fog")
        assertEquals(
            listOf(-3),
            flats(
                Ranged.modifiers(
                    conditions = listOf(fog),
                    gmValue = mapOf(fog.condition to -6),
                    darknessCause = DarknessCause.DARKNESS,
                    darknessReduction = 3,
                ),
            ),
        )
        // A relief larger than the penalty cancels it and stops there. Nothing
        // printed can reach this -- the book's reduction is 2 against a worst
        // case of -9 -- which is why it is guarded now rather than found.
        assertTrue(
            Ranged.modifiers(
                conditions = listOf(fog),
                gmValue = mapOf(fog.condition to -9),
                darknessCause = DarknessCause.DARKNESS,
                darknessReduction = 99,
            ).isEmpty(),
        )
    }

    /**
     * **The breakdown has to reconcile with the chip that produced it.** The
     * chip reads "-3 to -9" and the contribution would read -1 with nothing
     * saying why -- the numbers-that-do-not-add-up defect 6ag added a 300-case
     * test for and 6ai repeated. The rules author the description.
     */
    @Test
    fun `a relieved row says which trait relieved it`() {
        val fog = row("Darkness or heavy fog")
        val relieved = Ranged.modifiers(
            conditions = listOf(fog),
            darknessCause = DarknessCause.DARKNESS,
            darknessReduction = TraitEffects.NIGHT_EYES_REDUCTION,
        ).single()
        val said = (relieved.source as ModifierSource.Situational).description
        assertTrue(said.contains(TraitEffects.NIGHT_EYES), said)
        // Untouched, it is still the book's own row and nothing else.
        val plain = Ranged.modifiers(conditions = listOf(fog)).single()
        assertEquals(fog.condition, (plain.source as ModifierSource.Situational).description)
    }

    @Test
    fun `an Aim row is worth the chosen weapon's Acc`() {
        // This test used to assert the opposite -- that an Aim row could only
        // ever be refused, "because no weapon in this book has an Acc". The
        // book prints nine of them, in chapter 8, which nothing had read; the
        // row's own cell says "see weapon table".
        val aim = row("Shooter used Aim last turn")
        assertTrue(aim.weaponAcc)
        assertNull(aim.modifier)
        assertEquals(0, aim.accBonus)
        assertEquals(listOf(4), flats(Ranged.modifiers(conditions = listOf(aim), weaponAcc = 4)))
    }

    @Test
    fun `an Aim row with no weapon is still refused rather than applied as zero`() {
        val ex = assertThrows<IllegalArgumentException> {
            Ranged.modifiers(conditions = listOf(row("Shooter used Aim last turn")))
        }
        assertTrue(ex.message!!.contains("no weapon was chosen"))
    }

    @Test
    fun `a shot is aimed for one length of time, not both`() {
        // Both rows are the same aim held longer. Applying them together pays
        // the weapon's Acc twice and blows through the second row's own
        // printed ceiling, "max Acc +2 total".
        val ex = assertThrows<IllegalArgumentException> {
            Ranged.modifiers(
                conditions = listOf(
                    row("Shooter used Aim last turn"),
                    row("Shooter used Aim for 3+ turns"),
                ),
                weaponAcc = 4,
            )
        }
        assertTrue(ex.message!!.contains("one length of time"))
    }

    /**
     * Three rows print one quantity, the target's speed, in bounds that do
     * not meet: stationary, "up to 3 meters/sec", "4+ meters/sec". Walking
     * and running together is -3, a number no speed produces.
     */
    @Test
    fun `a target moves at one speed, not two`() {
        val ex = assertThrows<IllegalArgumentException> {
            Ranged.modifiers(
                conditions = listOf(
                    row("Target walking (up to 3 meters/sec)"),
                    row("Target running (4+ meters/sec)"),
                ),
            )
        }
        assertTrue(ex.message!!.contains("one speed"), ex.message)
        assertTrue(ex.message!!.contains("Target walking") && ex.message!!.contains("Target running"), ex.message)
        // Stationary prints +0, changes no roll, and is still a speed.
        assertThrows<IllegalArgumentException> {
            Ranged.modifiers(
                conditions = listOf(row("Target is stationary"), row("Target running (4+ meters/sec)")),
            )
        }
    }

    @Test
    fun `a row's alternatives are the other speeds or the other aim, and nothing else`() {
        assertEquals(
            setOf("Target is stationary", "Target walking (up to 3 meters/sec)"),
            Ranged.alternativesTo("Target running (4+ meters/sec)"),
        )
        assertEquals(
            setOf("Target walking (up to 3 meters/sec)", "Target running (4+ meters/sec)"),
            Ranged.alternativesTo("Target is stationary"),
        )
        assertEquals(setOf("Shooter used Aim for 3+ turns"), Ranged.alternativesTo("Shooter used Aim last turn"))
        // The shooter's own movement is not the target's speed.
        val alone = listOf(
            "Shooter moved this turn",
            "Target is prone",
            "Darkness or heavy fog",
            "Target has cover (half body)",
        )
        for (condition in alone) {
            assertEquals(emptySet<String>(), Ranged.alternativesTo(condition), condition)
        }
        assertEquals(emptySet<String>(), Ranged.alternativesTo("no such row"))
    }

    @Test
    fun `the second Aim row is not read as plus two`() {
        // "+ weapon's Acc +2 (max Acc +2 total)" -- the first signed integer
        // in that cell is +2, and it is not the modifier. It is what this row
        // adds ON TOP of the weapon's Acc, so a rifle at Acc 4 aims to +6 and
        // not to +2. Reading it as the modifier would make the longer aim
        // WORSE than the short one for every weapon with Acc 3 or better.
        val aim = row("Shooter used Aim for 3+ turns")
        assertTrue(aim.weaponAcc)
        assertNull(aim.modifier)
        assertEquals(2, aim.accBonus)
        assertEquals(
            listOf(6),
            flats(Ranged.modifiers(conditions = listOf(aim), weaponAcc = 4)),
        )
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

    /**
     * **"Canceled by any Active Defense or injury" reached nothing.**
     *
     * The two Acc rows are in the ranged modifiers table and the sentence that
     * takes them away is printed one table over, on the Aim maneuver itself --
     * so an aim survived a parry and a bullet, and a rifleman who defended
     * between taking aim and firing still collected the full Acc.
     *
     * A refusal rather than a penalty: the rows are withdrawn and the book's
     * sentence shown in their place, the call a Broken mystical tongue (6x)
     * and an unarmed parry against a shot (6bj) each needed.
     */
    @Test
    fun `a cancelled aim withdraws both Acc rows and nothing else`() {
        val all = Ranged.offeredConditions(cancelled = false)
        assertEquals(Ranged.conditions(), all)
        val left = Ranged.offeredConditions(cancelled = true)
        assertEquals(all.filterNot { it.weaponAcc }, left)
        // Exactly the two aim rows go; the other seven are untouched by it.
        assertEquals(2, all.size - left.size)
        assertTrue(left.none { it.weaponAcc })
    }

    /**
     * A plan still carrying the aim is refused, not quietly stripped.
     *
     * A turn was spent taking it, so a caller told nothing would believe the
     * Acc had been counted. Nothing reachable can build this plan -- the
     * screen withdraws the rows -- which is exactly why the guard exists: the
     * permissive path is what the next caller finds (6l).
     */
    @Test
    fun `the rules refuse an aim the GM has cancelled`() {
        val aim = row("Shooter used Aim last turn")
        val ex = assertThrows<IllegalArgumentException> {
            Ranged.modifiers(conditions = listOf(aim), weaponAcc = 4, aimCancelled = true)
        }
        assertTrue(ex.message!!.contains("Canceled by any Active Defense or injury"), ex.message)
        // The other rows still apply while the aim is gone: being hurt does
        // not move the target or lift the fog.
        val prone = row("Target is prone")
        assertEquals(
            listOf(-2),
            flats(Ranged.modifiers(conditions = listOf(prone), weaponAcc = 4, aimCancelled = true)),
        )
    }

    /**
     * The cancelling cell and the Acc rows are the same aim, and agree.
     *
     * The maneuver prints "max ACC+2" and the modifiers table "max Acc +2
     * total" -- one printed ceiling, said twice. That agreement is the whole
     * warrant for reading a maneuver's sentence as governing another table's
     * rows (6ad, and 6v is why it can never be per-source).
     */
    @Test
    fun `the aim maneuver's cell agrees with the rows it cancels`() {
        val printed = Ranged.printedAim()
        assertTrue(printed.contains("Canceled by any Active Defense or injury"), printed)
        assertTrue(printed.contains("max ACC+2"), printed)
        val longAim = row("Shooter used Aim for 3+ turns")
        assertEquals(2, longAim.accBonus)
        assertTrue(longAim.printed.contains("max Acc +2 total"), longAim.printed)
    }
}
