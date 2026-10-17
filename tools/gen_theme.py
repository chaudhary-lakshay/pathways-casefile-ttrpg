"""Generate `app/.../ThemeSchemes.kt` -- every Material 3 colour role, computed.

Run by hand when a token changes:

    py -m pip install materialyoucolor        # once; NOT a test dependency
    py tools/gen_theme.py

Deliberately not wired into `tools/test_tools_*.py`. That suite has no
third-party colour dependency, and an authoring tool run a few times a year
should not add one to it. What guards the OUTPUT is the Kotlin compiler plus
`ThemeSchemesTest`, which types the design's tokens in independently.

One scheme, Case File, light only (the owner, 2026-10-17: it replaces every
preset, and there is no dark mode). Until then this file generated six presets
in two modes from seeds rotated round the hue wheel; git history has it.

Why generate at all
-------------------
The design names six colours. Material has ~48 roles, and a role the scheme
does not set falls back to Material's BASELINE token, the stock purple-tinted
neutrals -- measured in this app before generation (15 of 48 unset). So every
role is computed from `materialyoucolor` (Google's material-color-utilities,
ported to Python), and the roles a screen paints are then pinned to the
design's exact hex.
"""

from __future__ import annotations

import sys
from pathlib import Path

try:
    from materialyoucolor.hct import Hct
    from materialyoucolor.dynamiccolor.material_dynamic_colors import MaterialDynamicColors
    from materialyoucolor.scheme.scheme_neutral import SchemeNeutral
except ImportError:  # pragma: no cover - the message IS the handling
    sys.exit("materialyoucolor is not installed. Run: py -m pip install materialyoucolor")

OUT = Path("app/src/main/kotlin/com/pathways/app/ThemeSchemes.kt")

# The Case File tokens (UX Pilot's "Beyonder Codex - Case File", Figma
# oVd3fSYYgtVQT0NXdRdY0x). Stamp red is the design's #C0392B darkened 2% to
# clear AA on paper through the grain; the owner's pick, 2026-10-17.
PAPER, CARD, INK, MUTE, RULE, STAMP = 0xF3EFE6, 0xFBF8F1, 0x1A1A1A, 0x6B6458, 0xD8D0BF, 0xBC382A
# Between card and paper: a raised ground that is still no darker than the
# page. Darker container tones (#EFE9DC, #E8E1D2) put mute and stamp under
# 4.5:1 through the grain, so no ground that carries text goes below paper.
RAISED = 0xF6F2EA
# A background that carries no text: the dimmest page.
DIM = 0xE8E1D2

# The paper grain is drawn OVER the content (`paperGrain` in Theme.kt), so it
# moves glyph and page alike. Its tile's mean grey and the alpha Theme.kt
# draws it at; ThemeSchemesTest re-checks with the same two numbers.
GRAIN_MEAN, GRAIN_ALPHA = 122, 0.07

PINS = {
    "background": PAPER, "onBackground": INK,
    "surface": PAPER, "onSurface": INK, "surfaceBright": PAPER, "surfaceDim": DIM,
    "surfaceVariant": PAPER, "onSurfaceVariant": MUTE, "surfaceTint": CARD,
    "surfaceContainerLowest": CARD, "surfaceContainerLow": CARD, "surfaceContainer": CARD,
    "surfaceContainerHigh": RAISED, "surfaceContainerHighest": PAPER,
    "inverseSurface": INK, "inverseOnSurface": PAPER, "inversePrimary": PAPER,
    # Ink is the one action colour. Anything filled with it is written in
    # paper, `onPrimary` included: Compose resolves a Surface's content colour
    # through `primary` first when two roles share ink (Codex's review).
    "primary": INK, "onPrimary": PAPER,
    # A selected FilterChip fills with secondaryContainer: the design's black chip.
    "secondaryContainer": INK, "onSecondaryContainer": PAPER,
    # The Beyonder's colour (SPI, a potion's grant, the mystical actions) is
    # the stamp; management is the mute grey.
    "secondary": STAMP, "onSecondary": PAPER,
    "tertiary": MUTE, "onTertiary": PAPER,
    "error": STAMP, "onError": PAPER,
    "outline": INK, "outlineVariant": RULE,
}

