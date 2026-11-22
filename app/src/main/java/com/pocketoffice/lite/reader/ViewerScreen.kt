package com.pocketoffice.lite.reader

import android.net.Uri
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketoffice.lite.file.FileException
import com.pocketoffice.lite.file.FileKind
import com.pocketoffice.lite.ui.components.SimpleTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private sealed interface ViewState {
    object Loading : ViewState
    class Failed(val msg: String) : ViewState
    class Ready(val doc: ViewDoc) : ViewState
}

@Composable
fun ViewerScreen(
    uri: Uri,
    name: String,
    kind: FileKind,
    fontSp: Int,
    onBack: () -> Unit,
    onLoaded: () -> Unit
) {
    val context = LocalContext.current
    var state by remember(uri) { mutableStateOf<ViewState>(ViewState.Loading) }

    LaunchedEffect(uri) {
        state = try {
            val d = withContext(Dispatchers.IO) { OfficeParser.parse(context, uri, kind) }
            onLoaded()
            ViewState.Ready(d)
        } catch (e: FileException) {
            ViewState.Failed(e.message ?: "File tidak dapat dibuka.")
        } catch (e: OutOfMemoryError) {
            ViewState.Failed("File terlalu besar untuk memori HP ini.")
        } catch (e: Exception) {
            ViewState.Failed("File tidak dapat dibuka.")
        }
    }

    Scaffold(topBar = { SimpleTopBar(title = name, onBack = onBack) }) { inner ->
        Box(Modifier.padding(inner).fillMaxSize()) {
            when (val s = state) {
                is ViewState.Loading -> Text(
                    "Membuka file…", Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                is ViewState.Failed -> Column(
                    Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Tidak bisa membuka file", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(s.msg, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onBack) { Text("Kembali") }
                }
                is ViewState.Ready -> DocContent(s.doc, fontSp)
            }
        }
    }
}

@Composable
private fun DocContent(doc: ViewDoc, fontSp: Int) {
    val isTable = doc.sections.firstOrNull()?.rows != null
    if (isTable) {
        var sel by remember { mutableStateOf(0) }
        Column(Modifier.fillMaxSize()) {
            if (doc.sections.size > 1) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(doc.sections) { i, sec ->
                        if (i == sel) Button(onClick = { sel = i }) { Text(sec.title, maxLines = 1) }
                        else OutlinedButton(onClick = { sel = i }) { Text(sec.title, maxLines = 1) }
                    }
                }
            }
            SheetTable(doc.sections[sel.coerceIn(0, doc.sections.lastIndex)])
        }
    } else {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (doc.kind == FileKind.PPTX) {
                items(doc.sections) { sec ->
                    Surface(
                        Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 1.dp
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(sec.title, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            sec.paragraphs.forEach { Text(it, fontSize = fontSp.sp, lineHeight = (fontSp * 1.4f).sp) }
                        }
                    }
                }
            } else {
                val paras = doc.sections.first().paragraphs
                if (paras.isEmpty()) item { Text("Dokumen ini tidak berisi teks.") }
                items(paras) { Text(it, fontSize = fontSp.sp, lineHeight = (fontSp * 1.5f).sp) }
            }
        }
    }
}

private fun colName(i: Int): String {
    var n = i + 1
    val sb = StringBuilder()
    while (n > 0) {
        val r = (n - 1) % 26
        sb.insert(0, 'A' + r)
        n = (n - 1) / 26
    }
    return sb.toString()
}

@Composable
private fun SheetTable(sec: ViewSection) {
    val rows = sec.rows ?: emptyList()
    val cols = remember(sec) { rows.maxOfOrNull { it.size } ?: 0 }
    if (rows.isEmpty() || cols == 0) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Lembar ini kosong.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val line = MaterialTheme.colorScheme.outlineVariant
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth().horizontalScroll(rememberScrollState())) {
            LazyColumn(Modifier.width((44 + cols * 112).dp)) {
                item {
                    Row {
                        Box(Modifier.width(44.dp).height(28.dp).border(0.5.dp, line))
                        for (c in 0 until cols) {
                            Box(
                                Modifier.width(112.dp).height(28.dp).border(0.5.dp, line),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(colName(c), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                itemsIndexed(rows) { i, row ->
                    Row(Modifier.height(IntrinsicSize.Min)) {
                        Box(
                            Modifier.width(44.dp).fillMaxHeight().border(0.5.dp, line).padding(4.dp),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Text("${i + 1}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        for (c in 0 until cols) {
                            Box(Modifier.width(112.dp).fillMaxHeight().border(0.5.dp, line).padding(horizontal = 6.dp, vertical = 4.dp)) {
                                Text(
                                    row.getOrElse(c) { "" }, fontSize = 13.sp,
                                    maxLines = 3, overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
        if (sec.truncated) {
            Text(
                "Hanya 3000 baris pertama yang ditampilkan.",
                Modifier.padding(8.dp), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
