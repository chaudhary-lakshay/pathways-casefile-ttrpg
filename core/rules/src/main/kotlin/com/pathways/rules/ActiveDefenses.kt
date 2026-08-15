package com.pathways.rules

import com.pathways.model.Outcome
import com.pathways.model.Trait
import com.pathways.rules.content.ContentLoader

/**
 * A printed reason a character gets NO active defence (Ch. 5).
 *
 * The book names no keys -- these are ours, one per extracted sentence, and
 * `ActiveDefensesTest` pins that the two sets match. A cause exists so the
 * refusal can be REPORTED with the right sentence and cancelled by the right
 * trait: a single boolean would have to answer "why" with a guess, and
 * Combat Reflexes cancels exactly one of the three.
 */
enum class DefenseRefusal {
    /** "Surprised characters skip their first turn entirely and cannot defend on it." */
    SURPRISE,

    /** The turn after an All-Out Attack or a Charge, per the maneuver table. */
    ALL_OUT_ATTACK,

    /** A shot the target never saw coming. Ranged only, by its own sentence. */
    UNAWARE_RANGED,
}

/**
 * A printed way to buy +2 on a defence (Ch. 5, p. 36-37).
 *
 * **Two rules, one bonus, and neither was applied.** The extra-effort table
 * prints "Pay 1 FP: +2 to one defense roll. Cannot defend next turn." and the
 * maneuver table prints "Pay 2 FP. +2 to one active defense until next turn.
 * Cannot attack." Identified by the SENTENCE rather than by the table it sits
 * in -- the lesson the forfeit taught one row over (6bh), where reading only
 * the maneuvers left the extra-effort option out of a list that looked
 * complete.
 *
 * They are NOT merged, because the book prices them differently in both
 * directions: the cheaper one costs a turn's defence afterwards, the dearer
 * one costs this turn's attack. Which was paid is the GM's to say.
 */
enum class DefenseBoost(
    /** The row's own name, exactly as its table prints it. */
    val printed: String,
    /** Where the row lives, which is what decides where its cost is read. */
    val extraEffort: Boolean,
) {
    /** "[Free Action] [Feverish Defense - Variant Rule] Pay 1 FP: ..." */
    FEVERISH("Feverish Defense — Variant Rule", extraEffort = true),

    /** "All-Out Defense (Increased) ... Pay 2 FP. +2 to one active defense" */
    ALL_OUT_INCREASED("All-Out Defense (Increased)", extraEffort = false),
}

/**
 * Whether the defences stand, and why not.
 *
 * [negated] is a cause that WAS declared and a trait cancelled, kept because
 * the screen has to say so: a GM who ticks "surprised" on a character with
 * Combat Reflexes and sees the defences simply not move would reasonably
 * think the tick failed.
 */
data class DefenseState(
    val refused: DefenseRefusal? = null,
    /** The sentence that governs the refusal in force. */
    val printed: String? = null,
    /**
     * A narrower printing of the same rule, where the book has one.
     *
     * Only the forfeit does: p. 37 excepts "an All-Out Attack maneuver" while
     * five rows forfeit. Shown beside [printed] rather than instead of it, so
     * the disagreement is recorded rather than smoothed (4.1).
     */
    val narrower: String? = null,
    val negated: DefenseRefusal? = null,
    val negatedBy: String? = null,
) {
    val defends: Boolean get() = refused == null
}

/**
 * Ch. 5's refusals of an active defence, and the one trait that cancels one.
 *
 * **All three are DECLARATIONS, and that is what makes them buildable.**
 * The handover ranked a round tracker as the way in, on the grounds that
 * these rules are round-scoped. They are not: whether a character was
 * surprised, took an All-Out Attack last turn, or never saw the shot is
 * something the GM knows and the app cannot compute -- the same call that
 * makes a Fright Check (6c) and a Major Wound's HT roll (6n) buttons rather
 * than automatic. What a round tracker would add is expiry, and expiry of a
 * fact the GM restates every time they resolve an attack is not worth global
 * state. See 6bf.
 *
 * **The withdrawal decision lives here, not in the dialog.** A screen that
 * works out its own answer is a second copy of one rule, which is how this
 * codebase's numbers stopped reconciling three times (6ag, 6ai, 6be).
 *
 * **Only the "cannot defend" half is enforced.** The surprise rule also says
 * the character "skips their first turn entirely", which is narration this
 * app has nothing to skip -- it holds no turn. Enforcing half a sentence and
 * saying which half is the honest version of a rule that is partly outside
 * the model.
 */
