package com.pathways.rules

import com.pathways.model.SkillKey
import com.pathways.model.Trait
import com.pathways.rules.TraitEffects.ALERTNESS
import com.pathways.rules.TraitEffects.ALERTNESS_PER_LEVEL
import com.pathways.rules.TraitEffects.BespokeBonus
import com.pathways.rules.TraitEffects.COMBAT_REFLEXES
import com.pathways.rules.TraitEffects.ETHER_BODY_DAMAGE
import com.pathways.rules.TraitEffects.FIT
import com.pathways.rules.TraitEffects.FIT_BONUS
import com.pathways.rules.TraitEffects.HIGH_PAIN_THRESHOLD
import com.pathways.rules.TraitEffects.UNFIT
import com.pathways.rules.TraitEffects.UNFIT_PENALTY
import com.pathways.rules.TraitEffects.VERY_FIT
import com.pathways.rules.TraitEffects.VERY_FIT_BONUS
import com.pathways.rules.TraitNames.levelOf

/**
 * What a character's traits do to an HT roll the Major Wound table asks for:
 * the number, and the sentence naming every trait that produced it.
 *
 * That table asks for two, and they are different rolls with different
 * reachers — the table's own roll ([TraitAttributeRolls.majorWoundHt]) and the one
 * its Failure row prints to recover from the stun ([StunRecovery.bonusFor]).
 * The shape is shared because both are "a bonus and the credit for it"; the
 * SCOPES are not, and each function decides its own.
 *
 * [line] is null exactly when [bonus] is zero, so a screen cannot print a
 * credit for a roll nothing moved.
 */
data class MajorWoundHt(val bonus: Int, val line: String?)

/**
 * What a character's traits add to a roll of an attribute itself, never of a
 * skill: Per (Alertness), SPI (Ether Body Damage) and HT, among them the HP
 * table's death and consciousness rolls and the Major Wound's knockdown and
 * stun recovery. [TraitEffects.modifiers] is the skill path; both read
 * [HT_ROLL_TRAITS] and [SPI_ROLL_TRAITS] (split step 9).
 */
object TraitAttributeRolls {

    /**
     * Alertness's "+1 per level to all Per rolls", for the two bare Per rolls
     * no [SkillKey] reaches: Detection's and Spirit Vision's (6fe). One answer,
     * where each call site used to build its own.
     */
    fun alertnessBonus(traits: List<Trait>): Int =
        (levelOf(traits, ALERTNESS) ?: 0) * ALERTNESS_PER_LEVEL

    /**
     * Every trait whose cell says "to all HT rolls", and by how much.
     *
     * One map, read by both paths -- [TraitEffects.modifiers] for an HT-controlled skill
     * and [htRollBonus] for the bare Major Wound roll -- because a trait that
     * reached one and not the other is exactly the gap this map was made to
     * close.
     */
    internal val HT_ROLL_TRAITS: Map<String, Int> = mapOf(
        FIT to FIT_BONUS,
        VERY_FIT to VERY_FIT_BONUS,
        UNFIT to -UNFIT_PENALTY,
    )

    /**
     * The traits whose cell says "to all HT rolls", for the guard tests.
     *
     * **This cannot check the map's own claim**, which is what let `Very Fit`
     * sit outside it: it returns the keys, so a row nobody added is invisible
     * to any test built on it. `VitalRollsTest` reads the CATALOGUE for the
     * phrase and compares -- the census-as-a-test 6bu argued for.
     */
    fun htRollNames(): Set<String> = HT_ROLL_TRAITS.keys

