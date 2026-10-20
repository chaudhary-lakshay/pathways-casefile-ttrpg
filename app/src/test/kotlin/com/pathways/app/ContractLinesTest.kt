package com.pathways.app

import com.pathways.rules.ClauseBonus
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Issue #1: the contract footer prints every term its target is made of. */
class ContractLinesTest {

    private val none = ClauseBonus(0, null)

    @Test
    fun `the corruption penalty is printed`() {
        val lines = contractLines(16, 0, 0, none, cor = 4, sequence = 6, held = 0, burned = 0)
        assertTrue("-1 for 4 CoR" in lines, "$lines")
    }

    @Test
    fun `hurt, shock and declared states are printed`() {
        val lines = contractLines(16, -1, -4, ClauseBonus(-2, "-2 declared"), cor = 0, sequence = 6, held = 0, burned = 0)
        assertTrue("-1 hurt or tired" in lines, "$lines")
        assertTrue("-4 shock" in lines, "$lines")
        assertTrue("-2 declared" in lines, "$lines")
    }

    @Test
    fun `a term worth nothing is not printed`() {
        val lines = contractLines(16, 0, 0, none, cor = 2, sequence = 6, held = 0, burned = 0)
        assertFalse(lines.any { "CoR" in it || "shock" in it || "hurt" in it }, "$lines")
    }
}
