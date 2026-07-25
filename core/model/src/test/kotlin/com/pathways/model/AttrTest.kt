package com.pathways.model

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class AttrTest {
    @Test
    fun `there are exactly four buyable attributes`() {
        assertEquals(listOf("ST", "DX", "IQ", "HT"), Attr.entries.map { it.name })
    }
}
