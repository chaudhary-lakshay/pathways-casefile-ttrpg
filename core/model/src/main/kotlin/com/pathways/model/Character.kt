package com.pathways.model

import kotlinx.serialization.Serializable

/**
 * Generous upper bound on [Character.spi], purely to catch malformed input (a
 * stray extra digit, a bad import) the same way [MAX_ATTRIBUTE] and
 * [MAX_SECONDARY_DELTA_MAGNITUDE] already do for their fields -- not a
 * rulebook figure. The book never states a numeric ceiling for SPI.
 */
const val MAX_SPI = 1000

/**
 * Mostly purchased state, plus [spi] - the one piece of table state that
 * already needs to exist this early (SPI-controlled skills like Spiritual
 * Intuition need a live SPI value to resolve against, even though SPI itself
 * is never bought with points). Nothing here is derived, and no OTHER table
 * state - current HP/FP, conditions, active situational modifiers - is
 * stored yet; that (and the rest of Beyonder state) arrives at M3 and M4.
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
        require(spi in 0..MAX_SPI) {
            "spi must be between 0 and $MAX_SPI (mortals start at 0; nothing legal reaches $MAX_SPI); was $spi"
        }
    }
}
