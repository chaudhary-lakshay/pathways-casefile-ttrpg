"""Generate `app/.../ThemeSchemes.kt` -- every Material 3 colour role, computed.

Run by hand when the preset list or the accent offsets change:

    py -m pip install materialyoucolor        # once; NOT a test dependency
    py tools/gen_theme.py

Deliberately not wired into `tools/test_tools_*.py`. That suite has
no third-party colour dependency, and an authoring tool run four times a year
should not add one to it. What guards the OUTPUT is the Kotlin compiler plus
`ThemeSchemesTest`, which re-checks the two invariants this file enforces.

Why generate rather than hand-pick
----------------------------------
The previous `Theme.kt` set 33 of Material's ~48 colour roles by hand, read off
a pixel-art palette. Two consequences, both measured:

* The 15 unset roles fell back to BASELINE Material tokens -- `surfaceBright`,
  `surfaceDim` and the whole `*Fixed` family. Those are the stock purple-tinted
  neutrals, so a scheme that never mentions purple was still serving it to any
  component that reads them.
* Hand-picking cannot honour the tonal contract. Material derives `onPrimary`
  from `primary` by a fixed tone delta; typing both in by eye means the pair is
  right only by luck.

`materialyoucolor` is Google's own material-color-utilities, ported to Python.
Running it here rather than at runtime means no Android dependency, no APK
growth, and no Kotlin metadata conflict -- MaterialKolor 5.0.0, the runtime
equivalent, is built with Kotlin 2.4.0 against this project's 2.2.0.

Why the accents rotate with the seed
------------------------------------
This app spends the accents SEMANTICALLY: combat is `error`, Beyonder material
is `secondary`, management is `tertiary` (see `ActionGroup.tintOf`). A seeded
scheme normally derives `secondary` at the seed's OWN hue, which would render
those three categories as three shades of one colour -- the distinction the
design direction exists to protect, deleted by the theming.

So each accent family is seeded separately, at a fixed offset from the theme
seed. The offsets are not invented: the palette this replaces already sat at
them. Brass is hue 90, its arcane purple 317 and its verdigris 165 -- exactly
+227 and +75. Generalising those offsets reproduces the old palette's hue
relationships at the default seed and guarantees separation at every other one.

`error` does NOT rotate. Red means danger in every theme, so it stays put, and
the cost is that a red-ish seed would collide with it. That is why SEEDS is not
a free colour picker: `check_separation` rejects any seed whose four category
hues come within MIN_SEPARATION of one another. Only two hue windows survive
(65-110 and 195-265); the presets below live inside them.
"""

from __future__ import annotations

import sys
from pathlib import Path

try:
    from materialyoucolor.hct import Hct
    from materialyoucolor.dynamiccolor.material_dynamic_colors import MaterialDynamicColors
    from materialyoucolor.scheme.scheme_neutral import SchemeNeutral
    from materialyoucolor.scheme.scheme_rainbow import SchemeRainbow
    from materialyoucolor.scheme.scheme_tonal_spot import SchemeTonalSpot
except ImportError:  # pragma: no cover - the message IS the handling
    sys.exit("materialyoucolor is not installed. Run: py -m pip install materialyoucolor")

OUT = Path("app/src/main/kotlin/com/pathways/app/ThemeSchemes.kt")

# Degrees on the HCT hue circle, measured off the palette this replaces.
SECONDARY_OFFSET = 227.0
TERTIARY_OFFSET = 75.0
# Material's error hue. Fixed: red means danger regardless of theme.
ERROR_HUE = 21.0
# Below this, two categories stop reading as different colours at a glance.
MIN_SEPARATION = 40.0

