package com.pathways.rules

import com.pathways.model.Layer
import com.pathways.model.ModEffect
import com.pathways.model.ModTarget
import com.pathways.model.Modifier
import com.pathways.model.ModifierId
import com.pathways.model.ModifierSource
import com.pathways.model.Outcome
import com.pathways.model.RollResult
import com.pathways.rules.content.ContentLoader
import com.pathways.rules.content.HitLocation
import com.pathways.rules.content.MajorWound
import com.pathways.rules.content.WoundingMultiplier

/**
 * Calling a shot, and what it multiplies (Ch. 5, p. 41, both Optional).
 *
 * One table costs the attacker to-hit and the other pays them back in damage,
 * and they are the same decision: aiming at a skull is -7 and x4. So both live
 * here rather than one sitting with [Attacks] and one with [Damage].
 *
 * The to-hit half is [Layer.SITUATIONAL], like [Attacks] and [Ranged] -- a
 * called shot is something the player chose this turn, not a state the
 * character is carrying.
 *
 * **The two multipliers STACK.** The book prints the worked example: "a
 * cutting hit to the Neck (Cut x2) with a sword (Cut x1.5) applies x3 total".
 * That is not GURPS proper, where a hit location's multiplier replaces the
 * damage type's, and following GURPS here would halve a cutting neck hit. Book
 * wins, as everywhere else in this project.
 *
 * **Nothing here applies the multiplier to a number.** The app shows damage as
 * an expression ("1d-3 cr"), never a rolled total, so there is no product to
 * round and no rounding rule to invent -- this book prints none, and GURPS's
 * round-down is GURPS's. What the player is shown is the multiplier itself.
 */
object Wounds {

    /** The nine hit locations, in printed order. */
    fun locations(): List<HitLocation> = ContentLoader.hitLocations()

    /** The eight damage types, in printed order. */
    fun damageTypes(): List<WoundingMultiplier> = ContentLoader.woundingMultipliers()

    /**
     * The row named [target]. Refused rather than defaulted: a called shot at
     * a location the book does not print is a caller bug, not a torso hit.
     */
    fun locationNamed(target: String): HitLocation =
        locations().firstOrNull { it.target.equals(target, ignoreCase = true) }
            ?: throw IllegalArgumentException(
                "no hit location named '$target'; the book prints " +
                    locations().joinToString { it.target },
            )

    /**
     * The wounding multiplier for a damage-type abbreviation ("cr", "cut").
     *
     * Only the four this TABLE abbreviates resolve. "pi-" and "pi+" are
     * GURPS's spelling of Piercing (small) and Large Piercing, and chapter 5
     * prints both rows without an abbreviation, so they are refused rather
     * than quietly read as x1 -- silently halving a small-piercing hit is
     * exactly the kind of wrong answer a default produces.
     *
     * **Chapter 8 prints "pi+" anyway**, on the Bolt-Action Rifle, so the
     * refusal is now reachable from real content and not only from a typo.
     * The two chapters disagree and neither is repaired; a caller holding a
     * weapon's damage type asks [knowsType] before it asks for a number.
     */
    fun typeMultiplier(type: String): Double {
        val row = damageTypes().firstOrNull { it.abbreviation.equals(type, ignoreCase = true) }
        if (row != null) return row.multiplier
        val fromAppendix = appendixMultiplier(type)
        require(fromAppendix != null) {
            "no damage type is abbreviated '$type'; this book prints " +
                knownAbbreviations().joinToString()
        }
        return fromAppendix
    }

    /**
     * The multiplier an Appendix A label reaches, or null when it names none.
     *
     * The appendix agrees with chapter 5 on every multiplier it repeats, so
     * this adds no number the book does not already print -- only the two
     * labels chapter 5 omits. `extract_appendix.py` fails if that agreement
     * ever breaks, which is what keeps this a reading rather than a second
     * opinion silently preferred.
     */
    private fun appendixMultiplier(type: String): Double? =
        ContentLoader.damageAbbreviations()
            .firstOrNull { it.abbreviation?.equals(type, ignoreCase = true) == true }
            ?.multiplier

    /** Every abbreviation this book prints, chapter 5's and the appendix's. */
    fun knownAbbreviations(): List<String> {
        val chapterFive = damageTypes().mapNotNull { it.abbreviation }
        val appendixOnly = ContentLoader.damageAbbreviations()
            .mapNotNull { it.abbreviation }
            .filterNot { it in chapterFive }
        return chapterFive + appendixOnly
    }

    /**
     * Whether the BOOK abbreviates [type] anywhere, so [typeMultiplier] can
     * price it.
     *
     * True for the Bolt-Action Rifle's "pi+" since Appendix A was read. It was
     * false for four milestones on the belief that chapter 5's silence was the
     * whole book's -- the appendix sat at 0.0% coverage and prints the label.
     * Still false for GURPS's "pi-", which this book never writes: the
     * appendix spells small piercing "Pi (small)".
     */
    fun knowsType(type: String): Boolean =
        damageTypes().any { it.abbreviation.equals(type, ignoreCase = true) } ||
            appendixMultiplier(type) != null

