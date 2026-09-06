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
    /**
     * The banner this skill is printed under -- "SOCIAL SKILLS", "COMBAT
     * SKILLS", "SPIRITUAL SKILLS (SPI-based)" and twelve more, verbatim.
     *
     * **Required, with no default.** The banners partition the Complete Skill
     * List, so a row without one means the extractor's bounds moved rather
     * than that the book left a skill uncategorised -- and a default would
     * withdraw every category-scoped rule from that skill in silence. The
     * extractor discarded these for four milestones, which is why a cell
     * reading "+3 to social skill rolls" named a group nothing could identify.
     */
    val category: String,
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
/**
 * The self-control roll a disadvantage prints (Ch. 3), or absent.
 *
 * [target] and [attribute] are mutually exclusive, and keeping them apart is
 * the whole point. A flat "roll vs. 12" is NOT an attribute roll: a potion
 * that raises Will must not quietly make Curious easier to resist. Merging
 * the two into one "effective target" is the shape that would hide it.
 *
 * [source] records WHERE the book printed the number, because for four rows
 * it is the only place it exists:
 *  - `"both"` -- name and effect cell, agreeing (five rows). That agreement
 *    is the warrant for reading the other four.
 *  - `"name"` -- "Overconfidence (12)", with an effect cell naming no roll.
 *    `strip_self_control_number` deleted these from M0 until 6am, and
 *    Overconfidence is on 11 of the 23 premades.
 *  - `"effect"` -- "Must roll vs. Will-3 ...", and the one row printing the
 *    roll outright as "roll 3d6 <= 6". Three spellings, none merged.
 *
 * Absent means the row prints no self-control roll at all -- not a target of
 * zero, the distinction [RitualCritical.corGained] already draws.
 */
@Serializable
data class SelfControlRoll(
    val target: Int? = null,
    val attribute: String? = null,
    val offset: Int = 0,
    val source: String,
) {
    init {
        require((target != null) != (attribute != null)) {
            "a self-control roll is a flat target or an attribute roll, " +
                "never both and never neither (target=$target, attribute=$attribute)"
        }
        require(target == null || offset == 0) {
            "a flat self-control target carries no offset; this book prints none"
        }
        require(source in setOf("both", "name", "effect")) {
            "unknown self-control provenance '$source'"
        }
        // Validated HERE, at load, and not where the number is read. The
        // sheet screen prints this target for every disadvantage it lists, so
        // an attribute this book never keys on would take the whole screen
        // down rather than a dismissable dialog -- and swallowing it at the
        // render site would hide a content defect behind a missing line.
        require(attribute == null || attribute in ATTRIBUTES) {
            "self-control roll keyed on '$attribute'; this book prints " +
                ATTRIBUTES.joinToString()
        }
    }

    companion object {
        /** The two attributes this book keys a self-control roll on. */
        val ATTRIBUTES = setOf("Will", "IQ")
    }
}

