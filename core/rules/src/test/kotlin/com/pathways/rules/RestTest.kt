package com.pathways.rules

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Ch. 7's Rest (v6.9i 3470-3530): what a short or long rest gives back. */
class RestTest {

    @Test
    fun `a short rest is 1 FP per 10 minutes and 1 SPI an hour for 3 hours`() {
        val short = Rest.named("Short Rest")
        assertEquals(RestRecovery(fp = 18, spi = 3), Rest.recovery(short, cogitated = false, maxFp = 30))
        // Cogitation's success doubles SPI recovery "for that rest".
        assertEquals(RestRecovery(fp = 18, spi = 6), Rest.recovery(short, cogitated = true, maxFp = 30))
    }

    @Test
    fun `a long rest restores all FP and 9 SPI, 18 with Cogitation`() {
        val long = Rest.named("Long Rest")
        // "All FP restored": all of it, even from below 0, so the recovery says so.
        assertEquals(RestRecovery(fp = 12, spi = 9, allFp = true), Rest.recovery(long, cogitated = false, maxFp = 12))
        assertEquals(RestRecovery(fp = 12, spi = 18, allFp = true), Rest.recovery(long, cogitated = true, maxFp = 12))
    }
}
