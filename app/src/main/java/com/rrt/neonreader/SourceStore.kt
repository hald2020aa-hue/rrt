package com.rrt.neonreader

import android.content.Context
import org.json.JSONArray

class SourceStore(context: Context) {
    private val prefs = context.getSharedPreferences("rrt_neon_data", Context.MODE_PRIVATE)
    var adultsEnabled: Boolean
        get() = prefs.getBoolean("adultsEnabled", false)
        set(value) { prefs.edit().putBoolean("adultsEnabled", value).apply() }
    fun sources(): MutableList<SourceDef> = parseSources(prefs.getString("sources", "[]") ?: "[]")
    private fun parseSources(text: String): MutableList<SourceDef> {
        val arr = JSONArray(text)
        return (0 until arr.length()).map { SourceDef.fromJson(arr.getJSONObject(it)) }.toMutableList()
    }
    fun saveSources(sources: List<SourceDef>) {
        val a = JSONArray(); sources.forEach { a.put(it.toJson()) }
        prefs.edit().putString("sources", a.toString()).apply()
    }
    fun favorites(): MutableList<Manga> {
        val arr = JSONArray(prefs.getString("favorites", "[]") ?: "[]")
        return (0 until arr.length()).map { Manga.fromJson(arr.getJSONObject(it)) }.toMutableList()
    }
    fun saveFavorites(items: List<Manga>) {
        val a = JSONArray(); items.forEach { a.put(it.toJson()) }
        prefs.edit().putString("favorites", a.toString()).apply()
    }
    fun exportJson(): String {
        val array = JSONArray(); sources().forEach { array.put(it.toJson()) }
        return array.toString(2)
    }
    fun importJson(value: String): Int {
        val imported = if (value.trim().startsWith("[")) parseSources(value) else mutableListOf(SourceDef.fromJson(org.json.JSONObject(value)))
        require(imported.isNotEmpty()) { "لا توجد مصادر في JSON" }
        val current = sources()
        imported.forEach { source ->
            SourceEngine.normalizeBase(source.baseUrl)
            current.removeAll { it.name == source.name }
            current.add(source)
        }
        saveSources(current)
        return imported.size
    }
}
