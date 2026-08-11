package com.pathways.app

import com.pathways.model.Attr
import com.pathways.model.Attributes
import com.pathways.model.Character
import com.pathways.model.Difficulty
import com.pathways.model.Trait
import com.pathways.model.TraitKind
import com.pathways.rules.Appearance
import com.pathways.rules.Derive
import com.pathways.rules.PotionDigestion
import com.pathways.rules.Premades
import com.pathways.rules.Wealth
import com.pathways.rules.content.ContentLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * A premade plus what the player has bought since.
 *
 * The invariant under all of it: the published sheet is never edited. An edit
 * is a delta, so the `computed vs asPublished` diff keeps comparing the engine
 * against the book rather than against the player's purchases.
 */
class EditedCharacterTest {

    private val mortal = Character(name = "test", attributes = Attributes(iq = 10))

    @Test
    fun `no edits leaves the character exactly as printed`() {
        assertEquals(mortal, SavedEdits().applyTo(mortal))
    }

    @Test
    fun `an unedited attribute keeps its published value`() {
        val edited = SavedEdits(iq = 13).applyTo(mortal)
        assertEquals(13, edited.attributes.iq)
        assertEquals(mortal.attributes.st, edited.attributes.st, "ST was never touched")
    }

    @Test
    fun `a bought skill resolves its attribute and difficulty from the book`() {
        // The save file stores a name and points only. Storing the controlling
        // attribute too would let a save contradict the book after a content
        // fix -- the book is the authority (CLAUDE.md 4.1).
        val edited = SavedEdits(skills = listOf(SavedSkill("Ritualistic Magic", points = 8)))
            .applyTo(mortal)
        val bought = edited.skills.single()
        assertEquals("Ritualistic Magic", bought.key.name)
        assertEquals(8, bought.points)
        val definition = definitionOf("Ritualistic Magic")
        assertNotNull(definition)
        assertEquals(definition!!.attr, bought.key.attr)
        assertEquals(definition.difficulty, bought.key.difficulty)
    }

    @Test
    fun `a bought skill reaches a level the ritual rules can use`() {
        val edited = SavedEdits(iq = 14, skills = listOf(SavedSkill("Ritualistic Magic", points = 8)))
            .applyTo(mortal)
        val level = skillLevelOf("Ritualistic Magic", Derive.of(edited).skillLevels)
        assertNotNull(level, "no premade prints this skill; buying it is the only way to cast")
        assertTrue(level!! > 0)
    }

    @Test
    fun `a bought skill is billed to the point budget`() {
        val before = Derive.of(mortal).budget.spent
        val bought = SavedEdits(skills = listOf(SavedSkill("Ritualistic Magic", points = 8)))
            .applyTo(mortal)
        assertEquals(before + 8, Derive.of(bought).budget.spent, "points bought in play are still points")
    }

    @Test
    fun `a skill the book does not print is dropped, never guessed at`() {
        // Inventing a controlling attribute and difficulty for it would be
        // making up a rule, and billing for a skill the sheet cannot show
        // would charge for nothing.
        val edited = SavedEdits(skills = listOf(SavedSkill("Sword Of Infinite Nonsense", points = 4)))
            .applyTo(mortal)
        assertTrue(edited.skills.isEmpty())
        assertNull(definitionOf("Sword Of Infinite Nonsense"))
    }

    @Test
    fun `buying a skill the sheet already prints does not duplicate it`() {
        // Character rejects two purchases of one key, and those points belong
        // on the existing entry rather than a second row.
        val printed = definitionOf("Occultism")!!
        val withPrinted = SavedEdits(skills = listOf(SavedSkill(printed.name, points = 2))).applyTo(mortal)
        val edited = SavedEdits(skills = listOf(SavedSkill(printed.name, points = 4))).applyTo(withPrinted)
        assertEquals(1, edited.skills.count { it.key.name == printed.name })
        assertEquals(2, edited.skills.single { it.key.name == printed.name }.points)
    }

