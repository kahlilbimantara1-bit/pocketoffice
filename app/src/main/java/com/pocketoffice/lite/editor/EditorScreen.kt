package com.pocketoffice.lite.editor

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketoffice.lite.data.AppViewModel
import com.pocketoffice.lite.ui.components.SimpleTopBar
import kotlinx.coroutines.delay

@Composable
fun EditorScreen(
    vm: AppViewModel,
    onSave: () -> Unit,
    onSaveAs: () -> Unit,
    onBack: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }

    // Autosave sederhana: 1,5 detik setelah pengguna berhenti mengetik.
    LaunchedEffect(vm.editorText) {
        delay(1500)
        vm.autosave()
    }

    Scaffold(
        topBar = {
            SimpleTopBar(title = vm.editorName, onBack = onBack) {
                TextButton(onClick = onSave) { Text("Simpan") }
                Box {
                    TextButton(onClick = { menu = true }) { Text("Lainnya") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Simpan Sebagai…") },
                            onClick = { menu = false; onSaveAs() }
                        )
                        DropdownMenuItem(
                            text = { Text("Ganti Nama") },
                            onClick = { menu = false; showRename = true }
                        )
                    }
                }
            }
        }
    ) { inner ->
        Column(Modifier.padding(inner).fillMaxSize().imePadding()) {
            TextField(
                value = vm.editorText,
                onValueChange = { vm.editorText = it },
                modifier = Modifier.weight(1f).fillMaxWidth(),
                textStyle = TextStyle(fontSize = vm.fontSp.sp, lineHeight = (vm.fontSp * 1.45f).sp),
                placeholder = { Text("Mulai menulis…") },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedIndicatorColor = MaterialTheme.colorScheme.surface,
                    unfocusedIndicatorColor = MaterialTheme.colorScheme.surface
                )
            )
            val status = when {
                vm.editorUri == null -> "Draf otomatis"
                vm.editorText == vm.savedText -> "Tersimpan"
                else -> "Belum disimpan"
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${vm.editorText.length} karakter",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                Text(
                    status,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showRename) {
        var input by remember { mutableStateOf(vm.editorName) }
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("Ganti Nama File") },
            text = {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    singleLine = true,
                    label = { Text("Nama file") }
                )
            },
            confirmButton = {
                TextButton(onClick = { showRename = false; vm.rename(input) }) { Text("Simpan") }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }) { Text("Batal") }
            }
        )
    }
}
