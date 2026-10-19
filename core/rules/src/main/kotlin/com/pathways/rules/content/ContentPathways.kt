package com.pathways.rules.content

import com.pathways.model.ControllingAttribute
import com.pathways.model.Difficulty
import kotlinx.serialization.Serializable

/**
 * One attribute row of a potion's "Stat / Advantage" table (Ch. 9-12).
 *
 * [attr] is a plain code rather than a typed attribute because the rows span
 * three different kinds of thing: buyable attributes (ST/DX/IQ/HT), the
 * non-buyable controlling attributes (Will/Per/SPI), and two secondary
 * characteristics (Basic Speed, Basic Move) that are no kind of attribute at
 * all. [StatGain.KNOWN_ATTRS] is the closed set the book actually prints; an
 * unknown code is a content bug and fails at load.
 *
 * [delta] is fractional only for Basic Speed (+0.25, Seq 9 Assassin), which
 * is why it is not an Int.
 */
@Serializable
data class StatGain(
    val attr: String,
    val delta: Double,
    val note: String = "",
) {
    init {
        require(attr in KNOWN_ATTRS) { "unknown stat row '$attr'; known: $KNOWN_ATTRS" }
    }

    companion object {
        val KNOWN_ATTRS = setOf(
            "ST", "DX", "IQ", "HT", "Will", "Per", "SPI", "HP", "FP", "Basic Speed", "Basic Move",
        )
    }
}

/**
 * One row of a potion's "Skill" table (Ch. 9-12).
 *
 * [bonus] is read as "attribute + N", NOT as rungs up the skill ladder from
 * default (p. 85: "Acrobatics/DX [Hard] +2" is DX+2, costing 16 points, not
 * the 2 points two rungs would suggest). Where the character already has the
 * same skill at the same difficulty, the book instead reads it as "current
 * level + N" -- that branch belongs to the engine, not to this row.
 *
 * [note] carries the qualifier the book prints in the bonus cell ("(Nature,
 * Life, and Weather domains)"), which is GM-facing text, not a modifier.
 */
@Serializable
data class SkillGrant(
    val name: String,
    @Serializable(with = ControllingAttributeCodeSerializer::class)
    val attr: ControllingAttribute,
    @Serializable(with = DifficultyCodeSerializer::class)
    val difficulty: Difficulty,
    val bonus: Int,
    val note: String = "",
)

/**
 * One Sequence stat block: what drinking that Sequence's potion does to a
 * character (Ch. 9-12, 22 pathways x Sequences 9/8/7/6).
 *
 * [advantages] and [drawbacks] are prose, not structured rows, and
 * deliberately so: the source PDF flattens a two-column table with no
 * recoverable boundary between a wrapped effect line and the next row's
 * name. See tools/extract_pathways.py for the full reasoning. They are
 * display text for the GM, exactly as the book prints them.
 *
 * [reinforcedAbilities] is the "Reinforced Abilities" section, present on 56
 * of the 88 blocks: how this Sequence upgrades the abilities its earlier
 * Sequences already granted.
 */
@Serializable
data class SequenceBlock(
    val sequence: Int,
    val name: String,
    val pathway: String,
    val stats: List<StatGain> = emptyList(),
    val skills: List<SkillGrant> = emptyList(),
    val advantages: String = "",
    val drawbacks: String = "",
    val ability: String = "",
    val reinforcedAbilities: String = "",
    val sourceLines: List<Int> = emptyList(),
    /**
     * Skill grants the book prints that name no skill it lists, verbatim
     * ("Inventor!/IQ [Wildcard] +2"). Shown, never applied (6gk).
     */
    val unresolvedSkills: List<String> = emptyList(),
)

/**
 * One row of the Acting Method table (Ch. 6, p. 45): how much of the current
 * potion a session of acting in the pathway's role digests.
 *
 * The book prints a RANGE per quality, not a figure -- the GM picks within
 * it -- so there is no single number to expose. The "None" row prints no
 * number at all ("0% or loses progress") and carries [losesProgress]
 * instead; a 0 there would be an invented floor.
 */
