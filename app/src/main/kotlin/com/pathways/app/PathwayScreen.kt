package com.pathways.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pathways.model.BeyonderState
import com.pathways.model.canDrink
import com.pathways.rules.PcrOutcome
import com.pathways.rules.PotionDigestion
import com.pathways.rules.PremadeSheet
import com.pathways.rules.RandomSource
import com.pathways.rules.ScoreFloors
import com.pathways.rules.TraitEffects
import com.pathways.rules.TraitGrants
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
    /** The gallery's file number, for the PCR form's "File Nº" (frame 12). */
    file: Int?,
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
    // The potion Drink offers, whichever rung is read (frames 10-11).
    val target = block?.let { b ->
        drinkTarget(b.pathway, b.sequence, drunk) { s -> blocks.any { it.pathway == s.pathway && it.sequence == s.sequence } }
    }
    val potionName = { s: BeyonderState -> blocks.single { it.pathway == s.pathway && it.sequence == s.sequence }.name }
    // The SPI the potions DRUNK give, as the sheet's tracker reads it: what a
    // held power is paid from, never the browsed block's.
    val maxSpi = remember(sheet, drunk, session.edits) {
        session.characterFor(sheet).digestedWith(drunk)?.effectiveSpi ?: 0
    }

    Scaffold(topBar = { PathwayBar(sheet.name, pathway, onBack) }) { insets ->
        LazyColumn(
            Modifier.padding(insets).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
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
                // The potion read, beside its sigil (frame 10). A player
                // walking a pathway looks at this screen more than any other.
                item { PotionHeading(block) }
                item {
                    SequenceRow {
                        SEQUENCES.forEach { n ->
                            SequenceTile(
                                sequence = n,
                                potion = blocks.single { it.pathway == block.pathway && it.sequence == n }.name,
                                selected = n == sequence,
                                // Cumulative: a Sequence 8 drank the 9 first.
                                drunk = drunk?.pathway == block.pathway && n >= drunk.sequence,
                                modifier = Modifier.weight(1f),
                                // Picking a potion is asking to read it.
                                onClick = { sequence = n; tab = 0 },
                            )
                        }
                    }
                }
                // The character's digestion, on every rung of the pathway
                // walked: a tile only changes what is read (frames 10-11).
                if (drunk != null && drunk.pathway == block.pathway) {
                    item { DigestionBar(drunk.digestion) }
                }
                item {
                    DrinkRow(
                        target = target,
                        drunk = drunk,
                        session = session,
                        printedCor = printedCor,
                        rerollSource = rerollSource,
                        maxCorFor = maxCorFor,
                        onAnother = { pathway = null },
                        onDrink = { t ->
                            // A second tap, or a CoR step, can reach this
                            // handler before the screen redraws. `drink`
                            // refuses a state it was not offered by throwing,
                            // so a stale tap does nothing instead.
                            if (session.stillShows(drunk, printedCor)) pcr = session.drink(t, dice, maxCorFor)
                        },
                        onReroll = { t ->
                            // A second tap finds nothing pending.
                            if (session.pendingPcr == t && ScoreFloors.holdsCeiling(maxCorFor(t))) {
                                pcr = session.rerollPcr(dice, maxCorFor)
                            }
                        },
                    )
                }
                // Two tabs once a potion is drunk: what this potion grants, and
                // the character's own digestion and corruption. Stacked, the
                // trackers pushed every potion's text below the fold.
                if (drunk != null) item { PathwayTabs(tab) { tab = it } }
                if (drunk != null && tab == 1) {
                    // Both trackers are the character's, whichever rung is read.
                    if (!session.isLost) {
                        item {
                            val current = drunk.pathway == block.pathway && drunk.sequence == block.sequence
                            DigestionTracker(
                                drunk,
                                session.cor,
                                traits,
                                title = if (current) "Digesting this potion" else "Digesting ${potionName(drunk)}",
                                onDigest = session::digest,
                                onAct = session::act,
                                onActingMethod = { drawn -> session.actingMethodRoll(drawn, dice) },
                            )
                        }
                    }
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
                                if (left == drawnLeft) session.usePower(price, maxSpi, maxCor)
                            }
                        },
                    )
                }
            }
        }
    }

    pcr?.let { outcome ->
        PcrDialog(
            outcome,
            session.corrupted,
            if (session.pendingPcr != null) rerollSource else null,
            // On a success the session already stands on the rung landed on.
            stamp = drunk?.takeIf { outcome.integrated }?.let { "Seq ${it.sequence} · ${potionName(it)}" },
            file = fileLabel(file),
        ) { pcr = null }
    }
}

