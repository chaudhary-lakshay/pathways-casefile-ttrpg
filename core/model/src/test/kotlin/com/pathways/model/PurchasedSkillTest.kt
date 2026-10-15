package com.pathways.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** [PurchasedSkill.studied] is a share of [PurchasedSkill.points], never more. */
class PurchasedSkillTest {

    private val knife = SkillKey("Knife", null, ControllingAttribute.Buyable(Attr.DX), Difficulty.EASY)

    @Test
    fun `studied points are some of the skill's points`() {
        assertEquals(2, PurchasedSkill(knife, 4, studied = 2).studied)
    }

    @Test
    fun `studied points cannot exceed the skill's points or go negative`() {
        assertThrows<IllegalArgumentException> { PurchasedSkill(knife, 1, studied = 2) }
        assertThrows<IllegalArgumentException> { PurchasedSkill(knife, 1, studied = -1) }
    }
}
