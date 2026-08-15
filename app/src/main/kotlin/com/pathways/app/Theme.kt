package com.pathways.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The app's design system: one colour scheme per mode, one type scale, one
 * shape scale.
 *
 * Until this file existed the app called `MaterialTheme { }` with no
 * arguments, which is baseline Material 3 -- the stock purple, light only,
 * over a 2014 platform theme. Nothing here changes a rule or a number; it is
 * presentation only.
 *
 * **Colours are derived, not copied.** The ramps below are read off the
 * Zenit-241 pixel-art palette that shipped with one of the downloaded asset
 * packs, and retyped here as Kotlin constants. No palette file and no PNG
 * enters this repo, so those packs' licence terms are not engaged.
 *
 * The two schemes differ in temperature rather than being one scheme
 * lightened: dark is aubergine-and-brass (the setting's gaslamp-occult
 * register), light is parchment-and-sepia (an old book). Taking one hue
 * family and inverting it produces a light mode that reads as a bug.
 */

// ---------------------------------------------------------------------------
// Palette -- Zenit-241 entries, named for the role they play here.
// The trailing comment is the palette's own index, so a colour can be traced
// back to the ramp it came from rather than guessed at.
// ---------------------------------------------------------------------------

private val Ink = Color(0xFF120E14)           // 1   near-black, purple cast
private val Aubergine = Color(0xFF23172D)     // 217 deep surface
private val AubergineHigh = Color(0xFF362F3C) // 218 raised surface
private val Slate = Color(0xFF504758)         // 219 outline variant, dark
private val Pewter = Color(0xFF6D6778)        // 220 outline, dark
private val Mist = Color(0xFFBDB5C8)          // 222 secondary text, dark
private val Bone = Color(0xFFE2D3CF)          // 7   primary text, dark

private val Brass = Color(0xFFF9C72F)         // 102 primary accent, dark
private val BrassDeep = Color(0xFF7B5122)     // 95  primary container, dark
private val BrassDim = Color(0xFF3D1F0C)      // 97  on-primary, dark
private val BrassPale = Color(0xFFFFE488)     // 91  on-primary-container

private val Arcane = Color(0xFFCD88F2)        // 210 secondary accent, dark
private val ArcaneDeep = Color(0xFF59288A)    // 207 secondary container, dark
private val ArcaneDim = Color(0xFF2E0931)     // 37  on-secondary, dark
private val ArcanePale = Color(0xFFE4B7EE)    // 211 on-secondary-container

private val Verdigris = Color(0xFF62F9B9)     // 162 tertiary accent, dark
private val VerdigrisDeep = Color(0xFF186B47) // 147 tertiary container, dark
private val VerdigrisDim = Color(0xFF0A2B1C)  // 145 on-tertiary, dark
private val VerdigrisPale = Color(0xFFB6FEDC) // 163 on-tertiary-container

private val Blood = Color(0xFFF06260)         // 57  error, dark
private val BloodDeep = Color(0xFF65142B)     // 60  error container, dark
private val BloodDim = Color(0xFF3A0C1B)      // 61  on-error, dark
private val BloodPale = Color(0xFFFF9999)     // 54  on-error-container

private val Parchment = Color(0xFFE2D3CF)     // 7   container, light
private val ParchmentDeep = Color(0xFFC9ADA3) // 8   high container, light
private val ParchmentEdge = Color(0xFFC2B1A9) // 6   surface variant, light
private val Sepia = Color(0xFF726361)         // 4   outline, light
private val Umber = Color(0xFF2B1210)         // 73  primary text, light
private val UmberSoft = Color(0xFF4A1F1E)     // 72  secondary text, light

private val BrassInk = Color(0xFF592F15)      // 108 primary, light
private val BrassLight = Color(0xFFF0D399)    // 79  primary container, light
private val ArcaneInk = Color(0xFF4F1661)     // 36  secondary, light
private val ArcaneLight = Color(0xFFD9CAFF)   // 199 secondary container, light
private val VerdigrisInk = Color(0xFF135E71)  // 167 tertiary, light
private val VerdigrisLight = Color(0xFFBAF3EA) // 175 tertiary container, light
private val BloodInk = Color(0xFF951F1C)      // 63  error, light
private val BloodLight = Color(0xFFF0C5B3)    // 55  error container, light

// ---------------------------------------------------------------------------
// Schemes
// ---------------------------------------------------------------------------

private val DarkScheme = darkColorScheme(
    primary = Brass,
    onPrimary = BrassDim,
    primaryContainer = BrassDeep,
    onPrimaryContainer = BrassPale,
    secondary = Arcane,
    onSecondary = ArcaneDim,
    secondaryContainer = ArcaneDeep,
    onSecondaryContainer = ArcanePale,
    tertiary = Verdigris,
    onTertiary = VerdigrisDim,
    tertiaryContainer = VerdigrisDeep,
    onTertiaryContainer = VerdigrisPale,
    error = Blood,
    onError = BloodDim,
    errorContainer = BloodDeep,
    onErrorContainer = BloodPale,
    background = Ink,
    onBackground = Bone,
    surface = Ink,
    onSurface = Bone,
    surfaceVariant = AubergineHigh,
    onSurfaceVariant = Mist,
    surfaceContainerLowest = Ink,
    surfaceContainerLow = Color(0xFF191320),
    surfaceContainer = Aubergine,
    surfaceContainerHigh = Color(0xFF2C2335),
    surfaceContainerHighest = AubergineHigh,
    outline = Pewter,
    outlineVariant = Slate,
    inverseSurface = Bone,
    inverseOnSurface = Ink,
    inversePrimary = BrassInk,
    scrim = Color(0xFF000000),
)

