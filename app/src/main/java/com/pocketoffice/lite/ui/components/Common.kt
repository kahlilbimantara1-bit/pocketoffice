package com.pocketoffice.lite.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pocketoffice.lite.data.RecentDoc
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SimpleTopBar(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("Kembali") }
            Text(
                title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium
            )
            actions()
        }
    }
}

@Composable
fun EmptyState(onOpen: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Belum ada dokumen", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            "Buka file PDF, Word, Excel, PowerPoint, CSV, atau TXT dari penyimpanan HP, atau buat dokumen baru.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onOpen) { Text("Buka File") }
    }
}

private fun formatTime(t: Long): String =
    SimpleDateFormat("d MMM yyyy, HH:mm", Locale("id", "ID")).format(Date(t))

@Composable
fun RecentItem(doc: RecentDoc, onClick: () -> Unit, onRemove: (() -> Unit)? = null) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Row(
            Modifier.clickable(onClick = onClick).padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text(
                    doc.type, color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(doc.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${doc.type} • ${formatTime(doc.lastOpened)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (onRemove != null) {
                TextButton(onClick = onRemove) { Text("Hapus") }
            }
        }
    }
}