# Two scheme families, picked by measurement rather than by name.
#
# Seeded at brass, in dark mode, the seven families Material ships put
# `onSurface` -- the colour every rules table is written in -- at these chromas:
#
#     Rainbow  2.7    Neutral  2.8    Fidelity/Content 6.7    TonalSpot 8.3
#     Expressive 22.3    Vibrant 28.0
#
# Vibrant and Expressive tint the body text itself yellow, which the design
# direction rules out ("dense tabular text stays legible"). Of what remains,
# Rainbow is the only family that pairs a neutral ground with a VIVID accent --
# its `primary` comes out at chroma 47.9, the highest of the seven, against
# Neutral's 7.8. That pairing is the design direction stated as arithmetic:
# neutral chrome, colour only on what carries meaning.
#
# TonalSpot is kept as the second family because it is the one that puts a warm
# tint back into the surfaces, which is the atmosphere the previous palette was
# reaching for. Offering both is what gives the presets their variety: the two
# permitted hue windows alone would only ever yield two distinguishable themes.
STYLES = {"rainbow": SchemeRainbow, "tonal": SchemeTonalSpot, "neutral": SchemeNeutral}

# name -> (seed argb, style key, one-line rationale for the KDoc)
SEEDS: dict[str, tuple[int, str, str]] = {
    "Fool": (
        0xFFF9C72F, "rainbow",
        "Brass on neutral graphite. The app's own gold, and the launcher icon's.",
    ),
    "Gaslamp": (
        0xFFF9C72F, "tonal",
        "The same brass, but with the warmth left in the surfaces -- what the hand-picked "
        "aubergine palette was reaching for, derived rather than guessed.",
    ),
    "Tyrant": (
        0xFF3E86B8, "rainbow",
        "Steel blue on neutral graphite. The far side of the wheel from brass, and the "
        "coldest seed the accent offsets allow.",
    ),
    "Abyss": (
        0xFF00A6B1, "tonal",
        "Deep teal, surfaces tinted with it. Indigo was tried first and the separation "
        "guard rejected it: hue 291 puts its tertiary 27 degrees off the error red.",
    ),
}

# Presets drawn from a fixed palette rather than one seed rotated round the
# wheel: each accent family is seeded at its own colour, then the roles a
# screen actually paints are pinned to the palette's exact hex.
#
# Wada is Sanzo Wada, *A Dictionary of Color Combinations* (1933), combination
# #269 -- the owner's pick, 2026-10-11. Its four colours fill four of the five
# category slots: Raw Sienna is the primary action, Aconite Violet is Beyonder
# material (`secondary`), Pale Burnt Lake is combat (`error`), Black is the
# page. Management (`tertiary`) has no fifth colour, so it takes a warm stone
# neutral: two call sites, and it must not repeat an accent.
#
# The rotation's separation check does not apply: Raw Sienna and Pale Burnt
# Lake are ~35 degrees apart in hue, under MIN_SEPARATION, but far apart in
# tone, which is what separates them on screen. `check_pinned` checks that
# each text colour reads on every ground a screen puts it on instead.
PINNED: dict[str, dict] = {
    "Wada": {
        "style": "rainbow",
        # Rainbow lifts any seed to a vivid primary (chroma ~48), which turned
        # the stone seed into a saturated yellow no Wada swatch has. The stone
        # family is built Neutral so it stays a stone.
        "family_styles": {"tertiary": "neutral"},
        "seeds": {"primary": 0xFFBB7125, "secondary": 0xFFA36AA5,
                  "tertiary": 0xFF9A8F80, "error": 0xFF802626},
        "dark": {
            # Wada's own hex is 3.5-3.9:1 as text on a raised card (#2A2A2A),
            # under AA. Each accent is its own hue lifted to HCT tone ~61, the
            # lowest that clears 4.6:1 on surfaceContainerHigh: #CE8033 is
            # Raw Sienna, #B97EBB Aconite Violet, #E2706B Pale Burnt Lake.
            # Burnt Lake itself stays as the error container, for fills.
            "primary": 0xCE8033, "onPrimary": 0x111314,
            "secondary": 0xB97EBB, "onSecondary": 0x111314,
            "error": 0xE2706B, "onError": 0x111314,
            "errorContainer": 0x802626, "onErrorContainer": 0xECE6DC,
            "background": 0x111314, "onBackground": 0xECE6DC,
            "surface": 0x111314, "onSurface": 0xECE6DC,
            # 4.6:1 on surfaceVariant, the darkest ground it is read on.
            "onSurfaceVariant": 0xBDB6AD,
        },
        "light": {
            # Raw Sienna and Aconite Violet are 3.6:1 and 3.9:1 on a light
            # page, under AA for text; darkened to 5.3:1 and 5.8:1.
            "primary": 0x965A1D, "onPrimary": 0xFFFFFF,
            "secondary": 0x82508A, "onSecondary": 0xFFFFFF,
            "error": 0x802626, "onError": 0xFFFFFF,
        },
        "why": "Sanzo Wada's combination #269: Raw Sienna, Aconite Violet and Pale "
               "Burnt Lake on black. Default.",
    },
}

