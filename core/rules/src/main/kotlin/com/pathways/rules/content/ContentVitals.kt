package com.pathways.rules.content

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
 * One Natural Recovery row of the HP recovery table (Ch. 5, p. 42).
 *
 * Three printed cells, kept verbatim and never turned into arithmetic:
 * "2× base rate" for "20–29 HP" does not say whether the band is read on
 * maximum or current HP, and "scales proportionally" prints no band past 39.
 * [com.pathways.rules.NaturalRecovery] offers the first row's roll and prints
 * the rest beside it. See tools/extract_recovery.py for the three methods and
 * the Regeneration table this file leaves out.
 */
@Serializable
data class HpRecoveryRow(val method: String, val recovered: String, val notes: String) {
    init {
        require(method.isNotBlank() && recovered.isNotBlank() && notes.isNotBlank()) {
            "recovery row '$method' has an empty cell"
        }
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
    val frightRecovery: FrightRecovery? = null,
    val aweRecovery: FrightRecovery? = null,
    val confusionRecovery: FrightRecovery? = null,
    val frightComputedDuration: ComputedDuration? = null,
    val aweComputedDuration: ComputedDuration? = null,
    val confusionComputedDuration: ComputedDuration? = null,
) {
    init {
        require(high == null || high >= low) { "fright band '$printed' runs $low..$high" }
        val present = listOfNotNull(frightRecovery, aweRecovery, confusionRecovery).size
        require(present == 0 || present == 3) {
            "fright band '$printed' offers a recovery roll in $present of 3 columns; " +
                "the table prints one in every column or in none"
        }
        val computed = listOfNotNull(
            frightComputedDuration, aweComputedDuration, confusionComputedDuration,
        ).size
        require(computed == 0 || computed == 3) {
            "fright band '$printed' computes a duration in $computed of 3 columns; " +
                "the one band that computes one does so in every column"
        }
    }
}

/**
 * A duration one Fright Effects cell works out from the character.
 *
 * "Retching for (25 - HT) sec", "Ecstasy for (25 - Will) sec", "Dazed for
 * (25 - IQ) sec" -- row 12's three cells, and the only durations in the table
 * with a stat operand. **The other two printed durations stay prose on
 * purpose**: rows 4-9 print "1 sec", already resolved in the cell, and 10-11
 * print "1d-2d sec", which is a range the GM rolls rather than a number the
 * app can derive. So this is not "the table's duration" structured -- it is
 * the subset a character's own sheet answers, which is the boundary the table
 * itself draws.
 *
 * **The operand is not the attribute the same cell ROLLS.** The fright cell
 * counts down from HT and rolls Will, which is why this cannot ride on
 * [FrightRecovery]: `extract_fright.py` anchors on the printed parenthetical
 * and a guard there fails the day the two anchors answer the same attribute.
 */
@Serializable
data class ComputedDuration(val base: Int, val attribute: DurationAttribute)

/**
 * What a Fright Effects cell counts its duration down from, in the book's own
 * name.
 *
 * Three members against [RecoveryAttribute]'s two, and the extra one is the
 * whole point: HT is named by row 12's fright duration and by no recovery roll
 * in the table. Widening [RecoveryAttribute] to hold it would put a value in
 * [com.pathways.rules.FrightCheck.levelFor]'s exhaustive `when` that no
 * recovery roll can ever take.
 */
@Serializable
enum class DurationAttribute(
    /**
     * The book's own spelling, so the screen never writes its own.
     *
     * The same string the serial name carries: a dialog that spelled these
     * itself would be a second copy of a decision, which is how this repo's
     * numbers have twice stopped reconciling.
     */
    val printed: String,
) {
    /** Row 12, Fright -- the column whose duration and roll disagree. */
    @SerialName("HT")
    HT("HT"),

    /** Row 12, Awe, which prints "Will" in both roles. */
    @SerialName("Will")
    WILL("Will"),

    /** Row 12, Confusion. */
    @SerialName("IQ")
    IQ("IQ"),
}

/**
 * The way out of a Fright Effects cell, structured by `tools/extract_fright.py`.
 *
 * **The attribute is the COLUMN's, never the state's.** Rows 4-9, 10-11 and 12
 * all roll Will in the Fright and Awe columns and IQ in Confusion, whatever
 * they leave the character as — so a screen cannot infer it from the outcome.
 * The extractor anchors on the cell's own "roll vs. X" and never on an
 * attribute name, because two of row 12's three cells name an attribute they
 * do NOT roll: its fright cell counts down from HT, and its awe cell prints
 * "Will" once as a duration and once as the roll.
 *
 * **[state] is the book's own leading word, decoded rather than matched.** The
 * extractor reads the prose and this ships the result, so no rules code has to
 * be a second authority on what the cell says (6dh). What reads it is Combat
 * Reflexes' "+6 to recover from stun", which reaches [RecoveryState.STUNNED]
 * and none of the other three.
 *
 * The duration each cell prints is deliberately unread — "1d-2d sec",
 * "(25 - HT) sec" — because this app holds no clock. The cell's own sentence
 * is shown beside the roll instead.
 */
@Serializable
data class FrightRecovery(val attribute: RecoveryAttribute, val state: RecoveryState)

/**
 * What a Fright Effects column rolls to recover, in the book's own name.
 *
 * A closed set rather than a string, so that a reader has to answer for every
 * member: with a string, "is it IQ" reads every other value as Will, and this
 * table is exactly where that goes wrong — two of row 12's cells NAME an
 * attribute they do not roll.
 */
@Serializable
enum class RecoveryAttribute {
    /** The Fright and Awe columns, all three rows that offer a way out. */
    @SerialName("Will")
    WILL,

    /** The Confusion column. */
    @SerialName("IQ")
    IQ,
}

/**
 * What a Fright Effects row leaves the character as, in the book's own word.
 *
 * A closed set because the table prints exactly these four across its three
 * rows that offer a way out, and an unknown fifth is a re-extraction that
 * drifted rather than a state to render.
 */
@Serializable
enum class RecoveryState {
    /** Rows 4-9 and 10-11, all three columns. The one a stun clause reaches. */
    @SerialName("Stunned")
    STUNNED,

    /** Row 12, Fright. */
    @SerialName("Retching")
    RETCHING,

    /** Row 12, Awe. */
    @SerialName("Ecstasy")
    ECSTASY,

    /** Row 12, Confusion. */
    @SerialName("Dazed")
    DAZED,
}
