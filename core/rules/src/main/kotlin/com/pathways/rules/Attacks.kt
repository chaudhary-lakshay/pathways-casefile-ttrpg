package com.pathways.rules

import com.pathways.model.Layer
import com.pathways.model.ModEffect
import com.pathways.model.ModTarget
import com.pathways.model.Modifier
import com.pathways.model.ModifierId
import com.pathways.model.ModifierSource
import com.pathways.rules.content.ContentLoader
import com.pathways.rules.content.UnarmedAttack

/**
 * The maneuvers whose choice moves the attack roll (Ch. 5, p. 36).
 *
 * Only five of the book's fourteen do. The other nine change what you may do
 * with the turn -- Move, Ready, Wait, Feint -- without changing the number you
 * roll against, so they are content the app displays, not cases here.
 *
 * [printed] is the book's own row label, and [AttacksTest] checks every one of
 * them still names a row in maneuvers.json. That is what stops this enum
 * drifting away from the table it claims to implement.
 */
enum class AttackManeuver(
    val printed: String,
    private val melee: Int,
    private val ranged: Int,
    /** What this maneuver adds to damage, which most of them do not touch. */
    val damageBonus: Int = 0,
) {
    /** "Make one melee or ranged attack." No modifier of its own. */
    REGULAR("Regular Attack", 0, 0),

    /** "Pay 2 FP. +4 to hit melee, +1 to hit ranged." */
    DETERMINED("All-Out Attack (Determined)", 4, 1),

    /** "Pay 2 FP. +2 to damage" -- damage only; the roll is unchanged. */
    STRONG("All-Out Attack (Strong)", 0, 0, damageBonus = 2),

    /** "Two separate attacks (roll twice)" at no penalty; each is a plain roll. */
    DOUBLE("All-Out Attack (Double)", 0, 0),

    /** "Move full distance, then attack. -4 to hit." */
    CHARGE("Charge Attack", -4, -4),
    ;

    /** What this maneuver does to the attack roll, melee or at range. */
    fun toHit(isRanged: Boolean): Int = if (isRanged) ranged else melee
}

/**
 * One turn's attack, as chosen at the table.
 *
 * [rapidStrikes] is how many EXTRA attacks were split out, not how many
 * attacks result: the book's "Additional rapid strikes in the same maneuver
 * stack the penalty" means one split gives two attacks at -6, two splits give
 * three at -12. [deceptive] is the penalty imposed on the defender, in -1
 * steps, which costs -2 each.
 */
data class AttackPlan(
    val maneuver: AttackManeuver = AttackManeuver.REGULAR,
    val isRanged: Boolean = false,
    val rapidStrikes: Int = 0,
    val deceptive: Int = 0,
    val heroicCharge: Boolean = false,
)

/**
 * What a chosen maneuver and its attack options do to the attack roll.
 *
 * The numbers live here rather than in maneuvers.json for the reason the
 * defenses slice settled: a formula the book prints in prose is implemented
 * once, in code, tested against the book. The extractor pins the sentences
 * they are read from, so the book changing one fails loudly on the Python
 * side instead of leaving these constants quietly wrong.
 *
 * Everything produced is [Layer.SITUATIONAL] and [ModifierSource.Situational],
 * deliberately NOT the [Layer.STATE_THRESHOLD] that [Conditions] emits: a
 * future "ignore fatigue penalties" effect must not also buy off a Charge
 * Attack. The two lists add together at the call site and the resolver keeps
 * them apart by kind.
 */
object Attacks {

    /** "Both attacks suffer -6 to skill", per split. */
    const val RAPID_STRIKE_PENALTY = -6

    /** "-2 penalty to your melee attack roll for every -1 you wish to impose". */
    const val DECEPTIVE_STEP_PENALTY = -2

    /** The extra-effort option that buys off [AttackManeuver.CHARGE]'s -4. */
    const val HEROIC_CHARGE = "Heroic Charge"

    /**
     * The most -1 steps of defence penalty [skillLevel] may buy.
     *
     * Two of the book's rules compose here: the price is -2 per step, and
     * "Maximum penalty is half your skill (rounded down)". Written as the two
     * halvings it is rather than the single floorDiv(skill, 4) they collapse
     * to, because the two rules can change independently.
     *
     * The cap reads the character's own skill level, NOT the level after this
     * turn's modifiers. Anything else is circular: Deceptive Attack's own
     * penalty would move the cap that limits it, and All-Out Attack would let
     * a fighter be more deceptive by swinging harder.
     */
    fun maxDeceptive(skillLevel: Int): Int =
        Math.floorDiv(Math.floorDiv(skillLevel.coerceAtLeast(0), 2), -DECEPTIVE_STEP_PENALTY)

    /**
     * What [plan] adds to the damage of a hit, on top of [fromSkill].
     *
     * A function rather than a bare `plan.maneuver.damageBonus` because the
     * bonuses come from unrelated places -- All-Out Attack (Strong) is the
     * maneuver's, Boxing's is the character's skill above DX -- and a caller
     * reading only one of them under-reports the other. That is exactly what
     * the first version of the attack screen did with Strong (Codex).
     */
    fun damageBonus(plan: AttackPlan, fromSkill: Int = 0): Int {
        require(fromSkill >= 0) { "a skill damage bonus cannot be negative: $fromSkill" }
        return plan.maneuver.damageBonus + fromSkill
    }

