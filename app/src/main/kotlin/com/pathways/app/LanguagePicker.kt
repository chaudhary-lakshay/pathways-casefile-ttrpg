package com.pathways.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pathways.rules.Languages
import com.pathways.rules.OwnedLanguage
import com.pathways.rules.content.LanguageEntry

/**
 * The languages bought in play (Ch. 3, pp. 13-14), each with the line that
 * says what it costs and why.
 *
 * [owned] is what the composition bills -- [SavedEdits.ownedLanguages] -- so a
 * saved row the book does not print is not listed here with a price. Changing
 * a language goes through the same picker as buying one: picking a language
 * already owned replaces it in place.
 */
@Composable
internal fun LanguagesRow(
    owned: List<OwnedLanguage>,
    onAdd: () -> Unit,
    onGiveBack: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        SectionHead("Languages", "Add or change", onAdd) {
            if (owned.isEmpty()) {
                Text(
                    "None bought",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        owned.forEach { language ->
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${language.label} — ${language.costLine}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                InkAction("Give back", onClick = { onGiveBack(language.purchase.language) })
            }
        }
    }
}

/**
 * The book's twelve languages in its own two groups, the three proficiencies
 * at that group's prices, and the three forms.
 *
 * **The price printed is the price billed.** [priced] is asked for the
 * candidate on screen and the Buy button stores that same candidate, so the
 * handler acts on what this composition drew (6eg).
 *
 * The proficiency and the form are plain state, not keyed on the language: a
 * keyed `remember` resets a frame late (6ef). Picking a language the
 * character owns loads what they own, in the tap itself -- from [owned], the
 * RESOLVED list, never the raw save: a hand-edited row the book does not
 * print would load a proficiency with no price and grey the button (Codex).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LanguagePicker(
    owned: List<OwnedLanguage>,
    priced: (SavedLanguage) -> OwnedLanguage?,
    onBuy: (SavedLanguage) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf<String?>(null) }
    var proficiency by remember { mutableStateOf(Languages.FLUENT) }
    var form by remember { mutableStateOf(Languages.BOTH_FORMS) }
    val candidate = name?.let { SavedLanguage(it, proficiency, form) }
    val price = candidate?.let(priced)
    val isOwned = owned.any { it.purchase.language == name }

    @Composable
    fun group(title: String, languages: List<LanguageEntry>) {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
        FlowRow(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            languages.forEach { language ->
                CaseChip(
                    selected = name == language.name,
                    onClick = {
                        name = language.name
                        owned.firstOrNull { it.purchase.language == language.name }?.let {
                            proficiency = it.purchase.proficiency
                            form = it.purchase.form
                        }
                    },
                    label = { Text(language.name) },
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            InkButton(if (isOwned) "Change" else "Buy", onClick = { candidate?.let(onBuy) }, enabled = price != null)
        },
        dismissButton = { CloseButton(onDismiss) },
        title = { FormTitle(null) { Text("Languages") } },
        text = steadyBody {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                group("Common", Languages.common())
                group("Mystical", Languages.mystical())
                val entry = Languages.all().firstOrNull { it.name == name }
                if (entry == null) {
                    Text(
                        "Pick a language. The book prints two price tables, and which one " +
                            "applies is the language's.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    return@Column
                }
                Text(entry.notes, style = MaterialTheme.typography.bodySmall)
                val rows = Languages.proficiencies(entry.type)
                Text(
                    "Proficiency",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
                FlowRow(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    rows.forEach { row ->
                        CaseChip(
                            selected = proficiency == row.proficiency,
                            onClick = { proficiency = row.proficiency },
                            label = { Text("${row.proficiency} ${row.points}") },
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                }
                rows.firstOrNull { it.proficiency == proficiency }?.let {
                    Text(it.effect, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    "Form",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
                FlowRow(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Languages.forms().forEach { option ->
                        CaseChip(
                            selected = form == option,
                            onClick = { form = option },
                            label = { Text(option) },
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                }
                Text(
                    price?.let { "${it.label} — ${it.costLine}" }
                        ?: "The book prints no such row.",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
                // Said here because the sheet says it too: the price is what
                // this app applies, and the row's penalties are not.
                Text(
                    "The price above is what the budget bills. The penalties to language-dependent skill " +
                        "rolls are the GM's: the book tags no skill as language-dependent.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
    )
}
