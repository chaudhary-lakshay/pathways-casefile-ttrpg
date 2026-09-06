package com.pathways.model

enum class Outcome {
    CRITICAL_SUCCESS,
    SUCCESS,
    FAILURE,
    CRITICAL_FAILURE,
    ;

    /**
     * Whether the roll came in under its target, critical or not.
     *
     * Three sites asked this as a two-branch comparison, and a screen that
     * re-derives it from a total and a recomputed target can disagree with the
     * roll it is printing. The answer travels with the result instead.
     */
    val isSuccess: Boolean get() = this == CRITICAL_SUCCESS || this == SUCCESS
}

data class RollResult(val total: Int, val target: Int, val margin: Int, val outcome: Outcome)