object ActiveDefenses {

    /** Ch. 3's Combat Reflexes: "never freeze in surprise". */
    const val NEVER_FREEZES = TraitEffects.COMBAT_REFLEXES

    /**
     * The book's own sentence for forfeiting a defence, printed verbatim in
     * TWO tables -- the maneuvers and the extra-effort options. The phrase is
     * the identity; the table it sits in is not.
     */
    const val FORFEIT_PHRASE = "Cannot defend next turn."

    /**
     * The state of the character's defences, given what the GM declared.
     *
     * When several causes are declared and one is cancelled, the surviving
     * one answers: a surprised character shot from concealment still cannot
     * defend, and naming the surprise would credit a trait that did not save
     * them. Order follows [DefenseRefusal], so the answer is deterministic
     * rather than set-iteration order.
     */
    fun state(declared: Set<DefenseRefusal>, traits: List<Trait>): DefenseState {
        val cancelled = DefenseRefusal.entries.filter { it in declared && cancels(it, traits) }
        val surviving = DefenseRefusal.entries.firstOrNull { it in declared && it !in cancelled }
        return DefenseState(
            refused = surviving,
            printed = surviving?.let { operativeFor(it) },
            narrower = surviving?.let { narrowerFor(it) },
            negated = cancelled.firstOrNull(),
            negatedBy = cancelled.firstOrNull()?.let { NEVER_FREEZES },
        )
    }

    /**
     * Everything whose own row forfeits the next turn's defence.
     *
     * **Five, where p. 37's parenthetical names three.** That sentence
     * excepts "an All-Out Attack maneuver"; the maneuver table also prints
     * the phrase on Charge Attack, and the extra-effort table prints it on
     * Feverish Defense. Specific beats general, so the tables win.
     *
     * **The SENTENCE is the identity, not the table it sits in.** This first
     * shipped reading only the maneuvers, which left the one option that
     * forfeits its defence out of the list the defence dialog shows -- and
     * invisibly, because a list of four All-Out rows plus a Charge looks
     * complete. Both tables are read for the same phrase, so a third table
     * printing it is included the day it does.
     *
     * Read from committed content rather than listed here, because a second
     * copy of a set the book prints is what 4.1 forbids.
     */
    fun forfeitingActions(): List<String> =
        ContentLoader.maneuvers().filter { FORFEIT_PHRASE in it.description }.map { it.maneuver } +
            ContentLoader.extraEffort().filter { FORFEIT_PHRASE in it.effect }.map { it.option }

    /**
     * The sentence that actually governs this refusal.
     *
     * For two causes that is their own extracted row. For [DefenseRefusal
     * .ALL_OUT_ATTACK] it is the TABLES' phrase, because p. 37's parenthetical
     * excepts only "an All-Out Attack maneuver" and five rows forfeit --
     * quoting it at a GM whose fighter spent 1 FP on a Feverish Defense cites
     * a rule that does not cover them (Codex). The narrower printing is still
     * shown, by [narrowerFor], with its scope said out loud: 4.1 records a
     * disagreement rather than blurring it.
     */
    private fun operativeFor(cause: DefenseRefusal): String =
        if (cause == DefenseRefusal.ALL_OUT_ATTACK) FORFEIT_PHRASE else printedFor(cause.name)

    /** The book's narrower printing of the same rule, where there is one. */
    private fun narrowerFor(cause: DefenseRefusal): String? =
        if (cause == DefenseRefusal.ALL_OUT_ATTACK) printedFor(cause.name) else null

