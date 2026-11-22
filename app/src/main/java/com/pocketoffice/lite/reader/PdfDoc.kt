package com.pocketoffice.lite.reader

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import com.pocketoffice.lite.file.FileException
import java.io.FileNotFoundException
import kotlin.math.roundToInt

/**
 * Pembungkus PdfRenderer. PdfRenderer hanya boleh membuka satu halaman sekaligus,
 * jadi semua akses dikunci. Halaman dirender per kebutuhan, bukan sekaligus.
 */
class PdfDoc private constructor(
    private val pfd: ParcelFileDescriptor,
    private val renderer: PdfRenderer
) {
    private val lock = Any()
    private var closed = false

    val pageCount: Int = renderer.pageCount

    /** Rasio lebar/tinggi halaman pertama, dipakai sebagai ukuran sementara. */
    val firstAspect: Float = run {
        val p = renderer.openPage(0)
        try { p.width.toFloat() / p.height.toFloat() } finally { p.close() }
    }

    fun render(index: Int, widthPx: Int): Bitmap? = synchronized(lock) {
        if (closed || index < 0 || index >= pageCount) return null
        try {
            val page = renderer.openPage(index)
            try {
                val h = (widthPx.toFloat() * page.height / page.width).roundToInt().coerceAtLeast(1)
                val bmp = Bitmap.createBitmap(widthPx, h, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bmp
            } finally {
                page.close()
            }
        } catch (e: OutOfMemoryError) {
            null
        } catch (e: Exception) {
            null
        }
    }

    fun close() = synchronized(lock) {
        if (!closed) {
            closed = true
            try { renderer.close() } catch (e: Exception) { }
            try { pfd.close() } catch (e: Exception) { }
        }
    }

    companion object {
        fun open(cr: ContentResolver, uri: Uri): PdfDoc {
            val pfd = (try {
                cr.openFileDescriptor(uri, "r")
            } catch (e: FileNotFoundException) {
                throw FileException("File tidak ditemukan.", gone = true)
            } catch (e: SecurityException) {
                throw FileException("Izin akses file hilang. Silakan pilih file itu lagi.", gone = true)
            }) ?: throw FileException("File tidak dapat dibaca.")
            try {
                val r = PdfRenderer(pfd)
                if (r.pageCount <= 0) {
                    r.close()
                    throw FileException("PDF ini tidak memiliki halaman.")
                }
                return PdfDoc(pfd, r)
            } catch (e: FileException) {
                pfd.close()
                throw e
            } catch (e: SecurityException) {
                pfd.close()
                throw FileException("PDF ini dilindungi kata sandi dan belum bisa dibuka.")
            } catch (e: Exception) {
                pfd.close()
                throw FileException("PDF rusak atau tidak dapat dibaca.")
            }
        }
    }
}

/** Cache bitmap halaman dengan batas memori (maks. 1/6 heap atau 40 MB). */
class PageCache {
    private val maxBytes = minOf(Runtime.getRuntime().maxMemory() / 6, 40L * 1024 * 1024).toInt()
    private val lru = object : LruCache<Long, Bitmap>(maxBytes) {
        override fun sizeOf(key: Long, value: Bitmap): Int = value.byteCount
    }

    fun get(key: Long): Bitmap? = lru.get(key)
    fun put(key: Long, bmp: Bitmap) { lru.put(key, bmp) }
    fun clear() = lru.evictAll()
}
