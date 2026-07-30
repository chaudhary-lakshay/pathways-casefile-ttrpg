package com.pathways.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pathways.model.BeyonderState
import com.pathways.model.Outcome
import com.pathways.rules.PremadeSheet
import com.pathways.rules.Premades
import com.pathways.rules.RandomSource
import com.pathways.rules.SheetComparison
import com.pathways.rules.SheetRow
import com.pathways.rules.SkillRoll
import com.pathways.rules.SkillRollOutcome
import java.io.File
import kotlin.random.Random

/** The app's real dice. Tests inject [com.pathways.rules.SeededRandom] instead. */
private val appDice = RandomSource { sides -> Random.nextInt(1, sides + 1) }

/** The destinations reachable from a selected premade. */
private enum class Screen { SHEET, PATHWAY, BUILDER }

/** [SheetComparison] emits the seven secondaries before any skill row. */
private const val SECONDARY_ROWS = 7

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ponytail: loaded on the main thread. 23 small JSONs off the APK
        // classpath; move behind a ViewModel when the sheet screen needs state
        // that survives rotation, not before.
        val store = SessionStore(File(filesDir, "sessions.json"))
        setContent { MaterialTheme { App(Premades.all(), store) } }
    }
}

/**
 * ponytail: four destinations, one enum, no navigation library. Add one at the
 * first deep link or saved back stack.
 */
@Composable
fun App(sheets: List<PremadeSheet>, store: SessionStore) {
    var selected by remember { mutableStateOf<PremadeSheet?>(null) }
    // Read once; every write goes straight back out, so the file and the
    // screens cannot drift apart.
    val saved = remember { store.load().toMutableMap() }
    var screen by remember(selected) { mutableStateOf(Screen.SHEET) }
    // One walk down a pathway per selected premade, restored from disk and
    // written back on every change.
    val session = remember(selected) {
        val name = selected?.name
        BeyonderSession(saved[name] ?: SavedSession()) { updated ->
            if (name != null) {
                saved[name] = updated
                store.save(saved)
            }
        }
    }

    when (val sheet = selected) {
        null -> PremadeList(sheets) { selected = it }
        else -> {
            BackHandler {
                if (screen == Screen.SHEET) selected = null else screen = Screen.SHEET
            }
            when (screen) {
                Screen.SHEET -> SheetScreen(
                    sheet,
                    session = session,
                    onPathways = { screen = Screen.PATHWAY },
                    onBuild = { screen = Screen.BUILDER },
                    onBack = { selected = null },
                )
                Screen.PATHWAY -> PathwayScreen(sheet, session, appDice) { screen = Screen.SHEET }
                Screen.BUILDER -> BuilderScreen(sheet) { screen = Screen.SHEET }
            }
        }
    }
}

@Composable
fun PremadeList(sheets: List<PremadeSheet>, onSelect: (PremadeSheet) -> Unit) {
    Scaffold { insets ->
        LazyColumn(Modifier.padding(insets).fillMaxSize()) {
            items(sheets) { sheet ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(sheet) }
                        .padding(16.dp),
                ) {
                    Text(sheet.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${sheet.skills.size} skills",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                HorizontalDivider()
            }
        }
    }
}

/**
 * The `computed vs asPublished` diff (spec section 8). A disagreement is shown,
 * never reconciled: most sheets floor Basic Speed where the book does not, and
 * Docks Bouncer's printed HP contradicts its own ST. Those rows render in the
 * error colour alongside what the sheet claimed.
 *
 * Skill rows are tappable and roll 3d6 against the resolved target.
 */
@Composable
fun SheetScreen(
    sheet: PremadeSheet,
    session: BeyonderSession,
    onPathways: () -> Unit,
    onBuild: () -> Unit,
    onBack: () -> Unit,
) {
    val drunk = session.state
    val rows = remember(sheet) { SheetComparison.of(sheet) }
    val differing = rows.count { !it.matches }
    var rolled by remember(sheet) { mutableStateOf<SkillRollOutcome?>(null) }

    Scaffold { insets ->
        LazyColumn(Modifier.padding(insets).fillMaxSize()) {
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Row {
                        TextButton(onClick = onBack) { Text("Back to premades") }
                        TextButton(onClick = onPathways) {
                            Text(if (drunk == null) "Drink a potion" else "Pathway")
                        }
                        TextButton(onClick = onBuild) { Text("Spend points") }
                    }
                    Text(sheet.name, style = MaterialTheme.typography.headlineSmall)
                    if (drunk != null) {
                        // The sheet rows below are still the mortal's: the
                        // diff they carry is against the published sheet, and
                        // no published sheet records a pathway. Saying so is
                        // better than silently mixing two states.
                        Text(
                            "Sequence ${drunk.sequence} of the ${drunk.pathway} Pathway, " +
                                "digested ${drunk.digestion}%, ${session.cor} CoR — " +
                                when {
                                    session.lost -> "LOST to the Rampager"
                                    session.corrupted -> "LOST to corruption"
                                    else -> "see the pathway screen for what it grants"
                                },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (session.isLost) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                    Text(
                        if (differing == 0) {
                            "all ${rows.size} values match the published sheet"
                        } else {
                            "$differing of ${rows.size} values differ from the published sheet"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
                HorizontalDivider()
            }

            items(rows.take(SECONDARY_ROWS)) { ComparisonRow(it) }

            // Row order past the secondaries is one per purchased skill, in
            // sheet order - pinned by SheetComparisonTest. That is what makes
            // this positional pairing safe.
            itemsIndexed(sheet.skills) { i, skill ->
                val row = rows.getOrNull(SECONDARY_ROWS + i)
                if (row != null) {
                    ComparisonRow(row, Modifier.clickable { rolled = SkillRoll.of(sheet, skill, appDice) })
                }
            }
        }
    }

    rolled?.let { RollDialog(it) { rolled = null } }
}

@Composable
private fun ComparisonRow(row: SheetRow, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(row.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(row.computed, style = MaterialTheme.typography.bodyMedium)
        if (!row.matches) {
            Text(
                " (sheet: ${row.published})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun RollDialog(out: SkillRollOutcome, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text(out.label) },
        text = {
            Column {
                val roll = out.roll
                if (roll == null) {
                    Text("Forbidden — this roll cannot be attempted.")
                } else {
                    Text(
                        "3d6: ${roll.total} vs ${roll.target}",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    // effectiveOutcome, not roll.outcome: RequireCrit demotes
                    // an ordinary success, and showing "Success" with a note
                    // underneath would misreport the result.
                    val shown = out.effectiveOutcome ?: roll.outcome
                    Text(label(shown), color = colorFor(shown))
                    Text("margin ${roll.margin}", style = MaterialTheme.typography.bodySmall)
                }
                if (out.target != out.baseTarget) {
                    Text(
                        "base ${out.baseTarget}, modified to ${out.target}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (out.requiresCritical) {
                    Text(
                        "Only a critical success counts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
    )
}

private fun label(outcome: Outcome) = when (outcome) {
    Outcome.CRITICAL_SUCCESS -> "Critical success"
    Outcome.SUCCESS -> "Success"
    Outcome.FAILURE -> "Failure"
    Outcome.CRITICAL_FAILURE -> "Critical failure"
}

@Composable
private fun colorFor(outcome: Outcome) = when (outcome) {
    Outcome.CRITICAL_FAILURE, Outcome.FAILURE -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.primary
}
