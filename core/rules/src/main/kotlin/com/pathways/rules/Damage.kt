package com.pathways.rules

import com.pathways.model.DiceExpr
import com.pathways.model.Outcome
import com.pathways.rules.content.ContentLoader
import com.pathways.rules.content.UnarmedAttack

/**
 * What an unarmed attack does when it lands (Ch. 5, p. 38-39).
 *
 * This is the first caller [ContentLoader.thrustSwing] has had: the table has
 * been loaded and tested since M0 with nothing reading it, because until the
 * attack dialog existed there was no roll to attach damage to.
 *
 * Damage is a formula in three parts -- a base off the ST table, the attack's
 * own modifier, and a damage type -- and [UnarmedAttack] carries all three
 * already, parsed by the extractor. What is left for here is the arithmetic:
 * look the base up against ST, add the modifiers, keep the type.
 *
 * Wounding multipliers (cut x1.5, imp x2) are NOT applied. They belong to the
 * target's side of the hit, along with DR, and every unarmed attack in this
 * book is crushing anyway -- pinned by [DamageTest] and by the extractor.
 */
object Damage {

    /** Boxing's bonus caps here "without Trained by a Master" (p. 38). */
    const val MAX_BOXING_BONUS = 2

    /**
     * [damage] thrown. The book prints no minimum, so none is invented; a
     * total below zero is no damage, never a heal.
     */
    fun roll(damage: DamageRoll, source: RandomSource): DamageResult {
        val faces = List(damage.dice.count) { source.roll(damage.dice.sides) }
        source.thrown(faces, null)
        return DamageResult(faces, (faces.sum() + damage.dice.modifier).coerceAtLeast(0), damage.type)
    }

    /**
     * Ch. 2's "A critical success on an attack roll deals double damage"
     * (book lines 150 and 280, `core_mechanic.json`). A reading: the ROLLED
     * total doubles, before DR and the wounding multiplier, which the target's
     * side applies to whatever this prints. Asked per attack, so each roll of
     * a Rapid Strike or an All-Out Double reads its own dice.
     */
    fun onAttack(thrown: DamageResult, attack: Outcome): DamageResult =
        if (attack == Outcome.CRITICAL_SUCCESS) thrown.copy(total = thrown.total * 2, doubled = true) else thrown

    /** The ST column an unarmed attack rolls off. */
    fun thrust(st: Int): DiceExpr = rowFor(st).thrust

    /** Present for completeness: no unarmed attack in this book swings. */
    fun swing(st: Int): DiceExpr = rowFor(st).swing

    private fun rowFor(st: Int) = ContentLoader.thrustSwing()[st]
        ?: error(
            "the thrust/swing table has no row for ST $st; it runs " +
                "${ContentLoader.thrustSwing().keys.min()}..${ContentLoader.thrustSwing().keys.max()}",
        )

    /** The listed attacks for [skill], empty for one with no table (Wrestling). */
    fun attacksFor(skill: String): List<UnarmedAttack> =
        ContentLoader.unarmedAttacks().filter { it.skill.equals(skill, ignoreCase = true) }

    /**
     * Boxing's damage bonus: "+1 to all Boxing attacks at DX+1; +2 at DX+2
     * (max +2 without Trained by a Master)".
     *
     * Measured in levels of Boxing above DX, not in DX itself -- a boxer with
     * DX 12 and Boxing 12 is at DX+0 and earns nothing, while DX 10 and Boxing
     * 12 earns the full +2.
     */
    fun boxingBonus(dx: Int, boxingLevel: Int): Int =
        (boxingLevel - dx).coerceIn(0, MAX_BOXING_BONUS)

    /**
     * What [attack] rolls for damage at [st], or null when it rolls none.
     *
     * Null is Shove, whose cell reads "Special": it knocks the target down
     * instead of wounding, and returning 0 damage would be a claim the book
     * does not make. [bonus] is Boxing's, from [boxingBonus]; Brawling has
     * none and Wrestling has no attacks here at all.
     */
    fun forAttack(attack: UnarmedAttack, st: Int, bonus: Int = 0): DamageRoll? {
        if (!attack.wounds) return null
        require(bonus >= 0) { "a damage bonus cannot be negative: $bonus" }
        val base = when (attack.base) {
            "thr" -> thrust(st)
            "sw" -> swing(st)
            else -> error("unknown damage base '${attack.base}' on ${attack.attack}")
        }
        return DamageRoll(
            dice = base.copy(modifier = base.modifier + attack.modifier + bonus),
            type = attack.type ?: error("${attack.attack} has a base but no damage type"),
        )
    }
}

/**
 * One attack's damage, ready to roll: "1d-1 cr".
 *
 * [DiceExpr] deliberately allows a zero or negative total -- this rulebook
 * prints no minimum-damage rule, and inventing one here would quietly make
 * weak attackers stronger than the book says they are.
 */
data class DamageRoll(val dice: DiceExpr, val type: String) {
    fun render(): String = "${dice.render()} $type"
}

/** A thrown [DamageRoll]: the faces, so the GM can read the throw back, and the total. */
data class DamageResult(
    val faces: List<Int>,
    val total: Int,
    val type: String,
    /** A critical hit's ×2 is in [total] ([Damage.onAttack]). */
    val doubled: Boolean = false,
)
