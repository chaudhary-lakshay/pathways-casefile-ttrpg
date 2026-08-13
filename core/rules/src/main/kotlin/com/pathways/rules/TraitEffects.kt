package com.pathways.rules

import com.pathways.model.Attr
import com.pathways.model.ControllingAttribute
import com.pathways.model.Layer
import com.pathways.model.ModEffect
import com.pathways.model.ModTarget
import com.pathways.model.Modifier
import com.pathways.model.ModifierId
import com.pathways.model.ModifierSource
import com.pathways.model.SkillKey
import com.pathways.model.Trait
import com.pathways.rules.content.ContentLoader

/** A trait whose printed effect is real and reaches nothing here, and why. */
data class TraitNote(val trait: String, val printed: String, val reason: String)

/**
 * Ch. 3's advantages, where their printed effects reach a roll.
 *
 * The 198-row trait catalogue has had no production reader since M0. The
 * repo-wide field sweep (6ai) recorded that as legitimate on the grounds that
 * "traits are not editable yet" -- which is true of BUYING one and irrelevant
 * to the 23 premades, every one of which prints traits the book gives numbers
 * to. **`Alertness 1` is on eight of them and says "+1 per level to all Per
 * rolls"**, and every Per roll in this app has been made at flat skill.
 * Exactly the defect class 6h fixed for the trackers, one source over.
 *
 * **Only three traits are applied, and the scope is what decides.** A printed
 * number is not enough: it also has to name a roll this app can identify.
 * [ControllingAttribute] distinguishes a Per skill from an HT skill, and
 * [ParryBlock] computes the active defences, so those joins are real.
 * Everything else is [reported] -- shown to the GM with the reason -- which
 * is the call the Sealed Artifact grade table (6af) and a technique's
 * availability (6ai) already needed. Inventing a "shock penalty" so that High
 * Pain Threshold could cancel one would be authoring one rule to justify
 * reading another.
 *
 * **The numbers live here, not in the JSON.** Each is a value inside a prose
 * `notes` cell, so committing it as a column would make the book look like it
 * printed one -- the split the maneuver table's +4/-4 (6i), the +3 support cap
 * (6y) and the orthodox -2 (6z) all take. `TraitEffectsTest` pins every
 * sentence against `ContentLoader.advantages()`, which is the copy production
 * reads.
 *
 * **Matching a trait by name has precedent**: [Corruption.FORTITUDE] has read
 * `Spiritual Fortitude` off a character since M4's first slice.
 */
object TraitEffects {

    const val ALERTNESS = "Alertness"
    const val FIT = "Fit"
    const val COMBAT_REFLEXES = "Combat Reflexes"
    const val PERFECT_BALANCE = "Perfect Balance"
    const val GREAT_BALANCE = "Great Balance"
    const val IRON_JAW = "Iron Jaw"
    const val RAPID_RECOVERY = "Rapid Recovery"

    /** Ch. 3's High Pain Threshold, whose first clause [Shock] applies. */
    const val HIGH_PAIN_THRESHOLD = Shock.IGNORED_BY

    /** "+1 per level to all Per rolls." */
    const val ALERTNESS_PER_LEVEL = 1

    /** "+1 to all HT rolls; recover FP at twice the normal rate." */
    const val FIT_BONUS = 1

    /** "+1 to all active defenses, +6 to recover from stun, never freeze." */
    const val COMBAT_REFLEXES_BONUS = 1

    /** "+3 on HT rolls to avoid knockdown." */
    const val KNOCKDOWN_BONUS = 3

    /**
     * The three skills both Balance rows name: "+2 to Acrobatics, Climbing,
     * Piloting".
     *
     * **This book prints no Piloting skill.** The shape "Wrestling Master"
     * already has (6bf) and `Dead Language Fluency` naming "Ancient Loen"
     * before it (6x). Recorded, never repaired -- and because the join is by
     * NAME, the day a Piloting row appears the bonus reaches it with no second
     * decision to make.
     */
    val BALANCE_SKILLS = setOf("Acrobatics", "Climbing", "Piloting")

    /** "+2 to ..." / "+1 to ...", the second clause of each Balance row. */
    private val BALANCE_SKILL_BONUS = mapOf(PERFECT_BALANCE to 2, GREAT_BALANCE to 1)

