package com.pocketoffice.lite.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class RecentDoc(
    val uri: String,
    val name: String,
    val type: String,
    val lastOpened: Long,
    val lastPage: Int = 0
)

/** Menyimpan metadata dokumen terbaru (bukan salinan file) sebagai JSON kecil. */
class RecentStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("recent_docs", Context.MODE_PRIVATE)

    fun load(): List<RecentDoc> {
        val raw = prefs.getString("items", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val out = ArrayList<RecentDoc>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    RecentDoc(
                        o.getString("uri"), o.getString("name"), o.getString("type"),
                        o.optLong("t"), o.optInt("page")
                    )
                )
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(list: List<RecentDoc>) {
        val arr = JSONArray()
        list.forEach { d ->
            arr.put(
                JSONObject().put("uri", d.uri).put("name", d.name).put("type", d.type)
                    .put("t", d.lastOpened).put("page", d.lastPage)
            )
        }
        prefs.edit().putString("items", arr.toString()).apply()
    }
}

class SettingsStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** 0 = ikuti sistem, 1 = terang, 2 = gelap */
    var themeMode: Int
        get() = prefs.getInt("theme", 0)
        set(v) = prefs.edit().putInt("theme", v).apply()

    var fontSp: Int
        get() = prefs.getInt("font", 16)
        set(v) = prefs.edit().putInt("font", v).apply()
}
