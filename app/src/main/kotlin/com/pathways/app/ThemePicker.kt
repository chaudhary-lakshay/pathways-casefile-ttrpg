package com.pathways.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * The theme picker.
 *
 * Every change is applied and written to disk immediately -- there is no Apply
 * button and no Cancel. The whole app sits inside `PathwaysTheme`, so a tap
 * repaints the screen behind this sheet; that repaint IS the preview, and an
 * Apply button would only delay the one piece of feedback the choice needs.
 *
 * The four accent swatches on every preset row are not decoration. They are
 * what the preset guarantees: a primary that differs from the Beyonder
 * secondary, the management tertiary, and the combat red. Showing them makes
 * the guarantee checkable by eye, which is the only way it is ever checked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemePicker(
    prefs: ThemePrefs,
    onChange: (ThemePrefs) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val dark = prefs.isDark()

    // One place that both applies and persists. Two call sites doing this
    // separately is how a setting ends up live but not saved.
    fun set(next: ThemePrefs) {
        saveThemePrefs(context, next)
        onChange(next)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Theme", style = MaterialTheme.typography.headlineSmall)

            SectionLabel("Palette")
            for (seed in ThemeSeed.entries) {
                PresetRow(
                    seed = seed,
                    dark = dark,
                    selected = seed == prefs.seed,
                    onClick = { set(prefs.copy(seed = seed)) },
                )
            }

            SectionLabel("Mode")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (mode in ThemeMode.entries) {
                    FilterChip(
                        selected = mode == prefs.mode,
                        onClick = { set(prefs.copy(mode = mode)) },
                        label = { Text(mode.label) },
                    )
                }
            }

            if (isDynamicColorAvailable) {
                ToggleRow(
                    title = "Material You",
                    // Says what it costs, because it is the one option here
                    // that can make the app look worse than the preset did.
                    detail = "Takes surfaces and the accent from your wallpaper.",
                    checked = prefs.dynamic,
                    onCheckedChange = { set(prefs.copy(dynamic = it)) },
                )
            }

            ToggleRow(
                title = "Pure black",
                detail = if (dark) {
                    "Black backgrounds for OLED panels. Cards stay raised."
                } else {
                    "Applies in dark mode only."
                },
                checked = prefs.amoled,
                enabled = dark,
                onCheckedChange = { set(prefs.copy(amoled = it)) },
            )

            ToggleRow(
                title = "Roll sounds",
                detail = "A sound when a divination's verdict lands.",
                checked = prefs.sounds,
                onCheckedChange = { set(prefs.copy(sounds = it)) },
            )
        }
    }
}

/** A section's name above its chips; the Attack dialog uses it too. */
@Composable
internal fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

/**
 * One palette, shown in the colours it would actually apply.
 *
 * The swatches read from the preset's OWN scheme for the current mode rather
 * than from `MaterialTheme.colorScheme`, so an unselected row previews itself
 * instead of previewing whatever is already selected.
 */
@Composable
private fun PresetRow(
    seed: ThemeSeed,
    dark: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = if (dark) seed.dark else seed.light
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics { contentDescription = "${seed.label} palette" }
            .padding(vertical = 10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Swatch(scheme.primary)
            Swatch(scheme.secondary)
            Swatch(scheme.tertiary)
            Swatch(scheme.error)
        }
        Text(
            seed.label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun Swatch(colour: Color) {
    Box(
        Modifier
            .size(22.dp)
            .background(colour, CircleShape)
            // Without an outline a dark swatch vanishes into a dark sheet, and
            // the pure-black option makes that reachable rather than theoretical.
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
    )
}

@Composable
private fun ToggleRow(
    title: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}
