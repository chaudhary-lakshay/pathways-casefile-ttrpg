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
data class RitualWeight(
    val weight: String,
    val modifier: Int,
    val categories: String,
    val spi: Int = 0,
    val spiMax: Int? = null,
    val spiOpen: Boolean = false,
) {
    /** "2 SPI", "4-6 SPI", "8+ SPI" -- the cell as the book prints it. */
    val printedCost: String get() = when {
        spiOpen -> "$spi+ SPI"
        spiMax != null -> "$spi-$spiMax SPI"
        else -> "$spi SPI"
    }
}

/**
 * One row of the Phase I Preparation Quality table (Ch. 7, p. 63).
 *
 * Three of the five carry something a single number cannot say: [best] is the
 * other end of "+1 to +2" and "-1 to -2", and [openEnded] marks "-4 or worse",
 * which is a floor rather than a value. [modifier] is always the end nearer
 * zero, so a caller that ignores the rest is gentle rather than harsh.
 */
@Serializable
data class RitualPreparation(
    val quality: String,
    val modifier: Int,
    val printed: String,
    val best: Int? = null,
    val openEnded: Boolean = false,
    val looksLike: String,
)

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
 * One band of the Question Difficulty table (Ch. 6.5, Step 1).
 *
 * [openEnded] marks "-6 or worse", the one band whose modifier is a floor
 * rather than a value -- the same shape as the "100+ meters" range band and
 * the darkness row. [modifier] is its gentle end.
 */
@Serializable
data class DivinationDifficulty(
    val band: String,
    val modifier: Int,
    val printedModifier: String,
    val example: String,
    val openEnded: Boolean = false,
)

/**
 * One row of the Methods & Tools table (Ch. 6.5, Section II).
 *
 * [spiMax] is present only on Dowsing Rods, whose cell reads "1-2": a cost the
 * GM picks inside, not a fixed one.
 */
@Serializable
data class DivinationMethod(
    val method: String,
    val bestFor: String,
    val time: String,
    val spi: Int,
    val spiMax: Int? = null,
    val notes: String,
)

/**
 * One row of the Step 3 modifier table (Ch. 6.5).
 *
 * [crossReference] marks "Question difficulty / See Step 1 table", which is
 * not a number at all -- it points at [DivinationDifficulty], which the caller
 * already applies. Reading it as zero would drop the whole of Step 1, so it is
 * refused, the same way the Aim rows are.
 *
 * [best] is the other end of a printed range ("+0 to +2"); [modifier] is the
 * end nearer zero. [perRepeat] marks the one row that multiplies.
 */
@Serializable
data class DivinationModifier(
    val source: String,
    val printed: String,
    val modifier: Int? = null,
    val best: Int? = null,
    val perRepeat: Boolean = false,
    val crossReference: Boolean = false,
)

/**
 * One row of the Step 4 clarity table (Ch. 6.5).
 *
 * Five rows are named by margin and [isCritical] marks the sixth, which is
 * named by OUTCOME. Two doors into one table, exactly as the ritual failure
 * table has -- see [com.pathways.rules.Divination.clarityFor].
 */
@Serializable
data class DivinationClarity(
    val result: String,
    val clarity: String,
    val effect: String,
    val isCritical: Boolean = false,
)

/** One row of the Supporting Skills table (Ch. 6.5), each worth +1. */
@Serializable
data class DivinationSupport(val skill: String, val bonus: Int)

/**
 * One row of the Shields table (Ch. 5, p. 37).
 *
 * [db] is the Defense Bonus, which "adds to all active defenses (Dodge, Parry,
 * and Block) against attacks from the front or shield side" -- not to Block
 * alone. [weight] and [cost] stay text because the improvised row prints
 * "varies" and an em dash, which are real values rather than missing ones.
 *
 * [notes] is the printed cell, and two of the four carry a Block penalty in
 * prose. The numbers are applied by [com.pathways.rules.Shields], which is
 * where the scope of each can be said out loud.
 */
@Serializable
data class Shield(
    val type: String,
    val db: Int,
    val coverDr: Int,
    val hp: Int,
    val weight: String,
    val cost: String,
    val notes: String,
)

/** One bullet under the Shields table: breaking, cover, or a shield bash. */
@Serializable
data class ShieldRule(val rule: String)

/**
 * One row of the Optional Major Wound Threshold table (Ch. 5, p. 41-42).
 *
 * [failBy] is present on the third row only ("Fail by 5+"): the first two are
 * reached by outcome, not by margin. Carrying no margin is not the same as
 * carrying a margin of zero, so the field is absent rather than 0 -- the same
 * shape as [RitualCritical.corGained].
 */
