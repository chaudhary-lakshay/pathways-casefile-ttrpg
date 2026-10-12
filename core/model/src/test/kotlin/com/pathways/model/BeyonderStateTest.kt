package com.pathways.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BeyonderStateTest {

    /**
     * Hanged Man 9's drawback is chosen once, at Sequence 9, and the chain
     * keeps it (6he). A fresh state on advancing would drop it, and
     * [canDrink]'s `next == advanced()` would then refuse a next potion that
     * carries it.
     */
    @Test
    fun `advancing keeps the drawback chosen at Sequence 9`() {
        val nine = BeyonderState("Hanged Man", 9, digestion = 100, drawback = "Paranoia")
        assertEquals(BeyonderState("Hanged Man", 8, drawback = "Paranoia"), nine.advanced())
    }
}
