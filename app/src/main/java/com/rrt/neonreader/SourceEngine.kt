package com.rrt.neonreader

import android.net.Uri
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI

/** Public-page HTML adapters. No challenge/CAPTCHA bypass or gated-content scraping. */
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
    val nextSelector: String = "",
    val enabled: Boolean = true,
    val template: String = "madara"
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("name",name); put("baseUrl",baseUrl); put("adult",adult); put("enabled", enabled)
        put("template",template); put("listingPath",listingPath); put("searchPath",searchPath)
        put("itemSelector",itemSelector); put("titleSelector",titleSelector); put("linkSelector",linkSelector)
        put("coverSelector",coverSelector); put("chapterSelector",chapterSelector)
        put("pageSelector",pageSelector); put("nextSelector",nextSelector)
    }
    companion object {
        fun fromJson(j: JSONObject): SourceDef = SourceDef(
            name=j.getString("name"),baseUrl=j.getString("baseUrl"), adult=j.optBoolean("adult"),
            listingPath=j.optString("listingPath", "/manga/"),
            searchPath=j.optString("searchPath", "/?s={query}&post_type=wp-manga"),
            itemSelector=j.optString("itemSelector", ".page-item-detail, .c-tabs-item__content"),
            titleSelector=j.optString("titleSelector", ".post-title a, h3 a"),
            linkSelector=j.optString("linkSelector", ".post-title a, h3 a"),
            coverSelector=j.optString("coverSelector", "img"),
            chapterSelector=j.optString("chapterSelector", ".wp-manga-chapter a"),
            pageSelector=j.optString("pageSelector", ".reading-content img"),
            nextSelector=j.optString("nextSelector", ""),
            enabled=j.optBoolean("enabled",true),template=j.optString("template","madara")
        )
    }
}

data class Manga(val title:String,val url:String,val cover:String,val sourceName:String) {
    fun toJson()=JSONObject().apply{put("title",title);put("url",url);put("cover",cover);put("sourceName",sourceName)}
    companion object { fun fromJson(j:JSONObject)=Manga(j.getString("title"),j.getString("url"),j.optString("cover"),j.getString("sourceName")) }
}
data class Chapter(val title:String,val url:String)
data class Detection(val source:SourceDef,val status:String,val count:Int,val requiresBrowser:Boolean=false)

object SourceEngine {
    private const val UA = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
    fun userAgent()=UA
    fun normalizeBase(value:String):String {
        val supplied=value.trim()
        val u=URI(if("://" in supplied) supplied else "https://$supplied")
        require(u.scheme.equals("https",true) && !u.host.isNullOrBlank()) { "استخدم عنوان HTTPS صحيحًا" }
        require(u.userInfo==null && u.port in listOf(-1,443)) { "عنوان غير مدعوم" }
        return "https://${u.host}${u.rawPath.orEmpty().trimEnd('/')}"
    }
    fun absolute(base:String,relative:String):String {
        val v=relative.trim().trim('"','\'')
        if(v.isBlank() || v.startsWith("data:") || v.startsWith("javascript:")) return ""
        return runCatching { URI(base).resolve(v).normalize().toString().takeIf { it.startsWith("https://") }.orEmpty() }.getOrDefault("")
    }
    fun indexUrl(s:SourceDef, query:String=""):String {
        val p=if(query.isBlank()) s.listingPath else s.searchPath.replace("{query}",Uri.encode(query))
        return absolute(s.baseUrl.trimEnd('/')+"/",p)
    }
    private fun fetch(url:String,ref:String):Document {
        require(url.startsWith("https://")) { "المصدر يجب أن يستخدم HTTPS" }
        val res=Jsoup.connect(url).userAgent(UA).referrer(ref).timeout(22000)
            .maxBodySize(6*1024*1024).followRedirects(true).ignoreHttpErrors(true).execute()
        val status=res.statusCode()
        if(status==403 || status==429 || status==503) error("HTTP $status — قد يتطلب الموقع تحققًا أمنيًا أو تسجيل دخول؛ لا يتم تجاوز الحماية")
        if(status>=400) error("HTTP $status — تعذر الوصول إلى الصفحة")
        val doc=res.parse()
        val title=doc.title().lowercase()
        val txt=doc.body()?.text().orEmpty().lowercase()
        if (title.contains("just a moment") || title.contains("attention required") ||
            (txt.contains("verify you are human") && txt.length<2500))
            error("الموقع يطلب تحققًا من المتصفح؛ افتحه خارجيًا وأضف محولًا رسميًا إن كان متاحًا")
        return doc
    }
    private fun url(el:Element?,base:String):String {
        if(el==null) return ""
        val link=el.attr("href").ifBlank{el.selectFirst("a[href]")?.attr("href").orEmpty()}
        return absolute(base,link)
    }
    private fun img(el:Element?,base:String):String {
        if(el==null) return ""
        val v=listOf("data-src","data-lazy-src","data-original","data-url","data-cfsrc","src","data-srcset","srcset")
            .firstNotNullOfOrNull{el.attr(it).takeIf{val1->val1.isNotBlank() && !val1.startsWith("data:")}}.orEmpty()
        val first=v.split(',').firstOrNull().orEmpty().trim().substringBefore(' ')
        return absolute(base,first)
    }
    fun template(base:String,name:String,kind:String="madara",adult:Boolean=false):SourceDef {
        val url=normalizeBase(base)
        return when(kind) {
            "madara" -> SourceDef(name,url,adult)
            "themesia" -> SourceDef(name,url,adult,"/manga/","/?s={query}",
                ".bs, .bsx, .listupd .bs", ".tt, .tt a, a[title]", "a[href]", "img", ".eph-num a, .clstyle a", ".readerarea img, #readerarea img", ".pagination a.next",template="themesia")
            "generic" -> SourceDef(name,url,adult,"/","/?s={query}",
                ".manga-item, article, .post, .item", "h2 a, h3 a, a[title]", "a[href]", "img", ".chapter-list a, .chapters a, li a", ".reading-content img, .reader-area img, #readerarea img",template="generic")
            else -> SourceDef(name,url,adult)
        }
    }
    /** Register a source without making a network request or applying content keyword filters.
     * Parsing capability and HTTP access are diagnosed separately after registration.
     */
    fun prepareSource(rawUrl:String,name:String="",adult:Boolean=false):SourceDef {
        val base=normalizeBase(rawUrl)
        val displayName=name.trim().ifBlank { URI(base).host.removePrefix("www.") }
        return template(base,displayName,"generic",adult)
    }

