package com.pathways.rules

import com.pathways.model.Weapon

enum class UnarmedStyle { BRAWLING, BOXING, WRESTLING }

/**
 * Ch. 5, p. 37. Parry is a property of a readied weapon, not of a
 * character — a character carrying two weapons has two parry values, and
 * some weapons have none at all.
 */
object ParryBlock {

    fun parryFor(weapon: Weapon, skillLevel: Int): Int? =
        weapon.parryMod?.let { mod -> skillLevel / 2 + 3 + mod }

    fun unarmedParry(style: UnarmedStyle, skillLevel: Int): Int = when (style) {
        UnarmedStyle.BRAWLING, UnarmedStyle.BOXING -> skillLevel / 2 + 3
        // The -3 sits inside the halving, so this is not the same as subtracting 3 at the end.
        UnarmedStyle.WRESTLING -> (skillLevel - 3) / 2 + 3
    }

    fun blockFor(shieldSkillLevel: Int, shieldDb: Int): Int = shieldSkillLevel / 2 + 3 + shieldDb
}
