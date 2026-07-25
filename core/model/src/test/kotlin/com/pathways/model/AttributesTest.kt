package com.pathways.model

import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test

/**
 * Construction-boundary validation for [Attributes] (KNOWN_ISSUES: "no
 * validation on ... attribute bounds"). Bounds mirror [AttributeCosts]'s
 * existing floor and the new [MAX_ATTRIBUTE] ceiling, so a malformed
 * attribute value fails here rather than deep inside a cost calculation.
 */
class AttributesTest {

    @Test
    fun `every level from BASE_ATTRIBUTE to MAX_ATTRIBUTE is legal`() {
        Attributes(st = BASE_ATTRIBUTE, dx = BASE_ATTRIBUTE, iq = BASE_ATTRIBUTE, ht = BASE_ATTRIBUTE)
        Attributes(st = MAX_ATTRIBUTE, dx = MAX_ATTRIBUTE, iq = MAX_ATTRIBUTE, ht = MAX_ATTRIBUTE)
    }

    @Test
    fun `below BASE_ATTRIBUTE is rejected for every attribute`() {
        assertFailsWith<IllegalArgumentException> { Attributes(st = BASE_ATTRIBUTE - 1) }
        assertFailsWith<IllegalArgumentException> { Attributes(dx = BASE_ATTRIBUTE - 1) }
        assertFailsWith<IllegalArgumentException> { Attributes(iq = BASE_ATTRIBUTE - 1) }
        assertFailsWith<IllegalArgumentException> { Attributes(ht = BASE_ATTRIBUTE - 1) }
    }

    @Test
    fun `above MAX_ATTRIBUTE is rejected`() {
        assertFailsWith<IllegalArgumentException> { Attributes(iq = MAX_ATTRIBUTE + 1) }
    }
}
