package com.pathways.rules.content

import kotlinx.serialization.Serializable

/**
 * One way a weapon can hurt someone: "sw-1 cut", "2d pi".
 *
 * [base] is an ST column ("sw", "thr") or a fixed die count ("2d"). The
 * difference is the whole point: a sword's damage grows with the arm swinging
 * it and a revolver's does not.
 */
@Serializable
data class WeaponMode(
    val base: String,
    val modifier: Int,
    val type: String,
    val printed: String,
) {
    /** True when [base] names a column of the thrust/swing table. */
    val isFromSt: Boolean get() = base == "sw" || base == "thr"
}

/**
 * A row of the Weapons table (Ch. 8, p. 80).
 *
 * Named apart from [com.pathways.model.Weapon] on purpose: that is a weapon a
 * character is carrying, this is a line in the book's catalogue.
 *
 * [damage] holds every mode the row prints -- two for the Combat Knife and the
 * Broadsword, which can cut or thrust. [halfRange]/[maxRange] are null for a
 * melee weapon, whose Range cell is a reach letter ("C", "C/1") and not a
 * distance in metres.
 */
@Serializable
data class CataloguedWeapon(
    val name: String,
    val damage: List<WeaponMode>,
    val acc: Int,
    val range: String,
    val halfRange: Int? = null,
    val maxRange: Int? = null,
    val cost: String,
    val skill: String,
    val notes: String = "",
)

/**
 * One row of Starting Wealth by Economic Status (Ch. 8, p. 80).
 *
 * [points] is SIGNED, exactly as the table prints it ("Dead Broke (-25 pts)"),
 * while [com.pathways.model.Trait.cost] is a magnitude whose direction lives
 * in its kind. [com.pathways.rules.Wealth.traitFor] is the one place the two
 * meet; applying both conventions would double the sign.
 *
 * [socialWeight] is the printed column, which is not symmetric around
 * Average -- see [com.pathways.rules.Wealth]. v6.9i calls it "a guideline for
 * the GM" (4294-4295): it is never added to a roll.
 */
@Serializable
data class WealthLevel(
    val status: String,
    val points: Int,
    val startingMoney: String,
    val socialWeight: Int,
    val lifestyle: String,
)

/**
 * A skill one of chapter 8's equipment rows names in a signed clause.
 *
 * [specialization] is part of the identity, not a decoration: the compass
 * reads "Navigation (Land) +1", and a character rolling the bare Navigation
 * row is rolling a different skill. Compared against
 * [com.pathways.model.SkillKey]'s own two fields, never against the name.
 */
@Serializable
data class EquipmentSkill(
    val name: String,
    val specialization: String? = null,
)

/**
 * One row of the Common Equipment table (Ch. 8, p. 81-83).
 *
 * [notes] is the book's cell VERBATIM, and no number is derived from it.
 * Nothing here records possession -- the 23 fixtures carry no equipment at
 * all -- so no bonus can be applied to a roll without the screen asserting
 * the character owns the item. It is shown and left to the GM, the same
 * standing the roll dialog already gives Occultism's conditional push.
 *
 * [skills] is a discovery index answering "which rows should a Search roll
 * put in front of the player", built only from SIGNED clauses. "Lock picks
 * (set)" says "Required for Lockpicking skill" and joins nothing, because a
 * mention is not a bonus.
 */
@Serializable
data class EquipmentItem(
    val name: String,
    val cost: String,
    val category: String,
    val notes: String,
    val skills: List<EquipmentSkill> = emptyList(),
)
