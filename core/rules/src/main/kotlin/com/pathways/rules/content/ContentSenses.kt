package com.pathways.rules.content

import kotlinx.serialization.Serializable

/** A: one Astral Projection colour and the emotional state it reveals. */
@Serializable
data class AstralColor(val color: String, val meaning: String)

/**
 * B: one Ether Body region and its colour.
 *
 * The INVERSE column order to [AstralColor], and seven of the eight colours
 * appear in both tables meaning unrelated things -- Purple is "Spirituality,
 * madness" there and the BRAIN here. Nothing may join the two on the colour.
 */
@Serializable
data class EtherBodyRegion(val region: String, val color: String)

/**
 * C: one row of chapter 7.6's Sequence 9 summary.
 *
 * **Reference only, and never a gate.** The table lists five pathways and
 * there are five Sequence 9 grants, but the membership differs by one in each
 * direction: it prints Visionary, whose Spectator block grants no Spirit
 * Vision at all, and omits Moon, whose Apothecary block grants it. Who may
 * roll comes from [SpiritVisionGrant], derived from each pathway's own block.
 */
@Serializable
data class PathwayDifference(val name: String, val sequence: Int, val ability: String)

/**
 * One row of D's interpretation table.
 *
 * [margin] is the band this row needs, absent on the rows the book names
 * instead ("Critical", "Failure"). This table prints a FAILURE row where
 * chapter 4's sibling tables print none: a failed reading is a confident
 * WRONG answer rather than no answer at all.
 */
@Serializable
data class SpiritVisionRow(
    val result: String,
    val effect: String,
    val margin: Int? = null,
)

/** D's activation cost and its five result rows. */
@Serializable
data class SpiritVisionReading(
    val activation: Int,
    val maintenance: Int,
    val rows: List<SpiritVisionRow>,
)

/** The chapter's closing sentence: each pathway's own name for the ability. */
@Serializable
data class SpecialForm(val form: String, val pathwayRank: String)

/**
 * Which pathway grants Spirit Vision, and from which rung.
 *
 * Derived by `tools/extract_spirit_vision.py` from each pathway's own potion
 * block, NOT from chapter 7.6's summary table, which is wrong in both
 * directions. The prose matching lives in the extractor so that the rules
 * engine consumes structure: a matcher here would be a second authority on
 * what the book grants.
 *
 * [sequence] is the HIGHEST sequence number granting it, and sequences count
 * DOWN -- a Sequence 8 Fool drank the Seer's potion first -- so a character
 * has the ability when their own sequence is at or below this rung.
 *
 * [form] is the book's own name for it, which is not always "Spirit Vision":
 * the Hermit's block writes "Eyes of Mystery Prying" and never the common
 * name at all.
 */
@Serializable
data class SpiritVisionGrant(val pathway: String, val sequence: Int, val form: String)

/** Chapter 7.6 whole: four lettered sections plus the derived grants. */
@Serializable
data class SpiritVisionFile(
    val astralColors: List<AstralColor>,
    val etherBody: List<EtherBodyRegion>,
    val etherBodyNote: String,
    val pathwayDifferences: List<PathwayDifference>,
    val reading: SpiritVisionReading,
    val specialForms: List<SpecialForm>,
    val grants: List<SpiritVisionGrant>,
)

/**
 * Generic envelope every content file shares: the entries themselves, plus
 * the real global line numbers (from build/extract/lines.json) that were
 * claimed to produce them -- the contract tools/coverage.py depends on.
 */
/**
 * One band of a spiritual sense's result table (Ch. 4, p. 33-34).
 *
 * [margin] is set only on the two rows the book names by margin, and is
 * absent -- not zero -- on "Success" and "Critical Success". A critical is
 * reached by OUTCOME, and a margin of 0 on it would make a bare success
 * satisfy the same band.
 */
@Serializable
data class SenseRow(
    val result: String,
    val effect: String,
    val margin: Int? = null,
)

/** One circumstance on a spiritual sense roll (Ch. 4, p. 33-34). */
@Serializable
data class SenseModifier(val situation: String, val modifier: Int)

/**
 * A spiritual sense skill and the table its own chapter prints for it
 * (Ch. 4, p. 33-34).
 *
 * Both tables band at "Success by 3+" and "Success by 5+", which are exactly
 * [com.pathways.rules.DegreeOfSuccess]'s Good and Excellent edges. They are
 * deliberately NOT that ladder: the effect text is per-skill, and a chapter's
 * own table beats the general one. Read through
 * [com.pathways.rules.SpiritualSenses].
 */
@Serializable
data class SpiritualSense(
    val skill: String,
    val attr: String,
    val difficulty: String,
    val rows: List<SenseRow>,
    val modifiers: List<SenseModifier>,
)