    /**
     * How many attack rolls [plan] produces.
     *
     * Two of the choices make more than one attack, and rolling once for them
     * loses the rest: All-Out Attack (Double) is "Two separate attacks (roll
     * twice)", and a Rapid Strike "split[s] a melee Attack maneuver into two
     * separate attacks", with further splits stacking.
     *
     * How the two COMPOSE is a documented reading, not the book's word: it
     * says a rapid strike splits "a melee Attack maneuver", and All-Out Double
     * is one maneuver making two attacks, so a split divides each of them --
     * 2 x (splits + 1). The alternative, adding them, would make the second
     * split cheaper than the first for no reason the book gives. The penalty
     * applies to every attack either way ("Both attacks suffer -6 to skill").
     */
    fun attackCount(plan: AttackPlan): Int {
        require(plan.rapidStrikes >= 0) { "rapidStrikes must not be negative: ${plan.rapidStrikes}" }
        val fromManeuver = if (plan.maneuver == AttackManeuver.DOUBLE) 2 else 1
        return fromManeuver * (plan.rapidStrikes + 1)
    }

    /**
     * The FP [plan] costs before it is rolled: the maneuver's own cost plus
     * Heroic Charge's, both read from the content rather than repeated here.
     *
     * Charged once per maneuver, not per attack in [attackCount] -- the book
     * prices All-Out Attack (Double) at "Pay 2 FP" for the maneuver that makes
     * both attacks, and Rapid Strike costs no FP at all.
     */
    fun fpCost(plan: AttackPlan): Int {
        val maneuver = ContentLoader.maneuvers().firstOrNull { it.maneuver == plan.maneuver.printed }
            ?: error("maneuvers.json has no row named '${plan.maneuver.printed}'")
        val charge = if (plan.heroicCharge) effortCost(HEROIC_CHARGE) else 0
        return (maneuver.fp ?: 0) + charge
    }

    private fun effortCost(option: String): Int =
        ContentLoader.extraEffort().firstOrNull { it.option == option }?.fp
            ?: error("extra_effort.json has no option named '$option'")

    /**
     * [plan] as modifiers on an attack rolled at [skillLevel].
     *
     * [skillLevel] is only the cap's input -- the level itself reaches the
     * roll through [SkillRoll], not through here.
     *
     * [unarmed] is the listed attack being made, if any: the book rolls a Kick
     * at "Brawling-2" and a Headbutt at "Brawling-3", and that penalty is a
     * to-hit modifier like every other one here. Keeping it in this list
     * rather than folding it into the level is what lets the screen show the
     * player why the number moved.
     */
    fun modifiers(
        plan: AttackPlan,
        skillLevel: Int,
        unarmed: UnarmedAttack? = null,
    ): List<Modifier> {
        require(plan.rapidStrikes >= 0) { "rapidStrikes must not be negative: ${plan.rapidStrikes}" }
        require(plan.deceptive >= 0) { "deceptive steps must not be negative: ${plan.deceptive}" }
        require(plan.deceptive <= maxDeceptive(skillLevel)) {
            "deceptive ${plan.deceptive} exceeds half of skill $skillLevel " +
                "(max ${maxDeceptive(skillLevel)} steps)"
        }
        // Both options say "melee". Paying for one at range is a mistake worth
        // failing on rather than quietly applying a penalty the book does not
        // give -- the UI offers neither once a ranged attack is chosen.
        require(!plan.isRanged || plan.rapidStrikes == 0) { "Rapid Strike is a melee option" }
        require(!plan.isRanged || plan.deceptive == 0) { "Deceptive Attack is a melee option" }
        // "Ignore Charge Attack penalties" buys off nothing on any other
        // maneuver, and it costs 2 FP; spending them for no effect is a bug.
        require(!plan.heroicCharge || plan.maneuver == AttackManeuver.CHARGE) {
            "$HEROIC_CHARGE only applies to ${AttackManeuver.CHARGE.printed}"
        }

        return buildList {
            if (unarmed != null) {
                situational("unarmed", unarmed.rollModifier, unarmed.attack)?.let(::add)
            }
            // Heroic Charge cancels the charge penalty by not levying it.
            // ponytail: a ModEffect.Cancel or `supersedes` would show both
            // sides in the applied list; do that when a screen wants to print
            // "what did my 2 FP buy".
            val fromManeuver = if (plan.heroicCharge) 0 else plan.maneuver.toHit(plan.isRanged)
            situational("maneuver", fromManeuver, plan.maneuver.printed)?.let(::add)
            situational(
                "rapid",
                RAPID_STRIKE_PENALTY * plan.rapidStrikes,
                "Rapid Strike x${plan.rapidStrikes}",
            )?.let(::add)
            situational(
                "deceptive",
                DECEPTIVE_STEP_PENALTY * plan.deceptive,
                "Deceptive Attack -${plan.deceptive} to their defence",
            )?.let(::add)
        }
    }

    private fun situational(id: String, value: Int, description: String): Modifier? {
        if (value == 0) return null
        return Modifier(
            id = ModifierId("attack:$id"),
            source = ModifierSource.Situational(description),
            // Retargeted onto the skill being rolled by SkillRoll.
            target = ModTarget.AllRolls,
            effect = ModEffect.Flat(value),
            layer = Layer.SITUATIONAL,
        )
    }
}
