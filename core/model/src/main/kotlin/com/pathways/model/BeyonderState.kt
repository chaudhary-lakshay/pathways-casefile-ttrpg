package com.pathways.model

import kotlinx.serialization.Serializable

/** The lowest and highest Sequence the extracted content covers (Ch. 9-12). */
const val LOWEST_SEQUENCE = 6
const val HIGHEST_SEQUENCE = 9

/**
 * Which pathway a character walks, and how far down it they have come.
 *
 * This is the whole of a Beyonder's potion state: the CHOICE, not its
 * consequences. Every stat gain, skill grant and ability follows from
 * (pathway, sequence) through the content, so storing them here would
 * duplicate the book and let the two drift. It also keeps [Character]
 * purchased-only, which is what stops potion gains reaching the point
 * budget -- see com.pathways.rules.PotionDigestion.
 *
 * Sequences count DOWN as a Beyonder ascends: 9 is the first potion, 6 the
 * strongest this content covers. The bound is the content's, not the
 * setting's -- the book's pathways continue past Sequence 6, and this widens
 * when those chapters are extracted.
 */
@Serializable
data class BeyonderState(
    val pathway: String,
    val sequence: Int,
    /**
     * How much of the CURRENT potion has been digested, 0-100% (Ch. 6, p. 44).
     * A fresh potion starts at 0 and climbs through the Acting Method; it is
     * not a level, and it resets when the next potion is drunk.
     */
    val digestion: Int = 0,
) {
    init {
        require(pathway.isNotBlank()) { "a Beyonder walks a named pathway" }
        require(sequence in LOWEST_SEQUENCE..HIGHEST_SEQUENCE) {
            "sequence must be between $LOWEST_SEQUENCE and $HIGHEST_SEQUENCE " +
                "(the extracted content covers no others); was $sequence"
        }
        require(digestion in 0..100) { "digestion is a percentage; was $digestion" }
    }

    /**
     * The next potion up the pathway, or null at the strongest Sequence held.
     * Digestion restarts at 0: the new potion is the one being digested now.
     */
    fun advanced(): BeyonderState? =
        if (sequence <= LOWEST_SEQUENCE) null else BeyonderState(pathway, sequence - 1)

    /** Digestion after a session, clamped to the 0-100% the book defines. */
    fun digested(points: Int): BeyonderState =
        copy(digestion = (digestion + points).coerceIn(0, 100))

    val isFullyDigested: Boolean get() = digestion == 100
}

/**
 * Whether [next] is a potion this character may legally drink now.
 *
 * A pathway is walked one potion at a time, from Sequence 9 down. A mortal
 * may only begin at the start; a Beyonder may only take the next Sequence of
 * the pathway they are already on. Without this, storing "Sequence 6" for a
 * mortal would derive the whole chain's grants as though the three skipped
 * potions had been drunk.
 *
 * Switching pathways is not a thing a character does at all -- it is a
 * different Beyonder.
 */
fun BeyonderState?.canDrink(next: BeyonderState): Boolean =
    if (this == null) next.sequence == HIGHEST_SEQUENCE else next == advanced()
