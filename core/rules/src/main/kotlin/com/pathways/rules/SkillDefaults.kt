package com.pathways.rules

import com.pathways.model.Character
import com.pathways.model.ControllingAttribute
import com.pathways.model.SkillKey
import com.pathways.model.Trait
import com.pathways.rules.content.CataloguedWeapon
import com.pathways.rules.content.ContentLoader
import com.pathways.rules.content.SkillDefinition

/**
 * What a character rolls for a skill they do not have (Ch. 2, p. 10).
 *
 * "You can attempt most skills even without training. This is called using
 * the default." The table is [SkillLadder.defaultFor], which had no
 * production caller from M0 until this object: every screen rolled only what
 * a sheet owned, and three of them said an untrained roll was "the GM's
 * call" while the book printed the number.
 *
 * **The table is chapter 2's, not Appendix A's.** The appendix restates it as
 * one row, "Attribute -4 to -6", which is the table's two ends and not a
 * range a GM picks inside; a summary may be checked against and never
 * overrules (6v).
 *
 * **A skill's own line beats the table**, which is why this is an object and
 * not a call to the ladder. Three skills print one:
 *
 * | Skill | The table | Its own line |
 * |---|---|---|
 * | Brawling (DX/E) | DX-4 | "Default: DX-2 if untrained" (p. 38) |
 * | Wrestling (DX/A) | DX-5 | "No default" (p. 39) |
 * | Divination Arts (SPI/H) | SPI-6 | "defaults to Ritualistic Magic-2 ... at 12+" (p. 54) |
 *
 * Boxing's "Default DX-5" agrees with the table and needs no row.
 *
 * **Divination Arts is a reading, and it is the one the Divine tile already
 * made.** Line 3559 does not say "only", so SPI-6 beside it is not excluded
 * by any sentence; it is withheld here because [Divination.crossDefaultFor]
 * has refused it since 6az -- "what the book declines to say is whether they
 * may attempt it at all" -- and a plain roll offering what the tile refuses
 * would be two answers to one question. Asked of that resolver, never
 * recomputed.
 *
 * **Not enforced: "any skill the GM rules requires specialist knowledge"**
 * (p. 10). That is a ruling per table, and this app records none, so the
 * screen prints the sentence and offers the roll.
 */
object SkillDefaults {

    const val BRAWLING = "Brawling"
    const val PHYSICIAN = "Physician"
    const val MEDICAL_TRAINING = "Medical Training (Informal)"

    /** "Use Physician at IQ-2 without purchasing the skill" (book 861). */
    const val MEDICAL_TRAINING_PENALTY = -2
    const val WRESTLING = "Wrestling"

    /** "Default: DX-2 if untrained" (p. 38, and the p. 39 summary). */
    const val BRAWLING_PENALTY = -2

    /**
     * The potion blocks whose text prints "no default penalty" for a weapon,
     * as (pathway, block name).
     *
     * **Named on the Skills list, and never applied there.** Each is about a
     * WEAPON -- "every weapon is treated as known at effective DX" -- and
     * which skill a weapon uses is a property of the weapon in hand, which a
     * list of skills does not ask about. The Attack dialog does have one in
     * hand, and [mastered] applies the two Twilight Giant lines there (6eu).
     */
    val WEAPON_LINES: Set<Pair<String, String>> get() = WEAPON_SCOPES.keys

    /**
     * What each line covers, in the block's own words. They are not one
     * rule: two say "any weapon" and the third only "any farming or gardening
     * tool as a weapon", and a note that flattened them would promise the
     * Harvest Priest the Warrior's exemption (the diff review's MEDIUM).
     */
    private val WEAPON_SCOPES = mapOf(
        ("Twilight Giant" to "Warrior") to "any weapon",
        ("Twilight Giant" to "Weapon Master") to "any weapon",
        ("Mother" to "Harvest Priest") to "a farming or gardening tool used as a weapon",
    )

    /**
     * What [offered] withholds, for the screen that lists it. A skill absent
     * from a long list reads as an oversight unless the list says otherwise.
     */
    const val NOT_LISTED =
        "Not listed: Very Hard skills and $WRESTLING, which the book gives no " +
            "default, and ${Divination.SKILL} unless ${Divination.SIBLING_SKILL} " +
            "is 12 or more, where its own line stands in for the table's, as on " +
            "the Divine tile. The GM may rule that a skill needs specialist " +
            "knowledge before it can be attempted at all; this app records no " +
            "such ruling."

