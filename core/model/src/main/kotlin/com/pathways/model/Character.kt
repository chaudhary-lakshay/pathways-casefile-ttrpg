package com.pathways.model

import kotlinx.serialization.Serializable

/**
 * Purchased state only. Nothing here is derived; nothing derived is stored.
 * Beyonder state and table state arrive at M3 and M4.
 */
@Serializable
data class Character(
    val name: String,
    val attributes: Attributes = Attributes(),
    val secondaryDelta: SecondaryDelta = SecondaryDelta(),
    val traits: List<Trait> = emptyList(),
    val skills: List<PurchasedSkill> = emptyList(),
    val weapons: List<Weapon> = emptyList(),
)
