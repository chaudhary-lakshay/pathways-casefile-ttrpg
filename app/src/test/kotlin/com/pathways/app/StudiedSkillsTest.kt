package com.pathways.app

import com.pathways.model.BeyonderState
import com.pathways.rules.Derive
import com.pathways.rules.Premades
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Study points recorded in play (ch. 3 p. 32): character points locked to one
 * skill, earned over downtime, so they raise the skill and the budget does
 * not bill them. [SavedEdits.studied] holds them; [applyTo] folds them on.
 */
class StudiedSkillsTest {

    private val bouncer = Premades.byName("Docks Bouncer")
    private val base = bouncer.toCharacter()
    private fun studied(name: String, points: Int, specialization: String? = null) =
        SavedSkill(name, specialization, points)

    @Test
    fun `study on a printed skill raises it in place and is not billed`() {
        val index = base.skills.indexOfFirst { it.key.name == "Brawling" }
        val printed = base.skills[index]
        val played = SavedEdits(studied = listOf(studied("Brawling", 2))).applyTo(base)
        assertEquals(base.skills.map { it.key }, played.skills.map { it.key })
        assertEquals(printed.points + 2, played.skills[index].points)
        assertEquals(2, played.skills[index].studied)
        assertEquals(Derive.of(base).budget.spent, Derive.of(played).budget.spent)
    }

    @Test
    fun `study on a bought skill adds to what was bought`() {
        val played = SavedEdits(
            skills = listOf(SavedSkill("Climbing", null, 1)),
            studied = listOf(studied("Climbing", 1)),
        ).applyTo(base)
        val climbing = played.skills.single { it.key.name == "Climbing" }
        assertEquals(2 to 1, climbing.points to climbing.studied)
    }

    @Test
    fun `study on a skill the character does not hold adds nothing`() {
        assertEquals(base.skills, SavedEdits(studied = listOf(studied("Climbing", 1))).applyTo(base).skills)
    }

    @Test
    fun `a save holding only study points is not empty`() {
        assertFalse(SavedEdits(studied = listOf(studied("Brawling", 1))).isEmpty)
    }

    @Test
    fun `recording study stores the count, and zero removes it`() {
        val (session, last) = session(sheet = bouncer)
        session.setStudied("Brawling", null, 2)
        session.setStudied("Brawling", null, 2)
        assertEquals(listOf(studied("Brawling", 2)), last().edits.studied)
        session.setStudied("Brawling", null, 0)
        assertEquals(emptyList<SavedSkill>(), last().edits.studied)
    }

    @Test
    fun `a skill a potion supersedes cannot be studied, and one it leaves alone can`() {
        // Twilight Giant 9 grants Brawling, which the Bouncer prints: the grant
        // supersedes it, and "potion-granted skills cannot be studied".
        val played = base.digestedWith(BeyonderState("Twilight Giant", 9, 0))
        val brawling = base.skills.single { it.key.name == "Brawling" }.key
        val knife = base.skills.single { it.key.name == "Knife" }.key
        assertTrue(isSupersededByPotion(brawling, played))
        assertFalse(isSupersededByPotion(knife, played))
        assertFalse(isSupersededByPotion(brawling, null))
    }

    @Test
    fun `un-buying a skill takes its study with it`() {
        val (session, last) = session(sheet = bouncer)
        session.buySkill("Climbing")
        session.setStudied("Climbing", null, 1)
        session.spendOnSkill("Climbing", null, -1)
        assertNull(last().edits.studied.firstOrNull { it.name == "Climbing" })
    }

    /**
     * The builder's stepper steps rung to rung from what the session holds
     * NOW, so a second tap of one picture steps again rather than repeating a
     * delta read off the first (6ei). Study counts toward the rung.
     */
    @Test
    fun `a skill steps rung to rung, counting its study`() {
        val (session, last) = session(sheet = bouncer)
        session.buySkill("Climbing")
        session.stepSkill("Climbing", null, up = true)
        session.stepSkill("Climbing", null, up = true)
        assertEquals(4, last().edits.skills.single { it.name == "Climbing" }.points)
        session.setStudied("Climbing", null, 2)
        session.stepSkill("Climbing", null, up = true)
        assertEquals(6, last().edits.skills.single { it.name == "Climbing" }.points)
        session.stepSkill("Climbing", null, up = false)
        assertEquals(2, last().edits.skills.single { it.name == "Climbing" }.points)
    }
}
