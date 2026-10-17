package com.pathways.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pathways.rules.ClauseBonus
import com.pathways.rules.AntiDivinationTechniques
import com.pathways.rules.Detection
import com.pathways.rules.DetectionResult
import com.pathways.rules.PoolStatus
import com.pathways.rules.RandomSource
import com.pathways.rules.SuccessRoll
import com.pathways.rules.TraitClause
import com.pathways.rules.TraitClauses
import com.pathways.rules.content.DivinationAwareness

/**
 * Feeling a divination land on you (Ch. 6.5, Sec. 1, 2 and 4).
 *
 * The other side of [DivinationDialog], and a separate screen for a reason:
 * the roll is the TARGET's, and this app holds one character. In the
 * divination dialog that character is the diviner and the target is a declared
 * Sequence with no sheet; here the character IS the target, so their own SPI
 * and Per are the roll.
 *
 * Nothing gates who may open it. Sec. 2 scopes itself to Beyonders in prose,
 * but the roll it defines is SPI or Per whichever is higher and a mortal has a
 * Per -- who suspects they are watched is the GM's call, the same reading that
 * left the Sealed Artifact grade table unenforced.
 *
 * The Sec. 1 row is a GATE first: it says whether there is a roll at all. A
 * row granting automatic awareness withdraws the Sense button, because the
 * book gives that character the answer without dice and rolling anyway could
 * take it away.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetectionDialog(
    spi: Int,
    per: Int,
    /**
     * Ch. 3's Alertness, "+1 per level to all Per rolls" -- and this is a Per
     * roll the app makes OUTSIDE `SkillKey`, so `TraitEffects.modifiers`
     * cannot reach it. Passed explicitly rather than left as a silent gap
     * (found in review). It lifts the Per branch only; a roll that takes SPI
     * is not a Per roll.
     */
    alertness: Int = 0,
    /**
     * The Major Wound's shock. "All rolls" names no exception, so it reaches
     * this one; [Detection] takes it separately from the situation modifiers
     * so the breakdown can name it (6ag's rule that the parts must sum to the
     * target above them).
     */
    shock: Int = 0,
    /**
     * Reeling and fatigue, "-1 to all rolls" on pp. 40 and 42 -- the same
     * unqualified phrase as the shock above, and this roll is one of the bare
     * attribute rolls the modifier ledger cannot reach. Unlike [spiTrait] below it, this is
     * not attribute-scoped: it moves the target whichever attribute is taken.
     */
    hurt: Int = 0,
    /**
     * Ether Body Damage's "-1 to all SPI-based rolls" -- Alertness's mirror,
     * one attribute over, and the same structural gap: this roll is made
     * outside `SkillKey`, so the attribute-scoped join cannot reach it.
     *
     * It lands on the SPI branch only, because a Per roll is not an SPI-based
     * roll, and it deliberately cannot decide WHICH attribute is rolled --
     * [Detection.attributeFor] chooses on the raw values first, or a -1 could
     * tip a tie into a Per roll and so cancel itself.
     */
    spiTrait: Int = 0,
    /** The held rows' declared "-N to all rolls" (6gs). */
    states: ClauseBonus = ClauseBonus(0, null),
    /**
     * [TraitClauses.perRollClauses]: Light Sleeper's "Perception rolls while
     * sleeping at +4", which the GM declares (6gu). It reaches the Per branch
     * alone, as [alertness] does.
     */
    perClauses: List<TraitClause> = emptyList(),
    /**
     * The SPI pool as it stands, for Spiritual Attrition's -3, which reaches
     * this roll only when an awareness row grants it ([Detection.attritionFor]).
     */
    spiPool: PoolStatus? = null,
    dice: RandomSource,
    onDismiss: () -> Unit,
) {
    val awarenessRows = remember { Detection.awareness() }
    val situations = remember { Detection.modifiers() }
    // Only the rows carrying a number reach this roll. The candle's -3 is
    // about sensing a RITUAL and the other four items do something else
    // entirely, so Detection refuses to sum them.
    val items = remember { Detection.items().filter { it.detectionBonus != null } }
    val wards = remember { AntiDivinationTechniques.onDetectionRoll() }

    var awareness by remember { mutableStateOf<DivinationAwareness?>(null) }
    // Three of the four printed bonuses are scoped to what the divination is
    // ABOUT, so the bonus is offered rather than applied.
    var takeBonus by remember(awareness) { mutableStateOf(false) }
    var chosen by remember { mutableStateOf<Set<String>>(emptySet()) }
    var wardIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var carried by remember { mutableStateOf<Set<String>>(emptySet()) }
    var result by remember { mutableStateOf<DetectionResult?>(null) }
    var declared by remember { mutableStateOf<Set<String>>(emptySet()) }
    val perClause = TraitClauses.clauseBonus(perClauses, declared)

    val pickedWards = wards.filter { it.id in wardIds }
    val pickedItems = items.filter { it.name in carried }
    // One charm, priced generally in Sec. 2 and specifically in Sec. 4.
    // Detection throws on the pair rather than summing it, so the general chip
    // withdraws instead of offering a throw -- the lesson the two Aim rows
    // taught: a chip that cannot be taken is not a choice.
    val charmTaken = pickedItems.isNotEmpty()
    val pickedSituations = situations.filter {
        it.situation in chosen &&
            !(charmTaken && it.situation.equals(Detection.GENERIC_CHARM, ignoreCase = true))
    }
    val bonus = if (takeBonus) awareness?.bonus ?: 0 else 0
    val modifier = Detection.modifierTotal(
        situations = pickedSituations,
        techniques = pickedWards,
        items = pickedItems,
        awarenessBonus = bonus,
    )
    // The row's own roll, when it grants one: four rows name an attribute and
    // it is not always the higher of the two, so the roll a Fool is granted is
    // a Per roll even when their SPI is better. See Detection.attributeFor.
    val freeRoll = awareness?.freeRoll
    val attrition = Detection.attritionFor(spiPool, awareness)
    val target = Detection.targetFor(
        spi, per, modifier, freeRoll, alertness, shock, hurt, spiTrait, states, perClause, attrition,
    )
    // The breakdown of that number, composed once: printed before the roll
    // and carried by the result after it, so the two lines cannot differ.
    val parts = Detection.partsFor(
        spi, per, pickedSituations, pickedWards, pickedItems, bonus, awareness?.pathway,
        freeRoll, alertness, shock, hurt, spiTrait, states, perClause, attrition,
    )
    // The row grants automatic awareness only against a WEAKER diviner, and
    // this screen holds the target's sheet alone -- so the comparison is the
    // GM's to state. Reset with the row, or a declaration about one pathway's
    // demigod would outlive the pick (the stale-state trap 6k and 6y found).
    var divinerIsWeaker by remember(awareness) { mutableStateOf(false) }
    val grantsAutomatic = awareness?.isAutomatic == true
    val automatic = awareness?.let { Detection.isAutomaticAgainst(it, divinerIsWeaker) } == true

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { CloseButton(onDismiss) },
        dismissButton = {
            if (result != null) {
                // The answer belongs to the divination it was rolled against.
                TextButton(onClick = { result = null }) { Text("Sense again") }
            }
        },
        title = { FormTitle(FORM_DETECT) { Text("Am I being divined?") } },
        text = steadyBody {
            val answer = result
            if (answer != null) {
                // A Column: the text slot stacks its children on top of one
                // another.
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    VerdictCard(answer.row.result, answer.roll.outcome.isSuccess, answer.roll) {
                        Text(
                            answer.row.effect,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    Text(
                        answer.parts.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                return@steadyBody
            }
            Column {
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    // The base is the attribute alone, the row's or the higher of
                    // the two, so the card strikes it out once anything moves it.
                    TargetCard(target, baseTarget = Detection.targetFor(spi, per, freeRoll = freeRoll))
                    SectionLabel("Pathway awareness (Sec. 1)")
                    Text(
                        "Some pathways feel it without rolling. Leave this off if " +
                            "yours is not listed.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    FlowRow {
                        FilterChip(
                            selected = awareness == null,
                            onClick = { awareness = null },
                            label = { Text("None") },
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        awarenessRows.forEach { row ->
                            FilterChip(
                                selected = awareness == row,
                                onClick = { awareness = row },
                                label = { Text(row.pathway.substringBefore(" (")) },
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                    }
                    awareness?.let { row ->
                        Text(
                            "${row.sequence} — ${row.detection}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        row.divinerPenalty?.let {
                            // A row inside the awareness table that grants no
                            // awareness. Quoted, never applied: it is the DIVINER's
                            // penalty, and at the GM's discretion.
                            Text(
                                "This row grants no detection roll. Its ${signedInt(it)} " +
                                    "is on the diviner's roll, at the GM's discretion.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        row.bonus?.let { value ->
                            FilterChip(
                                selected = takeBonus,
                                onClick = { takeBonus = !takeBonus },
                                label = { Text("Take ${signedInt(value)}") },
                                modifier = Modifier.padding(top = 4.dp),
                            )
                            row.bonusText?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    SectionLabel("Situation (Sec. 2)")
                    FlowRow {
                        situations.forEach { row ->
                            val superseded =
                                charmTaken && row.situation.equals(Detection.GENERIC_CHARM, ignoreCase = true)
                            FilterChip(
                                selected = row.situation in chosen && !superseded,
                                enabled = !superseded,
                                onClick = {
                                    chosen =
                                        if (row.situation in chosen) chosen - row.situation
                                        else chosen + row.situation
                                },
                                label = { Text("${row.situation} ${signedInt(row.modifier)}") },
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                    }
                    if (charmTaken) {
                        Text(
                            "The charm you named prices itself, so Sec. 2's general " +
                                "charm row does not also apply.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    SectionLabel("Your own protection (Sec. 3, 4)")
                    Text(
                        "A ward's price is on its chip; spend it on the tracker. " +
                            "Nothing here holds a clock for the duration it buys.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    FlowRow {
                        wards.forEach { row ->
                            FilterChip(
                                selected = row.id in wardIds,
                                onClick = {
                                    wardIds =
                                        if (row.id in wardIds) wardIds - row.id
                                        else wardIds + row.id
                                },
                                label = {
                                    // The book's own words for the price, not a
                                    // second spelling of `spi` typed on screen.
                                    val price = row.bullet("Cost")?.let { " · $it" }.orEmpty()
                                    Text(
                                        "${row.name} ${signedInt(row.detectionBonus ?: 0)}$price",
                                    )
                                },
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                        items.forEach { row ->
                            FilterChip(
                                selected = row.name in carried,
                                onClick = {
                                    carried =
                                        if (row.name in carried) carried - row.name
                                        else carried + row.name
                                },
                                label = {
                                    Text(
                                        row.name.substringBefore(" (") + " " +
                                            signedInt(row.detectionBonus ?: 0),
                                    )
                                },
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                    }

                    if (grantsAutomatic) {
                        // Offered rather than assumed: the row's automatic clause
                        // is scoped to a weaker diviner and this app never sees
                        // the diviner's sheet. Against an equal or stronger one
                        // the book calls for a Quick Contest, which is not
                        // modelled here for the same reason -- so the fallback is
                        // the ordinary Sense roll, not a claim of awareness.
                        FilterChip(
                            selected = divinerIsWeaker,
                            onClick = { divinerIsWeaker = !divinerIsWeaker },
                            label = { Text("The diviner is of weaker Sequence") },
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        if (!divinerIsWeaker) {
                            Text(
                                "Against an equal or stronger Sequence the book calls " +
                                    "for a Quick Contest of SPI vs the diviner's " +
                                    "Divination Arts or Ritualistic Magic, which needs " +
                                    "both sheets and is not rolled here. Sense below.",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                    // Last in the column: the note under it comes and goes
                    // with the row picked above, and nothing sits below to move.
                    ClauseChips(perClauses, declared) { declared = it }
                    if (perClauses.isNotEmpty() && !Detection.isPerRoll(spi, per, freeRoll)) {
                        Text(
                            "This roll takes SPI, so a Perception clause does not reach it.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                RollFooter(
                    // The rules object authors this sentence, because it is
                    // the one the target above is computed from. A second
                    // copy here is how the caption came to say "the higher
                    // of SPI and Per" over a target taken from the row's
                    // own attribute.
                    // Named BEFORE the roll as well as after it: the
                    // breakdown and the target above it must reconcile
                    // (6ag, 6ai). It is the result's own list, so each
                    // circumstance is itemised here as it is there.
                    lines = listOf(parts.joinToString(", ") + ". Free action, costs no SPI."),
                    caption = if (automatic) "Automatic awareness — no roll." else SuccessRoll.printed(target),
                    enabled = !automatic,
                    label = "Sense",
                ) {
                    // One roll per sensing; the re-entrancy guard the
                    // divination and ritual dialogs both needed.
                    if (result != null || automatic) return@RollFooter
                    result = Detection.rollAgainst(target, dice, parts)
                }
            }
        },
    )
}

private fun signedInt(n: Int) = if (n >= 0) "+$n" else "$n"
