package com.pathways.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pathways.model.BeyonderState
import com.pathways.model.canDrink
import com.pathways.rules.Corruption
import com.pathways.rules.DigestedCharacter
import com.pathways.rules.PcrOutcome
import com.pathways.rules.PotionConsumption
import com.pathways.rules.RandomSource
import com.pathways.rules.PotionDigestion
import com.pathways.rules.PremadeSheet
import com.pathways.rules.content.ContentLoader
import com.pathways.rules.content.SequenceBlock

/** The Sequences a standard pathway covers, weakest first (Ch. 9-12). */
private val SEQUENCES = listOf(9, 8, 7, 6)

/**
 * Pick a pathway and Sequence, and see what drinking that potion does to this
 * character (M3).
 *
 * The screen shows grants, never a rewritten sheet: the potion's gains are
 * free, so the point budget shown with them is the character's own,
 * unchanged. That is the rule made visible -- if that line ever moves, the
 * engine has started billing gifts.
 */
@Composable
fun PathwayScreen(
    sheet: PremadeSheet,
    session: BeyonderSession,
    dice: RandomSource,
    onBack: () -> Unit,
) {
    val drunk = session.state
    var pcr by remember { mutableStateOf<PcrOutcome?>(null) }
    val blocks = remember { ContentLoader.pathways() }
    val pathways = remember(blocks) { blocks.map { it.pathway }.distinct().sorted() }
    // Opening the screen on the potion already drunk, rather than on the
    // pathway list, is the difference between a browser and a character sheet.
    var pathway by remember(drunk) { mutableStateOf(drunk?.pathway) }
    var sequence by remember(drunk) { mutableIntStateOf(drunk?.sequence ?: PotionDigestion.STARTING_SEQUENCE) }

    val block = pathway?.let { chosen ->
        blocks.single { it.pathway == chosen && it.sequence == sequence }
    }
    // Cumulative, not one potion in isolation: a Sequence 8 Fool drank the
    // Sequence 9 potion first and carries its SPI +9.
    val digested = remember(sheet, block) {
        block?.let { PotionDigestion.digestThrough(sheet.toCharacter(), it.pathway, it.sequence, blocks) }
    }
    // Maximum CoR is Will, and Will moves with the potions actually DRUNK --
    // not with whichever block is being browsed, which is why this asks about
    // a BeyonderState rather than reusing `digested`. Drinking asks it about
    // the state being drunk INTO, since that potion may move Will itself.
    val maxCorFor = remember(sheet, blocks) {
        { state: BeyonderState? ->
            val base = sheet.toCharacter()
            val walked = PotionDigestion.of(base.copy(beyonder = state), blocks)
            if (walked == null) Corruption.maxFor(base)
            else Corruption.maxFor(walked.secondaries.will, base.traits)
        }
    }
    val maxCor = maxCorFor(drunk)

    Scaffold { insets ->
        LazyColumn(Modifier.padding(insets).fillMaxSize()) {
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    TextButton(onClick = onBack) { Text("Back to sheet") }
                    Text(sheet.name, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        pathway?.let { "$it Pathway" } ?: "choose a pathway",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
                HorizontalDivider()
            }

            if (block == null || digested == null) {
                items(pathways) { name ->
                    Text(
                        "$name Pathway",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { pathway = name }
                            .padding(16.dp),
                    )
                    HorizontalDivider()
                }
            } else {
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        SEQUENCES.forEach { n ->
                            FilterChip(
                                selected = n == sequence,
                                onClick = { sequence = n },
                                label = { Text("Seq $n") },
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                    }
                    Row {
                        TextButton(
                            onClick = { pathway = null },
                            modifier = Modifier.padding(horizontal = 8.dp),
                        ) { Text("Choose another pathway") }
                        val chosen = BeyonderState(block.pathway, block.sequence)
                        when {
                            session.lost -> Note("lost to the Rampager")
                            session.corrupted -> Note("lost to corruption at ${session.cor} CoR")
                            drunk.canDrink(chosen) ->
                                TextButton(onClick = { pcr = session.drink(chosen, dice, maxCorFor) }) {
                                    Text(if (drunk == null) "Drink this potion" else "Drink and roll")
                                }
                            drunk?.pathway == chosen.pathway && drunk.sequence == chosen.sequence ->
                                Note("digesting: ${drunk.digestion}%")
                            // A pathway is walked one potion at a time, so
                            // every other block is readable but not drinkable.
                            drunk == null -> Note("a pathway begins at Sequence ${PotionDigestion.STARTING_SEQUENCE}")
                            else -> Note(
                                drunk.advanced()
                                    ?.let { "next: Sequence ${it.sequence} of the ${it.pathway} Pathway" }
                                    ?: "already at Sequence ${drunk.sequence}",
                            )
                        }
                    }
                    HorizontalDivider()
                }
                val isCurrentPotion = drunk != null &&
                    drunk.pathway == block.pathway &&
                    drunk.sequence == block.sequence
                if (isCurrentPotion && !session.isLost) {
                    item { DigestionTracker(drunk!!, session::digest) }
                }
                // CoR is not tied to the potion being browsed: it is the
                // character's, so it shows whenever they have walked at all.
                if (drunk != null) {
                    item { CorruptionTracker(session, maxCor) }
                }
                digestion(block, digested)
            }
        }
    }

    pcr?.let { PcrDialog(it, session.corrupted) { pcr = null } }
}

