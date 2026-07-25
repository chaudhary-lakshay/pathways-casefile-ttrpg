package com.pathways.model

import kotlinx.serialization.Serializable

/**
 * Points spent moving a secondary characteristic off its base value.
 *
 * [basicSpeedQuarters] counts 0.25 steps, because the book prices Basic
 * Speed at 5 points per quarter. Storing it as an Int keeps the whole
 * engine in exact integer arithmetic.
 */
@Serializable
data class SecondaryDelta(
    val hp: Int = 0,
    val fp: Int = 0,
    val will: Int = 0,
    val per: Int = 0,
    val basicSpeedQuarters: Int = 0,
    val basicMove: Int = 0,
)
