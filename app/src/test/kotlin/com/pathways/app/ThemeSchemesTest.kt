package com.pathways.app

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

/**
 * Guards the output of `tools/gen_theme.py`: the one Case File scheme.
 *
 * The generator is not run by the build -- it needs a Python package that is
 * deliberately not a test dependency -- so `ThemeSchemes.kt` is a checked-in
 * artefact like any extracted content file. The token values below are typed
 * from the design (Figma `oVd3fSYYgtVQT0NXdRdY0x`), never read off the
 * generator, so the two cannot agree by sharing a mistake.
 */
class ThemeSchemesTest {

    /**
     * Every `Color` role `ColorScheme` exposes, found by reflection: a list of
     * the roles missing LAST time cannot see the next one.
     */
    private val roleReaders: List<Pair<String, (ColorScheme) -> Color>> =
        ColorScheme::class.members
            .filterIsInstance<kotlin.reflect.KProperty1<ColorScheme, *>>()
            .filter { it.returnType.classifier == Color::class }
            .sortedBy { it.name }
            .map { prop -> prop.name to { s: ColorScheme -> prop.get(s) as Color } }

    /** Pure white or black is the tonal ramp's endpoint in every scheme: agreement, not a fallback. */
    private fun isRampEndpoint(c: Color): Boolean = c == Color.White || c == Color.Black

    @Test
    fun `the reflective sweep actually finds the roles`() {
        assertTrue(roleReaders.size >= 45, "only ${roleReaders.size} Color roles found; the reflection broke")
    }

    @TestFactory
    fun `no role is left at Materials baseline token`(): List<DynamicTest> {
        val baseline = lightColorScheme()
        return roleReaders
            .filterNot { (role, _) -> role == "scrim" }
            .filterNot { (_, read) -> isRampEndpoint(read(CaseFile)) }
            .map { (role, read) ->
                DynamicTest.dynamicTest(role) {
                    assertNotEquals(read(baseline), read(CaseFile), "$role resolves to Material's baseline token")
                }
            }
    }

    @Test
    fun `endpoints stay rare`() {
        // The backstop for the exclusion above: a flattened scheme would
        // excuse itself into a green run otherwise.
        val endpoints = roleReaders.count { (_, read) -> isRampEndpoint(read(CaseFile)) }
        assertTrue(endpoints <= 8, "$endpoints roles are pure white or black")
    }

    /** The design's tokens, each on the role a screen paints it through. */
    @Test
    fun `the Case File tokens sit on their roles`() {
        val s = CaseFile
        assertEquals(PAPER, s.background)
        assertEquals(PAPER, s.surface)
        assertEquals(CARD, s.surfaceContainerLow)
        assertEquals(CARD, s.surfaceContainer)
        assertEquals(INK, s.primary)
        assertEquals(INK, s.onSurface)
        assertEquals(INK, s.outline)
        assertEquals(MUTE, s.onSurfaceVariant)
        assertEquals(RULE, s.outlineVariant)
        assertEquals(STAMP, s.error)
    }

    /**
     * A selected FilterChip fills with `secondaryContainer`: ink, so it reads
     * as the design's black chip with no call-site change. And a Surface
     * whose colour is `primary` takes `onPrimary`, which Compose resolves
     * FIRST when two roles share ink (Codex's design review): both must be paper.
     */
    @Test
    fun `whatever is filled with ink is written in paper`() {
        val s = CaseFile
        assertEquals(INK, s.secondaryContainer)
        assertEquals(PAPER, s.onSecondaryContainer)
        assertEquals(PAPER, s.onPrimary)
    }

    /**
     * Every text colour reads at AA (4.5:1) on every ground a screen puts it
     * on, measured AFTER the paper grain is drawn over both: the grain sits
     * over the content ([paperGrain]), so it moves glyph and page alike. The
     * tile's mean grey is 122 (measured on `paper_grain.webp`, 2026-10-17);
     * stamp red was darkened from #C0392B to pass, on the owner's pick.
     */
    @TestFactory
    fun `text reads at AA through the grain`(): List<DynamicTest> {
        val s = CaseFile
        val texts = mapOf(
            "onSurface" to s.onSurface, "onSurfaceVariant" to s.onSurfaceVariant,
            "primary" to s.primary, "secondary" to s.secondary, "tertiary" to s.tertiary, "error" to s.error,
        )
        val grounds = mapOf(
            "surface" to s.surface, "surfaceContainerLowest" to s.surfaceContainerLowest,
            "surfaceContainerLow" to s.surfaceContainerLow, "surfaceContainer" to s.surfaceContainer,
            "surfaceContainerHigh" to s.surfaceContainerHigh, "surfaceContainerHighest" to s.surfaceContainerHighest,
            "surfaceVariant" to s.surfaceVariant,
        )
        val filled = listOf(
            Triple("onPrimary", s.onPrimary, s.primary), Triple("onError", s.onError, s.error),
            Triple("onSecondaryContainer", s.onSecondaryContainer, s.secondaryContainer),
            Triple("onErrorContainer", s.onErrorContainer, s.errorContainer),
            Triple("onPrimaryContainer", s.onPrimaryContainer, s.primaryContainer),
        )
        val pairs = texts.flatMap { (t, fg) -> grounds.map { (g, bg) -> Triple("$t on $g", fg, bg) } } +
            filled.map { (t, fg, bg) -> Triple("$t on its fill", fg, bg) }
        return pairs.map { (name, fg, bg) ->
            DynamicTest.dynamicTest(name) {
                val ratio = contrast(grained(fg), grained(bg))
                assertTrue(ratio >= 4.5, "$name is ${"%.2f".format(ratio)}:1 through the grain")
            }
        }
    }

    /**
     * The launch window is painted from `res/values/colors.xml` before
     * Compose runs, so it repeats the page by hand. If the two drift, every
     * cold start flashes the wrong colour.
     */
    @Test
    fun `the launch background is the page`() {
        val xml = java.io.File("src/main/res/values/colors.xml").readText()
        val launch = Regex("""name="launch_background">#FF([0-9A-Fa-f]{6})<""").find(xml)!!.groupValues[1]
        assertEquals(CaseFile.background, Color(0xFF000000 or launch.toLong(16)))
    }

    @Test
    fun `the grain check can fail`() {
        // The mockup's own red, which measured 4.43:1 on paper through the
        // grain: proof the check above is not vacuous.
        assertTrue(contrast(grained(Color(0xFFC0392B)), grained(PAPER)) < 4.5)
    }

    private fun grained(c: Color): Color {
        val a = GRAIN_ALPHA
        val g = GRAIN_MEAN / 255f
        return Color(c.red * (1 - a) + g * a, c.green * (1 - a) + g * a, c.blue * (1 - a) + g * a)
    }

    private fun contrast(a: Color, b: Color): Float {
        val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (hi + 0.05f) / (lo + 0.05f)
    }

    private companion object {
        val PAPER = Color(0xFFF3EFE6)
        val CARD = Color(0xFFFBF8F1)
        val INK = Color(0xFF1A1A1A)
        val MUTE = Color(0xFF6B6458)
        val RULE = Color(0xFFD8D0BF)
        val STAMP = Color(0xFFBC382A)
        const val GRAIN_MEAN = 122f
    }
}