/**
 * The Acting Method, as a table plays it: the GM judges the session's acting
 * and moves the percentage.
 *
 * ponytail: a stepper, not a quality picker. Every acting quality prints a
 * RANGE ("+15-20%") and the book leaves the pick to the GM, so a button per
 * quality would have to invent a figure inside it. The ranges are shown
 * beside the stepper so the judgement stays informed and stays theirs.
 */
@Composable
private fun DigestionTracker(state: BeyonderState, onDigest: (Int) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Digesting this potion", style = MaterialTheme.typography.titleSmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onDigest(-5) }) { Text("-5%") }
            Text(
                "${state.digestion}%",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            TextButton(onClick = { onDigest(5) }) { Text("+5%") }
        }
        Text(
            "next potion rolls 3d6 ≤ ${PotionConsumption.targetFor(state.digestion)}" +
                if (state.isFullyDigested) "" else " — advancing now also costs 10+ CoR",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            ContentLoader.actingMethod().joinToString("   ") { quality ->
                val gain = if (quality.losesProgress) "0 or lose" else "+${quality.minGain}-${quality.maxGain}%"
                "${quality.quality} $gain"
            },
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/**
 * CoR, its ceiling, and how close the character is to it (Ch. 6, p. 45).
 *
 * ponytail: a +1/-1 stepper, same reasoning as the digestion tracker. Every
 * row of the book's CoR table is a range the GM picks inside ("1-3 CoR per
 * session") or an open floor ("10+"), so a button per row would have to
 * invent the figure. The table is printed beside the stepper instead, and the
 * one CoR cost the rules DO compute -- advancing before full digestion -- is
 * charged automatically when the potion is drunk.
 */
@Composable
private fun CorruptionTracker(session: BeyonderSession, max: Int) {
    val cor = session.cor
    val atRisk = session.corrupted || cor >= max - 1
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Corruption", style = MaterialTheme.typography.titleSmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { session.gainCor(-1, max) }, enabled = !session.isLost) { Text("−1") }
            Text(
                "$cor / $max",
                style = MaterialTheme.typography.titleLarge,
                color = if (atRisk) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            TextButton(onClick = { session.gainCor(1, max) }, enabled = !session.isLost) { Text("+1") }
        }
        Text(
            if (session.corrupted) {
                "At maximum CoR the character is permanently lost. The GM takes full control."
            } else {
                "maximum CoR is Will ($max); reaching it removes the character from play"
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (session.corrupted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        // Ritual-scoped, and labelled so: the book files this ladder under
        // "Corruption from Ritual Work", not under CoR in general.
        Corruption.ritualThresholdsReached(cor).forEach { threshold ->
            Text(
                "${threshold.cor} CoR (ritual work): ${threshold.effect}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Text(
            Corruption.gained().joinToString("   ") { "${it.action}: ${it.gain}" },
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** The Potion Consumption Roll, and what it cost. */
@Composable
private fun PcrDialog(outcome: PcrOutcome, corrupted: Boolean, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        title = {
            Text(
                when {
                    !outcome.integrated -> "Lost to the Rampager"
                    // Surviving the roll and being lost anyway is the book's
                    // own arithmetic: 10 CoR against a Will of 10 or less.
                    corrupted -> "The potion integrates — and takes them"
                    else -> "The potion integrates"
                },
            )
        },
        text = {
            Column {
                Text("3d6: ${outcome.roll.total} vs ${outcome.target} (digested ${outcome.digestion}%)")
                if (outcome.integrated && outcome.minimumCorGained > 0) {
                    Text(
                        "+${outcome.minimumCorGained} CoR charged for advancing before full " +
                            "digestion — the book's floor; the GM may charge more",
                    )
                }
                if (corrupted) {
                    Text(
                        "That reached their maximum CoR. The character is permanently lost; " +
                            "the GM takes full control.",
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                if (!outcome.integrated) {
                    Text(
                        "The character transforms into an NPC Rampager, permanently under the GM's control.",
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
    )
}

private fun LazyListScope.digestion(block: SequenceBlock, digested: DigestedCharacter) {
    item {
        Column(Modifier.padding(16.dp)) {
            Text("Sequence ${block.sequence}: ${block.name}", style = MaterialTheme.typography.titleLarge)
            if (block.sequence < PotionDigestion.STARTING_SEQUENCE) {
                Text(
                    "every potion from Sequence ${PotionDigestion.STARTING_SEQUENCE} down to " +
                        "${block.sequence}, digested in order",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                "point budget unchanged: ${digested.budget.spent} of ${digested.budget.budget} spent " +
                    "— the potion's gains are free",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        HorizontalDivider()

        // The rows are this potion's own; the totals beneath them are the
        // whole chain's, which is why the two are labelled differently.
        SectionHeader("Stats from this potion")
        if (block.stats.isEmpty()) {
            GrantRow("no stat gains", "", "")
        } else {
            block.stats.forEach { gain -> GrantRow(gain.attr, formatDelta(gain.delta), gain.note) }
        }
        if (digested.grants.spi != 0) {
            GrantRow("SPI after digestion", digested.effectiveSpi.toString(), "")
        }

        if (block.advantages.isNotEmpty()) {
            SectionHeader("Advantages")
            Prose(block.advantages)
        }
        if (block.drawbacks.isNotEmpty()) {
            SectionHeader("Drawbacks")
            Prose(block.drawbacks)
        }
    }

    if (block.skills.isNotEmpty()) {
        item { SectionHeader("Skills") }
        items(block.skills) { grant ->
            // The engine resolved each grant to a level; fall back to the
            // book's raw "+N" only if a grant somehow fails to match, rather
            // than showing a level that was never computed.
            //
            // The match is on the full identity, not the base name: the
            // grants map now carries earlier Sequences too, and Death grants
            // Hidden Lore (Spirits) at Seq 8 and Hidden Lore (Spirit World)
            // at Seq 6 -- a base-name match would show one row's level on the
            // other row.
            val base = grant.name.substringBefore(" (")
            val specialization =
                if (" (" in grant.name) grant.name.substringAfter(" (").removeSuffix(")") else null
            val level = digested.grants.skillLevels.entries
                .firstOrNull {
                    it.key.name == base &&
                        it.key.specialization == specialization &&
                        it.key.difficulty == grant.difficulty
                }
                ?.value
            GrantRow(grant.name, level?.toString() ?: "+${grant.bonus}", grant.note)
        }
    }

    item {
        SectionHeader("Ability")
        Prose(block.ability)
        if (block.reinforcedAbilities.isNotEmpty()) {
            SectionHeader("Reinforced abilities")
            Prose(block.reinforcedAbilities)
        }
    }
}

/** Why a potion is not drinkable, in the space its button would occupy. */
@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp))
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun GrantRow(label: String, value: String, note: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            if (note.isNotEmpty()) {
                Text(note, style = MaterialTheme.typography.bodySmall)
            }
        }
        Text(value, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End)
    }
}

@Composable
private fun Prose(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
}

/** "+2", "-1", "+0.25" — the book's own notation, without a trailing ".0". */
private fun formatDelta(delta: Double): String {
    val sign = if (delta < 0) "" else "+"
    return if (delta == delta.toInt().toDouble()) "$sign${delta.toInt()}" else "$sign$delta"
}