    /**
     * Every trait whose cell says "SPI-based rolls", and by how much.
     *
     * The third controlling-attribute join, beside Alertness on Per and
     * Fit/Unfit on HT -- and the attribute this app already scopes a condition
     * to, since [Conditions] keeps Spiritual Attrition on SPI rolls by the same
     * test (6h). Signed, exactly as [HT_ROLL_TRAITS] is: the direction belongs
     * to the value, not to the map's name, so a row granting a BONUS to SPI
     * rolls would join it without a second accessor.
     *
     * One map, two paths -- [TraitEffects.modifiers] for an SPI-controlled skill and
     * [spiRollBonus] for the bare attribute roll -- because a trait reaching
     * one and not the other is the gap Alertness fell into (6al) and Fit fell
     * into after it (6bs).
     *
     * **And the book itself draws that distinction, on this attribute.** Line
     * 2351 prints Spiritual Attrition as "-3 to all SPI-based SKILL rolls"
     * (and "Beyonder ability checks", which reaches Spirit Vision's look,
     * play session 3) where this cell says "All SPI-based rolls" -- the same one-word
     * difference the book makes between "social skill rolls" and "social
     * rolls" and settles in one bracket of its own (6bt). So the wider phrase
     * really is wider, and the bare rolls are not an assumption: a condition
     * scoped to skills stops at [TraitEffects.modifiers], and this does not.
     */
    internal val SPI_ROLL_TRAITS: Map<String, Int> = mapOf(
        ETHER_BODY_DAMAGE to -1,
    )

    /** The traits whose cell says "SPI-based rolls", for the guard tests. */
    fun spiRollNames(): Set<String> = SPI_ROLL_TRAITS.keys

    /**
     * What [traits] add to a bare SPI roll, signed.
     *
     * The one this app makes is a detection roll, which takes the higher of
     * SPI or Per and so is SPI-based only some of the time -- see
     * [Detection.attributeFor], which decides WHICH attribute is rolled before
     * applying this. A divination is the other SPI path and reaches its target
     * through [Divination.targetFor] rather than a [SkillKey], so it asks here
     * too.
     *
     * Deliberately NOT a general attribute-roll accessor, the same call
     * [htRollBonus] makes: the app's other bare rolls are Will, IQ and Per.
     * **"Nothing names those" was loose and 6de's sweep corrected it** -- four
     * catalogue rows name Will rolls, all CONDITIONAL and none carried by a
     * fixture. See [htRollBonus], which carries the census.
     */
    fun spiRollBonus(traits: List<Trait>): Int =
        SPI_ROLL_TRAITS.entries.sumOf { (trait, value) ->
            if (levelOf(traits, trait) != null) value else 0
        }

    /**
     * What [traits] add to an HT roll that can knock the character down.
     *
     * **The Major Wound's HT roll is one**, which is what a review caught
     * (Codex): its fail-by-5 row is "Knocked down and unconscious for 1d
     * hours", so the roll decides knockdown among its three outcomes. This
     * clause was about to ship disclaimed as "this app rolls no knockdown
     * roll to modify" -- a fresh excuse of exactly the kind this slice exists
     * to remove, in the same commit that disproved the old one.
     *
     * **This is High Pain Threshold's alone, and the scope is a decision.**
     * Five rows print a knockdown clause and only this one names an attribute
     * ("+3 on **HT rolls**"), which is what joins it to the Major Wound roll.
     * The Balance rows say "+6 / +2 to avoid knockdown" with no attribute, and
     * this book prints two OTHER knockdown mechanics that are DX-based --
     * Shove's "target rolls DX or fall", Takedown's opposed DX -- plus three
     * hit-location rolls whose attribute it never states at all. Which of them
     * an unscoped clause covers is not settled by anything printed, so it is
     * refused rather than defaulted, and [TraitReport.reported] says so on the sheet.
     *
     * **The +3 buys more than the clause names, and that is the reading.**
     * This one roll decides three outcomes -- shock, stun, and knockdown on its
     * fail-by-5 row -- so a bonus "to avoid knockdown" also makes the stun row
     * less likely. The alternative is to move only the fail-by-5 boundary,
     * which is arithmetic the book prints nowhere; the literal reading of "+3
     * on HT rolls to avoid knockdown", applied to an HT roll that avoids
     * knockdown, is the smaller claim. Recorded rather than smoothed (4.1).
     *
     * The book's other knockdown rolls are in the hit-location effects
     * ("Knockdown roll at -5", Neck and Face; "-10", Skull) and print no
     * attribute to roll against, so this app shows those cells rather than
     * rolling them. [RollKind.MAJOR_WOUND_HT] is this bonus's only roll.
     */
    fun knockdownBonus(traits: List<Trait>): Int = BespokeBonus.KNOCKDOWN.of(traits)

