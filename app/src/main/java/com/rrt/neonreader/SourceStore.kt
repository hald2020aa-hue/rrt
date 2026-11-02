package com.rrt.neonreader

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class SourceStore(context:Context) {
    private val prefs=context.getSharedPreferences("rrt_neon_data",Context.MODE_PRIVATE)
    var adultsEnabled:Boolean
        get()=prefs.getBoolean("adultsEnabled",false)
        set(v){prefs.edit().putBoolean("adultsEnabled",v).apply()}
    var gridColumns:Int
        get()=prefs.getInt("gridColumns",3).coerceIn(2,6)
        set(v){prefs.edit().putInt("gridColumns",v.coerceIn(2,6)).apply()}
    var rightToLeft:Boolean
        get()=prefs.getBoolean("readerRTL",false)
        set(v){prefs.edit().putBoolean("readerRTL",v).apply()}
    var horizontalReader:Boolean
        get()=prefs.getBoolean("horizontalReader",false)
        set(v){prefs.edit().putBoolean("horizontalReader",v).apply()}
    private fun sourcesArray(str:String):MutableList<SourceDef> {
        val arr=JSONArray(str)
        return (0 until arr.length()).map{SourceDef.fromJson(arr.getJSONObject(it))}.toMutableList()
    }
    fun sources(): MutableList<SourceDef> = sourcesArray(prefs.getString("sources","[]")?:"[]")
    fun visibleSources():List<SourceDef> = sources().filter { it.enabled && (!it.adult || adultsEnabled) }
    fun saveSources(list:List<SourceDef>) {
        val arr=JSONArray();list.forEach{arr.put(it.toJson())}
        prefs.edit().putString("sources",arr.toString()).apply()
    }
    fun favorites():MutableList<Manga> {
        val a=JSONArray(prefs.getString("favorites","[]")?:"[]")
        return (0 until a.length()).map{Manga.fromJson(a.getJSONObject(it))}.toMutableList()
    }
    fun saveFavorites(items:List<Manga>) {
        val a=JSONArray();items.distinctBy{it.url}.forEach{a.put(it.toJson())}
        prefs.edit().putString("favorites",a.toString()).apply()
    }
    fun history():MutableList<Manga> {
        val a=JSONArray(prefs.getString("history","[]")?:"[]")
        return (0 until a.length()).map{Manga.fromJson(a.getJSONObject(it))}.toMutableList()
    }
    fun addHistory(item:Manga) {
        val all=history();all.removeAll{it.url==item.url};all.add(0,item)
        val a=JSONArray();all.take(200).forEach{a.put(it.toJson())}
        prefs.edit().putString("history",a.toString()).apply()
    }
    fun progressKey(chapter:String)= "progress_"+chapter.hashCode().toUInt().toString(16)
    fun progress(chapter:String):Int=prefs.getInt(progressKey(chapter),0)
    fun setProgress(chapter:String,page:Int){prefs.edit().putInt(progressKey(chapter),page).apply()}
    fun exportJson():String {
        val a=JSONArray();sources().forEach{a.put(it.toJson())};return a.toString(2)
    }
    fun importJson(json:String):Int {
        val items=if(json.trim().startsWith("[")) sourcesArray(json)
            else mutableListOf(SourceDef.fromJson(JSONObject(json)))
        require(items.isNotEmpty()){ "الملف لا يحتوي على مصادر" }
        val existing=sources()
        items.forEach { s-> SourceEngine.normalizeBase(s.baseUrl)
            existing.removeAll{it.name==s.name};existing.add(s) }
        saveSources(existing)
        return items.size
    }
}
