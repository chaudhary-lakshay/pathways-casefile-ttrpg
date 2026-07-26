package com.pathways.model

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test

class ControllingAttributeTest {

    @Test
    fun `fromCode round-trips all seven rulebook spellings`() {
        assertEquals(ControllingAttribute.Buyable(Attr.ST), ControllingAttribute.fromCode("ST"))
        assertEquals(ControllingAttribute.Buyable(Attr.DX), ControllingAttribute.fromCode("DX"))
        assertEquals(ControllingAttribute.Buyable(Attr.IQ), ControllingAttribute.fromCode("IQ"))
        assertEquals(ControllingAttribute.Buyable(Attr.HT), ControllingAttribute.fromCode("HT"))
        assertEquals(ControllingAttribute.Will, ControllingAttribute.fromCode("Will"))
        assertEquals(ControllingAttribute.Per, ControllingAttribute.fromCode("Per"))
        assertEquals(ControllingAttribute.Spi, ControllingAttribute.fromCode("SPI"))
    }

    @Test
    fun `fromCode is case-insensitive`() {
        assertEquals(ControllingAttribute.Will, ControllingAttribute.fromCode("will"))
        assertEquals(ControllingAttribute.Spi, ControllingAttribute.fromCode("spi"))
        assertEquals(ControllingAttribute.Buyable(Attr.DX), ControllingAttribute.fromCode("dx"))
    }

    @Test
    fun `fromCode rejects garbage`() {
        assertFailsWith<IllegalArgumentException> { ControllingAttribute.fromCode("SPIRIT") }
        assertFailsWith<IllegalArgumentException> { ControllingAttribute.fromCode("") }
        assertFailsWith<IllegalArgumentException> { ControllingAttribute.fromCode("COR") }
    }

    @Test
    fun `code renders the exact rulebook spelling for every case`() {
        assertEquals("ST", ControllingAttribute.Buyable(Attr.ST).code)
        assertEquals("DX", ControllingAttribute.Buyable(Attr.DX).code)
        assertEquals("IQ", ControllingAttribute.Buyable(Attr.IQ).code)
        assertEquals("HT", ControllingAttribute.Buyable(Attr.HT).code)
        assertEquals("Will", ControllingAttribute.Will.code)
        assertEquals("Per", ControllingAttribute.Per.code)
        assertEquals("SPI", ControllingAttribute.Spi.code)
    }
}