    /**
     * What a roll from the list does NOT do, and where to go instead. The
     * Attack dialog takes the untrained skills it can make an attack with
     * ([attacks], 6eu); the Defend dialog takes Acrobatics, for the
     * Acrobatic Dodge, and no default for a parry (6ev).
     */
    const val PLAIN_ROLL =
        "A roll from this list is the skill alone. For an untrained punch, or " +
            "a weapon you have no skill with, use Attack, which carries the " +
            "damage. For an Acrobatic Dodge use Defend, which carries its ±2 " +
            "to the Dodge row."

    /** [WEAPON_LINES] as the sentence the list prints, or null for a character with none. */
    fun weaponNote(pathway: String, sequence: Int): String? =
        weaponLinesFor(pathway, sequence).takeIf { it.isNotEmpty() }?.let { names ->
            val lines = names.joinToString("; ") { name ->
                "$name's \"no default penalty\" with ${WEAPON_SCOPES.getValue(pathway to name)}"
            }
            "Not applied here: $lines. This list does not know what is in hand."
        }

    /**
     * The rows of [untrained] the Attack dialog can make an attack with: an
     * unarmed table of its own ([Damage.attacksFor]) or a row in chapter 8's
     * weapon table ([Weapons.forSkill]).
     *
     * **The attacks supported HERE, not a rule about who may attack** (the
     * design review's wording). The book prints a COMBAT SKILLS banner of
     * nineteen and a default for a Shield Bash, and neither decides this
     * list: a skill with no damage line this app can show is rolled from the
     * Skills tab, where a to-hit number is all there is to show. An OWNED
     * skill is offered by its banner instead ([ownedAttacks]). [owned] is the
     * character's skill levels, which [attackWeapons] reads.
     */
    fun attacks(untrained: List<Untrained>, carried: List<CataloguedWeapon>, owned: Map<SkillKey, Int>): List<Untrained> =
        untrained.filter {
            Damage.attacksFor(it.key.name).isNotEmpty() || attackWeapons(it.key, it.level, carried, owned).isNotEmpty()
        }

    /**
     * The weapons the Attack dialog arms [key] with. An UNTRAINED skill arms
     * only what is [carried]: the untrained list names every weapon skill in
     * the book, and play session 1 offered a Private Investigator a
     * Bolt-Action Rifle they never had. An OWNED skill still arms the whole
     * catalogue, because no premade prints a weapon row (6eb) and the sheet's
     * own skill is its evidence of one; that is not this fix's to change.
     *
     * [untrainedAt] is the untrained row's level, null for an owned skill. An
     * untrained row also skips a weapon an [owned] skill already wields at
     * that level or better ([Weapons.levelFor]): play session 2 offered Guns
     * (Pistol) at 6 for the revolver its holder fired at 12.
     */
    fun attackWeapons(
        key: SkillKey,
        untrainedAt: Int?,
        carried: List<CataloguedWeapon>,
        owned: Map<SkillKey, Int>,
    ): List<CataloguedWeapon> =
        Weapons.forSkill(key).filter { weapon ->
            untrainedAt == null ||
                weapon in carried && (Weapons.levelFor(weapon, owned) ?: Int.MIN_VALUE) < untrainedAt
        }
            // Carried first: the dialog arms the first, and play session 5's
            // carried Hunting Rifle lost to the catalogue's Bolt-Action.
            .sortedBy { it !in carried }

    /** The banner Ch. 3 prints nineteen skills under, every weapon among them. */
    const val COMBAT_SKILLS = "COMBAT SKILLS"

    /**
     * The OWNED skills the Attack dialog offers: the ones the book files under
     * [COMBAT_SKILLS], read through [TraitEffects.categoryOf].
     *
     * The banner and not [attacks]' damage line, which is a different
     * question: ten banner rows (Shortsword, Polearm, Shield, Guns (Pistol)
     * among them) print no damage this app can show and are still attacks,
     * with the maneuvers, Deceptive Attack and called shot that go with one.
     * Within the banner nothing is curated, so Fast-Draw stays (6i's refusal
     * to curate a banner's rows is the half of it that survives).
     *
     * So an untrained banner skill with no damage line (Shortsword at its
     * default) is not offered while the same skill owned is. That asymmetry
     * is the two questions' and is deliberate.
     *
     * What it loses: a skill outside the banner that a GM rules can strike,
     * an Acrobatics kick, is a plain roll on the Skills tab. No skill outside
     * the banner has a damage line or a weapon row.
     */
    fun ownedAttacks(skillLevels: Map<SkillKey, Int>): Map<SkillKey, Int> =
        skillLevels.filterKeys { TraitEffects.categoryOf(it) == COMBAT_SKILLS }

