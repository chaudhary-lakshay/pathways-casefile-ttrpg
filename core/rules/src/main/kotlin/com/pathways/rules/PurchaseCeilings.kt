package com.pathways.rules

import com.pathways.model.Attr
import com.pathways.model.MAX_ATTRIBUTE
import com.pathways.model.MAX_SECONDARY_DELTA_MAGNITUDE
import com.pathways.rules.content.ContentLoader

/**
 * The highest each score may be BOUGHT to, so that every potion and every boon
 * still fits on top of it.
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
    // A boon is held BESIDE a potion walk, so the room is for both: the
    // boon blocks under the name each is walked by, or Eternal Aeon's two
    // spellings would be measured as two half ladders.
    private val reach by lazy {
        PotionDigestion.largestGrants(
            ContentLoader.pathways(),
            NonStandardPathways.all().map { it.copy(pathway = NonStandardPathways.patronOf(it.pathway)) },
        )
    }

    /**
     * ST 17, DX 23, IQ 25, HT 21 against today's catalogue, less what the
     * blessings named in [held] add ([Blessings], 6hf): ST 14, DX 20, HT 18
     * for God of Combat. Required, so no caller forgets the blessing.
     */
    fun attribute(attr: Attr, held: Collection<String>): Int =
        MAX_ATTRIBUTE - (reach.attributes[attr] ?: 0) - Blessings.gainTo(attr, held)

    /** The steps of the book's secondary row [key]; 100 for a row no potion raises. */
    fun secondarySteps(key: String): Int =
        MAX_SECONDARY_DELTA_MAGNITUDE - SecondaryCharacteristics.stepsOf(reach.secondary, key)
}
