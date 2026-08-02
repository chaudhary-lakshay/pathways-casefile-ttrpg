package com.pathways.rules

import com.pathways.rules.content.ContentLoader
import com.pathways.rules.content.SequenceBlock

/**
 * The boon-granted pathways of the Outer Deities (Ch. 14-15).
 *
 * The same [SequenceBlock] shape as the 88 potion blocks, deliberately kept
 * in a different file and behind a different object.
 *
 * **They are not potions.** Chapter 14's second paragraph: "Unlike standard
 * Beyonders who consume potions brewed from Beyonder Characteristics,
 * followers of non-standard pathways advance through boons -- power granted
 * by an Outer Deity in response to prayer, sacrifice, and ritual."
 * [PotionDigestion] walks whatever `pathways.json` holds and `canDrink`
 * offers whatever it walks, so one of these landing in that file would put a
 * Sequence 9 Dancer potion on the pathway screen. The split is the guard, and
 * extract_non_standard.not_also_a_potion fails the build if it is ever
 * breached.
 *
 * **The ladder has holes.** Chapter 14 documents Sequence 9 for ten pathways
 * and chapter 15 Sequence 8 for seven, and they do not line up: three
 * pathways print a Sequence 8 whose Sequence 9 the book never records. The
 * intro says so -- "most pathways are only known at this sequence" -- and it
 * is why `pathways.json`'s rule that every pathway carries all of 6/7/8/9
 * could not have held these.
 */
object NonStandardPathways {

    /**
     * The one pathway the two chapters spell differently: ch. 14 prints
     * "Eternal Aeon (Inevitability) Pathway" and ch. 15 "Eternal Aeon
     * Pathway". Named here, and asserted by
     * extract_non_standard.PATHWAY_NAME_DISAGREEMENT, so the pair is a
     * recorded fact rather than an alias table quietly merging them.
     */
    private val NAME_DISAGREEMENT = setOf("Eternal Aeon (Inevitability)", "Eternal Aeon")

    fun all(): List<SequenceBlock> = ContentLoader.nonStandardPathways()

    /**
     * The other name this pathway is printed under, or null.
     *
     * The lookups stay exact -- these are two strings the book prints and
     * merging them would be a repair. But a reader who picks one and is told
     * a sequence is unrecorded needs to know the other spelling records it
     * (Codex), which is what this is for.
     */
    fun alsoPrintedAs(pathway: String): String? =
        if (pathway in NAME_DISAGREEMENT) (NAME_DISAGREEMENT - pathway).single() else null

    /** Every pathway the two chapters print, in printed order. */
    fun pathways(): List<String> = all().map { it.pathway }.distinct()

    /** What is documented for [pathway], Sequence 9 first. */
    fun blocksFor(pathway: String): List<SequenceBlock> {
        require(pathway in pathways()) {
            "no non-standard pathway named '$pathway'; the book prints " +
                pathways().joinToString()
        }
        return all().filter { it.pathway == pathway }.sortedByDescending { it.sequence }
    }

    /** The sequences [pathway] has a block for, descending. */
    fun knownSequences(pathway: String): List<Int> = blocksFor(pathway).map { it.sequence }

    /**
     * The block for [pathway] at [sequence], or null when the book records
     * that rung for nobody.
     *
     * Null and refusal mean different things here. A pathway the book never
     * prints is a bad call and throws; a rung it simply never recorded is a
     * real, expected answer -- the same distinction [Wounds.multiplierFor]
     * draws between an unlisted damage type and a silent cell.
     */
    fun blockFor(pathway: String, sequence: Int): SequenceBlock? =
        blocksFor(pathway).firstOrNull { it.sequence == sequence }
}