@Serializable
data class MajorWound(val result: String, val effect: String, val failBy: Int? = null)

/**
 * One rule of the Spirit Body Damage section (Ch. 5, p. 40-41).
 *
 * [section] is the book's own label, untidied -- "Recovery follows each pool's
 * normal rate" is what it prints, and shortening it would put a heading on the
 * screen that the rulebook does not have.
 *
 * The "Threshold Penalties by Pool" group is absent on purpose: its HP and SPI
 * lines are [PoolThreshold] rows and its FP line is one of the two prose
 * sources [com.pathways.rules.Pools] quotes. See tools/extract_spirit.py.
 */
@Serializable
data class SpiritDamageRule(val section: String, val rule: String)

/**
 * One row of the Optional Hit Locations table (Ch. 5, p. 41).
 *
 * [penalty] is the to-hit cost of calling the shot; [multiplier] is what a hit
 * does to penetrating damage, and STACKS with the damage type's own multiplier
 * rather than replacing it -- the book's worked example is a cutting sword to
 * the neck at x2 * x1.5 = x3.
 *
 * Two rows do not have one multiplier. The Neck prints "Cr x1.5 / Cut x2 /
 * Imp x2" and the Vitals "x3 (imp/pi only)", so [byType] carries the values
 * that vary and [multiplier] is what everything else takes. On the Vitals that
 * fallback is the book's own word ("only"); on the Neck it is a reading, since
 * the book prints three types there and is silent about burning or toxic. An
 * unlisted type takes the plain x1 rather than the nearest listed one, because
 * which is nearest is not something the rulebook says.
 *
 * Resolve a type through [com.pathways.rules.Wounds.multiplierFor], never by
 * reading [byType] directly. A bare `byType[key] ?: multiplier` cannot tell a
 * type the row leaves unlisted from a key that is not a damage type at all,
 * and would answer x1 for a misspelling (Codex) -- the same silent default
 * this project refuses on the Aim rows and on Shove.
 */
@Serializable
data class HitLocation(
    val target: String,
    val penalty: Int,
    val printedMultiplier: String,
    val multiplier: Double,
    val byType: Map<String, Double> = emptyMap(),
    val effect: String,
)

/**
 * One row of the Optional Wounding Multipliers table (Ch. 5, p. 41).
 *
 * [abbreviation] is present only for the four types the book abbreviates
 * somewhere -- "Cr", "Cut" and "Imp" in the Neck cell, "pi" in the Vitals
 * cell. Piercing (small) and Large Piercing carry none: GURPS writes them
 * "pi-" and "pi+", this book never does, and a key it never printed would let
 * a lookup answer for a row that does not exist here.
 */
@Serializable
data class WoundingMultiplier(
    val type: String,
    val multiplier: Double,
    val examples: String,
    val abbreviation: String? = null,
)

/**
 * One band of the Range Penalties table (Ch. 5, p. 39).
 *
 * [to] is null on "100+ meters", the one open band. The bands OVERLAP at
 * exactly 100 metres -- "51-100" is -5 and "100+" is -6 -- which is the
 * book's own arithmetic and is recorded rather than repaired; see
 * [com.pathways.rules.Ranged.penaltyFor] for which one wins and why.
 */
@Serializable
data class RangePenalty(
    val printed: String,
    val from: Int,
    val to: Int? = null,
    val penalty: Int,
    val printedPenalty: String,
    val gmDiscretion: Boolean = false,
) {
    init {
        require(to == null || to >= from) { "range band '$printed' runs $from..$to" }
    }
}

/**
 * One row of Other Ranged Modifiers (Ch. 5, p. 39-40).
 *
 * Three of the nine carry something a single signed integer cannot express:
 *
 * [weaponAcc] marks the two Aim rows, whose modifier is "+ weapon's Acc" --
 * and this book prints no weapon table, so the number does not exist to be
 * applied. They carry no [modifier] at all.
 *
 * [worstCase] is the harsh end of "-3 to -9 depending on severity"; [modifier]
 * is the gentle end, and the GM picks between them.
 *
 * [melee] is the same condition's value in reach: a prone target is -2 to hit
 * at range but -4 in melee, so one row means two different things depending on
 * how it is being attacked.
 */
@Serializable
data class RangedModifier(
    val condition: String,
    val printed: String,
    val modifier: Int? = null,
    val worstCase: Int? = null,
    val melee: Int? = null,
    val weaponAcc: Boolean = false,
)

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
