package com.pathways.rules.content

import com.pathways.model.ControllingAttribute
import com.pathways.model.Difficulty
import kotlinx.serialization.Serializable

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
