package com.pathways.rules

import com.pathways.model.Attributes
import com.pathways.model.SecondaryDelta
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class SecondaryCharacteristicsTest {
    private val baseline = Attributes(st = 9, dx = 9, iq = 9, ht = 9)

    @Test
    fun `baseline mortal matches the book`() {
        val s = SecondaryCharacteristics.of(baseline, SecondaryDelta())
        assertEquals(9, s.maxHp)
        assertEquals(9, s.maxFp)
        assertEquals(9, s.will)
        assertEquals(9, s.per)
        assertEquals(18, s.basicSpeedQuarters)   // 4.50
        assertEquals(4, s.basicMove)
        assertEquals(7, s.dodge)
    }

    @Test
    fun `basic speed is stored in quarters and never as a float`() {
        // DX 10, HT 9 -> 19 quarters = 4.75
        val s = SecondaryCharacteristics.of(Attributes(dx = 10, ht = 9), SecondaryDelta())
        assertEquals(19, s.basicSpeedQuarters)
        assertEquals(4, s.basicMove)             // floor(4.75)
        assertEquals(7, s.dodge)                 // floor(4.75) + 3
    }

    @Test
    fun `Occult Scholar's secondaries follow from IQ 10`() {
        val s = SecondaryCharacteristics.of(Attributes(st = 9, dx = 9, iq = 10, ht = 9), SecondaryDelta())
        assertEquals(9, s.maxHp)
        assertEquals(9, s.maxFp)
        assertEquals(10, s.will)
        assertEquals(10, s.per)
        assertEquals(7, s.dodge)
    }

    @Test
    fun `deltas shift the derived value`() {
        val s = SecondaryCharacteristics.of(baseline, SecondaryDelta(hp = 2, will = 1))
        assertEquals(11, s.maxHp)
        assertEquals(10, s.will)
    }

    @Test
    fun `buying basic move does not raise dodge`() {
        val s = SecondaryCharacteristics.of(baseline, SecondaryDelta(basicMove = 2))
        assertEquals(6, s.basicMove)
        assertEquals(7, s.dodge)                 // still floor(4.50) + 3
    }

    @Test
    fun `delta costs follow the book's per-level prices`() {
        assertEquals(4,  SecondaryCharacteristics.costOf(SecondaryDelta(hp = 2)))                 // 2 x 2
        assertEquals(6,  SecondaryCharacteristics.costOf(SecondaryDelta(fp = 2)))                 // 3 x 2
        assertEquals(5,  SecondaryCharacteristics.costOf(SecondaryDelta(will = 1)))               // 5 x 1
        assertEquals(5,  SecondaryCharacteristics.costOf(SecondaryDelta(per = 1)))                // 5 x 1
        assertEquals(10, SecondaryCharacteristics.costOf(SecondaryDelta(basicSpeedQuarters = 2))) // 5 per 0.25
        assertEquals(5,  SecondaryCharacteristics.costOf(SecondaryDelta(basicMove = 1)))          // 5 x 1
    }

    @Test
    fun `buying a characteristic down refunds points`() {
        assertEquals(-4, SecondaryCharacteristics.costOf(SecondaryDelta(hp = -2)))
    }

    @Test
    fun `no purchases cost nothing`() {
        assertEquals(0, SecondaryCharacteristics.costOf(SecondaryDelta()))
    }
}
