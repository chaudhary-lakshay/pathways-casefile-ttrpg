package com.pathways.app

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The Case File type roles (Figma `oVd3fSYYgtVQT0NXdRdY0x`): Fraunces for
 * display and headline, IBM Plex Sans for body and title, IBM Plex Mono for
 * labels and every changing number.
 */
class ThemeTypeTest {

    @Test
    fun `headlines are Fraunces, reading text is Plex Sans`() {
        val t = PathwaysType
        listOf(t.displaySmall, t.headlineLarge, t.headlineMedium, t.headlineSmall)
            .forEach { assertEquals(Fraunces, it.fontFamily) }
        listOf(t.bodySmall, t.bodyMedium, t.bodyLarge, t.titleSmall, t.titleMedium, t.titleLarge)
            .forEach { assertEquals(PlexSans, it.fontFamily) }
    }

    @Test
    fun `labels are Plex Mono`() {
        val t = PathwaysType
        listOf(t.labelSmall, t.labelMedium, t.labelLarge).forEach { assertEquals(PlexMono, it.fontFamily) }
    }

    @Test
    fun `a changing number is set in Plex Mono at its own size`() {
        val big = PathwaysType.headlineSmall.tabular()
        assertEquals(PlexMono, big.fontFamily)
        assertEquals(PathwaysType.headlineSmall.fontSize, big.fontSize)
        assertEquals(PlexMono, TextStyle().tabular().fontFamily)
    }

    @Test
    fun `corners are the design's small radii`() {
        assertEquals(RoundedCornerShape(2.dp), PathwaysShapes.extraSmall)
        assertEquals(RoundedCornerShape(4.dp), PathwaysShapes.small)
        assertEquals(RoundedCornerShape(6.dp), PathwaysShapes.medium)
        assertEquals(RoundedCornerShape(8.dp), PathwaysShapes.large)
        assertEquals(RoundedCornerShape(8.dp), PathwaysShapes.extraLarge)
    }
}
