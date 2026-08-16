package com.pathways.app

import androidx.annotation.DrawableRes

/**
 * The card art for each of the 22 pathways.
 *
 * **Where it comes from, and why this deck.** These are the Major Arcana of
 * the Rider-Waite-Smith tarot, published 1909 and drawn by Pamela Colman Smith
 * (d. 1951). The deck is public domain in the United States, having been
 * published before 1929, and in life-plus-70 jurisdictions since 2022. That is
 * the reason for using it rather than the fan art the setting has plenty of:
 * fan art belongs to the artist who drew it, and this repository is a public
 * portfolio project whose own notes forbid redistribution. Shipping someone
 * else's drawing in an APK on a public remote would be exactly that.
 *
 * It also happens to be the right art rather than merely the safe art: this
 * setting's 22 pathways ARE the 22 Major Arcana, so a tarot card is the thing
 * being depicted and not a decorative stand-in.
 *
 * **Half of this mapping is a reading, not canon.** Nine pathways match a card
 * by name -- Fool, Hermit, Wheel of Fortune, Justiciar/Justice, Hanged Man,
 * Death, White Tower/Tower, Moon, Sun -- and the rest are assigned by subject:
 * the Empress is the mother, the Devil's figures are chained, the Hierophant
 * is a priest. `Darkness` gets Temperance because it is the card left over,
 * which is the weakest link here and is written down rather than dressed up
 * (section 4.1 -- record the disagreement, never hide it).
 *
 * A missing name returns null and the caller draws its plain card. That is
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
