package com.pathways.rules

import com.pathways.model.ControllingAttribute
import com.pathways.model.Modifier
import com.pathways.model.SkillKey
import com.pathways.model.Trait
import com.pathways.model.TraitKind
import com.pathways.rules.TraitEffects.ACUTE_HEARING
import com.pathways.rules.TraitEffects.ACUTE_SMELL
import com.pathways.rules.TraitEffects.ACUTE_TASTE_SMELL
import com.pathways.rules.TraitEffects.ACUTE_TOUCH
import com.pathways.rules.TraitEffects.ACUTE_VISION
import com.pathways.rules.TraitEffects.CHRONIC_INSOMNIA
import com.pathways.rules.TraitEffects.GREATER_ACUTE_VISION
import com.pathways.rules.TraitEffects.INCISIVE_VISION
import com.pathways.rules.TraitEffects.RELUCTANT_KILLER
import com.pathways.rules.TraitEffects.SOCIAL_SKILLS
import com.pathways.rules.TraitEffects.THINNED_VEIL
import com.pathways.rules.TraitEffects.bonus
import com.pathways.rules.TraitEffects.categoryOf
import com.pathways.rules.TraitNames.catalogueName
import com.pathways.rules.TraitNames.displayName
import com.pathways.rules.TraitNames.levelOf
import com.pathways.rules.content.ContentLoader

/**
 * The clauses of held traits that a roll offers the GM to declare: the
 * Fright Check, bare Will, Concentrate, social, attack, named-skill and
 * sense rows. Moved out of [TraitEffects] unchanged, so that file stays
 * readable (split step 5).
 */
object TraitClauses {
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

