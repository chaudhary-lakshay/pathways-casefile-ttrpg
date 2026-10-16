package com.pathways.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pathways.model.BeyonderState
import com.pathways.rules.Pool
import com.pathways.model.Trait
import com.pathways.model.canDrink
import com.pathways.rules.Corruption
import com.pathways.rules.DigestedCharacter
import com.pathways.rules.PcrOutcome
import com.pathways.rules.PotionConsumption
import com.pathways.rules.PotionDigestion
import com.pathways.rules.PremadeSheet
import com.pathways.rules.RandomSource
import com.pathways.rules.ScoreFloors
import com.pathways.rules.TraitEffects
import com.pathways.rules.TraitGrants
import com.pathways.rules.TraitReport
import com.pathways.rules.content.ActingQuality
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
    var tab by remember { mutableIntStateOf(0) }
    val blocks = remember { ContentLoader.pathways() }
    val pathways = remember(blocks) {
        blocks.map { it.pathway }.distinct().sorted().swapping("Wheel of Fortune", "White Tower")
    }
    // Opening the screen on the potion already drunk, rather than on the
    // pathway list, is the difference between a browser and a character sheet.
    // Keyed on the potion, not the whole state: a digestion step or a
    // drawback chip (6he, on Sequence 9 while at 8) must not jump the view.
    var pathway by remember(drunk?.pathway, drunk?.sequence) { mutableStateOf(drunk?.pathway) }
    var sequence by remember(drunk?.pathway, drunk?.sequence) {
        mutableIntStateOf(drunk?.sequence ?: PotionDigestion.STARTING_SEQUENCE)
    }

    val block = pathway?.let { chosen ->
        blocks.single { it.pathway == chosen && it.sequence == sequence }
    }
    // The traits in play, the list the sheet screen reads (6fk): bought, with
    // builder edits, plus what the potions granted. Read here for Knows the
    // Acting Method (6ez) and Luck (6fg); no potion grants either today, and
    // one list means none has to be noticed the day one does (ecc review).
    val traits = remember(sheet, session.edits, drunk) { session.traitsInPlay(sheet) }
    // Who may reroll a failed PCR: read against the potion the failure left
    // the character on, which is the one a Lucky One's grant comes from (6fg).
    val rerollSource = TraitEffects.rerollSource(traits, drunk)
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
    // The SPI the potions DRUNK give, as the sheet's tracker reads it: what a
    // held power is paid from, never the browsed block's.
    val maxSpi = remember(sheet, drunk, session.edits) {
        session.characterFor(sheet).digestedWith(drunk)?.effectiveSpi ?: 0
    }

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
                            PathwayTile(name, Modifier.weight(1f)) { pathway = name; tab = 0 }
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
                                // The potion leads; the pathway is already in the top bar.
                                Text(block.name, style = MaterialTheme.typography.headlineSmall)
                                Text(
                                    "Sequence ${block.sequence} · ${block.pathway} Pathway",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                item {
                    Row(
                        Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(horizontal = 4.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        SEQUENCES.forEach { n ->
                            SequenceTile(
                                sequence = n,
                                potion = blocks.single { it.pathway == block.pathway && it.sequence == n }.name,
                                selected = n == sequence,
                                // Cumulative: a Sequence 8 drank the 9 first.
                                drunk = drunk?.pathway == block.pathway && n >= drunk.sequence,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                // Picking a potion is asking to read it.
                                onClick = { sequence = n; tab = 0 },
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
                        val chosen = potionOn(block, drunk)
                        when {
                            // Luck's reroll of the failed PCR, on the block
                            // it attempted (6fg). Saved, so it survives a
                            // restart; who may spend it is the rules'.
                            session.lost && session.pendingPcr == chosen && rerollSource != null ->
                                Column(Modifier.weight(1f)) {
                                    // Drink's gate, because it is Drink's roll:
                                    // Will lowered since the failure can leave
                                    // this potion no ceiling, and rerollPcr
                                    // refuses that by throwing (Codex's HIGH).
                                    val ceilingAfter = maxCorFor(chosen)
                                    val rerollable = ScoreFloors.holdsCeiling(ceilingAfter)
                                    Button(
                                        enabled = rerollable,
                                        onClick = {
                                            // A second tap finds nothing pending.
                                            if (session.pendingPcr == chosen &&
                                                ScoreFloors.holdsCeiling(maxCorFor(chosen))
                                            ) {
                                                pcr = session.rerollPcr(dice, maxCorFor)
                                            }
                                        },
                                    ) { Text("Reroll the PCR ($rerollSource)") }
                                    drinkLine(drunk, session.cor, ceilingAfter)?.let { line ->
                                        Text(
                                            line,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.padding(top = 4.dp, end = 16.dp),
                                        )
                                    }
                                    Note("lost to the Rampager, unless the reroll succeeds; the GM counts the session's rerolls")
                                }
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
                                Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                                    Text(
                                        "Digestion ${drunk.digestion}%",
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                    LinearProgressIndicator(
                                        progress = { drunk.digestion / 100f },
                                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                    )
                                }
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
                // Two tabs once a potion is drunk: what this potion grants, and
                // the character's own digestion and corruption. Stacked, the
                // trackers pushed every potion's text below the fold.
                if (drunk != null) {
                    item {
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            listOf("Potion", "Digestion & corruption").forEachIndexed { i, label ->
                                SegmentedButton(
                                    selected = tab == i,
                                    onClick = { tab = i },
                                    shape = SegmentedButtonDefaults.itemShape(i, 2),
                                ) { Text(label) }
                            }
                        }
                    }
                }
                if (drunk != null && tab == 1) {
                    if (isCurrentPotion && !session.isLost) {
                        item { DigestionTracker(drunk, traits, session::digest, session::act) }
                    }
                    // CoR is not tied to the potion being browsed: it is the
                    // character's, so it shows whenever they have walked at all.
                    item { CorruptionTracker(session, drunk, maxCor, traits) }
                } else {
                    // None for a lost character, so a disabled chip only
                    // ever means the row was bought.
                    val choices = drunk?.takeIf { it.pathway == block.pathway && !session.isLost }
                        ?.let { TraitGrants.drawbackChoiceTable()[block.pathway to block.sequence] }
                    potionContent(
                        block,
                        digested,
                        choices?.let {
                            {
                                DrawbackPicker(
                                    choices = it,
                                    chosen = drunk.drawback,
                                    pathway = block.pathway,
                                    canChoose = { name -> session.canChooseDrawback(sheet, name) },
                                    // An absolute setter, so a second tap is
                                    // harmless; the guard drops a stale one.
                                    onChoose = { name ->
                                        if (session.canChooseDrawback(sheet, name)) session.chooseDrawback(sheet, name)
                                    },
                                )
                            }
                        },
                        // A power is the character's once its potion is drunk:
                        // this Sequence or an earlier one of the pathway walked.
                        spend = drunk?.takeIf {
                            it.pathway == block.pathway && block.sequence >= it.sequence && !session.isLost
                        }?.let {
                            AbilitySpend(left = maxSpi - session.spiSpent) { price, drawnLeft ->
                                // A stale tap of the same picture: the SPI it
                                // was drawn with is already spent (6ei).
                                val left = maxSpi - session.spiSpent
                                if (left == drawnLeft && left >= price) session.spend(Pool.SPI, price, maxSpi, maxCor)
                            }
                        },
                    )
                }
            }
        }
    }

    pcr?.let { PcrDialog(it, session.corrupted, if (session.pendingPcr != null) rerollSource else null) { pcr = null } }
}

/**
 * The potion [block] offers [drunk]: its pathway and Sequence, carrying the
 * walked chain's chosen drawback (6he), because [canDrink] compares the
 * whole state and the next potion keeps the choice.
 */
internal fun potionOn(block: SequenceBlock, drunk: BeyonderState?): BeyonderState =
    BeyonderState(block.pathway, block.sequence, drawback = drunk?.takeIf { it.pathway == block.pathway }?.drawback)

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
 * A stepper, and a tap per quality that adds the low end of its RANGE
 * ("+15-20%"), the owner's call after play session 3: the GM steps +5% for
 * the rest of the span. "None" stays a tag, its loss the GM's to step.
 */
@Composable
private fun DigestionTracker(
    state: BeyonderState,
    /** Read for Knows the Acting Method, which doubles the table (6ez). */
    traits: List<Trait>,
    onDigest: (Int) -> Unit,
    onAct: (ActingQuality, drawnDigestion: Int) -> Unit,
) {
    val gains = remember(traits) { TraitEffects.actingGains(traits) }
    StepperCard(
        title = "Digesting this potion",
        minus = "-5%",
        plus = "+5%",
        value = "${state.digestion}%",
        onMinus = { onDigest(-5) },
        onPlus = { onDigest(5) },
    ) {
        Text(
            "next potion rolls ${pcrLine(state)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            // ponytail: two lines, the tablet's longest; a phone can wrap to
            // three. At 100% the line shrank to one and the bands below,
            // tapped repeatedly, moved under the finger (play session 4).
            minLines = 2,
        )
        // One chip a quality: the book prints a range and no verdict.
        FlowRow(
            Modifier.padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Drawn at this digestion: a second tap of it is dropped.
            val drawn = state.digestion
            gains.forEach { quality ->
                if (quality.losesProgress) {
                    ChipTag("${quality.quality} 0 or lose")
                } else {
                    AssistChip(
                        onClick = { onAct(quality, drawn) },
                        label = { Text("${quality.quality} +${quality.minGain}-${quality.maxGain}%") },
                    )
                }
            }
        }
        // Said, because a doubled range reads as a misprint of the book's.
        if (TraitEffects.doublesActingGains(traits)) {
            Text(
                "${TraitEffects.ACTING_METHOD}: \"all Digestion Gain Per Session is doubled\".",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
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
 * invent the figure; the other two are 1 per use and a 1d roll. The table
 * is printed beside the stepper instead. The
 * costs the rules DO compute are charged where they happen: advancing before
 * full digestion, a ritual's or a boon's critical failure, and Soul Debt.
 *
 * A holder of Cleansed Spirit is told here that none of those is halved
 * ([TraitReport.corruptionNotes], 6fd): the book lists no source as passive.
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
    StepperCard(
        title = "Corruption",
        minus = "−1",
        plus = "+1",
        value = "$cor / $max",
        onMinus = { step(-1) },
        onPlus = { step(1) },
        enabled = !session.isLost,
        valueColor = if (atRisk) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
    ) {
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
        HorizontalDivider(Modifier.padding(top = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
        RuleNote("What raises corruption") {
            Corruption.gained().forEach {
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(it.action, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(it.gain, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary)
                }
            }
        }
        TraitReport.corruptionNotes(traits).forEach {
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
private fun PcrDialog(
    outcome: PcrOutcome,
    corrupted: Boolean,
    /** What may reroll this failure, while one is unspent ([TraitEffects.rerollSource]). */
    rerollSource: String?,
    onDismiss: () -> Unit,
) {
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
                    rerollSource?.let {
                        Text(
                            "$it may reroll this failed roll: the potion's block offers it. " +
                                "How many rerolls are left this session is the GM's count.",
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        },
    )
}

/**
 * One rung of the pathway's ladder: its number and potion, filled when it is
 * the block being read, and checked when the character has drunk it.
 */
@Composable
private fun SequenceTile(
    sequence: Int,
    potion: String,
    selected: Boolean,
    drunk: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = if (selected) scheme.secondaryContainer else scheme.surfaceContainer,
        contentColor = if (selected) scheme.onSecondaryContainer else scheme.onSurface,
        shape = RoundedCornerShape(10.dp),
        border = if (selected) null else BorderStroke(1.dp, scheme.outlineVariant),
        modifier = modifier.selectable(selected = selected, role = Role.Tab, onClick = onClick),
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Seq $sequence",
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalContentColor.current.copy(alpha = 0.8f),
                    modifier = Modifier.weight(1f),
                )
                if (drunk) {
                    Icon(Icons.Filled.Check, contentDescription = "drunk", modifier = Modifier.size(14.dp))
                }
            }
            Text(
                potion,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** Why a potion is not drinkable, in the space its button would occupy. */
@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp))
}
