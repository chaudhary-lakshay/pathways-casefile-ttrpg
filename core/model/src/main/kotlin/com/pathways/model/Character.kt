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
 * Purchased state, plus three things nobody buys: [beyonder], the pathway
 * walked, [boon], the one held beside it, and [spi]. Nothing here is derived, and table state -- pools
 * spent, CoR -- is the session's, never the character's.
 *
 * [spi] is the SPI held BEFORE any potion: digestion adds each grant to it,
 * and the played value is `DigestedCharacter.effectiveSpi`. Since 6ft no
 * production path sets it above 0 -- a premade plays 0 whatever its sheet
 * prints -- so it is where an award from "supernatural events, or certain
 * rituals" (book line 2269) would go, and nothing writes one.
 *
 * Until 6fu this said the rest of Beyonder state "arrives at M3 and M4".
 * It arrived.
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
    /**
     * The pathway and Sequence this character has drunk their way to, or null
     * for a mortal. Only the choice is stored; every gain that follows from it
     * is derived through [com.pathways.rules.PotionDigestion], which is what
     * keeps free potion gains out of [com.pathways.rules.PointBudget].
     */
    val beyonder: BeyonderState? = null,
    /**
     * The boon held, or null. The choice only, beside [beyonder] and derived
     * the same way: a boon's gains are as free as a potion's.
     */
    val boon: BoonState? = null,
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
