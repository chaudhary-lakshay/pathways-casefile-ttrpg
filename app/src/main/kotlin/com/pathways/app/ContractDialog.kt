package com.pathways.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.pathways.model.RollResult
import com.pathways.rules.ClauseBonus
import com.pathways.rules.ContractPenalty
import com.pathways.rules.Corruption
import com.pathways.rules.RandomSource
import com.pathways.rules.SpiritContract
import com.pathways.rules.SuccessRoll

internal const val FORM_CONTRACT = "FORM 9-K · CONTRACT"

/**
 * The footer's terms: every one the target is made of, so the breakdown
 * reconciles (issue #1). The steppers' two are drawn even at zero, so a step
 * moves no button; the rest are fixed while the dialog is open.
 */
internal fun contractLines(
    skillLevel: Int,
    conditionPenalty: Int,
    shock: Int,
    states: ClauseBonus,
    cor: Int,
    sequence: Int,
    held: Int,
    burned: Int,
): List<String> = listOfNotNull(
    "Ritualistic Magic $skillLevel",
    if (conditionPenalty != 0) "$conditionPenalty hurt or tired" else null,
    if (shock != 0) "$shock shock" else null,
    states.line,
    Corruption.ritualPenalty(cor).takeIf { it != 0 }?.let { "$it for $cor CoR" },
    "Sequence -$sequence",
    "-${held}d6, rolled at Cast",
    "Burn +$burned",
    "${SpiritContract.spi() + burned} SPI",
)

/**
 * Ch. 9 VI's Spirit World Creature Contract (v6.9i): Ritualistic Magic less
 * the Sequence and 1d6 per creature already held, for 3 SPI. The extra dice
 * are rolled at Cast with the roll itself; the SPI and any burn are charged
 * in the same one-shot. How many contracts are held is the player's to say.
 */
@Composable
internal fun ContractDialog(
    skillLevel: Int,
    conditionPenalty: Int,
    shock: Int,
    states: ClauseBonus,
    cor: Int,
    sequence: Int,
    maxContracts: Int,
    dice: RandomSource,
    onSpendSpi: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var held by remember { mutableIntStateOf(0) }
    var burned by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf<Pair<ContractPenalty, RollResult>?>(null) }
    val skill = skillLevel + conditionPenalty + shock + states.total
    // The target before the extra dice: all the dialog can print ahead of Cast.
    val before = SpiritContract.targetFor(skill, cor, ContractPenalty(sequence, emptyList()), burned)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { CloseButton(onDismiss) },
        title = { FormTitle(FORM_CONTRACT) { Text("Spirit contract") } },
        text = steadyBody {
            result?.let { (penalty, roll) ->
                val row = SpiritContract.outcomeFor(roll)
                Column {
                    if (penalty.dice.isNotEmpty()) {
                        Text(
                            "Contracts held: ${penalty.dice.joinToString(" + ")} = -${penalty.dice.sum()}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    VerdictCard(row.printed, roll.outcome.isSuccess, roll)
                    Text(row.outcome, style = MaterialTheme.typography.bodyMedium)
                }
                return@steadyBody
            }
            val full = held >= maxContracts
            Column {
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    OptionStepper(
                        label = "Creatures already under contract",
                        value = held,
                        note = "each adds 1d6 to the penalty; the most is $maxContracts",
                    ) { held = (held + it).coerceIn(0, maxContracts) }
                    OptionStepper(
                        label = "Burn SPI",
                        value = burned,
                        shown = "+$burned",
                        note = "before rolling: 1 SPI buys +1, beside the ${SpiritContract.spi()} SPI cost",
                    ) { burned = (burned + it).coerceAtLeast(0) }
                    RuleNote("What the book says of the contract") {
                        val contract = SpiritContract.contract()
                        Text(contract.difficulty, style = MaterialTheme.typography.bodySmall)
                        Text("${contract.time}. ${contract.setup}", style = MaterialTheme.typography.bodySmall)
                        contract.methods.forEach {
                            Text("${it.name}: ${it.text}", style = MaterialTheme.typography.bodySmall)
                        }
                        Text(contract.demands, style = MaterialTheme.typography.bodySmall)
                    }
                }
                RollFooter(
                    // Every line drawn from the start, so a step moves no button.
                    lines = contractLines(skillLevel, conditionPenalty, shock, states, cor, sequence, held, burned),
                    caption = if (full) "$maxContracts contracts held: no other can be made" else SuccessRoll.printed(before),
                    target = before.takeIf { held == 0 },
                    enabled = !full,
                    label = "Cast",
                ) {
                    // A one-shot: a second tap of this picture is dropped (6ei).
                    if (result != null || held >= maxContracts) return@RollFooter
                    val penalty = SpiritContract.penaltyFor(sequence, held, maxContracts, dice)
                    onSpendSpi(SpiritContract.spi() + burned)
                    result = penalty to SuccessRoll.against(SpiritContract.targetFor(skill, cor, penalty, burned), dice)
                }
            }
        },
    )
}
