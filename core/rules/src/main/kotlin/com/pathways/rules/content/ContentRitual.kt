package com.pathways.rules.content

import kotlinx.serialization.Serializable

/**
 * One row of the ritual weight table (Ch. 9 II-III.A, v6.9i): its base
 * modifier, base time and the SPI that fuels it.
 *
 * v6.9i's Quick table is the one fuel column: the complexity ladder beside it
 * prints time and pacing only, and the worked rituals that once argued for
 * charging off that ladder are gone. Personal Spirituality's "2-15+ SPI"
 * disagrees with Major's open "12+"; the table wins, recorded in
 * `PowerSourcesTest`.
 */
@Serializable
data class RitualWeight(
    val weight: String,
    val modifier: Int,
    val baseTime: String,
    /** Null on Major, the one row v6.9i's Quick table adds without examples. */
    val categories: String? = null,
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
 * One rung of the Time & Complexity table (Ch. 9 III.B, v6.9i): base time and
 * what rushing or extending it does to the roll.
 *
 * The same four names as [RitualWeight], and read off the weight of the same
 * name ([com.pathways.rules.PowerSources.complexityFor]). The fuel is the
 * weight's: this table prints no SPI.
 */
/**
 * One row of Ch. 9's Revelation Penalties (v6.9i 4008-4024): the entity a
 * ritual petitions for revelations, its penalty, and what reduces it.
 * [perSacrifice] is the Seven Lights' "-5 per sacrifice"; [reducibleTo] the
 * deities' -10, which needs an advantage no table prints.
 */
@Serializable
data class RitualRevelation(
    val entity: String,
    val penalty: Int,
    val reduction: String,
    val perSacrifice: Int? = null,
    val reducibleTo: Int? = null,
)

/** Ch. 9 VI's Spirit World Creature Contract (v6.9i): one ritual, whole. */
@Serializable
data class RitualContract(
    val difficulty: String,
    val spi: Int,
    val time: String,
    val setup: String,
    val maxContracts: Int,
    val maxContractsScrollsProfessor: Int,
    val methods: List<ContractMethod>,
    val outcomes: List<ContractOutcome>,
    val demands: String,
)

@Serializable
data class ContractMethod(val method: String, val name: String, val text: String)

/** A margin band ([from]..[to], open-ended when [to] is null) or a failure row. */
@Serializable
data class ContractOutcome(
    val printed: String,
    val outcome: String,
    val from: Int? = null,
    val to: Int? = null,
    val failure: Boolean = false,
    val critical: Boolean = false,
)

@Serializable
data class RitualComplexity(
    val complexity: String,
    val baseTime: String,
    val rushed: Int,
    val extended: Int,
)

/**
 * One row of the Phase I Preparation Quality table (Ch. 7, p. 63).
 *
 * Three of the five carry something a single number cannot say: [best] is the
 * other end of "+1 to +2" and "-1 to -2", and [openEnded] marks "-4 or worse",
 * which is a floor rather than a value. [modifier] is always the end nearer
 * zero, and it is what an UNPICKED row applies: this app's default, not the
 * book's rule. The GM's pick inside the cell goes through
 * [com.pathways.rules.RitualMode.valueFor], which holds it to the cell (6ef).
 */
@Serializable
data class RitualPreparation(
    val quality: String,
    val modifier: Int,
    val printed: String,
    val best: Int? = null,
    val openEnded: Boolean = false,
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
     * and by test_tools_corruption_ritual.py.
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
 * One printed incantation structure (Ch. 7 p. 63/65, Ch. 7.5 p. 76).
 *
 * [parts] is the four-part Hermes structure's own enumeration and is empty for
 * the two the book describes in prose. [note] is what the page prints AFTER
 * the parts, so a screen can render intro, parts and note in that order.
 *
 * [referenceOnly] marks a structure no choice in this app selects: it is shown
 * and never chosen. See `Incantations.standard`.
 */
@Serializable
data class Incantation(
    val structure: String,
    val scope: String,
    val text: String,
    val parts: List<String> = emptyList(),
    val note: String? = null,
    val referenceOnly: Boolean = false,
)

/**
 * One row of the Quick Ritual's Target Link table (Ch. 7, p. 62).
 *
 * QUICK MODE ONLY. The Full Ritual folds the link into its single Preparation
 * Modifier -- its rows say "perfect link", "strong link", "weak link" -- so
 * adding this beside it pays a perfect link twice.
 * [com.pathways.rules.RitualMode.modifierTotal] refuses that.
 *
 * [modifier] is the end of a printed range nearer zero, [best] the other end,
 * exactly as [RitualPreparation] reads its own ranges -- and, as there, the
 * GM picks between them through [com.pathways.rules.RitualMode.valueFor].
 */
@Serializable
data class TargetLink(
    val link: String,
    val modifier: Int,
    val printed: String,
    val best: Int? = null,
    /** Null on None, the one row v6.9i prints without an example. */
    val looksLike: String? = null,
)
