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
import com.pathways.rules.TraitClauses
import com.pathways.rules.Wounds
import com.pathways.rules.DarknessCause
import com.pathways.rules.TraitEffects
import com.pathways.rules.Ranged
import com.pathways.rules.Weapons
import com.pathways.rules.content.RangePenalty
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
        val clauses = TraitClauses.attackClauses(killer)
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

    /**
     * The same sum over the terms the matrix above leaves out (6gd's open
     * item): the range band, every circumstance row, the darkness row at each
     * severity and relief, the prone row in reach, and the trait terms. Each
     * alone, then together, because a term that supersedes another only
     * shows beside the one it removes. Together is two plans: a Reluctant
     * Killer "cannot Aim", so the screen never draws the clause beside an
     * Aim row. Each has one speed row, since a target has one speed.
     *
     * 38 alone: 6 bands (the +0 band prints nothing), 8 rows (stationary
     * prints nothing), 21 of darkness (7 without Night Eyes; with it 7 fog
     * and 7 darkness, and dim light leaves nothing), 2 clauses and the prone
     * row in reach.
     */
    @Test
    fun `range, circumstances, darkness and the traits add up too`() {
        val level = 16
        val guns = SkillKey("Guns", "Pistol", ControllingAttribute.Buyable(Attr.DX), Difficulty.EASY)
        val killer = listOf(Trait(TraitEffects.RELUCTANT_KILLER, 5, TraitKind.DISADVANTAGE))
        val declared = TraitClauses.attackClauses(killer).map { it.key }
        val darkness = Ranged.conditions().single { it.worstCase != null }
        val rows = Ranged.conditions().map { it.condition }
        fun shot(
            band: RangePenalty? = null,
            picked: Set<String> = emptySet(),
            cause: DarknessCause = DarknessCause.HEAVY_FOG,
            reduction: Int = 0,
            severity: Int? = null,
            aimRefused: Boolean = false,
        ) = rangedModifiers(AttackPlan(isRanged = true), band, picked, 2, aimRefused, cause, reduction, severity)

        val alone = Ranged.bands().map { shot(band = it) } +
            rows.map { shot(picked = setOf(it)) } +
            (darkness.worstCase!!..darkness.modifier!!).flatMap { severity ->
                // The cause is asked only of a holder of Night Eyes, which
                // takes 2 off; without the trait it stays DARKNESS.
                (DarknessCause.entries.map { it to 2 } + (DarknessCause.DARKNESS to 0)).map { (cause, reduction) ->
                    shot(picked = setOf(darkness.condition), cause = cause, reduction = reduction, severity = severity)
                }
            } +
            declared.map { traitTerms(killer, guns, it) } +
            listOf(meleeModifiers(AttackPlan(), Ranged.meleeConditions().map { it.condition }.toSet()))
        fun together(picked: Set<String>, aimRefused: Boolean) = shot(
            band = Ranged.bands().last(),
            picked = picked,
            cause = DarknessCause.DARKNESS,
            reduction = 2,
            severity = darkness.worstCase,
            aimRefused = aimRefused,
        ) + listOfNotNull(Wounds.modifier(Wounds.locations().last()))
        // One speed and one aim: Ranged.modifiers refuses two of either.
        val running = rows.toSet() - Ranged.alternativesTo("Target running (4+ meters/sec)")
        val aimed = together(running - AIM_ROWS.first(), aimRefused = false)
        val refused = together(running - AIM_ROWS, aimRefused = true) +
            traitTerms(killer, guns, declared.first())
        assertEquals(ALONE_TERMS, alone.count { it.isNotEmpty() }, "plans that print a term")
        // The band, six rows (running, shooter moved, the long aim, prone,
        // darkness, cover) and the Eye: 1 + 6 + 1. Then 1 + 5 + 1 with no
        // aim, and the clause.
        assertEquals(TOGETHER_TERMS, aimed.size, "an aimed shot's terms")
        assertEquals(TOGETHER_TERMS, refused.size, "a Reluctant Killer's terms")

        val reeling = Conditions.forSkill(guns, Pools.hp(4, 14), Pools.fp(11, 11)) +
            Shock.modifiers(shocked = true, traits = emptyList())
        for (picked in alone + listOf(aimed, refused)) {
            // -5 written out, as above: reeling -1 and shock -4.
            for ((trackers, cost) in listOf(emptyList<com.pathways.model.Modifier>() to 0, reeling to -5)) {
                val standing = attackStanding("Guns (Pistol)", level, guns, trackers, picked)
                val plan = picked.joinToString { it.id.toString() }
                assertEquals(cost, standing.fromTrackers, plan)
                assertEquals(level + flats(picked).sum() + cost, standing.target.target, plan)
            }
        }
    }

    /**
     * The chip handler, as a function: a second speed or a second aim takes
     * the first one's place, since `Ranged.modifiers` refuses the pair while
     * the dialog is drawn. Every other row is a fact of its own.
     */
    @Test
    fun `a second speed or a second aim replaces the first`() {
        val walking = "Target walking (up to 3 meters/sec)"
        val running = "Target running (4+ meters/sec)"
        val moved = "Shooter moved this turn"
        val (brief, long) = AIM_ROWS.toList()
        assertEquals(setOf(running), toggled(setOf(walking), running))
        assertEquals(setOf(moved, "Target is stationary"), toggled(setOf(moved, running), "Target is stationary"))
        assertEquals(setOf(long, running), toggled(setOf(brief, running), long))
        assertEquals(setOf(running, moved), toggled(setOf(running), moved), "an independent row adds")
        assertEquals(emptySet<String>(), toggled(setOf(running), running), "a second tap takes it off")
        // Whatever is tapped, in whatever order, the set stays one the roll accepts.
        val names = Ranged.conditions().map { it.condition }
        var picked = emptySet<String>()
        for (name in names + names.reversed().drop(1)) {
            picked = toggled(picked, name)
            rangedModifiers(AttackPlan(isRanged = true), null, picked, 2, false, DarknessCause.DARKNESS, 0, null)
        }
    }

    /**
     * "×1.5 there" under a row of unlit location chips pointed at nothing:
     * with no shot called the screen names no place. The multiplier is the
     * damage type's own and is still printed.
     */
    @Test
    fun `the damage line says there only where a shot was called`() {
        assertEquals(", ×1.5", woundingNote(null, "pi+"))
        assertEquals("", woundingNote(null, "cr"))
        val skull = Wounds.locationNamed("Skull")
        assertEquals(", ×4 there", woundingNote(skull, "cr"))
        // A called Torso is a location like any other.
        assertEquals(", ×1.5 there", woundingNote(Wounds.locationNamed("Torso"), "pi+"))
    }

    private companion object {
        const val ALONE_TERMS = 38
        const val TOGETHER_TERMS = 8
    }
}
