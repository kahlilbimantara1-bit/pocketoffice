package com.pocketoffice.lite.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.pocketoffice.lite.file.FileException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private const val MAX_RENDER_PX = 1800
private val ZOOM_LEVELS = listOf(1f, 1.5f, 2f, 2.5f)

@Composable
fun PdfReaderScreen(
    uri: Uri,
    name: String,
    startPage: Int,
    onBack: () -> Unit,
    onLoaded: () -> Unit,
    onPageSaved: (Int) -> Unit
) {
    val context = LocalContext.current
    var doc by remember(uri) { mutableStateOf<PdfDoc?>(null) }
    var error by remember(uri) { mutableStateOf<String?>(null) }

    LaunchedEffect(uri) {
        try {
            val d = withContext(Dispatchers.IO) { PdfDoc.open(context.contentResolver, uri) }
            doc = d
            onLoaded()
        } catch (e: FileException) {
            error = e.message
        } catch (e: Exception) {
            error = "PDF tidak dapat dibuka."
        }
    }
    DisposableEffect(uri) {
        onDispose {
            doc?.close()
            doc = null
        }
    }

    val d = doc
    when {
        error != null -> Scaffold(topBar = { SimpleBar(name, onBack) }) { inner ->
            Column(
                Modifier.padding(inner).fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Tidak bisa membuka PDF", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(error ?: "", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onBack) { Text("Kembali") }
            }
        }
        d == null -> Scaffold(topBar = { SimpleBar(name, onBack) }) { inner ->
            Box(Modifier.padding(inner).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Membuka PDF…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        else -> PdfContent(d, name, startPage, onBack, onPageSaved)
    }
}

@Composable
private fun SimpleBar(title: String, onBack: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("Kembali") }
            Text(title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun PdfContent(
    doc: PdfDoc,
    name: String,
    startPage: Int,
    onBack: () -> Unit,
    onPageSaved: (Int) -> Unit
) {
    val count = doc.pageCount
    var zoom by remember { mutableStateOf(1f) }
    var fullscreen by remember { mutableStateOf(false) }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = startPage.coerceIn(0, count - 1)
    )
    val cache = remember(doc) { PageCache() }
    val page by remember { derivedStateOf { listState.firstVisibleItemIndex + 1 } }

    FullscreenEffect(fullscreen)
    BackHandler(enabled = fullscreen) { fullscreen = false }
    DisposableEffect(doc) {
        onDispose {
            onPageSaved(listState.firstVisibleItemIndex)
            cache.clear()
        }
    }

    fun stepZoom(dir: Int) {
        val i = ZOOM_LEVELS.indexOf(zoom).coerceAtLeast(0)
        zoom = ZOOM_LEVELS[(i + dir).coerceIn(0, ZOOM_LEVELS.lastIndex)]
    }

    Scaffold(
        topBar = {
            if (!fullscreen) {
                Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
                    Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = onBack) { Text("Kembali") }
                            Text(name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("  Hal $page / $count", style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { stepZoom(-1) }) { Text("−") }
                            Text("${(zoom * 100).roundToInt()}%", style = MaterialTheme.typography.labelLarge)
                            TextButton(onClick = { stepZoom(1) }) { Text("+") }
                            TextButton(onClick = { fullscreen = true }) { Text("Layar penuh") }
                        }
                    }
                }
            }
        }
    ) { inner ->
        Box(Modifier.padding(inner).fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val density = LocalDensity.current
                val pageWidth: Dp = maxWidth * zoom
                val renderPx = minOf(with(density) { pageWidth.roundToPx() }, MAX_RENDER_PX)

                Box(
                    Modifier
                        .fillMaxSize()
                        .horizontalScroll(rememberScrollState())
                        .pointerInput(Unit) {
                            detectTapGestures(onDoubleTap = { zoom = if (zoom > 1f) 1f else 2f })
                        }
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.width(pageWidth),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(count) { index ->
                            PdfPageView(doc, cache, index, renderPx, pageWidth, doc.firstAspect)
                        }
                    }
                }
            }
            if (fullscreen) {
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("  $page / $count", style = MaterialTheme.typography.labelLarge)
                        TextButton(onClick = { fullscreen = false }) { Text("Keluar") }
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfPageView(
    doc: PdfDoc,
    cache: PageCache,
    index: Int,
    renderPx: Int,
    widthDp: Dp,
    aspect: Float
) {
    val key = index.toLong() * 100_000L + renderPx
    var bmp by remember(key) { mutableStateOf(cache.get(key)) }
    var failed by remember(key) { mutableStateOf(false) }

    LaunchedEffect(key) {
        if (bmp == null) {
            val b = withContext(Dispatchers.IO) { doc.render(index, renderPx) }
            if (b != null) {
                cache.put(key, b)
                bmp = b
            } else {
                cache.clear()
                failed = true
            }
        }
    }

    val b = bmp
    val ratio = if (b != null) b.width.toFloat() / b.height.toFloat() else aspect
    Box(
        Modifier.width(widthDp).aspectRatio(ratio).background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        if (b != null) {
            Image(
                bitmap = b.asImageBitmap(),
                contentDescription = "Halaman ${index + 1}",
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(if (failed) "Gagal memuat halaman" else "Memuat…", color = Color.Gray)
        }
    }
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

@Composable
private fun FullscreenEffect(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled) {
        val window = view.context.findActivity()?.window
        val controller = if (window != null) WindowCompat.getInsetsController(window, view) else null
        if (enabled) {
            controller?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
}
