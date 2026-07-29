package com.pathways.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pathways.rules.PremadeSheet
import com.pathways.rules.Premades

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ponytail: loaded on the main thread. 23 small JSONs off the APK
        // classpath; move behind a ViewModel when the sheet screen needs state
        // that survives rotation, not before.
        setContent { MaterialTheme { PremadeList(Premades.all()) } }
    }
}

@Composable
fun PremadeList(sheets: List<PremadeSheet>) {
    Scaffold { insets ->
        LazyColumn(Modifier.padding(insets).fillMaxSize()) {
            items(sheets) { sheet ->
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
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