    /**
     * The Twilight Giant's weapon line, which a walked chain holds one stage
     * of: the Pugilist "upgrades" the Warrior's, and Weapon Master's block
     * calls itself "a strict upgrade to and replacement of the Warrior's
     * Combat Mastery". The Weapon Master "requires no warm-up".
     */
    enum class WeaponMastery(
        val block: String,
        /** The block whose "no default penalty" the DX floor quotes. */
        val quoted: String,
        private val meleeWarmUp: Boolean,
        private val rangedWarmUp: Boolean,
    ) {
        WARRIOR("Warrior", "Warrior", meleeWarmUp = true, rangedWarmUp = true),

        /**
         * Seq 8's reinforced Combat Mastery: "treats any weapon as known at
         * effective DX immediately (no warm-up needed) ... limited to melee
         * weapons only. Ranged weapons still need one use to reach full
         * proficiency (as the Warrior's original ability)". It changes no
         * number -- the Warrior's line already wields at DX -- so the floor
         * still quotes the Warrior, and only the melee warm-up goes. Its text
         * never says "no default penalty", which is how the keyword census
         * behind [WEAPON_LINES] missed it (6ey).
         */
        PUGILIST("Pugilist", "Warrior", meleeWarmUp = false, rangedWarmUp = true),
        WEAPON_MASTER("Weapon Master", "Weapon Master", meleeWarmUp = false, rangedWarmUp = false),
        ;

        /** Where a level [mastered] lifted came from, for the roll's label. */
        val source: String get() = "DX, $quoted's \"no default penalty\""

        /**
         * The clause of the line this app does not apply to the weapon in
         * hand, as the sentence a screen prints, or null. Only the Warrior's
         * "Weapons used repeatedly in a session are treated as known at
         * default +2" is left, and the Pugilist keeps it for a ranged weapon.
         *
         * **Refused for a reason in the book, not in this app** (6fj). The
         * old reason, "this app does not count uses", was false as a
         * reason: a GM could declare repeated use, as other circumstances
         * are declared. What no declaration fixes is the number. The
         * Warrior's example fires "at DX level immediately, but needs
         * several shots before reaching full proficiency (default +2 after
         * repeated use)", which puts it ABOVE DX (5800-5801). The Weapon
         * Master, "a strict upgrade to and replacement of the Warrior's
         * Combat Mastery" (7569-7570), stops at "effective DX (no default
         * penalty)" (7564). A warmed-up Warrior at DX+2 would outdo the line
         * that replaces his. The DX floor stays: the Warrior's block says DX
         * at first use twice, and the later blocks print no lower number.
         */
        fun unappliedFor(isRanged: Boolean): String? =
            WARM_UP.takeIf { if (isRanged) rangedWarmUp else meleeWarmUp }
    }

    private const val WARM_UP =
        "Not applied here: Warrior's \"default +2\" after repeated use. " +
            "Its own example puts that above DX; the Weapon Master, " +
            "\"a strict upgrade\", stops at DX."

    /** The mastery a character at [sequence] of [pathway] holds, or null. */
    fun masteryFor(pathway: String, sequence: Int): WeaponMastery? {
        val walked = ContentLoader.pathways()
            .filter { it.pathway == pathway && it.sequence >= sequence }
            .map { it.name }
        return WeaponMastery.entries.lastOrNull { it.block in walked }
    }

    /**
     * The level [weapon] is wielded at for its PARRY, or null where nothing
     * wields it: the highest skill of [skillLevels] that arms it
     * ([Weapons.levelFor]), floored at [dx] in a master's hand.
     *
     * Line 2468 prints "Weapon Skill ÷ 2 + 3", and a master's weapon is
     * "treated as known at effective DX", so DX is that operand and a
     * master's weapon parries untrained (6ey; the design review: SHIP). A
     * non-master's still needs a skill: the armed cell prints no "or DX",
     * and the unarmed cell, the one place an untrained parry operand is
     * printed (2473), prints DX and not Brawling's DX-2 default, so no line
     * halves a default (6gz). The highest skill, not the one an attack picked, so the
     * Attack and Defend dialogs print one parry for one weapon.
     */
    fun wieldedAt(weapon: CataloguedWeapon, skillLevels: Map<SkillKey, Int>, mastery: WeaponMastery?, dx: Int): Int? {
        val own = Weapons.levelFor(weapon, skillLevels)
        if (mastery == null) return own
        return maxOf(own ?: dx, dx)
    }

