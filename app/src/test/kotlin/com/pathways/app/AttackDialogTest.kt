package com.pathways.app

import com.pathways.model.ControllingAttribute
import com.pathways.model.Attr
import com.pathways.model.Difficulty
import com.pathways.model.ModEffect
import com.pathways.model.SkillKey
import com.pathways.model.Trait
import com.pathways.model.TraitKind
import com.pathways.rules.AttackManeuver
import com.pathways.rules.AttackPlan
import com.pathways.rules.Attacks
import com.pathways.rules.Conditions
import com.pathways.rules.Pools
import com.pathways.rules.Shock
import com.pathways.rules.Wounds
import com.pathways.rules.DarknessCause
import com.pathways.rules.TraitEffects
import com.pathways.rules.Ranged
import com.pathways.rules.Weapons
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AttackDialogTest {

    private fun flats(mods: List<com.pathways.model.Modifier>) =
        mods.map { (it.effect as ModEffect.Flat).n }

    @Test
    fun `a firearm is fired at range and a melee weapon is not`() {
        // REGRESSION (Codex): picking a Guns skill armed the character with a
        // revolver but left the plan in reach, so an immediate roll took
        // All-Out Determined's MELEE +4 instead of its ranged +1 and the
        // distance and Aim rows never appeared at all.
        assertTrue(Armed(Weapons.named("Revolver (.38 cal)")).isFired)
        assertTrue(Armed(Weapons.named("Bolt-Action Rifle")).isFired)
        assertFalse(Armed(Weapons.named("Broadsword")).isFired)
        assertFalse(Armed(Weapons.named("Combat Knife")).isFired)
    }

    @Test
    fun `an empty hand is not a ranged attack`() {
        // The nullable receiver is the whole point: putting a weapon down has
        // to answer this question too, and this book prints no thrown weapon.
        val empty: Armed? = null
        assertFalse(empty.isFired)
    }

    @Test
    fun `only a fired weapon lends its Acc to an Aim`() {
        // REGRESSION (Codex): the Aim chips were gated on merely HOLDING a
        // weapon, and the "At range" toggle stays the player's -- so a Hand
        // Axe, which prints Acc 1, could be aimed for +1, or +3 across three
        // turns. Aiming is something you do with a gun.
        assertEquals(4, Armed(Weapons.named("Bolt-Action Rifle")).aimAcc)
        assertEquals(2, Armed(Weapons.named("Revolver (.38 cal)")).aimAcc)
        assertNull(Armed(Weapons.named("Hand Axe")).aimAcc)
        assertNull(Armed(Weapons.named("Combat Knife")).aimAcc)
        assertNull((null as Armed?).aimAcc)
    }

    @Test
    fun `the hand axe really does print an Acc, which is why the gate matters`() {
        // If it were 0 the defect would have been invisible: Ranged.modifiers
        // drops a zero rather than adding one.
        assertEquals(1, Weapons.named("Hand Axe").acc)
    }

    @Test
    fun `a prone target is priced once, on whichever side of the toggle the plan is`() {
        // REGRESSION GUARD, and the whole safety property of this slice. The
        // row prints two numbers -- "-2 ranged (but -4 in melee)" -- and the
        // two helpers read the SAME plan, so exactly one of them may speak.
        //
        // Reachable in two taps: select the chip in reach, then tap "At range".
        // AttackDialog's onPlan lambda clears the set only when going the other
        // way, so the selection survives; without the guard both helpers fire
        // and the roll takes -6, which is nowhere in the book.
        val prone = setOf("Target is prone")
        assertEquals(listOf(-4), flats(meleeModifiers(AttackPlan(isRanged = false), prone)))
        assertTrue(meleeModifiers(AttackPlan(isRanged = true), prone).isEmpty())
    }

    @Test
    fun `nothing else in that table reaches a melee attack`() {
        // The other eight rows are distance, cover, movement and light: priced
        // for a shot and for nothing else. Offering one in reach would be the
        // sign-right-rule-wrong defect the melee value exists to prevent.
        val everything = Ranged.conditions().map { it.condition }.toSet()
        assertEquals(
            listOf(-4),
            flats(meleeModifiers(AttackPlan(isRanged = false), everything)),
        )
    }

    @Test
    fun `a weapon opens on the first attack the book prints`() {
        // The Combat Knife cuts before it thrusts, matching how an unarmed
        // skill opens on its first listed attack rather than on nothing.
        val knife = Armed(Weapons.named("Combat Knife"))
        assertEquals("sw-1 cut", knife.mode.printed)
        assertEquals(2, knife.weapon.damage.size)
    }

    @Test
    fun `the severity the GM picked reaches the darkness row's modifier`() {
        // The commit that added the picker wired it through `gmValue`, and
        // nothing asserted the wiring: deleting that argument left every test
        // green while the row went back to its gentlest printed figure.
        val darkness = Ranged.conditions().single { it.worstCase != null }
        val ranged = AttackPlan(isRanged = true)
        val unpicked = rangedModifiers(
            plan = ranged,
            band = null,
            circumstances = setOf(darkness.condition),
            weaponAcc = null,
            aimCancelled = false,
            darknessCause = DarknessCause.DARKNESS,
            darknessReduction = 0,
            darknessSeverity = null,
        )
        assertEquals(listOf(darkness.modifier), flats(unpicked))
        val picked = rangedModifiers(
            plan = ranged,
            band = null,
            circumstances = setOf(darkness.condition),
            weaponAcc = null,
            aimCancelled = false,
            darknessCause = DarknessCause.DARKNESS,
            darknessReduction = 0,
            darknessSeverity = darkness.worstCase,
        )
        assertEquals(listOf(darkness.worstCase), flats(picked))
    }

    @Test
    fun `a severity left over from a scene that is no longer lit cannot price one`() {
        // "Another turn" clears the row along with the severity, so the key
        // is unmatched rather than applied -- and an unmatched key is inert
        // rather than a throw, which is what makes the clearing a tidiness
        // fix and not a crash fix.
        val darkness = Ranged.conditions().single { it.worstCase != null }
        assertEquals(
            emptyList<Int>(),
            flats(
                rangedModifiers(
                    plan = AttackPlan(isRanged = true),
                    band = null,
                    circumstances = emptySet(),
                    weaponAcc = null,
                    aimCancelled = false,
                    darknessCause = DarknessCause.DARKNESS,
                    darknessReduction = 0,
                    darknessSeverity = darkness.worstCase,
                ),
            ),
        )
    }

    /**
     * **The attack roll took no trait term**, the one roll on the sheet that
     * skipped it (6ev). Reluctant Killer's to-hit clause is the first that
     * reaches it, and only once the GM declares the target.
     */
    @Test
    fun `the attack roll takes the trait terms and the declared to-hit clause`() {
        val killer = listOf(Trait(TraitEffects.RELUCTANT_KILLER, 5, TraitKind.DISADVANTAGE))
        val guns = SkillKey("Guns", "Pistol", ControllingAttribute.Buyable(Attr.DX), Difficulty.EASY)
        assertEquals(emptyList<Int>(), flats(traitTerms(killer, guns, killerTarget = null)))
        val clauses = TraitEffects.attackClauses(killer)
        assertEquals(listOf(-4), flats(traitTerms(killer, guns, clauses[0].key)))
        assertEquals(listOf(-2), flats(traitTerms(killer, guns, clauses[1].key)))
    }

    /**
     * **The dialog offers every owned skill, not the combat banner** (the
     * diff review's MEDIUM). The census behind "costs nothing yet" read the
     * 19 COMBAT SKILLS keys, so it could not see Perfect Balance's +2 reach
     * an Acrobatics attack here, as it does on the Skills tab.
     */
    @Test
    fun `a trait bonus on a non-combat skill reaches the attack too`() {
        val balance = listOf(Trait(TraitEffects.PERFECT_BALANCE, 15, TraitKind.ADVANTAGE))
        val acrobatics = SkillKey("Acrobatics", null, ControllingAttribute.Buyable(Attr.DX), Difficulty.HARD)
        assertEquals(listOf(2), flats(traitTerms(balance, acrobatics, killerTarget = null)))
    }

    private val brawling =
        SkillKey("Brawling", null, ControllingAttribute.Buyable(Attr.DX), Difficulty.EASY)

    /** Reeling at 4 of 14 HP, and shocked: the two tracker rows a brawler meets. */
    private val hurt = Conditions.forSkill(brawling, Pools.hp(4, 14), Pools.fp(11, 11)) +
        Shock.modifiers(shocked = true, traits = emptyList())

    /**
     * The attack dialog prints its target before the dice now (6gd), with the
     * trackers on a line of their own. That line is read off the ledger by
     * id: a remainder (target less everything else printed) would hide a term
     * the screen forgot.
     */
    @Test
    fun `the tracker line is what the ledger applied for the trackers alone`() {
        val deceptive = Attacks.modifiers(AttackPlan(deceptive = 2), 16)

        val standing = attackStanding("Brawling", 16, brawling, hurt, deceptive)

        assertEquals(-5, standing.fromTrackers, "reeling -1 and shock -4")
        assertEquals(16 - 4 - 5, standing.target.target)
        assertEquals(0, attackStanding("Brawling", 16, brawling, emptyList(), deceptive).fromTrackers)
    }

    /**
     * What makes the lines above Roll add up to the target under them: every
     * term is a plain signed number. A grouped or superseding modifier in any
     * of these lists would move the target by less than its line prints.
     */
    @Test
    fun `the level and the printed terms add up to the attack's target`() {
        val level = 16
        var plans = 0
        for (maneuver in AttackManeuver.entries) {
            for (deceptive in 0..Attacks.maxDeceptive(level)) {
                for (location in listOf(null) + Wounds.locations()) {
                    // The trackers' own number, written out: taking it from
                    // the function under test would let both sides move
                    // together (the diff review).
                    for ((trackers, cost) in listOf(emptyList<com.pathways.model.Modifier>() to 0, hurt to -5)) {
                        val picked = Attacks.modifiers(AttackPlan(maneuver, deceptive = deceptive), level) +
                            listOfNotNull(location?.let(Wounds::modifier))
                        val standing = attackStanding("Brawling", level, brawling, trackers, picked)
                        val plan = "$maneuver, deceptive $deceptive, ${location?.target}"
                        assertEquals(cost, standing.fromTrackers, plan)
                        assertEquals(level + flats(picked).sum() + cost, standing.target.target, plan)
                        plans++
                    }
                }
            }
        }
        assertTrue(plans > 100, "the matrix ran $plans plans")
    }
}
