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
 * Whether the defences stand, and why not.
 *
 * [negated] is a cause that WAS declared and a trait cancelled, kept because
 * the screen has to say so: a GM who ticks "surprised" on a character with
 * Combat Reflexes and sees the defences simply not move would reasonably
 * think the tick failed.
 */
data class DefenseState(
    val refused: DefenseRefusal? = null,
    val printed: String? = null,
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

    /** maneuvers.json's own sentence, the one that names the forfeiting rows. */
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
            printed = surviving?.let { printedFor(it.name) },
            negated = cancelled.firstOrNull(),
            negatedBy = cancelled.firstOrNull()?.let { NEVER_FREEZES },
        )
    }

    /**
     * The maneuvers whose own row forfeits the next turn's defence.
     *
     * **Four, where p. 37's parenthetical names three.** That sentence
     * excepts "an All-Out Attack maneuver"; the maneuver table also prints
     * the phrase on Charge Attack. Specific beats general, so the table wins
     * -- and it is read from committed content rather than listed here,
     * because a second copy of a set the book prints is what 4.1 forbids.
     */
    fun forfeitingManeuvers(): List<String> =
        ContentLoader.maneuvers().filter { FORFEIT_PHRASE in it.description }.map { it.maneuver }

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
