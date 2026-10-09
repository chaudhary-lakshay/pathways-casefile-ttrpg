package com.pathways.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pathways.model.BeyonderState
import com.pathways.model.Trait
import com.pathways.model.canDrink
import com.pathways.rules.Corruption
import com.pathways.rules.DigestedCharacter
import com.pathways.rules.PcrOutcome
import com.pathways.rules.PotionConsumption
import com.pathways.rules.RandomSource
import com.pathways.rules.PotionDigestion
import com.pathways.rules.TraitEffects
import com.pathways.rules.PremadeSheet
import com.pathways.rules.ScoreFloors
import com.pathways.rules.content.ContentLoader
import com.pathways.rules.content.SequenceBlock

/** The Sequences a standard pathway covers, weakest first (Ch. 9-12). */
private val SEQUENCES = listOf(9, 8, 7, 6)

/**
 * Exchange two entries, leaving the list alone if either is absent.
 *
 * The chooser is otherwise alphabetical, and alphabetical puts "Wheel of
 * Fortune" before "White Tower" -- an 'e' sorting under an 'i' four letters
 * in. The owner asked for those two exchanged, so this is a DISPLAY order and
 * carries no rules meaning: nothing downstream reads the position, and the
 * pathway a potion belongs to is matched by name.
 *
 * Returning the list unchanged when a name is missing matters because the
 * content file, not this screen, decides which pathways exist -- a renamed row
 * should reorder nothing rather than throw.
 */
private fun List<String>.swapping(a: String, b: String): List<String> {
    val i = indexOf(a)
    val j = indexOf(b)
    if (i < 0 || j < 0) return this
    return toMutableList().also { it[i] = b; it[j] = a }
}

