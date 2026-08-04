package com.pathways.app

import com.pathways.model.Attr
import com.pathways.model.Character
import com.pathways.model.PurchasedSkill
import com.pathways.model.SkillKey
import com.pathways.rules.Wealth
import com.pathways.rules.content.ContentLoader
import com.pathways.rules.content.SkillDefinition
import com.pathways.rules.content.WealthLevel

/**
 * Turning a published premade plus a session's edits into a character.
 *
 * The 23 sheets are read-only, so nothing here writes to them: the edits are
 * a delta, and [applyTo] is where the two meet. Every screen that needs the
 * character a player is actually running goes through this, while the
 * `computed vs asPublished` diff keeps using the unedited sheet -- that
 * comparison is about the engine against the book, and a bought skill is
 * neither.
 */
fun SavedEdits.applyTo(base: Character): Character {
    if (isEmpty) return base
    val attributes = base.attributes.copy(
        st = st ?: base.attributes.st,
        dx = dx ?: base.attributes.dx,
        iq = iq ?: base.attributes.iq,
        ht = ht ?: base.attributes.ht,
    )
    return base.copy(
        attributes = attributes,
        traits = wealthLevel()?.let { Wealth.applied(base.traits, it) } ?: base.traits,
        skills = base.skills + purchases(base),
    )
}

/**
 * The saved economic status, resolved against the book (Ch. 8, p. 80).
 *
 * A level the book does not print is DROPPED rather than guessed at, the same
 * way a saved skill it no longer prints is: the nearest wrong answer here is
 * Average, and taking it would move a character's budget by up to 25 points
 * without saying so.
 */
fun SavedEdits.wealthLevel(): WealthLevel? =
    wealth?.let { name -> Wealth.all().firstOrNull { it.status.equals(name, ignoreCase = true) } }

/**
 * The economic status to SHOW for [character]: the level chosen in play, or
 * the one the sheet printed.
 *
 * The choice is asked first because Average costs 0 pts and therefore leaves
 * no trait behind (see [Wealth.traitFor]). Reading the composed character
 * alone would report a player who deliberately picked Average as having no
 * wealth level at all -- and on a sheet that printed Poor, the budget would
 * have moved by 15 points with nothing on screen saying why (Codex).
 *
 * A saved level the book does not print falls through to the printed one,
 * which is what [SavedEdits.applyTo] did with it too.
 */
fun SavedEdits.wealthShownFor(character: Character): WealthLevel? =
    wealthLevel() ?: Wealth.levelOf(character.traits)

/**
 * The level of [name] on this character, or null if they do not have it.
 *
 * Matches on base name only: a caster looking for "Ritualistic Magic" does not
 * care which specialization is written beside it.
 */
fun skillLevelOf(name: String, levels: Map<SkillKey, Int>): Int? =
    levels.entries.firstOrNull { it.key.name.equals(name, ignoreCase = true) }?.value

/**
 * The book's definition of [name], or null when it prints no such skill.
 *
 * When a [specialization] is given it must MATCH: five names carry several
 * rows (Guns has four, Navigation three), and falling back to the first would
 * resolve a saved "Guns (Rifle)" as "Guns (Pistol)". A specializable template
 * is the one exception -- its specialization is the player's to pick, so the
 * printed row is the right answer for any of them.
 */
fun definitionOf(name: String, specialization: String? = null): SkillDefinition? {
    val byName = ContentLoader.skills().filter { it.name.equals(name, ignoreCase = true) }
    if (specialization == null) return byName.firstOrNull()
    return byName.firstOrNull { it.specialization == specialization }
        ?: byName.firstOrNull { it.specializable }
}

/**
 * The saved skills as purchases, resolved against the book.
 *
 * A saved skill the book no longer prints is DROPPED rather than guessed at:
 * inventing a controlling attribute and difficulty for it would make up a
 * rule, and billing points for a skill the sheet cannot show would charge for
 * nothing. A skill the premade already owns is also dropped, because
 * [Character] rejects two purchases of one key and those points belong on the
 * existing entry.
 */
private fun SavedEdits.purchases(base: Character): List<PurchasedSkill> {
    val owned = base.skills.map { it.key.name.lowercase() to it.key.specialization }.toSet()
    return skills.mapNotNull { saved ->
        val definition = definitionOf(saved.name, saved.specialization) ?: return@mapNotNull null
        val key = SkillKey(
            name = definition.name,
            specialization = saved.specialization ?: definition.specialization,
            attr = definition.attr,
            difficulty = definition.difficulty,
        )
        if ((key.name.lowercase() to key.specialization) in owned) null
        else PurchasedSkill(key, saved.points)
    }
}

/** [Attr] as the four nullable fields [SavedEdits] stores. */
fun SavedEdits.levelOf(attr: Attr): Int? = when (attr) {
    Attr.ST -> st
    Attr.DX -> dx
    Attr.IQ -> iq
    Attr.HT -> ht
}

fun SavedEdits.withAttribute(attr: Attr, level: Int): SavedEdits = when (attr) {
    Attr.ST -> copy(st = level)
    Attr.DX -> copy(dx = level)
    Attr.IQ -> copy(iq = level)
    Attr.HT -> copy(ht = level)
}
