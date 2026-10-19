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
 * | Divination Arts (IQ/H) | IQ-6 | "Default: Divination Arts defaults to SPI-4 (unskilled)" (ch. 8 I) |
 *
 * Boxing's "Default DX-5" agrees with the table and needs no row.
 *
 * **Divination Arts is withheld for the Divine tile.** Its SPI-4 default is
 * a divination, which pays its method's SPI before the roll and takes the
 * question's modifiers; a plain roll from this list would skip both (Codex).
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
     * hand, and [mastered] applies the three Twilight Giant lines there (6eu).
     */
    val WEAPON_LINES: Set<Pair<String, String>> get() = WEAPON_SCOPES.keys

    /**
     * What each line covers, in the block's own words. They are not one
     * rule, and a note that flattened them would promise the Harvest Priest
     * the Warrior's exemption (the diff review's MEDIUM).
     */
    private val WEAPON_SCOPES = mapOf(
        ("Twilight Giant" to "Warrior") to "any weapon at effective DX, DX+2 after repeated use",
        ("Twilight Giant" to "Pugilist") to "a melee weapon at DX+2",
        ("Twilight Giant" to "Weapon Master") to "any weapon at DX+4",
        ("Mother" to "Harvest Priest") to "a farming or gardening tool used as a weapon at no default penalty",
        // Printed in v6.9d too and never applied: chapter 8's table marks no
        // improvised weapon, and the firearm clause is the Abyss chain's own.
        ("Abyss" to "Unwinged Angel (Coldblooded)") to "any firearm at effective DX+1",
    )

    /**
     * What [offered] withholds, for the screen that lists it. A skill absent
     * from a long list reads as an oversight unless the list says otherwise.
     */
    const val NOT_LISTED =
        "Not listed: Very Hard skills and $WRESTLING, which the book gives no " +
            "default, and ${Divination.SKILL}, rolled from the Divine tile. The GM may rule that a skill needs specialist " +
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
                "$name: ${WEAPON_SCOPES.getValue(pathway to name)}"
            }
            "Not applied here: $lines. This list does not know what is in hand."
        }

    /**
     * The rows of [untrained] the Attack dialog can make an attack with: an
     * unarmed table of its own ([Damage.attacksFor]) or a row in chapter 8's
     * weapon table ([Weapons.forSkill]), Shield by its bash.
     *
     * **The attacks supported HERE, not a rule about who may attack** (the
     * design review's wording). The book prints a COMBAT SKILLS banner of
     * nineteen, and it does not decide this list: a skill with no damage
     * line this app can show is rolled from the Skills tab, where a to-hit
     * number is all there is to show. An OWNED skill is offered by its banner
     * instead ([ownedAttacks]). [owned] is the character's skill levels, which
     * [attackWeapons] reads.
     */
    fun attacks(untrained: List<Untrained>, carried: List<CataloguedWeapon>, owned: Map<SkillKey, Int>): List<Untrained> =
        untrained.filter {
            Damage.attacksFor(it.key.name, untrained = true).isNotEmpty() || attackWeapons(it.key, it.level, carried, owned).isNotEmpty()
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
     * of, as v6.9i prints it. The Pugilist "upgrades" the Warrior's, and
     * Weapon Master's block calls itself "a strict upgrade to and replacement
     * of the Warrior's Combat Mastery".
     *
     * Each is a floor over DX for the weapon in hand: [immediateMelee] and
     * [immediateRanged] from the moment it is picked up, and [FULL_PROFICIENCY]
     * once a weapon has been "used repeatedly in a session", where [warmsUp].
     * Warrior: DX, "default +2" after repeated use (5094-5099), which the
     * later blocks name "full proficiency (effective DX+2)". Pugilist: melee
     * "(effective DX+2) immediately", ranged DX until repeated use
     * (5875-5878). Weapon Master: "effective DX+4 immediately, melee and
     * ranged alike, with no warm-up" (6810-6816).
     */
    enum class WeaponMastery(
        val block: String,
        private val immediateMelee: Int,
        private val immediateRanged: Int,
    ) {
        WARRIOR("Warrior", immediateMelee = 0, immediateRanged = 0),
        PUGILIST("Pugilist", immediateMelee = FULL_PROFICIENCY, immediateRanged = 0),
        WEAPON_MASTER("Weapon Master", immediateMelee = 4, immediateRanged = 4),
        ;

        private fun immediate(isRanged: Boolean) = if (isRanged) immediateRanged else immediateMelee

        /** Whether repeated use still raises this hand's level: the dialog asks only then. */
        fun warmsUp(isRanged: Boolean): Boolean = immediate(isRanged) < FULL_PROFICIENCY

        /** The floor over DX for this hand. */
        fun bonusOverDx(isRanged: Boolean, warmedUp: Boolean): Int =
            if (warmedUp && warmsUp(isRanged)) FULL_PROFICIENCY else immediate(isRanged)

        /** Where a level [mastered] lifted came from, for the roll's label. */
        fun sourceFor(isRanged: Boolean, warmedUp: Boolean): String {
            val bonus = bonusOverDx(isRanged, warmedUp)
            val dx = if (bonus == 0) "DX" else "DX+$bonus"
            return "$dx, $block" + if (warmedUp && warmsUp(isRanged)) " after repeated use" else ""
        }
    }

    /** "full proficiency (effective DX+2)", the Warrior's warmed-up level. */
    private const val FULL_PROFICIENCY = 2

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
     * ([Weapons.levelFor]), floored in a master's hand at the line's
     * immediate melee level.
     *
     * Line 2468 prints "Weapon Skill ÷ 2 + 3", and a master's weapon is
     * "treated as known at effective DX" or better, so that floor is the
     * operand and a master's weapon parries untrained (6ey). [warmedUp] is
     * the declaration that this weapon was "used repeatedly in a session"
     * (book 5097), which lifts a Warrior's floor to DX+2. A non-master's
     * weapon still needs a skill (6gz).
     */
    fun wieldedAt(
        weapon: CataloguedWeapon,
        skillLevels: Map<SkillKey, Int>,
        mastery: WeaponMastery?,
        dx: Int,
        warmedUp: Boolean = false,
    ): Int? {
        val own = Weapons.levelFor(weapon, skillLevels)
        if (mastery == null) return own
        val floor = dx + mastery.bonusOverDx(isRanged = false, warmedUp = warmedUp)
        return maxOf(own ?: floor, floor)
    }

    /**
     * The level a weapon in a master's hand lifts [level] to, or null where
     * the line changes nothing: DX plus [WeaponMastery.bonusOverDx], as a
     * FLOOR. "Every weapon" is the book's phrase, so a skill bought below
     * the floor is lifted too, and one above it is left alone.
     *
     * Not a roll at a default, so Versatile's +1 does not come along.
     *
     * **[weaponInHand], not "this skill has a weapon".** Brawling arms a
     * truncheon and opens on a fist, and the line is about the truncheon (a
     * design review's HIGH). [warmedUp] is the player's declaration that the
     * weapon has been "used repeatedly in a session".
     */
    fun mastered(
        level: Int,
        weaponInHand: Boolean,
        mastery: WeaponMastery?,
        dx: Int,
        isRanged: Boolean = false,
        warmedUp: Boolean = false,
    ): Int? {
        if (!weaponInHand || mastery == null) return null
        val floor = dx + mastery.bonusOverDx(isRanged, warmedUp)
        return floor.takeIf { level < it }
    }

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
                defaultOf(skill, key, attributeLevel, defaultBonus)?.let { it.copy(source = it.source + suffix) }
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

    /**
     * The row rolled at [key]'s default, or null where none is offered here.
     * Divination Arts is withheld: its dialog rolls the SPI-4 default itself.
     */
    private fun defaultOf(
        skill: SkillDefinition,
        key: SkillKey,
        attributeLevel: (ControllingAttribute) -> Int,
        defaultBonus: Int,
    ): Untrained? {
        if (skill.name == Divination.SKILL) return null
        val level = untrainedLevel(key, attributeLevel, defaultBonus) ?: return null
        val source = if (skill.name == BRAWLING) {
            "${skill.attr.code}$BRAWLING_PENALTY, the book's own line for $BRAWLING"
        } else {
            "${skill.attr.code}${skill.difficulty.defaultPenalty}"
        }
        return Untrained(key, level, source)
    }

    /**
     * The level [key] is used at untrained, Versatile's [defaultBonus]
     * inside, or null where it has no default: Brawling's own DX-2,
     * Wrestling's none, Divination Arts' SPI-4, else ch. 2's table (Very
     * Hard has none). The one default the untrained list and a potion's +N
     * on an unbought skill both read (v6.9i 4771; owner's ruling 1).
     */
    fun untrainedLevel(key: SkillKey, attributeLevel: (ControllingAttribute) -> Int, defaultBonus: Int): Int? =
        when (key.name) {
            BRAWLING -> attributeLevel(key.attr) + BRAWLING_PENALTY
            WRESTLING -> null
            Divination.SKILL -> attributeLevel(ControllingAttribute.Spi) + Divination.DEFAULT_PENALTY
            else -> SkillLadder.defaultFor(key.difficulty, attributeLevel(key.attr))
        }?.plus(defaultBonus)

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
