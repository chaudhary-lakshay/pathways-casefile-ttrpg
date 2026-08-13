package com.pathways.rules

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