    // ---- the five rows that modify a bare Will roll ------------------------

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
     * way. The three that remain (Incorruptible Will, Grief-Stricken, Evil
     * Eye Fear) carry a modifier and reached nothing.
     *
     * **Two more say "to Will" and no "roll" (6gr): Addiction: Tobacco and
     * Guilt.** The query above cannot see them. Reading them as a penalty on
     * the ROLL is a reading, not the book's word. Every conditional "-N to
     * Will" the book prints elsewhere is a roll penalty: Sin of Greed's "-5
     * to Will roll to resist", Sin of Lust's "-5 to Will when pursuing". The
     * other reading, the Will SCORE, would let a day without tobacco move the
     * permanent CoR ceiling. `Bound to a Place` prints "-1 to Will per
     * additional week, cumulative" and stays out: that is a count of weeks,
     * and one fixed -1 chip would misstate every absence past two.
     *
     * **None of the five qualifies the ROLL, so each reaches all of them.**
     * The bar is the one 6de applied to the HP table's two HT rolls: `Hard to
     * Kill`'s "HT rolls to avoid death" picks one of a PRINTED pair, and
     * `High Pain Threshold`'s "to avoid knockdown" names a printed roll this
     * app does not make. "To resist Beyonder abilities", "in situations that
     * echo a specific loss" and "to act clearly" name no printed roll at all
     * -- they name the circumstance -- and neither does "if unable to smoke"
     * or "in situations that echo the original act". So they are declarations, and the roll
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
        willClause(
            "Addiction: Tobacco",
            "Minor withdrawal irritability (-1 to Will) if unable to smoke for a full day",
            -1,
        ),
        willClause(
            "Guilt",
            "-1 to Will in situations that echo the original act",
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
     * No level scaling, unlike [frightClauses]: not one of the five cells
     * prints "per level", so multiplying would invent a scale where
     * Fearlessness's cell states one.
     */
    fun willClauses(traits: List<Trait>): List<TraitClause> = traits.flatMap { owned ->
        val bare = catalogueName(owned.name)
        WILL_CLAUSES
            .filter { it.trait.equals(bare, ignoreCase = true) }
            .map { it.copy(shown = displayName(owned.name)) }
    }

    /**
     * The clauses that reach the Concentrate maneuver's roll and no other
     * ([Concentration], "Roll Will-3 if injured or defending").
     *
     * Thinned Veil prints "Concentration in spiritually active environments
     * at -2" (6fa). That is a concentration check, which the Fright Check and
     * a self-control roll are not, so it stays out of [WILL_CLAUSES]. The
     * environment is the GM's to declare. **A reading, not the book's only
     * meaning** (the design review): a ritual's focus could claim the same
     * sentence, so [TraitRefusals.PARTLY_APPLIED] says every other roll is the GM's call.
     * The capital C is not a warrant, because the word begins its sentence.
     *
     * Chronic Insomnia prints "rolls requiring sustained concentration at -1"
     * (6fb). The book sets no duration that makes concentration sustained,
     * and never says every Concentrate roll is, so it is the GM's to declare
     * like the environment above. Single-Minded and Absent-Mindedness stay
     * out by their own words (6fa): Absent-Mindedness names SKILL rolls,
     * which this Will roll is not, and is declared on those ([ANY_SKILL_CLAUSES]).
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

    /** The traits whose cell reaches the Concentrate roll alone, for [TraitEffects.known]. */
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
     * All three rows name the GROUP identifiably -- [TraitEffects.SOCIAL_SKILLS], read from
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

    /**
     * Reluctant Killer's two to-hit clauses, book line 1313: *"-4 to hit
     * recognizable people with deadly force (-2 if face hidden)"*.
     *
     * **"To hit" names the attack roll, not a skill banner**, so these are
     * not emitted by [TraitEffects.modifiers]: COMBAT SKILLS also holds Fast-Draw and
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

    /** The traits whose cell penalises an attack, for [TraitEffects.known]. */
    fun attackClauseNames(): Set<String> = ATTACK_CLAUSES.map { it.trait }.toSet()

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

    /**
     * The sense rows no catalogue prints: a potion's alone (6fi), each as its
     * block prints it. Pinned against that prose, never against [TraitEffects.known],
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
    internal val GRANTED_ONLY_SENSES: List<TraitClause> = listOf(
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
    /**
     * "Perception rolls while sleeping at +4" (Light Sleeper, book 906; 6gu).
     * A Per roll under a circumstance the GM declares, so it is offered where
     * Alertness's "all Per rolls" lands on a roll made TO the character:
     * every Per skill, and Detection's Per branch. Spirit Vision is left out:
     * it is the holder's own act and costs SPI to open, so no sleeper makes
     * it. The roll the cell most plainly means, a bare Per roll to wake, is
     * one this app does not make.
     */
    private val PER_ROLL_CLAUSES: List<TraitClause> = listOf(
        TraitClause(
            "Light Sleeper", "Light Sleeper", "Perception rolls while sleeping at +4", 4, conditional = true,
        ),
    )

    /** The declared clauses on any Per roll, for a Per skill and for Detection. */
    fun perRollClauses(traits: List<Trait>): List<TraitClause> = traits.flatMap { owned ->
        val bare = catalogueName(owned.name)
        PER_ROLL_CLAUSES
            .filter { it.trait.equals(bare, ignoreCase = true) }
            .map { it.copy(shown = displayName(owned.name)) }
    }

    fun visionClause(traits: List<Trait>): TraitClause? =
        senseClauses(traits).firstOrNull { it.trait == ACUTE_VISION }

    /**
     * The cells that say "skill rolls" and name no skill: every skill roll is
     * the roll, and the circumstance is the GM's (6gj). Absent-Mindedness,
     * book 1242. Industrial Expertise's "+2 to all relevant skill rolls" is
     * not here: "relevant" is a purpose, the reason Healer's "+2 to all rolls
     * to diagnose, treat, and heal" is refused ([TraitRefusals.UNAPPLIED]).
     */
    private val ANY_SKILL_CLAUSES: List<TraitClause> = listOf(
        TraitClause(
            "Absent-Mindedness",
            "Absent-Mindedness",
            "-3 to skill rolls requiring concentration or organisation in everyday life",
            -3,
            conditional = true,
        ),
    )

    /** The traits whose cell names one skill, or one sense, and a circumstance, for [TraitEffects.known]. */
    fun skillClauseNames(): Set<String> = skillClauseRows().map { it.trait }.toSet()

    /** The printed rows, for the clause guard. */
    fun skillClauseRows(): List<TraitClause> =
        SKILL_CLAUSES.map { it.clause } + SENSE_CLAUSES + PER_ROLL_CLAUSES + ANY_SKILL_CLAUSES

    /**
     * Every clause a roll of [skill] asks the GM about: the social rows on a
     * social skill, the rows naming this skill, the sense rows on a Per
     * skill, and the rows naming every skill roll. The one list the dialog offers and [TraitEffects.modifiers] reads, so the two
     * cannot disagree (6ff). Distinct by catalogue row and clause, which is
     * what a modifier's id reads, so a row held twice or under two spellings
     * is one declaration and one modifier, never two under one id (6fh).
     */
    fun skillRollClauses(traits: List<Trait>, skill: SkillKey): List<TraitClause> {
        val sense =
            if (skill.attr == ControllingAttribute.Per) senseClauses(traits) + perRollClauses(traits) else emptyList()
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
        val any = traits.flatMap { owned ->
            val bare = catalogueName(owned.name)
            ANY_SKILL_CLAUSES
                .filter { it.trait.equals(bare, ignoreCase = true) }
                .map { it.copy(shown = displayName(owned.name)) }
        }
        return (social + named + sense + any).distinctBy { it.trait to it.printed }
    }

    // ---- the rows that put a state on every roll ---------------------------

    /**
     * Every cell that prints "-N to all rolls" under a circumstance (6gs).
     *
     * Each names a STATE the character is in -- withdrawal, a disrupted
     * routine, a phobic stimulus present, an entity nearby, old pain
     * resurfacing -- and no code here can see any of them, so the GM
     * declares it, once, on the sheet. It then reaches every roll shock
     * reaches, by the reading shock already takes: "all rolls" at its word.
     * Unlike shock it also reaches the Major Wound's HT roll, because shock's
     * one refusal is that the roll CREATES it, and withdrawal is not created
     * by a wound.
     *
     * Cells whose "to all rolls" continues with a purpose ("to diagnose,
     * treat, and heal", "to influence others") name no state and stay out;
     * the census test reads the catalogue to keep this list complete.
     */
    private val ALL_ROLLS_CLAUSES: List<TraitClause> = listOf(
        allRollsClause(
            "Phobia (specify)",
            "-1 to all rolls while the phobic stimulus is present and unavoidable",
            -1,
        ),
        allRollsClause("Addiction: Laudanum", "Must use daily or suffer -2 to all rolls from withdrawal", -2),
        allRollsClause("Superstitious (mundane)", "If warding routine is disrupted, -1 to all rolls for the day", -1),
        // Quoted from its trigger, or the GM would read the -1 without WHEN.
        allRollsClause(
            "Soul Scar",
            "Under extreme spiritual stress (Fright Check failure by 5+, witnessing Seq. 5+ " +
                "abilities), old pain resurfaces: -1 to all rolls for the scene",
            -1,
        ),
        allRollsClause("Entity Fixation", "-1 to all rolls when a specific entity type is present nearby", -1),
    )

    /** The printed rows, for the pin tests. [TraitClause.shown] is per character. */
    fun allRollsClauseRows(): List<TraitClause> = ALL_ROLLS_CLAUSES

    /** The traits whose cell puts a state on every roll, for [TraitEffects.known]. */
    fun allRollsClauseNames(): Set<String> = ALL_ROLLS_CLAUSES.map { it.trait }.toSet()

    /** The clauses [traits] carry, under the sheet's own spellings. */
    fun allRollsClauses(traits: List<Trait>): List<TraitClause> = traits.flatMap { owned ->
        val bare = catalogueName(owned.name)
        ALL_ROLLS_CLAUSES
            .filter { it.trait.equals(bare, ignoreCase = true) }
            .map { it.copy(shown = displayName(owned.name)) }
        // By key, which is spelling AND clause: two phobias are two stimuli
        // and two declarations, and only a row held twice under one spelling
        // collapses (the diff review).
    }.distinctBy { it.key }

    /**
     * The [declared] ones as ledger modifiers, for every roll [SkillRoll]
     * resolves. FromTrait, as every declared trait clause is, so the ledger
     * line names the trait as the sheet spells it ("Phobia (Cities)", never
     * the catalogue's "(specify)"); the id takes the declaration's key, so
     * two phobias never share one.
     */
    fun allRollsModifiers(clauses: List<TraitClause>, declared: Set<String>): List<Modifier> =
        clauses.filter { it.key in declared }.map { bonus(it.trait, it.value, suffix = it.key, shown = it.shown) }

    private fun allRollsClause(trait: String, printed: String, value: Int) =
        TraitClause(trait, trait, printed, value, conditional = true)


    /** A row of [SOCIAL_CLAUSES]: always conditional, never counted unasked. */
    private fun socialClause(trait: String, printed: String, value: Int) =
        TraitClause(trait, trait, printed, value, conditional = true)
}
