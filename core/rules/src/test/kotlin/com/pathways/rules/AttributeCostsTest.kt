package com.pathways.rules

import com.pathways.model.Attr
import com.pathways.model.Attributes
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class AttributeCostsTest {
    @Test
    fun `nine is free and gives no refund`() {
        Attr.entries.forEach { assertEquals(0, AttributeCosts.costOf(it, 9)) }
    }

    @Test
    fun `the book's worked examples hold`() {
        assertEquals(60, AttributeCosts.costOf(Attr.DX, 11))   // 20 + 40
        assertEquals(70, AttributeCosts.costOf(Attr.ST, 12))   // 10 + 20 + 40
    }

    @Test
    fun `ST and HT are half the price of DX and IQ`() {
        assertEquals(10, AttributeCosts.costOf(Attr.ST, 10))
        assertEquals(10, AttributeCosts.costOf(Attr.HT, 10))
        assertEquals(20, AttributeCosts.costOf(Attr.DX, 10))
        assertEquals(20, AttributeCosts.costOf(Attr.IQ, 10))
    }

    @Test
    fun `Occult Scholar spends exactly twenty points on attributes`() {
        // ST 9, DX 9, IQ 10, HT 9 -> only IQ costs anything
        assertEquals(20, AttributeCosts.totalCost(Attributes(st = 9, dx = 9, iq = 10, ht = 9)))
    }

    @Test
    fun `below nine is not a legal purchase`() {
        assertFailsWith<IllegalArgumentException> { AttributeCosts.costOf(Attr.ST, 8) }
    }

    @Test
    fun `above MAX_ATTRIBUTE is rejected before the doubling ladder can overflow`() {
        // KNOWN_ISSUES: costOf's shift ladder overflows Int past roughly level
        // 40. MAX_ATTRIBUTE (30) rejects well before that, at construction time.
        assertFailsWith<IllegalArgumentException> {
            AttributeCosts.costOf(Attr.DX, com.pathways.model.MAX_ATTRIBUTE + 1)
        }
    }

    @Test
    fun `MAX_ATTRIBUTE itself is still a legal, non-overflowing purchase`() {
        val cost = AttributeCosts.costOf(Attr.DX, com.pathways.model.MAX_ATTRIBUTE)
        assertTrue(cost > 0)
    }
}
