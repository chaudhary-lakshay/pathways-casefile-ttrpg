package com.pathways.rules

import com.pathways.rules.TraitEffects.ACUTE_HEARING
import com.pathways.rules.TraitEffects.ACUTE_VISION
import com.pathways.rules.TraitEffects.BECKONING_LUCK
import com.pathways.rules.TraitEffects.CHARISMA
import com.pathways.rules.TraitEffects.CHRONIC_INSOMNIA
import com.pathways.rules.TraitEffects.CLEANSED_SPIRIT
import com.pathways.rules.TraitEffects.ETHER_BODY_DAMAGE
import com.pathways.rules.TraitEffects.FAST_HEALER
import com.pathways.rules.TraitEffects.FATE_DEBT
import com.pathways.rules.TraitEffects.GOD_OF_COMBAT
import com.pathways.rules.TraitEffects.GREAT_BALANCE
import com.pathways.rules.TraitEffects.IRON_JAW
import com.pathways.rules.TraitEffects.LUCK
import com.pathways.rules.TraitEffects.LUCK_REACH
import com.pathways.rules.TraitEffects.PERFECT_BALANCE
import com.pathways.rules.TraitEffects.RAPID_RECOVERY
import com.pathways.rules.TraitEffects.RELUCTANT_KILLER
import com.pathways.rules.TraitEffects.THINNED_VEIL
import com.pathways.rules.TraitEffects.UNFIT
import com.pathways.rules.TraitEffects.VERY_FIT

/**
 * What [TraitReport.reported] prints as NOT applied, and why: whole traits
 * ([UNAPPLIED]) and single clauses of applied ones ([PARTLY_APPLIED]). Moved
 * out of [TraitEffects] unchanged, so that file stays readable.
 */
