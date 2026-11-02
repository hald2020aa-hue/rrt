package com.rrt.neonreader

import android.net.Uri
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URI

/** Independent configurable HTML source adapter. It does not bypass site protections. */
data class SourceDef(
    val name: String,
    val baseUrl: String,
    val adult: Boolean = false,
    val listingPath: String = "/manga/",
    val searchPath: String = "/?s={query}&post_type=wp-manga",
    val itemSelector: String = ".page-item-detail, .c-tabs-item__content",
    val titleSelector: String = ".post-title a, h3 a",
    val linkSelector: String = ".post-title a, h3 a",
    val coverSelector: String = "img",
    val chapterSelector: String = ".wp-manga-chapter a",
    val pageSelector: String = ".reading-content img",
    val nextSelector: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name); put("baseUrl", baseUrl); put("adult", adult)
        put("listingPath", listingPath); put("searchPath", searchPath)
        put("itemSelector", itemSelector); put("titleSelector", titleSelector)
        put("linkSelector", linkSelector); put("coverSelector", coverSelector)
        put("chapterSelector", chapterSelector); put("pageSelector", pageSelector)
        put("nextSelector", nextSelector)
    }
    companion object {
        fun fromJson(j: JSONObject) = SourceDef(
            name = j.getString("name"), baseUrl = j.getString("baseUrl"),
            adult = j.optBoolean("adult", false),
            listingPath = j.optString("listingPath", "/manga/"),
            searchPath = j.optString("searchPath", "/?s={query}&post_type=wp-manga"),
            itemSelector = j.optString("itemSelector", ".page-item-detail, .c-tabs-item__content"),
            titleSelector = j.optString("titleSelector", ".post-title a, h3 a"),
            linkSelector = j.optString("linkSelector", ".post-title a, h3 a"),
            coverSelector = j.optString("coverSelector", "img"),
            chapterSelector = j.optString("chapterSelector", ".wp-manga-chapter a"),
            pageSelector = j.optString("pageSelector", ".reading-content img"),
            nextSelector = j.optString("nextSelector", "")
        )
    }
}

data class Manga(val title: String, val url: String, val cover: String, val sourceName: String) {
    fun toJson() = JSONObject().apply {
        put("title", title); put("url", url); put("cover", cover); put("sourceName", sourceName)
    }
    companion object {
        fun fromJson(j: JSONObject) = Manga(j.getString("title"), j.getString("url"), j.optString("cover"), j.getString("sourceName"))
    }
}
data class Chapter(val title: String, val url: String)

object SourceEngine {
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"

    fun normalizeBase(url: String): String {
        val u = URI(url.trim())
        require(u.scheme == "https" && !u.host.isNullOrBlank()) { "يجب أن يكون الرابط HTTPS صالحًا" }
        require(u.userInfo == null) { "روابط تحوي بيانات حساب غير مدعومة" }
        return "https://${u.rawAuthority}${u.rawPath.orEmpty().trimEnd('/')}"
    }
    fun absolute(base: String, relative: String): String {
        if (relative.isBlank()) return ""
        val value = relative.trim().removePrefix("\"").removeSuffix("\"")
        val result = URI(base).resolve(value).toString()
        return if (result.startsWith("https://")) result else ""
    }
    fun indexUrl(source: SourceDef, query: String = ""): String {
        val path = if (query.isBlank()) source.listingPath else source.searchPath.replace("{query}", Uri.encode(query))
        return absolute(source.baseUrl.trimEnd('/') + "/", path)
    }
    private fun fetchDocument(url: String, referer: String): org.jsoup.nodes.Document {
        if (!url.startsWith("https://")) error("هذا الإصدار يدعم روابط HTTPS فقط")
        val response = Jsoup.connect(url)
            .userAgent(USER_AGENT)
            .referrer(referer)
            .timeout(20000)
            .maxBodySize(0)
            .followRedirects(true)
            .execute()
        val status = response.statusCode()
        if (status == 403 || status == 429) error("الموقع قيّد الوصول (HTTP $status). لا يتم تجاوز الحماية")
        if (status >= 400) error("فشل HTTP: $status")
        return response.parse()
    }
    private fun getUrl(el: Element?, docBase: String): String {
        if (el == null) return ""
        val url = el.attr("href").ifBlank { el.selectFirst("a[href]")?.attr("href") ?: "" }
        return absolute(docBase, url)
    }
    private fun imageUrl(image: Element?, docBase: String): String {
        if (image == null) return ""
        val value = listOf("data-src", "data-lazy-src", "data-original", "data-url", "src", "data-srcset", "srcset")
            .firstNotNullOfOrNull { key -> image.attr(key).takeIf { it.isNotBlank() && !it.startsWith("data:") } } ?: return ""
        val first = value.split(',').first().trim().substringBefore(' ')
        return absolute(docBase, first)
    }
    fun catalog(source: SourceDef, query: String = ""): List<Manga> {
        val url = indexUrl(source, query)
        val doc = fetchDocument(url, source.baseUrl)
        val results = doc.select(source.itemSelector).mapNotNull { item ->
            val titleEl = item.selectFirst(source.titleSelector) ?: return@mapNotNull null
            val linkEl = item.selectFirst(source.linkSelector) ?: titleEl
            val link = getUrl(linkEl, doc.baseUri())
            if (link.isBlank()) return@mapNotNull null
            val title = titleEl.text().trim().ifBlank { titleEl.attr("title") }
            if (title.isBlank()) return@mapNotNull null
            Manga(title, link, imageUrl(item.selectFirst(source.coverSelector), doc.baseUri()), source.name)
        }.distinctBy { it.url }
        return results
    }
    fun chapters(source: SourceDef, mangaUrl: String): List<Chapter> {
        val doc = fetchDocument(mangaUrl, source.baseUrl)
        return doc.select(source.chapterSelector).mapNotNull { el ->
            val href = getUrl(el, doc.baseUri())
            if (href.isBlank()) null else Chapter(el.text().trim().ifBlank { "فصل" }, href)
        }.distinctBy { it.url }
    }
    fun pageUrls(source: SourceDef, chapterUrl: String): List<String> {
        val doc = fetchDocument(chapterUrl, source.baseUrl)
        return doc.select(source.pageSelector).mapNotNull { image ->
            imageUrl(image, doc.baseUri()).takeIf { it.isNotBlank() }
        }.distinct()
    }
    fun userAgent() = USER_AGENT
}
