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
 * One row of the HP or SPI threshold ladder (Ch. 5, p. 35 and p. 40).
 *
 * [level] is the printed label ("1/3 HP remaining", "-5xHP", "<= 1/3 Max
 * SPI"), kept verbatim and never turned into arithmetic here: "-5xHP" means
 * five times the character's OWN maximum, which is a rule and belongs to
 * [com.pathways.rules.Pools]. `PoolsTest` asserts the labels Pools switches
 * on are still the labels this file carries.
 *
 * FP has no row: the book states its thresholds only in prose, in two
 * sections that disagree. See tools/extract_pools.py.
 */
@Serializable
data class PoolThreshold(val pool: String, val level: String, val effect: String) {
    init {
        require(pool in KNOWN_POOLS) { "unknown pool '$pool'; known: $KNOWN_POOLS" }
        require(effect.isNotBlank()) { "pool threshold '$level' has no effect text" }
    }

    companion object {
        val KNOWN_POOLS = setOf("HP", "SPI")
    }
}

/**
 * One row of the Fright Check trigger table (Ch. 5, p. 43).
 *
 * [modifier] is the gentler end of what the row prints, because that is the
 * only figure all three printed shapes share: a flat "+2", a range
 * ("-2 to -6", whose other end is [worstCase]), and "-8 or worse" (which sets
 * [openEnded] and has no other end at all). The GM picks inside the range;
 * [printed] is what they are picking from.
 */
@Serializable
data class FrightTrigger(
    val trigger: String,
    val printed: String,
    val modifier: Int,
    val worstCase: Int? = null,
    val openEnded: Boolean = false,
)

/**
 * One band of the Fright / Awe / Confusion effects table (Ch. 5, p. 43-44).
 *
 * The same 3d6 + margin roll reads across three columns; which one applies is
 * the trigger's nature, not the roll's. [high] is null only on the "35+" band,
 * which the book leaves open.
 */
@Serializable
data class FrightEffect(
    val low: Int,
    val high: Int? = null,
    val printed: String,
    val fright: String,
    val awe: String,
    val confusion: String,
) {
    init {
        require(high == null || high >= low) { "fright band '$printed' runs $low..$high" }
    }
}

/** One row of the ritual weight table (Ch. 7, p. 67): how hard a category is. */
@Serializable
data class RitualWeight(val weight: String, val modifier: Int, val categories: String)

/**
 * One row of "Failure & Consequences" (Ch. 7, p. 67).
 *
 * [from]/[to] are a margin of failure, and are absent on the row the book
 * enters by the ROLL rather than by a margin -- "Critical Failure (roll
 * 17-18)", which sets [critical]. Giving that row a numeric band would let a
 * margin lookup match it and skip the real severe-misfire row.
 */
@Serializable
data class RitualFailure(
    val printed: String,
    val consequence: String,
    val from: Int? = null,
    val to: Int? = null,
    val critical: Boolean = false,
) {
    init {
        require(critical || from != null) { "non-critical row '$printed' has no margin band" }
    }
}

/**
 * One band of the Critical Failure Table (Ch. 7, p. 67-68), rolled on 3d6.
 *
 * The outcomes are NOT ordered by severity -- 3-4 is Backlash, 13-14 costs a
 * CoR, 18 is Catastrophe -- which matters because a severe misfire shifts the
 * roll by -2. The shift moves down the TABLE, not down a severity scale.
 */
@Serializable
data class RitualCritical(
    val low: Int,
    val high: Int,
    val printed: String,
    val outcome: String,
    /**
     * The CoR this row charges, read out of its printed prose by the
     * extractor ("Corruption. Gain 1 CoR."). Null on the six rows that charge
     * none -- the book charging no corruption is not the same as charging
     * zero. Scanning rule text for numbers at render time would be a parser
     * nobody tests; doing it once, in the extractor, is checked by verify()
     * and by test_tools.py.
     */
    val corGained: Int? = null,
)

/**
 * One row of the Active Defenses table (Ch. 5, p. 36-37).
 *
 * [formula] is the book's printed text, NOT arithmetic: [com.pathways.rules.ParryBlock]
 * implements the maths and is tested against the book, and a second copy that
 * drifted would be worse than none. [notes] is what the GM needs beside the
 * number -- "Cumulative -4 per additional parry", "Once per round".
 */
@Serializable
data class Defense(val defense: String, val formula: String, val notes: String)

/**
 * One bullet of the Defense Modifiers list (Ch. 5, p. 37).
 *
 * Prose, not signed numbers: "Acrobatic Dodge" grants +2 on a success and -2
 * on a failure, so there is no single figure to extract.
 */
@Serializable
data class DefenseModifier(val modifier: String)

/**
 * One row of the Maneuvers table (Ch. 5, p. 36).
 *
 * [fp] is the maneuver's own cost, present on the five All-Out rows and absent
 * on the nine that cost nothing -- absent, not zero, for the same reason
 * [RitualCritical.corGained] is: charging nothing is not charging zero. It is
 * extracted because the app SPENDS it; the to-hit numbers inside [description]
 * are not, because [com.pathways.rules.Attacks] implements those and is tested
 * against the book. Two copies of a number are one copy too many.
 */
@Serializable
data class Maneuver(
    val maneuver: String,
    val movement: String,
    val description: String,
    val fp: Int? = null,
)

/** One Extra Effort bullet (Ch. 5, p. 36): a free action bought with FP. */
@Serializable
data class ExtraEffort(val option: String, val fp: Int, val effect: String)

/**
 * One Attack Option bullet (Ch. 5, p. 36-37).
 *
 * Prose only. Rapid Strike is a flat penalty and Deceptive Attack is a rate
 * with a cap; there is no single number that means the same thing for both,
 * so [com.pathways.rules.Attacks] carries the arithmetic and this carries
 * what the GM reads.
 */
@Serializable
data class AttackOption(val option: String, val rule: String)

/**
 * One row of the Brawling or Boxing attack table (Ch. 5, p. 38-39).
 *
 * [damage] is the printed cell ("thr-1 cr"); [base], [modifier] and [type] are
 * its three parts, parsed once by the extractor and checked there against the
 * text they came from. All three are absent on Shove, whose cell reads
 * "Special" -- an attack that knocks down instead of wounding deals no damage,
 * which is not the same as dealing zero.
 *
 * Wrestling has no row here: the book's own summary gives it "None (grapple
 * only)".
 */
@Serializable
data class UnarmedAttack(
    val skill: String,
    val attack: String,
    val rollModifier: Int,
    val damage: String,
    val notes: String,
    val base: String? = null,
    val modifier: Int = 0,
    val type: String? = null,
) {
    /** False for Shove, the one row that rolls no damage at all. */
    val wounds: Boolean get() = base != null
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
