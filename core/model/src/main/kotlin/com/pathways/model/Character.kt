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
    /**
     * SPI (Spirit). Mortals start at 0; only potions, supernatural events, or
     * rituals raise it (spec: it "cannot be raised by points"). Purchased or
     * awarded state, stored directly — never derived, never billed to
     * [com.pathways.rules.PointBudget].
     */
    val spi: Int = 0,
    val traits: List<Trait> = emptyList(),
    val skills: List<PurchasedSkill> = emptyList(),
    val weapons: List<Weapon> = emptyList(),
)
