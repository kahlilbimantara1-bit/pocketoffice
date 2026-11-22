package com.pocketoffice.lite.file

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.IOException

enum class FileKind { PDF, TXT, DOCX, XLSX, PPTX, CSV, LEGACY, UNSUPPORTED }

class FileException(message: String, val gone: Boolean = false) : Exception(message)

data class FileInfo(val name: String, val size: Long, val kind: FileKind)

object FileUtils {
    const val MAX_TXT_BYTES = 1_000_000L
    const val MAX_PDF_BYTES = 200L * 1024 * 1024
    const val MAX_OFFICE_BYTES = 30L * 1024 * 1024
    const val MAX_CSV_BYTES = 3L * 1024 * 1024

    const val NOT_FOUND = "File tidak ditemukan. Mungkin sudah dipindah atau dihapus."
    const val NOT_READ = "File tidak dapat dibaca."
    const val NO_PERMISSION = "Izin akses file hilang. Silakan pilih file itu lagi."
    const val TOO_BIG = "File terlalu besar untuk dibuka di aplikasi ini."
    const val SAVE_FAIL = "Gagal menyimpan file. Pastikan penyimpanan tidak penuh dan file bisa ditulis."

    fun inspect(cr: ContentResolver, uri: Uri): FileInfo {
        var name: String? = null
        var size = -1L
        try {
            cr.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val n = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val s = c.getColumnIndex(OpenableColumns.SIZE)
                    if (n >= 0 && !c.isNull(n)) name = c.getString(n)
                    if (s >= 0 && !c.isNull(s)) size = c.getLong(s)
                } else {
                    throw FileException(NOT_FOUND, gone = true)
                }
            } ?: throw FileException(NOT_FOUND, gone = true)
        } catch (e: FileException) {
            throw e
        } catch (e: SecurityException) {
            throw FileException(NO_PERMISSION, gone = true)
        } catch (e: Exception) {
            throw FileException(NOT_FOUND, gone = true)
        }
        val finalName = name ?: uri.lastPathSegment ?: "Tanpa nama"
        val mime = try { cr.getType(uri) } catch (e: Exception) { null }
        val lower = finalName.lowercase()
        val kind = when {
            lower.endsWith(".pdf") || mime == "application/pdf" -> FileKind.PDF
            lower.endsWith(".docx") || mime?.contains("wordprocessingml") == true -> FileKind.DOCX
            lower.endsWith(".xlsx") || lower.endsWith(".xlsm") || mime?.contains("spreadsheetml") == true -> FileKind.XLSX
            lower.endsWith(".pptx") || mime?.contains("presentationml") == true -> FileKind.PPTX
            lower.endsWith(".csv") || mime == "text/csv" || mime == "text/comma-separated-values" -> FileKind.CSV
            lower.endsWith(".doc") || lower.endsWith(".xls") || lower.endsWith(".ppt") -> FileKind.LEGACY
            lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".log") || mime == "text/plain" -> FileKind.TXT
            else -> FileKind.UNSUPPORTED
        }
        return FileInfo(finalName, size, kind)
    }

    fun readText(cr: ContentResolver, uri: Uri, max: Long = MAX_TXT_BYTES): String {
        try {
            val input = cr.openInputStream(uri) ?: throw FileException(NOT_READ)
            input.use { s ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(8192)
                var total = 0L
                while (true) {
                    val n = s.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > max) throw FileException(TOO_BIG)
                    out.write(buf, 0, n)
                }
                return String(out.toByteArray(), Charsets.UTF_8).removePrefix("\uFEFF")
            }
        } catch (e: FileException) {
            throw e
        } catch (e: FileNotFoundException) {
            throw FileException(NOT_FOUND, gone = true)
        } catch (e: SecurityException) {
            throw FileException(NO_PERMISSION, gone = true)
        } catch (e: IOException) {
            throw FileException(NOT_READ)
        }
    }

    fun writeText(cr: ContentResolver, uri: Uri, text: String) {
        try {
            val out = cr.openOutputStream(uri, "wt") ?: throw FileException(SAVE_FAIL)
            out.use {
                it.write(text.toByteArray(Charsets.UTF_8))
                it.flush()
            }
        } catch (e: FileException) {
            throw e
        } catch (e: Exception) {
            throw FileException(SAVE_FAIL)
        }
    }

    /** Membersihkan nama file; null jika tidak valid. Selalu berakhiran .txt */
    fun cleanTxtName(raw: String): String? {
        val n = raw.replace(Regex("[\\\\/:*?\"<>|]"), "").trim().trimEnd('.')
        if (n.isBlank() || n.lowercase() == ".txt") return null
        val full = if (n.lowercase().endsWith(".txt")) n else "$n.txt"
        return if (full.length > 100) null else full
    }
}
