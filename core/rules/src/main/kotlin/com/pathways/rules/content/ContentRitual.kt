package com.pathways.rules.content

import kotlinx.serialization.Serializable

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
 * One printed incantation structure (Ch. 7 p. 63/65, Ch. 7.5 p. 76).
 *
 * [parts] is the four-part Hermes structure's own enumeration and is empty for
 * the two the book describes in prose. [note] is what the page prints AFTER
 * the parts, so a screen can render intro, parts and note in that order.
 *
 * [referenceOnly] marks a structure no choice in this app selects: it is shown
 * and never chosen. See `Incantations.forPowerSource`.
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
 * exactly as [RitualPreparation] reads its own ranges -- and, as there, the
 * GM picks between them through [com.pathways.rules.RitualMode.valueFor].
 */
@Serializable
data class TargetLink(
    val link: String,
    val modifier: Int,
    val printed: String,
    val best: Int? = null,
    val looksLike: String,
)
