package com.pathways.rules

import com.pathways.model.Outcome
import com.pathways.model.RollResult

/** Ch. 2, p. 7. Roll 3d6, succeed on equal or under. */
object SuccessRoll {

    /**
     * A 3 or a 4 succeeds whatever the target, and a 17 or 18 fails whatever
     * the target. Named because callers other than [evaluate] need to say so:
     * a screen that prints only "3d6 <= target" tells the player a target
     * below 3 is hopeless, and it is not.
     */
    const val ALWAYS_SUCCEEDS_AT_OR_UNDER = 4
    const val ALWAYS_FAILS_AT_OR_OVER = 17

    fun against(target: Int, source: RandomSource): RollResult =
        evaluate(source.throwThree(target), target)

    fun evaluate(total: Int, target: Int): RollResult {
        val margin = target - total
        val outcome = when {
            total <= ALWAYS_SUCCEEDS_AT_OR_UNDER -> Outcome.CRITICAL_SUCCESS
            total >= ALWAYS_FAILS_AT_OR_OVER -> Outcome.CRITICAL_FAILURE
            margin <= -10 -> Outcome.CRITICAL_FAILURE
            margin >= 0 -> Outcome.SUCCESS
            else -> Outcome.FAILURE
        }
        return RollResult(total, target, margin, outcome)
    }
}