@Serializable
data class TraitDefinition(
    val name: String,
    val cost: Int? = null,
    val costMin: Int? = null,
    val costMax: Int? = null,
    val variableCost: Boolean = false,
    val perLevel: Boolean = false,
    val notes: String = "",
    val selfControl: SelfControlRoll? = null,
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
 * One row of the Seven Orthodox Gods Blessings table (Ch. 3, p. 19).
 *
 * Its own columns, not a trait's: the book prints Deity and Blessing Effect
 * and no third. There is no cost column at all -- the price is stated once in
 * prose for all seven rows, so it lives on [BlessingsFile.costEach] rather
 * than being repeated here, which is also the mechanism by which
 * `extract_content.py`'s cost-line parser has always been unable to see this
 * table.
 *
 * A blessing reaches every trait consumer as a [TraitDefinition] via
 * [asTrait]; the separate file and the separate type are what keep the
 * provenance visible, the call 6v made for the appendix's damage labels.
 */
@Serializable
data class Blessing(val deity: String, val effect: String) {
    /** This row as the trait a player buys, priced from the shared sentence. */
    fun asTrait(cost: Int): TraitDefinition =
        TraitDefinition(name = deity, cost = cost, notes = effect)
}

/**
 * The blessings table as printed: seven rows, one price, one requirement.
 *
 * [requirement] is a rule the GM applies and this app cannot -- it models no
 * faith and no reconciliation -- so it is committed and shown beside the rows
 * rather than gating them, the call the Sealed Artifact grade table needed
 * (6af).
 */
@Serializable
data class BlessingsFile(
    val costEach: Int,
    val requirement: String,
    val entries: List<Blessing>,
) {
    init {
        require(costEach > 0) { "a blessing costs points; costEach was $costEach" }
        require(requirement.isNotBlank()) { "the blessings table prints a requirement" }
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
 * One rung of the ritual COMPLEXITY axis (Ch. 7, Section IV.A and the Time &
 * Complexity Reference, p. 65-66).
 *
 * Not the same axis as [RitualWeight], and deliberately not merged with it.
 * The weight is the ritual's CATEGORY -- divination, curse, summoning -- and
 * gives its base difficulty modifier. The complexity is its SCOPE -- duration,
 * target count, territory -- and gives its SPI cost, base time and pacing.
 *
 * Section V claims the weight determines the SPI cost too, and the book's own
 * six worked rituals disagree with it three times while agreeing with this
 * ladder six times out of six: a Light divination charged 3 where the weight
 * column says 2, a Heavy summoning charged 7 where it says 8+. The sentence
 * loses to the examples. `ritual_weights.json` keeps its printed cost anyway,
 * and `extract_power_sources.verify()` fails if the two ever reconcile.
 *
 * Section V prints no Major weight at all, which is why Major carries no base
 * difficulty modifier: it is a complexity, not a weight.
 *
 * [spiOpen] here means an open TOP on a printed range ("11-15+"). That is not
 * the weight table's "8+", which prints no ceiling at all.
 */
@Serializable
data class RitualComplexity(
    val complexity: String,
    val looksLike: String,
    val baseTime: String,
    val rushed: Int,
    val extended: Int,
    val description: String,
    val spi: Int,
    val spiMax: Int? = null,
    val spiOpen: Boolean = false,
) {
    /** "2-3 SPI", "11-15+ SPI" -- the cell as the book prints it. */
    val printedCost: String get() = buildString {
        append(spi)
        spiMax?.let { append("-").append(it) }
        if (spiOpen) append("+")
        append(" SPI")
    }
}

/**
 * One tier of External Sacrifice (Ch. 7, Section IV.D, p. 66).
 *
 * Committed with its examples because a GM choosing a tier needs to know what
 * counts as Precious. The "+2 per sentient being" riding on top of these is a
 * number inside the same sentence, and lives in
 * [com.pathways.rules.PowerSources.SENTIENT_BONUS].
 */
@Serializable
data class SacrificeTier(val tier: String, val modifier: Int, val looksLike: String)

/** One difficulty's row of the skill cost grid (Ch. 3, p. 26). */
@Serializable
data class SkillLadderRung(val difficulty: String, val offsets: List<Int>)

/** One step of a worked skill walk-up (Ch. 3, p. 26). */
@Serializable
data class SkillLadderStep(val points: Int, val level: Int)

/** One of the book's two worked skill examples (Ch. 3, p. 26). */
@Serializable
data class SkillLadderExample(
    val skill: String,
    val attribute: String,
    val difficulty: String,
    val attributeLevel: Int,
    val steps: List<SkillLadderStep>,
)

/**
 * The skill cost ladder (Ch. 3, p. 25-26) -- [com.pathways.rules.SkillLadder]'s
 * own source table, extracted so something fails if the two ever drift.
 *
 * [cumulative] prints NINE rungs and every [rungs] row prints eight offsets:
 * the 256-point rung has a cost and no printed offset, and continuing it by
 * +1 is a reading the prose supports and the grid does not show.
 */
@Serializable
data class SkillLadderTable(
    val cumulative: List<Int>,
    val incremental: List<Int>,
    val rungs: List<SkillLadderRung>,
    val examples: List<SkillLadderExample>,
)

/** One row of a worked ritual's modifier table (Ch. 7, Section VIII). */
@Serializable
data class RitualExampleModifier(val source: String, val value: Int)

/**
 * One of Section VIII's worked rituals (Ch. 7, p. 69-72).
 *
 * The book doing its own arithmetic in public, seven times, for one stated
 * caster. `GoldenRitualTest` replays them against the engine the way
 * `GoldenSheetTest` replays the 23 character sheets -- as a PARTIAL oracle:
 * where a worked example disagrees with a printed table, the disagreement is
 * recorded and the table is not repaired from the example, nor the reverse.
 *
 * [baseDifficulty] is the ritual's own, and three of the seven print a -3 that
 * [RitualWeight]'s +0/-2/-4 cannot express. [spi] is its own too, and three
 * fall outside the weight table's cost column while all seven fall inside
 * [RitualComplexity]'s ladder -- which is the evidence that the ladder is the
 * operative one.
 */
@Serializable
data class RitualExample(
    val number: Int,
    val name: String,
    val category: String,
    val baseDifficulty: Int,
    val time: String,
    val modifiers: List<RitualExampleModifier>,
    val net: Int,
    val casterSkill: Int,
    val effectiveSkill: Int,
    val spi: Int,
    val printedDelta: Int,
)

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
 * One band of Step 4's outcome table (Ch. 7, p. 62) for a ritual that WORKED.
 *
 * The chapter grades a successful ritual in exactly one place. "VI. Failure &
 * Consequences" is what its name says and prints no success row at all, so
 * without this table a ritual that surpassed its intent and one that scraped
 * in are the same event.
 *
 * It governs BOTH modes despite sitting under the Quick Ritual, because Phase
 * III of the full ritual says so: "Then resolve using the same outcome table
 * as Quick Ritual (Step 4)" (p. 63).
 *
 * Read by margin, best-first, and the bands do not overlap -- unlike
 * [DegreeBand], where a margin of 5 satisfies three rows at once. That
 * general ladder is deliberately NOT layered on top of this one: each chapter
 * grades against the table it prints (6ad), and this is ch. 7's.
 */
@Serializable
data class RitualSuccess(
    val printed: String,
    val outcome: String,
    val from: Int,
    val to: Int? = null,
)

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
 * One circumstantial option in a boon-granting modifier bullet (Ch. 13, p. 144).
 *
 * [best] is the other end of "+1 to +3" and "-2 to -4"; [modifier] is always
 * the end nearer zero, so a caller that ignores the range is gentle rather
 * than harsh. A row with no [best] prints one number and takes no override.
 */
@Serializable
data class BoonModifier(
    val group: String,
    val label: String,
    val modifier: Int,
    val printed: String,
    val best: Int? = null,
) {
    /**
     * A stable identity for this option. The label alone is not one: nothing
     * in the book or the extractor stops the two groups printing the same
     * words, and a GM's chosen value keyed by label would then move both.
     */
    val key: String get() = "$group / $label"
}

/**
 * The fixed parameters of the boon granting ritual (Ch. 13, p. 144-145).
 *
 * [complexity] and [weight] are two different axes and the book prints them
 * one line apart: complexity is TIME ("Heavy (1 hour base time)"), weight is
 * what the roll uses ("Moderate (-2 difficulty, 4-6 SPI required)"). Reading
 * the first as the second rolls at -4 and charges 8+ SPI.
 *
 * [boonLevelRule] is carried whole rather than parsed into rows: the book
 * prints three examples and then a rule, and three rows would have meant
 * inventing Sequence 6. The ladder lives in [com.pathways.rules.Boons].
 */
@Serializable
data class BoonGranting(
    val skill: String,
    val attribute: String,
    val difficulty: String,
    val complexity: String,
    val complexityTime: String,
    val weight: String,
    val weightModifier: Int,
    val spi: Int,
    val spiMax: Int,
    val boonLevelRule: String,
    val requirements: List<String>,
    val consequences: List<String>,
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
 * Prose, not signed numbers. This KDoc used to give the reason as "'Acrobatic
 * Dodge' grants +2 on a success and -2 on a failure, so there is no single
 * figure to extract" -- an excuse drawn from ONE row and generalised to the
 * table, exactly like the "no arc to test against" sentence 6ck found in three
 * files. Two of the five bullets print exactly one number each, and the
 * Acrobatic Dodge row's two are now applied by
 * [com.pathways.rules.ActiveDefenses.acrobaticDodge], keyed off a real roll.
 *
 * What is true is narrower: the bullets are a LIST, and which of them the app
 * applies is a per-bullet decision. [com.pathways.rules.ActiveDefenses
 * .unappliedModifiers] is the half that is still only printed.
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
    /**
     * The lower bound the Result cell prints ("Success by 5+" is 5), with
     * [isFailure] saying which side of the table it sits on. They exist so the
     * number that SELECTS a row travels ON the row -- the shape
     * `MajorWound.failBy`, `RitualFailure.from` and `SpiritualSenseRow.margin`
     * already have. [com.pathways.rules.Divination.clarityFor] held 5 and 3 as
     * Kotlin literals and reached its rows by array position, the only
     * margin-reader in this codebase that did. Bands overlap by design, so the
     * DEEPEST match wins.
     *
     * The critical row carries neither, being named by outcome: printing no
     * margin is not printing zero, the distinction [RitualCritical.corGained]
     * draws for corruption.
     */
    val margin: Int? = null,
    val isFailure: Boolean = false,
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
 * One printed reason a character gets NO active defence (Ch. 5).
 *
 * [cause] is ours -- the book prints three sentences in three sections and
 * names no key for any of them -- and [printed] is the book's, quoted so the
 * GM reads the rule rather than our paraphrase of it.
 */
@Serializable
data class DefenseRefusalRule(val cause: String, val printed: String)

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
 * a number that belongs to the weapon being fired, not to the row, so they
 * carry no [modifier] at all. The cell's "(see weapon table)" points at
 * chapter 8, which [CataloguedWeapon] now reads. [accBonus] is what the row
 * adds on top of that Acc: 0 for the short aim and +2 for the long one.
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
    /** What the row adds ON TOP of the weapon's Acc; 0 for the bare Aim row. */
    val accBonus: Int = 0,
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

/**
 * One way a weapon can hurt someone: "sw-1 cut", "2d pi".
 *
 * [base] is an ST column ("sw", "thr") or a fixed die count ("2d"). The
 * difference is the whole point: a sword's damage grows with the arm swinging
 * it and a revolver's does not.
 */
@Serializable
data class WeaponMode(
    val base: String,
    val modifier: Int,
    val type: String,
    val printed: String,
) {
    /** True when [base] names a column of the thrust/swing table. */
    val isFromSt: Boolean get() = base == "sw" || base == "thr"
}

/**
 * A row of the Weapons table (Ch. 8, p. 80).
 *
 * Named apart from [com.pathways.model.Weapon] on purpose: that is a weapon a
 * character is carrying, this is a line in the book's catalogue.
 *
 * [damage] holds every mode the row prints -- two for the Combat Knife and the
 * Broadsword, which can cut or thrust. [halfRange]/[maxRange] are null for a
 * melee weapon, whose Range cell is a reach letter ("C", "C/1") and not a
 * distance in metres.
 */
@Serializable
data class CataloguedWeapon(
    val name: String,
    val damage: List<WeaponMode>,
    val acc: Int,
    val range: String,
    val halfRange: Int? = null,
    val maxRange: Int? = null,
    val cost: String,
    val skill: String,
    val notes: String = "",
)

/**
 * One entry of Appendix A's Wounding quick-reference line (p. 159).
 *
 * [abbreviation] is present only for the piercing group, which is the one
 * place the appendix's spelling matches chapter 5's own ("Pi" / "pi"). The
 * other five cells print short names ("Crush" where chapter 5 abbreviates
 * "cr"), so they carry none rather than a second spelling for one type.
 *
 * [multiplier] duplicates chapter 5 deliberately: `extract_appendix.py`
 * asserts the two agree, and that agreement is what makes adopting the
 * appendix's label a reading rather than a repair.
 */
@Serializable
data class DamageAbbreviation(
    val type: String,
    val multiplier: Double,
    val printedAs: String,
    val abbreviation: String? = null,
)

/**
 * One row of the Study & Training time-and-money table (Ch. 3, p. 32).
 *
 * A study session earns "1 character point locked to that skill", so the CP
 * half of a plan is [com.pathways.rules.SkillLadder]'s job and this table only
 * prices the point. See [com.pathways.rules.Study].
 */
@Serializable
data class StudyCost(
    val difficulty: String,
    val tutor: StudyPrice,
    val selfStudy: StudyPrice,
)

/**
 * What one study point costs, or the cell that refuses to price it.
 *
 * Very Hard self-study prints "Not possible", so it carries [printed] and
 * NOTHING ELSE -- no hours, no money. Printing no cost is not printing zero,
 * the distinction [RitualCritical.corGained] draws for corruption, and here it
 * has teeth: read as free it would make the hardest road the cheapest.
 *
 * [amount] and [unit] are the money exactly as printed, in the two currencies
 * the column mixes ("4s" and "£1"). Nothing in this repo converts between
 * them -- the same call the wealth ladder made (6u).
 */
@Serializable
data class StudyPrice(
    val printed: String,
    val hours: Int? = null,
    val amount: Int? = null,
    val unit: String? = null,
)

/** One sentence of the Study & Training prose: a requirement or a Limit. */
@Serializable
data class StudyRule(val rule: String)

/**
 * One row of Starting Wealth by Economic Status (Ch. 8, p. 80).
 *
 * [points] is SIGNED, exactly as the table prints it ("Dead Broke (-25 pts)"),
 * while [com.pathways.model.Trait.cost] is a magnitude whose direction lives
 * in its kind. [com.pathways.rules.Wealth.traitFor] is the one place the two
 * meet; applying both conventions would double the sign.
 *
 * [reactionModifier] is the printed column, which is not symmetric around
 * Average -- see [com.pathways.rules.Wealth].
 */
@Serializable
data class WealthLevel(
    val status: String,
    val points: Int,
    val startingMoney: String,
    val reactionModifier: Int,
    val lifestyle: String,
)

/**
 * One band of the Entity Response Table (Ch. 7, Section IV.F, p. 66).
 *
 * [ritualModifier] and [automaticFailure] are BOTH absent on the 12-14 row and
 * that is deliberate: "No response. Ritual draws only on caster's residual
 * power" is an entity that does not answer, so nothing is added and nothing
 * fails. The 18 row carries [automaticFailure] and no modifier -- it is a
 * refusal, not a penalty, the same way a Broken mystical language forbids the
 * attempt rather than pricing it.
 *
 * See [com.pathways.rules.Invocation], which also carries the orthodox shift:
 * a -2 on the roll AGAINST this table, never on the ritual roll.
 */
@Serializable
data class EntityResponse(
    val low: Int,
    val high: Int,
    val response: String,
    val ritualModifier: Int? = null,
    val automaticFailure: Boolean = false,
)

/**
 * One supporting skill for a ritual (Ch. 7, Section II, p. 61).
 *
 * Each grants +1 and the five together are capped at +3 by a sentence, not a
 * column -- see [com.pathways.rules.RitualMode.SUPPORT_CAP].
 */
@Serializable
data class RitualSupportSkill(val skill: String, val bonus: Int, val notes: String)

/**
 * One row of the Quick Ritual's Target Link table (Ch. 7, p. 62).
 *
 * QUICK MODE ONLY. The Full Ritual folds the link into its single Preparation
 * Modifier -- its rows say "perfect link", "strong link", "weak link" -- so
 * adding this beside it pays a perfect link twice.
 * [com.pathways.rules.RitualMode.modifierTotal] refuses that.
 *
 * [modifier] is the end of a printed range nearer zero, [best] the other end,
 * exactly as [RitualPreparation] reads its own ranges.
 */
@Serializable
data class TargetLink(
    val link: String,
    val modifier: Int,
    val printed: String,
    val best: Int? = null,
    val looksLike: String,
)

/** One language, common or mystical (Ch. 3, p. 13-14). */
@Serializable
data class LanguageEntry(val type: String, val name: String, val notes: String)

/**
 * One row of a language proficiency table (Ch. 3, p. 13-14).
 *
 * There are TWO such tables and they print the same three [proficiency] names
 * at different [points] -- 6/4/2 common, 30/20/10 mystical. [type] is the only
 * thing telling them apart, so it is part of every lookup; keyed on the name
 * alone a mystical Native costs a fifth of what the book charges.
 *
 * [canPerformRituals] and [ritualModifier] are null on every COMMON row,
 * because that table says nothing about rituals -- which is not the same as
 * permitting one at no penalty. Mystical Broken carries `false` and no
 * modifier: "Cannot perform rituals in the language", and a penalty would
 * imply a roll exists to modify. See [com.pathways.rules.Languages].
 */
@Serializable
data class LanguageProficiency(
    val type: String,
    val proficiency: String,
    val points: Int,
    val effect: String,
    val canPerformRituals: Boolean? = null,
    val ritualModifier: Int? = null,
)

/** A prose rule printed with the language tables, carried as its sentence. */
@Serializable
data class LanguageNote(val text: String)

/**
 * One row of the Appearance table (Ch. 3, p. 13-14).
 *
 * [points] is SIGNED like [WealthLevel.points], and meets
 * [com.pathways.model.Trait]'s magnitude-plus-kind convention in exactly one
 * place, [com.pathways.rules.Appearance.traitFor].
 *
 * [reactionModifier] is the printed column and is NOT derivable from [points]:
 * the bad half skips -12, so -8 pts gives -2 and -16 gives -4. Both columns
 * are read for that reason.
 */
@Serializable
data class AppearanceLevel(
    val level: String,
    val points: Int,
    val reactionModifier: Int,
)

/**
 * One entry of the Glossary of Terms (p. 167-168).
 *
 * Reference, not rules: every term here names something the engine already
 * implements or something the setting already assumes, so nothing on this
 * type reaches a roll. [term] carries the book's spelling verbatim, including
 * `SPI — Spirituality`'s em-dash, and the list is kept in the book's printed
 * order, which is not alphabetical. A screen that wants A-Z sorts its own copy.
 */
@Serializable
data class GlossaryEntry(val term: String, val definition: String)

/** Raw thrust/swing row exactly as read from the ThrustTable spreadsheet tab. */
@Serializable
data class DamageRowJson(val st: Int, val thrust: String, val swing: String)

/** [DamageRowJson] with thrust/swing parsed into [com.pathways.model.DiceExpr]. */
data class DamageRow(val st: Int, val thrust: com.pathways.model.DiceExpr, val swing: com.pathways.model.DiceExpr)

/**
 * One row of Appendix A's Common Modifiers table (p. 159) -- the general
 * circumstances a GM applies to any skill roll.
 *
 * [modifier] is the end NEARER ZERO of whatever the row prints, so a range
 * ("+1 to +2") and an open end ("-6 or worse") both offer the gentle value
 * and keep the other bound in [otherEnd] or [isOpenEnded]. A GM choosing
 * inside a range is validated against the printed ends rather than against
 * nothing.
 *
 * [isDefault] marks the one row that is NOT a modifier: "Using untrained
 * skill / Attribute -4 to -6" replaces the roll with an attribute default
 * rather than adjusting a skill, and it carries no [modifier] at all --
 * printing no value is not the same as printing zero.
 */
@Serializable
data class CommonModifier(
    val situation: String,
    val printed: String,
    val modifier: Int? = null,
    val otherEnd: Int? = null,
    val isOpenEnded: Boolean = false,
    val isDefault: Boolean = false,
)

/**
 * One row of Appendix A's degree-of-success ladder (p. 159).
 *
 * A row is named EITHER by the roll itself ([rollLow]..[rollHigh], the two
 * "always" rows) OR by a comparison against the target ([comparison] and
 * [offset]) -- never both, which is why each pair is nullable rather than the
 * row carrying a zero it does not print. The same two-doors shape as the
 * divination clarity table and the ritual critical table.
 *
 * The roll-under rows OVERLAP by construction: a roll of target-5 satisfies
 * all three of them. The table prints them least- to most-demanding and the
 * last match is the answer, so read this list in order and keep the final
 * row that fits.
 */
@Serializable
data class SuccessBand(
    val result: String,
    val rollLow: Int? = null,
    val rollHigh: Int? = null,
    val comparison: String? = null,
    val offset: Int? = null,
)

/**
 * One row of the Sealed Artifact grade table (Ch. 6, p. 47).
 *
 * **[churchAccess] is the gate and [powerEquivalent] is not.** Every access
 * cell names an institutional RANK; the power column says how strong the
 * artifact is, in Sequence terms, and the book nowhere requires a wielder to
 * match it. Read through [com.pathways.rules.SealedArtifacts], which shows
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
 * One ability of a Sealed Artifact (Ch. 6, p. 49-53).
 *
 * [spi] is the printed cost and the one number the app spends. [perTurn] marks
 * the gauntlet's False Majesty, whose cell reads "1 SPI/turn" -- maintained,
 * not paid once.
 *
 * [hpCost] is present only for the one ability whose own effect cell charges HP
 * unconditionally (the candle's Extinguish Memory). Absent means the ability
 * charges no HP, which is not the same as charging zero.
 */
@Serializable
data class ArtifactAbility(
    val name: String,
    val spi: Int,
    val perTurn: Boolean = false,
    val effect: String,
    val hpCost: Int? = null,
)

/**
 * One documented Sealed Artifact (Ch. 6, p. 49-53).
 *
 * [hpPerUse] is the gauntlet's Soul's Price -- "Each use of any ability costs
 * 1 HP in addition to SPI" -- and is absent on the other three. Three of the
 * four print an HP number SOMEWHERE in [drawbacks], but the other three are
 * conditional on lying, on touching the flame, or on a use count, so they are
 * quoted for the GM and never charged.
 *
 * [grade] and [sequenceEquivalent] are shown, never enforced: see
 * [SealedArtifactGrade].
 */
@Serializable
data class SealedArtifact(
    val code: String,
    val name: String,
    val pathway: String,
    val grade: Int,
    val sequenceEquivalent: Int,
    val appearance: String,
    val abilities: List<ArtifactAbility>,
    val drawbacks: List<String>,
    val sealing: String,
    val hpPerUse: Int? = null,
)

/**
 * One row of Ch. 6.5's Sequence Immunity table (Sec. 5, p. 59-60).
 *
 * [penalty] is what a weaker diviner takes; [equalPenalty] and [linkedPenalty]
 * are printed by one row each and are absent elsewhere, because printing no
 * value is not printing zero.
 *
 * **[requiresCritical] is a SECOND rule, not a restatement of [penalty].** Seq
 * 4 and Seq 3 print "only a critical success (3-4) can succeed -- the penalty
 * cannot be offset by skill alone", which forbids the ordinary success a
 * skill-20 diviner would otherwise buy at -10. Seq 2 prints -10 and no such
 * clause; that asymmetry is the book's and is recorded, never repaired.
 */
@Serializable
data class SequenceImmunity(
    val label: String,
    val sequenceLow: Int,
    val sequenceHigh: Int,
    val requiresCritical: Boolean = false,
    val text: String,
    val penalty: Int? = null,
    val equalPenalty: Int? = null,
    val linkedPenalty: Int? = null,
)

/**
 * One way a diviner pierces anti-divination (Ch. 6.5, Sec. 6, p. 60).
 *
 * [ignoresPenalty] keeps the sign the book prints ("ignores -2 of
 * anti-divination penalties"), so a caller subtracts it rather than guessing a
 * convention. Only HP Sacrifice carries numbers the app applies; the ritual and
 * the deity are the GM's, and a field on them would imply otherwise.
 */
@Serializable
data class AntiDivinationCounter(
    val name: String,
    val text: String,
    val ignoresPenalty: Int? = null,
    val hpRequired: Int? = null,
)

/**
 * One pathway that feels a divination coming (Ch. 6.5, Sec. 1, p. 57).
 *
 * The row's primary output is a GATE, not a number: [isAutomatic] means no
 * roll is needed, [freeRoll] names the attribute when the book grants one
 * anyway, and a row with neither grants nothing.
 *
 * [bonusText] travels with [bonus] because three of the four printed bonuses
 * are scoped to what the divination is ABOUT ("related to their hidden
 * knowledge"), which this app cannot test. The scope belongs beside the value,
 * the same reason [PoolThreshold] carries its own.
 *
 * [divinerPenalty] appears on exactly one row and is not awareness at all --
 * Wheel of Fortune's passive luck penalises the DIVINER. Recorded here so the
 * cross-axis row stays visible; nothing applies it. See
 * [com.pathways.rules.Detection].
 */
@Serializable
data class DivinationAwareness(
    val pathway: String,
    val sequence: String,
    val detection: String,
    val isAutomatic: Boolean = false,
    val freeRoll: String? = null,
    val bonus: Int? = null,
    val bonusText: String? = null,
    val divinerPenalty: Int? = null,
)

/**
 * What noticing a divination tells the target (Ch. 6.5, Sec. 2, p. 57).
 *
 * Four rows are named by [outcome] and one by [margin] -- the two doors
 * [com.pathways.rules.FrightCheck], [com.pathways.rules.RitualResolution] and
 * [com.pathways.rules.Wounds] each read the wrong way round once.
 */
@Serializable
data class DetectionOutcome(
    val result: String,
    val outcome: String,
    val effect: String,
    val margin: Int? = null,
)

/** One circumstance on the target's detection roll (Ch. 6.5, Sec. 2, p. 57). */
@Serializable
data class DetectionModifier(val situation: String, val modifier: Int)

/**
 * One active anti-divination technique (Ch. 6.5, Sec. 3, p. 58-59).
 *
 * [divinationPenalty] is what the DIVINER takes; [detectionBonus] is what the
 * target gains on its own roll. 3A prints both, which is why the two are
 * separate fields rather than one signed number.
 *
 * [spi] is absent rather than zero for 3F, which prices itself as "SPI equal
 * to what a divination of that scope would normally cost" -- a rule, not a
 * number, the distinction [RitualCritical.corGained] already draws.
 */
@Serializable
data class AntiDivinationTechnique(
    val id: String,
    val name: String,
    val availability: String,
    val bullets: List<TechniqueBullet>,
    val spi: Int? = null,
    val divinationPenalty: Int? = null,
    val detectionBonus: Int? = null,
) {
    fun bullet(label: String): String? = bullets.firstOrNull { it.label == label }?.text
}

/** One labelled line of a technique's printed rules ("Cost", "Duration", ...). */
@Serializable
data class TechniqueBullet(val label: String, val text: String)

/**
 * One item that protects against divination (Ch. 6.5, Sec. 4, p. 59).
 *
 * [ritualDetectionPenalty] is deliberately not [detectionBonus]'s opposite:
 * the candle suppresses "detection of that ritual", which is sensing a ritual
 * being performed, not the being-divined roll Sec. 2 defines. Two keys so a
 * caller cannot sum them.
 */
@Serializable
data class AntiDivinationItem(
    val name: String,
    val effect: String,
    val cost: String,
    val detectionBonus: Int? = null,
    val ritualDetectionPenalty: Int? = null,
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

/**
 * One band of Ch. 2's own success-roll list (p. 7).
 *
 * Corroboration, not a rule the app applies: these are the same three edges
 * Appendix A names Good and Excellent (6ad), printed unnamed one chapter in.
 * [low] and [high] are absent on the two rows named by the roll itself, and
 * [high] alone is absent on "Success by 5+", which prints no ceiling.
 */
@Serializable
data class CoreBand(
    val outcome: String,
    val meaning: String,
    val low: Int? = null,
    val high: Int? = null,
)

/**
 * One pool that can be spent to push a roll (Ch. 2, p. 9).
 *
 * [max] is the FP row's "+5 per roll" and is ABSENT on the SPI row, which
 * says "There is no cap" in words. Read through
 * [com.pathways.rules.EffortBurn].
 */
@Serializable
data class BurnablePool(
    val pool: String,
    val kind: String,
    val scope: String,
    val perPoint: Int = 1,
    val max: Int? = null,
)

/** One worked Quick Contest (Ch. 2, p. 9) -- who rolls what against whom. */
@Serializable
data class QuickContest(
    val situation: String,
    val attacker: String,
    val defender: String,
)

/**
 * One untrained default (Ch. 2, p. 9-10) -- the PRIMARY printing of the table
 * Appendix A's Common Modifiers restates as "Attribute -4 to -6" (6ae).
 *
 * [penalty] is absent for Very Hard, which prints "No default -- cannot
 * attempt untrained". A refusal, not a number, so it carries none rather than
 * a large one.
 */
@Serializable
data class SkillDefault(
    val difficulty: String,
    val code: String,
    val penalty: Int? = null,
)

/**
 * A skill one of chapter 8's equipment rows names in a signed clause.
 *
 * [specialization] is part of the identity, not a decoration: the compass
 * reads "Navigation (Land) +1", and a character rolling the bare Navigation
 * row is rolling a different skill. Compared against
 * [com.pathways.model.SkillKey]'s own two fields, never against the name.
 */
@Serializable
data class EquipmentSkill(
    val name: String,
    val specialization: String? = null,
)

/**
 * One row of the Common Equipment table (Ch. 8, p. 81-83).
 *
 * [notes] is the book's cell VERBATIM, and no number is derived from it.
 * Nothing here records possession -- the 23 fixtures carry no equipment at
 * all -- so no bonus can be applied to a roll without the screen asserting
 * the character owns the item. It is shown and left to the GM, the same
 * standing the roll dialog already gives Occultism's conditional push.
 *
 * [skills] is a discovery index answering "which rows should a Search roll
 * put in front of the player", built only from SIGNED clauses. "Lock picks
 * (set)" says "Required for Lockpicking skill" and joins nothing, because a
 * mention is not a bonus.
 */
@Serializable
data class EquipmentItem(
    val name: String,
    val cost: String,
    val category: String,
    val notes: String,
    val skills: List<EquipmentSkill> = emptyList(),
)

/**
 * One row of the Secondary Characteristics table (Ch. 2, p. 8-9).
 *
 * The only place the book prices modifying a secondary -- "Cost to Modify"
 * occurs once in the whole text, at line 357. [SecondaryCharacteristics]'s
 * cost constants had no tie to it until this row existed, so a re-extraction
 * that moved a price would have left the engine billing the old one.
 *
 * [pointsPerStep] is null for the three rows the table refuses to price
 * (Spirituality "Fixed at 0", CoR "never bought", Digestion "Tracked through
 * acting"), and [cost] then carries the book's own words for the refusal.
 *
 * [step] is "level" everywhere except Basic Speed, which the book prices per
 * 0.25. Dropping that unit would price a whole point of Speed at 5 instead of
 * 20, which is why [com.pathways.model.SecondaryDelta] counts it in quarters.
 */
@Serializable
data class SecondaryCost(
    val key: String,
    val characteristic: String,
    val formula: String,
    val cost: String,
    val pointsPerStep: Int? = null,
    val step: String? = null,
)

@Serializable
data class ContentFile<T>(
    val sourceLines: Map<String, List<List<Int>>> = emptyMap(),
    val entries: List<T>,
)
