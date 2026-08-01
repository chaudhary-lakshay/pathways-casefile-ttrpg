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
    fun `Heroic Charge still names an extra effort option`() {
        assertTrue(ContentLoader.extraEffort().any { it.option == Attacks.HEROIC_CHARGE })
    }
}
