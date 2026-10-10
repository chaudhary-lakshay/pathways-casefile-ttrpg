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
import com.pathways.model.TraitKind
import com.pathways.rules.content.ActingQuality
import com.pathways.rules.content.ContentLoader
import com.pathways.rules.content.TraitDefinition
import com.pathways.rules.TraitNames.catalogue
import com.pathways.rules.TraitNames.catalogueName
import com.pathways.rules.TraitNames.displayName
import com.pathways.rules.TraitNames.levelOf
import com.pathways.rules.TraitRefusals.PARTLY_APPLIED
import com.pathways.rules.TraitRefusals.UNAPPLIED

/** A trait whose printed effect is real and reaches nothing here, and why. */
data class TraitNote(val trait: String, val printed: String, val reason: String)

/**
 * What a character's traits do to an HT roll the Major Wound table asks for:
 * the number, and the sentence naming every trait that produced it.
 *
 * That table asks for two, and they are different rolls with different
 * reachers — the table's own roll ([TraitEffects.majorWoundHt]) and the one
 * its Failure row prints to recover from the stun ([StunRecovery.bonusFor]).
 * The shape is shared because both are "a bonus and the credit for it"; the
 * SCOPES are not, and each function decides its own.
 *
 * [line] is null exactly when [bonus] is zero, so a screen cannot print a
 * credit for a roll nothing moved.
 */
data class MajorWoundHt(val bonus: Int, val line: String?)

/**
 * One clause of one trait that moves a roll, and the circumstance it needs.
 *
 * ONE type for the Fright Check rows (Ch. 5) and the social-skill rows
 * (Ch. 3), because they are the same thing said twice: a printed clause, a
 * value, and a circumstance the GM declares. Only the destination differs --
 * a Fright Check takes a single Int, a skill roll takes a [Modifier] through
 * the ledger. Two data classes carrying identical fields would be the
 * duplicate this file already has to guard against.
 *
 * [trait] is the catalogue row and [shown] the sheet's own spelling, which
 * differ for an instance of a placeholder row -- "Phobia (specify)" against
 * "Phobia (Cities)". A screen prints [shown], because which fear a character
 * has is content the GM needs; the row is what the number was read from.
 *
 * [conditional] is the whole of the interface: the book attaches a
 * circumstance to four of the five rows, this app holds one sheet and no
 * scene, so those are the GM's to declare and only the fifth may be counted
 * unasked.
 */
data class TraitClause(
    val trait: String,
    val shown: String,
    val printed: String,
    val value: Int,
    val conditional: Boolean,
) {
    /**
     * What a declaration names.
     *
     * Trait AND clause, because one cell prints two circumstances ("Fright
     * Check at -2 on direct exposure; -1 in consecrated buildings") and a key
     * that could not tell them apart would let one declaration move both --
     * the collision `BoonModifier.key` was widened for (6r).
     */
    val key: String get() = "$shown / $printed"
}

/**
 * What a character's traits do to a Fright Check: the number, and the sentence
 * naming every clause that produced it.
 *
 * One call answers both, because a screen that computes the number from one
 * expression and prints the breakdown from another is how these stopped
 * reconciling five times over (6ag, 6ai, 6be, 6bp, 6bs). [line] is null
 * exactly when nothing applied.
 */
data class ClauseBonus(val total: Int, val line: String?)

