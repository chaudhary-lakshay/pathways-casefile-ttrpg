package com.pathways.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

internal data class Credit(val role: String, val name: String)

/**
 * Who made the game, first, as the rulebook's SUPPLEMENT CREDITS page
 * prints them. The app is only the way to play it, so it comes last.
 */
internal val CREDITS = listOf(
    Credit("Supplement designer and author", "Earvin Salonoy"),
    Credit("Rulebook scribes", "Mr.Worm's LOM TTRPG Discord Server"),
    Credit("Source material", "Lord of the Mysteries, by Cuttlefish That Loves Diving"),
    Credit("Game system", "GURPS 4th Edition, by Steve Jackson Games"),
    Credit("App", "chaudhary-lakshay"),
)

/** The credits, a page of their own off the gallery, as the glossary is. */
@Composable
fun CreditsScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            BoxedIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
        }
        Text(
            "Credits",
            Modifier.padding(top = 8.dp).semantics { heading() },
            style = MaterialTheme.typography.displaySmall,
        )
        Text(
            "The game is Powered by GURPS: Pathways — Lord of the Mysteries. " +
                "Its rules, pathways, potions and characters are the work of the people below. " +
                "This app is only a way to play their game.",
            Modifier.padding(top = 12.dp, bottom = 16.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
        HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.outline)
        CREDITS.forEach { credit ->
            Column(Modifier.padding(vertical = 14.dp)) {
                Caps(credit.role)
                Text(credit.name, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.titleLarge)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        Text(
            "Personal, non-commercial use only, as the rulebook asks.",
            Modifier.padding(vertical = 20.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