internal object TraitRefusals {
    /**
     * Clauses of an APPLIED trait that reach nothing here, and why.
     *
     * **A notes cell can hold several rules.** Combat Reflexes prints three
     * and exactly one of them names a roll this app makes; Fit prints two and
     * one. [TraitReport.reported] keyed on the trait NAME, and both traits are applied,
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
    val PARTLY_APPLIED: Map<String, List<Pair<String, String>>> = mapOf(
        // Its +4 is declared on a Per roll ([TraitClauses.perRollClauses], 6gu).
        "Light Sleeper" to listOf(
            "never caught completely unaware at night" to
                "surprise is the GM's to declare on the Defend dialog, and " +
                "nothing here decides it",
        ),
        // The price half is applied by [Languages.owned]: two common
        // languages, fluent, free. The other half is the language row's own
        // refusal ([UNAPPLIED]).
        Languages.MULTILINGUAL to listOf(
            "No penalty to language-dependent social skills in those languages" to
                "this book tags no skill as language-dependent, so which rolls " +
                "that reaches is the GM's call",
        ),
        // Declared on the Concentrate roll ([TraitClauses.CONCENTRATION_CLAUSES]); this is
        // the reach the design review said the note must disclaim (6fa).
        THINNED_VEIL to listOf(
            "Concentration in spiritually active environments at -2" to
                "beyond the Concentrate roll, where it is declared; which other " +
                "rolls need concentration, a ritual's included, is the GM's call",
        ),
        // The -1 as Thinned Veil's -2 (6fb); the FP has no morning to fall on.
        CHRONIC_INSOMNIA to listOf(
            "Lose 1 FP each morning that cannot be recovered through rest" to
                "this app counts no mornings, so the table lowers FP on the FP " +
                "row, and keeps it from rest",
            "rolls requiring sustained concentration at -1" to
                "beyond the Concentrate roll, where it is declared; which other " +
                "rolls need sustained concentration, a ritual's included, is the GM's call",
        ),
        // Offered on a failed PCR, the one failed roll the session makes
        // permanent ([TraitEffects.rerollSource], 6fg). A ritual's or boon's critical
        // failure charges CoR before any reroll, and a table reroll does not
        // take it back (Codex, on the design).
        LUCK to listOf(
            "1 reroll per session" to "this app keeps no session, so the GM counts them",
            "may reroll any one failed roll" to LUCK_REACH,
        ),
        BECKONING_LUCK to listOf(
            "2 rerolls per session" to "this app keeps no session, so the GM counts them",
            "reroll any failed roll, usable at any time" to LUCK_REACH,
            "and fate may balance later" to "the book hands this to the GM, not to a roll",
        ),
        FATE_DEBT to listOf(
            "You owe fate something" to "the book hands this to the GM, not to a roll",
            "The GM holds a narrative debt to be called in at a dramatically significant moment" to
                "the book hands this to the GM, not to a roll",
        ),
        // The rest of the five rows [TraitClauses.SKILL_CLAUSES] reads and of Famous Face, whose -2 is a [TraitEffects.SKILL_BONUSES] row (6ff).
        "Class Mobility" to listOf(
            "Move believably across two social classes" to "the book hands this to the GM, not to a roll",
            "NPCs rarely question your presence" to "the book hands this to the GM, not to a roll",
        ),
        "Fearsome Reputation" to listOf(
            "Known in criminal circles" to "the book hands this to the GM, not to a roll",
            "Relevant NPCs start cautious" to "the book hands this to the GM, not to a roll",
        ),
        "Alcohol Tolerance" to listOf(
            "Never suffer social penalties from moderate drinking" to
                "this app applies no drinking penalty, so there is none to lift",
        ),
        "Navigator's Eye" to listOf(
            "Never become lost in any city previously visited" to
                "the book hands this to the GM, not to a roll",
        ),
        // The gloss after each "+2" (6fh). Its first task is a bare Vision
        // roll with no skill, and this app makes no such roll.
        ACUTE_VISION to listOf(
            "notice details at a distance, read lips, spot hidden objects" to
                "says what a Vision roll is for; on a Per roll the GM declares whether this is one",
        ),
        ACUTE_HEARING to listOf(
            "detect faint sounds, eavesdrop through walls, identify speech in noise" to
                "says what a Hearing roll is for; on a Per roll the GM declares whether this is one",
        ),
        "Famous Face" to listOf(
            "Recognizable in the city" to "the book hands this to the GM, not to a roll",
            "surveillance harder" to "the book prints no number for it, so it is the GM's",
            "strangers approach you, sometimes dangerously" to "the book hands this to the GM, not to a roll",
        ),
        // Its first clause is said on the recovery row ([NaturalRecovery.dayLine], 6fc).
        FAST_HEALER to listOf(
            "Injuries that would leave others bedridden leave you functional in half the time" to
                "this app tracks no wounds and holds no clock to halve",
        ),
        // The to-hit clauses are declared in the Attack dialog and the Aim
        // rows withdrawn; the Will roll is the row's own self-control check.
        RELUCTANT_KILLER to listOf(
            "become morose for 3d days" to
                "this app holds no clock to count the days against",
        ),
        // Very Fit's HT clause is applied (6de) and its burn clause read by
        // [EffortBurn.whenAllowed]; v6.9i dropped "lose FP at half the
        // normal rate" and Fit's recovery clause, leaving this one rate.
        VERY_FIT to listOf(
            "recover FP at twice the normal rate" to
                "this app holds no clock to recover against",
        ),
        // Combat Reflexes was here, on "+6 to recover from stun" — refused
        // saying "this app models no stun recovery roll to make". The Major
        // Wound's Failure row prints one and `DamageDialog` had rendered its
        // sentence since 6n, so the reason was a claim about the app that had
        // stopped being true. All three of that cell's clauses are applied
        // now ([TraitEffects.defenseBonus], [TraitAttributeRolls.stunRecoveryBonus], and the surprise refusal
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
        // its cell prints beside the one [TraitEffects.SKILL_BONUSES] applies. A reviewer
        // caught two of these missing entirely -- worse than an untouched
        // trait, because the sheet then shows the cell as understood.
        // Phobia's second half was here, refused because "this app models one
        // declared all-rolls state (shock) and has no control for a second
        // yet". The control shipped (6gs, [TraitClauses.allRollsClauses]).
        // Fearlessness's needs a contest between two characters' levels, and
        // this app holds one sheet (the refusal Feint has carried since 6o).
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
        // Its -3 is declared on every skill roll and its IQ roll is offered
        // (SelfControl); this sentence hands itself to the GM (6gj).
        "Absent-Mindedness" to listOf(
            "In combat, may forget to reload, change tactics, or use special " +
                "abilities (GM's discretion)" to
                "the cell leaves it to the GM's discretion in its own words",
        ),
        // Its "-1 to all rolls when ... present nearby" is declared on the
        // sheet (6gs); this clause names what a roll is for.
        "Entity Fixation" to listOf(
            "-2 to any roll that requires you to ignore them" to PURPOSE_NOT_SKILL,
        ),
        "Healer" to listOf(
            "+2 to all rolls to diagnose, treat, and heal" to PURPOSE_NOT_SKILL,
            "HT rolls to avoid or recover from disease at +2" to
                "this app models no disease roll",
        ),
        "Absolute Direction" to listOf(
            "Always know which way is north; never become lost in natural terrain" to
                "the book hands both to the GM, not to a roll",
        ),
        "Former Military Officer" to listOf(
            "military contacts" to
                "this app keeps no contacts, and who they are is the GM's",
            "entitled to officer courtesies in formal settings" to
                "the book hands this to the GM, not to a roll",
        ),
        "Photographic Instinct" to listOf(
            "instinctively know what to capture as evidence" to
                "the book hands this to the GM, not to a roll",
        ),
        "Sanctity" to listOf(
            "once per session, the GM must answer one factual question about " +
                "Church doctrine or history honestly" to
                "this app holds no session to count, and the answer is the GM's",
        ),
        // v6.9i's Charisma cell dropped "+1/level to reaction rolls"; its
        // second half is now the trust clause, which names no roll.
        CHARISMA to listOf(
            "NPCs are predisposed to trust and listen to you" to
                "the book hands this to the GM, not to a roll",
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
     * "social skill rolls" reaches [TraitEffects.SOCIAL_SKILLS].
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
     * Why a cell that names what a roll is FOR reaches no roll: Healer's
     * "to diagnose, treat, and heal", Voice's "to influence others through
     * speech", Industrial Expertise's "relevant" (6gj). Healer's said
     * "this book tags no skill as a healing skill", true of Healer alone.
     * The book does list "Influence skills", twice and differently (563-564,
     * 7645), and Voice's cell names neither the group nor a list. Healer's
     * verbs resemble Diagnosis, Physician and First Aid; resemblance is not
     * a name, and its cell names First Aid outright in its next clause.
     */
    private const val PURPOSE_NOT_SKILL =
        "this says what a roll is for and names no skill, nor a group the " +
            "book lists, so which rolls count is the GM's call"

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
     * finding). See [TraitAttributeRolls.knockdownBonus] for why these rows do not join the one
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
    val UNAPPLIED = mapOf(
        "Danger Sense" to "the book hands this to the GM, not to a roll",
        // Each names a purpose and no skill or group, Healer's case (6gj).
        // Voice's phrase is neither of the book's two "Influence skills"
        // lists (563-564, 7645), which differ; "relevant" names no industry.
        "Voice" to PURPOSE_NOT_SKILL,
        "Industrial Expertise" to PURPOSE_NOT_SKILL,
        // Not the mirror of Incorruptible Will's +3 (6gw). Every "against
        // you" the book prints with a number is the other party's side (3855,
        // 8252, 1460), and a divination against the holder is refused the same
        // way under "Goddess of Evernight". The second half is the one a reason about the other
        // party alone would miss: most attempts on a mind have the TARGET
        // roll (8529), and "pathway side effects" are a potion's drawbacks
        // (5448), one of which this app rolls self-control for.
        "Anchored Soul" to
            "the -2 is on the attempt, the other party's roll, and this app " +
            "holds one sheet; where the holder is the one who rolls (\"The " +
            "target rolls Will-6 to resist\", a potion drawback's self-control " +
            "roll) the book prints no rule turning it into a bonus on that " +
            "roll, so that is the GM's call",
        // Said beside the CoR stepper too ([TraitReport.corruptionNotes]). "Passive
        // exposure" occurs once in the book, and Soul Debt -- a hit taken on
        // SPI among its routes -- is charged automatically, so no charge is called
        // deliberate either (the design review's MEDIUM, 6fd).
        CLEANSED_SPIRIT to
            "the book lists no CoR source as passive exposure, so this app " +
            "halves nothing it charges, Soul Debt from SPI spent below 0 " +
            "included; the GM halves what is passive with the CoR stepper",
        // The only cell that lists "<Skill> +N". Chapter 3 writes a roll
        // bonus as "+N to X" and chapter 6 defines a bare +N for a potion's
        // entry, so this is neither idiom's own table. Refused whole on the
        // design review's HIGH: that both readings give +N to a skill already
        // owned is an argument about the cell, not a sentence in it (6es).
        "Official Beyonder" to
            "the book does not say whether its four \"+N\" entries are " +
            "bonuses to a roll or skills granted at that level, so none is " +
            "counted; the language, the Legal Enforcement Powers and the " +
            "Duty are not added to this sheet either",
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
        // The five Orthodox blessings that reach nothing. The God of Knowledge
        // is applied by CATEGORY_BONUSES, and the God of Combat's +3 ST/DX/HT
        // by [Blessings.played], as a played gain the budget never bills (6hf).
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
            "one sheet",
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
        // Soul Scar was here: "it names a Fright Check FAILURE as its trigger
        // rather than a roll to make, so there is nothing here to roll or to
        // modify". The trigger is no roll, but the "-1 to all rolls for the
        // scene" it starts lands on every roll after it, and is declared on
        // the sheet now (6gs).
        // The four rows that modify a Fright Check. The reason is a GAP, not a
        // refusal, and it is stated as one: this app DOES roll a Fright Check
        // (Ch. 5, `FrightCheck.against`), so "no such roll exists" would be a
        // fresh excuse of the kind 6bo caught one commit from shipping. What is
        // missing is the join between a trait and that roll's modifier. Named
        // as the next slice, and nothing is lost meanwhile: no premade carries
        // any of the four.
        // Two rows that became visible with [TraitEffects.placeholderRow], because eight
        // sheet spellings fill their placeholder in. Both print a number that
        // reaches nothing, and both would otherwise go quiet on the character
        // who owns them -- Church Healer's Resistant, and three premades' Duty.
        "Resistant (specify)" to
            "the bonus is scoped to a category the GM names (Disease, Poison, " +
            "Temperature Extremes), and this app offers no roll to resist any " +
            "of them",
        // The same refusal Phobia's Fright Check line carries in SelfControl
        // (6am); said to the player without the cross-reference (6gl).
        "Duty (specify)" to
            "its \"roll 9 or less\" is how often the duty comes up, the GM's " +
            "to roll, rather than a roll this character makes",
        // The five rows that modify a Fright Check were HERE, and the reason
        // they carried was a GAP stated as one -- "what is missing is the
        // join". The join shipped, so the disclaimer went with it: a report of
        // what is NOT applied expires exactly when the code changes (6ba, and
        // 6bl found one of these that had outlived its own truth by a slice).
        // Three of the five are wholly applied now; the other two keep the
        // sibling clause their cell prints, in [PARTLY_APPLIED].
        // The three conditional social rows were HERE, each saying "this app
        // has no control to declare it yet". [TraitClauses.SOCIAL_CLAUSES] is that control,
        // so the disclaimers went with it rather than outliving their own
        // truth (6ba, and 6bl found one that had by a slice).
    )
}
