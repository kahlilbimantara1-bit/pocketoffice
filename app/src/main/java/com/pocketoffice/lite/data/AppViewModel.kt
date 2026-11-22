package com.pocketoffice.lite.data

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pocketoffice.lite.file.FileException
import com.pocketoffice.lite.file.FileKind
import com.pocketoffice.lite.file.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

sealed interface Screen {
    object Main : Screen
    class Pdf(val uri: Uri, val name: String, val startPage: Int) : Screen
    object Editor : Screen
    class Viewer(val uri: Uri, val name: String, val kind: FileKind) : Screen
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val cr get() = getApplication<Application>().contentResolver
    private val recentStore = RecentStore(app)
    private val settingsStore = SettingsStore(app)
    private val draftFile = File(app.filesDir, "draft.txt")
    private val saveLock = Mutex()

    var screen by mutableStateOf<Screen>(Screen.Main)
    var tab by mutableStateOf(0)
    var message by mutableStateOf<String?>(null)
    var recent by mutableStateOf(recentStore.load())
    var themeMode by mutableStateOf(settingsStore.themeMode)
    var fontSp by mutableStateOf(settingsStore.fontSp)

    var editorText by mutableStateOf("")
    var editorUri by mutableStateOf<Uri?>(null)
    var editorName by mutableStateOf(DEFAULT_NAME)
    var savedText by mutableStateOf("")

    // ---------- Pengaturan ----------
    fun setTheme(mode: Int) { themeMode = mode; settingsStore.themeMode = mode }
    fun setFont(sp: Int) { fontSp = sp; settingsStore.fontSp = sp }

    // ---------- Membuka dokumen ----------
    fun openRecent(doc: RecentDoc) = openUri(Uri.parse(doc.uri), fromRecent = true)

    fun openUri(uri: Uri, fromRecent: Boolean = false) {
        if (screen !is Screen.Main) return
        viewModelScope.launch {
            try {
                val info = withContext(Dispatchers.IO) { FileUtils.inspect(cr, uri) }
                if (!fromRecent) takePermission(uri)
                when (info.kind) {
                    FileKind.PDF -> {
                        if (info.size == 0L) throw FileException("File PDF kosong.")
                        if (info.size > FileUtils.MAX_PDF_BYTES) throw FileException(FileUtils.TOO_BIG)
                        val page = recent.firstOrNull { it.uri == uri.toString() }?.lastPage ?: 0
                        screen = Screen.Pdf(uri, info.name, page)
                    }
                    FileKind.TXT -> {
                        if (info.size > FileUtils.MAX_TXT_BYTES) throw FileException(FileUtils.TOO_BIG)
                        val text = withContext(Dispatchers.IO) { FileUtils.readText(cr, uri) }
                        editorUri = uri
                        editorName = info.name
                        editorText = text
                        savedText = text
                        addRecent(uri, info.name, "TXT")
                        screen = Screen.Editor
                        if (text.isEmpty()) message = "File ini kosong. Silakan mulai menulis."
                    }
                    FileKind.DOCX, FileKind.XLSX, FileKind.PPTX, FileKind.CSV -> {
                        if (info.size == 0L) throw FileException("File ini kosong.")
                        val limit = if (info.kind == FileKind.CSV) FileUtils.MAX_CSV_BYTES else FileUtils.MAX_OFFICE_BYTES
                        if (info.size > limit) throw FileException(FileUtils.TOO_BIG)
                        screen = Screen.Viewer(uri, info.name, info.kind)
                    }
                    FileKind.LEGACY ->
                        message = "Format lama (.doc, .xls, .ppt) belum didukung. Simpan ulang sebagai .docx, .xlsx, atau .pptx."
                    FileKind.UNSUPPORTED ->
                        message = "Format tidak didukung. Yang bisa dibuka: PDF, TXT, DOCX, XLSX, PPTX, CSV."
                }
            } catch (e: FileException) {
                message = e.message
                if (fromRecent && e.gone) removeRecent(uri.toString())
            } catch (e: Exception) {
                message = "Terjadi kesalahan saat membuka file."
            }
        }
    }

