package com.pathways.rules.content

import com.pathways.model.ControllingAttribute
import com.pathways.model.Difficulty
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * The book's skill list writes controlling attribute as a bare short code
 * ("DX", "Per", "SPI", "Will", ...) and [ControllingAttribute] itself is a
 * polymorphic sealed interface (it needs its own `{"type": ...}` envelope
 * under kotlinx's default derived serializer). This use-site serializer
 * bridges the two: read/write the plain code, resolve through
 * [ControllingAttribute.fromCode] exactly like [com.pathways.rules.Premades]
 * already does by hand.
 */
private object ControllingAttributeCodeSerializer : KSerializer<ControllingAttribute> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("ControllingAttributeCode", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ControllingAttribute) {
        encoder.encodeString(value.code)
    }

    override fun deserialize(decoder: Decoder): ControllingAttribute =
        ControllingAttribute.fromCode(decoder.decodeString())
}

/**
 * The book's skill list writes difficulty as the short code ("E"/"A"/"H"/"VH"),
 * matching [Difficulty.code] -- not the enum entry name kotlinx's default
 * derived serializer expects ("EASY"/"AVERAGE"/...). Same bridging pattern
 * as [ControllingAttributeCodeSerializer].
 */
private object DifficultyCodeSerializer : KSerializer<Difficulty> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("DifficultyCode", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Difficulty) {
        encoder.encodeString(value.code)
    }

    override fun deserialize(decoder: Decoder): Difficulty =
        Difficulty.fromCode(decoder.decodeString())
}

/**
 * One row of the "Complete Skill List by Category" table (Ch. 3).
 *
 * [specializable] is true when the book marks this entry with a
 * "(specify...)" style placeholder -- e.g. "Area Knowledge (specify)" on
 * the name, or "Specific region (specify)" trailing the notes -- meaning
 * the printed entry is a generic template and character sheets pick a
 * concrete [specialization] of their own that will not appear verbatim in
 * this file (see ContentLoaderTest for how fixtures are matched against
 * these by base name).
 */
@Serializable
data class SkillDefinition(
    val name: String,
    val specialization: String? = null,
    @Serializable(with = ControllingAttributeCodeSerializer::class)
    val attr: ControllingAttribute,
    @Serializable(with = DifficultyCodeSerializer::class)
    val difficulty: Difficulty,
    val specializable: Boolean = false,
    val notes: String = "",
)

/**
 * One row of the Advantages/Disadvantages tables (Ch. 3).
 *
 * Cost is printed in one of three shapes and every entry uses EXACTLY one:
 *  - [cost]: a single fixed point value (the common case).
 *  - [costMin]/[costMax]: a variable-point entry printed as a range
 *    ("-5 to -20") -- the specific value is chosen per character, so there
 *    is no single "the" cost to report (e.g. Obsession, Code of Honor,
 *    Social Stigma).
 *  - [variableCost]: cost is stated only in prose elsewhere (e.g.
 *    Reputation, Language) and cannot be reduced to a number at all here.
 *
 * The [init] check is the hard-failure guard for a defect where some
 * disadvantages parsed with no cost representation whatsoever: a content
 * entry with none of the three shapes present must never load silently.
 * Costs are always positive magnitudes, matching [com.pathways.model.Trait]'s
 * "cost is a magnitude; the sign lives in how it's used" convention -- a
 * disadvantage's -15 is stored here as 15.
 */
@Serializable
data class TraitDefinition(
    val name: String,
    val cost: Int? = null,
    val costMin: Int? = null,
    val costMax: Int? = null,
    val variableCost: Boolean = false,
    val perLevel: Boolean = false,
    val notes: String = "",
) {
    init {
        require(cost != null || (costMin != null && costMax != null) || variableCost) {
            "trait '$name' has no cost representation at all " +
                "(none of cost / costMin+costMax / variableCost is present)"
        }
        if (cost != null) require(cost >= 0) { "trait '$name' cost is a magnitude; was $cost" }
        if (costMin != null) require(costMin >= 0) { "trait '$name' costMin is a magnitude; was $costMin" }
        if (costMax != null) require(costMax >= 0) { "trait '$name' costMax is a magnitude; was $costMax" }
    }
}

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

/** Raw thrust/swing row exactly as read from the ThrustTable spreadsheet tab. */
@Serializable
data class DamageRowJson(val st: Int, val thrust: String, val swing: String)

/** [DamageRowJson] with thrust/swing parsed into [com.pathways.model.DiceExpr]. */
data class DamageRow(val st: Int, val thrust: com.pathways.model.DiceExpr, val swing: com.pathways.model.DiceExpr)

/**
 * Generic envelope every content file shares: the entries themselves, plus
 * the real global line numbers (from build/extract/lines.json) that were
 * claimed to produce them -- the contract tools/coverage.py depends on.
 */
@Serializable
data class ContentFile<T>(
    val sourceLines: Map<String, List<List<Int>>> = emptyMap(),
    val entries: List<T>,
)
