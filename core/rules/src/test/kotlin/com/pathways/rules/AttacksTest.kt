package com.pathways.rules

import com.pathways.model.Attr
import com.pathways.model.ControllingAttribute
import com.pathways.model.Difficulty
import com.pathways.model.ModEffect
import com.pathways.model.SkillKey
import com.pathways.model.SourceKind
import com.pathways.rules.content.ContentLoader
import com.pathways.rules.content.UnarmedAttack
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * The maneuver and attack options a player chooses, as modifiers.
 *
 * The book prints these numbers inside prose rather than in a column, so
 * [Attacks] is the only copy of them and this is what pins it to the book.
 */
class AttacksTest {

    private val brawling = SkillKey("Brawling", null, ControllingAttribute.Buyable(Attr.DX), Difficulty.EASY)

    private fun flats(plan: AttackPlan, skill: Int = 20, unarmed: UnarmedAttack? = null) =
        Attacks.modifiers(plan, skill, unarmed).map { (it.effect as ModEffect.Flat).n }

    @Test
    fun `a plain attack carries no modifier at all`() {
        assertTrue(Attacks.modifiers(AttackPlan(), skillLevel = 12).isEmpty())
    }

    @Test
    fun `all-out determined is worth more in reach than at range`() {
        // p. 36: "Pay 2 FP. +4 to hit melee, +1 to hit ranged."
        assertEquals(listOf(4), flats(AttackPlan(AttackManeuver.DETERMINED)))
        assertEquals(listOf(1), flats(AttackPlan(AttackManeuver.DETERMINED, isRanged = true)))
    }

    @Test
    fun `all-out strong and double move damage and count, not the roll`() {
        // "+2 to damage" and "roll twice" are not to-hit modifiers, and
        // reading them as such would hand a fighter four free points.
        assertTrue(Attacks.modifiers(AttackPlan(AttackManeuver.STRONG), 12).isEmpty())
        assertTrue(Attacks.modifiers(AttackPlan(AttackManeuver.DOUBLE), 12).isEmpty())
    }

    @Test
    fun `strong's plus two reaches damage even though it misses the roll`() {
        // The first attack screen applied only Boxing's bonus and dropped
        // this one, so Strong cost 2 FP and did nothing at all (Codex).
        assertEquals(2, Attacks.damageBonus(AttackPlan(AttackManeuver.STRONG)))
        assertEquals(0, Attacks.damageBonus(AttackPlan(AttackManeuver.DETERMINED)))
        assertEquals(0, Attacks.damageBonus(AttackPlan()))
    }

    @Test
    fun `the maneuver's damage bonus adds to the skill's, never replaces it`() {
        // Boxing at DX+2 punching with All-Out (Strong): +2 and +2.
        assertEquals(4, Attacks.damageBonus(AttackPlan(AttackManeuver.STRONG), fromSkill = 2))
        assertEquals(2, Attacks.damageBonus(AttackPlan(), fromSkill = 2))
        assertThrows<IllegalArgumentException> {
            Attacks.damageBonus(AttackPlan(), fromSkill = -1)
        }
    }

    /**
     * **"Regular melee attacks only" names the Regular Attack MANEUVER.**
     *
     * The rejected reading is that "regular" means "ordinary", scoping the
     * option to melee and nothing more. Three things settle it:
     *
     *  - "Regular Attack" is a printed maneuver row (line 2411), two lines
     *    above this bullet list.
     *  - The sibling bullet keys to a row by its printed name the same way --
     *    Heroic Charge is "Ignore **Charge Attack** penalties". This list
     *    references maneuvers, which is per-table evidence rather than a
     *    resemblance.
     *  - Read loosely the word does no work: the sentence already says
     *    "melee ... only".
     *
     * "Regular" is printed as an adjective for an attack exactly once in the
     * whole book, which is what makes the maneuver row the only thing it can
     * be pointing at. Under the loose reading All-Out Attack (Strong) is
     * strictly dominated -- 2 FP for the +2 that this option buys for 1 --
     * and the two would stack to +4 for 3 FP.
     *
     * A reading, so it is pinned here rather than left to be rediscovered.
     */
    @Test
    fun `strong blow is refused on every maneuver but the regular attack`() {
        for (maneuver in AttackManeuver.entries - AttackManeuver.REGULAR) {
            assertThrows<IllegalArgumentException>("$maneuver should refuse Strong Blow") {
                Attacks.modifiers(AttackPlan(maneuver, strongBlow = true), 12)
            }
        }
        // The one it is printed for resolves, and adds nothing to the roll.
        assertTrue(Attacks.modifiers(AttackPlan(strongBlow = true), 12).isEmpty())
    }

