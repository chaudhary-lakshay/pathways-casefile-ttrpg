package com.pathways.rules

import com.pathways.model.BoonState
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

    private const val WALKED_AS = "Eternal Aeon (Inevitability)"

    fun all(): List<SequenceBlock> = ContentLoader.nonStandardPathways()

    /**
     * The other name this pathway is printed under, or null.
     *
     * The lookups stay exact -- these are two strings the book prints. Only
     * a WALK merges them ([patronOf]), on the owner's ruling. But a reader who picks one and is told
     * a sequence is unrecorded needs to know the other spelling records it
     * (Codex), which is what this is for.
     */
    fun alsoPrintedAs(pathway: String): String? =
        if (pathway in NAME_DISAGREEMENT) (NAME_DISAGREEMENT - pathway).single() else null

    /**
     * The name [pathway] is WALKED under: ch. 14's spelling for both of
     * Eternal Aeon's. The owner's ruling (2026-10-13) that the two printings
     * are one ladder; [blockFor] and [alsoPrintedAs] still read the book's
     * two strings exactly.
     */
    fun patronOf(pathway: String): String = if (pathway in NAME_DISAGREEMENT) WALKED_AS else pathway

    /** The pathways a boon can be petitioned on, in printed order. */
    fun patrons(): List<String> = pathways().map(::patronOf).distinct()

    /**
     * Every printed block [boon] holds, the first granted first. A rung the
     * book never recorded grants nothing, and neither does a pathway it does
     * not print: empty, never a throw, because this is read off a save.
     */
    fun chainFor(boon: BoonState): List<SequenceBlock> = all()
        .filter { patronOf(it.pathway) == patronOf(boon.pathway) && it.sequence >= boon.sequence }
        .sortedByDescending { it.sequence }

    /**
     * The boon a petition to [patron] asks for: the next rung the book
     * prints below [held], or its first for a character holding none.
     *
     * Null when nothing further is recorded, and for a second patron: each
     * Outer Deity "is the sole source of all boons on that path" (ch. 14),
     * and a character walks one. READING: ch. 13's "the same Sequence
     * ladder" taken one rung at a time, as a potion's is; the first rung is
     * Sequence 8 where no Sequence 9 is printed (the owner's ruling).
     */
    fun nextRung(held: BoonState?, patron: String): BoonState? {
        if (held != null && patronOf(held.pathway) != patronOf(patron)) return null
        val below = held?.sequence ?: Int.MAX_VALUE
        return all().filter { patronOf(it.pathway) == patronOf(patron) && it.sequence < below }
            .maxOfOrNull { it.sequence }
            ?.let { BoonState(patronOf(patron), it) }
    }

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
