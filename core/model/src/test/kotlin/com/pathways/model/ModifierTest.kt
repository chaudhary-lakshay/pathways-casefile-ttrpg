package com.pathways.model

import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test

/**
 * Construction-boundary validation for [ModEffect] payloads (KNOWN_ISSUES:
 * "no validation on effect parameters"). These fail fast where a malformed
 * value is created, rather than deep inside [com.pathways.rules.ModifierResolver].
 */
class ModifierTest {

    @Test
    fun `Scale rejects a zero denominator`() {
        // Previously threw ArithmeticException from Math.floorDiv deep inside
        // the resolver instead of failing at construction.
        assertFailsWith<IllegalArgumentException> { ModEffect.Scale(1, 0) }
    }

    @Test
    fun `Scale accepts a negative numerator - a legal reduction to zero or below`() {
        ModEffect.Scale(0, 2)
        ModEffect.Scale(-1, 2)
    }

    @Test
    fun `Cancel rejects a negative upTo`() {
        // A negative Cancel budget would act as an extra penalty rather than an offset.
        assertFailsWith<IllegalArgumentException> { ModEffect.Cancel(-1, SourceKind.SITUATIONAL) }
    }

    @Test
    fun `Cancel accepts a zero upTo - a no-op cancel is legal`() {
        ModEffect.Cancel(0, SourceKind.SITUATIONAL)
    }
}
