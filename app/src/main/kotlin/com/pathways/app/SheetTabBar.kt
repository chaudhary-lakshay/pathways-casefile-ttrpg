package com.pathways.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * The sheet's three destinations at the foot of the screen (Case File,
 * frame 04): an ink rule across the bar, and the open tab raised over it as
 * a card. A footer, not a tab row under the top bar: three destinations
 * with icons where a thumb reaches without crossing the screen.
 */
@Composable
internal fun SheetTabBar(tab: SheetTab, onTab: (SheetTab) -> Unit) {
    Column(Modifier.background(MaterialTheme.colorScheme.background).navigationBarsPadding()) {
        HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.outline)
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 6.dp).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (option in SheetTab.entries) {
                val open = option == tab
                // Ink, not `secondary`: that is the stamp red, which marks
                // the Beyonder and harm, not a tab.
                val ink = if (open) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                Box(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp)
                        .then(
                            if (open) {
                                Modifier
                                    .background(MaterialTheme.colorScheme.surfaceContainerLow, RAISED)
                                    .border(2.dp, MaterialTheme.colorScheme.outline, RAISED)
                            } else {
                                Modifier
                            },
                        )
                        .selectable(selected = open, role = Role.Tab, onClick = { onTab(option) }),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(option.icon, contentDescription = null, tint = ink, modifier = Modifier.size(20.dp))
                        Caps(
                            option.label,
                            Modifier.padding(start = 8.dp),
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontFamily = PlexMono,
                                fontWeight = if (open) FontWeight.SemiBold else FontWeight.Normal,
                            ),
                            color = ink,
                        )
                    }
                }
            }
        }
    }
}

/** A card hung from the rule: square at the top, rounded below. */
private val RAISED = RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp)