    /**
     * What a [DefenseBoost] adds, and it is ONE defence rather than all of
     * them.
     *
     * Both sentences say "one" -- "one defense roll", "one active defense" --
     * where Combat Reflexes says "+1 to all active defenses" and a shield's
     * DB "adds to all active defenses". So this deliberately does NOT go
     * through the function every row already uses: adding it there would give
     * a fighter +2 on Dodge, every Parry and Block at once for 1 FP. The
     * screen applies it to the row the GM names.
     *
     * A number inside a prose cell, so it lives here with the sentence pinned
     * by a test -- the split the maneuver table's +4/-4 (6i), the +3 support
     * cap (6y) and the orthodox -2 (6z) all take.
     */
    const val BOOST_BONUS = 2

    /**
     * The FP [boost] costs, read from the table it is printed in.
     *
     * Never a constant here: 1 and 2 are already committed content, and a
     * second copy is what 4.1 forbids. Which table to read is the enum's own
     * [DefenseBoost.extraEffort], because the two rows live in different
     * files and are priced differently on purpose.
     */
    fun fpCostOf(boost: DefenseBoost): Int {
        if (boost.extraEffort) {
            val row = ContentLoader.extraEffort().firstOrNull { it.option == boost.printed }
            require(row != null) { "extra_effort.json has no option named '${boost.printed}'" }
            return row.fp
        }
        val row = ContentLoader.maneuvers().firstOrNull { it.maneuver == boost.printed }
        require(row != null) { "maneuvers.json has no row named '${boost.printed}'" }
        val fp = row.fp
        require(fp != null) { "'${boost.printed}' no longer prints an FP cost" }
        return fp
    }

    /** The book's own sentence for [boost], quoted rather than paraphrased. */
    fun printedBoost(boost: DefenseBoost): String =
        if (boost.extraEffort) {
            ContentLoader.extraEffort().first { it.option == boost.printed }.effect
        } else {
            ContentLoader.maneuvers().first { it.maneuver == boost.printed }.description
        }

    /**
     * Priced rows this app charges no FP for, each beside the reason it cannot.
     *
     * The standard is [TraitEffects.unappliedNames]': a printed rule reaching
     * nothing says so with its reason, so the omission stays a decision rather
     * than becoming an accident. The reason lives in the VALUE and is written
     * once; this KDoc carries only how the entry got here.
     *
     * **The one-defence limit is extracted, and half of it is unread.** The
     * sentence `defense_refusals.json` stores for [DefenseRefusal.ALL_OUT_ATTACK]
     * is "When you know you are being attacked, you may attempt one active
     * defense (unless you took an All-Out Attack maneuver)." Its PARENTHETICAL
     * is the refusal this app applies; its MAIN CLAUSE is the limit All-Out
     * Defense (Double) buys out of, and nothing reads it. Both of this repo's
     * sweeps pass the row -- a field audit (6ai) sees a field with a reader, a
     * row audit (6ci) sees a row a consumer names -- and no Kotlin constant
     * restates the "one", because the book prints it and 4.1 forbids the copy.
     *
     * **Two predecessors disagreed, and each was half right.** 6bl refused the
     * row: the dialog allocates no defence, so there is no limit for 2 FP to
     * raise. 6ci proposed building it from a census -- nine priced rows across
     * the two FP tables, seven charged -- having found "no code, no test, no
     * comment, no recorded reason", which was false: 6bl's reason was in the
     * slice log, where no grep over Kotlin reaches it. That is why the reason
     * is here now and not only there.
     *
     * Re-run as a query, 6bl's PROVENANCE half expired (the sentence it called
     * unmodelled is extracted, stored, and on screen) and its MECHANISM half
     * survived, which is what the value below records. A design review named
     * that split before any code was written.
     *
     * The row's third clause, "Cannot attack.", is unenforced here exactly as
     * its sibling All-Out Defense (Increased)'s already is.
     */
    val UNPAYABLE = mapOf(
        "All-Out Defense (Double)" to
            "this dialog allocates no defence — it reports what every row WOULD " +
            "be and never asks the GM to pick one, so the one-defence limit the " +
            "row buys out of is not imposed here; a cap invented in the same " +
            "commit that charges for lifting it would be a mechanic authored to " +
            "justify a price, not a rule applied",
    )

