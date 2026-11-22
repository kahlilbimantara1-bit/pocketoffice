package com.pocketoffice.lite.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketoffice.lite.data.RecentDoc
import com.pocketoffice.lite.ui.components.EmptyState
import com.pocketoffice.lite.ui.components.RecentItem

@Composable
fun HomeScreen(
    recent: List<RecentDoc>,
    onOpenDocument: () -> Unit,
    onNewDocument: () -> Unit,
    onOpenRecent: (RecentDoc) -> Unit,
    onSeeAll: () -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(64.dp).clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text("P", color = MaterialTheme.colorScheme.onPrimary, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(10.dp))
                Text("PocketOffice Lite", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                Text(
                    "Aplikasi office ringan untuk HP Android Anda",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onOpenDocument, modifier = Modifier.weight(1f)) { Text("Buka Dokumen", maxLines = 1) }
                OutlinedButton(onClick = onNewDocument, modifier = Modifier.weight(1f)) { Text("Dokumen Baru", maxLines = 1) }
            }
        }
        item {
            Text(
                if (recent.isEmpty()) "Dokumen terakhir" else "Dokumen terakhir (${recent.size})",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        if (recent.isEmpty()) {
            item { EmptyState(onOpenDocument) }
        } else {
            items(recent.take(5), key = { it.uri }) { doc ->
                RecentItem(doc, onClick = { onOpenRecent(doc) })
            }
            if (recent.size > 5) {
                item { TextButton(onClick = onSeeAll) { Text("Lihat semua") } }
            }
        }
    }
}