    /**
     * What [traits] add to an HT roll that can knock the character down.
     *
     * **The Major Wound's HT roll is one**, which is what a review caught
     * (Codex): its fail-by-5 row is "Knocked down and unconscious for 1d
     * hours", so the roll decides knockdown among its three outcomes. This
     * clause was about to ship disclaimed as "this app rolls no knockdown
     * roll to modify" -- a fresh excuse of exactly the kind this slice exists
     * to remove, in the same commit that disproved the old one.
     *
     * **This is High Pain Threshold's alone, and the scope is a decision.**
     * Five rows print a knockdown clause and only this one names an attribute
     * ("+3 on **HT rolls**"), which is what joins it to the Major Wound roll.
     * The Balance rows say "+6 / +2 to avoid knockdown" with no attribute, and
     * this book prints two OTHER knockdown mechanics that are DX-based --
     * Shove's "target rolls DX or fall", Takedown's opposed DX -- plus three
     * hit-location rolls whose attribute it never states at all. Which of them
     * an unscoped clause covers is not settled by anything printed, so it is
     * refused rather than defaulted, and [reported] says so on the sheet.
     *
     * **The +3 buys more than the clause names, and that is the reading.**
     * This one roll decides three outcomes -- shock, stun, and knockdown on its
     * fail-by-5 row -- so a bonus "to avoid knockdown" also makes the stun row
     * less likely. The alternative is to move only the fail-by-5 boundary,
     * which is arithmetic the book prints nowhere; the literal reading of "+3
     * on HT rolls to avoid knockdown", applied to an HT roll that avoids
     * knockdown, is the smaller claim. Recorded rather than smoothed (4.1).
     *
     * The book's other knockdown rolls are in the hit-location effects
     * ("Knockdown roll at -5", Neck and Face; "-10", Skull) and print no
     * attribute to roll against, so this app shows those cells rather than
     * rolling them. [RollKind.MAJOR_WOUND_HT] is this bonus's only roll.
     */
    fun knockdownBonus(traits: List<Trait>): Int =
        if (levelOf(traits, HIGH_PAIN_THRESHOLD) != null) KNOCKDOWN_BONUS else 0

    /**
     * The sentence a screen prints beside [knockdownBonus], or null when
     * nothing was added.
     *
     * Authored here because `DamageDialog` spelt "includes +3 from High Pain
     * Threshold" itself, which is a second copy of WHICH trait pays -- the
     * failure this codebase produced three times (6ag, 6ai, 6be). A caller
     * cannot now name the wrong trait.
     */
    fun knockdownLine(traits: List<Trait>): String? {
        val bonus = knockdownBonus(traits)
        if (bonus == 0) return null
        return "includes +$bonus from $HIGH_PAIN_THRESHOLD — " +
            "this roll's fail-by-5 row is a knockdown."
    }

    /**
     * The level a character owns [name] at, or null when they do not own it.
     *
     * A sheet prints a levelled trait with the level IN the name --
     * "Alertness 1", "Hard to Kill 2" -- while the catalogue prints the bare
     * name and a `perLevel` flag. An unlevelled trait matches exactly and
     * counts as ONE, never zero: zero would silently cancel its own bonus.
     */
    fun levelOf(traits: List<Trait>, name: String): Int? {
        val owned = traits.firstOrNull {
            it.name.equals(name, ignoreCase = true) ||
                it.name.startsWith("$name ", ignoreCase = true)
        } ?: return null
        // A trailing integer is a level; anything else is part of the name
        // ("Poverty: Struggling"), so it does not become one.
        return owned.name.substringAfterLast(' ').toIntOrNull() ?: 1
    }

