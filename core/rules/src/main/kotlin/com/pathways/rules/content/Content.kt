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
 * [ControllingAttribute.fromCode] exactly like [com.pathways.rules.Fixtures]
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
