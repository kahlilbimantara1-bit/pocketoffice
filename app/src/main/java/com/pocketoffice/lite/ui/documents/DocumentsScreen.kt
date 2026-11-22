package com.pocketoffice.lite.ui.documents

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pocketoffice.lite.data.RecentDoc
import com.pocketoffice.lite.ui.components.EmptyState
import com.pocketoffice.lite.ui.components.RecentItem

@Composable
fun DocumentsScreen(
    recent: List<RecentDoc>,
    onOpenDocument: () -> Unit,
    onOpenRecent: (RecentDoc) -> Unit,
    onRemove: (RecentDoc) -> Unit,
    onClear: () -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Text("Dokumen", style = MaterialTheme.typography.headlineSmall) }
        item { Button(onClick = onOpenDocument, modifier = Modifier.fillMaxWidth()) { Text("Buka Dokumen") } }
        if (recent.isEmpty()) {
            item { EmptyState(onOpenDocument) }
        } else {
            items(recent, key = { it.uri }) { doc ->
                RecentItem(doc, onClick = { onOpenRecent(doc) }, onRemove = { onRemove(doc) })
            }
            item {
                TextButton(onClick = onClear) { Text("Bersihkan daftar") }
                Text(
                    "Menghapus dari daftar tidak menghapus file asli.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