    /**
     * What a [type] hit at [location] does to penetrating damage, both tables
     * composed. A null [location] is an uncalled shot: the type's own value.
     *
     * The only way to resolve a location's multiplier, deliberately. Two
     * different things look alike in [HitLocation.byType] -- a real damage
     * type the row does not list (burning on the Neck, which the book leaves
     * at x1) and a key that is not a damage type at all (a typo, or GURPS's
     * "pi-"). [typeMultiplier] runs FIRST so the second is refused; only then
     * does an unlisted type fall back (Codex).
     *
     * **"pi+" at the Vitals is that fallback, and it is a reading.** The cell
     * prints "x3 (imp/pi only)" and chapter 5 abbreviates exactly one row
     * "pi" -- plain Piercing -- so the bracket names that row rather than the
     * piercing family, and Large Piercing takes the location's plain x1.
     * GURPS gives the whole family x3 there; following it would triple a
     * rifle hit on a bracket this book wrote narrowly. Reachable only since
     * Appendix A made "pi+" a type at all, and pinned by a named test.
     */
    fun multiplierFor(location: HitLocation?, type: String): Double {
        val ofType = typeMultiplier(type)
        if (location == null) return ofType
        return ofType * (location.byType[type.lowercase()] ?: location.multiplier)
    }

    /** [location]'s to-hit penalty, or null for the Torso, which costs +0. */
    fun modifier(location: HitLocation): Modifier? {
        if (location.penalty == 0) return null
        return Modifier(
            id = ModifierId("wounds:${location.target.lowercase()}"),
            source = ModifierSource.Situational("${location.target} (called shot)"),
            // Retargeted onto the skill being rolled by SkillRoll.
            target = ModTarget.AllRolls,
            effect = ModEffect.Flat(location.penalty),
            layer = Layer.SITUATIONAL,
        )
    }

    /** The three Major Wound rows, in printed order. */
    fun majorWounds(): List<MajorWound> = ContentLoader.majorWound()

    /**
     * The smallest single hit that is a Major Wound at [maxHp] (p. 41-42).
     *
     * The book's sentence is two instructions that do not agree: "more than
     * half your HP (round up)". Taken literally that is "damage > ceil(HP/2)",
     * which at HP 9 means 6 -- and the book's own worked example says a
     * 5-damage strike triggers it. So "round up" is describing what to do with
     * the half, not a ceiling to then exceed, and the test is simply
     * `damage > HP/2`.
     *
     * **The example is an ODD total, so it does not settle even ones** (Codex).
     * Two readings survive it: `damage >= ceil(HP/2)`, which at HP 10 makes 5
     * a Major Wound, and `damage > HP/2`, which needs 6. The second is taken,
     * for two reasons:
     *
     *  - At HP 10 a 5-damage hit deals EXACTLY half, and the sentence asks for
     *    more than half. The other reading contradicts the words it came from.
     *  - GURPS 4e, which this rulebook is derived from, defines a major wound
     *    as damage over 1/2 HP -- strictly greater. Where this book's own
     *    phrasing is ambiguous, the parent rule breaks the tie the same way.
     *
     * It is still a reading rather than the book's word, so it is recorded
     * here and pinned by a named test rather than left to be rediscovered.
     */
    fun majorWoundThreshold(maxHp: Int): Int {
        require(maxHp >= 1) { "HP must be at least 1; was $maxHp" }
        return Math.floorDiv(maxHp, 2) + 1
    }

    /** Whether one hit of [damage] is a Major Wound at [maxHp]. */
    fun isMajorWound(damage: Int, maxHp: Int): Boolean = damage >= majorWoundThreshold(maxHp)

    /**
     * The row an HT [roll] lands on.
     *
     * Read by OUTCOME first and only then by margin. A 17 always fails, even
     * against a target it beat on the dice, so a margin-first reading reports
     * the book's own failure rows as successes -- the slip [FrightCheck] and
     * [RitualResolution] each made once before.
     */
    fun majorWoundFor(roll: RollResult): MajorWound {
        val rows = majorWounds()
        if (roll.outcome == Outcome.SUCCESS || roll.outcome == Outcome.CRITICAL_SUCCESS) {
            return rows[0]
        }
        val by = rows[2].failBy ?: error("the 'Fail by' row prints no margin")
        // margin is target - total, so a failure's margin is negative.
        return if (-roll.margin >= by) rows[2] else rows[1]
    }

    /** "x3", "x1.5" -- a whole multiplier prints without a decimal tail. */
    fun render(multiplier: Double): String =
        if (multiplier == Math.floor(multiplier)) "×${multiplier.toInt()}" else "×$multiplier"
}
