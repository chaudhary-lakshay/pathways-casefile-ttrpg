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


/**
 * The Acting Method, as a table plays it: the GM judges the session's acting
 * and moves the percentage.
 *
 * A stepper, and a tap per quality that adds the low end of its RANGE
 * ("+15-20%"), the owner's call after play session 3: the GM steps +5% for
 * the rest of the span. "None" stays a tag, its loss the GM's to step.
 */
@Composable
internal fun DigestionTracker(
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
            // ponytail: the tablet's two lines; at 100% it fell to one and moved the bands.
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
internal fun CorruptionTracker(
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
internal fun PcrDialog(
    outcome: PcrOutcome,
    corrupted: Boolean,
    /** What may reroll this failure, while one is unspent ([TraitEffects.rerollSource]). */
    rerollSource: String?,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        title = { FormTitle(FORM_PCR) {
            Text(
                when {
                    !outcome.integrated -> "Lost to the Rampager"
                    // Surviving the roll and being lost anyway is the book's
                    // own arithmetic: 10 CoR against a Will of 10 or less.
                    corrupted -> "The potion integrates — and takes them"
                    else -> "The potion integrates"
                },
            )
        } },
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

/** Why a potion is not drinkable, in the space its button would occupy. */
@Composable
internal fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp))
}