    /**
     * The skill the Acrobatic Dodge bullet rolls against (Ch. 5, p. 37).
     *
     * `skills.json` prints it as DX/Hard. Pinned against that list by
     * `ActiveDefensesTest`, which is the direction 6cg found missing: the
     * extractor pins the book into the JSON, and nothing pinned the JSON to the
     * constant Kotlin names it by.
     */
    const val ACROBATIC_SKILL = "Acrobatics"

    /**
     * What the roll is worth, in either direction: "success gives +2 Dodge;
     * failure gives -2 Dodge".
     *
     * ONE constant rather than a +2 and a -2, because the bullet prints one
     * magnitude twice and a pair could drift apart. A number inside a prose
     * cell, so it lives here with the sentence pinned -- the split
     * [BOOST_BONUS] and [STUNNED_PENALTY] already take.
     */
    const val ACROBATIC_SWING = 2

    /** The bullet's own tag, which is how its row is found rather than by index. */
    private const val ACROBATIC_ROW = "[Acrobatic Dodge]"

    /**
     * The bullet carrying [tag], quoted whole rather than restated (4.1).
     *
     * It THROWS on a content drop rather than degrading, which a review noted
     * is the one throw on this render path. Kept: [printedStun] already refuses
     * the same way on the same screen, `ActiveDefensesTest` turns a lost bullet
     * into a red build before any render reaches it, and a rule the app applies
     * must not be able to render as an empty note (`Detection.attributeFor`,
     * 6ah -- a refusal beats a plausible answer).
     *
     * One lookup for both applied bullets rather than the same four lines
     * written twice: the same predicate at two call sites is the defect, not
     * the drift it eventually produces.
     */
    private fun printedModifier(tag: String): String {
        val row = ContentLoader.defenseModifiers().firstOrNull { tag in it.modifier }
        require(row != null) { "defense_modifiers.json no longer prints $tag" }
        return row.modifier
    }

    /** The Acrobatic Dodge bullet, quoted whole. */
    fun printedAcrobaticDodge(): String = printedModifier(ACROBATIC_ROW)

    /**
     * What an Acrobatic Dodge did to the Dodge row, given how the roll came out.
     *
     * Null is "not rolled", which is the ordinary case and moves nothing: the
     * bullet is a free action the defender chooses to take, not a modifier that
     * applies itself. A critical counts as its plain sibling -- the book prints
     * two outcomes for this roll and only two, so grading it further would
     * invent a rule (4.1).
     *
     * **Scoped to Dodge alone, deliberately not through [everyDefence].** The
     * bullet names Dodge twice and no other row, where the stun's second
     * printing says "active defenseS". Same bar, different answer.
     */
    fun acrobaticDodge(outcome: Outcome?): Int = when (outcome) {
        null -> 0
        Outcome.SUCCESS, Outcome.CRITICAL_SUCCESS -> ACROBATIC_SWING
        Outcome.FAILURE, Outcome.CRITICAL_FAILURE -> -ACROBATIC_SWING
    }

    /** The bullet's own tag, which is how its row is found rather than by index. */
    private const val DIVE_ROW = "[Dive!]"

    /**
     * What throwing yourself flat is worth on the Dodge row (Ch. 5, p. 37).
     *
     * A number inside a prose cell, so it lives here with the sentence pinned
     * -- the split [BOOST_BONUS], [ACROBATIC_SWING] and [STUNNED_PENALTY]
     * already take.
     */
    const val DIVE_BONUS = 3

    /** The Dive! bullet, quoted whole. */
    fun printedDive(): String = printedModifier(DIVE_ROW)