    @Test
    fun `a specialization picks its own row, not the first one printed`() {
        // REGRESSION (Codex): five names carry several rows -- Guns has four,
        // Navigation three -- so resolving by name alone bought "Guns
        // (Pistol)" whichever variant was tapped.
        val rifle = definitionOf("Guns", "Rifle")
        val pistol = definitionOf("Guns", "Pistol")
        assertNotNull(rifle)
        assertEquals("Rifle", rifle!!.specialization)
        assertEquals("Pistol", pistol!!.specialization)

        val bought = SavedEdits(skills = listOf(SavedSkill("Guns", "Rifle", points = 2)))
            .applyTo(mortal).skills.single()
        assertEquals("Rifle", bought.key.specialization)
    }

    @Test
    fun `two specializations of one skill are two purchases`() {
        val edited = SavedEdits(
            skills = listOf(
                SavedSkill("Guns", "Rifle", points = 1),
                SavedSkill("Guns", "Shotgun", points = 1),
            ),
        ).applyTo(mortal)
        assertEquals(
            setOf("Rifle", "Shotgun"),
            edited.skills.map { it.key.specialization }.toSet(),
        )
    }

    @Test
    fun `an attribute edit is stored per attribute, not as a whole block`() {
        val edits = SavedEdits().withAttribute(Attr.DX, 12)
        assertEquals(12, edits.levelOf(Attr.DX))
        assertNull(edits.levelOf(Attr.ST), "an untouched attribute stays as printed")
    }

    @Test
    fun `an empty edit set is recognised as empty`() {
        assertTrue(SavedEdits().isEmpty)
        assertTrue(!SavedEdits(ht = 11).isEmpty)
        assertTrue(!SavedEdits(skills = listOf(SavedSkill("Brawling", points = 1))).isEmpty)
        assertTrue(!SavedEdits(wealth = "Wealthy").isEmpty)
    }

    @Test
    fun `a chosen wealth level replaces the one the sheet printed`() {
        // A character has one economic status. Left beside each other, the
        // printed level and the bought one would raise the budget and spend
        // from it at the same time, and both totals would look plausible.
        val poor = mortal.copy(traits = listOf(Trait("Wealth: Poor", 15, TraitKind.DISADVANTAGE)))
        val edited = SavedEdits(wealth = "Wealthy").applyTo(poor)

        assertEquals(listOf("Wealth: Wealthy"), edited.traits.map { it.name })
        assertEquals(
            Derive.of(poor).budget.surplus - 30,
            Derive.of(edited).budget.surplus,
            "15 points of disadvantage lost and 15 spent is a 30-point move",
        )
    }

    @Test
    fun `a sheet's other traits survive a wealth change`() {
        val printed = mortal.copy(
            traits = listOf(
                Trait("Skinny", 5, TraitKind.DISADVANTAGE),
                Trait("Wealth: Poor", 15, TraitKind.DISADVANTAGE),
            ),
        )
        assertEquals(
            listOf("Skinny", "Wealth: Comfortable"),
            SavedEdits(wealth = "Comfortable").applyTo(printed).traits.map { it.name },
        )
    }

    @Test
    fun `Average leaves no wealth trait behind`() {
        val poor = mortal.copy(traits = listOf(Trait("Wealth: Poor", 15, TraitKind.DISADVANTAGE)))
        assertTrue(SavedEdits(wealth = "Average").applyTo(poor).traits.isEmpty())
    }

    @Test
    fun `choosing Average is still a choice, and still shows as one`() {
        // REGRESSION (Codex): Average costs 0 pts, so Wealth.applied leaves no
        // trait behind. A display derived from the composed character read
        // "the sheet prints no wealth level" for a player who had deliberately
        // picked Average -- while the budget had moved by 15 points.
        val poor = mortal.copy(traits = listOf(Trait("Wealth: Poor", 15, TraitKind.DISADVANTAGE)))
        val edits = SavedEdits(wealth = "Average")

        assertTrue(edits.applyTo(poor).traits.isEmpty(), "and it really does leave no trait")
        assertEquals("Average", edits.wealthShownFor(edits.applyTo(poor))?.status)
    }

