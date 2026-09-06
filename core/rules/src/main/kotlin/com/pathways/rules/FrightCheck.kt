package com.pathways.rules

import com.pathways.model.Outcome
import com.pathways.model.Trait
import com.pathways.model.RollResult
import com.pathways.rules.content.ContentLoader
import com.pathways.rules.content.FrightEffect
import com.pathways.rules.content.FrightRecovery
import com.pathways.rules.content.FrightTrigger

/**
 * Which column of the effects table a trigger reads (Ch. 5, p. 43).
 *
 * "All three use the same roll; only the effects differ" -- so this is a
 * property of what was witnessed, never of how the dice fell.
 */
enum class FrightKind { FRIGHT, AWE, CONFUSION }

/**
 * A Fright Check and, if it failed, what the table said.
 *
 * [margin] is the margin of FAILURE -- how far the roll overshot its target --
 * and is 0 on a pass. Note that [RollResult.margin] counts the other way
 * (target minus total, positive on a success), which is why this is not just
 * forwarded.
 */
data class FrightOutcome(
    val roll: RollResult,
    val target: Int,
    val margin: Int,
    val effectRoll: Int? = null,
    val effect: FrightEffect? = null,
) {
    val passed: Boolean get() = effect == null
}

/**
 * Fright Checks (Ch. 5, p. 43-44).
 *
 * "When characters witness supernatural phenomena, they must make a Fright
 * Check: roll 3d6 vs. Will with the modifier listed below... When a Fright
 * Check is failed, roll 3d6 + margin of failure and consult the table."
 *
 * TWO rolls, not one: the check itself, then the severity. A single roll
 * would make a near miss and a catastrophe the same event.
 *
 * Unlike the Potion Consumption Roll, this table does NOT override the
 * general critical rules -- it says nothing about automatic success or
 * failure -- so [SuccessRoll]'s verdict stands and a 3 passes at any target.
 * Specific beats general only where the specific rule actually speaks.
 */
object FrightCheck {

    /** The table starts at 4: 3 on 3d6 plus the smallest possible margin. */
    const val LOWEST_EFFECT_ROLL = 4

    fun triggers(): List<FrightTrigger> = ContentLoader.frightTriggers()

    /**
     * Roll a Fright Check against [will], shifted by the trigger's [modifier],
     * and resolve the effect if it fails.
     *
     * [modifier] is passed in rather than looked up: two trigger rows print a
     * range and one is open-ended, so the number is the GM's choice, not the
     * table's (see [FrightTrigger]).
     */
    fun against(
        will: Int,
        modifier: Int,
        source: RandomSource,
        effects: List<FrightEffect> = ContentLoader.frightEffects(),
    ): FrightOutcome {
        val target = will + modifier
        val roll = SuccessRoll.against(target, source)
        // By outcome, not by margin: 3 and 4 always succeed whatever the
        // target, and a Will of 6 facing an Outer God at -8 is exactly when
        // that matters.
        if (roll.outcome == Outcome.SUCCESS || roll.outcome == Outcome.CRITICAL_SUCCESS) {
            return FrightOutcome(roll = roll, target = target, margin = 0)
        }
        // RollResult.margin is target - total, so a failure carries it
        // negative; the table wants the distance overshot. A 17 or 18 fails
        // even against a target it did not beat, and that leaves nothing
        // overshot at all -- hence the floor at 0.
        val margin = (roll.total - target).coerceAtLeast(0)
        // The printed table starts at 4, so a bare 3 with no margin lands on
        // no row. The mildest band is the honest answer there; refusing to
        // answer would crash a table mid-scene.
        val effectRoll = (rollThreeDSix(source) + margin).coerceAtLeast(LOWEST_EFFECT_ROLL)
        return FrightOutcome(
            roll = roll,
            target = target,
            margin = margin,
            effectRoll = effectRoll,
            effect = effectFor(effectRoll, effects),
        )
    }

    /** The band [roll] lands in. The last band is open-ended ("35+"). */
    fun effectFor(
        roll: Int,
        effects: List<FrightEffect> = ContentLoader.frightEffects(),
    ): FrightEffect {
        require(roll >= LOWEST_EFFECT_ROLL) {
            "the fright table starts at $LOWEST_EFFECT_ROLL " +
                "(3d6 plus a margin of at least 1); was $roll"
        }
        return effects.lastOrNull { roll >= it.low && (it.high == null || roll <= it.high) }
            ?: error("no fright band covers $roll")
    }

    /** The column of [effect] that [kind] reads. */
    fun text(effect: FrightEffect, kind: FrightKind): String = when (kind) {
        FrightKind.FRIGHT -> effect.fright
        FrightKind.AWE -> effect.awe
        FrightKind.CONFUSION -> effect.confusion
    }

    /**
     * The way out of that column, or null where the table prints none.
     *
     * Three of the nine bands offer one and six do not — past 12 the table
     * hands out a quirk, a disadvantage or a permanent loss with no roll to
     * escape it, and offering one there would be this app granting a way back
     * the book withholds.
     *
     * **The repetition is the GM's**, exactly as [StunRecovery]'s is: rows 4-9
     * say "each sec" and this app holds no clock, so one roll is offered and
     * the cell's own sentence is printed beside it.
     */
    fun recoveryFor(effect: FrightEffect, kind: FrightKind): FrightRecovery? = when (kind) {
        FrightKind.FRIGHT -> effect.frightRecovery
        FrightKind.AWE -> effect.aweRecovery
        FrightKind.CONFUSION -> effect.confusionRecovery
    }

    /**
     * What [traits] add to [recovery], signed.
     *
     * One clause reaches these and it is scoped on the STATE, not the roll:
     * Combat Reflexes' "+6 to recover from stun" answers for rows 4-9 and
     * 10-11, whose cells all print "Stunned", and for none of row 12's three.
     * The discriminator is [StunRecovery]'s, which is where this app already
     * decides what a stun is.
     *
     * **No attribute bonus composes here, and that is the book's doing.**
     * These rolls are Will and IQ; `Fit`'s "+1 to all HT rolls" reaches the
     * Major Wound's recovery for exactly that reason and reaches none of
     * these, and this book prints no "all Will rolls" or "all IQ rolls" trait
     * to take its place. The GM-declared Will clauses are a different thing
     * and the caller supplies them, on the Will columns only.
     */
    fun recoveryBonus(recovery: FrightRecovery, traits: List<Trait>): Int =
        if (StunRecovery.isStun(recovery)) TraitEffects.stunRecoveryBonus(traits) else 0

    private fun rollThreeDSix(source: RandomSource) = (1..3).sumOf { source.roll(6) }
}
