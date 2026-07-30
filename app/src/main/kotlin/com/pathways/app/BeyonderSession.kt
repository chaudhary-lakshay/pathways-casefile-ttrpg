package com.pathways.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pathways.model.BeyonderState
import com.pathways.model.canDrink
import com.pathways.rules.PcrOutcome
import com.pathways.rules.PotionConsumption
import com.pathways.rules.RandomSource

/**
 * One character's walk down a pathway, as the screens see it.
 *
 * ponytail: plain Compose state, no ViewModel and no storage. It survives
 * navigation, not process death, which is the right size while the 23
 * premades are read-only. Persist when there is a character worth saving.
 */
class BeyonderSession(
    saved: SavedSession = SavedSession(),
    private val onChange: (SavedSession) -> Unit = {},
) {

    var state by mutableStateOf(saved.beyonder)
        private set

    /**
     * True once a Potion Consumption Roll has been failed. The book is blunt
     * about it: "the character is lost -- they transform into an NPC Rampager
     * immediately, permanently under the GM's control" (p. 45). Nothing here
     * un-loses them; that is the point.
     */
    var lost by mutableStateOf(saved.lost)
        private set

    private fun persist() = onChange(SavedSession(state, lost))

    /**
     * Move the current potion's digestion, clamped to 0-100% by the model.
     *
     * A Rampager digests nothing further: [lost] is the end of play for this
     * character, so leaving the tracker live would keep changing a sheet the
     * GM has already taken over.
     */
    fun digest(delta: Int) {
        if (lost) return
        state = state?.digested(delta)
        persist()
    }

    /**
     * Drink [next], rolling for it when it is an advancement.
     *
     * The first potion is not rolled for: the PCR is made against "the
     * drinker's digestion of their current potion" (p. 45), and a mortal has
     * no current potion. Returns the roll, or null when there was none.
     */
    fun drink(next: BeyonderState, dice: RandomSource): PcrOutcome? {
        val current = state
        require(!lost) { "a Rampager drinks nothing further" }
        require(current.canDrink(next)) { "$next is not the next potion after $current" }

        if (current == null) {
            state = next
            persist()
            return null
        }
        val outcome = PotionConsumption.attempt(current, dice)
        if (outcome.integrated) state = next else lost = true
        persist()
        return outcome
    }
}
