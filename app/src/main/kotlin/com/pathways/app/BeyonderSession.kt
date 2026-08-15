package com.pathways.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pathways.model.Attr
import com.pathways.model.BASE_ATTRIBUTE
import com.pathways.model.BeyonderState
import com.pathways.model.Character
import com.pathways.model.MAX_ATTRIBUTE
import com.pathways.model.canDrink
import com.pathways.rules.Corruption
import com.pathways.rules.PcrOutcome
import com.pathways.rules.Pool
import com.pathways.rules.PotionConsumption
import com.pathways.rules.PremadeSheet
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

    /**
     * What the player has bought since the sheet was printed. Attributes are
     * a delta against the published values and skills are additions; see
     * [SavedEdits].
     */
    var edits by mutableStateOf(saved.edits)
        private set

    private fun persist() =
        onChange(SavedSession(state, lost, cor, corrupted, hpLost, fpLost, spiSpent, edits))

    /** Set [attr] to [level]; the point budget answers back through Derive. */
    fun setAttribute(attr: Attr, level: Int) {
        edits = edits.withAttribute(attr, level.coerceIn(BASE_ATTRIBUTE, MAX_ATTRIBUTE))
        persist()
    }

    /**
     * Choose an economic status, or null to keep the sheet's printed one
     * (Ch. 8, p. 80).
     *
     * Only the NAME is stored; the points, the reaction modifier and the
     * lifestyle are re-read from the book on every load, the same way a bought
     * skill's controlling attribute is. [com.pathways.rules.Wealth.applied]
     * replaces rather than adds when the character is composed, because a
     * character has one economic status and two wealth traits would bill twice.
     */
    fun setWealth(status: String?) {
        edits = edits.copy(wealth = status)
        persist()
    }

    /** Choose an appearance level, or null to go back to the printed sheet. */
    fun setAppearance(level: String?) {
        edits = edits.copy(appearance = level)
        persist()
    }

    /**
     * Buy the trait called [name] (Ch. 3's advantage and disadvantage lists).
     *
     * Nothing is stored but the name -- see [SavedEdits.traits]. Buying one
     * already saved is a no-op rather than a second entry: a double tap on a
     * list is not a rules violation, the same call [buySkill] makes.
     */
    fun buyTrait(name: String) {
        if (name in edits.traits) return
        edits = edits.copy(traits = edits.traits + name)
        persist()
    }

    /**
     * Give back a trait bought in play.
     *
     * **A choice needs a way back** -- a setter that is tested and unreachable
     * from the screen is a defect a review already caught here once (6u). Only
     * a BOUGHT trait can go: removing one the sheet printed is a different
     * feature needing a suppression the save has no shape for, and the
     * published sheet is read-only by design.
     */
    fun sellTrait(name: String) {
        if (name !in edits.traits) return
        edits = edits.copy(traits = edits.traits - name)
        persist()
    }

    /**
     * Buy a trait whose price the book leaves to the instance.
     *
     * Nothing here judges the purchase: [com.pathways.rules.PurchasableTraits]
     * refuses what the book does not license when the character is composed,
     * so a save may hold an entry that bills nothing rather than this method
     * carrying a second copy of that rule. What IS refused here is an exact
     * repeat, the same way [buyTrait] refuses one -- a double tap on a list is
     * not a rules question.
     */
    fun buyInstancedTrait(purchase: SavedInstancedTrait) {
        // Keyed exactly as the composition keys it, and the COST is not part
        // of that key. Refusing only an identical row would let the same
        // trait be saved twice at two prices: composition bills the first and
        // the builder lists both, so giving back the first would silently
        // change what the second costs (Codex). One identity, one decision.
        if (edits.instanced.any { it.key == purchase.key }) return
        edits = edits.copy(instanced = edits.instanced + purchase)
        persist()
    }

    /**
     * Give one back, by position.
     *
     * By INDEX rather than by name, because two purchases of one row are legal
     * -- Monastery Scholar carries two Vows -- and a bare ranged row composes
     * to the book's own name for both, so "Sense of Duty" can name two rows on
     * screen and could not say which to remove.
     */
    fun sellInstancedTrait(index: Int) {
        if (index !in edits.instanced.indices) return
        edits = edits.copy(
            instanced = edits.instanced.filterIndexed { at, _ -> at != index },
        )
        persist()
    }

    /**
     * Buy [name] at one point, the cheapest a skill can be owned at.
     *
     * A skill already bought is left exactly as it is rather than reset: a
     * double tap on a list is not a rules violation, and silently discarding
     * the points already spent would be the worse answer.
     */
    fun buySkill(name: String, specialization: String? = null) {
        if (edits.skills.any { it.name == name && it.specialization == specialization }) return
        edits = edits.copy(skills = edits.skills + SavedSkill(name, specialization, points = 1))
        persist()
    }

    /**
     * Move a bought skill's points by [delta], dropping it at zero.
     *
     * Zero points is not a skill at level 0 -- [com.pathways.model.PurchasedSkill]
     * refuses it and the book has no such thing -- so stepping below 1 removes
     * the purchase, which is also how a player un-buys a mistake.
     */
    fun spendOnSkill(name: String, specialization: String?, delta: Int) {
        val updated = edits.skills.mapNotNull { skill ->
            if (skill.name != name || skill.specialization != specialization) skill
            else (skill.points + delta).takeIf { it > 0 }?.let { skill.copy(points = it) }
        }
        edits = edits.copy(skills = updated)
        persist()
    }

    /** The premade as it is actually being played: published sheet plus edits. */
    fun characterFor(sheet: PremadeSheet): Character = edits.applyTo(sheet.toCharacter())

    /**
     * Spend or restore [pool] by [delta] points against a maximum of [max],
     * never taking the pool above that maximum.
     *
     * There is no floor: HP below zero is where the book's death rules live
     * (p. 40), FP below zero costs HP (p. 42), and SPI below zero is Soul
     * Debt (p. 35). Clamping any of them at zero would delete the three most
     * consequential states a character can be in.
     *
     * Two of the three pools bill a SECOND pool once they go negative, and
     * both charges are applied here rather than printed and forgotten:
     *
     *  - FP below zero costs HP, one per point (p. 42).
     *  - SPI below zero is Soul Debt: "+1 CoR per 1 point below 0" (p. 35).
     *
     * They differ in one way, and the book is the reason. FP's charge is
     * SYMMETRIC -- stepping back up refunds the HP, because a stepper the GM
     * cannot undo is a worse tool than one that occasionally needs a
     * correction, and the book says nothing to the contrary.
     *
     * **Soul Debt is one-way.** "CoR from Soul Debt are permanent and require
     * special rituals (Chapter 6) to remove" (p. 41), so recovering SPI does
     * NOT hand the corruption back (Codex). [cor] is a single total with no
     * memory of what charged it, so a refund could also take back CoR a ritual
     * or a potion put there. Removal has its own path: the CoR stepper on the
     * pathway screen, which is the explicit action the book asks for.
     *
     * Charging runs through [gainCor], so it cannot fire once the character is
     * lost -- a Rampager's books are the GM's now.
     *
     * [maxCor] is the drinker's CoR ceiling, read only on the SPI pool. It is
     * required rather than defaulted because no value is safe when it is
     * wrong: too low corrupts a character who should have survived. It is the
     * character's CURRENT Will, so it is passed per call for the same reason
     * [gainCor] takes one -- a potion can move it mid-walk.
     */
    fun spend(pool: Pool, delta: Int, max: Int, maxCor: Int) {
        when (pool) {
            Pool.HP -> hpLost = (hpLost + delta).coerceAtLeast(0)
            Pool.FP -> {
                val chargedBefore = (fpLost - max).coerceAtLeast(0)
                fpLost = (fpLost + delta).coerceAtLeast(0)
                val chargedAfter = (fpLost - max).coerceAtLeast(0)
                hpLost = (hpLost + chargedAfter - chargedBefore).coerceAtLeast(0)
            }
            Pool.SPI -> {
                val debtBefore = (spiSpent - max).coerceAtLeast(0)
                spiSpent = (spiSpent + delta).coerceAtLeast(0)
                val debtAfter = (spiSpent - max).coerceAtLeast(0)
                // Only a DEEPER debt charges; recovery gives nothing back.
                // Also keeps gainCor(0) meaning what it means elsewhere --
                // how a Will-lowering potion latches its loss.
                if (debtAfter > debtBefore) gainCor(debtAfter - debtBefore, maxCor)
            }
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
