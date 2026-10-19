package com.pathways.app

import com.pathways.model.BoonState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * A granted boon, stored and played. Before this a successful prayer printed
 * "granted" and wrote nothing.
 */
class BoonSessionTest {

    @TempDir
    lateinit var dir: File

    private val broker = BoonState("Chaos Mist", 9)

    @Test
    fun `a granted boon is saved and played`() {
        val (s, saved) = session(sheet = plain())
        s.grantBoon(broker)
        assertEquals(broker, s.boon)
        assertEquals(broker, saved().edits.boon)
        val character = s.characterFor(plain())
        assertEquals(5, character.digestedWith(null)!!.effectiveSpi)
        // v6.9i: +4 on the IQ-6 default, at the IQ 10 the boon made of the sheet's 9.
        assertEquals(8, liveSkillLevels(character).entries.single { it.key.name == "Diplomacy" }.value)
        // Free: the bill is the unedited character's.
        assertEquals(plain().toCharacter().let { com.pathways.rules.Derive.of(it).budget }, character.digestedWith(null)!!.budget)
    }

    @Test
    fun `a boon alone is an edit - composition must not skip it`() {
        // applyTo returns the sheet untouched for edits that are empty.
        assertFalse(SavedEdits(boon = broker).isEmpty)
        assertEquals(broker, SavedEdits(boon = broker).applyTo(plain().toCharacter()).boon)
    }

    @Test
    fun `only the next printed rung of the patron served is granted`() {
        val (s, _) = session(sheet = plain())
        assertEquals(broker, s.nextBoon("Chaos Mist"))
        assertThrows<IllegalArgumentException> { s.grantBoon(BoonState("Chaos Mist", 8)) }
        s.grantBoon(broker)
        assertNull(s.nextBoon("Everlasting"))
        assertThrows<IllegalArgumentException> { s.grantBoon(BoonState("Everlasting", 9)) }
        // The second tap of one picture: the same grant again is refused.
        assertThrows<IllegalArgumentException> { s.grantBoon(broker) }
        s.grantBoon(BoonState("Chaos Mist", 8))
        assertNull(s.nextBoon("Chaos Mist"))
    }

    @Test
    fun `a boon's catalogue rows are in play, and never on the character`() {
        // Patriarch 8 prints "Fit +1 to all HT rolls" (6hn).
        val (s, _) = session(sheet = plain())
        s.grantBoon(BoonState("Patriarch", 9))
        assertFalse(s.traitsInPlay(plain()).any { it.name == "Fit" })
        s.grantBoon(BoonState("Patriarch", 8))
        assertEquals(listOf(0), s.traitsInPlay(plain()).filter { it.name == "Fit" }.map { it.cost })
        assertFalse(s.characterFor(plain()).traits.any { it.name == "Fit" })
    }

    @Test
    fun `a lost character is granted nothing`() {
        val (s, _) = session(SavedSession(lost = true), plain())
        assertNull(s.nextBoon("Chaos Mist"))
        assertThrows<IllegalArgumentException> { s.grantBoon(broker) }
    }

    @Test
    fun `a boon's Will raises the CoR ceiling`() {
        // Alms Monk, Will +2: the ceiling is the played Will (plain IQ 9).
        val monk = SavedEdits(boon = BoonState("Eternal Aeon (Inevitability)", 8))
        assertEquals(SavedEdits().corCeilingOn(plain(), null) + 2, monk.corCeilingOn(plain(), null))
    }

    @Test
    fun `a boon survives the save file`() {
        val store = SessionStore(File(dir, "sessions.json"))
        val sessions = mapOf("plain" to SavedSession(edits = SavedEdits(boon = broker)))
        store.save(sessions)
        assertEquals(sessions, store.load())
    }
}
