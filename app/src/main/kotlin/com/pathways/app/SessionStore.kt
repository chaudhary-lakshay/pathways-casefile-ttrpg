package com.pathways.app

import android.util.Log
import com.pathways.model.BeyonderState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** One premade's walk down a pathway, as written to disk. */
@Serializable
data class SavedSession(
    val beyonder: BeyonderState? = null,
    val lost: Boolean = false,
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