    /**
     * What a Dive! adds, given the two facts the GM declared.
     *
     * **Both scopes are the bullet's own, and neither is inferred.** "+3 Dodge
     * vs. ranged only" names the row and names the attack, so this composes the
     * AND here rather than leaving the dialog to remember the second half --
     * the same division [everyDefence] and [acrobaticDodge] already take, where
     * the screen supplies declared facts and the rules module returns the
     * number. `Incoming` is the dialog's own private enum and cannot cross the
     * module boundary, so [isRanged] arrives reduced.
     *
     * **Deliberately not routed through [everyDefence]**, for the reason
     * [acrobaticDodge] is not: the bullet names Dodge and no other row, where
     * the stun's second printing says "active defenseS". Same bar, same answer.
     *
     * **The third clause is not enforced and this is not an oversight.** "Spend
     * 1 turn getting back up (Change Posture)" is a turn cost, and this app
     * holds no clock to spend one against (6bf) -- the split [printedStun]
     * already takes on the stun's own recovery. The bullet is quoted whole at
     * the control so the GM can see the half the screen did not do.
     *
     * **Being prone costs the defender nothing the book prints.** Checked
     * rather than assumed, the way 6ck's excuse should have been: the only
     * printed consequence of a prone target is on the ATTACKER's roll -- p. 39
     * prints "Target is prone: -2 ranged (but -4 in melee)" in the ranged
     * modifiers, which is a to-hit figure and belongs to the attack screen. No
     * page prices a prone character's own defences, so a Dive! that also
     * lowered the Dodge row would be a rule authored from a resemblance (4.1).
     */
    fun dive(taken: Boolean, isRanged: Boolean): Int =
        if (taken && isRanged) DIVE_BONUS else 0

    /**
     * The bullets this app applies, which is the half [unappliedModifiers] is
     * defined against.
     *
     * A list rather than a second `filterNot` clause: the two halves are one
     * decision, and writing the applied set once is what keeps a bullet from
     * landing in both (or in neither) as a third one is wired up.
     */
    private val APPLIED_ROWS = listOf(ACROBATIC_ROW, DIVE_ROW)

    /**
     * The Defense Modifiers bullets this app does NOT apply.
     *
     * The list was rendered whole while every bullet was display-only. Two of
     * the five now reach a number, and a screen that goes on printing one
     * beside the three it does not apply says the same sentence twice and marks
     * neither -- the disclaimer-outlives-its-rule defect 6bl found on this very
     * screen. Each applied bullet is quoted at the control that applies it.
     *
     * A partition rather than a filter written in the dialog: `ActiveDefenses`
     * decides what is applied, so the two halves cannot disagree, and a test
     * can read both (6bl again -- only a test that reads both objects notices).
     */
    fun unappliedModifiers(): List<String> =
        ContentLoader.defenseModifiers().map { it.modifier }
            .filterNot { bullet -> APPLIED_ROWS.any { it in bullet } }

    /**
     * What being stunned costs every active defence (Ch. 5, p. 37).
     *
     * **The app hands this state out and never charged for it.** Three of its
     * own tables produce it -- the fright effects ("Stunned 1 sec"), the Major
     * Wound's HT roll ("Stunned for 1d6 seconds") and two rows of the ritual
     * Critical Failure table ("Stunned 1d minutes") -- and the only place the
     * book prices it is the Do Nothing maneuver's cell.
     *
     * **It is attached to the STATE, not to the maneuver whose row prints it.**
     * Read the other way a stunned fighter would dodge at full value until
     * they chose Do Nothing, which is the reverse of what being stunned is
     * for. Ch. 11 settles it: an ability that leaves a target "mentally
     * Stunned for 1 turn (see GURPS stun rules, p. B420: -4 to active
     * defenses...)" names no maneuver at all. That second printing is
     * load-bearing twice, because it also says "active defenseS" -- which is
     * the whole basis for taking it to every row rather than to Dodge alone.
     * `extract_combat.verify()` pins it.
     *
     * A number inside a prose cell, so it lives here with the sentence pinned
     * -- the split the maneuver table's +4/-4 (6i) and the orthodox -2 (6z)
     * already take.
     */
    const val STUNNED_PENALTY = -4