/**
 * Pick a pathway and Sequence, and see what drinking that potion does to this
 * character (M3).
 *
 * The screen shows grants, never a rewritten sheet: the potion's gains are
 * free, so the point budget shown with them is the character's own,
 * unchanged. That is the rule made visible -- if that line ever moves, the
 * engine has started billing gifts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PathwayScreen(
    sheet: PremadeSheet,
    session: BeyonderSession,
    dice: RandomSource,
    onBack: () -> Unit,
) {
    val drunk = session.state
    // What this composition drew, for the one button that cannot be undone:
    // Drink acts only while the session still matches both (see stillShows).
    val printedCor = session.cor
    var pcr by remember { mutableStateOf<PcrOutcome?>(null) }
    val blocks = remember { ContentLoader.pathways() }
    val pathways = remember(blocks) {
        blocks.map { it.pathway }.distinct().sorted().swapping("Wheel of Fortune", "White Tower")
    }
    // Opening the screen on the potion already drunk, rather than on the
    // pathway list, is the difference between a browser and a character sheet.
    var pathway by remember(drunk) { mutableStateOf(drunk?.pathway) }
    var sequence by remember(drunk) { mutableIntStateOf(drunk?.sequence ?: PotionDigestion.STARTING_SEQUENCE) }

    val block = pathway?.let { chosen ->
        blocks.single { it.pathway == chosen && it.sequence == sequence }
    }
    // The traits as bought, builder edits included: the expression the sheet
    // screen reads. Read here for Knows the Acting Method (6ez).
    val traits = remember(sheet, session.edits) { session.characterFor(sheet).traits }
    // Cumulative, not one potion in isolation: a Sequence 8 Fool drank the
    // Sequence 9 potion first and carries its SPI +9.
    val digested = remember(sheet, block, session.edits) {
        block?.let {
            PotionDigestion.digestThrough(session.characterFor(sheet), it.pathway, it.sequence, blocks)
        }
    }
    // Maximum CoR is Will, and Will moves with the potions actually DRUNK --
    // not with whichever block is being browsed, which is why this asks about
    // a BeyonderState rather than reusing `digested`. Drinking asks it about
    // the state being drunk INTO, since that potion may move Will itself.
    val maxCorFor = remember(sheet, session.edits) {
        { state: BeyonderState? -> session.edits.corCeilingOn(sheet, state) }
    }
    val maxCor = maxCorFor(drunk)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            sheet.name,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            pathway?.let { "$it Pathway" } ?: "Choose a pathway",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        // A real icon. This was a "←" glyph in a Text, from
                        // before material-icons-extended was on the classpath;
                        // the reason expired the moment the action hub needed
                        // twelve icons and the dependency landed.
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to sheet",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { insets ->
        LazyColumn(
            Modifier.padding(insets).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (block == null || digested == null) {
                // A GRID, not rows, and the reasoning is the opposite of the
                // character gallery's on purpose. A character row earns its
                // full width because it carries five stats and two traits; a
                // pathway carries a name and a sigil, so a full-width row is
                // nine-tenths empty. The sigil is also the identifier here --
                // each pathway has its own colour, so a grid is scannable
                // before a word is read. Three columns puts all 22 in three
                // screens instead of eight.
                item {
                    FlowRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        maxItemsInEachRow = 3,
                    ) {
                        for (name in pathways) {
                            PathwayTile(name, Modifier.weight(1f)) { pathway = name }
                        }
                    }
                }
            } else {
                // The chosen pathway's card, large, beside its own Sequences.
                // A player walking a pathway looks at this screen more than any
                // other, and it had nothing on it to say WHICH pathway except a
                // line of text.
                item {
                    val art = pathwayArt(block.pathway)
                    if (art != null) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Image(
                                painter = painterResource(art),
                                contentDescription = "${block.pathway} Pathway",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.size(112.dp),
                            )
                            Column(Modifier.padding(start = 14.dp)) {
                                Text(
                                    "${block.pathway} Pathway",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    "Sequence ${block.sequence}: ${block.name}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
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
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = { pathway = null },
                            modifier = Modifier.padding(end = 8.dp),
                        ) { Text("Another pathway") }
                        val chosen = BeyonderState(block.pathway, block.sequence)
                        when {
                            session.lost -> Note("lost to the Rampager")
                            session.corrupted -> Note("lost to corruption at ${session.cor} CoR")
                            // Filled, because this is what the screen is FOR.
                            // It was a text button sitting beside "Choose
                            // another pathway", weighted identically to the
                            // control that throws the choice away.
                            drunk.canDrink(chosen) ->
                                Column(Modifier.weight(1f)) {
                                    val ceilingAfter = maxCorFor(chosen)
                                    Button(
                                        // `drink` refuses this too, before it
                                        // rolls; the line below says why.
                                        enabled = ScoreFloors.holdsCeiling(ceilingAfter),
                                        onClick = {
                                            // A second tap, or a CoR step, can
                                            // reach this handler before the
                                            // screen redraws. `drink` refuses
                                            // a state it was not offered by
                                            // throwing, so a stale tap does
                                            // nothing instead.
                                            if (session.stillShows(drunk, printedCor)) {
                                                pcr = session.drink(chosen, dice, maxCorFor)
                                            }
                                        },
                                    ) {
                                        Text(if (drunk == null) "Drink this potion" else "Drink and roll")
                                    }
                                    // The roll, beside the button that makes
                                    // it: the tracker that prints it is on the
                                    // PREVIOUS block. Built from what
                                    // stillShows compares, so it is the roll
                                    // the tap makes or the tap does nothing.
                                    drinkLine(drunk, printedCor, ceilingAfter)?.let { line ->
                                        Text(
                                            line,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.padding(top = 4.dp, end = 16.dp, bottom = 8.dp),
                                        )
                                    }
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
                    item {
                        DigestionTracker(drunk!!, traits, session::digest)
                    }
                }
                // CoR is not tied to the potion being browsed: it is the
                // character's, so it shows whenever they have walked at all.
                if (drunk != null) {
                    item { CorruptionTracker(session, drunk, maxCor, traits) }
                }
                digestion(block, digested)
            }
        }
    }

    pcr?.let { PcrDialog(it, session.corrupted) { pcr = null } }
}

/**
 * One pathway in the chooser: its sigil, then its name.
 *
 * The sigil is given the room because it is what tells 22 pathways apart at a
 * glance -- each carries its own colour, where the names are all one word in
 * one typeface. The name still shows, because a symbol nobody has learnt yet
 * identifies nothing on a first visit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PathwayTile(name: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        modifier = modifier,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val art = pathwayArt(name)
            if (art != null) {
                Image(
                    painter = painterResource(art),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(64.dp),
                )
            }
            Text(
                name,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
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
private fun DigestionTracker(
    state: BeyonderState,
    /** Read for Knows the Acting Method, which doubles the table (6ez). */
    traits: List<Trait>,
    onDigest: (Int) -> Unit,
) {
    val gains = remember(traits) { TraitEffects.actingGains(traits) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Digesting this potion", style = MaterialTheme.typography.titleSmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Outlined and neutral, matching the sheet's pool steppers. These
            // were brass text buttons, so the same control looked like two
            // different controls depending which screen you were on.
            OutlinedButton(onClick = { onDigest(-5) }) { Text("-5%") }
            Text(
                "${state.digestion}%",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            OutlinedButton(onClick = { onDigest(5) }) { Text("+5%") }
        }
        Text("next potion rolls ${pcrLine(state)}", style = MaterialTheme.typography.bodySmall)
        Text(
            gains.joinToString("   ") { quality ->
                val gain = if (quality.losesProgress) "0 or lose" else "+${quality.minGain}-${quality.maxGain}%"
                "${quality.quality} $gain"
            },
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp),
        )
        // Said, because a doubled range reads as a misprint of the book's.
        if (TraitEffects.doublesActingGains(traits)) {
            Text(
                "${TraitEffects.ACTING_METHOD}: \"all Digestion Gain Per Session is doubled\".",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/**
 * The Potion Consumption Roll [state] would make, and what it charges.
 *
 * One line for two blocks: the tracker on the potion being digested, and the
 * Drink button on the next one. The cost is charged on success only, and it
 * is the book's floor ("10+ CoR"), which is what [BeyonderSession.drink]
 * charges.
 */
internal fun pcrLine(state: BeyonderState): String {
    val cost = PotionConsumption.corCostFor(state)
    return "3d6 ≤ ${PotionConsumption.targetFor(state.digestion)}" +
        if (cost == 0) "" else " — on success +$cost CoR minimum; the GM may charge more"
}

/**
 * What the Drink button says about its own roll.
 *
 * [ceilingAfter] is the maximum CoR of the potion being drunk INTO, the one
 * `drink` charges against. A character can survive the roll and be lost on
 * the same tap -- the floor of 10 against a Will of 10, or a potion that
 * lowers Will onto the total at no cost -- and this is the last screen that
 * can say so.
 *
 * A [ceilingAfter] below the floor is the reason Drink is greyed out, and is
 * printed rather than asked about: [Corruption] throws on it, and this runs
 * while the block is being DRAWN. Abyss Sequence 9 lowers Will, so a mortal's
 * first potion can be refused too -- the one case a mortal ([state] null),
 * who makes no roll, gets a line at all.
 */
internal fun drinkLine(state: BeyonderState?, cor: Int, ceilingAfter: Int): String? {
    val roll = state?.let { "at ${it.digestion}% digested, rolls ${pcrLine(it)}" }
    if (!ScoreFloors.holdsCeiling(ceilingAfter)) {
        val refused = "CoR ceiling after this potion would be $ceilingAfter, and this app keeps it at " +
            "${ScoreFloors.MIN_COR_CEILING} or more — it cannot be drunk until the ceiling is raised"
        return if (roll == null) refused else "$roll. $refused"
    }
    if (state == null) return null
    val cost = PotionConsumption.corCostFor(state)
    return roll +
        if (Corruption.isLostAfter(cor, cost, ceilingAfter)) {
            ". Maximum CoR after this potion is $ceilingAfter; ${cor + cost} CoR reaches it — " +
                "lost to corruption even if the potion integrates"
        } else {
            ""
        }
}

/**
 * CoR, its ceiling, and how close the character is to it (Ch. 6, p. 45).
 *
 * ponytail: a +1/-1 stepper, same reasoning as the digestion tracker. Most
 * rows of the book's CoR table are a range the GM picks inside ("1-3 CoR per
 * session") or an open floor ("10+"), so a button per row would have to
 * invent the figure; the other two are 1 per use and a 1d roll. The table is printed beside the stepper instead. The
 * costs the rules DO compute are charged where they happen: advancing before
 * full digestion, a ritual's or a boon's critical failure, and Soul Debt.
 *
 * A holder of Cleansed Spirit is told here that none of those is halved
 * ([TraitEffects.corruptionNotes], 6fd): the book lists no source as passive.
 */
@Composable
private fun CorruptionTracker(
    session: BeyonderSession,
    drunk: BeyonderState,
    max: Int,
    traits: List<Trait>,
) {
    // [max] is the ceiling of the potion this composition drew. A Drink that
    // lands before the redraw can LOWER it, and a step taken against the old
    // one would miss the loss -- so a step waits for the screen to catch up.
    val step = { delta: Int -> if (session.onSamePotion(drunk)) session.gainCor(delta, max) }
    val cor = session.cor
    val atRisk = session.corrupted || cor >= max - 1
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Corruption", style = MaterialTheme.typography.titleSmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { step(-1) }, enabled = !session.isLost) { Text("−1") }
            Text(
                "$cor / $max",
                style = MaterialTheme.typography.titleLarge,
                color = if (atRisk) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            OutlinedButton(onClick = { step(1) }, enabled = !session.isLost) { Text("+1") }
        }
        Text(
            if (session.corrupted) {
                "At maximum CoR the character is permanently lost. The GM takes full control."
            } else {
                // Not "Will ($max)": Spiritual Fortitude makes it Will + 3,
                // and the label read "Will (13)" beside a Will of 10.
                "maximum CoR is $max; reaching it removes the character from play"
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
        TraitEffects.corruptionNotes(traits).forEach {
            Text(
                "${it.trait}: ${it.printed} Not halved here: ${it.reason}.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
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