# Every role `androidx.compose.material3.ColorScheme` accepts, in the order the
# generated calls list them. `scrim` is not a MaterialDynamicColors entry -- it
# is always pure black -- so it is emitted separately.
ROLES = [
    "primary", "onPrimary", "primaryContainer", "onPrimaryContainer", "inversePrimary",
    "secondary", "onSecondary", "secondaryContainer", "onSecondaryContainer",
    "tertiary", "onTertiary", "tertiaryContainer", "onTertiaryContainer",
    "background", "onBackground",
    "surface", "onSurface", "surfaceVariant", "onSurfaceVariant", "surfaceTint",
    "surfaceBright", "surfaceDim",
    "surfaceContainerLowest", "surfaceContainerLow", "surfaceContainer",
    "surfaceContainerHigh", "surfaceContainerHighest",
    "inverseSurface", "inverseOnSurface",
    "error", "onError", "errorContainer", "onErrorContainer",
    "outline", "outlineVariant",
    "primaryFixed", "primaryFixedDim", "onPrimaryFixed", "onPrimaryFixedVariant",
    "secondaryFixed", "secondaryFixedDim", "onSecondaryFixed", "onSecondaryFixedVariant",
    "tertiaryFixed", "tertiaryFixedDim", "onTertiaryFixed", "onTertiaryFixedVariant",
]

# Roles taken from a rotated scheme's PRIMARY family rather than from the theme
# scheme. Mapping is by position in the family, so `secondaryContainer` comes
# from the rotated scheme's `primaryContainer`.
SECONDARY_FROM_PRIMARY = {
    "secondary": "primary",
    "onSecondary": "onPrimary",
    "secondaryContainer": "primaryContainer",
    "onSecondaryContainer": "onPrimaryContainer",
    "secondaryFixed": "primaryFixed",
    "secondaryFixedDim": "primaryFixedDim",
    "onSecondaryFixed": "onPrimaryFixed",
    "onSecondaryFixedVariant": "onPrimaryFixedVariant",
}
TERTIARY_FROM_PRIMARY = {
    "tertiary": "primary",
    "onTertiary": "onPrimary",
    "tertiaryContainer": "primaryContainer",
    "onTertiaryContainer": "onPrimaryContainer",
    "tertiaryFixed": "primaryFixed",
    "tertiaryFixedDim": "primaryFixedDim",
    "onTertiaryFixed": "onPrimaryFixed",
    "onTertiaryFixedVariant": "onPrimaryFixedVariant",
}


def separation(a: float, b: float) -> float:
    """Shortest arc between two hues, in degrees."""
    d = abs(a - b) % 360.0
    return min(d, 360.0 - d)


def category_hues(seed: int) -> dict[str, float]:
    """The four hues a seed puts on screen as distinct meanings."""
    h = Hct.from_int(seed).hue
    return {
        "primary": h,
        "secondary": (h + SECONDARY_OFFSET) % 360.0,
        "tertiary": (h + TERTIARY_OFFSET) % 360.0,
        "error": ERROR_HUE,
    }


def closest_pair(seed: int) -> float:
    """Degrees between the two nearest category hues -- the number to beat."""
    hues = category_hues(seed)
    return min(
        separation(hues[a], hues[b])
        for a in hues for b in hues if a < b
    )


