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
 * What a character's traits do to the Major Wound's HT roll: the number, and
 * the sentence naming every trait that produced it.
 *
 * [line] is null exactly when [bonus] is zero, so a screen cannot print a
 * credit for a roll nothing moved.
 */
data class MajorWoundHt(val bonus: Int, val line: String?)

/**
 * One clause of one trait that moves a Fright Check (Ch. 5, p. 43).
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
data class FrightClause(
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
data class FrightBonus(val total: Int, val line: String?)

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
    const val NIGHT_EYES = "Night Eyes"
    const val CHARISMA = "Charisma"
    const val OUTDOORSMAN = "Outdoorsman"
    const val EMPATHY = "Empathy"
    const val CLUELESS = "Clueless"

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
        UNFIT to -UNFIT_PENALTY,
    )

    /** The traits whose cell says "to all HT rolls", for the guard tests. */
    fun htRollNames(): Set<String> = HT_ROLL_TRAITS.keys

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
    )

    /** The traits whose cell names a category, for the guard tests. */
    fun categoryBonusNames(): Set<String> = CATEGORY_BONUSES.keys

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
    fun knockdownBonus(traits: List<Trait>): Int =
        if (levelOf(traits, HIGH_PAIN_THRESHOLD) != null) KNOCKDOWN_BONUS else 0

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
     * What [traits] add to any roll of HT itself.
     *
     * **"+1 to all HT rolls" means all of them, and [modifiers] can only
     * reach the ones a [SkillKey] carries.** This app makes exactly one bare
     * HT roll -- the Major Wound's -- so Fit missed it for six premades from
     * 6n until now. The same structural gap 6al had to thread explicitly for
     * Alertness, whose "all Per rolls" could not reach the detection roll.
     *
     * Deliberately NOT a general attribute-roll bonus: the app's other bare
     * rolls are Will, IQ, Per and SPI (fright, self-control, concentration,
     * detection), and no trait here names those.
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
        return placeholderRow(printed) ?: gluedHead(printed) ?: printed
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
        val open = printed.lastIndexOf(" (")
        if (open <= 0) return null
        val inside = printed.substring(open + 2, printed.length - 1)
        // An empty bracket names nothing, and the row is not an instance of
        // itself.
        if (inside.isBlank() || " ($inside)" == PLACEHOLDER) return null
        val head = printed.substring(0, open)
        return catalogue()["${head.lowercase()}$PLACEHOLDER"]?.name
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
    fun displayName(printed: String): String = gluedHead(printed) ?: printed

    /** How a published sheet separates a trait's name from its effect cell. */
    private const val SHEET_SEPARATOR = " - "

    /** Both catalogues by lowercase name: a trait may be either kind. */
    private fun catalogue(): Map<String, TraitDefinition> =
        (ContentLoader.advantages() + ContentLoader.disadvantages())
            .associateBy { it.name.lowercase() }

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
    fun modifiers(traits: List<Trait>, skill: SkillKey): List<Modifier> = buildList {
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
    private val FRIGHT_CLAUSES: List<FrightClause> = listOf(
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
    fun frightClauseRows(): List<FrightClause> = FRIGHT_CLAUSES

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
    fun frightClauses(traits: List<Trait>): List<FrightClause> = traits.flatMap { owned ->
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
    fun frightBonus(clauses: List<FrightClause>, declared: Set<String>): FrightBonus {
        val applied = clauses.filter { !it.conditional || it.key in declared }
        if (applied.isEmpty()) return FrightBonus(0, null)
        val parts = applied.joinToString(", ") {
            "${it.shown} ${if (it.value > 0) "+" else ""}${it.value}"
        }
        return FrightBonus(applied.sumOf { it.value }, parts)
    }

    /** A row of [FRIGHT_CLAUSES]: [shown] is filled in per character. */
    private fun frightClause(
        trait: String,
        printed: String,
        value: Int,
        conditional: Boolean = true,
    ) = FrightClause(trait, trait, printed, value, conditional)

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
            CATEGORY_BONUSES.keys + frightClauseNames() +
            setOf(ALERTNESS, FIT, COMBAT_REFLEXES)

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
                "this app never shows an untrained level, so no default is rolled",
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
        // Two more mechanics the book has its OWN names for, found by a review
        // asking what a census keyed on the word "roll" still cannot see (6bu).
        // Neither is carried by a premade, and both would otherwise be dropped
        // in silence, because `reported` only emits what these maps name.
        "Natural Leader" to
            "this app models no morale roll, and the reroll it grants belongs " +
            "to an ally's Fright Check rather than to this character's",
        "Uncanny Survivor" to
            "this app models no death roll to succeed at — the same refusal " +
            "Hard to Kill already carries",
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
            "Temperature Extremes), and the one bare HT roll this app makes — " +
            "a Major Wound's — resists none of them",
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
        // The three social-skill rows whose penalty is CONDITIONAL. The group
        // they name is identifiable now [SOCIAL_SKILLS], so what stops them is
        // the circumstance and nothing else -- and two of the three are
        // carried beside a social skill (Barmaid Brawler and Docks Bouncer own
        // Callous with Intimidation, Academic Researcher owns Shyness with
        // Public Speaking), so applying them flat would charge a penalty the
        // GM never declared. A declaration control is the next slice.
        "Callous" to CONDITIONAL_SOCIAL,
        "Shyness" to
            "the penalty depends on severity and audience, and this app has no " +
            "control for either yet",
        "Social Anxiety" to
            "both values depend on the size and status of the group, and this " +
            "app has no control for either yet",
    )

    /** Why a social penalty the GM must first declare reaches no roll. */
    private const val CONDITIONAL_SOCIAL =
        "the book charges this only when the circumstance it names applies, " +
            "and this app has no control to declare it yet"

    private fun bonus(trait: String, value: Int) = Modifier(
        id = ModifierId("trait:${trait.lowercase()}"),
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
