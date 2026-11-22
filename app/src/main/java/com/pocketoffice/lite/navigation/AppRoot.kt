package com.pocketoffice.lite.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pocketoffice.lite.data.AppViewModel
import com.pocketoffice.lite.data.Screen
import com.pocketoffice.lite.editor.EditorScreen
import com.pocketoffice.lite.reader.PdfReaderScreen
import com.pocketoffice.lite.reader.ViewerScreen
import com.pocketoffice.lite.ui.documents.DocumentsScreen
import com.pocketoffice.lite.ui.home.HomeScreen
import com.pocketoffice.lite.ui.settings.SettingsScreen

@Composable
fun AppRoot(
    vm: AppViewModel,
    onPickDocument: () -> Unit,
    onPickSaveAs: (String) -> Unit
) {
    val snackbar = remember { SnackbarHostState() }
    val msg = vm.message
    LaunchedEffect(msg) {
        if (msg != null) {
            snackbar.showSnackbar(msg)
            vm.message = null
        }
    }

    BackHandler(enabled = vm.screen !is Screen.Main || vm.tab != 0) {
        when (vm.screen) {
            is Screen.Pdf -> vm.closePdf()
            is Screen.Viewer -> vm.closeViewer()
            is Screen.Editor -> vm.closeEditor()
            else -> vm.tab = 0
        }
    }

    Box(Modifier.fillMaxSize()) {
        when (val s = vm.screen) {
            is Screen.Main -> MainScaffold(vm, onPickDocument)
            is Screen.Pdf -> key(s.uri) {
                PdfReaderScreen(
                    uri = s.uri,
                    name = s.name,
                    startPage = s.startPage,
                    onBack = { vm.closePdf() },
                    onLoaded = { vm.onPdfLoaded(s.uri, s.name) },
                    onPageSaved = { vm.savePdfPage(s.uri, it) }
                )
            }
            is Screen.Viewer -> key(s.uri) {
                ViewerScreen(
                    uri = s.uri,
                    name = s.name,
                    kind = s.kind,
                    fontSp = vm.fontSp,
                    onBack = { vm.closeViewer() },
                    onLoaded = { vm.onViewerLoaded(s.uri, s.name, s.kind) }
                )
            }
            is Screen.Editor -> EditorScreen(
                vm = vm,
                onSave = {
                    if (vm.editorUri == null) onPickSaveAs(vm.editorName) else vm.saveCurrent()
                },
                onSaveAs = { onPickSaveAs(vm.editorName) },
                onBack = { vm.closeEditor() }
            )
        }
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = if (vm.screen is Screen.Main) 80.dp else 40.dp)
        )
    }
}

@Composable
private fun MainScaffold(vm: AppViewModel, onPickDocument: () -> Unit) {
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = vm.tab == 0, onClick = { vm.tab = 0 },
                    icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                    label = { Text("Home") }
                )
                NavigationBarItem(
                    selected = vm.tab == 1, onClick = { vm.tab = 1 },
                    icon = { Icon(Icons.Filled.Menu, contentDescription = null) },
                    label = { Text("Dokumen") }
                )
                NavigationBarItem(
                    selected = vm.tab == 2, onClick = { vm.tab = 2 },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text("Pengaturan") }
                )
            }
        }
    ) { inner ->
        Box(Modifier.padding(inner).fillMaxSize()) {
            when (vm.tab) {
                0 -> HomeScreen(
                    recent = vm.recent,
                    onOpenDocument = onPickDocument,
                    onNewDocument = { vm.newDocument() },
                    onOpenRecent = { vm.openRecent(it) },
                    onSeeAll = { vm.tab = 1 }
                )
                1 -> DocumentsScreen(
                    recent = vm.recent,
                    onOpenDocument = onPickDocument,
                    onOpenRecent = { vm.openRecent(it) },
                    onRemove = { vm.removeRecent(it.uri) },
                    onClear = { vm.clearRecent() }
                )
                else -> SettingsScreen(
                    themeMode = vm.themeMode,
                    fontSp = vm.fontSp,
                    onTheme = { vm.setTheme(it) },
                    onFont = { vm.setFont(it) }
                )
            }
        }
    }
}