/**
 * The row under the rungs (frame 10): "[ Another pathway ]" and the Drink
 * button for [target] ([drinkTarget]), its caption full width beneath; or
 * the reroll a failed PCR leaves, or why nothing can be drunk. Every guard
 * reads [target], never the rung browsed: an Abyss walker reading Seq 6 is
 * charged Seq 8's ceiling, the potion the tap drinks.
 */
@Composable
private fun DrinkRow(
    target: BeyonderState?,
    drunk: BeyonderState?,
    session: BeyonderSession,
    printedCor: Int,
    rerollSource: String?,
    maxCorFor: (BeyonderState?) -> Int,
    onAnother: () -> Unit,
    onDrink: (BeyonderState) -> Unit,
    onReroll: (BeyonderState) -> Unit,
) {
    // Luck's reroll of the failed PCR (6fg), on the potion it attempted.
    // Saved, so it survives a restart; who may spend it is the rules'.
    val rerolling = session.lost && target != null && session.pendingPcr == target && rerollSource != null
    val drinking = !session.isLost && target != null && drunk.canDrink(target)
    // The ceiling of the potion the tap drinks: Drink's gate, and the
    // reroll's, since Will lowered since a failure can leave it none.
    val ceilingAfter = if (rerolling || drinking) maxCorFor(target) else null
    val caption = when {
        rerolling -> listOfNotNull(
            drinkCaption(target!!, drunk, session.cor, ceilingAfter!!),
            "lost to the Rampager, unless the reroll succeeds; the GM counts the session's rerolls",
        ).joinToString(". ")
        // Built from what stillShows compares, so it is the roll the tap
        // makes or the tap does nothing.
        drinking -> drinkCaption(target!!, drunk, printedCor, ceilingAfter!!)
        else -> null
    }
    Column {
        // A flow, so Drink takes its own line under the bracket when both do
        // not fit: at 390 dp and font 1.3 the bracket left it a sliver.
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            BracketAction("Another pathway", onAnother)
            Box(Modifier.weight(1f).widthIn(min = DRINK_MIN_WIDTH), contentAlignment = Alignment.CenterEnd) {
                when {
                    rerolling -> DrinkButton(
                        "Reroll the PCR ($rerollSource)",
                        ScoreFloors.holdsCeiling(ceilingAfter!!),
                        Modifier.fillMaxWidth(),
                    ) { onReroll(target!!) }
                    session.lost -> Note("lost to the Rampager")
                    session.corrupted -> Note("lost to corruption at ${session.cor} CoR")
                    // `drink` refuses a ceiling below the floor too, before
                    // it rolls; the caption says why.
                    drinking -> DrinkButton(
                        if (drunk == null) "Drink this potion" else "Drink and roll",
                        ScoreFloors.holdsCeiling(ceilingAfter!!),
                        Modifier.fillMaxWidth(),
                    ) { onDrink(target!!) }
                    // A pathway is walked one potion at a time.
                    drunk == null -> Note("a pathway begins at Sequence ${PotionDigestion.STARTING_SEQUENCE}")
                    else -> Note(
                        drunk.advanced()
                            ?.let { "next: Sequence ${it.sequence} of the ${it.pathway} Pathway" }
                            ?: "already at Sequence ${drunk.sequence}",
                    )
                }
            }
        }
        caption?.let {
            Text(
                it,
                Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall.tabular(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The potion [block] offers [drunk]: its pathway and Sequence, carrying the
 * walked chain's chosen drawback (6he), because [canDrink] compares the
 * whole state and the next potion keeps the choice.
 */
internal fun potionOn(block: SequenceBlock, drunk: BeyonderState?): BeyonderState =
    BeyonderState(block.pathway, block.sequence, drawback = drunk?.takeIf { it.pathway == block.pathway }?.drawback)

/** The narrowest Drink stands beside "[ Another pathway ]"; narrower, it takes its own line. */
private val DRINK_MIN_WIDTH = 220.dp
