package com.pathways.model

import kotlinx.serialization.Serializable

/**
 * [parryMod] is null when the sheet prints "—": that weapon cannot parry
 * at all, which is not the same as parrying at +0.
 */
@Serializable
data class Weapon(
    val name: String,
    val skill: SkillKey,
    val parryMod: Int? = 0,
)