    /**
     * What [traits] add to a roll made to avoid DEATH, which is one of the two
     * rolls the HP table prints ([VitalCheck.SURVIVAL]).
     *
     * `Hard to Kill` is "+1/level to HT rolls to avoid death", so the level is
     * the number: a sheet printing "Hard to Kill 2" rolls at +2. It is
     * deliberately scoped away from the consciousness roll one row above, for
     * the reason [knockdownBonus] is scoped away from both -- a clause naming
     * its own condition is not widened by the app that reads it (6ck).
     *
     * **This used to be an UNAPPLIED entry reading "this app models no death
     * roll to avoid".** That was true when it was written and stopped being
     * true in the commit that offered the roll; a refusal expires exactly like
     * a sweep verdict (6ba, 6bl).
     */
    fun deathRollBonus(traits: List<Trait>): Int = BespokeBonus.DEATH.of(traits)

    /**
     * What [traits] add to a roll made to REMAIN CONSCIOUS, the other of the
     * two rolls the HP table prints ([VitalCheck.CONSCIOUSNESS]).
     *
     * `Hard to Subdue` is printed immediately below `Hard to Kill`, at the same
     * price and the same shape, and the pair partitions the table: one names
     * "HT rolls to avoid death", the other "to remain conscious when reduced to
     * 0 or negative HP". The second quotes the 0 HP row's own words — "roll
     * each turn to remain conscious".
     *
     * **It reached nothing until 6dt, and the reason is the filter.** Every
     * census that would have found it looked for the phrase "HT roll", which
     * this cell never uses — a roll the book NAMES rather than describes, which
     * is 6bu's finding on a fourth mechanic.
     *
     * **It does not reach the survival roll**, even though that roll happens
     * at negative HP and this clause names negative HP. Both checks are due
     * below `-HP`, and two rolls falling in one moment does not merge their
     * scopes: the survival row's own words are "or die". Widening a clause
     * that names its own condition is the move 6ck refused.
     *
     * No cap: `Hard to Kill`'s cell prints "can take 1-5 levels" and this one
     * prints nothing, so a ceiling here would be app-authored (4.1).
     */
    fun consciousnessRollBonus(traits: List<Trait>): Int = BespokeBonus.CONSCIOUSNESS.of(traits)

    /**
     * What [traits] do to the Major Wound's HT roll, and the sentence that
     * says so.
     *
     * **One object rather than two functions, because the number and its
     * explanation must agree.** `DamageDialog` once spelt "includes +3 from
     * High Pain Threshold" itself, which is a second copy of WHICH trait pays
     * -- the failure this codebase produced three times (6ag, 6ai, 6be) --
     * and 6bp moved the sentence here for that reason. It stayed named after
     * the knockdown alone until a second trait reached the same roll, at
     * which point a caller adding one bonus and printing the other would be
     * showing a breakdown that does not sum.
     *
     * Two rules reach it and they are different rules. [knockdownBonus] is
     * High Pain Threshold's, scoped by the clause that names an attribute;
     * [htRollBonus] is "all HT rolls", which this roll is.
     */
    fun majorWoundHt(traits: List<Trait>): MajorWoundHt {
        val parts = mutableListOf<String>()
        val knockdown = knockdownBonus(traits)
        if (knockdown != 0) parts += "+$knockdown from $HIGH_PAIN_THRESHOLD"
        for ((trait, value) in HT_ROLL_TRAITS) {
            if (levelOf(traits, trait) == null) continue
            parts += "${if (value < 0) "−${-value}" else "+$value"} from $trait"
        }
        if (parts.isEmpty()) return MajorWoundHt(0, null)
        val tail = if (knockdown != 0) " — this roll's fail-by-5 row is a knockdown." else "."
        return MajorWoundHt(
            knockdown + htRollBonus(traits),
            "includes ${parts.joinToString(", ")}$tail",
        )
    }

