package com.pathways.rules

import com.pathways.model.Attr
import com.pathways.model.ControllingAttribute
import com.pathways.model.Layer
import com.pathways.model.ModEffect
import com.pathways.model.ModTarget
import com.pathways.model.Modifier
import com.pathways.model.ModifierId
import com.pathways.model.ModifierSource
import com.pathways.model.SkillKey
import com.pathways.model.Trait
import com.pathways.rules.content.BurnablePool
import com.pathways.rules.content.ContentLoader

/** Which pool a roll may be burned with, if any (Ch. 2). */
enum class BurnKind { PHYSICAL, MYSTICAL }

/** When a character may burn a pool: only in combat, or at any time. */
enum class BurnWhen { IN_COMBAT, ALWAYS }

/**
 * Burning FP or SPI to modify a roll (Ch. 2, v6.9i book lines 459-468).
 *
 * "Spend 1 FP to gain +1 on any roll based on ST, DX, or HT ... You must
 * declare this before the roll." "Spend 1 SPI to gain +1 on any SPI-based or
 * supernatural skill roll. You must declare this before the roll." "You
 * cannot spend both FP and SPI on the same roll."
 *
 * **v6.9i rewrote this rule, and the app follows the new text.** v6.9d let a
 * player push AFTER seeing the dice, capped FP at +5 per roll, and printed
 * "Occultism vs. Beyonder phenomena" as an SPI example. v6.9i prints none of
 * that: the burn is declared before the roll, has no cap, and is limited by
 * WHEN instead -- FP in combat needs Fit or Very Fit, FP out of combat needs
 * Very Fit, and SPI only in combat. So the burn is a [modifier] on the target,
 * not a re-reading of a roll already made.
 *
 * The app tracks no combat state, so [whenAllowed] says which case the burn
 * needs and the screen labels its control with it; the player declares it.
 *
 * Ch. 9's ritual and Ch. 10's divination SPI spend are their own rules (the
 * owner's ruling 2: they work out of combat), charged by those dialogs.
 */
object EffortBurn {

    /** Both printed pools, in printed order. */
    fun all(): List<BurnablePool> = ContentLoader.effortBurn()

    fun pool(kind: BurnKind): BurnablePool =
        all().firstOrNull { it.kind.equals(kind.name, ignoreCase = true) }
            ?: error("the book prints no ${kind.name.lowercase()} burn")

    /**
     * Which pool [key] may be burned with, or null when the book names
     * neither.
     *
     * IQ, Will and Per skills fall in that gap: the FP row lists ST, DX and
     * HT and the SPI row lists SPI-based and supernatural rolls. Null rather
     * than a default, so a Research roll is not pushed with fatigue on the
     * strength of the book saying nothing.
     */
    fun kindFor(key: SkillKey): BurnKind? = when (val ca = key.attr) {
        is ControllingAttribute.Buyable ->
            if (ca.attr in PHYSICAL_ATTRS) BurnKind.PHYSICAL
            else if (key.name in SUPERNATURAL_SKILLS) BurnKind.MYSTICAL
            else null
        ControllingAttribute.Spi -> BurnKind.MYSTICAL
        else -> null
    }

    /**
     * The supernatural skills the SPI row reaches although they are not
     * SPI-based: ch. 3's "Spiritual Skills in Detail" (book lines 2404-2410)
     * lists exactly these two, both IQ skills.
     */
    val SUPERNATURAL_SKILLS = setOf(Conditions.RITUAL_SKILL, "Divination Arts")

    /**
     * When a holder of [traits] may burn [kind], or null when never.
     *
     * Read from the row: a trait in `outOfCombatTraits` allows it at any time;
     * otherwise a trait in `combatTraits`, or an empty `combatTraits`, allows
     * it in combat only.
     */
    fun whenAllowed(kind: BurnKind, traits: List<Trait>): BurnWhen? {
        val row = pool(kind)
        fun holdsAny(names: List<String>) = names.any { TraitNames.levelOf(traits, it) != null }
        return when {
            row.outOfCombatTraits?.let(::holdsAny) == true -> BurnWhen.ALWAYS
            row.combatTraits.isEmpty() || holdsAny(row.combatTraits) -> BurnWhen.IN_COMBAT
            else -> null
        }
    }

    /** The bonus [points] spent on [kind] buys. The book prints no cap. */
    fun bonusFor(kind: BurnKind, points: Int): Int {
        require(points >= 0) { "cannot burn a negative number of points; was $points" }
        return Math.multiplyExact(points, pool(kind).perPoint)
    }

    /**
     * The declared burn as a modifier on the roll's target, or null at zero
     * points. Retargeted onto the skill by [SkillRoll.targetFor].
     */
    fun modifier(kind: BurnKind, points: Int): Modifier? {
        val bonus = bonusFor(kind, points)
        if (bonus == 0) return null
        return Modifier(
            id = idFor(kind),
            source = ModifierSource.Situational("Burned ${pool(kind).pool}"),
            target = ModTarget.AllRolls,
            effect = ModEffect.Flat(bonus),
            layer = Layer.SITUATIONAL,
        )
    }

    /** The ledger name a burned point appears under. */
    fun idFor(kind: BurnKind) = ModifierId("burn:${pool(kind).pool.lowercase()}")

    /**
     * "You cannot spend both FP and SPI on the same roll." Refused rather
     * than silently preferring one: a caller handed a quietly dropped pool
     * would charge for it and not receive it.
     */
    fun requireOnePool(fp: Int, spi: Int) {
        require(fp == 0 || spi == 0) {
            "the book allows one pool per roll, not both; was $fp FP and $spi SPI"
        }
    }

    /**
     * Whether a roll of [key] is one of the book's "Physical Rolls": the FP
     * row's "any roll based on ST, DX, or HT". [kindFor]'s answer, for a rule
     * that scopes itself to those rolls and spends nothing (6gv).
     */
    fun isPhysical(key: SkillKey): Boolean = kindFor(key) == BurnKind.PHYSICAL

    private val PHYSICAL_ATTRS = setOf(Attr.ST, Attr.DX, Attr.HT)
}
