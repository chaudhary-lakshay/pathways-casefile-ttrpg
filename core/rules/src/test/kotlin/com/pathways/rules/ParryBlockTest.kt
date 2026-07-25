package com.pathways.rules

import com.pathways.model.*
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

class ParryBlockTest {

    private fun weapon(name: String, mod: Int?) =
        Weapon(name, SkillKey(name, null, Attr.DX, Difficulty.AVERAGE), mod)

    @Test
    fun `armed parry halves the skill and rounds down`() {
        assertEquals(10, ParryBlock.parryFor(weapon("Rapier", 0), 14))   // 7 + 3
        assertEquals(10, ParryBlock.parryFor(weapon("Rapier", 0), 15))   // still 7 + 3
        assertEquals(11, ParryBlock.parryFor(weapon("Rapier", 0), 16))   // 8 + 3
    }

    @Test
    fun `the weapon modifier applies after the halving`() {
        assertEquals(9, ParryBlock.parryFor(weapon("Sap", -1), 14))
        assertEquals(8, ParryBlock.parryFor(weapon("Scalpel", -2), 14))
    }

    @Test
    fun `a weapon that cannot parry returns null`() {
        assertNull(ParryBlock.parryFor(weapon("Brass knuckles", null), 14))
    }

    @Test
    fun `Wrestling applies its penalty inside the halving`() {
        // (14 - 3) / 2 + 3 = 8, NOT (14 / 2 + 3) - 3 = 7
        assertEquals(8, ParryBlock.unarmedParry(UnarmedStyle.WRESTLING, 14))
        assertEquals(10, ParryBlock.unarmedParry(UnarmedStyle.BRAWLING, 14))
        assertEquals(10, ParryBlock.unarmedParry(UnarmedStyle.BOXING, 14))
    }

    @Test
    fun `block halves the shield skill and adds the shield's DB`() {
        assertEquals(11, ParryBlock.blockFor(shieldSkillLevel = 14, shieldDb = 1))
    }

    @Test
    fun `a character has one parry per readied weapon, not one parry`() {
        val parries = listOf(weapon("Sap", -1), weapon("Brass knuckles", null))
            .map { ParryBlock.parryFor(it, 12) }
        assertEquals(listOf(8, null), parries)
    }

    @Test
    fun `parry floors rather than truncates below zero`() {
        // When skill < 3 in Wrestling, the numerator (skillLevel - 3) goes negative.
        // Math.floorDiv(-1, 2) = -1 (floors toward negative infinity)
        // But plain / in Kotlin truncates toward zero: -1 / 2 = 0
        // Working: skill 2 WRESTLING: (2 - 3) / 2 + 3 = (-1) / 2 + 3 = -1 + 3 = 2 (with floorDiv)
        assertEquals(2, ParryBlock.unarmedParry(UnarmedStyle.WRESTLING, 2))
    }
}