    /** The maneuver row the stun penalty is printed on. */
    private const val STUN_ROW = "Do Nothing"

    /** That row, quoted whole rather than cut at render time (6f). */
    fun printedStun(): String =
        ContentLoader.maneuvers().first { it.maneuver == STUN_ROW }.description

    /**
     * Everything that moves EVERY active defence, composed once.
     *
     * Combat Reflexes is "+1 to all active defenses" and the stun penalty is
     * "-4 to Active Defense", so both land on every row -- deliberately unlike
     * a bought [DefenseBoost], which the book scopes to one. Composed here
     * rather than at the seven places the screen renders a defence: two of
     * them add the figure by hand, and a rule applied at six sites out of
     * seven is the drift this codebase has produced three times (6ag, 6ai,
     * 6be).
     *
     * Combat Reflexes deliberately does NOT cancel the stun: its own clause is
     * "+6 to recover from stun", which is the recovery roll this app does not
     * model (6bf), not the penalty while stunned.
     *
     * [Shock] is the third thing that moves every row, and it comes from the
     * OTHER half of the same table: a Major Wound's HT roll stuns on a failure
     * and shocks on a success. It composes here rather than anywhere else for
     * the same reason the stun does -- the screen renders a defence in seven
     * places, two of them by hand.
     *
     * **The facing penalty is deliberately NOT a fourth term here, and the bar
     * it fails is the one the stun had to clear.** `defense_modifiers.json`
     * prints "Attacked from side: -2 to defense" and "Attacked from rear: -4 to
     * defense". Both are bare and SINGULAR, and the stun's own note above says
     * why that is not enough on its own: taking a penalty to every row rather
     * than to Dodge alone rests on ch. 11's second printing saying "active
     * defenseS". The facing rule has no second printing anywhere in the book --
     * checked, not assumed -- and this book demonstrably distinguishes the two
     * scopes when it means them ("+1 to ALL active defenses" for Combat
     * Reflexes, "+2 to ONE active defense" for All-Out Defense). So the rows
     * stay on screen as the book prints them and reach no number, which is the
     * honest state until a printing settles the scope. `ActiveDefensesTest`
     * pins the singular, so this reason expires the day the wording changes.
     *
     * [Facing] is still modelled, for the rule that IS printed in the required
     * form: the shield's DB is conditional on the same arc, in a sentence that
     * says "all active defenses (Dodge, Parry, and Block)" and enumerates them.
     * See [Shields.coverageAt].
     */
    fun everyDefence(traits: List<Trait>, stunned: Boolean, shocked: Boolean = false): Int =
        TraitEffects.defenseBonus(traits) +
            (if (stunned) STUNNED_PENALTY else 0) +
            Shock.penaltyFor(RollKind.ACTIVE_DEFENSE, shocked, traits)

    /** The book's own sentence for a cause, refused when the content prints none. */
    fun printedFor(cause: String): String {
        val row = ContentLoader.defenseRefusals().firstOrNull { it.cause == cause }
        require(row != null) { "the book prints no defence refusal called '$cause'" }
        return row.printed
    }

    /**
     * Whether a trait cancels this cause.
     *
     * Combat Reflexes prints "never freeze in surprise" and the prose beside
     * it says "prevents freezing on surprise", so SURPRISE is the freeze it
     * names. It is deliberately NOT read as general defence insurance: the
     * unaware shot arrives before reaction rather than freezing anyone, and
     * an All-Out Attack is a choice the fighter made. Extending the clause to
     * those would author a rule from a resemblance -- the reasoning 6aj
     * refused when chapter 4 printed none of Appendix A's words.
     */
    private fun cancels(cause: DefenseRefusal, traits: List<Trait>): Boolean =
        cause == DefenseRefusal.SURPRISE && TraitEffects.levelOf(traits, NEVER_FREEZES) != null
}
