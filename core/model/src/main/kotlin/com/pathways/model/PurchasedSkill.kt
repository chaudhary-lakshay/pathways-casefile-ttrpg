package com.pathways.model

import kotlinx.serialization.Serializable

@Serializable
/**
 * [studied] is how many of [points] were earned by downtime study (ch. 3
 * p. 32): they set the level like any other point, and the budget does not
 * bill them.
 */
data class PurchasedSkill(val key: SkillKey, val points: Int, val studied: Int = 0) {
    init {
        require(points > 0) { "a purchased skill needs at least 1 point" }
        require(studied in 0..points) { "studied points are a share of the skill's; $studied of $points" }
    }
}