    /**
     * What [traits] add to a roll of [skill].
     *
     * Scoped by the skill's controlling attribute, the same test [Conditions]
     * uses to keep Spiritual Attrition on SPI rolls (6h). A trait whose
     * printed scope this app cannot identify contributes nothing here and is
     * named by [reported] instead.
     */
    fun modifiers(traits: List<Trait>, skill: SkillKey): List<Modifier> = buildList {
        if (skill.attr == ControllingAttribute.Per) {
            levelOf(traits, ALERTNESS)?.let {
                add(bonus(ALERTNESS, it * ALERTNESS_PER_LEVEL))
            }
        }
        if (skill.attr == ControllingAttribute.Buyable(Attr.HT)) {
            levelOf(traits, FIT)?.let { add(bonus(FIT, FIT_BONUS)) }
        }
        // The Balance rows name their three skills outright, which is a
        // stronger join than an attribute: Alertness has to be scoped by
        // controlling attribute because "all Per rolls" names no skill, and
        // this cell does. Nothing in the book makes the two rows exclusive, so
        // a character printing both would take both -- the literal reading,
        // and no premade prints either beside the other.
        if (skill.name in BALANCE_SKILLS) {
            for ((trait, value) in BALANCE_SKILL_BONUS) {
                levelOf(traits, trait)?.let { add(bonus(trait, value)) }
            }
        }
    }

    /**
     * What [traits] add to every active defence (Dodge, Parry and Block).
     *
     * "+1 to all active defenses" -- ALL of them, so this is not a Block
     * bonus even though Block is the only defence whose formula names a
     * modifier. The reading a shield's DB already needed (6o), and the same
     * trap: applying it to Block alone would understate a shielded Dodge.
     */
    fun defenseBonus(traits: List<Trait>): Int =
        if (levelOf(traits, COMBAT_REFLEXES) != null) COMBAT_REFLEXES_BONUS else 0

    /**
     * The traits a character owns whose printed effect is real and lands on
     * nothing this app rolls, each with the reason.
     *
     * Shown rather than silently dropped. A GM who can see "High Pain
     * Threshold -- this app models no shock penalty to ignore" can apply it
     * themselves; one who sees nothing assumes it was counted.
     */
    fun reported(traits: List<Trait>): List<TraitNote> {
        val catalogue = ContentLoader.advantages().associateBy { it.name.lowercase() }
        return traits.flatMap { owned ->
            val bare = bareName(owned.name)
            val printed = catalogue[bare.lowercase()]?.notes ?: return@flatMap emptyList()
            val whole = UNAPPLIED[bare]?.let { listOf(TraitNote(owned.name, printed, it)) }.orEmpty()
            // A trait can be applied and still have rules left over, so the
            // clauses are reported even though the trait itself is not.
            val clauses = PARTLY_APPLIED[bare].orEmpty().map { (clause, reason) ->
                TraitNote(owned.name, clause, reason)
            }
            whole + clauses
        }
    }

    /** Every trait this object knows about, applied or not. */
    fun known(): Set<String> =
        UNAPPLIED.keys + PARTLY_APPLIED.keys + setOf(ALERTNESS, FIT, COMBAT_REFLEXES)

    /** The traits reported whole, for the disjointness test. */
    fun unappliedNames(): Set<String> = UNAPPLIED.keys

    /** The traits reported clause by clause, for the disjointness test. */
    fun partlyAppliedNames(): Set<String> = PARTLY_APPLIED.keys