private val LightScheme = lightColorScheme(
    primary = BrassInk,
    onPrimary = Color(0xFFFFF3D6),
    primaryContainer = BrassLight,
    onPrimaryContainer = BrassDim,
    secondary = ArcaneInk,
    onSecondary = Color(0xFFF6ECFF),
    secondaryContainer = ArcaneLight,
    onSecondaryContainer = ArcaneDim,
    tertiary = VerdigrisInk,
    onTertiary = Color(0xFFE8FBFF),
    tertiaryContainer = VerdigrisLight,
    onTertiaryContainer = Color(0xFF071B25),
    error = BloodInk,
    onError = Color(0xFFFFF0EA),
    errorContainer = BloodLight,
    onErrorContainer = BloodDim,
    background = Color(0xFFF3EAE4),
    onBackground = Umber,
    surface = Color(0xFFF3EAE4),
    onSurface = Umber,
    surfaceVariant = ParchmentEdge,
    onSurfaceVariant = UmberSoft,
    surfaceContainerLowest = Color(0xFFFFFAF6),
    surfaceContainerLow = Color(0xFFEDE3DC),
    surfaceContainer = Parchment,
    surfaceContainerHigh = Color(0xFFDACAC4),
    surfaceContainerHighest = ParchmentDeep,
    outline = Sepia,
    outlineVariant = ParchmentDeep,
    inverseSurface = Umber,
    inverseOnSurface = Parchment,
    inversePrimary = Brass,
    scrim = Color(0xFF000000),
)

// ---------------------------------------------------------------------------
// Type scale
// ---------------------------------------------------------------------------

/**
 * The scale is redefined here rather than at the call sites, and that is the
 * point of it.
 *
 * A census of the 16 UI files found **155 of 281** type calls (55%) using
 * `bodySmall`, whose Material default is 12sp -- the caption size. Only 8
 * used `bodyMedium`, the actual body size. At that ratio `bodySmall` is not
 * a deliberate caption choice repeated 155 times; it is the style the first
 * screen reached for and every later screen copied. So the fix is to make
 * the style readable, which is one file and no call-site diff, rather than
 * to rewrite 155 call sites and then review the result.
 *
 * The trade-off, stated because it is real: any genuinely deliberate caption
 * grows too. At 55% usage that is the minority case.
 *
 * Tracking is tighter than Material's defaults on the small sizes. Material
 * tracks small text loosely for glanceable labels; this app renders dense
 * tabular rules text, where loose tracking costs width the numbers need.
 */
private val PathwaysType = Typography().run {
    copy(
        // Body -- the reading sizes. bodySmall carries the app.
        bodySmall = bodySmall.copy(
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.15.sp,
        ),
        bodyMedium = bodyMedium.copy(
            fontSize = 15.sp,
            lineHeight = 21.sp,
            letterSpacing = 0.15.sp,
        ),
        bodyLarge = bodyLarge.copy(
            fontSize = 16.sp,
            lineHeight = 24.sp,
        ),
        // Titles -- row headers and dialog headings.
        titleSmall = titleSmall.copy(
            fontSize = 15.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.sp,
        ),
        titleMedium = titleMedium.copy(
            fontSize = 17.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.sp,
        ),
        titleLarge = titleLarge.copy(
            fontSize = 21.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.SemiBold,
        ),
        headlineSmall = headlineSmall.copy(
            fontSize = 25.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.SemiBold,
        ),
        // Labels -- buttons and chips.
        labelMedium = labelMedium.copy(fontSize = 13.sp, lineHeight = 17.sp),
        labelLarge = labelLarge.copy(fontSize = 15.sp, lineHeight = 20.sp),
    )
}

/**
 * A monospaced style for rolled totals and target numbers.
 *
 * ponytail: not part of the Material scale, because it answers a question the
 * scale does not ask -- "do these digits stay put when the number changes".
 * A plain val; promote to a CompositionLocal if a second theme ever varies it.
 */
val TabularNumerals: TextStyle
    @Composable get() = MaterialTheme.typography.titleMedium.copy(
        fontFamily = FontFamily.Monospace,
    )

// ---------------------------------------------------------------------------
// Shape scale
// ---------------------------------------------------------------------------

/**
 * Rounder than Material's defaults, following the direction Material 3
 * Expressive took in 1.4. The Expressive entry points themselves
 * (`MaterialExpressiveTheme`) are deliberately not used: they are
 * `@ExperimentalMaterial3ExpressiveApi`, and opting in across 16 files buys
 * motion physics this app has no animation to spend it on.
 */
private val PathwaysShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * Wraps the whole app. Follows the system's dark-mode setting; there is no
 * in-app override, because nothing yet asks for one.
 */
@Composable
fun PathwaysTheme(
    isDark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (isDark) DarkScheme else LightScheme,
        typography = PathwaysType,
        shapes = PathwaysShapes,
        content = content,
    )
}
