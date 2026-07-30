package com.pathways.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pathways.model.BeyonderState
import com.pathways.model.canDrink
import com.pathways.rules.Corruption
import com.pathways.rules.PcrOutcome
import com.pathways.rules.Pool
import com.pathways.rules.PotionConsumption
import com.pathways.rules.RandomSource

/**
 * One character's walk down a pathway, as the screens see it.
 *
 * ponytail: plain Compose state over [SessionStore]'s JSON. No ViewModel: the
 * 23 premades are read-only, so this holds the walk and nothing else. Add one
 * when there is a character worth editing.
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

    /** Running CoR (Ch. 6, p. 45). Table state, so it lives here, not on Character. */
    var cor by mutableIntStateOf(saved.cor)
        private set

    /**
     * True once CoR has reached the character's maximum -- "the character is
     * permanently lost. The GM takes full control" (p. 45).
     *
     * Latched, never recomputed from `cor >= max`: the maximum is Will, and a
     * later potion can raise Will. A character lost at Will 10 does not come
     * back because a potion made it 11.
     */
    var corrupted by mutableStateOf(saved.corrupted)
        private set

    /** Both endings, for callers that only need to know play has stopped. */
    val isLost: Boolean get() = lost || corrupted

    /**
     * Damage taken and energy spent, as losses from the current maximum --
     * see [SavedSession]. Negative losses are legal and mean the pool is
     * above its maximum, which nothing in the book grants today; the clamp
     * lives at the top only.
     */
    var hpLost by mutableIntStateOf(saved.hpLost)
        private set

    var fpLost by mutableIntStateOf(saved.fpLost)
        private set

    var spiSpent by mutableIntStateOf(saved.spiSpent)
        private set

    private fun persist() =
        onChange(SavedSession(state, lost, cor, corrupted, hpLost, fpLost, spiSpent))

    /**
     * Spend or restore [pool] by [delta] points against a maximum of [max],
     * never taking the pool above that maximum.
     *
     * There is no floor: HP below zero is where the book's death rules live
     * (p. 40), FP below zero costs HP (p. 42), and SPI below zero is Soul
     * Debt (p. 35). Clamping any of them at zero would delete the three most
     * consequential states a character can be in.
     *
     * FP below zero also bills HP -- "Every point of FP spent below 0 also
     * costs 1 HP" (p. 42). Printing that rule under the tracker while leaving
     * the HP row untouched would show a character less hurt than they are, so
     * the charge is applied here. It is applied SYMMETRICALLY: stepping back
     * up refunds it, because a stepper the GM cannot undo is a worse tool
     * than one that occasionally needs a correction.
     */
    fun spend(pool: Pool, delta: Int, max: Int) {
        when (pool) {
            Pool.HP -> hpLost = (hpLost + delta).coerceAtLeast(0)
            Pool.FP -> {
                val chargedBefore = (fpLost - max).coerceAtLeast(0)
                fpLost = (fpLost + delta).coerceAtLeast(0)
                val chargedAfter = (fpLost - max).coerceAtLeast(0)
                hpLost = (hpLost + chargedAfter - chargedBefore).coerceAtLeast(0)
            }
            Pool.SPI -> spiSpent = (spiSpent + delta).coerceAtLeast(0)
        }
        persist()
    }

    /**
     * Move CoR by [delta], held inside 0..[max], and latch the loss if this
     * reaches the maximum.
     *
     * [max] is passed in rather than stored: it is the character's CURRENT
     * Will, which a potion can move mid-walk, so a copy taken at construction
     * would go stale. See [com.pathways.rules.Corruption.maxFor].
     */
    fun gainCor(delta: Int, max: Int) {
        if (isLost) return
        cor = Corruption.clamp(cor + delta, max)
        if (Corruption.isLostAt(cor, max)) corrupted = true
        persist()
    }

    /**
     * Move the current potion's digestion, clamped to 0-100% by the model.
     *
     * A Rampager digests nothing further: [lost] is the end of play for this
     * character, so leaving the tracker live would keep changing a sheet the
     * GM has already taken over.
     */
    fun digest(delta: Int) {
        if (isLost) return
        state = state?.digested(delta)
        persist()
    }

    /**
     * Drink [next], rolling for it when it is an advancement, and charge the
     * CoR the advancement costs.
     *
     * The first potion is not rolled for: the PCR is made against "the
     * drinker's digestion of their current potion" (p. 45), and a mortal has
     * no current potion. Returns the roll, or null when there was none.
     *
     * [maxCorAfter] gives the drinker's maximum CoR for a given state, and is
     * asked about the state they land in, not the one they left. Potions move
     * Will in both directions -- the Abyss pathway LOWERS it at Sequences 9, 8
     * and 6 -- so a potion can drop the ceiling onto a CoR total that was
     * safe a moment ago. Passing a number computed before the drink would
     * miss exactly that character.
     *
     * The book's floor for advancing early is 10 CoR, at or above a typical
     * Will of 10, so a character who rushes it can survive the roll and still
     * be lost to corruption on the same click. That is the book's arithmetic,
     * not a bug: [PcrOutcome.integrated] and [corrupted] are reported
     * separately so the dialog can say which happened.
     */
    fun drink(
        next: BeyonderState,
        dice: RandomSource,
        maxCorAfter: (BeyonderState) -> Int,
    ): PcrOutcome? {
        val current = state
        require(!isLost) { "a lost character drinks nothing further" }
        require(current.canDrink(next)) { "$next is not the next potion after $current" }

        if (current == null) {
            state = next
            persist()
            return null
        }
        val outcome = PotionConsumption.attempt(current, dice)
        if (outcome.integrated) {
            state = next
            // Charged only on success: a failed PCR ends the character as a
            // Rampager, and stacking a corruption total onto that would be
            // bookkeeping for a sheet the GM already owns.
            //
            // Called even when the potion costs nothing (a fully digested
            // advancement): a gain of 0 against the NEW ceiling is how a
            // Will-lowering potion latches the loss it just caused.
            gainCor(outcome.minimumCorGained, maxCorAfter(next))
        } else {
            lost = true
        }
        persist()
        return outcome
    }
}