    /**
     * Clauses of an APPLIED trait that reach nothing here, and why.
     *
     * **A notes cell can hold several rules.** Combat Reflexes prints three
     * and exactly one of them names a roll this app makes; Fit prints two and
     * one. [reported] keyed on the trait NAME, and both traits are applied,
     * so it answered with nothing for either -- the remaining clauses were
     * not merely unenforced but invisible, on the one screen whose whole job
     * is to say what was not counted.
     *
     * **This is not the field sweep (6ai) restated.** That asked whether a
     * field has a consumer, and `notes` has one. This is clause-level drift
     * INSIDE a field with a production reader, which is a gap no sweep in
     * this repo could see -- 6al's own triage was correct when it ran, and
     * went stale the moment one clause of a multi-rule cell was implemented.
     * The general lesson is 6ba's: a verdict expires.
     *
     * Both reasons here are the familiar refusals, not new ones. The FP rate
     * needs a clock, which this app deliberately does not hold (6ad, 6ah,
     * 6an); the stun clause names a RECOVERY roll, which is not the penalty
     * while stunned -- that one is applied, by [ActiveDefenses.everyDefence].
     *
     * **`never freeze in surprise` was here and is not any more**, which is
     * the point of the sentence this KDoc used to end on: it said the clause
     * was "one declaration away", the surprise refusal shipped one slice
     * later, and the disclaimer stayed. A GM was told the trait did nothing
     * on the same screen whose dialog was using it. A report of what is NOT
     * applied expires exactly when the code changes (6ba), so the seam is
     * pinned by a test in `ActiveDefensesTest` rather than by this comment.
     */
    private val PARTLY_APPLIED: Map<String, List<Pair<String, String>>> = mapOf(
        FIT to listOf(
            "recover FP at twice the normal rate" to
                "this app holds no clock to recover against",
        ),
        COMBAT_REFLEXES to listOf(
            "+6 to recover from stun" to
                "this app models no stun recovery roll to make",
        ),
        // Both Balance rows are the Fit shape: one clause reaches a roll and
        // one does not. The clause sweep (6bh) DID flag these two cells --
        // they carry two mechanical clauses each, which is exactly its filter
        // -- and the triage filed them under "trait reports", a bucket that
        // never contained them. 6bj's lesson, a second time: the sweep found
        // it and the pass over its shortlist lost it.
        PERFECT_BALANCE to listOf(
            "+6 to avoid knockdown" to KNOCKDOWN_UNREACHED,
        ),
        GREAT_BALANCE to listOf(
            "+2 to avoid knockdown" to KNOCKDOWN_UNREACHED,
        ),
    )

    /**
     * Why an unscoped knockdown clause reaches nothing, said once.
     *
     * The three hit-location rolls print a modifier and no attribute, and this
     * repo refuses rather than defaulting one (a design review's blocking
     * finding). See [knockdownBonus] for why these rows do not join the one
     * knockdown roll the app does make.
     */
    private const val KNOCKDOWN_UNREACHED =
        "the book's knockdown rolls for a hit location print no attribute to " +
            "roll against, so this app shows those cells rather than rolling them"

    /**
     * Printed, real, and reaching no roll here -- with the reason, so the
     * omission stays a decision rather than becoming an accident. The
     * asymmetry 6ai turned on: a field left unapplied on purpose says so.
     */
    private val UNAPPLIED = mapOf(
        "Hard to Kill" to "this app models no death roll to avoid",
        "Danger Sense" to "the book hands this to the GM, not to a roll",
        "Eidetic Memory" to "this app models no recall roll",
        "Single-Minded" to "which tasks count as extended concentration is the GM's call",
        "Lightning Calculator" to "this app models no numerical-puzzle roll",
        "Versatile" to "every skill on a sheet is bought, so no default is rolled here",
        // Scoped to "the head", which only a hit-location roll could supply --
        // and those print no attribute (see KNOCKDOWN_UNREACHED). Its second
        // clause is a bite nothing here rolls. Glass Jaw is the disadvantage
        // with the same scope; the sheet prints drawbacks whole, so it needs
        // no entry to be visible.
        IRON_JAW to "this app rolls no knockdown for a hit location, and no bite",
        RAPID_RECOVERY to "both halves are durations, and this app holds no clock",
    )

    /** "Alertness 1" -> "Alertness"; an unlevelled name is returned as-is. */
    private fun bareName(name: String): String =
        if (name.substringAfterLast(' ').toIntOrNull() != null) {
            name.substringBeforeLast(' ')
        } else {
            name
        }

    private fun bonus(trait: String, value: Int) = Modifier(
        id = ModifierId("trait:${trait.lowercase()}"),
        // FromTrait, NOT Situational. Both existed from M0 and this is the
        // first production caller of either; `Situational` is what a GM says
        // about this turn, and an advantage is what the character permanently
        // IS. They resolve to different `SourceKind`s, which is what a
        // `Cancel` budget is measured against -- so mislabelling one as the
        // other would only surface the day some situational rule tried to
        // cancel a circumstance and silently ate a trait instead.
        source = ModifierSource.FromTrait(trait, advantage = true),
        // Retargeted onto the skill being rolled by SkillRoll, exactly as
        // Conditions' and CommonModifiers' rows are.
        target = ModTarget.AllRolls,
        effect = ModEffect.Flat(value),
        layer = Layer.ADVANTAGE,
    )
}
