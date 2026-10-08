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
import com.pathways.rules.content.TraitDefinition

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
     * occult, which is the list-building 6i refused when no skill is tagged as
     * a combat skill. Every occult skill this book prints -- Occultism, Hidden
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
     * default in its own word. It is the only default this app rolls, which
     * is what the entry left in [PARTLY_APPLIED] says on the sheet.
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
     * detection). **A sweep in 6de asked whether any trait names those**, and
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
     * The level a character owns [name] at, or null when they do not own it.
     *
     * A sheet prints a levelled trait with the level IN the name --
     * "Alertness 1", "Hard to Kill 2" -- while the catalogue prints the bare
     * name and a `perLevel` flag. An unlevelled trait matches exactly and
     * counts as ONE, never zero: zero would silently cancel its own bonus.
     */
    fun levelOf(traits: List<Trait>, name: String): Int? {
        val owned = traits.firstOrNull {
            catalogueName(it.name).equals(name, ignoreCase = true)
        } ?: return null
        // A trailing integer is a level; anything else is part of the name
        // ("Poverty: Struggling"), so it does not become one.
        return owned.name.substringAfterLast(' ').toIntOrNull() ?: 1
    }

    /**
     * The catalogue row an owned trait's printed name resolves to, or that
     * name unchanged when it resolves to none.
     *
     * **One resolution, shared by everything that matches a trait**: this
     * object's applied bonuses and [reported], [SelfControl.checksFor], and
     * the sheet's disadvantage rows. Two matchers is how a character ends up
     * with a bonus whose explanation vanished -- the
     * two-copies-of-one-decision failure this file records three times over
     * (6ag, 6ai, 6be) and 6bp repeated in a caption.
     *
     * Two shapes resolve. A trailing integer is a LEVEL, so "Alertness 2" is
     * Alertness (6al). And **three premades print the trait's name and the
     * book's own effect cell as one string** -- "Healer - +2 to all rolls to
     * diagnose, treat, and heal; ..." -- because the published sheet ran them
     * together on one line.
     *
     * **The second shape is an exact join, not a fuzzy one.** The tail must
     * equal the catalogue row's own notes, ignoring the whitespace the sheet
     * wrapped it with; the sheet is quoting the book verbatim, so the two are
     * provably the same trait. That is what makes it different from the
     * unmatched spellings 6am recorded -- "Absent-Minded" against
     * "Absent-Mindedness", "Paranoia: Bodies of water" against a bare
     * "Paranoia" -- where the only evidence is resemblance and 4.1 says the
     * disagreement is recorded rather than guessed away. Four other glued
     * names in the fixtures fail this test and stay unmatched.
     */
    fun catalogueName(printed: String): String {
        if (printed.substringAfterLast(' ').toIntOrNull() != null) {
            return printed.substringBeforeLast(' ')
        }
        return placeholderRow(printed) ?: variantRow(printed) ?: gluedHead(printed)
            ?: quotedInstanceRow(printed) ?: printed
    }

    /**
     * The row a bracket instantiates where a SHEET has proved it does, or null.
     *
     * **One row is in this set and the evidence is a quotation.** Church Healer
     * prints `"Sense of Duty (Flock) - "` followed by that row's own effect
     * cell VERBATIM -- the only spelling in the corpus that quotes exactly
     * behind an instantiated head -- which is the sheet asserting that
     * `Sense of Duty (X)` IS the bare `Sense of Duty` the book prints. That is
     * the standard 6br set for glued names, and [gluedHead] cannot reach it
     * because the head is not itself a row.
     *
     * **The five bare instances follow from that one, and no further.** The
     * row's notes say "certain groups", and Patients, Friends, Public,
     * Coworkers and Workers are each a group -- so they are the same pattern
     * applied, not five independent guesses.
     *
     * **The obvious generalisation is REFUSED, and the reason is worth the
     * space.** It is tempting to say that any row printing a cost interval and
     * enumerating no variants is instantiated by a bracket: that resolves
     * `Secret (Courier network)` too, and it excludes Shyness, Social Stigma
     * and Reputation -- exactly the three objections 6by raised. But excluding
     * the counter-examples is not the same as producing evidence, and the
     * corroboration on offer is the wrong kind: **every instance paying inside
     * its printed interval is evidence about PRICING, never about catalogue
     * IDENTITY** (Codex, on the design). With the filters removed it is the
     * plausible match 4.1 calls repair. So `Secret` stays unmatched, because
     * no sheet quotes its cell, and the day one does this set grows by a name.
     */
    private fun quotedInstanceRow(printed: String): String? {
        // The glued spelling carries the quotation AND the bracket, so the
        // tail comes off first; the bare five carry the bracket alone.
        val named = printed.substringBefore(SHEET_SEPARATOR)
        if (!named.endsWith(")")) return null
        val open = named.lastIndexOf(" (")
        if (open <= 0 || named.substring(open + 2, named.length - 1).isBlank()) return null
        val head = named.substring(0, open)
        val row = QUOTED_INSTANCE_ROWS.firstOrNull { it.equals(head, ignoreCase = true) }
            ?: return null
        // **A tail that is present must still be the row's own words.**
        // Dropping it unconditionally would resolve the bracket on a sheet
        // whose quotation was a typo, a partial quote or a different note --
        // silently repairing a disagreement 4.1 says to record, and letting
        // the glued path round 6br's exact-tail rule (Codex).
        if (printed == named) return row
        val tail = printed.substringAfter(SHEET_SEPARATOR)
        val notes = catalogue()[row.lowercase()]?.notes ?: return null
        return row.takeIf { quoted(tail) == quoted(notes) }
    }

    /**
     * Rows a published sheet has proved are instantiated by a bracket.
     *
     * Named rather than derived, because the evidence lives in the FIXTURES
     * and production cannot read them. A test pins the quotation that puts
     * each name here, so the warrant fails loudly rather than the set drifting.
     */
    private val QUOTED_INSTANCE_ROWS = listOf("Sense of Duty")

    /**
     * A priced option a cell enumerates for itself, as (name, points).
     *
     * Two rows in 198 print one of these, and both are offering a CHOICE:
     * Shyness prints "Mild (-5): ... Severe (-10): ..." and Social Stigma
     * "Criminal Record -5, Servant Class -5".
     *
     * **Two conditions make this safe, and they exclude different rows.**
     *
     * **An enumeration has at least two members.** Allow one and the same
     * pattern reads "Must roll vs. Will-3" as a variant called "Will" priced
     * at 3 -- eleven rows print a number in prose that way, including four
     * self-control targets.
     *
     * **A variant is a NAME, so it is capitalised.** This is the condition
     * that keeps out a SCALE with worked points, and the member count cannot:
     * Mystical Item prints "Cost scales with power: a minor charm (5 pts), a
     * useful tool (10 pts), or a significant piece of equipment (15 pts)" and
     * Duty (specify) prints "-5 for light duty, -10 for hazardous duty, -15
     * for extremely hazardous" -- three priced options each, spanning their
     * own cost range, and neither is a closed set. Their own sentences say the
     * value MOVES ("scales with power", "reflects frequency and danger"), so
     * the rows admit a price between the points they work. Read as
     * enumerations they offer a variant called "or a significant piece of
     * equipment".
     *
     * Together the two take 198 rows down to the two this reads. Both are
     * pinned by tests, because widening the parse is the obvious next move
     * here and the near-misses do not look alike.
     */
    fun pricedVariants(notes: String): List<Pair<String, Int>> {
        val found = PRICED_VARIANT.findAll(notes)
            .map { it.groupValues[1].trim() to it.groupValues[2].toInt() }
            .toList()
        return if (found.size >= 2) found else emptyList()
    }

    /** "Mild (-5)" and "Criminal Record -5": a name, then what it costs. */
    private val PRICED_VARIANT =
        Regex("""([A-Z][A-Za-z]*(?: [A-Za-z]+)*?)\s*\(?[-−](\d+)\)?""")

    /**
     * The row whose cell PRICES the variant a sheet spelling names, or null.
     *
     * **A cell that enumerates its own options names them as surely as a
     * placeholder invites one** (see [placeholderRow]). Academic Researcher
     * buys "Shyness (Mild)" for 5 points and the Shyness row prints
     * "Mild (-5)"; Pickpocket buys "Social Stigma (Criminal)" for 5 and the
     * row prints "Criminal Record -5". The qualifier is named by the book and
     * the price agrees -- two independent agreements, which is what separates
     * this from the resemblance 4.1 refuses.
     *
     * **This corrects a stated reason rather than repairing a disagreement.**
     * 6bv refused both on the grounds that "the sheet added a qualifier the
     * book never asked for, so the only evidence is resemblance". That
     * sentence is simply false for these two, and checking a sweep's own
     * excuse is 6bf's lesson. The nine spellings whose reason still holds --
     * five Sense of Duty, two Secret, Paranoia, Blacklisted -- hang off cells
     * that enumerate nothing, and a test pins them still unmatched.
     *
     * The qualifier must PREFIX exactly one option, because the sheet writes
     * "Criminal" where the book writes "Criminal Record". Naming none, or
     * several, refuses rather than guesses (Codex, on the design).
     *
     * The COST is deliberately not read here: this takes a name, and
     * threading a cost through [catalogueName] would give one decision a
     * second matcher. It is the warrant, proven across all 23 fixtures by a
     * test, and the day a sheet pays a price the cell does not print for its
     * variant that test fails rather than this resolving on the name alone.
     */
    private fun variantRow(printed: String): String? {
        val open = printed.lastIndexOf(" (")
        if (open <= 0 || !printed.endsWith(")")) return null
        val qualifier = printed.substring(open + 2, printed.length - 1)
        if (qualifier.isBlank()) return null
        val row = catalogue()[printed.substring(0, open).lowercase()] ?: return null
        val named = pricedVariants(row.notes)
            .filter { it.first.startsWith(qualifier, ignoreCase = true) }
        return if (named.size == 1) row.name else null
    }

    /** How the book asks a sheet to name an instance: "Phobia (specify)". */
    const val PLACEHOLDER = " (specify)"

    /**
     * The placeholder row a printed name is an INSTANCE of, or null.
     *
     * Five rows print [PLACEHOLDER] in their name and eight fixture traits
     * fill it in -- "Phobia (Cities)", three Duties, three Vows, "Resistant
     * (Disease +3)". None resolved before, so every clause they carry was
     * invisible, including the only Fright Check modifier a premade owns.
     *
     * **The warrant is an instruction, not a resemblance.** "(specify)" is the
     * book telling the reader to substitute, and Duty's cell says it in words:
     * "Specify organization and nature at creation." The same reading matched
     * `"Hidden Lore (relevant domain)"` for a ritual's supporting skills
     * (6am), and seven other cells print "(specify)" inside their own prose.
     *
     * **So the join turns on the BOOK printing the word, never on the sheet
     * printing a bracket.** That is what keeps it out of 4.1's territory: the
     * qualifiers 6am refused -- "Paranoia: Bodies of water", "Shyness (Mild)",
     * "Social Stigma (Criminal)", "Sense of Duty (Patients)" -- hang off rows
     * whose names print no placeholder at all, and they stay unmatched.
     *
     * The trailing bracket is the instance, so a row name that carries its own
     * ("Pathway Resonance (Hostile)") cannot be mistaken for a head.
     */
    private fun placeholderRow(printed: String): String? {
        if (!printed.endsWith(")")) return null
        // The LAST bracket first, and then the first: an instance can carry a
        // bracket of its own -- "Language (Balam (Dutan), Accented)" -- and
        // splitting on the last one alone reads the head as "Language (Balam".
        return listOf(printed.lastIndexOf(" ("), printed.indexOf(" (")).distinct()
            .firstNotNullOfOrNull { open ->
                if (open <= 0) return@firstNotNullOfOrNull null
                val inside = printed.substring(open + 2, printed.length - 1)
                // An empty bracket names nothing, and the row is not an
                // instance of itself.
                if (inside.isBlank() || " ($inside)" == PLACEHOLDER) return@firstNotNullOfOrNull null
                val head = printed.substring(0, open)
                catalogue()["${head.lowercase()}$PLACEHOLDER"]?.name
            }
    }

    /**
     * The catalogue row whose name AND effect cell a glued sheet spelling
     * reproduces, or null when no row's does.
     *
     * **One resolution for both callers**, because [displayName] used to split
     * on [SHEET_SEPARATOR] itself: a spelling that resolved for MATCHING while
     * failing to resolve for DISPLAY would apply the trait and render its whole
     * effect cell as the name -- 6br's device-only defect, and a review caught
     * the plan to reintroduce it.
     *
     * **Two glue shapes, one standard of proof.** Three sheets write
     * "Healer - +2 to all rolls to ..." and two write "Clueless -3 to all
     * social skill rolls; ..." with a bare space and no separator at all. The
     * tail must reproduce the row's own notes either way, so the sheet is
     * provably quoting the book rather than resembling it -- which is what
     * separates this from the spellings 6am refused, where a name was similar
     * and nothing else agreed. Four sheet spellings still fail this test and
     * stay unmatched.
     *
     * The longest matching name wins, so a row whose name prefixes another's
     * cannot answer for it.
     */
    private fun gluedHead(printed: String): String? {
        val rows = catalogue()
        // A name that IS a catalogue row carries no glue, and this is the
        // common case: skip the scan rather than searching for a prefix of it.
        if (printed.lowercase() in rows) return null
        return rows.values
            .filter { printed.length > it.name.length && printed.startsWith(it.name) }
            .sortedByDescending { it.name.length }
            .firstOrNull { row ->
                val rest = printed.substring(row.name.length)
                // Exactly the glue, never a greedy trim: a cell that opens
                // with its own "-3" would lose that character to one.
                val tail = when {
                    rest.startsWith(SHEET_SEPARATOR) -> rest.removePrefix(SHEET_SEPARATOR)
                    rest.startsWith(" ") -> rest.removePrefix(" ")
                    else -> return@firstOrNull false
                }
                quoted(tail) == quoted(row.notes)
            }
            ?.name
    }

    /**
     * Two quotations of one sentence, compared as sentences.
     *
     * Only the sheet's typography is normalised away: its line wrapping, the
     * U+2212 it writes where the catalogue writes an ASCII hyphen (the
     * two-kinds-of-minus trap 6u set first, here between two files rather than
     * inside one table), and a trailing full stop that Church Healer drops and
     * Monastery Scholar keeps on the same trait. None of the three can make a
     * quotation a different sentence. Nothing else is touched -- a word that
     * differs is a row that does not match.
     */
    private fun quoted(text: String): String =
        squashed(text).replace('−', '-').trimEnd('.')

    /**
     * What a screen prints as an owned trait's NAME.
     *
     * The sheet's own spelling, minus the book's effect cell where the sheet
     * ran the two together -- because the row displays that cell underneath
     * anyway, so leaving it in the name prints the same sentence twice. **The
     * device is the only thing that could show this**: every test asserted the
     * right lookup and the right clause, and a 150-character name reads as a
     * broken row rather than a wrong one.
     *
     * It collapses ONLY where [catalogueName] verified the tail against the
     * book's own words, so an unmatched glued name keeps every character the
     * sheet printed -- "Sense of Duty (Flock) - Must help/protect certain
     * groups" is a `(specify)` instantiation this book never prints, and 4.1
     * says that disagreement is recorded rather than tidied away. A LEVEL is
     * never stripped either: "Alertness 2" is what the sheet says and what the
     * GM needs to see.
     */
    fun displayName(printed: String): String = gluedHead(printed)
        // A quoted instantiation is glued too, and its head is not a row --
        // so without this the sheet would print the whole effect cell as the
        // trait's NAME and then repeat it as the note underneath. That is
        // 6br's device-only defect, one resolver over (Codex).
        // `substringBefore` returns the whole string when there is no
        // separator, so the bare bracket instances pass through untouched and
        // need no guard of their own.
        ?: printed.takeIf { quotedInstanceRow(it) != null }?.substringBefore(SHEET_SEPARATOR)
        ?: printed

    /** How a published sheet separates a trait's name from its effect cell. */
    private const val SHEET_SEPARATOR = " - "

    /** Both catalogues by lowercase name: a trait may be either kind. */
    /**
     * Every trait row the book prints, keyed for resolution.
     *
     * **The blessings are in here, and that is deliberately the ONLY place
     * they are added.** `reported`, `catalogueName`, `displayName` and
     * `PurchasableTraits.canBuy` all resolve through this one map, so a
     * blessing became a trait for every consumer at once. Widening them one
     * consumer at a time is the two-matchers failure this file has produced
     * six times (6ag, 6ai, 6be, 6bp, 6bs, 6cd) -- a row that resolves for
     * MATCHING while failing to resolve for DISPLAY renders its whole effect
     * cell as the trait's own name.
     *
     * They are a third list rather than a third column in an existing file:
     * the price is stated once in prose for all seven and the table prints no
     * cost column, so writing them into `advantages.json` would make that file
     * claim the book printed one (6v).
     */
    private fun catalogue(): Map<String, TraitDefinition> {
        val cost = ContentLoader.blessingCost()
        return (
            ContentLoader.advantages() +
                ContentLoader.disadvantages() +
                ContentLoader.blessings().map { it.asTrait(cost) }
            ).associateBy { it.name.lowercase() }
    }

    /** Wrapping is the sheet's, so it cannot decide whether two cells agree. */
    private fun squashed(text: String): String =
        text.split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ").trim()

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
        if (category == SOCIAL_SKILLS) {
            for (clause in socialClauses(traits)) {
                if (clause.key !in declared) continue
                // Keyed by CLAUSE, not by trait: Social Anxiety prints two
                // circumstances that can both hold, and two modifiers sharing
                // a ModifierId is a collision the resolver should never be
                // handed -- the same reason the declaration key is trait
                // plus clause (6r).
                add(bonus(clause.trait, clause.value, suffix = clause.printed))
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
        return traits.flatMap { owned ->
            val bare = catalogueName(owned.name)
            val printed = catalogue[bare.lowercase()]?.notes ?: return@flatMap emptyList()
            val shown = displayName(owned.name)
            val whole = UNAPPLIED[bare]?.let { listOf(TraitNote(shown, printed, it)) }.orEmpty()
            // A trait can be applied and still have rules left over, so the
            // clauses are reported even though the trait itself is not.
            val clauses = PARTLY_APPLIED[bare].orEmpty().map { (clause, reason) ->
                TraitNote(shown, clause, reason)
            }
            whole + clauses
        }
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
            willClauseNames() +
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
            Languages.PRICING_TRAITS

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
        // The price half is applied by [Languages.owned]: two common
        // languages, fluent, free. The other half is the language row's own
        // refusal ([UNAPPLIED]).
        Languages.MULTILINGUAL to listOf(
            "No penalty to language-dependent social skills in those languages" to
                "this book tags no skill as language-dependent, so which rolls " +
                "that reaches is the GM's call",
        ),
        // Applied, and to one roll. The sheet says which, because a holder
        // with no Ritualistic Magic would otherwise read the trait as live.
        VERSATILE to listOf(
            "+1 to defaults skill rolls" to
                // Read after the sheet's own "Not applied here:" prefix.
                "to any roll but ${Divination.SKILL} defaulted from " +
                "${Divination.SIBLING_SKILL} 12+, which takes it — this app " +
                "offers no other roll at a default",
        ),
        FIT to listOf(
            "recover FP at twice the normal rate" to
                "this app holds no clock to recover against",
        ),
        // The moment Very Fit's HT clause was applied (6de), its other two
        // became clauses of a HANDLED trait -- which is what the guard test
        // says must be reported. Both are FP rates, and the rates are the
        // half of p. 42 this app deliberately does not run.
        VERY_FIT to listOf(
            "lose FP at half the normal rate" to
                "this app charges FP where the book prices it, and halving a " +
                "RATE needs a clock it does not hold",
            "recover FP at twice the normal rate" to
                "this app holds no clock to recover against",
        ),
        // Combat Reflexes was here, on "+6 to recover from stun" — refused
        // saying "this app models no stun recovery roll to make". The Major
        // Wound's Failure row prints one and `DamageDialog` had rendered its
        // sentence since 6n, so the reason was a claim about the app that had
        // stopped being true. All three of that cell's clauses are applied
        // now ([defenseBonus], [stunRecoveryBonus], and the surprise refusal
        // pinned in `ActiveDefensesTest`), which is why no entry is left.
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
        // The other five rows that name a skill, each with the sibling clause
        // its cell prints beside the one [SKILL_BONUSES] applies. A reviewer
        // caught two of these missing entirely -- worse than an untouched
        // trait, because the sheet then shows the cell as understood.
        // The two Fright Check rows that print a rule beside their modifier.
        // Phobia's second half is a declared ALL-ROLLS state, which this app
        // models for exactly one thing -- shock, through an enum tested per
        // roll class (6bo) -- and a second is its own slice. Fearlessness's
        // needs a contest between two characters' levels, and this app holds
        // one sheet (the refusal Feint has carried since 6o).
        "Phobia (specify)" to listOf(
            "-1 to all rolls while the phobic stimulus is present and unavoidable" to
                "this app models one declared all-rolls state (shock) and has no " +
                "control for a second yet",
        ),
        "Fearlessness" to listOf(
            "immunity to intimidation from beings with fewer levels of Fearlessness" to
                "that is a contest between two characters' levels, and this app " +
                "holds one sheet",
        ),
        "Flexibility" to listOf(
            "ignore up to -3 close-quarters penalties" to CLOSE_QUARTERS_UNPRINTED,
        ),
        "Double-Jointed" to listOf(
            "ignore up to -5 close-quarters penalties" to CLOSE_QUARTERS_UNPRINTED,
            "any body part bends any way" to
                "a capability rather than a roll, so the GM adjudicates it",
        ),
        "Healer" to listOf(
            "+2 to all rolls to diagnose, treat, and heal" to
                "this book tags no skill as a healing skill, so which rolls " +
                "count is the GM's call",
            "HT rolls to avoid or recover from disease at +2" to
                "this app models no disease roll",
        ),
        "Medical Training (Informal)" to listOf(
            "Use Physician at IQ-2 without purchasing the skill" to
                "the Skills tab has no row for a level a trait grants, so this " +
                    "one is not offered — a gap, not a ruling",
        ),
        "Absolute Direction" to listOf(
            "Always know which way is north; never become lost in natural terrain" to
                "the book hands both to the GM, not to a roll",
        ),
        "Sanctity" to listOf(
            "once per session, the GM must answer one factual question about " +
                "Church doctrine or history honestly" to
                "this app holds no session to count, and the answer is the GM's",
        ),
        // Charisma's cell prints its two halves in ONE sentence joined by
        // "and", which is why the clause guard cannot see the reaction half
        // on its own -- the whole cell is a single segment and the skill
        // names inside it satisfy the check. Reported explicitly for that
        // reason. Outdoorsman's cell has no second clause at all.
        CHARISMA to listOf(
            "+1/level to reaction rolls" to
                "this book prints no reaction roll to make, and at least five " +
                "sources hand one out, so each is shown beside its source " +
                "rather than summed",
        ),
        // The first DISADVANTAGE to reach this map, and its second clause is
        // word for word the refusal Fit's own mirror clause already carries.
        // The third sentence its cell prints, "Unfit for sustained physical
        // exertion.", names no roll at all and is visible on the sheet's
        // drawback row, which prints a disadvantage whole (6am).
        UNFIT to listOf(
            "recover FP at half normal rate" to
                "this app holds no clock to recover against",
        ),
        // The first SPI-scoped trait, and its sibling clause is about what
        // SOMEONE ELSE sees when they look at this character. This app holds
        // one sheet, which is the same reason the chapter's Quick Contests are
        // unmodelled (6ah, and Feint before it in 6o).
        ETHER_BODY_DAMAGE to listOf(
            "Spirit Vision on you reveals the damage immediately" to
                "that is what another character's Spirit Vision sees, and this " +
                "app holds one sheet",
        ),
        // Loner's Will roll is already made -- SelfControl reads it off the
        // cell's "roll vs. Will" -- so this is the clause beside an applied
        // one, which is what this map is for. It says "social ROLLS", not
        // "social SKILL rolls", and the book prints both phrases in one
        // bracket at line 6522, so the bare one is not an abbreviation of the
        // other and names no group this app can identify.
        "Loner" to listOf(
            "-1 to social rolls in crowds" to BARE_SOCIAL_ROLLS,
        ),
        // Workaholic's roll became reachable in 6bu -- the book prints its
        // target as "at Will-3", a phrasing the self-control anchor could not
        // see -- so this is now the clause beside an applied one, exactly as
        // Loner's is, and for the same reason.
        "Workaholic" to listOf(
            "-2 to social rolls in leisure settings" to BARE_SOCIAL_ROLLS,
        ),
    )

    /**
     * Why a cell saying "social rolls" reaches nothing, where one saying
     * "social skill rolls" reaches [SOCIAL_SKILLS].
     *
     * **The book distinguishes them itself.** Line 6522 prints "(-1 to all
     * emotional reaction rolls and social skill rolls)" -- two phrases, one
     * bracket -- so the bare form is not shorthand for the other. It is never
     * defined and never a banner, and the rows using it read like reactions:
     * "+2 to social rolls there; people look out for you", "-2 to
     * **first-impression** social rolls with strangers". This book prints no
     * reaction roll procedure or result table anywhere (6w), so the phrase may
     * name rolls this app cannot make or skills it can, and nothing printed
     * settles which.
     */
    private const val BARE_SOCIAL_ROLLS =
        "the book prints \"social rolls\" here and \"social skill rolls\" " +
            "elsewhere, and distinguishes the two in one sentence, so which " +
            "rolls it names is the GM's call"

    /**
     * Why both jointedness rows' second clause reaches nothing.
     *
     * **Checked, not assumed.** 6bo found that a near-identical excuse for
     * High Pain Threshold was false -- the book printed a shock penalty all
     * along, on a table this app already rolls -- so "close-quarters" was
     * grepped across the whole book before this sentence was written. It
     * occurs exactly twice, both inside these two trait cells. There is no
     * such penalty to ignore.
     */
    private const val CLOSE_QUARTERS_UNPRINTED =
        "this book prints no close-quarters penalty for it to ignore"

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
        "Danger Sense" to "the book hands this to the GM, not to a roll",
        // Bought through the builder's Languages row. The price is applied;
        // what the proficiency does to a roll is not, for the reason
        // [Languages]' own KDoc gives.
        Languages.ROW to
            "the -2 and -3 are \"to language-dependent skill rolls\", and this " +
            "book tags no skill as language-dependent, so which rolls they " +
            "touch is the GM's call; a ritual's language is picked in the " +
            "Ritual dialog",
        "Eidetic Memory" to "this app models no recall roll",
        "Single-Minded" to "which tasks count as extended concentration is the GM's call",
        "Lightning Calculator" to "this app models no numerical-puzzle roll",
        // Scoped to "the head", which only a hit-location roll could supply --
        // and those print no attribute (see KNOCKDOWN_UNREACHED). Its second
        // clause is a bite nothing here rolls. Glass Jaw is the disadvantage
        // with the same scope; the sheet prints drawbacks whole, so it needs
        // no entry to be visible.
        IRON_JAW to "this app rolls no knockdown for a hit location, and no bite",
        // The six Orthodox blessings that reach nothing. The seventh, the God
        // of Knowledge, is applied by CATEGORY_BONUSES.
        //
        // God of Combat is the only one refused for a reason that is about
        // THIS APP's shape rather than the book's silence, so it says so
        // exactly. M3's rule is that a granted attribute is never written into
        // attributes/secondaryDelta/skills, because PointBudget bills those --
        // only the CHOICE is stored and every gain re-derives from it. A
        // blessing is a stored choice, so the same route would work; what does
        // not exist is a trait ever taking it, since a potion is the only
        // thing in this codebase that grants an attribute (6al). Naming that
        // is the honest version: the number is real and the plumbing is
        // missing, which is not the same as the book printing nothing.
        GOD_OF_COMBAT to
            "a potion is the only thing here that grants an attribute — a " +
            "granted attribute is never written into a billable field, and no " +
            "trait re-derives one yet",
        "Goddess of Evernight" to
            "\"most divination\" prints no number, and being divined against " +
            "is the other party's roll — this app holds one sheet",
        "Lord of Storms" to
            "the +3 is scoped to pressure, and this app rolls no pressure " +
            "roll; the ten hours are a duration it holds no clock for",
        "Earth Mother" to
            "reviving a DEAD character is not a roll — the HP table's own two " +
            "rolls stop at \"-5xHP: Dead, no roll required\" — and burying to " +
            "regenerate is healing over time, which needs a clock",
        "Eternal Blazing Sun" to
            "the 1d6 falls on someone else each round, and this app holds " +
            "neither a second sheet nor a round",
        "God of Steam & Machinery" to
            "identification and negated curses print no number to apply",
        RAPID_RECOVERY to "both halves are durations, and this app holds no clock",
        // Two more mechanics the book has its OWN names for, found by a review
        // asking what a census keyed on the word "roll" still cannot see (6bu).
        // Neither is carried by a premade, and both would otherwise be dropped
        // in silence, because `reported` only emits what these maps name.
        "Natural Leader" to
            "this app models no morale roll, and the reroll it grants belongs " +
            "to an ally's Fright Check rather than to this character's",
        "Uncanny Survivor" to
            "the death roll now exists, but \"once per campaign arc\" is a " +
            "budget this app holds no campaign to spend from, and the book " +
            "hands the cost to the GM",
        "Glass Jaw" to
            "the book's knockdown rolls for a hit location print no attribute " +
            "to roll against, so this app shows those cells rather than rolling " +
            "them — the refusal Iron Jaw carries, with the sign flipped",
        "Soul Scar" to
            "it names a Fright Check FAILURE as its trigger rather than a roll " +
            "to make, so there is nothing here to roll or to modify",
        // The four rows that modify a Fright Check. The reason is a GAP, not a
        // refusal, and it is stated as one: this app DOES roll a Fright Check
        // (Ch. 5, `FrightCheck.against`), so "no such roll exists" would be a
        // fresh excuse of the kind 6bo caught one commit from shipping. What is
        // missing is the join between a trait and that roll's modifier. Named
        // as the next slice, and nothing is lost meanwhile: no premade carries
        // any of the four.
        // Two rows that became visible with [placeholderRow], because eight
        // sheet spellings fill their placeholder in. Both print a number that
        // reaches nothing, and both would otherwise go quiet on the character
        // who owns them -- Church Healer's Resistant, and three premades' Duty.
        "Resistant (specify)" to
            "the bonus is scoped to a category the GM names (Disease, Poison, " +
            "Temperature Extremes), and this app offers no roll to resist any " +
            "of them",
        "Duty (specify)" to
            "its \"roll 9 or less\" is how often the duty comes up rather than " +
            "a roll this character makes, the same refusal Phobia's Fright " +
            "Check line carries in SelfControl (6am)",
        // The five rows that modify a Fright Check were HERE, and the reason
        // they carried was a GAP stated as one -- "what is missing is the
        // join". The join shipped, so the disclaimer went with it: a report of
        // what is NOT applied expires exactly when the code changes (6ba, and
        // 6bl found one of these that had outlived its own truth by a slice).
        // Three of the five are wholly applied now; the other two keep the
        // sibling clause their cell prints, in [PARTLY_APPLIED].
        // The three conditional social rows were HERE, each saying "this app
        // has no control to declare it yet". [SOCIAL_CLAUSES] is that control,
        // so the disclaimers went with it rather than outliving their own
        // truth (6ba, and 6bl found one that had by a slice).
    )

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
