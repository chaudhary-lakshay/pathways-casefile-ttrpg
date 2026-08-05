package com.pathways.rules

import com.pathways.rules.content.ContentLoader
import com.pathways.rules.content.EntityResponse

/**
 * What the entity gave, once it has been asked.
 *
 * [roll] is what the dice said and [shifted] is what was read off the table --
 * they differ only for an orthodox deity in good standing. [ritualModifier] is
 * 0 for a rejection: the refusal travels in [failsRitual], never as a number a
 * caller might add to a target and then roll against anyway.
 */
data class EntityAnswer(
    val roll: Int,
    val shifted: Int,
    val response: EntityResponse,
    val ritualModifier: Int,
    val failsRitual: Boolean,
)

/**
 * Borrowing a ritual's power from an entity (Ch. 7, Section IV.F, p. 66).
 *
 * "The GM rolls secretly on the Entity Response Table first, then applies the
 * entity's modifier to the Ritualistic Magic roll."
 *
 * **The orthodox shift lands on the WRONG roll if read quickly.** The book
 * says orthodox deities "shift the table result by -2 when the request aligns
 * with their domain and the caster is in good standing". That -2 is applied to
 * the GM's 3d6 against THIS table, not to the ritual roll. The table runs
 * best-first -- 3-5 is a Full response worth +5, 18 is Active rejection -- so
 * subtracting 2 walks a favoured caster toward the good rows. It is a reward.
 * Applied to the ritual roll it becomes a penalty, and a Nighthawk praying to
 * the Evernight Goddess ends up worse off than a stranger. Hence
 * [shiftedRoll], which takes a roll and returns a roll: nothing here hands
 * back a number that could be added to a skill by mistake.
 *
 * **Two rows print no modifier, and they are not the same absence.** 12-14 is
 * an entity that does not answer -- the ritual proceeds unaided, so nothing is
 * added. 18 is a refusal: the ritual fails whatever the caster rolled, exactly
 * as a Broken mystical language forbids the attempt instead of pricing it
 * ([Languages]). [ritualModifier] answers 0 for the first and refuses the
 * second.
 *
 * **The secret roll is not modelled.** The book has the GM roll this unseen,
 * and this app is one screen held by one person -- hiding a roll from its only
 * viewer is theatre, not a rule. A GM running the table can roll here and
 * simply not say what came up.
 */
object Invocation {

    /**
     * What an orthodox deity in good standing subtracts from the TABLE roll.
     *
     * Negative BECAUSE the table is ordered best-first. Kept here rather than
     * in the JSON because it is a number inside a sentence -- the same split
     * the maneuver table's +4/-4 and [RitualMode.SUPPORT_CAP] take -- and
     * because a committed column would lose the fact that matters most about
     * it: which roll it lands on.
     */
    const val ORTHODOX_SHIFT = -2

    /** The lowest and highest a 3d6 can show, and this table's own bounds. */
    private const val LOWEST = 3
    private const val HIGHEST = 18

    /** The six bands, best first. */
    fun responses(): List<EntityResponse> = ContentLoader.entityResponse()

    /**
     * The band a [roll] of 3..18 lands in.
     *
     * Refused outside that range rather than clamped: a caller with a 2 has a
     * bug in its dice, and answering "Full response" would hide it.
     */
    fun responseFor(roll: Int): EntityResponse {
        require(roll in LOWEST..HIGHEST) { "an entity response is rolled on 3d6; got $roll" }
        return responses().firstOrNull { roll >= it.low && roll <= it.high }
            ?: throw IllegalStateException("no entity response band covers $roll")
    }

    /**
     * What [response] adds to the ritual roll.
     *
     * 0 when the book prints no number -- an entity that does not answer does
     * not penalise either. Refused for the rejection row, because there is no
     * roll left to modify.
     */
    fun ritualModifier(response: EntityResponse): Int {
        require(!response.automaticFailure) {
            "the ${response.low}-${response.high} row fails the ritual automatically; " +
                "there is no modifier to apply"
        }
        return response.ritualModifier ?: 0
    }

    /**
     * [roll] as it should be read off the table.
     *
     * Floored at 3: a rolled 3 is already the best row and shifting it to 1
     * would land on no row at all. Not clamped at the top, because the shift
     * only ever moves down.
     */
    fun shiftedRoll(roll: Int, orthodoxInGoodStanding: Boolean): Int {
        require(roll in LOWEST..HIGHEST) { "an entity response is rolled on 3d6; got $roll" }
        if (!orthodoxInGoodStanding) return roll
        return maxOf(LOWEST, roll + ORTHODOX_SHIFT)
    }

    /**
     * Ask the entity, and report what it gave.
     *
     * The shift is applied BEFORE the row is read, because it moves the roll
     * and not the result: reading the row first and adjusting afterwards would
     * answer from the wrong band entirely.
     */
    fun ask(source: RandomSource, orthodoxInGoodStanding: Boolean): EntityAnswer {
        val roll = (1..3).sumOf { source.roll(6) }
        val shifted = shiftedRoll(roll, orthodoxInGoodStanding)
        val response = responseFor(shifted)
        return EntityAnswer(
            roll = roll,
            shifted = shifted,
            response = response,
            ritualModifier = if (response.automaticFailure) 0 else ritualModifier(response),
            failsRitual = response.automaticFailure,
        )
    }
}
