package com.pathways.rules

import com.pathways.model.Character
import com.pathways.model.ControllingAttribute
import com.pathways.model.SkillKey
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
    const val WRESTLING = "Wrestling"

    /** "Default: DX-2 if untrained" (p. 38, and the p. 39 summary). */
    const val BRAWLING_PENALTY = -2

    /**
     * The potion blocks whose text prints "no default penalty" for a weapon,
     * as (pathway, block name).
     *
     * **Named, never applied.** Each is about a WEAPON -- "every weapon is
     * treated as known at effective DX" -- and which skill a weapon uses is a
     * property of the weapon in hand, which a list of skills does not ask
     * about. Applying it to every row the weapon table names would be this
     * app deciding what counts as picking one up.
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
     * What a roll from the list does NOT do. The Attack and Defend screens
     * take only a skill the sheet owns, so an untrained Brawling roll is a
     * to-hit number with no damage line behind it -- a gap, said out loud.
     */
    const val PLAIN_ROLL =
        "A roll from this list is the skill alone: an untrained punch's " +
            "damage and an Acrobatic Dodge's ±2 are not carried to the Attack " +
            "and Defend screens."

    /** [WEAPON_LINES] as the sentence the list prints, or null for a character with none. */
    fun weaponNote(pathway: String, sequence: Int): String? =
        weaponLinesFor(pathway, sequence).takeIf { it.isNotEmpty() }?.let { names ->
            val lines = names.joinToString("; ") { name ->
                "$name's \"no default penalty\" with ${WEAPON_SCOPES.getValue(pathway to name)}"
            }
            "Not applied here: $lines. This list does not know what is in hand."
        }

    /** One skill a character may attempt untrained, and how the number was reached. */
    data class Untrained(val key: SkillKey, val level: Int, val source: String)

    /**
     * Every printed skill [owned] does not hold and the book gives a default,
     * in the catalogue's order.
     *
     * [attributeLevel] answers for the character as PLAYED -- after potions --
     * the same levels a bought skill is rolled from. [defaultBonus] is
     * [TraitEffects.defaultBonus], added once.
     *
     * Owned is the same name and specialization at ANY difficulty: five
     * potion grants disagree with chapter 3 about a difficulty, and the
     * catalogue prints no name and specialization twice, so a difficulty
     * cannot be what tells two of its rows apart.
     */
    fun offered(
        owned: Map<SkillKey, Int>,
        attributeLevel: (ControllingAttribute) -> Int,
        defaultBonus: Int,
    ): List<Untrained> {
        val held = owned.keys.map { it.name.lowercase() to it.specialization?.lowercase() }.toSet()
        val suffix = if (defaultBonus != 0) ", +$defaultBonus ${TraitEffects.VERSATILE}" else ""
        return ContentLoader.skills()
            .filter { (it.name.lowercase() to it.specialization?.lowercase()) !in held }
            .mapNotNull { skill ->
                val (level, source) = defaultOf(skill, owned, attributeLevel, defaultBonus)
                    ?: return@mapNotNull null
                Untrained(
                    SkillKey(skill.name, skill.specialization, skill.attr, skill.difficulty),
                    level,
                    source + suffix,
                )
            }
    }

    /**
     * [offered], for [character] as played: the digested attributes, pools
     * and skill levels where a potion has been drunk, the purchased ones
     * where none has. The same two sources every bought skill is rolled from.
     */
    fun offeredTo(character: Character): List<Untrained> {
        val walked = PotionDigestion.of(character, ContentLoader.pathways())
        val attributes = walked?.effectiveAttributes ?: character.attributes
        val secondaries = walked?.secondaries ?: Derive.of(character).secondaries
        val spi = walked?.effectiveSpi ?: character.spi
        return offered(
            owned = walked?.skillLevels ?: Derive.of(character).skillLevels,
            attributeLevel = { Derive.controllingLevel(it, attributes, secondaries, spi) },
            defaultBonus = TraitEffects.defaultBonus(character.traits),
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
     * drunk, earliest first, for the screen to say they are not applied.
     */
    fun weaponLinesFor(pathway: String, sequence: Int): List<String> =
        ContentLoader.pathways()
            .filter { it.pathway == pathway && it.sequence >= sequence }
            .sortedByDescending { it.sequence }
            .filter { (it.pathway to it.name) in WEAPON_LINES }
            .map { it.name }
}