    /**
     * The same, for the recovery roll the Failure row prints ([StunRecovery]).
     *
     * Here rather than on [StunRecovery] because [HT_ROLL_TRAITS] is private
     * and the credit is built from it — the number itself still comes from
     * [StunRecovery.bonusFor], so the two cannot disagree.
     *
     * The scopes differ by one clause each way, which is the reason this is
     * not [majorWoundHt] with a flag: `High Pain Threshold` reaches that roll
     * and not this one (its clause names knockdown), and Combat Reflexes
     * reaches this one and not that one (its clause names stun recovery).
     */
    fun stunRecoveryHt(traits: List<Trait>): MajorWoundHt {
        val parts = mutableListOf<String>()
        if (stunRecoveryBonus(traits) != 0) {
            parts += "+${StunRecovery.COMBAT_REFLEXES_BONUS} from $COMBAT_REFLEXES"
        }
        for ((trait, value) in HT_ROLL_TRAITS) {
            if (levelOf(traits, trait) == null) continue
            parts += "${if (value < 0) "−${-value}" else "+$value"} from $trait"
        }
        if (parts.isEmpty()) return MajorWoundHt(0, null)
        return MajorWoundHt(StunRecovery.bonusFor(traits), "includes ${parts.joinToString(", ")}.")
    }

    /**
     * What [traits] add to any roll of HT itself.
     *
     * **"+1 to all HT rolls" means all of them, and [TraitEffects.modifiers] can only
     * reach the ones a [SkillKey] carries.** The Major Wound's roll was the
     * only bare HT roll this app made -- so Fit missed it for six premades
     * from 6n until 6bs -- and 6de added two more, the HP table's own
     * consciousness and survival rolls ([VitalRolls]). The same structural
     * gap 6al had to thread explicitly for Alertness, whose "all Per rolls"
     * could not reach the detection roll.
     *
     * Deliberately NOT a general attribute-roll bonus. The app's other bare
     * rolls are Will, IQ, Per and SPI (fright, self-control, concentration,
     * detection, Spirit Vision). **A sweep in 6de asked whether any trait names those**, and
     * the Will half of its answer expired in 6df: the query is `Will rolls`
     * (plural, which is what excludes `Compulsive Gambling`'s singular
     * "requires Will roll at -2"), it returns four rows, and three of them
     * carry a modifier that now reaches a roll -- through [TraitClauses.WILL_CLAUSES],
     * NOT through here. That is the structural point this KDoc is making: a
     * clause naming a CIRCUMSTANCE is the GM's to declare, so it cannot be
     * summed unasked the way "+1 to all HT rolls" can. 6de's "no fixture
     * carries any of them" was also a claim about the wrong corpus --
     * `PurchasableTraits.offered()` makes all three buyable. IQ is still
     * named by nothing at all.
     */
    fun htRollBonus(traits: List<Trait>): Int =
        HT_ROLL_TRAITS.entries.sumOf { (trait, value) ->
            if (levelOf(traits, trait) != null) value else 0
        }

    /**
     * What [traits] add to a roll to recover from stun.
     *
     * The same cell's second clause, and it was refused until 6dr on the
     * ground that this app made no such roll. [StunRecovery] is that roll;
     * read its KDoc for which stuns it answers for and why the others are a
     * gap rather than a refusal.
     *
     * **The clause names the CONDITION, never an attribute** — "+6 to recover
     * from stun" — so it would reach the Fright Effects table's Will and IQ
     * recovery rolls on the same warrant, if this app offered them. That is
     * the opposite scoping to [knockdownBonus] one clause up, whose "+3 on
     * **HT rolls** to avoid knockdown" names both, and the difference is the
     * book's rather than this app's.
     */
    fun stunRecoveryBonus(traits: List<Trait>): Int =
        if (levelOf(traits, COMBAT_REFLEXES) != null) StunRecovery.COMBAT_REFLEXES_BONUS else 0
}
