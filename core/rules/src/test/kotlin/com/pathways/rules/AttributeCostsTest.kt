package com.pathways.rules

import com.pathways.model.Attr
import com.pathways.model.Attributes
import com.pathways.rules.content.ContentLoader
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test

class AttributeCostsTest {
    @Test
    fun `nine is free and gives no refund`() {
        Attr.entries.forEach { assertEquals(0, AttributeCosts.costOf(it, 9)) }
    }

    @Test
    fun `the book's worked examples hold`() {
        // v6.9i 552-553: "DX 11: Two levels ... at 20 pts each. Total: 40 pts"
        // and "ST 12: Three levels ... at 10 pts each. Total: 30 pts".
        assertEquals(40, AttributeCosts.costOf(Attr.DX, 11))
        assertEquals(30, AttributeCosts.costOf(Attr.ST, 12))
    }

    @Test
    fun `every level costs the flat rate attribute_costs json prints`() {
        // v6.9i 354: "Raising them costs a flat rate per level": ST 10, DX,
        // IQ and HT 20. HT was 10 under v6.9d's doubling table.
        val printed = ContentLoader.attributeCosts().associate { it.attr to it.costPerLevel }
        assertEquals(mapOf("ST" to 10, "DX" to 20, "IQ" to 20, "HT" to 20), printed)
        for (attr in Attr.entries) {
            for (level in 10..20) {
                assertEquals(
                    printed.getValue(attr.name),
                    AttributeCosts.costOf(attr, level) - AttributeCosts.costOf(attr, level - 1),
                    "$attr ${level - 1}->$level",
                )
            }
        }
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
    fun `above MAX_ATTRIBUTE is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            AttributeCosts.costOf(Attr.DX, com.pathways.model.MAX_ATTRIBUTE + 1)
        }
    }

    @Test
    fun `MAX_ATTRIBUTE itself is a legal purchase at the flat rate`() {
        assertEquals(20 * (com.pathways.model.MAX_ATTRIBUTE - 9), AttributeCosts.costOf(Attr.DX, com.pathways.model.MAX_ATTRIBUTE))
    }
}