    /**
     * The level a weapon in a master's hand lifts [level] to, or null where
     * the line changes nothing.
     *
     * "Every weapon is treated as known at effective DX (no default
     * penalty)": [dx], as a FLOOR. An untrained skill is always below it, and
     * so is one bought a rung under DX -- one point in an Average weapon
     * skill -- which the first version of this left there, so a Warrior who
     * trained with the axe swung it worse than one who never had (the diff
     * review's MEDIUM). "Every weapon" is the book's phrase.
     *
     * Not a roll at a default any more, so Versatile's +1 -- "any time you
     * use a skill at default" -- does not come along: the answer is [dx].
     *
     * **[weaponInHand], not "this skill has a weapon".** Brawling arms a
     * truncheon and opens on a fist, and the line is about the truncheon (a
     * design review's HIGH).
     */
    fun mastered(level: Int, weaponInHand: Boolean, mastery: WeaponMastery?, dx: Int): Int? =
        dx.takeIf { weaponInHand && mastery != null && level < dx }

    /**
     * The Harvest Priest's line, for a screen with a weapon on it: "any
     * farming or gardening tool as a weapon at no default penalty". Chapter
     * 8's table marks no row as one, so which weapon qualifies is the GM's.
     */
    fun toolNote(pathway: String, sequence: Int): String? =
        weaponLinesFor(pathway, sequence).firstOrNull { it == HARVEST_PRIEST }?.let {
            "Not applied here: $it's \"no default penalty\" with a farming or " +
                "gardening tool. This app does not know which weapon is one."
        }

    private const val HARVEST_PRIEST = "Harvest Priest"

    /** One skill a character may attempt untrained, and how the number was reached. */
    data class Untrained(val key: SkillKey, val level: Int, val source: String)

    /**
     * Every printed skill [owned] does not hold and the book gives a default,
     * in the catalogue's order.
     *
     * [attributeLevel] answers for the character as PLAYED -- after potions --
     * the same levels a bought skill is rolled from. [defaultBonus] is
     * [TraitEffects.defaultBonus], added once. [traits] are the same list
     * that bonus was read from; a level one of them grants ([grantedBy])
     * stands in for the default and takes no bonus.
     *
     * Owned is the same name and specialization at ANY difficulty: five
     * potion grants disagree with chapter 3 about a difficulty, and the
     * catalogue prints no name and specialization twice, so a difficulty
     * cannot be what tells two of its rows apart. See [covers] for the two
     * spellings a sheet prints that the book does not.
     */
    fun offered(
        owned: Map<SkillKey, Int>,
        attributeLevel: (ControllingAttribute) -> Int,
        defaultBonus: Int,
        traits: List<Trait>,
    ): List<Untrained> {
        val printed = ContentLoader.skills()
        val suffix = if (defaultBonus != 0) ", +$defaultBonus ${TraitEffects.VERSATILE}" else ""
        return printed
            .filter { row ->
                val onlyRow = printed.count { it.name == row.name } == 1
                owned.keys.none { covers(it, row, onlyRow) }
            }
            .mapNotNull { skill ->
                val key = SkillKey(skill.name, skill.specialization, skill.attr, skill.difficulty)
                grantedBy(skill, traits, attributeLevel)?.let { (level, source) ->
                    return@mapNotNull Untrained(key, level, source)
                }
                val (level, source) = defaultOf(skill, owned, attributeLevel, defaultBonus)
                    ?: return@mapNotNull null
                Untrained(key, level, source + suffix)
            }
    }

    /**
     * The level a held trait gives for a skill the character does not own:
     * [MEDICAL_TRAINING]'s "Use Physician at IQ-2 without purchasing the
     * skill", the one catalogue cell that grants a level (6gy).
     *
     * **Not a default, so [TraitEffects.defaultBonus] is not added**: the
     * cell gives a level and never uses the word, as a Warrior's weapon "at
     * effective DX" does not ([mastered]). A reading, not the book's only
     * one: page 10 calls any untrained attempt "using the default", and
     * Brawling's own "Default: DX-2" does take the bonus. It also needs no comparison with
     * the table's row: Physician is Hard, IQ-6, and Versatile's +1 is the
     * only bonus a default takes, so the grant is always the better number.
     * A bought Physician is at least IQ-2 at its first point and is not on
     * this list at all.
     */
    private fun grantedBy(
        skill: SkillDefinition,
        traits: List<Trait>,
        attributeLevel: (ControllingAttribute) -> Int,
    ): Pair<Int, String>? {
        if (skill.name != PHYSICIAN || TraitNames.levelOf(traits, MEDICAL_TRAINING) == null) return null
        return attributeLevel(skill.attr) + MEDICAL_TRAINING_PENALTY to
            "${skill.attr.code}$MEDICAL_TRAINING_PENALTY, $MEDICAL_TRAINING"
    }

