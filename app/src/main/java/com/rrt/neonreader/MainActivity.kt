package com.rrt.neonreader

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import org.json.JSONObject

class MainActivity : Activity() {
    private val bg = Color.rgb(11, 16, 32)
    private val panel = Color.rgb(25, 31, 53)
    private val purple = Color.rgb(150, 105, 255)
    private val mint = Color.rgb(59, 241, 203)
    private val white = Color.rgb(240, 240, 253)
    private val muted = Color.rgb(157, 163, 188)
    private lateinit var store: SourceStore
    private lateinit var root: LinearLayout
    private var backHandler: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SourceStore(this)
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg) }
        setContentView(root)
        home()
    }
    @Deprecated("Deprecated in Android")
    override fun onBackPressed() { backHandler?.invoke() ?: super.onBackPressed() }

    private fun dp(i: Int) = (i * resources.displayMetrics.density).toInt()
    private fun padding(view: View, h: Int = 14, v: Int = 12) { view.setPadding(dp(h), dp(v), dp(h), dp(v)) }
    private fun shape(color: Int, border: Int? = null) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(15).toFloat(); if (border != null) setStroke(dp(1), border)
    }
    private fun text(label: String, size: Float = 16f, color: Int = white, bold: Boolean = false): TextView = TextView(this).apply {
        this.text = label; textSize = size; setTextColor(color)
        if (bold) setTypeface(null, Typeface.BOLD)
        gravity = Gravity.CENTER_VERTICAL
    }
    private fun add(parent: LinearLayout, child: View, space: Int = 8) {
        parent.addView(child, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(space) })
    }
    private fun button(label: String, color: Int = panel, click: () -> Unit): TextView = text(label, 15f, white, true).apply {
        gravity = Gravity.CENTER; background = shape(color, purple)
        padding(this, 13, 13); setOnClickListener { click() }
    }
    private fun card(parent: LinearLayout, title: String, summary: String, action: () -> Unit): LinearLayout {
        val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = shape(panel, Color.rgb(54,62,94)); padding(this, 16, 14) }
        add(row, text(title, 17f, white, true), 3)
        add(row, text(summary, 12f, muted), 0)
        add(parent, row, 10)
        row.setOnClickListener { action() }
        return row
    }
    private fun screen(title: String, subtitle: String, back: (() -> Unit)? = null): LinearLayout {
        backHandler = back
        root.removeAllViews()
        val bar = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; padding(this, 20, 22) }
        add(bar, text("✦  $title", 24f, mint, true), 3)
        add(bar, text(subtitle, 13f, muted), 0)
        root.addView(bar)
        val scroll = ScrollView(this).apply { fillViewport = true }
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; padding(this, 17, 8) }
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        if (back != null) {
            val b = button("‹ رجوع", bg) { back() }
            root.addView(b, LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(15), dp(3), dp(15), dp(12)) })
        }
        return body
    }
    private fun notify(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    private fun <T> background(work: () -> T, complete: (Result<T>) -> Unit) {
        Thread {
            val result = runCatching { work() }
            runOnUiThread { if (!isFinishing && !isDestroyed) complete(result) }
        }.start()
    }
    private fun error(t: Throwable): String = when (t) {
        is org.jsoup.HttpStatusException -> "خطأ HTTP ${t.statusCode}: قد يكون الموقع يمنع الروبوتات أو يحتاج تسجيل دخول"
        else -> t.message ?: "خطأ غير معروف"
    }
    private fun home() {
        val body = screen("RRT NEON READER", "قارئ مصادر مرن • مانجا ومانهوا • Kotlin")
        add(body, button("＋ إضافة مصدر جديد", purple) { editSource(null) })
        add(body, button("◈ استيراد مصادر JSON") { importJson() })
        add(body, button("⌕ بحث في جميع المصادر") { globalSearch() })
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val a = button("♡ مكتبتي") { library() }
        val b = button("↓ التنزيلات") { downloads() }
        val c = button("⚙ الإعدادات") { settings() }
        for (v in listOf(a,b,c)) nav.addView(v, LinearLayout.LayoutParams(0,-2,1f).apply { setMargins(dp(2),0,dp(2),dp(3)) })
        add(body, nav, 15)
        add(body, text("المصادر (${store.sources().size})", 19f, white, true), 13)
        val eligible = store.sources().filter { !it.adult || store.adultsEnabled }
        if (eligible.isEmpty()) add(body, text("لا توجد مصادر ظاهرة. أضف مصدرًا أو فعّل مصادر البالغين من الإعدادات.", 14f, muted))
        eligible.forEach { s ->
            val v = card(body, "${if (s.adult) "18+  " else "🌐  "}${s.name}", s.baseUrl) { catalog(s) }
            add(v, button("إدارة المصدر / تعديل / حذف", panel) { manageSource(s) }, 0)
        }
        add(body, text("لا توجد قائمة سوداء باسم الموقع. قبول المصدر لا يضمن توافق صفحاته مع CSS.", 12f, muted), 9)
    }
    private fun manageSource(source: SourceDef) {
        AlertDialog.Builder(this).setTitle(source.name)
            .setItems(arrayOf("تعديل المحددات", "اختبار فهرس المصدر", "حذف المصدر")) { _, index ->
                when (index) {
                    0 -> editSource(source)
                    1 -> background({ SourceEngine.catalog(source).size }) { res ->
                        res.onSuccess { notify("نجح التحليل، عدد العناصر: $it") }.onFailure { notify(error(it)) }
                    }
                    2 -> AlertDialog.Builder(this).setTitle("تأكيد حذف المصدر")
                        .setMessage("لن يتم حذف ملفات الفصول التي نزلتها")
                        .setNegativeButton("إلغاء", null)
                        .setPositiveButton("حذف") { _, _ ->
                            store.saveSources(store.sources().filterNot { it.name == source.name }); home()
                        }.show()
                }
            }.show()
    }
    private fun field(parent: LinearLayout, label: String, value: String): EditText {
        add(parent, text(label, 13f, mint), 3)
        val input = EditText(this).apply {
            setSingleLine(true); setText(value); textSize = 14f; setTextColor(white)
            setHintTextColor(muted); background = shape(panel, Color.rgb(54,62,94)); padding(this, 12, 12)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        add(parent, input, 10); return input
    }
    private fun editSource(old: SourceDef?) {
        val s = old ?: SourceDef("", "https://example.org")
        val scroll = ScrollView(this)
        val form = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; padding(this) }
        scroll.addView(form)
        val keys = listOf(
            "اسم المصدر" to s.name, "رابط الموقع HTTPS" to s.baseUrl,
            "مسار الفهرس" to s.listingPath, "مسار البحث {query}" to s.searchPath,
            "CSS بطاقات الأعمال" to s.itemSelector, "CSS عنوان العمل" to s.titleSelector,
            "CSS رابط العمل" to s.linkSelector, "CSS الغلاف" to s.coverSelector,
            "CSS روابط الفصول" to s.chapterSelector, "CSS صور الصفحات" to s.pageSelector,
            "CSS الصفحة التالية (اختياري)" to s.nextSelector
        )
        val edits = keys.map { field(form, it.first, it.second) }
        val adult = CheckBox(this).apply {
            text = "مصدر للبالغين 18+"; setTextColor(white); isChecked = s.adult
        }
        add(form, adult)
        AlertDialog.Builder(this).setTitle(if (old == null) "إضافة مصدر" else "تعديل المصدر")
            .setView(scroll).setNegativeButton("إلغاء", null)
            .setPositiveButton("حفظ") { _, _ ->
                runCatching {
                    val x = edits.map { it.text.toString().trim() }
                    require(x[0].isNotBlank()) { "اكتب اسم المصدر" }
                    SourceEngine.normalizeBase(x[1])
                    val obj = SourceDef(x[0], x[1].trimEnd('/'), adult.isChecked,
                        x[2],x[3],x[4],x[5],x[6],x[7],x[8],x[9],x[10])
                    val list = store.sources().apply { removeAll { it.name == old?.name }; add(obj) }
                    store.saveSources(list)
                }.onSuccess { notify("تم حفظ إعدادات المصدر"); home() }.onFailure { notify(error(it)) }
            }.show()
    }
    private fun importJson() {
        val e = EditText(this).apply {
            hint = "الصق JSON لمصدر واحد أو مصفوفة مصادر"; minLines = 6; maxLines = 12
            setTextColor(white); setHintTextColor(muted); padding(this)
        }
        AlertDialog.Builder(this).setTitle("استيراد مصدر بصيغة JSON").setView(e)
            .setNegativeButton("إلغاء", null).setPositiveButton("استيراد") { _, _ ->
                runCatching { store.importJson(e.text.toString()) }
                    .onSuccess { notify("تم استيراد $it مصدر"); home() }
                    .onFailure { notify(error(it)) }
            }.show()
    }
    private fun catalog(source: SourceDef, query: String = "") {
        val body = screen(source.name, "فهرس ونتائج البحث", ::home)
        val input = field(body, "بحث داخل المصدر", query)
        add(body, button("⌕ بحث", purple) { catalog(source, input.text.toString()) })
        val result = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        add(body, result)
        add(result, text("جارٍ تحميل الأعمال…", 14f, muted))
        background({ SourceEngine.catalog(source, query) }) { res ->
            result.removeAllViews()
            res.onSuccess { list ->
                add(result, text("النتائج: ${list.size}", 14f, mint), 10)
                if (list.isEmpty()) add(result, text("لا توجد نتائج. عدّل محددات CSS للمصدر أو مسار الفهرس.", 14f, muted))
                list.forEach { item -> mangaRow(result, item) { detail(source, item) } }
            }.onFailure { add(result, text("فشل التحميل: ${error(it)}", 14f, muted)) }
        }
    }
    private fun mangaRow(parent: LinearLayout, manga: Manga, onClick: () -> Unit) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; background = shape(panel); padding(this, 12, 9) }
        val cover = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.rgb(38,43,65))
        }
        row.addView(cover, LinearLayout.LayoutParams(dp(62),dp(88)).apply { rightMargin = dp(14) })
        if (manga.cover.isNotBlank()) Glide.with(this).load(GlideUrl(manga.cover, LazyHeaders.Builder().addHeader("Referer", manga.url).build())).into(cover)
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        add(column, text(manga.title, 16f, white, true), 6)
        add(column, text(manga.sourceName, 12f, muted), 0)
        row.addView(column, LinearLayout.LayoutParams(0,-2,1f))
        add(parent, row, 10); row.setOnClickListener { onClick() }
    }
    private fun detail(source: SourceDef, manga: Manga) {
        val body = screen(manga.title, "${source.name} • فصول العمل", { catalog(source) })
        val saved = store.favorites().any { it.url == manga.url }
        add(body, button(if (saved) "♥ إزالة من مكتبتي" else "♡ أضف إلى مكتبتي", purple) {
            val fav = store.favorites()
            if (fav.any { it.url == manga.url }) fav.removeAll { it.url == manga.url } else fav.add(manga)
            store.saveFavorites(fav); detail(source, manga)
        })
        val listing = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        add(body, listing); add(listing, text("جارٍ استخراج الفصول…",14f,muted))
        background({ SourceEngine.chapters(source, manga.url) }) { r ->
            listing.removeAllViews()
            r.onSuccess { chapters ->
                add(listing, text("عدد الفصول: ${chapters.size}", 14f, mint), 8)
                if (chapters.isEmpty()) add(listing, text("لا توجد فصول. تحقق من chapterSelector.",14f,muted))
                chapters.forEach { chapter -> card(listing, "◈ ${chapter.title}", chapter.url) {
                    openReader(source, chapter)
                } }
            }.onFailure { add(listing, text("تعذر جلب الفصول: ${error(it)}",14f,muted)) }
        }
    }
    private fun openReader(source: SourceDef, chapter: Chapter) {
        startActivity(Intent(this, ReaderActivity::class.java).apply {
            putExtra("source", source.toJson().toString())
            putExtra("chapterUrl", chapter.url)
            putExtra("chapterTitle", chapter.title)
        })
    }
    private fun library() {
        val body = screen("مكتبتي", "الأعمال المحفوظة", ::home)
        val all = store.favorites().filter { item -> store.sources().any { it.name == item.sourceName && (!it.adult || store.adultsEnabled) } }
        if (all.isEmpty()) add(body, text("مكتبتك فارغة. احفظ الأعمال من صفحات الفصول.", 14f, muted))
        all.forEach { manga -> mangaRow(body, manga) {
            store.sources().firstOrNull { it.name == manga.sourceName }?.let { detail(it, manga) }
        } }
    }
    private fun downloads() {
        val body = screen("التنزيلات", "الفصول المحفوظة للاستخدام دون اتصال", ::home)
        val files = DownloadUtil.list(this)
        if (files.isEmpty()) add(body, text("لا توجد فصول نُزّلت حتى الآن.",14f,muted))
        files.forEach { saved -> card(body, "↓ ${saved.title}", "الصفحات المحفوظة: ${saved.pageCount}") {
            startActivity(Intent(this, ReaderActivity::class.java).apply {
                putExtra("source", saved.source.toJson().toString())
                putExtra("chapterUrl", saved.chapterUrl)
                putExtra("chapterTitle", saved.title)
                putExtra("offline", true)
            })
        } }
    }
    private fun globalSearch() {
        val body = screen("بحث شامل", "يبحث في المصادر المضافة التي تسمح بذلك", ::home)
        val input = field(body, "عنوان المانجا أو المانهوا", "")
        val results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        add(body, button("ابدأ البحث", purple) {
            val query = input.text.toString().trim()
            if (query.isEmpty()) { notify("اكتب كلمة بحث"); return@button }
            results.removeAllViews(); add(results, text("جارٍ البحث في المصادر…",14f,muted))
            val sources = store.sources().filter { !it.adult || store.adultsEnabled }
            background({ sources.map { it to runCatching { SourceEngine.catalog(it, query) } } }) { outcome ->
                results.removeAllViews()
                outcome.onSuccess { groups ->
                    groups.forEach { (src, res) ->
                        add(results, text(src.name, 17f, mint, true))
                        res.onSuccess { manga -> manga.take(30).forEach { item -> mangaRow(results,item) { detail(src,item) } } }
                            .onFailure { add(results,text(error(it),12f,muted)) }
                    }
                }.onFailure { add(results, text(error(it),14f,muted)) }
            }
        })
        add(body, results)
    }
    private fun settings() {
        val body = screen("الإعدادات", "المصادر والخصوصية", ::home)
        val adult = CheckBox(this).apply { text = "إظهار مصادر البالغين 18+"; setTextColor(white); isChecked = store.adultsEnabled }
        add(body, adult)
        adult.setOnCheckedChangeListener { _, enabled -> store.adultsEnabled = enabled; notify(if (enabled) "تم تفعيل مصادر البالغين" else "تم إخفاء مصادر البالغين") }
        add(body, text("التفعيل اختياري. لا يتم فحص العمر تلقائيًا؛ هذا إعداد محتوى وليس وسيلة للتحقق من السن.",12f,muted))
        add(body, button("نسخ إعدادات المصادر JSON") {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("rrt-sources",store.exportJson()))
            notify("تم نسخ إعدادات المصادر")
        })
        add(body, button("استيراد إعدادات JSON") { importJson() })
        add(body, text("هذا التطبيق لا يتجاوز تسجيل الدخول أو CAPTCHA أو حظر المواقع. يدعم HTML ومحددات CSS القابلة للتعديل.", 13f, muted))
        add(body, text("RRT Neon Reader v0.1.0 • قارئ مستقل وليس نسخة مفكوكة من APK تجاري", 12f, muted))
    }
}