    @Test
    fun `strong blow is refused at range`() {
        // "melee attacks only" -- a shot is not a melee attack, and paying
        // 1 FP for damage the book does not grant is a bug, not a rounding.
        assertThrows<IllegalArgumentException> {
            Attacks.modifiers(AttackPlan(isRanged = true, strongBlow = true), 12)
        }
    }

    @Test
    fun `going to range drops the strong blow with the melee options`() {
        // Same reason Rapid Strike and Deceptive are dropped: modifiers()
        // REFUSES the pair, so a screen that changed only isRanged would hand
        // the next composition an exception instead of an attack (6as).
        val melee = AttackPlan(strongBlow = true, rapidStrikes = 1)
        val shot = Attacks.atRange(melee, isRanged = true)
        assertTrue(!shot.strongBlow)
        Attacks.modifiers(shot, skillLevel = 12)
    }

    /**
     * **A +2 to damage needs damage, and one printed attack has none.**
     *
     * Shove's damage cell reads "Special" -- it knocks the target down and
     * rolls nothing, which is why `Damage.forAttack` answers null for it
     * (6j). Offering Strong Blow there charges 1 FP for a bonus with nothing
     * to land on (Codex). Refused in rules rather than only hidden by the
     * screen, so a caller that forgets to ask still cannot charge for it --
     * the call `EffortBurn.canPush` already needed (6ak).
     */
    @Test
    fun `strong blow is refused on an attack that rolls no damage`() {
        val shove = Damage.attacksFor("Brawling").single { it.attack == "Shove" }
        assertThrows<IllegalArgumentException> {
            Attacks.modifiers(AttackPlan(strongBlow = true), 12, shove)
        }
        val punch = Damage.attacksFor("Brawling").single { it.attack == "Punch" }
        assertTrue(Attacks.modifiers(AttackPlan(strongBlow = true), 12, punch).isEmpty())
    }

    @Test
    fun `moving onto a shove drops the strong blow and moving off it does not`() {
        val shove = Damage.attacksFor("Brawling").single { it.attack == "Shove" }
        val punch = Damage.attacksFor("Brawling").single { it.attack == "Punch" }
        val plan = AttackPlan(strongBlow = true)
        assertTrue(!Attacks.withAttack(plan, shove).strongBlow)
        assertTrue(Attacks.withAttack(plan, punch).strongBlow)
        // A weapon is held, or no attack is chosen: every catalogued weapon
        // rolls damage, so there is nothing to drop.
        assertTrue(Attacks.withAttack(plan, null).strongBlow)
        // The composition that used to throw now resolves.
        Attacks.modifiers(Attacks.withAttack(plan, shove), 12, shove)
    }

    /**
     * **A firearm held in reach is still a firearm.**
     *
     * `isRanged` is where the fighter is standing, not what they are holding:
     * a Guns skill opens at range and the toggle can put it back in reach,
     * where the rifle still rolls damage and `modifiers` -- which never sees
     * the weapon -- would accept the melee-only +2 (Codex). So the offer is a
     * rules predicate rather than a screen's condition.
     */
    @Test
    fun `strong blow is not offered for a firearm however the fighter stands`() {
        val melee = AttackPlan()
        assertTrue(Attacks.offersStrongBlow(melee, dealsDamage = true, isFired = false))
        assertTrue(!Attacks.offersStrongBlow(melee, dealsDamage = true, isFired = true))
        // Shove, and any skill with no attack and no catalogued weapon.
        assertTrue(!Attacks.offersStrongBlow(melee, dealsDamage = false, isFired = false))
        // The two scopes modifiers() can see, so the predicate agrees with it.
        assertTrue(
            !Attacks.offersStrongBlow(
                AttackPlan(isRanged = true),
                dealsDamage = true,
                isFired = false,
            ),
        )
        for (maneuver in AttackManeuver.entries - AttackManeuver.REGULAR) {
            assertTrue(
                !Attacks.offersStrongBlow(AttackPlan(maneuver), dealsDamage = true, isFired = false),
                "$maneuver should not offer Strong Blow",
            )
        }
    }

    @Test
    fun `strong blow adds its two to damage and never to the roll`() {
        // "+2 damage" is the same shape as All-Out (Strong)'s, from a
        // different place -- and damageBonus exists so a caller reading one
        // cannot miss the other (6j).
        assertEquals(2, Attacks.damageBonus(AttackPlan(strongBlow = true)))
        assertEquals(4, Attacks.damageBonus(AttackPlan(strongBlow = true), fromSkill = 2))
    }

