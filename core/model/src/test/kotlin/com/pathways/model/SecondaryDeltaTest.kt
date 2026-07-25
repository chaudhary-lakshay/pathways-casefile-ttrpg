package com.pathways.model

import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test

/**
 * Construction-boundary validation for [SecondaryDelta] (KNOWN_ISSUES: "no
 * validation on ... SecondaryDelta accept[ing] any value"). The bound is
 * deliberately generous - see [MAX_SECONDARY_DELTA_MAGNITUDE]'s KDoc - it
 * only needs to reject obviously malformed input.
 */
class SecondaryDeltaTest {

    @Test
    fun `magnitudes at the bound are legal`() {
        SecondaryDelta(
            hp = MAX_SECONDARY_DELTA_MAGNITUDE, fp = -MAX_SECONDARY_DELTA_MAGNITUDE,
            will = MAX_SECONDARY_DELTA_MAGNITUDE, per = -MAX_SECONDARY_DELTA_MAGNITUDE,
            basicSpeedQuarters = MAX_SECONDARY_DELTA_MAGNITUDE, basicMove = -MAX_SECONDARY_DELTA_MAGNITUDE,
        )
    }

    @Test
    fun `a magnitude one past the bound is rejected on every field`() {
        val over = MAX_SECONDARY_DELTA_MAGNITUDE + 1
        assertFailsWith<IllegalArgumentException> { SecondaryDelta(hp = over) }
        assertFailsWith<IllegalArgumentException> { SecondaryDelta(fp = -over) }
        assertFailsWith<IllegalArgumentException> { SecondaryDelta(will = over) }
        assertFailsWith<IllegalArgumentException> { SecondaryDelta(per = -over) }
        assertFailsWith<IllegalArgumentException> { SecondaryDelta(basicSpeedQuarters = over) }
        assertFailsWith<IllegalArgumentException> { SecondaryDelta(basicMove = -over) }
    }
}
