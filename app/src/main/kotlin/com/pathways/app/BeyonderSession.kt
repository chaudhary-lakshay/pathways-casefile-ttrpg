package com.pathways.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pathways.model.Attr
import com.pathways.model.BeyonderState
import com.pathways.model.Character
import com.pathways.model.SecondaryDelta
import com.pathways.model.Trait
import com.pathways.model.canDrink
import com.pathways.rules.Corruption
import com.pathways.rules.PcrOutcome
import com.pathways.rules.Pool
import com.pathways.rules.PotionConsumption
import com.pathways.rules.PremadeSheet
import com.pathways.rules.RandomSource
import com.pathways.rules.ScoreFloors
import com.pathways.rules.TraitEffects
import com.pathways.rules.TraitGrants

/**
 * One character in play, as the screens see it: the walk down a pathway,
 * the pools, CoR and everything bought since the sheet was printed ([edits]).
 *
 * ponytail: plain Compose state over [SessionStore]'s JSON, and no ViewModel.
 * The reason it gave, "the 23 premades are read-only, so this holds the walk
 * and nothing else. Add one when there is a character worth editing", has
 * expired on both counts (6fu): it holds [edits], and none was added. The
 * save is written on every change, so what a recreated activity loses is
 * the screen and whatever was only on it, never the character. A rotation
 * no longer recreates it (the manifest's `configChanges`, 6fv).
 */
