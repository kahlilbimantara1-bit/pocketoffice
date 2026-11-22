package com.pocketoffice.lite.reader

import android.content.Context
import android.content.ContentResolver
import android.net.Uri
import android.util.Xml
import com.pocketoffice.lite.file.FileException
import com.pocketoffice.lite.file.FileKind
import com.pocketoffice.lite.file.FileUtils
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.math.BigDecimal
import java.math.MathContext
import java.util.zip.ZipException
import java.util.zip.ZipFile

class ViewSection(
    val title: String,
    val paragraphs: List<String>,
    val rows: List<List<String>>?,
    val truncated: Boolean = false
)

class ViewDoc(val kind: FileKind, val sections: List<ViewSection>)

/**
 * Pembaca ringan DOCX / XLSX / PPTX / CSV tanpa library tambahan.
 * Hanya mengambil teks dan tabel (tanpa gambar, grafik, dan format visual).
 */
object OfficeParser {
    private const val MAX_ROWS = 3000
    private const val MAX_COLS = 50
    private const val MAX_PARAS = 20000

    fun parse(context: Context, uri: Uri, kind: FileKind): ViewDoc =
        if (kind == FileKind.CSV) parseCsv(context.contentResolver, uri)
        else parseOoxml(context, uri, kind)

    // ---------- Umum ----------
    private fun parseOoxml(context: Context, uri: Uri, kind: FileKind): ViewDoc {
        val tmp = File(context.cacheDir, "view_tmp.zip")
        try {
            copyToTemp(context.contentResolver, uri, tmp)
            ZipFile(tmp).use { zip ->
                return when (kind) {
                    FileKind.DOCX -> docx(zip)
                    FileKind.XLSX -> xlsx(zip)
                    FileKind.PPTX -> pptx(zip)
                    else -> throw FileException("Format tidak didukung.")
                }
            }
        } catch (e: FileException) {
            throw e
        } catch (e: ZipException) {
            throw FileException("File rusak atau dilindungi kata sandi, sehingga tidak bisa dibuka.")
        } catch (e: XmlPullParserException) {
            throw FileException("Isi file rusak dan tidak bisa dibaca.")
        } catch (e: FileNotFoundException) {
            throw FileException(FileUtils.NOT_FOUND, gone = true)
        } catch (e: SecurityException) {
            throw FileException(FileUtils.NO_PERMISSION, gone = true)
        } catch (e: IOException) {
            throw FileException(FileUtils.NOT_READ)
        } finally {
            tmp.delete()
        }
    }