    @Test
    fun `an unchosen level falls back to the one the sheet printed`() {
        val poor = mortal.copy(traits = listOf(Trait("Wealth: Poor", 15, TraitKind.DISADVANTAGE)))
        assertEquals("Poor", SavedEdits().wealthShownFor(poor)?.status)
        // And a level the book does not print falls through the same way, so
        // the row agrees with what applyTo actually billed.
        assertEquals("Poor", SavedEdits(wealth = "Fabulously Rich").wealthShownFor(poor)?.status)
        assertNull(SavedEdits().wealthShownFor(mortal), "a sheet with no wealth row still has none")
    }

    @Test
    fun `a saved wealth level the book does not print is dropped, never guessed at`() {
        // Same rule as a saved skill the book no longer prints: a save file is
        // convenience, never authority. Answering "Average" for an unreadable
        // level would silently move the budget by up to 25 points.
        val poor = mortal.copy(traits = listOf(Trait("Wealth: Poor", 15, TraitKind.DISADVANTAGE)))
        assertEquals(poor.traits, SavedEdits(wealth = "Fabulously Rich").applyTo(poor).traits)
    }

    @Test
    fun `a chosen appearance level is billed like any other trait`() {
        val edited = SavedEdits(appearance = "Ugly").applyTo(mortal)
        assertEquals(listOf("Ugly"), edited.traits.map { it.name })
        assertEquals(
            Derive.of(mortal).budget.surplus + 8,
            Derive.of(edited).budget.surplus,
            "Ugly refunds 8 points",
        )
    }

    @Test
    fun `wealth and appearance are two choices, not one`() {
        // They resolve by different rules -- wealth by its "Wealth: " prefix,
        // appearance by the bare level name -- so one must not disturb the
        // other. Chapter 3's trait lists print none of the appearance levels,
        // which is what makes the bare name safe to match on.
        val printed = mortal.copy(traits = listOf(Trait("Wealth: Poor", 15, TraitKind.DISADVANTAGE)))
        val both = SavedEdits(wealth = "Comfortable", appearance = "Attractive").applyTo(printed)

        assertEquals(listOf("Wealth: Comfortable", "Attractive"), both.traits.map { it.name })
        assertEquals("Comfortable", Wealth.levelOf(both.traits)?.status)
        assertEquals("Attractive", Appearance.levelOf(both.traits)?.level)
    }

    @Test
    fun `clearing the choice returns the sheet's own looks`() {
        // No premade prints an appearance level, so on all 23 this is the way
        // back to ordinary. A tested setter that no screen can reach is not a
        // way back -- the picker's first row calls this.
        val ugly = SavedEdits(appearance = "Ugly").applyTo(mortal)
        assertEquals(listOf("Ugly"), ugly.traits.map { it.name })
        assertTrue(SavedEdits(appearance = null).applyTo(mortal).traits.isEmpty())
    }

    @Test
    fun `a saved appearance level the book does not print is dropped`() {
        // Same rule as wealth and as a bought skill. There is no nearest level
        // to fall back on here: the table prints no ordinary row at all.
        assertTrue(SavedEdits(appearance = "Radiant").applyTo(mortal).traits.isEmpty())
        assertNull(SavedEdits(appearance = "Radiant").appearanceLevel())
        assertTrue(!SavedEdits(appearance = "Ugly").isEmpty)
    }

    // ---- what a sheet row actually rolls -------------------------------

    private val researcher = Premades.all().single { it.name == "Academic Researcher" }
    private val paragon = PotionDigestion
        .digestThrough(researcher.toCharacter(), "Paragon", 9, ContentLoader.pathways())
        .skillLevels

