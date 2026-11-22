package com.pocketoffice.lite.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    themeMode: Int,
    fontSp: Int,
    onTheme: (Int) -> Unit,
    onFont: (Int) -> Unit
) {
    val ctx = LocalContext.current
    val version = remember {
        try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "1.0" }
        catch (e: Exception) { "1.0" }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("Pengaturan", style = MaterialTheme.typography.headlineSmall)

        Spacer(Modifier.height(20.dp))
        Text("Tampilan", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        listOf("Ikuti sistem", "Mode terang", "Mode gelap").forEachIndexed { i, label ->
            Row(
                Modifier.fillMaxWidth()
                    .selectable(selected = themeMode == i, onClick = { onTheme(i) }, role = Role.RadioButton)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = themeMode == i, onClick = null)
                Spacer(Modifier.width(12.dp))
                Text(label)
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("Ukuran font teks: $fontSp sp", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Slider(
            value = fontSp.toFloat(),
            onValueChange = { onFont(it.roundToInt()) },
            valueRange = 12f..28f,
            steps = 15
        )
        Text("Contoh teks dokumen Anda.", fontSize = fontSp.sp)

        Spacer(Modifier.height(28.dp))
        Text("Tentang", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(6.dp))
        Text("PocketOffice Lite")
        Text("Versi $version", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Text(
            "Aplikasi office ringan untuk HP Android lama. Bekerja offline, tanpa iklan, tanpa analitik, dan tanpa izin penyimpanan khusus.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
