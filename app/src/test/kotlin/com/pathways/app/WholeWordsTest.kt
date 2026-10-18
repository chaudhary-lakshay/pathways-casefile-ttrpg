package com.pathways.app

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** A rung's name may wrap between words, never inside one ("Magicia/n", seen at 390 dp and font 1.3). */
class WholeWordsTest {

    @Test
    fun `a line that ends inside a word breaks it`() {
        assertTrue(breaksAWord("Magician", lineEnds = listOf(7, 8)))
    }

    @Test
    fun `a line that ends at a space keeps every word whole`() {
        assertFalse(breaksAWord("Unwinged Angel", lineEnds = listOf(9, 14)))
    }

    @Test
    fun `one line breaks nothing`() {
        assertFalse(breaksAWord("Faceless", lineEnds = listOf(8)))
    }

    @Test
    fun `a break after a bracket or hyphen is between words`() {
        assertFalse(breaksAWord("Unwinged Angel (Coldblooded)", lineEnds = listOf(15, 28)))
        assertFalse(breaksAWord("Fast-Draw", lineEnds = listOf(5, 9)))
    }
}
