package com.pathways.model

import kotlinx.serialization.Serializable

@Serializable
enum class TraitKind { ADVANTAGE, DISADVANTAGE, QUIRK }

/**
 * [cost] is always a positive magnitude, exactly as the sheets print it.
 * Disadvantages and quirks GIVE that many points; advantages COST them.
 * Keeping the sign out of the data stops it being applied twice.
 */
@Serializable
data class Trait(val name: String, val cost: Int, val kind: TraitKind) {
    init { require(cost >= 0) { "trait cost is a magnitude; '$name' was $cost" } }
}
