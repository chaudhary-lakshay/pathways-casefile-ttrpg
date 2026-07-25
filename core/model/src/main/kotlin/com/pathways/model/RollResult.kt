package com.pathways.model

enum class Outcome { CRITICAL_SUCCESS, SUCCESS, FAILURE, CRITICAL_FAILURE }

data class RollResult(val total: Int, val target: Int, val margin: Int, val outcome: Outcome)
