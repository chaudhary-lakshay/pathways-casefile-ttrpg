package com.pathways.app

import android.util.Log
import com.pathways.model.BeyonderState
import com.pathways.rules.InstancedPurchase
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
    /**
     * Traits bought at a price the book leaves to the instance (Ch. 3).
     *
     * A separate list rather than a widening of [traits], and that is a
     * compatibility decision rather than a style one: [traits] is a
     * `List<String>` in every save already written, and turning it into a list
     * of objects makes those saves fail to decode -- at which point
     * [SessionStore.load] discards the WHOLE file and every character loses
     * their walk. Additive and defaulted is the only shape that leaves an old
     * save readable.
     */
    val instanced: List<SavedInstancedTrait> = emptyList(),
    /**
     * Secondary characteristics bought in play (Ch. 2, pp. 8-9), as the
     * book's own row key to the number of STEPS bought.
     *
     * The key is the one `secondary_costs.json` prints -- "hp", "basicSpeed"
     * -- and it is a stable save-file identifier from here on, not an
     * incidental content label: renaming a row in that file stops applying
     * every save that named it, silently (Codex, on the design). Basic Speed
     * counts quarters, because that is the step the book prices.
     *
     * A map of numbers rather than a [com.pathways.model.SecondaryDelta],
     * which rejects a magnitude over 100 in its constructor: a save file is
     * hand-editable and [SessionStore.load] discards the WHOLE file on a
     * decode failure, so one bad number would delete every other edit. The
     * steps are coerced and unknown or unpriced keys dropped on the way back
     * in, by [com.pathways.rules.SecondaryCharacteristics.plusSteps], which
     * is the same rule [traits] follows -- the book resolves what a save
     * merely names.
     */
    val secondaries: Map<String, Int> = emptyMap(),
    /**
     * The weapons carried in play, by their chapter-8 catalogue NAME
     * (p. 80).
     *
     * The name alone, for the reason [traits] stores one: the damage, the
     * reach, the skill that arms it and whether it parries at all belong to
     * the book, and a save carrying its own copy could contradict the
     * catalogue after a content fix. Resolved through
     * [com.pathways.rules.Weapons] on the way back in; a name the book no
     * longer prints is skipped and NOT deleted, because a content fix may
     * print it again (6as).
     *
     * Possession, not training: a character may carry a weapon they have no
     * skill for, and the defence dialog then prints no parry row for it
     * because the book's formula is `skill / 2 + 3` and there is no skill to
     * halve. Chapter 8 prices weapons in money, which this app does not
     * track, so nothing here spends points.
     */
    val weapons: List<String> = emptyList(),
    /**
     * Languages bought in play (Ch. 3, pp. 13-14), in the order bought.
     *
     * Three NAMES and no price, for the reason [traits] stores a name: the
     * points belong to the book's two tables, and they move when Language
     * Talent or Multilingual is bought or sold. Resolved through
     * [com.pathways.rules.Languages.owned] on the way back in; a language,
     * proficiency or form the book does not print bills nothing and is NOT
     * deleted. The order is kept because Multilingual frees the first two
     * that qualify.
     */
    val languages: List<SavedLanguage> = emptyList(),
) {
    val isEmpty: Boolean
        get() = st == null && dx == null && iq == null && ht == null &&
            skills.isEmpty() && wealth == null && appearance == null &&
            traits.isEmpty() && instanced.isEmpty() && secondaries.isEmpty() &&
            weapons.isEmpty() && languages.isEmpty()
}

/** One bought language, as written to disk: the book's names, never a cost. */
@Serializable
data class SavedLanguage(val language: String, val proficiency: String, val form: String)

/**
 * A trait whose price the book leaves to the instance, as written to disk.
 *
 * The PARTS are stored and the displayed name is composed on the way back in
 * -- "Shyness (Mild)" is a spelling the published sheets use where the book
 * prints only "Shyness", so persisting it would make an app-authored string
 * into canonical data (Codex, on the design). [cost] is stored because it is
 * the player's choice inside the book's printed interval and cannot be derived
 * from the name; it is re-validated against that interval on every load, so a
 * hand-edited save cannot widen what the book allows.
 *
 * [variant] and [instance] are exclusive: a row whose cell enumerates priced
 * options is settled by naming one, and every other row by describing the
 * instance.
 */
@Serializable
data class SavedInstancedTrait(
    val row: String,
    val variant: String? = null,
    val instance: String? = null,
    val cost: Int,
) {
    /**
     * What makes two saved purchases the same purchase, deferred to the rules
     * layer so this file cannot hold a second opinion about it.
     *
     * The COST is deliberately not part of it. Two entries differing only in
     * price are one trait bought twice, and composition would bill the first
     * while the builder listed both -- a screen disagreeing with the character
     * it describes.
     */
    val key: String
        get() = InstancedPurchase(row, variant, instance, cost).key
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
    val pendingPcr: BeyonderState? = null,
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
