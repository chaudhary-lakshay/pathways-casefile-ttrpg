package com.pathways.rules

import com.pathways.model.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class PremadeSkill(
    val name: String,
    val specialization: String? = null,
    val attr: String,
    val difficulty: String,
    val points: Int,
    val publishedLevel: Int,
) {
    /** "Occultism", or "Area Knowledge (Backlund)" when specialized. */
    val label: String get() = specialization?.let { "$name ($it)" } ?: name

    fun key() = SkillKey(
        name, specialization,
        ControllingAttribute.fromCode(attr), Difficulty.fromCode(difficulty),
    )
}

@Serializable
data class PremadeTrait(val name: String, val cost: Int)

@Serializable
data class PublishedValues(
    val hp: Int? = null, val fp: Int? = null, val will: Int? = null, val per: Int? = null,
    val spi: Int? = null, val cor: Int? = null, val basicSpeed: Double? = null,
    val basicMove: Int? = null, val dodge: Int? = null,
    val bpTotal: Int? = null, val totalSpent: Int? = null, val unspent: Int? = null,
    /**
     * Per-category POINT BREAKDOWN subtotals (KNOWN_ISSUES: "fixtures carry
     * no per-category point breakdown"). Null on every field for Beat Cop,
     * which has no such block at all. [disadvantagesSubtotal] is also null
     * for Church Healer, whose Disadvantages cell is a broken spreadsheet
     * formula ("#ERROR!") rather than a printed number.
     */
    val attributesSubtotal: Int? = null,
    val advantagesSubtotal: Int? = null,
    val disadvantagesSubtotal: Int? = null,
    val skillsSubtotal: Int? = null,
)

@Serializable
data class PremadeSheet(
    val name: String,
    val attributes: Map<Attr, Int>,
    val published: PublishedValues,
    val advantages: List<PremadeTrait> = emptyList(),
    val disadvantages: List<PremadeTrait> = emptyList(),
    val skills: List<PremadeSkill> = emptyList(),
) {
    private fun attributesOnly() = Attributes(
        st = attributes.getValue(Attr.ST), dx = attributes.getValue(Attr.DX),
        iq = attributes.getValue(Attr.IQ), ht = attributes.getValue(Attr.HT),
    )

    /**
     * The published sheets key skills off six controlling stats (ST/DX/IQ/HT
     * plus Will and Per), resolved through the real [ControllingAttribute]
     * and [Derive.controllingLevel]. None of the fixtures carry a
     * [SecondaryDelta], so Will/Per resolve to raw IQ here, same as the
     * published sheets (verified: published.will == published.per ==
     * attributes.IQ on all 23 sheets) — but through the production
     * resolution path, not a test-only substitution.
     *
     * SPI is [MORTAL_SPI], the one the character plays ([toCharacter]): no
     * sheet keys a skill off SPI today, and one that did would be computed
     * from what the book gives a character no potion has reached.
     */
    fun attributeLevelFor(skill: PremadeSkill): Int {
        val attrs = attributesOnly()
        val secondaries = SecondaryCharacteristics.of(attrs, SecondaryDelta())
        return Derive.controllingLevel(ControllingAttribute.fromCode(skill.attr), attrs, secondaries, MORTAL_SPI)
    }

    /** The level this sheet's points buy for [skill], per the book. */
    fun levelFor(skill: PremadeSkill): Int = SkillLadder.levelFor(
        points = skill.points,
        difficulty = Difficulty.fromCode(skill.difficulty),
        attributeLevel = attributeLevelFor(skill),
    )

    fun toCharacter() = Character(
        name = name,
        spi = MORTAL_SPI,
        attributes = attributesOnly(),
        traits = advantages.map { Trait(it.name, it.cost, TraitKind.ADVANTAGE) } +
            disadvantages.map { Trait(it.name, it.cost, TraitKind.DISADVANTAGE) },
        skills = skills.map { PurchasedSkill(it.key(), it.points) },
    )
}

/**
 * What a published sheet plays for SPI, whatever it prints. "All mortals
 * begin at SPI 0, fixed. SPI increases only through Beyonder potions,
 * supernatural events, or certain rituals" (book lines 2268-2269), and no
 * sheet prints a pathway, a sequence or a potion. Four print SPI 9, which is
 * [com.pathways.model.BASE_ATTRIBUTE], what every attribute cell holds at
 * zero cost. The book wins and the 9 stays in [PublishedValues.spi],
 * recorded and never played (6ft).
 *
 * Until 6ft the 9 was carried, on a reason written before the app had a
 * pathway: "an import that drops it silently turns a Beyonder into a
 * mortal". A Beyonder is [com.pathways.model.Character.beyonder] now, and
 * [PotionDigestion] adds each grant to this, so an Apothecary on the
 * Hermit's Sequence 9 played SPI 18, more than any Sequence 9 block grants.
 *
 * **The other reading, refused**: 9 is the Fool, Hermit and Darkness grant
 * at Sequence 9 (2270-2276), so the four could be Beyonders whose pathway
 * the sheet omits. Nothing on a sheet says which, and this app offers each
 * a first potion as it does a mortal, so it cannot also play them as
 * having drunk one.
 */
private const val MORTAL_SPI = 0

object Premades {
    private val json = Json { ignoreUnknownKeys = true }

    private val NAMES = listOf(
        "academic_researcher", "antiquarian_dealer", "apothecary", "barmaid_brawler",
        "beat_cop", "church_healer", "dock_foreman", "docks_bouncer", "factory_foreman",
        "fortune_teller", "hunter_tracker", "journalist", "lotm_blank_character_sheet",
        "military_police", "monastery_scholar", "night_watchman", "nighthawk_recruit",
        "occult_scholar", "pickpocket", "private_investigator", "street_acrobat",
        "underground_courier", "university_librarian",
    )

    fun all(): List<PremadeSheet> = NAMES.map { load(it) }

    fun byName(display: String): PremadeSheet = all().first { it.name == display }

    private fun load(slug: String): PremadeSheet {
        val text = Premades::class.java.getResourceAsStream("/$slug.json")
            ?.bufferedReader()?.use { it.readText() }
            ?: error("premade sheet not on the classpath: $slug.json")
        return json.decodeFromString(text)
    }
}