    @Test
    fun `a published row rolls the level the character has now`() {
        // A Paragon Sequence 9 potion raises IQ, and every bought IQ skill
        // re-levels with it (M3). The sheet's own printed number is the level
        // before the potion, so rolling it is rolling a character who no
        // longer exists: Research is 13 as printed and 17 once digested.
        val research = researcher.skills.single { it.label.startsWith("Research") }

        assertEquals(13, researcher.levelFor(research), "what the book prints")
        assertEquals(17, liveLevelOf(research.key(), paragon, researcher.levelFor(research)))
    }

    @Test
    fun `a mortal's published row is unchanged`() {
        // No potion, no difference -- the fallback and the live map agree, so
        // this fix moves nothing for the 23 premades as printed.
        val levels = Derive.of(researcher.toCharacter()).skillLevels
        researcher.skills.forEach { skill ->
            val printed = researcher.levelFor(skill)
            assertEquals(printed, liveLevelOf(skill.key(), levels, printed), skill.label)
        }
    }

    @Test
    fun `a specialization is matched, never guessed`() {
        // The Guns bug, one row over: asking by NAME returns whichever
        // specialization the map happens to hold first (6e, 6as). Hidden Lore
        // (Fae) is on this sheet, so a stray sibling must not answer for it.
        val fae = researcher.skills.single { it.label.startsWith("Hidden Lore") }.key()
        val impostor = fae.copy(specialization = "Spirits")

        assertEquals(13, liveLevelOf(fae, paragon, fallback = 0))
        assertEquals(0, liveLevelOf(impostor, paragon, fallback = 0), "no sibling answers")
    }

    @Test
    fun `a potion-granted skill is a row of its own`() {
        // Ch. 9 p. 85 forbids buying the SPI skills, so a potion is their only
        // route -- and the sheet listed one row per PUBLISHED skill and one
        // per BOUGHT skill, which between them cannot show a granted one.
        // 6aj opened this hole for bought skills and 6aq closed the purchase
        // route, which re-opened it for exactly the skills chapter 4 prints
        // result tables for.
        val fool = PotionDigestion
            .digestThrough(researcher.toCharacter(), "Fool", 9, ContentLoader.pathways())
            .skillLevels
        val granted = grantedSkills(researcher, SavedEdits(), fool).map { it.first.name }

        assertTrue("Spiritual Intuition" in granted, "the Seer's sense, granted: $granted")
        assertTrue("Divination Arts" in granted)
        // Occultism is on the sheet already, so the potion raises the
        // published row rather than adding a second one beside it.
        assertTrue("Occultism" !in granted)
    }

    @Test
    fun `a bought skill is not also listed as granted`() {
        // The bought rows are rendered from `edits.skills` and already roll
        // the digested level. Listing one here as well would show it twice.
        val fool = PotionDigestion
            .digestThrough(researcher.toCharacter(), "Fool", 9, ContentLoader.pathways())
            .skillLevels
        val edits = SavedEdits(skills = listOf(SavedSkill("Ritualistic Magic", points = 1)))

        assertTrue("Ritualistic Magic" !in grantedSkills(researcher, edits, fool).map { it.first.name })
        assertTrue("Ritualistic Magic" in grantedSkills(researcher, SavedEdits(), fool).map { it.first.name })
    }

