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
     * The roles the previous hand-written palette never set.
     *
     * This is the defect the generator exists to close, so it is pinned by
     * name. `darkColorScheme()` and `lightColorScheme()` default each of these
     * to a BASELINE Material token -- the stock purple-tinted neutrals -- and
     * the old `Theme.kt` accepted every one of those defaults. A preset that
     * matches the baseline here has stopped generating that role.
     */
    private val previouslyUnset: List<Pair<String, (ColorScheme) -> Color>> = listOf(
        "surfaceBright" to { s: ColorScheme -> s.surfaceBright },
        "surfaceDim" to { s: ColorScheme -> s.surfaceDim },
        "primaryFixed" to { s: ColorScheme -> s.primaryFixed },
        "primaryFixedDim" to { s: ColorScheme -> s.primaryFixedDim },
        "onPrimaryFixed" to { s: ColorScheme -> s.onPrimaryFixed },
        "onPrimaryFixedVariant" to { s: ColorScheme -> s.onPrimaryFixedVariant },
        "secondaryFixed" to { s: ColorScheme -> s.secondaryFixed },
        "secondaryFixedDim" to { s: ColorScheme -> s.secondaryFixedDim },
        "onSecondaryFixed" to { s: ColorScheme -> s.onSecondaryFixed },
        "onSecondaryFixedVariant" to { s: ColorScheme -> s.onSecondaryFixedVariant },
        "tertiaryFixed" to { s: ColorScheme -> s.tertiaryFixed },
        "tertiaryFixedDim" to { s: ColorScheme -> s.tertiaryFixedDim },
        "onTertiaryFixed" to { s: ColorScheme -> s.onTertiaryFixed },
        "onTertiaryFixedVariant" to { s: ColorScheme -> s.onTertiaryFixedVariant },
    )

    @TestFactory
    fun `no preset leaves a role at Materials baseline token`(): List<DynamicTest> {
        val baselineDark = darkColorScheme()
        val baselineLight = lightColorScheme()
        return ThemeSeed.entries.flatMap { seed ->
            listOf("dark" to (seed.dark to baselineDark), "light" to (seed.light to baselineLight))
                .flatMap { (mode, pair) ->
                    val (scheme, baseline) = pair
                    previouslyUnset.map { (role, read) ->
                        DynamicTest.dynamicTest("${seed.name} $mode $role") {
                            assertNotEquals(
                                read(baseline), read(scheme),
                                "$role still resolves to Material's baseline token, which is " +
                                    "the purple-tinted neutral this generator exists to replace",
                            )
                        }
                    }
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