/**
 * Ch. 3's advantages, where their printed effects reach a roll.
 *
 * **How this object began, kept as history** (put in the past tense in 6fu,
 * when each sentence had become false of the app). The 198-row trait
 * catalogue had no production reader from M0 until this object. The
 * repo-wide field sweep (6ai) recorded that as legitimate on the grounds that
 * "traits are not editable yet" -- which was true then of BUYING one
 * ([PurchasableTraits] sells them now) and irrelevant to the 23 premades,
 * every one of which prints traits the book gives numbers to. **`Alertness
 * 1` is on eight of them and says "+1 per level to all Per rolls"**, and
 * every Per roll in this app was being made at flat skill.
 * Exactly the defect class 6h fixed for the trackers, one source over.
 *
 * **Three traits were applied at first ([known] is what is read now), and
 * the scope is what decides.** A printed
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

    /**
     * "Very Fit", which says "+2 to all HT rolls" and was outside
     * [HT_ROLL_TRAITS] until 6de.
     *
     * No premade carries it, which is why nothing caught it: every guard on
     * that map reads the map. The catalogue is the corpus that can disagree.
     */
    const val VERY_FIT = "Very Fit"
    const val COMBAT_REFLEXES = "Combat Reflexes"
    const val PERFECT_BALANCE = "Perfect Balance"
    const val GREAT_BALANCE = "Great Balance"
    const val IRON_JAW = "Iron Jaw"
    const val RAPID_RECOVERY = "Rapid Recovery"
    const val NIGHT_EYES = "Night Eyes"
    const val CHARISMA = "Charisma"
    const val OUTDOORSMAN = "Outdoorsman"
    const val EMPATHY = "Empathy"
    const val CLUELESS = "Clueless"

    // The two blessings whose cell prints a number the engine could reach.
    // The other five are [UNAPPLIED], each with its own reason.
    const val GOD_OF_KNOWLEDGE = "God of Knowledge and Wisdom"
    const val GOD_OF_COMBAT = "God of Combat"

    /**
     * The banner "+5 to all occult/knowledge skills" names.
     *
     * **"occult" names no printed group and "knowledge skills" does**, which
     * is the whole reading and is checked rather than argued: the phrase
     * "occult skill" appears NOWHERE in this book, while "knowledge skill"
     * appears twice and one of those IS chapter 3's banner. So the slash
     * cannot be joining two groups -- one half of it is not a group.
     * `extract_blessings.verify()` fails on either half changing, because
     * that grep is the warrant (the per-table standard 6ad set, 6v is why it
     * can never be per-source).
     *
     * The two rejected readings are rejected for opposite reasons, and both
     * would author content. WIDER would add Astrology, the one occult-flavoured
     * skill outside this banner -- but the book files it under SURVIVAL &
     * OTHER, so calling it occult is my judgement and not the book's.
     * NARROWER would mean curating which of the banner's twenty-six rows are
     * occult, which is list-building of the kind 6i refused for attacks. (6i's
     * stated premise, that no skill is tagged as a combat skill, was false:
     * the book prints a COMBAT SKILLS banner. Its refusal to curate WITHIN a
     * banner is the part that carries over.) Every occult skill this book prints -- Occultism, Hidden
     * Lore, Thaumatology, Theology, Alchemy -- is already inside it.
     *
     * "Expert in mysticism." is the cell's lead-in and grants nothing, so it
     * is named in the clause guard's prose exemption rather than reported.
     */
    const val KNOWLEDGE_SKILLS = "KNOWLEDGE SKILLS"

    /** What the God of Knowledge's blessing adds to a KNOWLEDGE skill roll. */
    private const val KNOWLEDGE_BLESSING_BONUS = 5

    /**
     * The banner Ch. 3 prints sixteen skills under, and the group every
     * "social skill rolls" cell names.
     *
     * The membership is NOT restated here. `skills.json` carries each skill's
     * banner, so the set is read from the content -- a second copy of a set
     * the book prints is what 4.1 forbids, and 6bg's forfeit list is the
     * precedent for reading one out of its own file. That is the difference
     * from [BALANCE_SKILLS] and Charisma's six, whose CELLS name their skills
     * outright and so have no printed table to read.
     */
    const val SOCIAL_SKILLS = "SOCIAL SKILLS"

    /**
     * The ceiling both levelled skill rows print for themselves -- Charisma's
     * "[max 4 levels]" and Outdoorsman's "Max 4 levels."
     *
     * A property of those two cells, not of levelled traits generally:
     * Alertness is "+1 per level" and prints no ceiling at all, so a general
     * cap would invent one for it.
     */
    const val CAPPED_LEVELS = 4

    /** "Reduce all darkness penalties by 2". */
    const val NIGHT_EYES_REDUCTION = 2

    /** Ch. 3's High Pain Threshold, whose first clause [Shock] applies. */
    const val HIGH_PAIN_THRESHOLD = Shock.IGNORED_BY

    /** "+1 per level to all Per rolls." */
    const val ALERTNESS_PER_LEVEL = 1

    /**
     * Alertness's "+1 per level to all Per rolls", for the two bare Per rolls
     * no [SkillKey] reaches: Detection's and Spirit Vision's (6fe). One answer,
     * where each call site used to build its own.
     */
    fun alertnessBonus(traits: List<Trait>): Int =
        (levelOf(traits, ALERTNESS) ?: 0) * ALERTNESS_PER_LEVEL

    /** "+1 to all HT rolls; recover FP at twice the normal rate." */
    const val FIT_BONUS = 1

    /** Ch. 3's disadvantage that prints Fit's sentence with the sign flipped. */
    const val UNFIT = "Unfit"

    /**
     * "-1 to all HT rolls; recover FP at half normal rate."
     *
     * Stored as a MAGNITUDE, like every other number here, and negated where
     * it is applied. [Trait] carries costs the same way, with [TraitKind]
     * giving the direction -- the convention `Wealth.traitFor` is the one
     * place that meets the other (6u).
     */
    const val UNFIT_PENALTY = 1

    /** "+2 to all HT rolls; lose FP at half the normal rate...". */
    const val VERY_FIT_BONUS = 2

    /** "+1/level to HT rolls to avoid death; can take 1-5 levels". */
    const val HARD_TO_KILL = "Hard to Kill"

    /** "+1/level to remain conscious when reduced to 0 or negative HP". */
    const val HARD_TO_SUBDUE = "Hard to Subdue"

    /** "+1 to all active defenses, +6 to recover from stun, never freeze." */
    const val COMBAT_REFLEXES_BONUS = 1

    /** "+3 on HT rolls to avoid knockdown." */
    const val KNOCKDOWN_BONUS = 3

    const val VERSATILE = "Versatile"

    /** "+1 to defaults skill rolls" (p. 13). */
    const val DEFAULT_BONUS = 1

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

    /**
     * Every trait whose cell says "to all HT rolls", and by how much.
     *
     * One map, read by both paths -- [modifiers] for an HT-controlled skill
     * and [htRollBonus] for the bare Major Wound roll -- because a trait that
     * reached one and not the other is exactly the gap this map was made to
     * close.
     */
    private val HT_ROLL_TRAITS: Map<String, Int> = mapOf(
        FIT to FIT_BONUS,
        VERY_FIT to VERY_FIT_BONUS,
        UNFIT to -UNFIT_PENALTY,
    )

    /**
     * The traits whose cell says "to all HT rolls", for the guard tests.
     *
     * **This cannot check the map's own claim**, which is what let `Very Fit`
     * sit outside it: it returns the keys, so a row nobody added is invisible
     * to any test built on it. `VitalRollsTest` reads the CATALOGUE for the
     * phrase and compares -- the census-as-a-test 6bu argued for.
     */
    fun htRollNames(): Set<String> = HT_ROLL_TRAITS.keys

    /** "Prior spirit body damage ... All SPI-based rolls at -1." */
    const val ETHER_BODY_DAMAGE = "Ether Body Damage"

    /**
     * Every trait whose cell says "SPI-based rolls", and by how much.
     *
     * The third controlling-attribute join, beside Alertness on Per and
     * Fit/Unfit on HT -- and the attribute this app already scopes a condition
     * to, since [Conditions] keeps Spiritual Attrition on SPI rolls by the same
     * test (6h). Signed, exactly as [HT_ROLL_TRAITS] is: the direction belongs
     * to the value, not to the map's name, so a row granting a BONUS to SPI
     * rolls would join it without a second accessor.
     *
     * One map, two paths -- [modifiers] for an SPI-controlled skill and
     * [spiRollBonus] for the bare attribute roll -- because a trait reaching
     * one and not the other is the gap Alertness fell into (6al) and Fit fell
     * into after it (6bs).
     *
     * **And the book itself draws that distinction, on this attribute.** Line
     * 2351 prints Spiritual Attrition as "-3 to all SPI-based SKILL rolls"
     * where this cell says "All SPI-based rolls" -- the same one-word
     * difference the book makes between "social skill rolls" and "social
     * rolls" and settles in one bracket of its own (6bt). So the wider phrase
     * really is wider, and the bare rolls are not an assumption: a condition
     * scoped to skills stops at [modifiers], and this does not.
     */
    private val SPI_ROLL_TRAITS: Map<String, Int> = mapOf(
        ETHER_BODY_DAMAGE to -1,
    )

    /** The traits whose cell says "SPI-based rolls", for the guard tests. */
    fun spiRollNames(): Set<String> = SPI_ROLL_TRAITS.keys

    /**
     * What [traits] add to a bare SPI roll, signed.
     *
     * The one this app makes is a detection roll, which takes the higher of
     * SPI or Per and so is SPI-based only some of the time -- see
     * [Detection.attributeFor], which decides WHICH attribute is rolled before
     * applying this. A divination is the other SPI path and reaches its target
     * through [Divination.targetFor] rather than a [SkillKey], so it asks here
     * too.
     *
     * Deliberately NOT a general attribute-roll accessor, the same call
     * [htRollBonus] makes: the app's other bare rolls are Will, IQ and Per.
     * **"Nothing names those" was loose and 6de's sweep corrected it** -- four
     * catalogue rows name Will rolls, all CONDITIONAL and none carried by a
     * fixture. See [htRollBonus], which carries the census.
     */
    fun spiRollBonus(traits: List<Trait>): Int =
        SPI_ROLL_TRAITS.entries.sumOf { (trait, value) ->
            if (levelOf(traits, trait) != null) value else 0
        }

    /**
     * Every trait whose cell names the skills it lifts, and by how much.
     *
     * **A skill NAME is the strongest join there is.** Alertness has to be
     * scoped by controlling attribute because "all Per rolls" names no skill
     * (6al); these cells name theirs outright, and [modifiers] already takes
     * the [SkillKey]. Two traits naming one skill both apply -- nothing in the
     * book makes independently purchased advantages exclusive, and Pickpocket
     * owns Perfect Balance and Flexibility beside a bought Climbing.
     *
     * **Healer's +3 is its whole First Aid bonus.** Its cell also prints "+2
     * to all rolls to diagnose, treat, and heal", and a First Aid roll is a
     * roll to treat -- so stacking them would charge +5 on the one roll the
     * cell singles out with the word "specifically". The general clause
     * reaches nothing and is [reported]: it names a PURPOSE, and this book
     * tags no skill as a healing skill, the refusal [Languages] makes for
     * "language-dependent skill rolls" (6x).
     *
     * Two of the names are printed by no skill list this book has -- Piloting
     * (see [BALANCE_SKILLS]) and Absolute Direction's "Body Sense", which
     * occurs exactly once in the whole book, inside that trait's own cell.
     * Recorded, never repaired, and the name join reaches them the day either
     * appears (6bf's "Wrestling Master", 6x's "Ancient Loen").
     */
    private val SKILL_BONUSES: Map<String, SkillBonus> = mapOf(
        PERFECT_BALANCE to flat(2, BALANCE_SKILLS),
        GREAT_BALANCE to flat(1, BALANCE_SKILLS),
        "Flexibility" to flat(3, setOf("Climbing", "Escape")),
        "Double-Jointed" to flat(5, setOf("Climbing", "Escape")),
        "Healer" to flat(3, setOf("First Aid")),
        "Medical Training (Informal)" to flat(1, setOf("First Aid")),
        "Sanctity" to flat(2, setOf("Theology", "Religious Ritual")),
        "Absolute Direction" to flat(3, setOf("Navigation", "Body Sense")),
        // Three cells that name printed skills and no circumstance, found by
        // walking the catalogue for rows OUTSIDE [known] (6es). "in formal
        // settings" belongs to the officer's courtesies, not to his +1.
        "Acute Social Awareness" to flat(2, setOf("Body Language", "Detect Lies")),
        "Former Military Officer" to flat(1, setOf("Leadership", "Tactics")),
        "Photographic Instinct" to flat(2, setOf("Photography")),
        // "Disguise attempts at -2" prints no qualifier; "Recognizable in the
        // city" explains it and is not referred back to, as Fearsome
        // Reputation's "these circles" is (6ff's design review).
        "Famous Face" to flat(-2, setOf("Disguise")),
        CHARISMA to perLevel(
            1,
            setOf(
                "Leadership", "Panhandling", "Public Speaking",
                "Savoir-Faire", "Sex Appeal", "Streetwise",
            ),
        ),
        OUTDOORSMAN to perLevel(
            1,
            setOf(
                "Camouflage", "Fishing", "Naturalist", "Navigation",
                "Survival", "Tracking", "Weather Sense",
            ),
        ),
    )

    /**
     * Every trait whose cell names a skill CATEGORY, and by how much.
     *
     * **A third join, weaker than a skill name and much stronger than a
     * controlling attribute.** Empathy's "+3 to social skill rolls" names no
     * skill, so [SKILL_BONUSES] cannot reach it; and scoping by attribute
     * would be wrong rather than merely loose, because Fast-Talk, Research
     * and Alchemy are all IQ-based and only the first is social.
     *
     * **Only unconditional rows are here.** Five cells say "social skill
     * rolls" and three of them qualify it -- Callous "when warmth or empathy
     * is required", Shyness "depending on severity and audience", Social
     * Anxiety "in groups of 6+" -- which are circumstances a GM declares and
     * this app has no control for. Applying them at their printed value would
     * charge a penalty nobody declared, on three premades that own a social
     * skill; they are [UNAPPLIED] with the reason instead.
     *
     * **The bare phrase "social rolls" is a different thing and reaches
     * nothing.** Line 6522 prints "(-1 to all emotional reaction rolls and
     * social skill rolls)", so the book distinguishes the two in one bracket;
     * the bare phrase is never defined, is never a banner, and the rows using
     * it read like reactions ("first-impression", "people look out for you").
     * This book prints no reaction roll procedure at all (6w), so which rolls
     * it touches is settled by nothing printed -- the refusal [Languages]
     * makes about a common tongue's silence (6x). Workaholic and Loner are
     * the carried cases.
     */
    private val CATEGORY_BONUSES: Map<String, Pair<Int, String>> = mapOf(
        EMPATHY to (3 to SOCIAL_SKILLS),
        // "-3 to ALL social skill rolls" -- the book's own "all" is what puts
        // this row on the unconditional side of the same page's other three.
        // Two premades carry it, both with the name and the cell glued into one
        // string, which is why it resolved to no catalogue row until now.
        CLUELESS to (-3 to SOCIAL_SKILLS),
        // The one Orthodox blessing whose cell names a group the book prints.
        // See [KNOWLEDGE_SKILLS] for why the slash resolves to one banner and
        // not to two groups, and why both the wider and the narrower readings
        // would author content rather than read it.
        GOD_OF_KNOWLEDGE to (KNOWLEDGE_BLESSING_BONUS to KNOWLEDGE_SKILLS),
    )

    /** The traits whose cell names a category, for the guard tests. */
    fun categoryBonusNames(): Set<String> = CATEGORY_BONUSES.keys

    /** The banner [trait]'s cell names, null for a trait that names none. */
    fun categoryFor(trait: String): String? = CATEGORY_BONUSES[trait]?.second

    /**
     * The banner [skill] is printed under, read from the content.
     *
     * A skill the book does not print has no banner and takes no
     * category-scoped rule -- which is the honest answer for a bought skill
     * whose name a content fix removed, and for the three grants that name no
     * real skill (6be, M3's recorded placeholders).
     */
    private fun categoryOf(skill: SkillKey): String? =
        ContentLoader.skills().firstOrNull { it.name == skill.name }?.category

    /**
     * One row of [SKILL_BONUSES]: what a cell adds, and to which skills.
     *
     * [perLevel] is spelt out rather than inferred from a nullable ceiling,
     * because a row that scales and a row that does not are two readings of a
     * cell and the wrong default loses a bonus in silence. The two
     * constructors below are what make it impossible to get by accident.
     */
    private data class SkillBonus(
        val value: Int,
        val skills: Set<String>,
        val perLevel: Boolean,
        val maxLevels: Int?,
    )

    /** A cell that prints one number however many levels are owned. */
    private fun flat(value: Int, skills: Set<String>) =
        SkillBonus(value, skills, perLevel = false, maxLevels = null)

    /** A cell that prints "+n per level" and its own ceiling. */
    private fun perLevel(value: Int, skills: Set<String>) =
        SkillBonus(value, skills, perLevel = true, maxLevels = CAPPED_LEVELS)

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
    fun knockdownBonus(traits: List<Trait>): Int = BespokeBonus.KNOCKDOWN.of(traits)

    /**
     * The traits applied by a function of their own rather than through a map.
     *
     * **This exists because [known] missed them, twice over.** That function
     * builds the census's handled set out of the collections above, so a trait
     * applied by a single-trait function belongs to none of them and reads as
     * unreached — and `Hard to Kill` and `High Pain Threshold` were both
     * applied, both reported nothing, and both invisible to the guard whose
     * whole job is to notice that. The KDoc on [known] already records the
     * same failure twice (Outdoorsman, then the HT-roll map) and both times
     * the fix was "add the collection". These three had no collection.
     *
     * So the declaration IS the collection: each bonus function reads its own
     * entry, and [known] reads the enum. A fourth bespoke bonus cannot be
     * written without an entry here, which is what stops this recurring.
     *
     * [fixed] is null where the book prices the clause per level.
     */
    private enum class BespokeBonus(val trait: String, val fixed: Int?) {
        /** "+1/level to HT rolls to avoid death" — [VitalCheck.SURVIVAL]. */
        DEATH(HARD_TO_KILL, null),

        /**
         * "+1/level to remain conscious when reduced to 0 or negative HP" —
         * [VitalCheck.CONSCIOUSNESS].
         */
        CONSCIOUSNESS(HARD_TO_SUBDUE, null),

        /** "+3 on HT rolls to avoid knockdown" — the Major Wound's own roll. */
        KNOCKDOWN(HIGH_PAIN_THRESHOLD, KNOCKDOWN_BONUS),

        /** "any time you use a skill at default" — [Divination.divinationLevelFor]. */
        DEFAULT(VERSATILE, DEFAULT_BONUS),
        ;

        fun of(traits: List<Trait>): Int {
            val level = levelOf(traits, trait) ?: return 0
            return fixed ?: level
        }
    }

    /**
     * What [traits] add to a roll made to avoid DEATH, which is one of the two
     * rolls the HP table prints ([VitalCheck.SURVIVAL]).
     *
     * `Hard to Kill` is "+1/level to HT rolls to avoid death", so the level is
     * the number: a sheet printing "Hard to Kill 2" rolls at +2. It is
     * deliberately scoped away from the consciousness roll one row above, for
     * the reason [knockdownBonus] is scoped away from both -- a clause naming
     * its own condition is not widened by the app that reads it (6ck).
     *
     * **This used to be an UNAPPLIED entry reading "this app models no death
     * roll to avoid".** That was true when it was written and stopped being
     * true in the commit that offered the roll; a refusal expires exactly like
     * a sweep verdict (6ba, 6bl).
     */
    fun deathRollBonus(traits: List<Trait>): Int = BespokeBonus.DEATH.of(traits)

    /**
     * What [traits] add to a skill rolled AT A DEFAULT: Versatile's "+1 to
     * defaults skill rolls -- any time you use a skill at default, you are
     * effectively one level better".
     *
     * **This was an UNAPPLIED entry reading "every skill on a sheet is bought,
     * so no default is rolled here".** [Divination.divinationLevelFor] rolls
     * Divination Arts at Ritualistic Magic-2, and p. 54 calls that number a
     * default in its own word.
     *
     * **Then it was a [PARTLY_APPLIED] entry reading "this app offers no
     * other roll at a default"**, which [SkillDefaults] made false in turn
     * (6et). Every roll this app makes at a default takes the +1 now -- the
     * Divine tile's, the Skills tab's untrained roll, the Attack dialog's
     * untrained attack (6eu) and the Defend dialog's untrained Acrobatic
     * Dodge (6ev), all four through [SkillDefaults] or [Divination] -- so
     * the trait has nothing left to report. A fifth consumer of a default
     * must take it too.
     *
     * NOT a Warrior's or Weapon Master's weapon, which is "treated as known
     * at effective DX": that is not a roll at a default, and
     * [SkillDefaults.mastered] leaves the +1 off it.
     */
    fun defaultBonus(traits: List<Trait>): Int = BespokeBonus.DEFAULT.of(traits)

    /**
     * What [traits] add to a roll made to REMAIN CONSCIOUS, the other of the
     * two rolls the HP table prints ([VitalCheck.CONSCIOUSNESS]).
     *
     * `Hard to Subdue` is printed immediately below `Hard to Kill`, at the same
     * price and the same shape, and the pair partitions the table: one names
     * "HT rolls to avoid death", the other "to remain conscious when reduced to
     * 0 or negative HP". The second quotes the 0 HP row's own words — "roll
     * each turn to remain conscious".
     *
     * **It reached nothing until 6dt, and the reason is the filter.** Every
     * census that would have found it looked for the phrase "HT roll", which
     * this cell never uses — a roll the book NAMES rather than describes, which
     * is 6bu's finding on a fourth mechanic.
     *
     * **It does not reach the survival roll**, even though that roll happens
     * at negative HP and this clause names negative HP. Both checks are due
     * below `-HP`, and two rolls falling in one moment does not merge their
     * scopes: the survival row's own words are "or die". Widening a clause
     * that names its own condition is the move 6ck refused.
     *
     * No cap: `Hard to Kill`'s cell prints "can take 1-5 levels" and this one
     * prints nothing, so a ceiling here would be app-authored (4.1).
     */
    fun consciousnessRollBonus(traits: List<Trait>): Int = BespokeBonus.CONSCIOUSNESS.of(traits)

    /**
     * What [traits] take off a darkness penalty: Night Eyes' "reduce all
     * darkness penalties by 2".
     *
     * **Where it lands is [DarknessCause]'s business, not this object's.** The
     * one darkness penalty this app prices is chapter 5's "Darkness or heavy
     * fog", which charges two causes with one number while the trait covers
     * only one -- so how much comes off is here and whether any comes off at
     * all is declared at the roll. Two objects, because a trait cannot know
     * what the GM says the weather is.
     *
     * Appendix A's "Adverse conditions (dark, noisy, dangerous) -4" is
     * deliberately NOT a darkness penalty for this purpose: it prices three
     * causes as one, so taking 2 off would claim the whole -4 was the dark.
     * Nothing here can tell, and 6ae already withholds that row entirely on a
     * ranged shot, where chapter 5 prices the same circumstance specifically.
     */
    fun darknessReduction(traits: List<Trait>): Int =
        if (levelOf(traits, NIGHT_EYES) != null) NIGHT_EYES_REDUCTION else 0

    const val ACTING_METHOD = "Knows the Acting Method"

    const val FAST_HEALER = "Fast Healer"

    const val CLEANSED_SPIRIT = "Cleansed Spirit"

    const val LUCK = "Luck"
    const val BECKONING_LUCK = "Beckoning Luck"
    const val FATE_DEBT = "Fate Debt"
    const val LUCKY_ONE = "Lucky One"
    private const val WHEEL_OF_FORTUNE = "Wheel of Fortune"
    private const val LUCKY_ONE_SEQUENCE = 7

    /** What a Luck reroll reaches here, for both rows' reports. */
    internal const val LUCK_REACH =
        "offered on a failed Potion Consumption Roll, which would end the character; " +
            "any other reroll is the table's, and a ritual's or boon's critical-failure " +
            "CoR is charged before it and not undone"

    /**
     * What lets [traits], walking [state], reroll a failed roll, or null (6fg).
     *
     * Luck ("may reroll any one failed roll"); Beckoning Luck, unless Fate
     * Debt is held ("Beckoning Luck never applies", line 1449, which names
     * no other source); and the Lucky One's "5 rerolls per session" (line
     * 7766), a pathway grant no trait carries, from Sequence 7 down because
     * sequences are cumulative. Which source a GM spends, and how many are
     * left this session, is the table's.
     */
    fun rerollSource(traits: List<Trait>, state: com.pathways.model.BeyonderState?): String? = when {
        levelOf(traits, LUCK) != null -> LUCK
        levelOf(traits, BECKONING_LUCK) != null && levelOf(traits, FATE_DEBT) == null -> BECKONING_LUCK
        state?.pathway == WHEEL_OF_FORTUNE && state.sequence <= LUCKY_ONE_SEQUENCE -> LUCKY_ONE
        else -> null
    }

    /**
     * What the CoR stepper says to a holder of [CLEANSED_SPIRIT]: its row in
     * [reported], refused whole in [UNAPPLIED] (6fd). The book lists no CoR
     * source as "passive exposure", so nothing is classified, and nothing is
     * halved.
     */
    fun corruptionNotes(traits: List<Trait>): List<TraitNote> =
        reported(traits.filter { catalogueName(it.name).equals(CLEANSED_SPIRIT, ignoreCase = true) })

    /** Whether [traits] hold [FAST_HEALER]: the recovery row asks before saying so (6fc). */
    fun healsFast(traits: List<Trait>): Boolean = levelOf(traits, FAST_HEALER) != null

    /** Whether [traits] hold [ACTING_METHOD]: the question a screen asks before saying so. */
    fun doublesActingGains(traits: List<Trait>): Boolean = levelOf(traits, ACTING_METHOD) != null

    /** "all Digestion Gain Per Session is doubled". */
    private const val ACTING_GAIN_FACTOR = 2

    /**
     * The Acting Method table (Ch. 6) as [traits] digest by it: every gain
     * range doubled for a holder of [ACTING_METHOD], whose cell prints its own
     * example -- "Exemplary +15-20% becomes +30-40%" (6ez).
     *
     * The GM still picks the figure inside a range and moves the tracker; the
     * app moves nothing on its own, so the doubled table IS the application.
     * "None" loses progress, which is not a gain, and stays as printed.
     */
    fun actingGains(traits: List<Trait>): List<ActingQuality> {
        if (!doublesActingGains(traits)) return ContentLoader.actingMethod()
        return ContentLoader.actingMethod().map {
            it.copy(minGain = it.minGain?.times(ACTING_GAIN_FACTOR), maxGain = it.maxGain?.times(ACTING_GAIN_FACTOR))
        }
    }

    /** Night Eyes' cell, quoted rather than paraphrased. */
    fun printedNightEyes(): String =
        ContentLoader.advantages().first { it.name == NIGHT_EYES }.notes

    /**
     * What [traits] do to the Major Wound's HT roll, and the sentence that
     * says so.
     *
     * **One object rather than two functions, because the number and its
     * explanation must agree.** `DamageDialog` once spelt "includes +3 from
     * High Pain Threshold" itself, which is a second copy of WHICH trait pays
     * -- the failure this codebase produced three times (6ag, 6ai, 6be) --
     * and 6bp moved the sentence here for that reason. It stayed named after
     * the knockdown alone until a second trait reached the same roll, at
     * which point a caller adding one bonus and printing the other would be
     * showing a breakdown that does not sum.
     *
     * Two rules reach it and they are different rules. [knockdownBonus] is
     * High Pain Threshold's, scoped by the clause that names an attribute;
     * [htRollBonus] is "all HT rolls", which this roll is.
     */
    fun majorWoundHt(traits: List<Trait>): MajorWoundHt {
        val parts = mutableListOf<String>()
        val knockdown = knockdownBonus(traits)
        if (knockdown != 0) parts += "+$knockdown from $HIGH_PAIN_THRESHOLD"
        for ((trait, value) in HT_ROLL_TRAITS) {
            if (levelOf(traits, trait) == null) continue
            parts += "${if (value < 0) "−${-value}" else "+$value"} from $trait"
        }
        if (parts.isEmpty()) return MajorWoundHt(0, null)
        val tail = if (knockdown != 0) " — this roll's fail-by-5 row is a knockdown." else "."
        return MajorWoundHt(
            knockdown + htRollBonus(traits),
            "includes ${parts.joinToString(", ")}$tail",
        )
    }

    /**
     * The same, for the recovery roll the Failure row prints ([StunRecovery]).
     *
     * Here rather than on [StunRecovery] because [HT_ROLL_TRAITS] is private
     * and the credit is built from it — the number itself still comes from
     * [StunRecovery.bonusFor], so the two cannot disagree.
     *
     * The scopes differ by one clause each way, which is the reason this is
     * not [majorWoundHt] with a flag: `High Pain Threshold` reaches that roll
     * and not this one (its clause names knockdown), and Combat Reflexes
     * reaches this one and not that one (its clause names stun recovery).
     */
    fun stunRecoveryHt(traits: List<Trait>): MajorWoundHt {
        val parts = mutableListOf<String>()
        if (stunRecoveryBonus(traits) != 0) {
            parts += "+${StunRecovery.COMBAT_REFLEXES_BONUS} from $COMBAT_REFLEXES"
        }
        for ((trait, value) in HT_ROLL_TRAITS) {
            if (levelOf(traits, trait) == null) continue
            parts += "${if (value < 0) "−${-value}" else "+$value"} from $trait"
        }
        if (parts.isEmpty()) return MajorWoundHt(0, null)
        return MajorWoundHt(StunRecovery.bonusFor(traits), "includes ${parts.joinToString(", ")}.")
    }

    /**
     * What [traits] add to any roll of HT itself.
     *
     * **"+1 to all HT rolls" means all of them, and [modifiers] can only
     * reach the ones a [SkillKey] carries.** The Major Wound's roll was the
     * only bare HT roll this app made -- so Fit missed it for six premades
     * from 6n until 6bs -- and 6de added two more, the HP table's own
     * consciousness and survival rolls ([VitalRolls]). The same structural
     * gap 6al had to thread explicitly for Alertness, whose "all Per rolls"
     * could not reach the detection roll.
     *
     * Deliberately NOT a general attribute-roll bonus. The app's other bare
     * rolls are Will, IQ, Per and SPI (fright, self-control, concentration,
     * detection, Spirit Vision). **A sweep in 6de asked whether any trait names those**, and
     * the Will half of its answer expired in 6df: the query is `Will rolls`
     * (plural, which is what excludes `Compulsive Gambling`'s singular
     * "requires Will roll at -2"), it returns four rows, and three of them
     * carry a modifier that now reaches a roll -- through [WILL_CLAUSES],
     * NOT through here. That is the structural point this KDoc is making: a
     * clause naming a CIRCUMSTANCE is the GM's to declare, so it cannot be
     * summed unasked the way "+1 to all HT rolls" can. 6de's "no fixture
     * carries any of them" was also a claim about the wrong corpus --
     * `PurchasableTraits.offered()` makes all three buyable. IQ is still
     * named by nothing at all.
     */
    fun htRollBonus(traits: List<Trait>): Int =
        HT_ROLL_TRAITS.entries.sumOf { (trait, value) ->
            if (levelOf(traits, trait) != null) value else 0
        }

    /**
     * What [traits] add to a roll of [skill].
     *
     * Scoped by the skill's controlling attribute, the same test [Conditions]
     * uses to keep Spiritual Attrition on SPI rolls (6h). A trait whose
     * printed scope this app cannot identify contributes nothing here and is
     * named by [reported] instead.
     */
    fun modifiers(
        traits: List<Trait>,
        skill: SkillKey,
        declared: Set<String> = emptySet(),
    ): List<Modifier> = buildList {
        if (skill.attr == ControllingAttribute.Per) {
            levelOf(traits, ALERTNESS)?.let {
                add(bonus(ALERTNESS, it * ALERTNESS_PER_LEVEL))
            }
        }
        if (skill.attr == ControllingAttribute.Buyable(Attr.HT)) {
            for ((trait, value) in HT_ROLL_TRAITS) {
                levelOf(traits, trait)?.let { add(bonus(trait, value)) }
            }
        }
        if (skill.attr == ControllingAttribute.Spi) {
            for ((trait, value) in SPI_ROLL_TRAITS) {
                levelOf(traits, trait)?.let { add(bonus(trait, value)) }
            }
        }
        // The Balance rows name their three skills outright, which is a
        // stronger join than an attribute: Alertness has to be scoped by
        // controlling attribute because "all Per rolls" names no skill, and
        // this cell does. Nothing in the book makes the two rows exclusive, so
        // a character printing both would take both -- the literal reading,
        // and no premade prints either beside the other.
        for ((trait, row) in SKILL_BONUSES) {
            if (skill.name !in row.skills) continue
            val owned = levelOf(traits, trait) ?: continue
            // A flat row is flat whatever level the sheet prints beside it:
            // only a cell that says "per level" scales, and only as far as
            // its own printed ceiling.
            val levels = if (row.perLevel) minOf(owned, row.maxLevels ?: owned) else 1
            add(bonus(trait, row.value * levels))
        }
        // The category join, resolved once for the skill rather than per trait.
        val category = categoryOf(skill)
        if (category != null) {
            for ((trait, row) in CATEGORY_BONUSES) {
                val (value, named) = row
                if (named != category) continue
                if (levelOf(traits, trait) == null) continue
                add(bonus(trait, value))
            }
        }
        // The conditional social rows, which reach the roll only where the GM
        // says the circumstance holds. Emitted as FromTrait like every other
        // trait modifier and NOT as Situational: a cancel budget is measured
        // by SourceKind, and a drawback the character permanently has is not
        // something a GM said about this turn.
        // One loop over the one list the dialog offers (6ff): a social row
        // and a named-skill row both reach a social skill, and two loops
        // would emit a clause twice the day one sits in both.
        for (clause in skillRollClauses(traits, skill)) {
            if (clause.key !in declared) continue
            // Keyed by CLAUSE, not by trait: Social Anxiety prints two
            // circumstances that can both hold, and two modifiers sharing
            // a ModifierId is a collision the resolver should never be
            // handed -- the same reason the declaration key is trait
            // plus clause (6r).
            add(bonus(clause.trait, clause.value, suffix = clause.printed))
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
     * What [traits] add to a roll to recover from stun.
     *
     * The same cell's second clause, and it was refused until 6dr on the
     * ground that this app made no such roll. [StunRecovery] is that roll;
     * read its KDoc for which stuns it answers for and why the others are a
     * gap rather than a refusal.
     *
     * **The clause names the CONDITION, never an attribute** — "+6 to recover
     * from stun" — so it would reach the Fright Effects table's Will and IQ
     * recovery rolls on the same warrant, if this app offered them. That is
     * the opposite scoping to [knockdownBonus] one clause up, whose "+3 on
     * **HT rolls** to avoid knockdown" names both, and the difference is the
     * book's rather than this app's.
     */
    fun stunRecoveryBonus(traits: List<Trait>): Int =
        if (levelOf(traits, COMBAT_REFLEXES) != null) StunRecovery.COMBAT_REFLEXES_BONUS else 0

    /**
     * The traits a character owns whose printed effect is real and lands on
     * nothing this app rolls, each with the reason.
     *
     * Shown rather than silently dropped. A GM who can see "High Pain
     * Threshold -- this app models no shock penalty to ignore" can apply it
     * themselves; one who sees nothing assumes it was counted.
     */
    fun reported(traits: List<Trait>): List<TraitNote> {
        // Both catalogues, because Unfit is a DRAWBACK with a clause to
        // report -- and reading advantages alone made this function blind to
        // exactly the trait it was widened for, which is the shape 6am found
        // one list over when a character's disadvantages rendered nowhere.
        val catalogue = catalogue()
        val known = known()
        return traits.flatMap { owned ->
            val bare = catalogueName(owned.name)
            val row = catalogue[bare.lowercase()] ?: return@flatMap emptyList()
            val printed = row.notes
            val shown = displayName(owned.name)
            // No collection names it: nothing this cell prints is applied but
            // its price and, where the sheet offers one, its roll. Saying
            // nothing reads as counted. Asked of the code that offers the
            // roll, which refuses a quirk sharing a drawback's name.
            if (bare !in known) {
                val rolled = SelfControl.checksFor(listOf(owned)).isNotEmpty()
                val reason = if (rolled) ROLLED_ONLY else UNREAD
                return@flatMap listOf(TraitNote(shown, printed, reason))
            }
            val whole = UNAPPLIED[bare]?.let { listOf(TraitNote(shown, printed, it)) }.orEmpty()
            // A trait can be applied and still have rules left over, so the
            // clauses are reported even though the trait itself is not.
            val clauses = PARTLY_APPLIED[bare].orEmpty().map { (clause, reason) ->
                TraitNote(shown, clause, reason)
            }
            whole + clauses
        }
    }

    /**
     * What [reported] says of a held trait no collection here names.
     *
     * **One sentence for the whole complement of [known]**, which was 148 of
     * the 198 rows when it was first listed (6es) and had never been: 65
     * advantages printed nowhere on the sheet, and the drawbacks printed
     * their cell with no word on whether it was counted. A reason per row
     * would be the honest-looking alternative and is the one that rots --
     * each is a claim about this app that nothing re-reads (6eq). This one is
     * true by construction, PROVIDED every reader of an EFFECT is in [known].
     *
     * **Nothing enforces that proviso.** The census tests name the readers
     * that were found; they cannot see the next one. Before this sentence a
     * trait missing from [known] made the sheet silent, and now the sheet
     * says "applies nothing" beside a rule that is applied -- a disclaimer
     * that expires the day a reader is written (6ba). So a new production
     * reader of a trait by name adds it to [known] in the same commit.
     *
     * **"Its price aside", because a price is read from rows outside
     * [known]** and the first wording, "reads no rule from this row", was
     * false of five of them (the diff review's MEDIUM): [Wealth] resolves the
     * four `Wealth:` rows and the builder shows their starting money, and
     * [pricedVariants] takes Social Stigma's two prices out of its cell.
     * Neither applies anything the cell prints to a roll or a pool.
     *
     * Read after the sheet's own "Not applied here:" prefix.
     */
    const val UNREAD =
        "its price aside, this app applies nothing this row prints, so its " +
            "text is the table's to apply"

    /**
     * What [reported] says of a held trait outside [known] whose row offers
     * a self-control roll: [UNREAD], the roll excepted.
     *
     * **6es exempted these rows whole**, on the reading that a row offering
     * its roll is applied. 28 of the 30 self-control rows were outside
     * [known] (27 since 6ex applied Reluctant Killer's; a sweep test pins
     * the count), and their cells print
     * rules beside the roll, among them Reckless's *"-1 to any roll where
     * caution would be smarter"*, Reluctant Killer's *"-4 to hit ... cannot
     * Aim"*, Absent-Mindedness's -3 and Nightmares' FP. None reached
     * anything and the sheet said nothing. The same proviso as [UNREAD]: a
     * new reader of one of these rows adds it to [known] in the same commit.
     *
     * Decided by [SelfControl.checksFor], the code that offers the roll, so
     * a quirk sharing a drawback's name is not told its roll is applied.
     */
    const val ROLLED_ONLY =
        "its price and its self-control roll aside, this app applies nothing " +
            "this row prints, so the rest of its text is the table's to apply"

    /**
     * One held trait as the sheet prints it: the book's [cell] where the
     * sheet's spelling finds one, and what of it is not applied.
     */
    data class HeldTrait(
        val trait: Trait,
        val shown: String,
        val cell: String?,
        val notes: List<TraitNote>,
    )

    /**
     * Every trait of [kind] in [traits], in sheet order.
     *
     * The advantages had no such list: the sheet printed [reported] and the
     * drawbacks, so an advantage with nothing to report -- applied in full,
     * or read by nothing -- was nowhere on the sheet, and its cell on no
     * screen at all (6am's finding, one list over). A sheet name that matches no row keeps its name and a
     * null cell, which is the record 4.1 asks for.
     */
    fun held(traits: List<Trait>, kind: TraitKind): List<HeldTrait> {
        val catalogue = catalogue()
        return traits.filter { it.kind == kind }.map { owned ->
            val shown = displayName(owned.name)
            val row = catalogue[catalogueName(owned.name).lowercase()]
            val (cell, notes) = if (row != null) row.notes to reported(listOf(owned)) else unrowed(owned, shown)
            HeldTrait(owned, shown, cell, notes)
        }
    }

    /**
     * The cell and notes of a held trait no catalogue row prints, from the
     * table that owns it; (null, none) where none does, which is 4.1's record.
     * A census over every fixture and table row found three owners (6fr).
     */
    private fun unrowed(owned: Trait, shown: String): Pair<String?, List<TraitNote>> {
        // A face: the appearance table's reaction column, which nothing applies (6fq).
        val face = Appearance.played(listOf(owned))
        if (face != null) {
            val cell = Appearance.cellFor(face)
            return cell to listOf(TraitNote(shown, cell, UNREAD))
        }
        // A wealth level by another name ("Poverty: Struggling", nine sheets)
        // prints the book's row; Wealthy and Very Wealthy have none, so the table's.
        val level = Wealth.levelOf(listOf(owned))
        if (level != null) {
            val book = Wealth.traitFor(level)
            val row = book?.let { catalogue()[it.name.lowercase()] }
            if (row != null) return row.notes to reported(listOf(book)).map { it.copy(trait = shown) }
            val cell = Wealth.cellFor(level)
            return cell to listOf(TraitNote(shown, cell, UNREAD))
        }
        // A potion's sense row: its chip is declared on Per rolls (6fi), and
        // two rows print more than the chip carries.
        val name = catalogueName(owned.name)
        val sense = GRANTED_ONLY_SENSES.firstOrNull { it.trait.equals(name, ignoreCase = true) }
            ?: return null to emptyList()
        val rest = SENSE_ROW_REST[sense.trait] ?: return sense.printed to emptyList()
        return "${sense.printed}; $rest" to listOf(TraitNote(shown, rest, SENSE_REST))
    }

    // ---- the five rows that modify a Fright Check --------------------------

    /**
     * Every clause the book prints against a Fright Check, before a character
     * is considered.
     *
     * The numbers live here rather than in the JSON, the split the maneuver
     * table's +4/-4 (6i) and the orthodox -2 (6z) take; the sentence each was
     * read from is the clause itself, and a test pins every one against
     * [ContentLoader].
     *
     * **Four of the five name a circumstance and one does not**, which is the
     * only structural fact a screen needs: Fearlessness is what the character
     * always is, and the other four are what the scene happens to be.
     */
    private val FRIGHT_CLAUSES: List<TraitClause> = listOf(
        frightClause("Fearlessness", "+1 per level to Fright Checks", 1, conditional = false),
        frightClause(
            "Faithful Grounding",
            "Fright Checks in church buildings, shrines, or during prayer are at +3",
            3,
        ),
        frightClause("Chronophobia (Temporal)", "immediate Fright Check at -3", -3),
        frightClause("Phobia (specify)", "Fright Check at -2 when exposed", -2),
        // ONE cell, TWO circumstances, and both are offered. A cathedral
        // holding a relic is both at once; the book prints no exclusion, and
        // two consecutive sentences of one cell were already read as adding
        // (6aa's Precious altar beside its sentient offering).
        frightClause("Phobia: Sacred Symbols", "Fright Check at -2 on direct exposure", -2),
        frightClause("Phobia: Sacred Symbols", "-1 in consecrated buildings", -1),
    )

    /** The printed rows, for the pin tests. [shown] is the row's own name. */
    fun frightClauseRows(): List<TraitClause> = FRIGHT_CLAUSES

    /** The traits whose cell modifies a Fright Check, for the guard tests. */
    fun frightClauseNames(): Set<String> = FRIGHT_CLAUSES.map { it.trait }.toSet()

    /**
     * The clauses [traits] bring to a Fright Check, under the sheet's own
     * spellings.
     *
     * Authored here rather than assembled by the screen: two copies of one
     * decision is what this file records five times over, and the dialog was
     * not even being handed the traits until this slice (Codex, on the
     * design).
     */
    fun frightClauses(traits: List<Trait>): List<TraitClause> = traits.flatMap { owned ->
        val bare = catalogueName(owned.name)
        val levels = levelOf(traits, bare) ?: 1
        FRIGHT_CLAUSES
            .filter { it.trait.equals(bare, ignoreCase = true) }
            .map { row ->
                // Only Fearlessness prints "per level", and it prints no
                // ceiling of its own -- unlike Charisma and Outdoorsman, whose
                // cells name one, so a general cap would invent one here.
                val value = if (row.conditional) row.value else row.value * levels
                row.copy(shown = displayName(owned.name), value = value)
            }
    }

    /**
     * What [clauses] add to a Fright Check once the GM has [declared] which
     * circumstances hold, and the sentence saying so.
     *
     * An unconditional clause counts whether or not it was declared: the book
     * attaches no circumstance to it, so withdrawing it because a caller
     * forgot to pass it would silently take away a bonus the character always
     * has. Declared clauses SUM -- the book prints no composition rule, and
     * the two sentences of Sacred Symbols' cell name two circumstances that
     * can hold at once.
     */
    fun clauseBonus(clauses: List<TraitClause>, declared: Set<String>): ClauseBonus {
        val applied = clauses.filter { !it.conditional || it.key in declared }
        if (applied.isEmpty()) return ClauseBonus(0, null)
        val parts = applied.joinToString(", ") {
            "${it.shown} ${if (it.value > 0) "+" else ""}${it.value}"
        }
        return ClauseBonus(applied.sumOf { it.value }, parts)
    }

    /** A row of [FRIGHT_CLAUSES]: [shown] is filled in per character. */
    private fun frightClause(
        trait: String,
        printed: String,
        value: Int,
        conditional: Boolean = true,
    ) = TraitClause(trait, trait, printed, value, conditional)

    // ---- the three rows that modify a bare Will roll -----------------------

    /**
     * Every clause the book prints against a Will roll, before a character is
     * considered.
     *
     * **The query is `Will rolls`, plural, and it returns four rows.** 6de
     * recorded that count without stating the query, which is why it could
     * not be checked: `Compulsive Gambling` prints "requires Will roll at -2"
     * in the SINGULAR and is already read as a self-control check (the
     * catalogue's own `selfControl` field), and `Reluctant Killer`, which the
     * plural query does return, carries no number at all and is read the same
     * way. The three that remain carry a modifier and reached nothing.
     *
     * **None of the three qualifies the ROLL, so each reaches all of them.**
     * The bar is the one 6de applied to the HP table's two HT rolls: `Hard to
     * Kill`'s "HT rolls to avoid death" picks one of a PRINTED pair, and
     * `High Pain Threshold`'s "to avoid knockdown" names a printed roll this
     * app does not make. "To resist Beyonder abilities", "in situations that
     * echo a specific loss" and "to act clearly" name no printed roll at all
     * -- they name the circumstance -- so they are declarations, and the roll
     * they reach is whichever of this app's three bare Will rolls
     * (self-control, Concentrate, the Fright Check) is happening when the GM
     * says the circumstance holds.
     *
     * `Grief-Stricken` was routed here by a test and never collected:
     * `SelfControlTest` records that "its offset is attached TO a roll ... so
     * it is a modifier and `TraitEffects`' business", and this file had never
     * taken the business. The suite knew and production did not (6bx, 6bz).
     *
     * Each cell is quoted whole, because the qualifier is the GM's to honour:
     * `Incorruptible Will` excludes mundane social pressure in its own words,
     * and no code here can tell which pressure a scene is.
     */
    private val WILL_CLAUSES: List<TraitClause> = listOf(
        willClause(
            "Incorruptible Will",
            "+3 to all Will rolls to resist Beyonder abilities, evil god " +
                "whispers, and ritual compulsion",
            3,
        ),
        willClause(
            "Grief-Stricken",
            "In situations that echo a specific loss, Will rolls to act " +
                "clearly are at -2",
            -2,
        ),
        willClause(
            "Evil Eye Fear",
            "If interrupted from a warding routine, -1 to Will rolls for the day",
            -1,
        ),
    )

    /** The printed rows, for the pin tests. [TraitClause.shown] is per character. */
    fun willClauseRows(): List<TraitClause> = WILL_CLAUSES

    /** The traits whose cell modifies a bare Will roll, for the guard tests. */
    fun willClauseNames(): Set<String> = WILL_CLAUSES.map { it.trait }.toSet()

    /**
     * The clauses [traits] bring to a bare Will roll, under the sheet's own
     * spellings.
     *
     * No level scaling, unlike [frightClauses]: not one of the three cells
     * prints "per level", so multiplying would invent a scale where
     * Fearlessness's cell states one.
     */
    fun willClauses(traits: List<Trait>): List<TraitClause> = traits.flatMap { owned ->
        val bare = catalogueName(owned.name)
        WILL_CLAUSES
            .filter { it.trait.equals(bare, ignoreCase = true) }
            .map { it.copy(shown = displayName(owned.name)) }
    }

    const val THINNED_VEIL = "Thinned Veil"
    const val CHRONIC_INSOMNIA = "Chronic Insomnia"

    /**
     * The clauses that reach the Concentrate maneuver's roll and no other
     * ([Concentration], "Roll Will-3 if injured or defending").
     *
     * Thinned Veil prints "Concentration in spiritually active environments
     * at -2" (6fa). That is a concentration check, which the Fright Check and
     * a self-control roll are not, so it stays out of [WILL_CLAUSES]. The
     * environment is the GM's to declare. **A reading, not the book's only
     * meaning** (the design review): a ritual's focus could claim the same
     * sentence, so [PARTLY_APPLIED] says every other roll is the GM's call.
     * The capital C is not a warrant, because the word begins its sentence.
     *
     * Chronic Insomnia prints "rolls requiring sustained concentration at -1"
     * (6fb). The book sets no duration that makes concentration sustained,
     * and never says every Concentrate roll is, so it is the GM's to declare
     * like the environment above. Single-Minded and Absent-Mindedness stay
     * out by their own words (6fa).
     */
    private val CONCENTRATION_CLAUSES: List<TraitClause> = listOf(
        TraitClause(
            THINNED_VEIL,
            THINNED_VEIL,
            "Concentration in spiritually active environments at -2",
            -2,
            conditional = true,
        ),
        TraitClause(
            CHRONIC_INSOMNIA,
            CHRONIC_INSOMNIA,
            "rolls requiring sustained concentration at -1",
            -1,
            conditional = true,
        ),
    )

    /** The traits whose cell reaches the Concentrate roll alone, for [known]. */
    fun concentrationClauseNames(): Set<String> = CONCENTRATION_CLAUSES.map { it.trait }.toSet()

    /**
     * What [traits] bring to the Concentrate roll: every bare Will clause
     * ([willClauses]), plus the ones that name concentration itself.
     */
    fun concentrationClauses(traits: List<Trait>): List<TraitClause> =
        willClauses(traits) + traits.flatMap { owned ->
            val bare = catalogueName(owned.name)
            CONCENTRATION_CLAUSES
                .filter { it.trait.equals(bare, ignoreCase = true) }
                .map { it.copy(shown = displayName(owned.name)) }
        }

    /** A row of [WILL_CLAUSES]: every one names a circumstance, so all are conditional. */
    private fun willClause(trait: String, printed: String, value: Int) =
        TraitClause(trait, trait, printed, value, conditional = true)

    // ---- the social rows whose penalty needs a circumstance ----------------

    /**
     * Every clause that penalises a social skill roll only when a printed
     * circumstance holds, before a character is considered.
     *
     * All three rows name the GROUP identifiably -- [SOCIAL_SKILLS], read from
     * the content since 6bt -- and none of them names the CIRCUMSTANCE in
     * anything this app can compute: whether warmth is required, how many
     * people are in the room, whether the listener outranks you. So they are
     * declarations, exactly as the four conditional Fright Check rows are, and
     * four of the five carriers own a social skill, which is why applying them
     * flat would charge a penalty nobody declared.
     *
     * **Shyness's value is decided by the sheet, not by the GM.** Its cell
     * prices two severities and a character bought one of them; the app can
     * tell which only because the sheet spells it and the price agrees
     * (`variantRow`). The circumstance stays the GM's, but which clause is on
     * offer is not a choice — Academic Researcher is Mild.
     */
    private val SOCIAL_CLAUSES: List<Pair<String?, TraitClause>> = listOf(
        null to socialClause(
            "Callous",
            "-3 to social skill rolls when warmth or empathy is required",
            -3,
        ),
        "Mild" to socialClause("Shyness", "Mild (-5): -1 in groups of 6+", -1),
        "Severe" to socialClause("Shyness", "Severe (-10): -2 in groups of 3+", -2),
        // One cell, two circumstances that can both hold -- a large gathering
        // of strangers who outrank you -- and the book prints no exclusion, so
        // they are separate declarations that sum. The reading 6aa made of two
        // consecutive sentences, and 6bv of Sacred Symbols' two.
        null to socialClause(
            "Social Anxiety",
            "-2 to social skill rolls in groups of 6+",
            -2,
        ),
        null to socialClause(
            "Social Anxiety",
            "-3 when addressing strangers of higher status",
            -3,
        ),
    )

    /** The printed rows, for the pin tests. */
    fun socialClauseRows(): List<TraitClause> = SOCIAL_CLAUSES.map { it.second }

    const val RELUCTANT_KILLER = "Reluctant Killer"

    /**
     * Reluctant Killer's two to-hit clauses, book line 1313: *"-4 to hit
     * recognizable people with deadly force (-2 if face hidden)"*.
     *
     * **"To hit" names the attack roll, not a skill banner**, so these are
     * not emitted by [modifiers]: COMBAT SKILLS also holds Fast-Draw and
     * Shield, which make no attack (the design review). Whether this target
     * is a recognizable person and the force deadly is the GM's declaration,
     * the reading [socialClauses] makes of a circumstance.
     */
    private val ATTACK_CLAUSES: List<TraitClause> = listOf(
        TraitClause(
            RELUCTANT_KILLER, RELUCTANT_KILLER,
            "-4 to hit recognizable people with deadly force", -4, conditional = true,
        ),
        TraitClause(RELUCTANT_KILLER, RELUCTANT_KILLER, "-2 if face hidden", -2, conditional = true),
    )

    /** The to-hit clauses [traits] offer the GM, in the cell's order. */
    fun attackClauses(traits: List<Trait>): List<TraitClause> = traits.flatMap { owned ->
        if (owned.kind != TraitKind.DISADVANTAGE) return@flatMap emptyList()
        val bare = catalogueName(owned.name)
        ATTACK_CLAUSES.filter { it.trait.equals(bare, ignoreCase = true) }
            .map { it.copy(shown = displayName(owned.name)) }
    }

    /**
     * The term the [declared] clause adds to an attack, or none.
     *
     * **One declaration, not a set**: the parenthetical is the same penalty
     * at a lower value, since a hidden face is still a recognizable person,
     * and two keys would let one attack take -6. A key [attackClauses] did
     * not offer [traits] is refused, so a stale or foreign declaration
     * cannot reach the dice (the design review).
     */
    fun attackModifiers(traits: List<Trait>, declared: String?): List<Modifier> {
        if (declared == null) return emptyList()
        val clause = attackClauses(traits).firstOrNull { it.key == declared }
        requireNotNull(clause) { "no to-hit clause offered for '$declared'" }
        return listOf(bonus(clause.trait, clause.value, suffix = clause.printed))
    }

    /**
     * Whether [traits] take the Aim rows away: *"cannot Aim"*, book lines
     * 1313-1314.
     *
     * **Unconditional, the literal reading.** The clause follows the
     * semicolon with no qualifier of its own. I proposed scoping it to the
     * declared target, and the design review's MEDIUM was that the cell
     * prints no such gate (6ev's lesson about a gate that was mine). The
     * drawback alone: a quirk sharing the name is not it.
     */
    fun refusesAim(traits: List<Trait>): Boolean =
        attackClauses(traits).isNotEmpty()

    /** The cell's own words for [refusesAim], for the screen. */
    const val AIM_REFUSAL = "cannot Aim"

    /** The traits whose cell penalises a social roll conditionally. */
    fun socialClauseNames(): Set<String> = SOCIAL_CLAUSES.map { it.second.trait }.toSet()

    /**
     * The clauses [traits] bring to a social skill roll, under the sheet's own
     * spellings.
     *
     * A row whose clause is keyed to a variant is offered only to the
     * character who bought that variant, so a Mild shyness never produces
     * Severe's -2.
     */
    fun socialClauses(traits: List<Trait>): List<TraitClause> = traits.flatMap { owned ->
        val bare = catalogueName(owned.name)
        val variant = owned.name.substringAfterLast('(', "").removeSuffix(")")
        SOCIAL_CLAUSES
            .filter { (needs, row) ->
                row.trait.equals(bare, ignoreCase = true) &&
                    // A row keyed to a variant needs one NAMED. An empty
                    // qualifier prefixes every option, so a bare "Shyness"
                    // would offer Mild and Severe at once and let a GM declare
                    // both -- and the severity is the sheet's to state, not a
                    // choice (Codex). Refused rather than guessed, the call
                    // `Detection.attributeFor` makes for an attribute the
                    // table never names (6ah).
                    (
                        needs == null ||
                            (variant.isNotEmpty() && needs.startsWith(variant, ignoreCase = true))
                        )
            }
            .map { (_, row) -> row.copy(shown = displayName(owned.name)) }
    }

    /**
     * The rows that name ONE skill and a circumstance this app cannot compute
     * (6es's first list; 6ff), so each is a declaration on that skill's roll.
     * [NamedSkillClause.specializations] null is every row of the name;
     * Navigator's Eye is "rural", so it leaves out Navigation (Sea).
     */
    private data class NamedSkillClause(
        val skill: String,
        val specializations: Set<String?>?,
        val clause: TraitClause,
    )

    private val SKILL_CLAUSES: List<NamedSkillClause> = listOf(
        // "in either" is the Savoir-Faire of either class, and the row
        // records neither class, so the GM says which this is.
        skillClause("Class Mobility", "Savoir-Faire", "+2 to Savoir-Faire in either", 2),
        skillClause("Fearsome Reputation", "Intimidation", "Intimidation in these circles is at +2", 2),
        skillClause(
            "Safecracker", "Lockpicking",
            "+3 to Lockpicking for combination locks and mechanical safes specifically", 3,
        ),
        skillClause("Alcohol Tolerance", "Carousing", "Carousing rolls to appear sober at +3", 3),
        skillClause(
            "Navigator's Eye", "Navigation", "rural navigation rolls at +2", 2,
            specializations = setOf(null, "Land"),
        ),
    )

    private fun skillClause(
        trait: String,
        skill: String,
        printed: String,
        value: Int,
        specializations: Set<String?>? = null,
    ) = NamedSkillClause(skill, specializations, TraitClause(trait, trait, printed, value, conditional = true))

    const val ACUTE_VISION = "Acute Vision"
    const val ACUTE_HEARING = "Acute Hearing"

    /**
     * "+2 to Vision rolls" and "+2 to Hearing rolls" (book 936, 940; 6fh).
     * The book never lists those rolls, but the potion rows print the same
     * bonus as "+2 to vision-based Perception rolls" (8688), so a Vision roll
     * is a Per roll. Every Per-controlled skill offers both, and the GM
     * declares which sense this roll is. Flat: neither cell says "per level".
     *
     * Detection is offered neither: its "SPI or Per roll" (3810) senses a
     * divination and names no sense. Lip Reading ("Understand speech without
     * hearing it") still offers Hearing, which its note contradicts; accepted
     * rather than blocklisted, since the GM declares and an unticked chip
     * changes nothing (6fh's design review).
     */
    private val SENSE_CLAUSES: List<TraitClause> = listOf(
        TraitClause(ACUTE_VISION, ACUTE_VISION, "+2 to Vision rolls", 2, conditional = true),
        TraitClause(ACUTE_HEARING, ACUTE_HEARING, "+2 to Hearing rolls", 2, conditional = true),
    )

    const val GREATER_ACUTE_VISION = "Greater Acute Vision"
    internal const val ACUTE_TASTE_SMELL = "Acute Taste & Smell"
    internal const val ACUTE_TOUCH = "Acute Touch"
    internal const val ACUTE_SMELL = "Acute Smell"
    const val INCISIVE_VISION = "Incisive Vision"

    /**
     * The sense rows no catalogue prints: a potion's alone (6fi), each as its
     * block prints it. Pinned against that prose, never against [known],
     * which is the advantage catalogue's census. Incisive Vision prints a
     * name and "+4" and nothing else (line 6334), so its chip says exactly
     * that and the GM says what it reaches.
     *
     * **A different row is a different chip.** Greater Acute Vision (Demoness
     * 6) and Incisive Vision (Visionary 8) follow an Acute Vision their
     * pathway's Sequence 9 granted, and neither cell says "Replaces" (as 8887
     * and 9456 do) or prints a total (as 7257 and 8005 do). So both chips are
     * offered: the +4 alone is what either reading gives, and whether the
     * +2 adds is the GM's, as Fast Healer's failure is (6fc). **The same row
     * twice is one chip**: a bought and a granted Acute Vision, or Red Priest
     * 9's and 6's. Its cell is a flat "+2", not per level, and the stacking
     * rule at 5458 is scoped to skills by its own words.
     */
    private val GRANTED_ONLY_SENSES: List<TraitClause> = listOf(
        TraitClause(
            GREATER_ACUTE_VISION, GREATER_ACUTE_VISION,
            "+4 to all vision-based Perception rolls", 4, conditional = true,
        ),
        TraitClause(
            ACUTE_TASTE_SMELL, ACUTE_TASTE_SMELL,
            "+2 to taste/smell-based Perception rolls", 2, conditional = true,
        ),
        TraitClause(ACUTE_TOUCH, ACUTE_TOUCH, "+2 to touch-based Perception rolls", 2, conditional = true),
        TraitClause(ACUTE_SMELL, ACUTE_SMELL, "+2 sharper sense of smell", 2, conditional = true),
        TraitClause(INCISIVE_VISION, INCISIVE_VISION, "+4", 4, conditional = true),
    )

    /** What two of those rows print past their bonus (7196-7198, 8355-8357), which nothing reads (6fr). */
    private val SENSE_ROW_REST: Map<String, String> = mapOf(
        ACUTE_SMELL to "detect subtle scents and emotional cues",
        GREATER_ACUTE_VISION to "allows the Pleasure to see their own Spider's Threads",
    )

    private const val SENSE_REST =
        "its bonus aside, declared on Perception rolls, this app applies nothing this row prints, " +
            "so the rest is the table's to apply"

    private fun senseClauses(traits: List<Trait>): List<TraitClause> = traits.flatMap { owned ->
        val bare = catalogueName(owned.name)
        (SENSE_CLAUSES + GRANTED_ONLY_SENSES)
            .filter { it.trait.equals(bare, ignoreCase = true) }
            .map { it.copy(shown = displayName(owned.name)) }
    }

    /**
     * Acute Vision's clause, for Spirit Vision's Perception-based roll (book
     * 4840), which is a sight by its own name: the GM declares whether the
     * eye's acuity reaches it. Hearing is never offered there. Null for a
     * character who does not hold it. Acute Vision alone, because the potion
     * rows above it (Greater, Incisive) are Demoness's and Visionary's, and
     * neither pathway is granted Spirit Vision; Red Priest 8 is, and holds
     * Sequence 9's Acute Vision (6fi).
     */
    fun visionClause(traits: List<Trait>): TraitClause? =
        senseClauses(traits).firstOrNull { it.trait == ACUTE_VISION }

    /** The traits whose cell names one skill, or one sense, and a circumstance, for [known]. */
    fun skillClauseNames(): Set<String> = skillClauseRows().map { it.trait }.toSet()

    /** The printed rows, for the clause guard. */
    fun skillClauseRows(): List<TraitClause> = SKILL_CLAUSES.map { it.clause } + SENSE_CLAUSES

    /**
     * Every clause a roll of [skill] asks the GM about: the social rows on a
     * social skill, the rows naming this skill, and the sense rows on a Per
     * skill. The one list the dialog offers and [modifiers] reads, so the two
     * cannot disagree (6ff). Distinct by catalogue row and clause, which is
     * what a modifier's id reads, so a row held twice or under two spellings
     * is one declaration and one modifier, never two under one id (6fh).
     */
    fun skillRollClauses(traits: List<Trait>, skill: SkillKey): List<TraitClause> {
        val sense = if (skill.attr == ControllingAttribute.Per) senseClauses(traits) else emptyList()
        val social = if (categoryOf(skill) == SOCIAL_SKILLS) socialClauses(traits) else emptyList()
        val named = traits.flatMap { owned ->
            val bare = catalogueName(owned.name)
            SKILL_CLAUSES
                .filter { row ->
                    row.clause.trait.equals(bare, ignoreCase = true) &&
                        row.skill == skill.name &&
                        (row.specializations == null || skill.specialization in row.specializations)
                }
                .map { it.clause.copy(shown = displayName(owned.name)) }
        }
        return (social + named + sense).distinctBy { it.trait to it.printed }
    }


    /** A row of [SOCIAL_CLAUSES]: always conditional, never counted unasked. */
    private fun socialClause(trait: String, printed: String, value: Int) =
        TraitClause(trait, trait, printed, value, conditional = true)

    /** The traits whose cell names the skills it lifts, for the coverage test. */
    fun skillBonusNames(): Set<String> = SKILL_BONUSES.keys

    /** The skills [trait] names, empty for a trait that names none. */
    fun skillsFor(trait: String): Set<String> = SKILL_BONUSES[trait]?.skills.orEmpty()

    /** The ceiling [trait]'s own cell prints, null for a row that prints none. */
    fun maxLevelsFor(trait: String): Int? = SKILL_BONUSES[trait]?.maxLevels

    /**
     * Every trait this object knows about, applied or not.
     *
     * [skillBonusNames] is in here because Outdoorsman is the first row that
     * is wholly applied and has no clause left to report -- every earlier one
     * happened to appear in [PARTLY_APPLIED] as well, so the catalogue guard
     * covered them by accident (Codex, on the design). A typo in a name that
     * appears nowhere else makes the modifier unreachable in silence, which
     * is exactly what that guard exists to catch.
     */
    fun known(): Set<String> =
        UNAPPLIED.keys + PARTLY_APPLIED.keys + SKILL_BONUSES.keys +
            CATEGORY_BONUSES.keys + frightClauseNames() + socialClauseNames() +
            ATTACK_CLAUSES.map { it.trait } +
            willClauseNames() + concentrationClauseNames() + skillClauseNames() +
            // [HT_ROLL_TRAITS].keys, not FIT alone. The map has held three
            // traits since 6de and this set named one -- and the guard still
            // covered all three, because `Very Fit` and `Unfit` are also
            // [PARTLY_APPLIED] keys. That is the accident this function's own
            // KDoc already records against Outdoorsman, one map over: covered
            // by a second route rather than by the one that applies them, so
            // the first HT-roll trait with nothing left to report would have
            // been the one that slipped. A census reads the collection, never
            // a member of it.
            HT_ROLL_TRAITS.keys + setOf(ALERTNESS, COMBAT_REFLEXES) + SPI_ROLL_TRAITS.keys +
            // The third instance of the failure this KDoc records twice above,
            // and the one that had no collection to add: see [BespokeBonus].
            BespokeBonus.entries.map { it.trait } +
            // Applied in another file: [Languages.owned] reads these two to
            // price a language. The fourth instance, and the first where the
            // reader is not this object.
            Languages.PRICING_TRAITS +
            // The fifth and sixth, found by listing the complement (6es)
            // rather than by the next accident: [darknessReduction] applies
            // one from this object, and [Corruption.maxFor] has read the
            // other since M4. Both clauses of Night Eyes are applied
            // ([DarknessCause]), so neither has anything to report.
            setOf(NIGHT_EYES, Corruption.FORTITUDE) +
            // The seventh, from 6es's untriaged list: [actingGains] (6ez).
            setOf(ACTING_METHOD)

    /** The traits reported whole, for the disjointness test. */
    fun unappliedNames(): Set<String> = UNAPPLIED.keys

    /** The traits reported clause by clause, for the disjointness test. */
    fun partlyAppliedNames(): Set<String> = PARTLY_APPLIED.keys

    private fun bonus(trait: String, value: Int, suffix: String? = null) = Modifier(
        id = ModifierId("trait:${trait.lowercase()}" + (suffix?.let { ":$it" } ?: "")),
        // FromTrait, NOT Situational. Both existed from M0 and this is the
        // first production caller of either; `Situational` is what a GM says
        // about this turn, and an advantage is what the character permanently
        // IS. They resolve to different `SourceKind`s, which is what a
        // `Cancel` budget is measured against -- so mislabelling one as the
        // other would only surface the day some situational rule tried to
        // cancel a circumstance and silently ate a trait instead.
        //
        // The advantage flag is read from the CATALOGUE rather than passed
        // in, because it is a fact about the trait and not about the caller:
        // Unfit is the first disadvantage here, and a second place deciding
        // the sign is how these decisions have drifted before (6ag, 6ai,
        // 6be, 6bp). It picks the `SourceKind` a `Cancel` budget is measured
        // against, so it is not cosmetic.
        source = ModifierSource.FromTrait(trait, advantage = isAdvantage(trait)),
        // Retargeted onto the skill being rolled by SkillRoll, exactly as
        // Conditions' and CommonModifiers' rows are.
        target = ModTarget.AllRolls,
        effect = ModEffect.Flat(value),
        // Layer is the resolution STAGE, not the sign: the enum prints no
        // DISADVANTAGE member because a drawback resolves where an advantage
        // does. Only the source's kind separates them.
        layer = Layer.ADVANTAGE,
    )

    /** Whether the book prints [trait] as an advantage rather than a drawback. */
    private fun isAdvantage(trait: String): Boolean =
        ContentLoader.advantages().any { it.name.equals(trait, ignoreCase = true) }
}