    @Test
    fun `a grant at another difficulty is a second skill, not the same one`() {
        // The five recorded difficulty disagreements are real on screen. A
        // Barmaid Brawler prints Knife/DX [Easy]; the Abyss potion grants
        // Knife/DX [Average] at Sequence 6, and p. 85 stacks a grant only when
        // the name AND difficulty match -- so she ends up holding both.
        //
        // Keyed on the name alone, the published row answered with the
        // potion's level and the granted skill was suppressed as a duplicate
        // (Codex). Neither is repaired: 4.1 keeps the disagreement.
        val brawler = Premades.all().single { it.name == "Barmaid Brawler" }
        val abyss = PotionDigestion
            .digestThrough(brawler.toCharacter(), "Abyss", 6, ContentLoader.pathways())
            .skillLevels
        val printed = brawler.skills.single { it.label.startsWith("Knife") }

        val easy = printed.key()
        val average = easy.copy(difficulty = Difficulty.AVERAGE)
        assertEquals(Difficulty.EASY, easy.difficulty, "the sheet's own row")

        // Two entries, one label, two levels -- and each row reads its own.
        val liveEasy = liveLevelOf(easy, abyss, brawler.levelFor(printed))
        val liveAverage = liveLevelOf(average, abyss, fallback = 0)
        assertNotEquals(liveEasy, liveAverage, "the two Knives are not one skill")
        assertEquals(liveEasy, abyss.entries.single { it.key.difficulty == Difficulty.EASY && it.key.name == "Knife" }.value)

        // and the potion's Knife gets a row of its own rather than vanishing
        val granted = grantedSkills(brawler, SavedEdits(), abyss)
        assertTrue(granted.any { it.first.name == "Knife" && it.first.difficulty == Difficulty.AVERAGE })
        assertTrue(granted.none { it.first.name == "Knife" && it.first.difficulty == Difficulty.EASY })
    }

    @Test
    fun `a bought skill suppresses its granted twin by the book's key`() {
        // A purchase stores a name and points; its difficulty is the book's.
        // The bought rows and grantedSkills have to resolve that the SAME way
        // or a skill shows up twice, once as bought and once as granted
        // (Codex). Occultism is IQ/Average in the catalogue and the Seer
        // potion grants Occultism at the same difficulty, so buying it must
        // leave exactly one row.
        val fool = PotionDigestion
            .digestThrough(researcher.toCharacter(), "Fool", 9, ContentLoader.pathways())
            .skillLevels
        val bought = SavedEdits(skills = listOf(SavedSkill("Divination Arts", points = 1)))

        val keys = bought.purchasedKeys()
        assertEquals(listOf("Divination Arts"), keys.map { it.name })
        assertTrue(grantedSkills(researcher, bought, fool).none { it.first.name == "Divination Arts" })
    }

    @Test
    fun `a purchase the book no longer prints is not a row`() {
        // It stays in the save file -- the book may print it again after a
        // content fix (6as) -- but it resolves to no key, so it is neither
        // billed nor rendered. Rendering it was what let a potion's skill of
        // the same name appear as bought AND as granted.
        val unknown = SavedEdits(skills = listOf(SavedSkill("Chronomancy", points = 1)))
        assertTrue(unknown.purchasedKeys().isEmpty())
        assertEquals(1, unknown.skills.size, "the saved entry itself is kept")
    }

    @Test
    fun `the fallback is unreachable, and this is what says so`() {
        // liveLevelOf falls back to the sheet's printed level, which Codex
        // rightly called a silent stale roll if a published skill ever went
        // missing from the digested map. It cannot today: all 23 premades,
        // down all 22 pathways to Sequence 6, keep every published skill.
        //
        // Asserted rather than recalled, because the fallback's whole defence
        // is that nothing reaches it -- and a defence nobody rechecks is how a
        // deferral goes stale (6au, 6av). If this fails, the fallback has
        // become load-bearing and needs deciding, not extending.
        val blocks = ContentLoader.pathways()
        Premades.all().forEach { premade ->
            blocks.map { it.pathway }.distinct().forEach { pathway ->
                val levels = PotionDigestion
                    .digestThrough(premade.toCharacter(), pathway, 6, blocks)
                    .skillLevels
                premade.skills.forEach { skill ->
                    val key = skill.key()
                    assertTrue(
                        levels.keys.any {
                            it.name.equals(key.name, ignoreCase = true) &&
                                it.specialization == key.specialization &&
                                it.difficulty == key.difficulty
                        },
                        "${premade.name} down $pathway lost ${skill.label}",
                    )
                }
            }
        }
    }

    @Test
    fun `a mortal is granted nothing`() {
        val levels = Derive.of(researcher.toCharacter()).skillLevels
        assertTrue(grantedSkills(researcher, SavedEdits(), levels).isEmpty())
    }
}
