package com.rrt.neonreader

import android.content.Context
import android.os.Environment
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Files are private to this app; chapters remain available without network access. */
data class SavedChapter(val title: String, val chapterUrl: String, val source: SourceDef, val pageCount: Int)

object DownloadUtil {
    private fun key(url: String): String = MessageDigest.getInstance("SHA-256")
        .digest(url.toByteArray(Charsets.UTF_8))
        .take(12).joinToString("") { "%02x".format(it) }
    private fun root(context: Context): File {
        val parent = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir
        return File(parent, "RRTReader").apply { mkdirs() }
    }
    fun chapterDir(context: Context, url: String): File = File(root(context), key(url))
    fun pageFile(context: Context, chapterUrl: String, pageIndex: Int): File =
        File(chapterDir(context, chapterUrl), "page_${(pageIndex + 1).toString().padStart(4, '0')}.img")

    fun list(context: Context): List<SavedChapter> = root(context).listFiles()?.mapNotNull { dir ->
        try {
            val j = JSONObject(File(dir, "chapter.json").readText())
            SavedChapter(j.getString("title"), j.getString("chapterUrl"),
                SourceDef.fromJson(j.getJSONObject("source")), j.getInt("pageCount"))
        } catch (_: Exception) { null }
    }?.sortedBy { it.title } ?: emptyList()

    fun offlinePages(context: Context, chapterUrl: String): List<File> =
        chapterDir(context, chapterUrl).listFiles()?.filter { it.name.matches(Regex("page_\\d+\\.img")) }
            ?.sortedBy { it.name } ?: emptyList()

    fun download(context: Context, source: SourceDef, title: String, chapterUrl: String,
                 urls: List<String>, progress: (Int, Int) -> Unit) {
        require(urls.isNotEmpty()) { "هذا الفصل لا يحتوي على صور" }
        require(urls.size <= 400) { "عدد الصفحات يتجاوز الحد 400" }
        val dir = chapterDir(context, chapterUrl).apply { mkdirs() }
        urls.forEachIndexed { index, link ->
            val file = pageFile(context, chapterUrl, index)
            if (!file.isFile || file.length() == 0L) {
                require(link.startsWith("https://")) { "رابط الصورة ليس HTTPS" }
                val temp = File(dir, file.name + ".part")
                val conn = (URL(link).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 20000; readTimeout = 30000
                    setRequestProperty("User-Agent", SourceEngine.userAgent())
                    setRequestProperty("Referer", chapterUrl)
                    instanceFollowRedirects = true
                }
                try {
                    if (conn.responseCode !in 200..299) error("تعذر تنزيل الصفحة ${index + 1}: HTTP ${conn.responseCode}")
                    val contentType = conn.contentType.orEmpty()
                    if (!contentType.startsWith("image/", true)) error("استجابة الصفحة ليست صورة (${contentType})")
                    conn.inputStream.use { input -> temp.outputStream().use { out -> input.copyTo(out) } }
                    require(temp.length() > 0L) { "ملف صفحة فارغ" }
                    if (!temp.renameTo(file)) { temp.copyTo(file, overwrite = true); temp.delete() }
                } finally {
                    conn.disconnect(); temp.delete()
                }
            }
            progress(index + 1, urls.size)
        }
        val metadata = JSONObject().apply {
            put("title",title); put("chapterUrl",chapterUrl); put("source",source.toJson()); put("pageCount",urls.size)
        }
        File(dir, "chapter.json").writeText(metadata.toString(2))
    }
}
