package com.pathways.rules.content

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
    val thresholds: List<HitLocationThreshold> = emptyList(),
)

/**
 * The hit-location table plus the paragraph that governs its effect column.
 *
 * `loadWhole`, because [cripplingRule] is one rule for all nine rows and
 * [ContentLoader.load] would drop it -- the shape [BlessingsFile] already has
 * for the same reason.
 */
@Serializable
data class HitLocationFile(
    val cripplingRule: String,
    val entries: List<HitLocation>,
) {
    init {
        require(cripplingRule.isNotBlank()) { "the crippling rule is a printed paragraph" }
    }
}

/**
 * One computable clause of a hit location's `Special Effect on Hit` cell.
 *
 * Parsed by `tools/extract_wounds.py`, never here: the cell is prose, and a
 * matcher in the engine would be a second authority on what the book says.
 * Five of the nine rows carry these; the other four print effects this app
 * refuses, each for its own reason recorded in production Kotlin.
 *
 * Exactly one of the two conditions is normally set, and both may be absent —
 * the Hand's cell prints "Drop held item" bare where the Arm's prints
 * ">= 3", so an unconditional clause fires on any damage that lands.
 *
 * [minDamage] is read as "at least", [hpDivisor] as "more than", because the
 * book writes them that way: ">= 3" against "> HP/6".
 *
 * [approximate] marks a divisor the BOOK itself offers as an approximation.
 * Arm and Leg print the governing rule as "> 1/2 HP of limb" and give
 * "~ HP/3" as its equivalent in character HP; Hand, Neck and Eye print a
 * character-HP threshold outright. Without this flag a reader would take the
 * approximation for the rule.
 */
@Serializable
data class HitLocationThreshold(
    val outcome: String,
    val minDamage: Int? = null,
    val hpDivisor: Int? = null,
    // The JSON key is the book's word; the property carries the repo's
    // boolean prefix, so the two are joined rather than renamed apart.
    @SerialName("approximate") val isApproximate: Boolean = false,
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
    /** One of the three rows that print the target's speed; a target has one. */
    val targetSpeed: Boolean = false,
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
 * Wrestling's three rows come from a DIFFERENT table on the same page --
 * Maneuver / Roll / Opposed Roll / Notes, which prints no damage column at
 * all. Their [damage] is the summary table's own cell for the skill, "None
 * (grapple only)", read from the book rather than typed; [opposedRoll] is the
 * third column, which the other two tables do not have. The older reason for
 * leaving that table out -- that Wrestling deals no damage -- is true and is
 * about the damage column: Shove has been a row here with no damage parts
 * since M0.
 *
 * The book's fourth grappling row, Escape, is NOT here. It rolls off Escape
 * and its subject is the character being HELD, where every other row here is
 * the one acting; this table is keyed by the acting character's skill and read
 * by an attack picker. A gap with no screen to live on, not a refusal.
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
    /** The grappling table's third column; absent on the two damage tables. */
    val opposedRoll: String? = null,
) {
    /** False for Shove and the grappling rows, which roll no damage at all. */
    val wounds: Boolean get() = base != null
}

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

/** Raw thrust/swing row exactly as read from the ThrustTable spreadsheet tab. */
@Serializable
data class DamageRowJson(val st: Int, val thrust: String, val swing: String)

/** [DamageRowJson] with thrust/swing parsed into [com.pathways.model.DiceExpr]. */
data class DamageRow(val st: Int, val thrust: com.pathways.model.DiceExpr, val swing: com.pathways.model.DiceExpr)