@Serializable
data class ActingQuality(
    val quality: String,
    val description: String,
    val minGain: Int? = null,
    val maxGain: Int? = null,
    val losesProgress: Boolean = false,
) {
    init {
        require((minGain != null && maxGain != null) || losesProgress) {
            "acting quality '$quality' has neither a gain range nor losesProgress"
        }
        if (minGain != null && maxGain != null) {
            require(minGain <= maxGain) { "acting quality '$quality' has $minGain-$maxGain" }
        }
    }
}

/**
 * One row of the Potion Consumption Roll table (Ch. 6, p. 45-46).
 *
 * The roll is `3d6 <= 10 + modifier`, and the book prints the resulting
 * target as its own column. Both are kept: recomputing the target would hide
 * a disagreement between the book's own columns instead of surfacing it.
 *
 * The modifier progression is not linear (-9, -7, -5, -4, -2, 0, +2, +4, +5,
 * +7, +9), so it is data, never a formula.
 */
@Serializable
data class PcrRow(
    val digestion: Int,
    val modifier: Int,
    val effectiveTarget: Int,
    val note: String = "",
)

/**
 * One row of the "CoR Gained" table (Ch. 6, p. 45): what an action costs in
 * corruption.
 *
 * The rows print four different shapes of number -- a flat 1, a range
 * ("1-3"), an open floor ("10+"), and a die ("1d") -- so no single field fits
 * them all and [gain] carries the printed text whatever else is parsed.
 *
 * [maximum] is absent where the book prints no ceiling; [minimum] is absent
 * where it prints no floor (the "1d" row). Reading either as 0 would invent a
 * bound. [dice] is kept verbatim rather than rolled here: the roll belongs to
 * a caller with a RandomSource, not to content.
 */
@Serializable
data class CorGain(
    val action: String,
    val gain: String,
    val minimum: Int? = null,
    val maximum: Int? = null,
    val dice: String? = null,
    val per: String = "",
) {
    init {
        require(minimum != null || dice != null) {
            "CoR row '$action' has neither a printed floor nor a die: '$gain'"
        }
        if (minimum != null && maximum != null) {
            require(minimum <= maximum) { "CoR row '$action' has $minimum-$maximum" }
        }
    }
}

/**
 * One rung of the corruption ladder printed under "Corruption from Ritual
 * Work" (Ch. 7, p. 68).
 *
 * RITUAL-SPECIFIC, and named so deliberately. The book introduces these as
 * "additional ritual-specific thresholds"; the design spec's section 5
 * paraphrases the same 3/5/8/10 ladder as general CoR thresholds. The book is
 * the authority (CLAUDE.md 4.1), so the disagreement stands recorded and the
 * type name keeps a caller from applying a ritual penalty to a combat roll.
 *
 * The 10 rung is NOT the point of no return: that is CoR = Will, which
 * [com.pathways.rules.Corruption.maxFor] computes per character and may sit
 * either side of 10.
 */
@Serializable
data class RitualCorThreshold(val cor: Int, val effect: String)

/**
 * One row of the Sealed Artifact grade table (Ch. 6, p. 47).
 *
 * **[churchAccess] is the gate and [powerEquivalent] is not.** Every access
 * cell names an institutional RANK; the power column says how strong the
 * artifact is, in Sequence terms, and the book nowhere requires a wielder to
 * match it. Read through [com.pathways.rules.SealedArtifacts], which quotes
 * both and enforces neither.
 */
@Serializable
data class SealedArtifactGrade(
    val grade: Int,
    val danger: String,
    val powerEquivalent: String,
    val sequenceLow: Int,
    val sequenceHigh: Int,
    val churchAccess: String,
)

/**
 * One tier of Chapter 4's claims about what a Sequence 9 potion grants
 * (p. 33).
 *
 * Not a rule the app applies -- `pathways.json` already holds every grant.
 * This is the chapter describing another chapter's data, kept as data so
 * `GoldenSpiAffinityTest` can mark it. [low] and [high] are absent on the
 * tier the book describes only in words.
 */
@Serializable
data class SpiAffinity(
    val tier: String,
    val printed: String,
    val pathways: List<String> = emptyList(),
    val low: Int? = null,
    val high: Int? = null,
)
