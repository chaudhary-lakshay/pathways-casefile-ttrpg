package com.pathways.app

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

/**
 * Guards the output of `tools/gen_theme.py`.
 *
 * The generator is not run by the build -- it needs a Python package that is
 * deliberately not a test dependency -- so `ThemeSchemes.kt` is a checked-in
 * artefact like any extracted content file. These tests are what stops it
 * drifting from the promises it makes.
 */
class ThemeSchemesTest {

    /**
     * Every `Color` role `ColorScheme` exposes, found by reflection.
     *
     * Reflection rather than a list of names, because the defect class is not
     * "these 14 roles". It is "any role missing from the generator's ROLES list
     * silently falls back to a baseline token" -- so a hand-written list of the
     * roles that were missing LAST time cannot see the next one. If Material
     * adds a role, or someone trims ROLES, this sweep notices and a name list
     * would not.
     */
    private val roleReaders: List<Pair<String, (ColorScheme) -> Color>> =
        ColorScheme::class.members
            .filterIsInstance<kotlin.reflect.KProperty1<ColorScheme, *>>()
            .filter { it.returnType.classifier == Color::class }
            .sortedBy { it.name }
            .map { prop -> prop.name to { s: ColorScheme -> prop.get(s) as Color } }

    /**
     * A role whose value is an ENDPOINT of the tonal ramp -- pure white or pure
     * black -- carries no evidence either way.
     *
     * Tone 100 is white and tone 0 is black in every Material scheme that has
     * ever been generated, so a preset agreeing with the baseline there is
     * agreement, not a fallback. Measured: `onPrimary`, `onSecondary`,
     * `onTertiary`, `onError` and `surfaceContainerLowest` all come out
     * #FFFFFF in the light Rainbow presets. The TonalSpot ones do not -- theirs
     * are tinted (#FFF8EF) -- which is what confirms this is about the ramp and
     * not about a role going missing.
     *
     * **Stated limitation.** This means the sweep cannot tell agreement from
     * absence at the endpoints: a role dropped from the generator's ROLES list
     * whose baseline happens to be white would pass. `endpointsStayRare` is the
     * backstop -- if a generator bug flattened a scheme, the count would jump
     * and that test fails even though each individual role is excused.
     */
    private fun isRampEndpoint(c: Color): Boolean = c == Color.White || c == Color.Black

    /** `scrim` is black by definition in every scheme, in both modes. */
    private val mayMatchBaseline = setOf("scrim")

    @Test
    fun `the reflective sweep actually finds the roles`() {
        // A sweep that silently found nothing would make every assertion below
        // vacuous, and it would still report green.
        assertTrue(
            roleReaders.size >= 45,
            "only ${roleReaders.size} Color roles found on ColorScheme; the reflection broke",
        )
    }

    @TestFactory
    fun `no preset leaves any role at Materials baseline token`(): List<DynamicTest> {
        val baselineDark = darkColorScheme()
        val baselineLight = lightColorScheme()
        return ThemeSeed.entries.flatMap { seed ->
            listOf("dark" to (seed.dark to baselineDark), "light" to (seed.light to baselineLight))
                .flatMap { (mode, pair) ->
                    val (scheme, baseline) = pair
                    roleReaders
                        .filterNot { (role, _) -> role in mayMatchBaseline }
                        .filterNot { (_, read) -> isRampEndpoint(read(scheme)) }
                        .map { (role, read) ->
                            DynamicTest.dynamicTest("${seed.name} $mode $role") {
                                assertNotEquals(
                                    read(baseline), read(scheme),
                                    "$role resolves to Material's baseline token. Either the " +
                                        "generator stopped emitting it, or ColorScheme grew a " +
                                        "role that tools/gen_theme.py's ROLES list does not know",
                                )
                            }
                        }
                }
        }
    }

    @Test
    fun `endpointsStayRare`() {
        // The backstop for the exclusion above. Excusing white and black is
        // safe only while few roles ARE white or black; a generator bug that
        // flattened a scheme would excuse itself into a green run otherwise.
        // Measured at the time of writing: 5 in the light Rainbow presets,
        // 1 (scrim) elsewhere.
        for (seed in ThemeSeed.entries) {
            for ((mode, scheme) in listOf("dark" to seed.dark, "light" to seed.light)) {
                val endpoints = roleReaders.count { (_, read) -> isRampEndpoint(read(scheme)) }
                assertTrue(
                    endpoints <= 8,
                    "$endpoints roles are pure white or black in ${seed.name} $mode; the " +
                        "baseline-token sweep excuses all of them, so this scheme is barely " +
                        "being checked. Expected at most 8.",
                )
            }
        }
    }

    /**
     * The guarantee the picker advertises with four swatches per row.
     *
     * This app spends the accents semantically -- combat is `error`, Beyonder
     * material is `secondary`, management is `tertiary`. Two of them landing on
     * the same colour does not break a layout or fail a contrast check; it
     * silently deletes a distinction, which is why it needs a test rather than
     * an eye.
     */
    @TestFactory
    fun `every preset keeps its four category accents distinct`(): List<DynamicTest> =
        ThemeSeed.entries.flatMap { seed ->
            listOf("dark" to seed.dark, "light" to seed.light).map { (mode, s) ->
                DynamicTest.dynamicTest("${seed.name} $mode") {
                    val accents = mapOf(
                        "primary" to s.primary,
                        "secondary" to s.secondary,
                        "tertiary" to s.tertiary,
                        "error" to s.error,
                    )
                    assertTrue(
                        accents.values.toSet().size == accents.size,
                        "two categories share a colour in ${seed.name} $mode: $accents",
                    )
                }
            }
        }

    @Test
    fun `light and dark are different schemes`() {
        // Cheap, but it is the failure a copy-paste in the generator's render
        // step would produce, and every other assertion here would still pass.
        for (seed in ThemeSeed.entries) {
            assertNotEquals(
                seed.light.background, seed.dark.background,
                "${seed.name} uses one background for both modes",
            )
        }
    }

    @Test
    fun `the default preset is the one the launch background approximates`() {
        // res/values/colors.xml hardcodes FOOL's backgrounds, because a platform
        // theme is resolved before any preference can be read. If the default
        // moves and that file does not, every cold start flashes.
        assertTrue(
            ThemePrefs().seed == ThemeSeed.FOOL,
            "the default preset changed; update launch_background in " +
                "res/values/colors.xml and res/values-night/colors.xml to match",
        )
    }
}
