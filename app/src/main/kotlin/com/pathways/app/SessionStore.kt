package com.pathways.app

import android.util.Log
import com.pathways.model.BeyonderState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * A skill bought in play, as written to disk.
 *
 * Only the identity and the points are stored. The controlling attribute and
 * difficulty are NOT: they belong to the book's skill list, and a save file
 * carrying its own copy could contradict it after a content fix. They are
 * resolved through the content loader on the way back in, which is the same
 * rule the rest of this codebase follows -- the book is the authority
 * (CLAUDE.md 4.1).
 */
@Serializable
data class SavedSkill(
    val name: String,
    val specialization: String? = null,
    val points: Int,
) {
    init { require(points > 0) { "a purchased skill needs at least 1 point; '$name' had $points" } }
}

/**
 * What has been changed about a premade in play.
 *
 * The 23 published sheets are read-only, so an edit is stored as a DELTA
 * against one rather than as a replacement character: a null attribute means
 * "as printed". That keeps the `computed vs asPublished` diff honest -- it
 * still compares the engine against the published sheet, not against whatever
 * the player has since bought.
 */
@Serializable
data class SavedEdits(
    val st: Int? = null,
    val dx: Int? = null,
    val iq: Int? = null,
    val ht: Int? = null,
    val skills: List<SavedSkill> = emptyList(),
    /**
     * The chosen economic status ("Wealthy"), or null for the level the sheet
     * printed (Ch. 8, p. 80).
     *
     * The NAME only, exactly as a bought skill stores a name and its points:
     * the cost, the reaction modifier and the lifestyle belong to the book,
     * and a save file carrying its own copy could contradict it after a
     * content fix. Resolved through [com.pathways.rules.Wealth] on the way
     * back in, and dropped when the book prints no such level.
     */
    val wealth: String? = null,
    /**
     * The chosen appearance level ("Ugly"), or null for the sheet's own
     * (Ch. 3, p. 13-14).
     *
     * The NAME only, for the same reason [wealth] stores one. Unlike wealth
     * there is no zero-cost level to pick, so null here means ordinary looks
     * on all 23 premades -- none of them prints an appearance level at all.
     */
    val appearance: String? = null,
    /**
     * Traits bought in play, by NAME (Ch. 3's advantage and disadvantage
     * lists).
     *
     * The name alone, for the reason [SavedSkill] stores one: the cost and
     * whether it is an advantage belong to the book, and a save carrying its
     * own copy could contradict the catalogue after a content fix. Resolved
     * through [com.pathways.rules.PurchasableTraits] on the way back in.
     *
     * A name the book no longer prints is not billed and not applied, and is
     * NOT deleted from the file -- the book may print it again after a content
     * fix, which is exactly how a saved skill is treated (6as). That is the
     * opposite call to the three SPI skills, which are barred permanently and
     * so are dropped on load.
     */
    val traits: List<String> = emptyList(),
) {
    val isEmpty: Boolean
        get() = st == null && dx == null && iq == null && ht == null &&
            skills.isEmpty() && wealth == null && appearance == null && traits.isEmpty()
}

/**
 * One premade's walk down a pathway, as written to disk.
 *
 * Two separate losses, because they are two separate endings and neither
 * implies the other: [lost] is a failed Potion Consumption Roll (the Rampager,
 * p. 45), [corrupted] is CoR reaching the character's maximum (p. 45 again,
 * "the character is permanently lost. The GM takes full control").
 *
 * [corrupted] is STORED, not derived from `cor >= max`. The maximum is Will,
 * and a later potion can raise Will -- recomputing would hand a lost
 * character back to the player because their ceiling moved. The crossing is
 * latched when it happens.
 */
@Serializable
data class SavedSession(
    val beyonder: BeyonderState? = null,
    val lost: Boolean = false,
    val cor: Int = 0,
    val corrupted: Boolean = false,
    /**
     * Pools are stored as what has been LOST, not as what is left. The
     * maximums move -- a potion raises HT and with it FP, and SPI starts at
     * zero and is entirely a potion's gift -- so a stored current value would
     * silently mean something different after the next Sequence. A loss of 4
     * means the same thing at any maximum.
     */
    val hpLost: Int = 0,
    val fpLost: Int = 0,
    val spiSpent: Int = 0,
    val edits: SavedEdits = SavedEdits(),
)

/**
 * Remembers each premade's Beyonder state across restarts.
 *
 * ponytail: one JSON file, read and written on the calling thread. It holds
 * at most 23 tiny records and is touched on navigation, not per frame. Move it
 * off the main thread when there is a real character list, or when a profiler
 * says so -- not before.
 *
 * A save file is convenience, never authority: anything unreadable is
 * discarded and reported rather than crashing the app or silently
 * resurrecting half a character. Losing a digestion percentage is an
 * annoyance; refusing to start is a bug.
 */
class SessionStore(private val file: File) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun load(): Map<String, SavedSession> {
        if (!file.exists()) return emptyMap()
        return runCatching {
            json.decodeFromString<Map<String, SavedSession>>(file.readText())
                // A save written before ch. 9 p. 85 was applied can carry a
                // purchase the book forbids. Dropped HERE, at the boundary,
                // rather than at each reader: composition already skipped
                // them, but the raw list is what the sheet's bought-skill
                // rows iterate, so a stale entry came back as a rollable row
                // the moment a potion granted the same skill.
                .mapValues { (_, session) ->
                    session.copy(edits = session.edits.withoutForbiddenPurchases())
                }
        }.getOrElse { failure ->
            // A malformed or outdated save must not be a crash on launch. It
            // is dropped loudly here rather than quietly half-applied.
            Log.w(TAG, "discarding unreadable ${file.name}", failure)
            emptyMap()
        }
    }

    fun save(sessions: Map<String, SavedSession>) {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(sessions))
        }.onFailure { failure ->
            Log.w(TAG, "could not write ${file.name}", failure)
        }
    }

    private companion object {
        const val TAG = "SessionStore"
    }
}
