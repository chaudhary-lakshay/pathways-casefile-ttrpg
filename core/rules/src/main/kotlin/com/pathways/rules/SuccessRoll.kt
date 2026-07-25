package com.pathways.rules

import com.pathways.model.DiceExpr
import com.pathways.model.Outcome
import com.pathways.model.RollResult

/** Ch. 2, p. 7. Roll 3d6, succeed on equal or under. */
object SuccessRoll {

    private val THREE_D_SIX = DiceExpr(3, 6, 0)

    fun against(target: Int, source: RandomSource): RollResult =
        evaluate(THREE_D_SIX.roll(source), target)

    fun evaluate(total: Int, target: Int): RollResult {
        val margin = target - total
        val outcome = when {
            total <= 4 -> Outcome.CRITICAL_SUCCESS
            total >= 17 -> Outcome.CRITICAL_FAILURE
            margin <= -10 -> Outcome.CRITICAL_FAILURE
            margin >= 0 -> Outcome.SUCCESS
            else -> Outcome.FAILURE
        }
        return RollResult(total, target, margin, outcome)
    }
}
