package com.pathways.rules

import com.pathways.model.Attr
import com.pathways.model.MAX_ATTRIBUTE
import com.pathways.model.MAX_SECONDARY_DELTA_MAGNITUDE
import com.pathways.rules.content.ContentLoader

/**
 * The highest each score may be BOUGHT to, so that every potion still fits on
 * top of it.
 *
 * ponytail: the app's bound, not the book's, the ceiling side of
 * [ScoreFloors]. A potion adds to what was bought, and the digested score is
 * built through the same [com.pathways.model.Attributes] (ST 30) and
 * [com.pathways.model.SecondaryDelta] (+-100) the purchase is, and rolls
 * damage off a thrust/swing table that stops at ST 30. The pathway screen
 * digests whichever block is BROWSED, so the room left must fit every walk,
 * not only the one this character is on. Derived from the catalogue, so a
 * content fix that raises a grant lowers the ceiling with it.
 */
object PurchaseCeilings {
    private val reach by lazy { PotionDigestion.largestGrants(ContentLoader.pathways()) }

    /** ST 19, DX 26, IQ 26, HT 25 against today's catalogue. */
    fun attribute(attr: Attr): Int = MAX_ATTRIBUTE - (reach.attributes[attr] ?: 0)

    /** The steps of the book's secondary row [key]; 100 for a row no potion raises. */
    fun secondarySteps(key: String): Int =
        MAX_SECONDARY_DELTA_MAGNITUDE - SecondaryCharacteristics.stepsOf(reach.secondary, key)
}
