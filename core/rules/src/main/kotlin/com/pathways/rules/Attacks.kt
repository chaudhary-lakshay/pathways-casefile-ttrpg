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
 *
 * [strongBlow] and [heroicCharge] are the two extra-effort options that reach
 * an attack. Both are scoped to a MANEUVER by their own printed cells, and
 * [Attacks.modifiers] refuses a plan carrying one out of scope rather than
 * dropping it silently -- 1 FP bought for nothing is a bug, not a rounding.
 *
 * [giantStep] is the third, and the one that reaches no number at all: its
 * cell buys "an extra step before or after attack" for 1 FP, and this app
 * holds no map for a step to be taken on. It is here because **the price is
 * the part this app does model** -- it charges every other priced row the two
 * FP tables print, and an option a GM cannot pay for is a tracker drifting
 * from the table it claims to implement.
 */
data class AttackPlan(
    val maneuver: AttackManeuver = AttackManeuver.REGULAR,
    val isRanged: Boolean = false,
    val rapidStrikes: Int = 0,
    val deceptive: Int = 0,
    val heroicCharge: Boolean = false,
    val strongBlow: Boolean = false,
    val giantStep: Boolean = false,
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

    /** The extra-effort option that adds to damage: "+2 damage." */
    const val STRONG_BLOW = "Strong Blow"

    /**
     * The extra-effort option that costs 1 FP and moves no number.
     *
     * "Take an extra step before or after attack." There is no map here for
     * the step, so nothing joins a roll -- but the FP is real, and this object
     * already charges the other two options in the same bullet list. Named as
     * a constant rather than left out, because the alternative is the state it
     * was found in: a priced row with no code, no test and no recorded reason,
     * which reads identically to an oversight.
     *
     * ponytail: no `require` guards it, because its cell prints no scope --
     * neither a maneuver nor a reach, where both neighbours print one.
     */
    const val GIANT_STEP = "Giant Step"

    /**
     * What [STRONG_BLOW] adds to damage.
     *
     * A number inside a prose cell, so it lives here and the extractor pins
     * the sentence it was read from -- the split the maneuver table's
     * +4/-4/-6/-2 already take. Reading it out of the effect string at render
     * time would be a parser nobody tests (6f).
     */
    const val STRONG_BLOW_DAMAGE = 2

    /**
     * [plan] moved between reach and range, dropping what cannot come along.
     *
     * Rapid Strike and Deceptive Attack both say "melee", and [modifiers]
     * REFUSES a ranged plan carrying either -- correctly, because silently
     * applying them would invent a penalty the book does not give. That
     * refusal is a `require`, so a screen that changed only `isRanged` handed
     * the next composition an exception instead of an attack: picking up a
     * second firearm after setting a Rapid Strike in reach took the dialog
     * down before Roll could be tapped.
     *
     * Strong Blow travels with them, for the same reason and by a different
     * clause: its cell says "Regular MELEE attacks only", so reach is exactly
     * what scopes it and moving to range is what puts it out of scope.
     *
     * Heroic Charge is kept, because it is scoped to its MANEUVER rather than
     * to reach, and the maneuver is not what changed here.
     *
     * Lives beside the two requires it exists to satisfy. A caller doing its
     * own reset is a second copy of the rule -- and there were already two,
     * disagreeing: the range toggle rebuilt the plan from scratch while the
     * weapon swap copied it.
     */
    fun atRange(plan: AttackPlan, isRanged: Boolean): AttackPlan =
        if (!isRanged) plan.copy(isRanged = false)
        else plan.copy(isRanged = true, rapidStrikes = 0, deceptive = 0, strongBlow = false)

    /**
     * [plan] moved to a different skill [level], dropping what cannot come
     * along.
     *
     * A fourth transition, new with 6eu: a level was fixed once a skill was
     * picked, and now a Warrior's untrained knife is wielded at DX and the
     * same skill with the knife put down is back at its default. A Deceptive
     * Attack bought under the higher ceiling would throw in [modifiers].
     * Dropped to zero rather than trimmed, because a trimmed plan is a
     * number the player did not pick -- the refusal [modifiers] already makes.
     */
    fun atLevel(plan: AttackPlan, level: Int): AttackPlan =
        if (plan.deceptive > maxDeceptive(level)) plan.copy(deceptive = 0) else plan

    /**
     * [plan] moved onto a different unarmed attack, dropping what cannot come
     * along.
     *
     * Only Strong Blow can be stranded here: its "+2 damage" needs a damage
     * roll to land on, and Shove has none -- its cell reads "Special", it
     * knocks the target down, and [Damage.forAttack] answers null for it (6j).
     * Ticking the option and then tapping Shove would spend 1 FP on nothing,
     * which is the same wasted spend [modifiers] refuses at range.
     *
     * A transition function rather than a reset in the screen, for the reason
     * [atRange] exists: a caller doing its own is a second copy of the rule,
     * and this codebase's numbers stopped reconciling three times that way
     * (6ag, 6ai, 6be).
     *
     * [unarmed] is null when a weapon is held or none is chosen, and every
     * weapon in the book rolls damage, so nothing is dropped there.
     */
    fun withAttack(plan: AttackPlan, unarmed: UnarmedAttack?): AttackPlan =
        if (unarmed == null || unarmed.wounds) plan else plan.copy(strongBlow = false)

    /**
     * [plan] moved onto a different maneuver, dropping what cannot come along.
     *
     * The third transition, and the one that was NOT here: the screen rebuilt
     * the plan from scratch inline, which is the second-copy failure [atRange]
     * and [withAttack] both exist to prevent (6ag, 6ai, 6be). It went unnoticed
     * while every option it dropped deserved dropping -- Heroic Charge and
     * Strong Blow each name a maneuver in their own cell, and carrying one
     * across would throw on Roll rather than merely mislead.
     *
     * [AttackPlan.giantStep] is what exposed it. Its cell names no maneuver, so
     * a maneuver change is not a reason to drop it, and a wholesale rebuild
     * dropped it anyway -- a player comparing to-hit numbers across maneuvers
     * lost a paid option with nothing on screen to say so.
     *
     * The rapid strikes and deceptive steps are reset with the scoped options,
     * which is what the screen already did. That is stricter than the book:
     * both are scoped to REACH rather than to a maneuver, and only [atRange]
     * enforces that. Kept as it was, because loosening it is a different
     * question from centralising it, and one slice should not answer both.
     */
    fun withManeuver(plan: AttackPlan, maneuver: AttackManeuver): AttackPlan =
        AttackPlan(
            maneuver = maneuver,
            isRanged = plan.isRanged,
            giantStep = plan.giantStep,
        )

    /**
     * Whether [STRONG_BLOW] may be offered at all.
     *
     * Its cell scopes it three ways and [modifiers] can only see two of them:
     * the maneuver and the plan's reach are on the plan, but whether the
     * attack rolls damage and whether the weapon is a FIREARM are facts about
     * what is held. [plan.isRanged] is positioning, not a weapon -- a rifle
     * toggled "in reach" is still a gun, and "melee attacks only" excludes it
     * however the fighter is standing (Codex).
     *
     * A predicate here rather than a condition in the screen, so the offer and
     * the refusal cannot drift apart: a screen computing its own is the
     * two-copies failure this codebase produced three times (6ag, 6ai, 6be).
     */
    fun offersStrongBlow(plan: AttackPlan, dealsDamage: Boolean, isFired: Boolean): Boolean =
        plan.maneuver == AttackManeuver.REGULAR && !plan.isRanged && dealsDamage && !isFired

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
     *
     * There are three of them now: the extra-effort option Strong Blow adds
     * its own +2 for 1 FP. It is scoped to the Regular Attack maneuver, so it
     * never composes with All-Out Attack (Strong)'s -- [modifiers] refuses
     * that pair, and this stays arithmetic rather than a second guard.
     */
    fun damageBonus(plan: AttackPlan, fromSkill: Int = 0): Int {
        require(fromSkill >= 0) { "a skill damage bonus cannot be negative: $fromSkill" }
        val effort = if (plan.strongBlow) STRONG_BLOW_DAMAGE else 0
        return plan.maneuver.damageBonus + effort + fromSkill
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
     * the extra-effort options', all read from the content rather than
     * repeated here.
     *
     * Charged once per maneuver, not per attack in [attackCount] -- the book
     * prices All-Out Attack (Double) at "Pay 2 FP" for the maneuver that makes
     * both attacks, and Rapid Strike costs no FP at all. Strong Blow follows
     * that reading: a Regular Attack split by a Rapid Strike is still one
     * maneuver, so its +2 rides the damage expression both attacks share and
     * is paid for once. The book composes the two nowhere.
     */
    fun fpCost(plan: AttackPlan): Int {
        val maneuver = ContentLoader.maneuvers().firstOrNull { it.maneuver == plan.maneuver.printed }
            ?: error("maneuvers.json has no row named '${plan.maneuver.printed}'")
        val charge = if (plan.heroicCharge) effortFp(HEROIC_CHARGE) else 0
        val blow = if (plan.strongBlow) effortFp(STRONG_BLOW) else 0
        val step = if (plan.giantStep) effortFp(GIANT_STEP) else 0
        return (maneuver.fp ?: 0) + charge + blow + step
    }

    /**
     * What [option] costs in FP, read from the row that prints it.
     *
     * Public because the screen that OFFERS an option states its price in the
     * chip's own label. A number typed there is a second copy of this one, and
     * the copy nothing pins is the copy that drifts (6cg).
     */
    fun effortFp(option: String): Int =
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
        // ^ [atRange] is the supported way to move a plan past these two.
        // "Ignore Charge Attack penalties" buys off nothing on any other
        // maneuver, and it costs 2 FP; spending them for no effect is a bug.
        require(!plan.heroicCharge || plan.maneuver == AttackManeuver.CHARGE) {
            "$HEROIC_CHARGE only applies to ${AttackManeuver.CHARGE.printed}"
        }
        // "Regular melee attacks only" -- both halves of that cell, and the
        // first names the maneuver row printed two lines above the bullet.
        // See AttacksTest for the reading and the one it rejects. Refused
        // rather than ignored: 1 FP is spent either way.
        require(!plan.strongBlow || plan.maneuver == AttackManeuver.REGULAR) {
            "$STRONG_BLOW only applies to ${AttackManeuver.REGULAR.printed}"
        }
        require(!plan.strongBlow || !plan.isRanged) { "$STRONG_BLOW is a melee option" }
        // "+2 damage" needs damage to add to. Shove's cell reads "Special":
        // it knocks the target down and rolls nothing (6j), so the option
        // would cost 1 FP and buy nothing. [withAttack] is the supported way
        // to move a plan onto an attack that does not wound.
        require(!plan.strongBlow || unarmed == null || unarmed.wounds) {
            "$STRONG_BLOW needs an attack that rolls damage, and ${unarmed?.attack} does not"
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