def check_separation(name: str, seed: int) -> None:
    """Reject a seed whose categories would stop reading as different colours.

    This is the whole reason SEEDS is a curated list rather than a colour
    picker. Raising here is the point: a preset that fails is a preset that
    silently deletes the app's category encoding.
    """
    hues = category_hues(seed)
    for a in hues:
        for b in hues:
            if a >= b:
                continue
            gap = separation(hues[a], hues[b])
            if gap < MIN_SEPARATION:
                raise SystemExit(
                    f"seed '{name}' (#{seed & 0xFFFFFF:06X}) puts {a} and {b} "
                    f"{gap:.0f} degrees apart; {MIN_SEPARATION:.0f} is the minimum. "
                    f"Pick a hue in 65-110 or 195-265."
                )


def scheme_for(seed_hct: Hct, is_dark: bool, style: str):
    return STYLES[style](seed_hct, is_dark, 0.0)


def rotated(seed: int, offset: float) -> Hct:
    """The seed's chroma and tone, moved round the hue circle."""
    h = Hct.from_int(seed)
    return Hct.from_hct((h.hue + offset) % 360.0, h.chroma, h.tone)


def role_value(scheme, role: str) -> int:
    """One role's RGB, resolved through MaterialDynamicColors."""
    colour = getattr(MaterialDynamicColors, role)
    r, g, b = colour.get_hct(scheme).to_rgba()[:3]
    return (r << 16) | (g << 8) | b


def build(seed: int, is_dark: bool, style: str) -> dict[str, int]:
    """Every role for one preset in one mode, accents seeded separately."""
    base = scheme_for(Hct.from_int(seed), is_dark, style)
    sec = scheme_for(rotated(seed, SECONDARY_OFFSET), is_dark, style)
    ter = scheme_for(rotated(seed, TERTIARY_OFFSET), is_dark, style)

    out: dict[str, int] = {}
    for role in ROLES:
        if role in SECONDARY_FROM_PRIMARY:
            out[role] = role_value(sec, SECONDARY_FROM_PRIMARY[role])
        elif role in TERTIARY_FROM_PRIMARY:
            out[role] = role_value(ter, TERTIARY_FROM_PRIMARY[role])
        else:
            out[role] = role_value(base, role)
    return out


def build_pinned(spec: dict, is_dark: bool) -> dict[str, int]:
    """Every role for a pinned preset: each family from its own seed, then pins."""
    seeds = spec["seeds"]
    style = spec["style"]
    styles = spec.get("family_styles", {})
    fam = {k: scheme_for(Hct.from_int(v), is_dark, styles.get(k, style)) for k, v in seeds.items()}
    error_from_primary = {
        "error": "primary", "onError": "onPrimary",
        "errorContainer": "primaryContainer", "onErrorContainer": "onPrimaryContainer",
    }
    out: dict[str, int] = {}
    for role in ROLES:
        if role in SECONDARY_FROM_PRIMARY:
            out[role] = role_value(fam["secondary"], SECONDARY_FROM_PRIMARY[role])
        elif role in TERTIARY_FROM_PRIMARY:
            out[role] = role_value(fam["tertiary"], TERTIARY_FROM_PRIMARY[role])
        elif role in error_from_primary:
            out[role] = role_value(fam["error"], error_from_primary[role])
        else:
            out[role] = role_value(fam["primary"], role)
    out.update(spec["dark" if is_dark else "light"])
    return out


def contrast(a: int, b: int) -> float:
    """WCAG contrast ratio of two RGB ints."""
    def lum(c: int) -> float:
        ch = [((c >> sh) & 0xFF) / 255 for sh in (16, 8, 0)]
        ch = [x / 12.92 if x <= 0.03928 else ((x + 0.055) / 1.055) ** 2.4 for x in ch]
        return 0.2126 * ch[0] + 0.7152 * ch[1] + 0.0722 * ch[2]
    hi, lo = sorted((lum(a), lum(b)), reverse=True)
    return (hi + 0.05) / (lo + 0.05)


