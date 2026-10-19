package com.pathways.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pathways.rules.Rest
import com.pathways.rules.RestRecovery
import com.pathways.rules.content.ContentLoader

internal const val FORM_REST = "FORM 7-Z · REST"

/**
 * Ch. 7's Short or Long Rest (v6.9i): FP and SPI given back through the
 * trackers. Cogitation's success is the player's to declare: they roll the
 * skill from the Skills tab, and "on success, SPI recovery is doubled".
 * One rest per tap: [onRest] refuses a stale second ([BeyonderSession.rest]).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RestDialog(
    maxFp: Int,
    /** FP lost and SPI spent as this dialog was drawn, for the one-shot guard. */
    drawn: Pair<Int, Int>,
    onRest: (RestRecovery, Pair<Int, Int>) -> Boolean,
    onDismiss: () -> Unit,
) {
    val rests = remember { Rest.rests() }
    var rest by remember { mutableStateOf(rests.first()) }
    var cogitated by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf<RestRecovery?>(null) }
    val recovery = Rest.recovery(rest, cogitated, maxFp)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { CloseButton(onDismiss) },
        title = { FormTitle(FORM_REST) { Text("Rest") } },
        text = steadyBody {
            done?.let {
                Text("${rest.name}: +${it.fp} FP and +${it.spi} SPI, up to full.", style = MaterialTheme.typography.titleSmall)
                return@steadyBody
            }
            Column {
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    FlowRow {
                        rests.forEach { option ->
                            CaseChip(
                                selected = option == rest,
                                onClick = { rest = option },
                                label = { Text("${option.name} · ${option.hours} h") },
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                    }
                    CaseChip(
                        selected = cogitated,
                        onClick = { cogitated = !cogitated },
                        label = { Text("${ContentLoader.restFile().spi.cogitationSkill} roll succeeded: 2 SPI an hour") },
                    )
                    RuleNote("What the book says of this rest") {
                        Text(rest.text, style = MaterialTheme.typography.bodySmall)
                        rest.bullets.forEach { Text("${it.label}: ${it.text}", style = MaterialTheme.typography.bodySmall) }
                        ContentLoader.restFile().restrictions.forEach {
                            Text("${it.label}: ${it.text}", style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            "HP heals by the daily roll on Vitals; one rest a day and the focused tasks are the GM's.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                RollFooter(
                    lines = listOf("+${recovery.fp} FP", "+${recovery.spi} SPI"),
                    caption = "each up to full",
                    target = null,
                    label = "Rest",
                    icon = Icons.Filled.Check,
                ) {
                    // A one-shot: the session drops a tap drawn at another picture (6ei).
                    if (done == null && onRest(recovery, drawn)) done = recovery
                }
            }
        },
    )
}