    private fun copyToTemp(cr: ContentResolver, uri: Uri, out: File) {
        val input = cr.openInputStream(uri) ?: throw FileException(FileUtils.NOT_READ)
        input.use { s ->
            out.outputStream().use { o ->
                val buf = ByteArray(16 * 1024)
                var total = 0L
                while (true) {
                    val n = s.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > FileUtils.MAX_OFFICE_BYTES) throw FileException(FileUtils.TOO_BIG)
                    o.write(buf, 0, n)
                }
            }
        }
    }

    private fun newParser(s: InputStream): XmlPullParser {
        val p = Xml.newPullParser()
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        p.setInput(s, null)
        return p
    }

    private fun XmlPullParser.attr(name: String): String? {
        for (i in 0 until attributeCount) if (getAttributeName(i) == name) return getAttributeValue(i)
        return null
    }

    // ---------- DOCX ----------
    private fun docx(zip: ZipFile): ViewDoc {
        val entry = zip.getEntry("word/document.xml")
            ?: throw FileException("Ini bukan file DOCX yang valid.")
        val paras = ArrayList<String>()
        val sb = StringBuilder()
        val cell = StringBuilder()
        val row = ArrayList<String>()
        var tableDepth = 0
        var truncated = false

        zip.getInputStream(entry).use { s ->
            val p = newParser(s)
            var ev = p.eventType
            loop@ while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG) {
                    when (p.name) {
                        "t" -> sb.append(p.nextText())
                        "tab" -> sb.append('\t')
                        "br", "cr" -> sb.append('\n')
                        "tbl" -> tableDepth++
                    }
                } else if (ev == XmlPullParser.END_TAG) {
                    when (p.name) {
                        "p" -> {
                            val text = sb.toString()
                            sb.setLength(0)
                            if (tableDepth > 0) {
                                if (text.isNotBlank()) {
                                    if (cell.isNotEmpty()) cell.append(' ')
                                    cell.append(text.trim())
                                }
                            } else {
                                if (text.isNotBlank() || (paras.isNotEmpty() && paras.last().isNotEmpty())) {
                                    paras.add(text)
                                }
                                if (paras.size >= MAX_PARAS) { truncated = true; break@loop }
                            }
                        }
                        "tc" -> { row.add(cell.toString()); cell.setLength(0) }
                        "tr" -> { paras.add(row.joinToString("  |  ")); row.clear() }
                        "tbl" -> if (tableDepth > 0) tableDepth--
                    }
                }
                ev = p.next()
            }
        }
        val list = if (truncated) paras + "… (dokumen dipotong karena terlalu panjang)" else paras
        return ViewDoc(FileKind.DOCX, listOf(ViewSection("", list, null)))
    }

    // ---------- PPTX ----------
    private fun pptx(zip: ZipFile): ViewDoc {
        val regex = Regex("ppt/slides/slide(\\d+)\\.xml")
        val slides = zip.entries().asSequence()
            .mapNotNull { e -> regex.matchEntire(e.name)?.let { m -> m.groupValues[1].toInt() to e.name } }
            .sortedBy { it.first }
            .toList()
        if (slides.isEmpty()) throw FileException("Ini bukan file PPTX yang valid.")

        val sections = slides.map { (num, name) ->
            val paras = ArrayList<String>()
            val sb = StringBuilder()
            zip.getInputStream(zip.getEntry(name)).use { s ->
                val p = newParser(s)
                var ev = p.eventType
                while (ev != XmlPullParser.END_DOCUMENT) {
                    if (ev == XmlPullParser.START_TAG) {
                        when (p.name) {
                            "t" -> sb.append(p.nextText())
                            "br" -> sb.append('\n')
                        }
                    } else if (ev == XmlPullParser.END_TAG && p.name == "p") {
                        val t = sb.toString().trim()
                        sb.setLength(0)
                        if (t.isNotEmpty()) paras.add(t)
                    }
                    ev = p.next()
                }
            }
            if (paras.isEmpty()) paras.add("(slide ini tidak berisi teks)")
            ViewSection("Slide $num", paras, null)
        }
        return ViewDoc(FileKind.PPTX, sections)
    }

    // ---------- XLSX ----------
    private fun xlsx(zip: ZipFile): ViewDoc {
        if (zip.getEntry("xl/workbook.xml") == null) throw FileException("Ini bukan file XLSX yang valid.")
        val shared = sharedStrings(zip)
        val sheets = sheetList(zip)
        if (sheets.isEmpty()) throw FileException("File ini tidak memiliki lembar kerja.")
        return ViewDoc(FileKind.XLSX, sheets.map { (name, path) -> sheet(zip, name, path, shared) })
    }

    private fun sharedStrings(zip: ZipFile): List<String> {
        val entry = zip.getEntry("xl/sharedStrings.xml") ?: return emptyList()
        val list = ArrayList<String>()
        val sb = StringBuilder()
        var inSi = false
        var inRph = false
        zip.getInputStream(entry).use { s ->
            val p = newParser(s)
            var ev = p.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG) {
                    when (p.name) {
                        "si" -> { inSi = true; sb.setLength(0) }
                        "rPh" -> inRph = true
                        "t" -> if (inSi && !inRph) sb.append(p.nextText())
                    }
                } else if (ev == XmlPullParser.END_TAG) {
                    when (p.name) {
                        "rPh" -> inRph = false
                        "si" -> { list.add(sb.toString()); inSi = false }
                    }
                }
                ev = p.next()
            }
        }
        return list
    }

    private fun sheetList(zip: ZipFile): List<Pair<String, String>> {
        val rels = HashMap<String, String>()
        zip.getEntry("xl/_rels/workbook.xml.rels")?.let { e ->
            zip.getInputStream(e).use { s ->
                val p = newParser(s)
                var ev = p.eventType
                while (ev != XmlPullParser.END_DOCUMENT) {
                    if (ev == XmlPullParser.START_TAG && p.name == "Relationship") {
                        val id = p.attr("Id")
                        val target = p.attr("Target")
                        if (id != null && target != null) {
                            rels[id] = if (target.startsWith("/")) target.substring(1) else "xl/$target"
                        }
                    }
                    ev = p.next()
                }
            }
        }
        val result = ArrayList<Pair<String, String>>()
        zip.getEntry("xl/workbook.xml")?.let { e ->
            zip.getInputStream(e).use { s ->
                val p = newParser(s)
                var ev = p.eventType
                while (ev != XmlPullParser.END_DOCUMENT) {
                    if (ev == XmlPullParser.START_TAG && p.name == "sheet") {
                        val name = p.attr("name") ?: "Lembar ${result.size + 1}"
                        val path = rels[p.attr("id") ?: ""]
                        if (path != null && zip.getEntry(path) != null) result.add(name to path)
                    }
                    ev = p.next()
                }
            }
        }
        if (result.isEmpty()) {
            zip.entries().asSequence().map { it.name }
                .filter { it.startsWith("xl/worksheets/sheet") && it.endsWith(".xml") }
                .sorted()
                .forEachIndexed { i, n -> result.add("Lembar ${i + 1}" to n) }
        }
        return result
    }

    private fun colIndex(ref: String): Int {
        var n = 0
        var any = false
        for (ch in ref) {
            val c = ch.uppercaseChar()
            if (c in 'A'..'Z') { n = n * 26 + (c - 'A' + 1); any = true } else break
        }
        return if (any) n - 1 else -1
    }

    private fun fmtNum(s: String): String {
        if (s.isEmpty()) return s
        return try {
            BigDecimal(s).round(MathContext(12)).stripTrailingZeros().toPlainString()
        } catch (e: Exception) {
            s
        }
    }

    private fun sheet(zip: ZipFile, name: String, path: String, shared: List<String>): ViewSection {
        val rows = ArrayList<List<String>>()
        var cur: ArrayList<String>? = null
        var col = 0
        var type: String? = null
        var v = ""
        var lastRow = 0
        var truncated = false

        zip.getInputStream(zip.getEntry(path)).use { s ->
            val p = newParser(s)
            var ev = p.eventType
            loop@ while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG) {
                    when (p.name) {
                        "row" -> {
                            val rn = p.attr("r")?.toIntOrNull() ?: (lastRow + 1)
                            if (rn > lastRow + 1) repeat(minOf(rn - lastRow - 1, 200)) { rows.add(emptyList()) }
                            lastRow = rn
                            cur = ArrayList()
                        }
                        "c" -> {
                            col = colIndex(p.attr("r") ?: "")
                            if (col < 0) col = cur?.size ?: 0
                            type = p.attr("t")
                            v = ""
                        }
                        "v" -> v = p.nextText()
                        "t" -> if (type == "inlineStr") v += p.nextText()
                    }
                } else if (ev == XmlPullParser.END_TAG) {
                    when (p.name) {
                        "c" -> {
                            val text = when (type) {
                                "s" -> shared.getOrElse(v.trim().toIntOrNull() ?: -1) { "" }
                                "b" -> if (v.trim() == "1") "TRUE" else "FALSE"
                                "str", "inlineStr", "e", "d" -> v
                                else -> fmtNum(v.trim())
                            }
                            val c = cur
                            if (c != null && col < MAX_COLS) {
                                while (c.size < col) c.add("")
                                if (c.size == col) c.add(text) else c[col] = text
                            }
                        }
                        "row" -> {
                            cur?.let { rows.add(it) }
                            cur = null
                            if (rows.size >= MAX_ROWS) { truncated = true; break@loop }
                        }
                    }
                }
                ev = p.next()
            }
        }
        return ViewSection(name, emptyList(), rows, truncated)
    }

    // ---------- CSV ----------
    private fun parseCsv(cr: ContentResolver, uri: Uri): ViewDoc {
        val text = FileUtils.readText(cr, uri, FileUtils.MAX_CSV_BYTES)
        val firstLine = text.substringBefore('\n')
        val delim = listOf(',', ';', '\t').maxByOrNull { d -> firstLine.count { it == d } } ?: ','
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val f = StringBuilder()
        var inQ = false
        var truncated = false
        var i = 0
        loop@ while (i < text.length) {
            val ch = text[i]
            if (inQ) {
                if (ch == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { f.append('"'); i++ } else inQ = false
                } else f.append(ch)
            } else {
                when (ch) {
                    '"' -> if (f.isEmpty()) inQ = true else f.append(ch)
                    delim -> { row.add(f.toString()); f.setLength(0) }
                    '\r' -> {}
                    '\n' -> {
                        row.add(f.toString()); f.setLength(0)
                        rows.add(row.take(MAX_COLS))
                        row = ArrayList()
                        if (rows.size >= MAX_ROWS) { truncated = true; break@loop }
                    }
                    else -> f.append(ch)
                }
            }
            i++
        }
        if (!truncated && (f.isNotEmpty() || row.isNotEmpty())) {
            row.add(f.toString())
            rows.add(row.take(MAX_COLS))
        }
        return ViewDoc(FileKind.CSV, listOf(ViewSection("CSV", emptyList(), rows, truncated)))
    }
}