# Every role `androidx.compose.material3.ColorScheme` accepts, in the order the
# generated call lists them. `scrim` is always pure black, emitted separately.
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

# Each unpinned family from its own seed: the containers and Fixed roles of
# the warm grey, and of the stamp for error and secondary.
FAMILY_SEED = {"primary": MUTE, "secondary": STAMP, "tertiary": MUTE, "error": STAMP}


def role_value(scheme, role: str) -> int:
    """One role's RGB, resolved through MaterialDynamicColors."""
    r, g, b = getattr(MaterialDynamicColors, role).get_hct(scheme).to_rgba()[:3]
    return (r << 16) | (g << 8) | b


def neutral(seed: int):
    return SchemeNeutral(Hct.from_int(0xFF000000 | seed), False, 0.0)


def family_of(role: str) -> str | None:
    """`onSecondaryFixed` -> `secondary`; None for a neutral role."""
    bare = role[2].lower() + role[3:] if role.startswith("on") else role
    return next((f for f in FAMILY_SEED if bare.startswith(f)), None)


def build() -> dict[str, int]:
    """Every role: each family from its seed's Neutral scheme, then the pins."""
    base = neutral(PAPER)
    out: dict[str, int] = {}
    for role in ROLES:
        family = family_of(role)
        if family is None:
            out[role] = role_value(base, role)
            continue
        # The seed's own Neutral scheme, read at the PRIMARY position.
        primary_role = role.replace(family, "primary").replace(family.capitalize(), "Primary")
        out[role] = role_value(neutral(FAMILY_SEED[family]), primary_role)
    out.update(PINS)
    return out


def grained(c: int) -> tuple[float, ...]:
    return tuple(((c >> sh) & 0xFF) * (1 - GRAIN_ALPHA) + GRAIN_MEAN * GRAIN_ALPHA for sh in (16, 8, 0))


def contrast(a: int, b: int) -> float:
    """WCAG contrast of two RGB ints, both seen through the grain."""
    def lum(c: int) -> float:
        ch = [x / 255 for x in grained(c)]
        ch = [x / 12.92 if x <= 0.03928 else ((x + 0.055) / 1.055) ** 2.4 for x in ch]
        return 0.2126 * ch[0] + 0.7152 * ch[1] + 0.0722 * ch[2]
    hi, lo = sorted((lum(a), lum(b)), reverse=True)
    return (hi + 0.05) / (lo + 0.05)


def check(roles: dict[str, int]) -> None:
    """Every text colour clears AA (4.5:1) on every ground it is read on, through the grain."""
    texts = ("onSurface", "onSurfaceVariant", "primary", "secondary", "tertiary", "error")
    grounds = ("surface", "surfaceContainerLowest", "surfaceContainerLow", "surfaceContainer",
               "surfaceContainerHigh", "surfaceContainerHighest", "surfaceVariant")
    pairs = [(t, g) for t in texts for g in grounds]
    pairs += [("onPrimary", "primary"), ("onError", "error"), ("onSecondaryContainer", "secondaryContainer"),
              ("onErrorContainer", "errorContainer"), ("onPrimaryContainer", "primaryContainer")]
    for fg, bg in pairs:
        ratio = contrast(roles[fg], roles[bg])
        if ratio < 4.5:
            raise SystemExit(f"{fg} is {ratio:.2f}:1 on {bg} through the grain; 4.5 is the minimum")


HEADER = '''package com.pathways.app

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * GENERATED by `tools/gen_theme.py`. Do not edit by hand -- rerun the tool.
 *
 * The Case File scheme: every Material 3 colour role, light only. Nothing is
 * left to Material's baseline tokens, the stock purple-tinted neutrals.
 */
'''


def main() -> None:
    roles = build()
    check(roles)
    lines = ["internal val CaseFile: ColorScheme = lightColorScheme("]
    lines += [f"    {role} = Color(0xFF{roles[role]:06X})," for role in ROLES]
    lines += ["    scrim = Color(0xFF000000),", ")"]
    OUT.write_text(HEADER + "\n" + "\n".join(lines) + "\n", encoding="utf-8")
    print(f"wrote {OUT} -- {len(ROLES) + 1} roles")


if __name__ == "__main__":
    main()