    @Test
    fun `strong blow costs the FP the content prints`() {
        assertEquals(1, Attacks.fpCost(AttackPlan(strongBlow = true)))
    }

    @Test
    fun `Strong Blow still names an extra effort option`() {
        assertTrue(ContentLoader.extraEffort().any { it.option == Attacks.STRONG_BLOW })
    }

    @Test
    fun `a charge costs four and heroic charge buys it back`() {
        assertEquals(listOf(-4), flats(AttackPlan(AttackManeuver.CHARGE)))
        val heroic = AttackPlan(AttackManeuver.CHARGE, heroicCharge = true)
        assertTrue(Attacks.modifiers(heroic, 12).isEmpty())
    }

    @Test
    fun `heroic charge outside a charge is refused rather than wasted`() {
        // It costs 2 FP and the book scopes it to "Charge Attack penalties".
        assertThrows<IllegalArgumentException> {
            Attacks.modifiers(AttackPlan(AttackManeuver.REGULAR, heroicCharge = true), 12)
        }
    }

    @Test
    fun `each rapid strike stacks another six`() {
        // "Both attacks suffer -6 to skill. Additional rapid strikes in the
        // same maneuver stack the penalty."
        assertEquals(listOf(-6), flats(AttackPlan(rapidStrikes = 1)))
        assertEquals(listOf(-12), flats(AttackPlan(rapidStrikes = 2)))
    }

    @Test
    fun `deceptive attack costs the attacker two per one imposed`() {
        // The -1 lands on the DEFENDER; what reaches this roll is the -2.
        assertEquals(listOf(-2), flats(AttackPlan(deceptive = 1)))
        assertEquals(listOf(-6), flats(AttackPlan(deceptive = 3)))
    }

    @Test
    fun `the deceptive cap is half the skill, floored, spent two at a time`() {
        // "Maximum penalty is half your skill (rounded down)": at 11 the
        // ceiling is 5, so a third step (-6) does not fit.
        assertEquals(2, Attacks.maxDeceptive(11))
        assertEquals(3, Attacks.maxDeceptive(12))
        assertEquals(3, Attacks.maxDeceptive(13))
        assertEquals(0, Attacks.maxDeceptive(3))
        assertEquals(0, Attacks.maxDeceptive(0))
    }

    @Test
    fun `a step past the cap is refused, not silently trimmed`() {
        assertThrows<IllegalArgumentException> { Attacks.modifiers(AttackPlan(deceptive = 3), 11) }
        assertThrows<IllegalArgumentException> { Attacks.modifiers(AttackPlan(deceptive = -1), 11) }
        assertThrows<IllegalArgumentException> { Attacks.modifiers(AttackPlan(rapidStrikes = -1), 11) }
    }

    @Test
    fun `the cap reads the sheet's skill, not the level after this turn`() {
        // All-Out Attack (Determined) is +4 to hit. If the cap read the
        // modified level, swinging harder would also let you feint deeper --
        // and Deceptive's own penalty would move the cap limiting it.
        val determined = AttackPlan(AttackManeuver.DETERMINED, deceptive = 3)
        assertThrows<IllegalArgumentException> { Attacks.modifiers(determined, 11) }
    }

    @Test
    fun `both melee-only options are refused at range`() {
        assertThrows<IllegalArgumentException> {
            Attacks.modifiers(AttackPlan(isRanged = true, rapidStrikes = 1), 12)
        }
        assertThrows<IllegalArgumentException> {
            Attacks.modifiers(AttackPlan(isRanged = true, deceptive = 1), 12)
        }
    }

    @Test
    fun `options and maneuver land as separate modifiers`() {
        val plan = AttackPlan(AttackManeuver.CHARGE, rapidStrikes = 1, deceptive = 2)
        assertEquals(listOf(-4, -6, -4), flats(plan))
    }

    @Test
    fun `a chosen option is situational, never a state threshold`() {
        // Conditions emits STATE. Sharing a kind would let a future "ignore
        // fatigue" effect buy off a Charge Attack as well.
        val mods = Attacks.modifiers(AttackPlan(AttackManeuver.CHARGE), 12)
        assertEquals(listOf(SourceKind.SITUATIONAL), mods.map { it.source.kind })
        val fatigue = Conditions.forSkill(brawling, Pools.hp(10, 10), Pools.fp(-2, 12))
        assertNotEquals(fatigue.single().source.kind, mods.single().source.kind)
    }