    /**
     * Whether holding [owned] is holding the book's [row].
     *
     * The same name and specialization, first. Then the one case where a
     * sheet's spelling and the book's differ and can still only be one
     * skill: the book prints a single row of that name ([onlyRow]), the row
     * is not open, and one of the two prints no specialization -- "History
     * (Occult)" on a sheet against the book's bare History, a bare
     * "Mathematics" against its Mathematics (Applied). Five premades carry
     * one or the other, and each was offered its own skill back untrained.
     *
     * **Drawn from the catalogue's shape, not from the names seen.** An open
     * row is never covered by an instance (Hidden Lore (Demons) is not
     * Hidden Lore (Spirits)), two printed specializations that differ are
     * two skills (Fast-Draw (Revolver) beside the book's (Pistol)), and a
     * placeholder grant -- "Guns (any)", "Survival (any terrain)" -- covers
     * nothing, because the player chose one and this app was not told which.
     * A sheet name the book does not print at all ("Engineer (Steam)" against
     * Engineering) matches nothing either: recorded, never repaired.
     */
    private fun covers(owned: SkillKey, row: SkillDefinition, onlyRow: Boolean): Boolean {
        if (!owned.name.equals(row.name, ignoreCase = true)) return false
        if (owned.specialization.equals(row.specialization, ignoreCase = true)) return true
        return onlyRow && !row.specializable &&
            (owned.specialization == null || row.specialization == null)
    }

    /**
     * [offered], for [character] as played: the digested attributes, pools
     * and skill levels where a potion has been drunk, the purchased ones
     * where none has. The same two sources every bought skill is rolled from,
     * and the same call the sheet's `digestedWith` makes for the same state.
     */
    fun offeredTo(character: Character): List<Untrained> {
        val walked = PotionDigestion.of(character, ContentLoader.pathways())
        val attributes = Derive.attributes(character, walked)
        val secondaries = walked?.secondaries ?: Derive.of(character).secondaries
        val spi = walked?.effectiveSpi ?: character.spi
        return offered(
            owned = walked?.skillLevels ?: Derive.of(character).skillLevels,
            attributeLevel = { Derive.controllingLevel(it, attributes, secondaries, spi) },
            defaultBonus = TraitEffects.defaultBonus(character.traits),
            traits = character.traits,
        )
    }

    /** The level and where it came from, or null where the book gives no default. */
    private fun defaultOf(
        skill: SkillDefinition,
        owned: Map<SkillKey, Int>,
        attributeLevel: (ControllingAttribute) -> Int,
        defaultBonus: Int,
    ): Pair<Int, String>? {
        val attribute = attributeLevel(skill.attr)
        return when (skill.name) {
            WRESTLING -> null
            BRAWLING -> attribute + BRAWLING_PENALTY + defaultBonus to
                "${skill.attr.code}$BRAWLING_PENALTY, the book's own line for $BRAWLING"
            Divination.SKILL -> {
                val sibling = owned.entries
                    .firstOrNull { it.key.name.equals(Divination.SIBLING_SKILL, ignoreCase = true) }
                    ?.value
                val level = Divination.divinationLevelFor(null, sibling, defaultBonus)
                    ?: return null
                level.level to
                    "${Divination.SIBLING_SKILL} $sibling ${Divination.CROSS_DEFAULT_PENALTY}"
            }
            else -> {
                val level = SkillLadder.defaultFor(skill.difficulty, attribute) ?: return null
                level + defaultBonus to "${skill.attr.code}${skill.difficulty.defaultPenalty}"
            }
        }
    }

    /**
     * The [WEAPON_LINES] blocks a character at [sequence] of [pathway] has
     * drunk, earliest first. The Skills list names them as not applied; the
     * Attack dialog asks [masteryFor] and [toolNote] instead.
     */
    fun weaponLinesFor(pathway: String, sequence: Int): List<String> =
        ContentLoader.pathways()
            .filter { it.pathway == pathway && it.sequence >= sequence }
            .sortedByDescending { it.sequence }
            .filter { (it.pathway to it.name) in WEAPON_LINES }
            .map { it.name }
}