    fun detect(rawUrl:String,name:String,adult:Boolean=false):Detection {
        val base=normalizeBase(rawUrl)
        val doc=fetch(base,"https://${URI(base).host}/")
        val family=when {
            doc.select(".page-item-detail, .wp-manga-chapter, .c-tabs-item__content").isNotEmpty() -> "madara"
            doc.select(".bsx, .listupd .bs, .eph-num").isNotEmpty() -> "themesia"
            else -> "generic"
        }
        val detected=template(base,name,family,adult)
        val pages=listOf(detected.listingPath,"/").distinct()
        var count=0;var path=detected.listingPath
        for(p in pages) {
            val result=runCatching { fetch(absolute(base+"/",p),base) }.getOrNull() ?: continue
            val c=runCatching{result.select(detected.itemSelector).size}.getOrDefault(0)
            if(c>count){count=c;path=p}
        }
        val adjusted=detected.copy(listingPath=path)
        val message=if(count>0) "تم التعرف على $family واكتشاف $count بطاقة محتملة" else
            "الاتصال نجح، لكن لم تُكتشف بطاقات. يحتاج الموقع ضبط محددات CSS أو محول API."
        return Detection(adjusted,message,count)
    }
    fun catalog(source:SourceDef,query:String="",pageLimit:Int=1):List<Manga> {
        val results=linkedMapOf<String,Manga>()
        var address=indexUrl(source,query)
        val visited=mutableSetOf<String>()
        repeat(pageLimit.coerceIn(1,3)) {
            if(address.isBlank() || !visited.add(address)) return@repeat
            val doc=fetch(address,source.baseUrl)
            doc.select(source.itemSelector).forEach { item ->
                val titleEl=item.selectFirst(source.titleSelector) ?: return@forEach
                val linkEl=item.selectFirst(source.linkSelector) ?: titleEl
                val link=url(linkEl,doc.baseUri());if(link.isBlank()) return@forEach
                val title=titleEl.text().ifBlank{titleEl.attr("title")}.ifBlank{item.selectFirst("img[alt]")?.attr("alt").orEmpty()}.trim()
                if(title.isBlank()) return@forEach
                val cover=img(item.selectFirst(source.coverSelector),doc.baseUri())
                results[link]=Manga(title,link,cover,source.name)
            }
            address=if(source.nextSelector.isBlank()) "" else url(doc.selectFirst(source.nextSelector),doc.baseUri())
        }
        return results.values.toList()
    }
    fun chapters(source:SourceDef,mangaUrl:String):List<Chapter> {
        val doc=fetch(mangaUrl,source.baseUrl)
        return doc.select(source.chapterSelector).mapNotNull { e ->
            val link=url(e,doc.baseUri())
            if(link.isBlank()) null else Chapter(e.text().trim().ifBlank { "فصل" },link)
        }.distinctBy{it.url}
    }
    fun pageUrls(source:SourceDef,chapterUrl:String):List<String> {
        val doc=fetch(chapterUrl,source.baseUrl)
        return doc.select(source.pageSelector).mapNotNull{e->
            img(e,doc.baseUri()).takeIf{it.isNotBlank()}
        }.distinct()
    }
}
