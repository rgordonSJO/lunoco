// Modified by Lunoco contributors. Phase 38C changes incorporated into this release source on 2026-11-22.
package com.github.damontecres.wholphin.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.mikepenz.aboutlibraries.ui.compose.android.produceLibraries
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer

/** Keeps project and dependency notices in the dedicated, user-accessible licenses route. */
@Composable
fun LicenseInfo(modifier: Modifier = Modifier) {
    val libraries by produceLibraries()
    var showGpl by remember { mutableStateOf(false) }
    Column(modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.jellylu_attribution), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.lunoco_source_notice), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { showGpl = true }) {
                Text(stringResource(R.string.lunoco_gpl_notice))
            }
        }
        LibrariesContainer(libraries, Modifier.weight(1f))
    }
    if (showGpl) {
        val resources = LocalResources.current
        val paragraphs = remember(resources) {
            resources.openRawResource(R.raw.lunoco_gpl_v2).bufferedReader().use { it.readText() }
                .split(Regex("\\r?\\n\\s*\\r?\\n"))
        }
        ScrollableDialog(onDismissRequest = { showGpl = false }) {
            items(paragraphs) { paragraph ->
                Text(paragraph, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
