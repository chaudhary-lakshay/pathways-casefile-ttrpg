package com.pathways.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pathways.model.BeyonderState
import com.pathways.model.DiceExpr
import com.pathways.model.Trait
import com.pathways.model.canDrink
import com.pathways.rules.Corruption
import com.pathways.rules.PcrOutcome
import com.pathways.rules.PotionConsumption
import com.pathways.rules.PotionDigestion
import com.pathways.rules.ScoreFloors
import com.pathways.rules.TraitEffects
import com.pathways.rules.TraitReport
import com.pathways.rules.content.ActingQuality
import com.pathways.rules.content.ContentLoader
import com.pathways.rules.content.RitualCorThreshold


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
    /** The character's CoR, which lowers the PCR one per point (v6.9i). */
    cor: Int,
    /** Read for Knows the Acting Method, which doubles the table (6ez). */
    traits: List<Trait>,
    /** "Digesting this potion", or the potion's name while another rung is read. */
    title: String,
    onDigest: (Int) -> Unit,
    onAct: (ActingQuality, drawnDigestion: Int) -> Unit,
) {
    val gains = remember(traits) { TraitEffects.actingGains(traits) }
    StepperCard(
        title = title,
        minus = "-5%",
        plus = "+5%",
        value = "${state.digestion}%",
        onMinus = { onDigest(-5) },
        onPlus = { onDigest(5) },
    ) {
        Text(
            "next potion rolls ${pcrLine(state, cor)}",
            style = MaterialTheme.typography.bodySmall.tabular(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            // ponytail: the tablet's two lines; at 100% it fell to one and moved the bands.
            minLines = 2,
        )
        SectionLabel("Acting")
        // One box a quality: the book prints a range and no verdict.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Drawn at this digestion: a second tap of it is dropped.
            val drawn = state.digestion
            gains.forEach { quality ->
                if (quality.losesProgress) {
                    ChipTag("${quality.quality} 0 or lose")
                } else {
                    OutlinedButton(
                        onClick = { onAct(quality, drawn) },
                        shape = ButtonShape,
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                        contentPadding = PaddingValues(horizontal = 14.dp),
                    ) {
                        Text(
                            "${quality.quality} +${quality.minGain}-${quality.maxGain}%",
                            style = MaterialTheme.typography.labelLarge.tabular(),
                        )
                    }
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
 * Drink button on the next one. The target is less one per point of [cor]
 * (v6.9i), and the cost is the book's "1d6 CoR", rolled on success only by
 * [BeyonderSession.drink].
 */
internal fun pcrLine(state: BeyonderState, cor: Int): String {
    val dice = PotionConsumption.corDiceFor(state)
    return "3d6 ≤ ${PotionConsumption.targetFor(state.digestion, cor)}" +
        if (dice == null) "" else " — on success +${printedDice(dice)} CoR"
}

/** "1d6" as the CoR table prints it, where [DiceExpr.render] says GURPS's "1d". */
private fun printedDice(dice: DiceExpr): String = "${dice.count}d${dice.sides}"

/**
 * What the Drink button says about its own roll.
 *
 * [ceilingAfter] is the maximum CoR of the potion being drunk INTO, the one
 * `drink` charges against. A character can survive the roll and be lost on
 * the same tap -- the 1d6 landing on the ceiling, or a potion that lowers
 * Will onto the total at no cost -- and this is the last screen that can say
 * so. Certain when the die's lowest face reaches it; otherwise the face that
 * would is named.
 *
 * A [ceilingAfter] below the floor is the reason Drink is greyed out, and is
 * printed rather than asked about: [Corruption] throws on it, and this runs
 * while the block is being DRAWN. Abyss Sequence 9 lowers Will, so a mortal's
 * first potion can be refused too -- the one case a mortal ([state] null),
 * who makes no roll, gets a line at all.
 */
internal fun drinkLine(state: BeyonderState?, cor: Int, ceilingAfter: Int): String? {
    val roll = state?.let { "at ${it.digestion}% digested, rolls ${pcrLine(it, cor)}" }
    if (!ScoreFloors.holdsCeiling(ceilingAfter)) {
        val refused = "CoR ceiling after this potion would be $ceilingAfter, and this app keeps it at " +
            "${ScoreFloors.MIN_COR_CEILING} or more — it cannot be drunk until the ceiling is raised"
        return if (roll == null) refused else "$roll. $refused"
    }
    if (state == null) return null
    val dice = PotionConsumption.corDiceFor(state)
    val least = dice?.let { it.count + it.modifier } ?: 0
    val most = dice?.let { it.count * it.sides + it.modifier } ?: 0
    return roll + when {
        Corruption.isLostAfter(cor, least, ceilingAfter) ->
            ". Maximum CoR after this potion is $ceilingAfter; ${cor + least} CoR reaches it — " +
                "lost to corruption even if the potion integrates"
        dice != null && Corruption.isLostAfter(cor, most, ceilingAfter) ->
            ". Maximum CoR after this potion is $ceilingAfter; a ${ceilingAfter - cor} or more on the " +
                "${printedDice(dice)} reaches it — lost to corruption if it does"
        else -> ""
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
            style = MaterialTheme.typography.bodySmall.tabular(),
            color = if (session.corrupted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // All four rungs, the reached in red (frame 11, the owner's ruling of
        // 2026-10-18). Captioned, because the book files this ladder under
        // "Corruption from Ritual Work", not under CoR in general.
        SectionLabel("From ritual work")
        corLadder(cor).forEach { (threshold, reached) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text(
                    "${threshold.cor} CoR",
                    Modifier.width(72.dp),
                    style = MaterialTheme.typography.labelLarge.tabular().copy(fontWeight = FontWeight.SemiBold),
                    color = if (reached) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    threshold.effect,
                    style = MaterialTheme.typography.bodySmall.tabular(),
                    color = if (reached) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DashedRule()
        }
        Box(
            Modifier
                .padding(top = 12.dp)
                .fillMaxWidth()
                .dashedBorder(MaterialTheme.colorScheme.outlineVariant)
                .padding(horizontal = 12.dp),
        ) {
            RuleNote("What raises corruption") {
                Corruption.gained().forEach {
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text(it.action, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(it.gain, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary)
                    }
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

/**
 * The Potion Consumption Roll, and what it cost (frame 12): the form and the
 * file number, the new rung stamped on when the potion integrates, the dice.
 */
@Composable
internal fun PcrDialog(
    outcome: PcrOutcome,
    corrupted: Boolean,
    /** What may reroll this failure, while one is unspent ([TraitEffects.rerollSource]). */
    rerollSource: String?,
    /** "Seq 8 · Clown": the rung landed on. Null on a failure, which lands on none. */
    stamp: String?,
    /** "File Nº 10", or null for a sheet the gallery does not number. */
    file: String?,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        // Filled ink, as frame 12 draws it: the one dialog whose way out is
        // not CloseButton's stamp red.
        confirmButton = {
            Button(onClick = onDismiss, shape = ButtonShape) {
                Caps("OK", style = MaterialTheme.typography.labelLarge.tabular(), color = LocalContentColor.current)
            }
        },
        title = { FormTitle(FORM_PCR, file) {
            Text(
                when {
                    !outcome.integrated -> "Lost to the Rampager"
                    // Surviving the roll and being lost anyway is the book's
                    // own arithmetic: 10 CoR against a Will of 10 or less.
                    corrupted -> "The potion integrates — and takes them"
                    else -> "The potion integrates"
                },
                style = MaterialTheme.typography.headlineMedium,
            )
        } },
        text = {
            Column {
                if (stamp != null) {
                    Stamp(
                        stamp,
                        MaterialTheme.colorScheme.secondary,
                        MaterialTheme.typography.titleMedium.copy(fontFamily = PlexMono, fontWeight = FontWeight.SemiBold),
                    )
                }
                Text(
                    "3d6: ${outcome.roll.total} vs ${outcome.target} (digested ${outcome.digestion}%)",
                    Modifier.padding(top = 16.dp),
                    style = MaterialTheme.typography.titleMedium.tabular(),
                )
                if (outcome.integrated && outcome.corGained > 0) {
                    Text(
                        "+${outcome.corGained} CoR (1d6) charged for advancing before full digestion",
                        Modifier.padding(top = 8.dp),
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

/** A 1 dp dashed rule between ledger rows (frames 10-11). */
@Composable
internal fun DashedRule() {
    val color = MaterialTheme.colorScheme.outlineVariant
    Canvas(Modifier.fillMaxWidth().height(1.dp)) {
        drawLine(
            color,
            Offset(0f, 0f),
            Offset(size.width, 0f),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
        )
    }
}

/** Why a potion is not drinkable, in the space its button would occupy. */
@Composable
internal fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp))
}

/**
 * The potion the Drink button offers while [pathway] Sequence [sequence] is
 * browsed, or null (frames 10-11, the owner's ruling of 2026-10-18): a walker
 * is offered the NEXT potion of the pathway walked on every rung of it, so a
 * tile only changes what is read; a mortal, the browsed pathway's first.
 * [printed] says whether the content holds that block.
 */
internal fun drinkTarget(
    pathway: String,
    sequence: Int,
    drunk: BeyonderState?,
    printed: (BeyonderState) -> Boolean,
): BeyonderState? = when {
    drunk == null -> BeyonderState(pathway, sequence).takeIf { sequence == PotionDigestion.STARTING_SEQUENCE }
    drunk.pathway != pathway -> null
    // advanced() keeps the chain's drawback (6he), as canDrink compares it.
    else -> drunk.advanced()?.takeIf(printed)
}

/** The line under Drink: which potion, then the roll [drinkLine] prints. */
internal fun drinkCaption(target: BeyonderState, drunk: BeyonderState?, cor: Int, ceilingAfter: Int): String? {
    val line = drinkLine(drunk, cor, ceilingAfter) ?: return null
    return if (drunk == null) line else "next: Sequence ${target.sequence} of the ${target.pathway} Pathway · $line"
}

/** Filled cells of the ten-cell digestion bar: one per whole 10%. */
internal fun digestionCells(digestion: Int): Int = Math.floorDiv(digestion, 10)

/** All four rungs of the ritual-work ladder, each with whether [cor] has reached it. */
internal fun corLadder(cor: Int): List<Pair<RitualCorThreshold, Boolean>> {
    val reached = Corruption.ritualThresholdsReached(cor).toSet()
    return ContentLoader.ritualCorThresholds().map { it to (it in reached) }
}