    @Test
    fun `FP is charged from the content, not from a second copy of it`() {
        assertEquals(0, Attacks.fpCost(AttackPlan()))
        assertEquals(2, Attacks.fpCost(AttackPlan(AttackManeuver.DETERMINED)))
        assertEquals(0, Attacks.fpCost(AttackPlan(AttackManeuver.CHARGE)))
        // Charge itself is free; the 2 FP is Heroic Charge's own price.
        assertEquals(2, Attacks.fpCost(AttackPlan(AttackManeuver.CHARGE, heroicCharge = true)))
    }

    @Test
    fun `the options that make several attacks are rolled several times`() {
        // "Two separate attacks (roll twice)" and "Split a melee Attack
        // maneuver into two separate attacks" both make more than one attack.
        // Resolving one roll for them silently drops the rest (Codex).
        assertEquals(1, Attacks.attackCount(AttackPlan()))
        assertEquals(2, Attacks.attackCount(AttackPlan(AttackManeuver.DOUBLE)))
        assertEquals(2, Attacks.attackCount(AttackPlan(rapidStrikes = 1)))
        assertEquals(3, Attacks.attackCount(AttackPlan(rapidStrikes = 2)))
        // A split divides each of the Double's attacks: the documented
        // reading, since the book composes the two nowhere.
        assertEquals(4, Attacks.attackCount(AttackPlan(AttackManeuver.DOUBLE, rapidStrikes = 1)))
    }

    @Test
    fun `the FP price is the maneuver's, not each attack's`() {
        // All-Out Attack (Double) prints "Pay 2 FP" once for the maneuver
        // that makes both attacks; Rapid Strike costs nothing at all.
        val double = AttackPlan(AttackManeuver.DOUBLE, rapidStrikes = 1)
        assertEquals(4, Attacks.attackCount(double))
        assertEquals(2, Attacks.fpCost(double))
    }

    @Test
    fun `a listed attack brings its own to-hit penalty`() {
        // The book rolls a Kick at "Brawling-2" and a Headbutt at
        // "Brawling-3". Folding that into the level instead would hide from
        // the player why the number moved.
        val kick = ContentLoader.unarmedAttacks().first { it.attack == "Kick" }
        assertEquals(listOf(-2), flats(AttackPlan(), unarmed = kick))
        val punch = ContentLoader.unarmedAttacks().first { it.attack == "Punch" }
        assertTrue(Attacks.modifiers(AttackPlan(), 12, punch).isEmpty())
    }

    @Test
    fun `a listed attack stacks with the maneuver, it does not replace it`() {
        val headbutt = ContentLoader.unarmedAttacks().first { it.attack == "Headbutt" }
        val plan = AttackPlan(AttackManeuver.DETERMINED)
        assertEquals(listOf(-3, 4), flats(plan, unarmed = headbutt))
    }

    @Test
    fun `every maneuver this enum implements still names a row in the book`() {
        val printed = ContentLoader.maneuvers().map { it.maneuver }.toSet()
        val missing = AttackManeuver.entries.map { it.printed }.filterNot { it in printed }
        assertTrue(missing.isEmpty(), "maneuvers.json no longer prints: $missing")
    }

    @Test
    fun `arming a firearm drops the melee-only options rather than throwing`() {
        // The taps that took the dialog down: toggle a gun attack into reach,
        // set a Rapid Strike and two Deceptive steps, then pick up a different
        // firearm. The swap copied the plan and changed only isRanged, so the
        // very next composition called modifiers() on a ranged plan carrying
        // both -- and modifiers refuses that with a require, correctly.
        val inReach = AttackPlan(isRanged = false, rapidStrikes = 1, deceptive = 2)
        val armed = Attacks.atRange(inReach, isRanged = true)
        assertEquals(0, armed.rapidStrikes)
        assertEquals(0, armed.deceptive)
        assertTrue(armed.isRanged)
        // The proof is that the composition which used to crash now resolves.
        Attacks.modifiers(armed, skillLevel = 12)
    }

    @Test
    fun `going back into reach keeps the maneuver and its charge`() {
        // Heroic Charge is scoped to its MANEUVER, not to reach, and the
        // maneuver is not what changed -- so dropping it would refund nothing
        // and silently undo a choice that costs 2 FP.
        val charging = AttackPlan(
            maneuver = AttackManeuver.CHARGE,
            isRanged = false,
            heroicCharge = true,
        )
        val back = Attacks.atRange(charging, isRanged = false)
        assertEquals(AttackManeuver.CHARGE, back.maneuver)
        assertTrue(back.heroicCharge)
    }

    @Test
    fun `Heroic Charge still names an extra effort option`() {
        assertTrue(ContentLoader.extraEffort().any { it.option == Attacks.HEROIC_CHARGE })
    }
}