    private fun takePermission(uri: Uri) {
        val rw = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            cr.takePersistableUriPermission(uri, rw)
        } catch (e: Exception) {
            try {
                cr.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e2: Exception) { /* tidak semua penyedia mendukung */ }
        }
    }

    // ---------- PDF ----------
    fun onPdfLoaded(uri: Uri, name: String) = addRecent(uri, name, "PDF")
    fun closePdf() { screen = Screen.Main }

    fun savePdfPage(uri: Uri, page: Int) {
        val s = uri.toString()
        if (recent.none { it.uri == s }) return
        recent = recent.map { if (it.uri == s) it.copy(lastPage = page) else it }
        recentStore.save(recent)
    }

    // ---------- Viewer Office ----------
    fun onViewerLoaded(uri: Uri, name: String, kind: FileKind) = addRecent(uri, name, kind.name)
    fun closeViewer() { screen = Screen.Main }

    // ---------- Editor ----------
    fun newDocument() {
        if (screen !is Screen.Main) return
        viewModelScope.launch {
            val draft = withContext(Dispatchers.IO) {
                try { if (draftFile.exists()) draftFile.readText() else "" } catch (e: Exception) { "" }
            }
            editorUri = null
            editorName = DEFAULT_NAME
            editorText = draft
            savedText = draft
            screen = Screen.Editor
            if (draft.isNotBlank()) message = "Draf sebelumnya dipulihkan."
        }
    }

    fun closeEditor() {
        if (editorUri == null && editorText.isNotBlank()) {
            message = "Draf tersimpan otomatis. Pakai \"Simpan Sebagai\" untuk membuat file."
        }
        flush()
        screen = Screen.Main
    }

    /** Menyimpan perubahan yang belum tersimpan (dipanggil saat keluar/ke background). */
    fun flush() {
        if (screen !is Screen.Editor && screen !is Screen.Main) return
        if (editorText == savedText) return
        val t = editorText
        val u = editorUri
        viewModelScope.launch { persist(t, u)?.let { message = it } }
    }

    fun autosave() {
        val t = editorText
        if (t == savedText) return
        val u = editorUri
        viewModelScope.launch { persist(t, u)?.let { message = it } }
    }

    fun saveCurrent() {
        val t = editorText
        val u = editorUri ?: return
        viewModelScope.launch { message = persist(t, u) ?: "Tersimpan." }
    }

    fun saveAsTo(uri: Uri) {
        viewModelScope.launch {
            val text = editorText
            val wasDraft = editorUri == null
            val err = persist(text, uri)
            if (err != null) { message = err; return@launch }
            takePermission(uri)
            val info = try {
                withContext(Dispatchers.IO) { FileUtils.inspect(cr, uri) }
            } catch (e: Exception) { null }
            editorUri = uri
            editorName = info?.name ?: editorName
            savedText = text
            if (wasDraft) withContext(Dispatchers.IO) { runCatching { draftFile.delete() } }
            addRecent(uri, editorName, "TXT")
            message = "Disimpan: $editorName"
        }
    }

    fun rename(raw: String) {
        viewModelScope.launch {
            val name = FileUtils.cleanTxtName(raw)
            if (name == null) { message = "Nama file tidak valid."; return@launch }
            val uri = editorUri
            if (uri == null) { editorName = name; return@launch }
            try {
                val newUri = withContext(Dispatchers.IO) { DocumentsContract.renameDocument(cr, uri, name) }
                if (newUri == null) {
                    message = "Lokasi penyimpanan ini tidak mendukung ganti nama. Coba \"Simpan Sebagai\"."
                    return@launch
                }
                val info = try {
                    withContext(Dispatchers.IO) { FileUtils.inspect(cr, newUri) }
                } catch (e: Exception) { null }
                val old = uri.toString()
                val finalName = info?.name ?: name
                recent = recent.map {
                    if (it.uri == old) it.copy(uri = newUri.toString(), name = finalName) else it
                }
                recentStore.save(recent)
                editorUri = newUri
                editorName = finalName
                message = "Nama file diubah."
            } catch (e: Exception) {
                message = "Gagal mengganti nama. Lokasi penyimpanan mungkin tidak mendukungnya."
            }
        }
    }

    private suspend fun persist(text: String, uri: Uri?): String? = saveLock.withLock {
        val err = withContext(Dispatchers.IO) {
            try {
                if (uri == null) draftFile.writeText(text) else FileUtils.writeText(cr, uri, text)
                null
            } catch (e: FileException) {
                e.message
            } catch (e: Exception) {
                "Gagal menyimpan."
            }
        }
        if (err == null && uri == editorUri) savedText = text
        err
    }

    // ---------- Dokumen terbaru ----------
    private fun addRecent(uri: Uri, name: String, type: String) {
        val s = uri.toString()
        val page = recent.firstOrNull { it.uri == s }?.lastPage ?: 0
        val item = RecentDoc(s, name, type, System.currentTimeMillis(), page)
        recent = (listOf(item) + recent.filter { it.uri != s }).take(MAX_RECENT)
        recentStore.save(recent)
    }

    fun removeRecent(uri: String) {
        recent = recent.filter { it.uri != uri }
        recentStore.save(recent)
    }

    fun clearRecent() {
        recent = emptyList()
        recentStore.save(recent)
    }

    companion object {
        const val DEFAULT_NAME = "Tanpa Judul.txt"
        const val MAX_RECENT = 20
    }
}