class BeyonderSession(
    saved: SavedSession = SavedSession(),
    /**
     * The premade this session edits, for the score floors: an edit that
     * would leave a guarded score below [ScoreFloors] is refused, and a save
     * already below one is bought back to it on opening. Null skips both --
     * for a session whose edits nothing composes.
     */
    private val sheet: PremadeSheet? = null,
    private val onChange: (SavedSession) -> Unit = {},
) {

    var state by mutableStateOf(saved.beyonder)
        private set

    /**
     * True once a Potion Consumption Roll has been failed. The book is blunt
     * about it: "the character is lost -- they transform into an NPC Rampager
     * immediately, permanently under the GM's control" (p. 45). Nothing here
     * un-loses them; that is the point -- save one roll the book itself
     * reaches: a Luck reroll of the failed PCR ([rerollPcr], 6fg).
     */
    var lost by mutableStateOf(saved.lost)
        private set

    /**
     * The potion a failed PCR attempted, while a reroll of it is unspent.
     * SAVED, not remembered: a restart between the failure and the reroll
     * would otherwise strand a Lucky character as a Rampager (Codex's HIGH).
     * Who may spend it is [com.pathways.rules.TraitEffects.rerollSource]'s.
     */
    var pendingPcr by mutableStateOf(saved.pendingPcr)
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

    /**
     * Luck's "may reroll any one failed roll", spent on the failed PCR
     * [pendingPcr] holds: the same potion, from the same digestion. A success
     * replaces the failure -- the character advances and pays the same CoR
     * floor a first success pays; a failure changes nothing ("If the re-roll
     * also fails there is no additional effect"). Spent either way: one
     * reroll per failure is this app's, and the session's count is the GM's.
     */
    fun rerollPcr(dice: RandomSource, maxCorAfter: (BeyonderState) -> Int): PcrOutcome {
        val next = pendingPcr
        val current = state
        require(lost && !corrupted && next != null && current != null) { "no failed PCR to reroll" }
        val ceilingAfter = maxCorAfter(next)
        // The guard [drink] makes before its roll, made again: the ceiling
        // is the CURRENT Will's, which an edit since the failure can move.
        require(ScoreFloors.holdsCeiling(ceilingAfter)) {
            "$next would leave a CoR ceiling of $ceilingAfter"
        }
        val outcome = PotionConsumption.attempt(current, dice)
        pendingPcr = null
        if (outcome.integrated) {
            // Cleared FIRST: gainCor does nothing for a lost character.
            lost = false
            state = next
            gainCor(outcome.minimumCorGained, ceilingAfter)
        }
        persist()
        return outcome
    }

    private fun persist() =
        onChange(SavedSession(state, lost, cor, corrupted, hpLost, fpLost, spiSpent, edits, pendingPcr))

    // A save written before the floors existed can hold a score the rules
    // throw on -- HP sold to 0 killed the sheet screen while it was drawn, on
    // every open. Bought back ONCE and written back, rather than lifted on
    // every read: a lift-on-read would leave the builder's "-11 bought" and
    // the bill disagreeing (Codex, on the design).
    // A save bought past a ceiling throws wherever a potion is digested on
    // top of it -- browsing included. Held to the ceilings FIRST: lowering ST
    // lowers HP, and the floors must see the HP that results.
    init {
        if (sheet != null) {
            // The repair itself is [repairedEdits], which the gallery reads
            // too; writing it back and latching the loss are this session's.
            val repaired = saved.repairedEdits(sheet)
            if (!isLost && repaired.corCeilingOn(sheet, state) <= cor) corrupted = true
            if (repaired != edits || corrupted != saved.corrupted) {
                edits = repaired
                persist()
            }
        }
    }

    /**
     * Whether [next] keeps every guarded score at its floor, digested through
     * the potion this character is on. [edit] asks it of every edit that can
     * lower a score; the builder asks it through [canSetAttribute] and
     * [canSetSecondary], which build the SAME edit the setter would -- a
     * candidate built on the screen asked about ST 8 itself, and `Attributes`
     * throws on an 8 (SM-T570).
     */
    private fun allows(next: SavedEdits): Boolean =
        sheet == null || (next.shortfallOn(sheet, state) == SecondaryDelta() && keepsBelowCeiling(next))

    /**
     * Whether a live character stays below their maximum CoR after [next].
     * "At maximum CoR: The character is permanently lost" (book line 3075) is
     * a STATE, and the latch fires only on a gain, so an edit that lowers
     * the ceiling -- Will, IQ, or selling Spiritual Fortitude -- is refused
     * at the total rather than latched: a builder tap is a purchase, not an
     * event in play. A potion is, and [drink] latches instead.
     */
    private fun keepsBelowCeiling(next: SavedEdits): Boolean =
        isLost || sheet == null || next.corCeilingOn(sheet, state) > cor

    private fun edit(next: SavedEdits) {
        if (!allows(next)) return
        edits = next
        persist()
    }

    /** Set [attr] to [level]; the point budget answers back through Derive. */
    fun setAttribute(attr: Attr, level: Int) = edit(withAttribute(attr, level))

    /** Whether [setAttribute] would take [level], rather than refuse it. */
    fun canSetAttribute(attr: Attr, level: Int): Boolean = allows(withAttribute(attr, level))

    private fun withAttribute(attr: Attr, level: Int) = edits.withAttribute(attr, level).withinCeilings()

    /**
     * Carry the chapter-8 weapon [name] (p. 80).
     *
     * A repeat is refused rather than appended: the catalogue's nine rows
     * carry nine distinct names, so a second copy is not a second weapon and
     * would print a second identical parry row. Matched without case, which
     * is also what a hand-edited save takes.
     *
     * Nothing is billed. Chapter 8 prices weapons in money and this app
     * tracks none, so carrying one moves no point total.
     */
    fun carryWeapon(name: String) {
        if (edits.weapons.any { it.equals(name, ignoreCase = true) }) return
        edits = edits.copy(weapons = edits.weapons + name)
        persist()
    }

    /** Put [name] down again — the way back from [carryWeapon]. */
    fun dropWeapon(name: String) {
        val kept = edits.weapons.filterNot { it.equals(name, ignoreCase = true) }
        if (kept.size == edits.weapons.size) return
        edits = edits.copy(weapons = kept)
        persist()
    }

    /**
     * Buy [steps] of the secondary characteristic the book's row [key] names
     * (Ch. 2, pp. 8-9); 0 is the way back to what the sheet printed.
     *
     * A zero is REMOVED rather than stored, so a player who steps back to the
     * printed value leaves a save that is empty again rather than one that
     * looks edited forever. Coerced here as well as on the way back in: this
     * is the input boundary, and the file is the other one.
     */
    fun setSecondary(key: String, steps: Int) = edit(withSecondary(key, steps))

    /** Whether [setSecondary] would take [steps], rather than refuse them. */
    fun canSetSecondary(key: String, steps: Int): Boolean = allows(withSecondary(key, steps))

    private fun withSecondary(key: String, steps: Int): SavedEdits {
        val held = edits.copy(secondaries = edits.secondaries + (key to steps)).withinCeilings()
        return if (steps == 0) held.copy(secondaries = held.secondaries - key) else held
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
        // Spiritual Fortitude is +3 to the CoR ceiling, so selling it can
        // take the ceiling below its floor, or onto the CoR total.
        edit(edits.copy(traits = edits.traits - name))
    }

    /** Whether [sellTrait] would give [name] back, rather than refuse it. */
    fun canSellTrait(name: String): Boolean = name in edits.traits && allows(edits.copy(traits = edits.traits - name))

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
     * Buy a language, or change one already owned (Ch. 3, pp. 13-14).
     *
     * Nothing is stored but the three names -- see [SavedEdits.languages] --
     * and nothing here judges the purchase: [com.pathways.rules.Languages]
     * drops what the book does not print when the character is composed.
     */
    fun buyLanguage(language: SavedLanguage) {
        edits = edits.withLanguage(language)
        persist()
    }

    /** Give a language back, by the name it is owned under. */
    fun sellLanguage(name: String) {
        val kept = edits.languages.filterNot { it.language.equals(name, ignoreCase = true) }
        if (kept.size == edits.languages.size) return
        edits = edits.copy(languages = kept)
        persist()
    }

    /**
     * Give one back, by position.
     *
     * By INDEX rather than by name, because two purchases of one row are legal
     * -- Monastery Scholar carries two Vows -- and a bare ranged row composes
     * to the book's own name for both, so "Sense of Duty" can name two rows on
     * screen and could not say which to remove.
     *
     * [drawn] is the row the button was drawn beside. A second tap of the
     * same picture arrives with the first's index, which by then holds the
     * neighbour (6gb): nothing is sold unless the row is still there.
     */
    fun sellInstancedTrait(index: Int, drawn: SavedInstancedTrait) {
        if (edits.instanced.getOrNull(index) != drawn) return
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
     * The traits in play: [characterFor]'s plus every row the potions drunk
     * granted ([TraitGrants.withGrantedTraits], 6fk). One answer for the
     * sheet's rolls and the builder's "already held", so a granted row is
     * never sold again. Never the character: the budget prices that.
     */
    fun traitsInPlay(sheet: PremadeSheet): List<Trait> =
        TraitGrants.withGrantedTraits(characterFor(sheet).traits, state)

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
        if (Corruption.isLostAfter(cor, delta, max)) corrupted = true
        cor = Corruption.clamp(cor + delta, max)
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
     * Whether a screen that drew [printed] -- and [printedCor], where the act
     * is priced in it -- is still showing the truth.
     *
     * A click handler belongs to the composition that drew it, and this
     * session can move before that composition is replaced: a second tap on
     * Drink, a CoR step, a lost roll. [drink] is irreversible and refuses a
     * state it was not offered by THROWING, so the handler asks this first
     * and does nothing on a stale picture. The screen redraws; the GM taps
     * again, looking at what is true.
     *
     * [isLost] is compared as well as [state], because a failed roll leaves
     * [state] exactly where it was -- and it is [isLost], not [lost], because
     * a character can be lost to corruption without failing any roll.
     * [edits] is not compared: nothing on the pathway screen changes it.
     *
     * Reproduced on an SM-T570 before this existed: two taps on Drink a few
     * milliseconds apart, and the second died in [drink]'s own `require`.
     */
    fun stillShows(printed: BeyonderState?, printedCor: Int): Boolean =
        !isLost && state == printed && cor == printedCor

    /**
     * The looser question the CoR steppers ask: is this still the potion the
     * screen drew a ceiling for?
     *
     * Pathway and Sequence only. A digestion step or an earlier CoR step
     * moves neither the ceiling nor the meaning of the next tap, and
     * comparing them would drop the second of two quick taps. (Checked, not
     * assumed: the ceiling is `SavedEdits.corCeilingOn`, which goes
     * through `PotionDigestion.of`, and nothing in `PotionDigestion.kt` or
     * `Corruption.kt` reads `digestion`.) A Drink does
     * move the ceiling -- a potion can LOWER Will -- so a step from the
     * composition before it waits for the screen to catch up.
     */
    fun onSamePotion(printed: BeyonderState?): Boolean =
        !isLost && state?.pathway == printed?.pathway && state?.sequence == printed?.sequence

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
        // Before the roll and before any state moves, the first potion
        // included: Abyss Sequence 9 lowers Will, and a ceiling below the
        // floor has no CoR the rules can count towards. The CEILING alone,
        // because no potion lowers any other guarded score (ScoreFloorsTest).
        val ceilingAfter = maxCorAfter(next)
        require(ScoreFloors.holdsCeiling(ceilingAfter)) {
            "$next would leave a CoR ceiling of $ceilingAfter"
        }

        if (current == null) {
            state = next
            // A mortal can carry CoR (a ritual's), and Abyss Sequence 9 is
            // Will -1: the first potion can land on the total too, and it is
            // an event in play, so it latches rather than being bought back.
            gainCor(0, ceilingAfter)
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
            gainCor(outcome.minimumCorGained, ceilingAfter)
        } else {
            lost = true
            pendingPcr = next
        }
        persist()
        return outcome
    }
}
