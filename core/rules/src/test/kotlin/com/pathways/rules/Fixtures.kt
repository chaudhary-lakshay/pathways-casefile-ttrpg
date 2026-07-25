package com.pathways.rules

import com.pathways.model.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class FixtureSkill(
    val name: String,
    val specialization: String? = null,
    val attr: String,
    val difficulty: String,
    val points: Int,
    val publishedLevel: Int,
)

@Serializable
data class FixtureTrait(val name: String, val cost: Int)

@Serializable
data class FixturePublished(
    val hp: Int? = null, val fp: Int? = null, val will: Int? = null, val per: Int? = null,
    val spi: Int? = null, val cor: Int? = null, val basicSpeed: Double? = null,
    val basicMove: Int? = null, val dodge: Int? = null,
    val bpTotal: Int? = null, val totalSpent: Int? = null, val unspent: Int? = null,
)

@Serializable
data class Fixture(
    val name: String,
    val attributes: Map<Attr, Int>,
    val published: FixturePublished,
    val advantages: List<FixtureTrait> = emptyList(),
    val disadvantages: List<FixtureTrait> = emptyList(),
    val skills: List<FixtureSkill> = emptyList(),
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
     */
    fun attributeLevelFor(skill: FixtureSkill): Int {
        val attrs = attributesOnly()
        val secondaries = SecondaryCharacteristics.of(attrs, SecondaryDelta())
        return Derive.controllingLevel(ControllingAttribute.fromCode(skill.attr), attrs, secondaries, spi = 0)
    }

    fun toCharacter() = Character(
        name = name,
        attributes = attributesOnly(),
        traits = advantages.map { Trait(it.name, it.cost, TraitKind.ADVANTAGE) } +
            disadvantages.map { Trait(it.name, it.cost, TraitKind.DISADVANTAGE) },
        skills = skills.map {
            PurchasedSkill(
                SkillKey(
                    it.name, it.specialization,
                    ControllingAttribute.fromCode(it.attr), Difficulty.fromCode(it.difficulty),
                ),
                it.points,
            )
        },
    )
}

object Fixtures {
    private val json = Json { ignoreUnknownKeys = true }

    private val NAMES = listOf(
        "academic_researcher", "antiquarian_dealer", "apothecary", "barmaid_brawler",
        "beat_cop", "church_healer", "dock_foreman", "docks_bouncer", "factory_foreman",
        "fortune_teller", "hunter_tracker", "journalist", "lotm_blank_character_sheet",
        "military_police", "monastery_scholar", "night_watchman", "nighthawk_recruit",
        "occult_scholar", "pickpocket", "private_investigator", "street_acrobat",
        "underground_courier", "university_librarian",
    )

    fun all(): List<Fixture> = NAMES.map { load(it) }

    fun byName(display: String): Fixture = all().first { it.name == display }

    private fun load(slug: String): Fixture {
        val text = Fixtures::class.java.getResourceAsStream("/$slug.json")
            ?.bufferedReader()?.use { it.readText() }
            ?: error("fixture not on the test classpath: $slug.json")
        return json.decodeFromString(text)
    }
}