def check_pinned(name: str, roles: dict[str, int], mode: str) -> None:
    """Every pinned text colour must clear AA (4.5:1) on every ground it is read on.

    The grounds are the page and the three containers a card sits on; checking
    the page alone passed three accents that read at 3.5-3.9:1 on a card.
    """
    grounds = ("surface", "surfaceContainerLow", "surfaceContainer", "surfaceContainerHigh")
    pairs = [(accent, g) for accent in ("primary", "secondary", "tertiary", "error", "onSurfaceVariant")
             for g in grounds]
    pairs += [("onSurfaceVariant", "surfaceVariant"), ("onPrimary", "primary"),
              ("onSecondary", "secondary"), ("onError", "error"),
              ("onErrorContainer", "errorContainer")]
    for fg, bg in pairs:
        ratio = contrast(roles[fg], roles[bg])
        if ratio < 4.5:
            raise SystemExit(
                f"pinned preset '{name}' {mode}: {fg} is {ratio:.2f}:1 on {bg}; 4.5 is the minimum"
            )


def render_scheme(kotlin_name: str, roles: dict[str, int], is_dark: bool) -> str:
    fn = "darkColorScheme" if is_dark else "lightColorScheme"
    lines = [f"private val {kotlin_name} = {fn}("]
    for role in ROLES:
        lines.append(f"    {role} = Color(0xFF{roles[role]:06X}),")
    lines.append("    scrim = Color(0xFF000000),")
    lines.append(")")
    return "\n".join(lines)


HEADER = '''package com.pathways.app

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * GENERATED by `tools/gen_theme.py`. Do not edit by hand -- rerun the tool.
 *
 * Every Material 3 colour role for every preset, in both modes. Nothing is
 * left to Material's baseline tokens, which is the defect this file exists to
 * close: the palette it replaces set 33 of ~48 roles, and the 15 it missed
 * resolved to the stock purple-tinted neutrals.
 *
 * The three accent families are seeded at fixed hue offsets from the theme
 * seed rather than derived from it, because this app spends them semantically
 * -- combat is `error`, Beyonder material is `secondary`, management is
 * `tertiary`. See the generator's docstring for why the offsets are +227 and
 * +75, and why `error` does not move.
 */
'''


def main() -> None:
    for name, (seed, _, _) in SEEDS.items():
        check_separation(name, seed)

    parts = [HEADER]
    for name, spec in PINNED.items():
        dark, light = build_pinned(spec, True), build_pinned(spec, False)
        check_pinned(name, dark, "dark")
        check_pinned(name, light, "light")
        parts.append(
            f"/**\n * {spec['why']}\n *\n"
            " * Pinned, not rotated: see the generator's PINNED.\n */"
        )
        parts.append(render_scheme(f"{name}Dark", dark, True))
        parts.append(render_scheme(f"{name}Light", light, False))
    for name, (seed, style, why) in SEEDS.items():
        hues = category_hues(seed)
        parts.append(
            f"/**\n * {why}\n *\n"
            f" * Seed #{seed & 0xFFFFFF:06X}, hue {hues['primary']:.0f}, "
            f"{STYLES[style].__name__}. Closest pair of\n"
            f" * category hues is {closest_pair(seed):.0f} degrees apart "
            f"(minimum {MIN_SEPARATION:.0f}).\n */"
        )
        parts.append(render_scheme(f"{name}Dark", build(seed, True, style), True))
        parts.append(render_scheme(f"{name}Light", build(seed, False, style), False))

    entries = "\n".join(
        f'    {name.upper()}("{name}", {name}Light, {name}Dark),' for name in [*PINNED, *SEEDS]
    )
    parts.append(
        "/**\n"
        " * The presets offered in the theme picker.\n"
        " *\n"
        " * A curated list rather than a colour picker, and the generator enforces\n"
        " * why: only hues in 65-110 and 195-265 keep all four category accents at\n"
        f" * least {MIN_SEPARATION:.0f} degrees apart once the fixed error red is counted.\n"
        " */\n"
        "enum class ThemeSeed(\n"
        "    val label: String,\n"
        "    val light: ColorScheme,\n"
        "    val dark: ColorScheme,\n"
        ") {\n"
        f"{entries}\n"
        "}"
    )

    OUT.write_text("\n\n".join(parts) + "\n", encoding="utf-8")
    print(f"wrote {OUT} -- {len(PINNED) + len(SEEDS)} presets, {len(ROLES) + 1} roles each, both modes")


if __name__ == "__main__":
    main()
