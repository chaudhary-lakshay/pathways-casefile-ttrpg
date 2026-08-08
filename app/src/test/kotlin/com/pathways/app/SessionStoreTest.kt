package com.pathways.app

import com.pathways.model.BeyonderState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * A save file is convenience, never authority.
 *
 * The schema has already grown twice in one milestone -- CoR, then the three
 * pools -- so the tests that matter most here are the one that reads a file
 * written by an OLDER build, and the one that refuses to crash on a file that
 * makes no sense at all.
 */
class SessionStoreTest {

    @TempDir
    lateinit var dir: File

    private fun store(name: String = "sessions.json") = SessionStore(File(dir, name))

    @Test
    fun `a missing file is an empty session list, not an error`() {
        assertEquals(emptyMap<String, SavedSession>(), store("absent.json").load())
    }

    @Test
    fun `what is saved is what comes back`() {
        val store = store()
        val sessions = mapOf(
            "Academic Researcher" to SavedSession(
                beyonder = BeyonderState("Fool", 8, 40),
                cor = 3,
                hpLost = 2,
                fpLost = 11,
                spiSpent = 1,
            ),
        )
        store.save(sessions)
        assertEquals(sessions, store.load())
    }

    @Test
    fun `a file from before CoR and pools existed still loads`() {
        // Exactly what cc61648 wrote. Every field added since must default,
        // or upgrading the app would silently drop a table's whole walk.
        val file = File(dir, "sessions.json")
        file.writeText(
            """{"Academic Researcher":{"beyonder":{"pathway":"Fool","sequence":9,"digestion":15},"lost":false}}""",
        )
        val loaded = SessionStore(file).load().getValue("Academic Researcher")
        assertEquals(BeyonderState("Fool", 9, 15), loaded.beyonder)
        assertEquals(0, loaded.cor)
        assertFalse(loaded.corrupted)
        assertEquals(0, loaded.hpLost)
    }

    @Test
    fun `an unreadable file is discarded, not fatal`() {
        // Losing a digestion percentage is an annoyance; refusing to start is
        // a bug. android.util.Log is stubbed in unit tests, so this asserts
        // the path, not the logging.
        val file = File(dir, "sessions.json")
        file.writeText("{ this is not json")
        assertTrue(SessionStore(file).load().isEmpty())
    }

    @Test
    fun `a purchase the book forbids is dropped on load`() {
        // Ch. 9 p. 85 bars buying the three SPI-based skills. 6aq stopped
        // BILLING them, but the raw entry stayed in the file -- and the
        // sheet's bought-skill rows read that raw list, so once a potion
        // granted the same skill it came back as a rollable "bought" row for
        // a purchase that is not legal.
        val file = File(dir, "sessions.json")
        file.writeText(
            """
            {"Academic Researcher":{"edits":{"skills":[
              {"name":"Divination Arts","points":4},
              {"name":"Ritualistic Magic","points":4}
            ]}}}
            """.trimIndent(),
        )
        val edits = SessionStore(file).load().getValue("Academic Researcher").edits
        assertEquals(listOf("Ritualistic Magic"), edits.skills.map { it.name })
    }

    @Test
    fun `a skill the book does not print today survives the load`() {
        // The difference that makes dropping safe: an SPI skill is barred
        // permanently, but a name the book does not print TODAY may be
        // printed again after a content fix. `purchases` keeps skipping those
        // non-destructively; deleting them here would lose them for good.
        val file = File(dir, "sessions.json")
        file.writeText(
            """{"Beat Cop":{"edits":{"skills":[{"name":"Zzz Not A Skill","points":2}]}}}""",
        )
        val edits = SessionStore(file).load().getValue("Beat Cop").edits
        assertEquals(listOf("Zzz Not A Skill"), edits.skills.map { it.name })
    }

    @Test
    fun `a field this build does not know about is ignored`() {
        val file = File(dir, "sessions.json")
        file.writeText("""{"Beat Cop":{"lost":false,"somethingFromTheFuture":7}}""")
        assertEquals(setOf("Beat Cop"), SessionStore(file).load().keys)
    }
}
