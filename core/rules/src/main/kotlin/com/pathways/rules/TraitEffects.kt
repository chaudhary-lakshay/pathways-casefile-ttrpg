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
import com.pathways.rules.TraitAttributeRolls.HT_ROLL_TRAITS
import com.pathways.rules.TraitAttributeRolls.SPI_ROLL_TRAITS
import com.pathways.rules.TraitClauses.allRollsClauseNames
import com.pathways.rules.TraitClauses.attackClauseNames
import com.pathways.rules.TraitClauses.concentrationClauseNames
import com.pathways.rules.TraitClauses.frightClauseNames
import com.pathways.rules.TraitClauses.skillClauseNames
import com.pathways.rules.TraitClauses.skillRollClauses
import com.pathways.rules.TraitClauses.socialClauseNames
import com.pathways.rules.TraitClauses.willClauseNames
import com.pathways.rules.TraitNames.levelOf
import com.pathways.rules.TraitRefusals.PARTLY_APPLIED
import com.pathways.rules.TraitRefusals.UNAPPLIED

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
 * Everything else is [TraitReport.reported] -- shown to the GM with the reason -- which
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

    /** "Prior spirit body damage ... All SPI-based rolls at -1." */
    const val ETHER_BODY_DAMAGE = "Ether Body Damage"

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
     * reaches nothing and is [TraitReport.reported]: it names a PURPOSE, and this book
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
     * Anxiety "in groups of 6+" -- which are circumstances a GM declares.
     * Applying them at their printed value would charge a penalty nobody
     * declared, on three premades that own a social skill; they are chips on
     * the skill roll instead ([TraitClauses.skillRollClauses]). This said
     * "this app has no control for" them and filed them as unapplied.
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
    internal fun categoryOf(skill: SkillKey): String? =
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
    internal enum class BespokeBonus(val trait: String, val fixed: Int?) {
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
     * What [traits] add to a roll of [skill].
     *
     * Scoped by the skill's controlling attribute, the same test [Conditions]
     * uses to keep Spiritual Attrition on SPI rolls (6h). A trait whose
     * printed scope this app cannot identify contributes nothing here and is
     * named by [TraitReport.reported] instead.
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

    const val THINNED_VEIL = "Thinned Veil"
    const val CHRONIC_INSOMNIA = "Chronic Insomnia"

    const val RELUCTANT_KILLER = "Reluctant Killer"


    const val ACUTE_VISION = "Acute Vision"
    const val ACUTE_HEARING = "Acute Hearing"

    const val GREATER_ACUTE_VISION = "Greater Acute Vision"
    internal const val ACUTE_TASTE_SMELL = "Acute Taste & Smell"
    internal const val ACUTE_TOUCH = "Acute Touch"
    internal const val ACUTE_SMELL = "Acute Smell"
    const val INCISIVE_VISION = "Incisive Vision"

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
            attackClauseNames() +
            willClauseNames() + concentrationClauseNames() + skillClauseNames() +
            allRollsClauseNames() + TraitClauses.physicalRollsClauseNames() +
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
            setOf(ACTING_METHOD) +
            // The eighth: [SkillDefaults.offered] reads it for Physician at
            // IQ-2 (6gy). Its First Aid +1 already had it in [SKILL_BONUSES],
            // the second-route cover this KDoc records against Outdoorsman.
            setOf(SkillDefaults.MEDICAL_TRAINING)

    /** The traits reported whole, for the disjointness test. */
    fun unappliedNames(): Set<String> = UNAPPLIED.keys

    /** The traits reported clause by clause, for the disjointness test. */
    fun partlyAppliedNames(): Set<String> = PARTLY_APPLIED.keys

    internal fun bonus(
        trait: String,
        value: Int,
        suffix: String? = null,
        /** The sheet's spelling, for the ledger line; [trait] stays the catalogue row. */
        shown: String = trait,
    ) = Modifier(
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
        source = ModifierSource.FromTrait(shown, advantage = isAdvantage(trait)),
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
