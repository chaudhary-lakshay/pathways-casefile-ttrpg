package com.pathways.rules

import com.pathways.model.DiceExpr
import com.pathways.model.SkillKey
import com.pathways.rules.content.CataloguedWeapon
import com.pathways.rules.content.ContentLoader
import com.pathways.rules.content.WeaponMode

/**
 * The weapons this book prints (Ch. 8, p. 80).
 *
 * **It does print them.** Four milestones' worth of notes said otherwise --
 * "this book prints no weapon table" is still the refusal message inside
 * [Ranged.modifiers] -- and three features were deferred on that premise:
 * armed parry, weapon damage, and Aim, whose modifier is "+ weapon's Acc".
 * The table was in chapter 8, which coverage.py reported at 0.0% because
 * nothing had ever read it.
 *
 * [Damage] is the unarmed half of the same job and is left alone: it answers
 * for the Brawling and Boxing tables, this answers for the catalogue. Both
 * roll off [ContentLoader.thrustSwing], which is the only thing they share.
 */
object Weapons {

    /** Chapter 5's parry formula (p. 37): "Weapon Skill / 2 + 3". */
    private const val PARRY_BASE = 3

    /** The nine rows, in printed order. */
    fun all(): List<CataloguedWeapon> = ContentLoader.weapons()

    /**
     * The row called [name]. Refused rather than defaulted: arming a character
     * with a weapon the book does not print is a caller bug, not a knife.
     */
    fun named(name: String): CataloguedWeapon =
        all().firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?: throw IllegalArgumentException(
                "no weapon named '$name'; the book prints " + all().joinToString { it.name },
            )

    /**
     * Every weapon [key] can be used with.
     *
     * Matched on the specialization too, not the bare name: the sheets print
     * "Guns (Revolver)" and "Guns (Rifle)" and so does this table, and
     * resolving by name alone would hand a revolver shooter a bolt-action
     * rifle -- the same defect the bought-skill picker already had once.
     */
    fun forSkill(key: SkillKey): List<CataloguedWeapon> {
        val printed = key.specialization?.let { "${key.name} ($it)" } ?: key.name
        return all().filter { it.skill.equals(printed, ignoreCase = true) }
    }

    /**
     * The level [weapon] is wielded at, or null when no skill this character
     * has arms it.
     *
     * The HIGHEST among every matching skill, never the first the map yields:
     * p. 85 makes two rows of one name at two difficulties into two different
     * skills, and five of this book's potion grants produce exactly that, so
     * iteration order would otherwise decide the number.
     *
     * Matched through [forSkill], the join that already exists and the one
     * that reads the specialization. Going the other way would mean parsing
     * "Guns (Revolver)" back into a [SkillKey], inventing a difficulty and a
     * controlling attribute the catalogue never printed.
     *
     * A weapon no skill arms answers null rather than 0: a borrowed
     * broadsword is still CARRIED, it just cannot be parried with, and 0
     * would be a parry of 3.
     */
    fun levelFor(weapon: CataloguedWeapon, skillLevels: Map<SkillKey, Int>): Int? =
        skillLevels.entries
            .filter { (key, _) -> forSkill(key).any { it.name == weapon.name } }
            .maxOfOrNull { it.value }

    /**
     * Whether [weapon] is fired rather than held.
     *
     * The table has no such column, so the only signal in it is the Range
     * cell: a melee weapon prints a reach letter ("C", "C/1") and a firearm
     * prints metres. **A reading**, and the reason it is not a column in
     * `weapons.json` -- inventing one there would make it look extracted.
     */
    fun isRanged(weapon: CataloguedWeapon): Boolean = weapon.halfRange != null

    /**
     * Whether [weapon] can parry at all. The same reading as [isRanged], under
     * the name of the other question it answers: nothing in this book is both
     * thrown and parried, so one field settles both.
     */
    fun canParry(weapon: CataloguedWeapon): Boolean = !isRanged(weapon)

    /**
     * What [weapon] parries at, or null when it parries at nothing.
     *
     * Chapter 5 prints one formula for every weapon -- "Weapon Skill / 2 + 3"
     * -- with no per-weapon modifier anywhere in this book, so
     * [com.pathways.model.Weapon.parryMod] has nothing to be set from here and
     * [ParryBlock.parryFor] is left for the sheets that carry one.
     *
     * Math.floorDiv, never "/": the two differ on a negative skill level, and
     * this defect class has recurred three times in this codebase.
     */
    fun parryFor(weapon: CataloguedWeapon, skillLevel: Int): Int? =
        if (canParry(weapon)) Math.floorDiv(skillLevel, 2) + PARRY_BASE else null

    /**
     * Whether chapter 5 can price [mode]'s damage type at all.
     *
     * False for exactly one row today: the Bolt-Action Rifle's "pi+", which
     * chapter 8 prints and chapter 5's Optional Wounding Multipliers table
     * never abbreviates. [Wounds.typeMultiplier] refuses it, correctly -- the
     * fix is not to add the abbreviation to a chapter that does not print it,
     * so a caller asks this first and says so instead of crashing.
     *
     * The two chapters disagree about the guns as well: chapter 5's Large
     * Piercing row gives "Hunting rifles" as its example while chapter 8
     * prints the Hunting Rifle as plain "pi". Recorded, not reconciled.
     */
    fun hasWoundingMultiplier(mode: WeaponMode): Boolean = Wounds.knowsType(mode.type)

    /**
     * What [mode] rolls at [st], with [bonus] added to the total.
     *
     * ST reaches a swing or a thrust and stops there: a firearm's dice are
     * printed outright ("2d pi") and a stronger arm does not push a bullet
     * harder. [bonus] is All-Out Attack (Strong)'s +2 and anything else
     * [Attacks.damageBonus] composes -- the book scopes Determined to melee
     * and ranged separately and deliberately leaves Strong unscoped, so it is
     * not filtered out here for a gun.
     */
    fun damageFor(
        weapon: CataloguedWeapon,
        mode: WeaponMode,
        st: Int,
        bonus: Int = 0,
    ): DamageRoll {
        require(mode in weapon.damage) {
            "'${mode.printed}' is not one of ${weapon.name}'s attacks: " +
                weapon.damage.joinToString { it.printed }
        }
        val base = when (mode.base) {
            "sw" -> Damage.swing(st)
            "thr" -> Damage.thrust(st)
            else -> DiceExpr.parse(mode.base)
        }
        return DamageRoll(
            dice = base.copy(modifier = base.modifier + mode.modifier + bonus),
            type = mode.type,
        )
    }
}
