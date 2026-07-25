package com.pathways.model

import kotlinx.serialization.Serializable

/** Generous magnitude bound for a [SecondaryDelta] field — see its class KDoc. */
const val MAX_SECONDARY_DELTA_MAGNITUDE = 100

/**
 * Points spent moving a secondary characteristic off its base value.
 *
 * [basicSpeedQuarters] counts 0.25 steps, because the book prices Basic
 * Speed at 5 points per quarter. Storing it as an Int keeps the whole
 * engine in exact integer arithmetic.
 *
 * Each field is bounded to +-[MAX_SECONDARY_DELTA_MAGNITUDE], well clear of
 * the largest delta exercised anywhere in this codebase (a 25-quarter Basic
 * Speed swing) or in a published sheet, but tight enough to reject an
 * obviously malformed import (a stray extra digit) at construction rather
 * than let it silently distort [com.pathways.rules.SecondaryCharacteristics].
 */
@Serializable
data class SecondaryDelta(
    val hp: Int = 0,
    val fp: Int = 0,
    val will: Int = 0,
    val per: Int = 0,
    val basicSpeedQuarters: Int = 0,
    val basicMove: Int = 0,
) {
    init {
        val fields = listOf(
            "hp" to hp, "fp" to fp, "will" to will, "per" to per,
            "basicSpeedQuarters" to basicSpeedQuarters, "basicMove" to basicMove,
        )
        for ((label, v) in fields) {
            require(v in -MAX_SECONDARY_DELTA_MAGNITUDE..MAX_SECONDARY_DELTA_MAGNITUDE) {
                "SecondaryDelta.$label magnitude too large: $v (must be within " +
                    "+-$MAX_SECONDARY_DELTA_MAGNITUDE)"
            }
        }
    }
}
