package com.pathways.app

import androidx.annotation.DrawableRes

/**
 * The sigil for each of the 22 pathways.
 *
 * **What these are.** The pathway emblems from the Lord of the Mysteries wiki
 * (`<Pathway> Symbol2.png`), one per pathway, restyled flat on the owner's
 * pick (2026-10-17, style "C" of three): the faint halo under alpha 128 cut
 * and islands under 40 px dropped, posterised to 2 bits per channel and
 * mode-filtered, then a #111314 outline. All of it runs at 4x (960 px art,
 * a 12 px outline) and is scaled to 256 px once at the end: done at 256 the
 * edges came out grainy on the tablet. WebP quality 92, alpha lossless. Each
 * keeps its own hue. They are the setting's OWN symbols -- a first
 * attempt used the Rider-Waite tarot Major Arcana on the reasoning that the 22
 * pathways are the 22 arcana, and it was rejected for the obvious reason: a
 * 1909 tarot card is not Lord of the Mysteries.
 *
 * **The licence, recorded rather than glossed.** These are NOT public domain.
 * Lord of the Mysteries is Cuttlefish That Loves Diving's, its official art is
 * the publisher's, and wiki uploads are hosted as fair use rather than under a
 * licence that permits redistribution. Committing them here is a deliberate
 * decision by the repository's owner, taken after the position above was put
 * to them, for a personal non-commercial project. It is recorded here so that
 * the next reader knows it was a choice and not an oversight -- and so that
 * anyone packaging this for distribution knows to strip `res/drawable` first.
 *
 * There is no mapping question any more: each pathway has its own symbol, so
 * nothing here is a reading. That was the other cost of the tarot attempt --
 * half its assignments were interpretation, and `Darkness` got Temperance
 * purely because it was the card left over.
 *
 * A missing name returns null and the caller draws no sigil. That is
 * deliberate: `NonStandardPathways` knows of pathways the potion tables do not
 * print, and an art lookup must never be the thing that decides what exists.
 */
@DrawableRes
fun pathwayArt(pathway: String): Int? = when (pathway) {
    "Fool" -> R.drawable.pathway_fool
    "Error" -> R.drawable.pathway_error
    "Door" -> R.drawable.pathway_door
    "Mother" -> R.drawable.pathway_mother
    "Tyrant" -> R.drawable.pathway_tyrant
    "Red Priest" -> R.drawable.pathway_red_priest
    "Demoness" -> R.drawable.pathway_demoness
    "Black Emperor" -> R.drawable.pathway_black_emperor
    "Twilight Giant" -> R.drawable.pathway_twilight_giant
    "Hermit" -> R.drawable.pathway_hermit
    "Wheel of Fortune" -> R.drawable.pathway_wheel_of_fortune
    "Justiciar" -> R.drawable.pathway_justiciar
    "Hanged Man" -> R.drawable.pathway_hanged_man
    "Death" -> R.drawable.pathway_death
    "Darkness" -> R.drawable.pathway_darkness
    "Chained" -> R.drawable.pathway_chained
    "White Tower" -> R.drawable.pathway_white_tower
    "Visionary" -> R.drawable.pathway_visionary
    "Moon" -> R.drawable.pathway_moon
    "Sun" -> R.drawable.pathway_sun
    "Abyss" -> R.drawable.pathway_abyss
    "Paragon" -> R.drawable.pathway_paragon
    else -> null
}
