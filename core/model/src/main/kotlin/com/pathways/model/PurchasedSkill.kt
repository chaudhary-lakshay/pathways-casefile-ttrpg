package com.pathways.model

import kotlinx.serialization.Serializable

@Serializable
data class PurchasedSkill(val key: SkillKey, val points: Int) {
    init { require(points > 0) { "a purchased skill needs at least 1 point" } }
}
