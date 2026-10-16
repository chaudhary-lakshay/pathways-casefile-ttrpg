package com.pathways.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.pathways.rules.DefenseBoost
import com.pathways.rules.DefenseRefusal
import com.pathways.rules.SkillRollOutcome

/**
 * The values a round boundary clears, held together because the boundary has
 * to move all of them at once.
 *
 * p. 36 scopes two rules to a round over this screen: "Each type may be used
 * multiple times per round, but Parry accumulates a -4 cumulative penalty after
 * the first" (line 2454), and "Free Actions: Retreat, Acrobatic Dodge, and
 * Dive! ... Each can be used once per round unless stated otherwise" (2456-
 * 2457). The paid boost is priced "until next turn" by its own maneuver row,
 * and the three refusals each print a scope of one turn or less -- surprise is
 * the "first turn", the All-Out Attack is the turn it was taken, and an unaware
 * shot is one attack.
 *
 * **6bf and 6co both refused this boundary, and the reason was about REACH.**
 * Latching one control while `priorParries` and a paid boost stayed put would
 * claim a round the screen could not keep for the rest. Reach is what changed:
 * every value the paragraph governs is local to one composable, so a single
 * assignment honours it for all of them -- with no global combat state, which
 * is what 6bf actually refused.
 *
 * **Membership is the rule: a field here is a value a new round CLEARS.**
 * `stunned` is deliberately not one -- the book gives it its own clock,
 * "Stunned for 1d6 seconds (roll HT each turn to recover early)" (2826) -- and
 * the shield carried, the facing and the incoming attack are not per-round
 * facts at all, which is a second reason rather than the same one.
 */
internal data class RoundState(
    /**
     * What the GM says is true of this character right now: none of the three
     * is computable, because the app holds no turn, no initiative and no line
     * of sight. See ActiveDefenses.
     */
    val declared: Set<DefenseRefusal> = emptySet(),
    /**
     * The Acrobatic Dodge as it came out, or null for "not taken". The whole
     * outcome rather than the ±2 it produces, because the note under the button
     * reports the target the trackers left and the total rolled against it --
     * a number on screen with no way to reconcile it is what 6be found.
     */
    val acrobatic: SkillRollOutcome? = null,
    /** Whether the character threw themselves flat: the second free action. */
    val dived: Boolean = false,
    /** Whether the character gave ground: the third. */
    val retreated: Boolean = false,
    /**
     * Parries already made THIS ROUND. The book's cumulative penalty was
     * committed in defenses.json's own notes cell from 6g onward, shown to the
     * GM, and applied by nothing -- the class 6h, 6m and 6ai each found. Zero
     * is the honest default: a round starts with the free parry.
     */
    val priorParries: Int = 0,
    /**
     * Which boost was paid for, and WHICH ROW it was spent on. Both printed
     * sentences say "+2 to ONE defence", so the row is part of the choice --
     * folding it into every row would buy Dodge, each Parry and Block for one
     * payment. Null is the ordinary case: nothing bought. Both price it "until
     * next turn", which is why the boundary clears it.
     */
    val boost: Pair<DefenseBoost, String>? = null,
) {
    /**
     * This round, less an Acrobatic Dodge rolled against any skill or level
     * but [skill]'s: its note would report a target the row's ±2 no longer
     * matches. Read off the stored outcome, because the round outlives the
     * dialog (play session 1) and a `remember` inside it cannot see a level
     * that moved while it was closed.
     */
    fun keptFor(skill: AcrobaticSkill?): RoundState {
        val roll = acrobatic ?: return this
        val same = skill != null && roll.label == skill.label && roll.baseTarget == skill.level
        return if (same) this else copy(acrobatic = null)
    }

    /**
     * One more parry made this round, counted by the roll that made it.
     *
     * 6cp left [priorParries] for the GM to set because the dialog "allocates
     * no defence". Each row has had its own Roll since the redesign, so a
     * rolled parry IS a defence chosen, and play session 5 parried and then
     * read 0 here. The stepper stays, for a parry made off this screen.
     */
    fun parried(): RoundState = copy(priorParries = priorParries + 1)

    /** A fresh round: every value the two sentences govern, back to default. */
    fun newRound(): RoundState = RoundState()

    /**
     * Whether anything the boundary would clear has actually been set.
     *
     * Compared against a fresh instance rather than field by field, so a value
     * added to this class is covered the day it is added -- the same reason
     * [newRound] is not six assignments.
     */
    fun isSpent(): Boolean = this != RoundState()
}
