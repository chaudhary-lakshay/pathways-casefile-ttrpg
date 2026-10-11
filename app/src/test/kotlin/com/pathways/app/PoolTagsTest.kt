package com.pathways.app

import com.pathways.rules.Pools
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The tags under a pool on the sheet's strip: the book's own name for the
 * state, then the number it costs. The printed row stays one tap away, so a
 * tag only has to say which row and what it moves.
 */
class PoolTagsTest {

    @Test
    fun `a pool at normal function carries no tag`() {
        assertEquals(emptyList<String>(), poolTags(Pools.hp(9, 9)))
        assertEquals(emptyList<String>(), poolTags(Pools.fp(9, 9)))
        assertEquals(emptyList<String>(), poolTags(Pools.spi(9, 9)))
    }

    @Test
    fun `a named state is tagged with the book's name, then its penalty`() {
        assertEquals(listOf("Reeling", "−1 all rolls"), poolTags(Pools.hp(3, 9)))
        assertEquals(listOf("Spiritual Attrition", "−3 SPI rolls"), poolTags(Pools.spi(1, 9)))
    }

    @Test
    fun `a row the book does not name is tagged with its printed level`() {
        assertEquals(listOf("0 HP", "−1 all rolls"), poolTags(Pools.hp(0, 9)))
    }

    @Test
    fun `fatigue has no name, so only its penalty shows`() {
        assertEquals(listOf("−1 all rolls"), poolTags(Pools.fp(3, 9)))
    }

    @Test
    fun `soul debt says what it charged`() {
        assertEquals(listOf("Soul Debt", "−3 SPI rolls", "+2 CoR"), poolTags(Pools.spi(-2, 9)))
    }

    @Test
    fun `dead and unconscious say so first`() {
        assertEquals(listOf("Dead"), poolTags(Pools.hp(-45, 9)))
        assertEquals("Unconscious", poolTags(Pools.fp(-10, 9)).first())
    }

    @Test
    fun `exhaustion and the survival row are tagged too`() {
        assertEquals(listOf("Spiritual Exhaustion", "−3 SPI rolls"), poolTags(Pools.spi(0, 9)))
        assertEquals(listOf("-HP", "−1 all rolls"), poolTags(Pools.hp(-9, 9)))
        assertEquals(listOf("−2 all rolls"), poolTags(Pools.fp(-2, 9)))
    }
}
