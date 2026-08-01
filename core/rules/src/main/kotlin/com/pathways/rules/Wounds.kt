package com.pathways.rules

import com.pathways.model.Layer
import com.pathways.model.ModEffect
import com.pathways.model.ModTarget
import com.pathways.model.Modifier
import com.pathways.model.ModifierId
import com.pathways.model.ModifierSource
import com.pathways.rules.content.ContentLoader
import com.pathways.rules.content.HitLocation
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
     * Only the four the book abbreviates resolve. "pi-" and "pi+" are GURPS's
     * spelling of Piercing (small) and Large Piercing and appear nowhere in
     * this rulebook, so they are refused rather than quietly read as x1 --
     * silently halving a small-piercing hit is exactly the kind of wrong
     * answer a default produces.
     */
    fun typeMultiplier(type: String): Double {
        val row = damageTypes().firstOrNull { it.abbreviation.equals(type, ignoreCase = true) }
        require(row != null) {
            "no damage type is abbreviated '$type'; this book prints " +
                damageTypes().mapNotNull { it.abbreviation }.joinToString()
        }
        return row.multiplier
    }

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

    /** "x3", "x1.5" -- a whole multiplier prints without a decimal tail. */
    fun render(multiplier: Double): String =
        if (multiplier == Math.floor(multiplier)) "×${multiplier.toInt()}" else "×$multiplier"
}
