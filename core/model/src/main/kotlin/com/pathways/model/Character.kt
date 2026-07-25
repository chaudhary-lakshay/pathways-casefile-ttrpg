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
) {
    init {
        // A character buys a given skill once; if a second purchase of the
        // same skill is wanted, its points are added to the existing entry.
        // Without this check, two PurchasedSkill entries sharing a SkillKey
        // silently collapse to one in Derive.skillLevels (built with
        // associate) while PointBudget.report bills both -- a sheet screen
        // would show one skill and charge for two. See KNOWN_ISSUES.
        val duplicates = skills.groupBy { it.key }.filterValues { it.size > 1 }.keys
        require(duplicates.isEmpty()) {
            "duplicate skill purchase(s) for $name: ${duplicates.joinToString { it.display }} " +
                "-- combine each into a single PurchasedSkill instead of buying it twice"
        }
    }
}
