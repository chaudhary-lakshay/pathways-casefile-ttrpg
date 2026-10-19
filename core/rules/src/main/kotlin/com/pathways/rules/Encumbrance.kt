package com.pathways.rules

import com.pathways.model.Trait
import com.pathways.rules.content.ContentLoader
import com.pathways.rules.content.EncumbranceLevel

/**
 * Ch. 2's Basic Lift & Carrying Capacity (v6.9i 400-427). The player types
 * the total weight carried (owner's ruling 9: only shields print a weight),
 * and the level gives Move and Dodge their penalties.
 *
 * Pushing/Lifting (424-426) is not modelled: it is a ST roll the GM calls
 * for an object, and the app holds no object's weight to divide by.
 */
object Encumbrance {

    const val LIFTING_ST = "Lifting ST"

    /** "Basic Lift (BL) = (ST x ST) / 5 lbs", with Lifting ST "added to your ST". */
    fun basicLift(st: Int, traits: List<Trait>): Double {
        val lifting = st + (TraitNames.levelOf(traits, LIFTING_ST) ?: 0)
        return lifting * lifting / 5.0
    }

    fun levels(): List<EncumbranceLevel> = ContentLoader.encumbrance()

    /**
     * The level [carriedLbs] puts a character with [basicLift] at: the first
     * whose "Max Load" holds it. Null past Overload's 15 x BL, where the table
     * prints no level, so the load cannot be carried.
     */
    fun levelFor(carriedLbs: Double, basicLift: Double): EncumbranceLevel? {
        require(carriedLbs >= 0) { "a carried weight cannot be negative (was $carriedLbs)" }
        return levels().sortedBy { it.level }.firstOrNull { carriedLbs <= it.maxLoadBl * basicLift }
    }
}
