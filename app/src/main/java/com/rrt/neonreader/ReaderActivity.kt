package com.rrt.neonreader

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import org.json.JSONObject
import java.io.File

class ReaderActivity : Activity() {
    private lateinit var source: SourceDef
    private lateinit var chapterUrl: String
    private lateinit var chapterTitle: String
    private lateinit var content: LinearLayout
    private lateinit var recycler: RecyclerView
    private lateinit var status: TextView
    private lateinit var downloadButton: TextView
    private var pageUrls: List<String> = emptyList()
    private var onlyOffline = false
    private val bg = Color.rgb(11, 16, 32)
    private val ink = Color.rgb(240,240,253)
    private val mint = Color.rgb(59,241,203)

    private fun dp(x: Int) = (x * resources.displayMetrics.density).toInt()
    private fun label(value: String, size: Float = 14f, color: Int = ink): TextView = TextView(this).apply {
        text = value; textSize = size; setTextColor(color); setPadding(dp(12),dp(11),dp(12),dp(11))
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            source = SourceDef.fromJson(JSONObject(intent.getStringExtra("source") ?: error("مصدر مفقود")))
            chapterUrl = intent.getStringExtra("chapterUrl") ?: error("رابط فصل مفقود")
            chapterTitle = intent.getStringExtra("chapterTitle") ?: "فصل"
            onlyOffline = intent.getBooleanExtra("offline", false)
        } catch (e: Exception) { Toast.makeText(this,e.message,Toast.LENGTH_LONG).show(); finish(); return }
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg) }
        setContentView(content)
        val title = label("‹  $chapterTitle", 18f, mint).apply { setTypeface(null, Typeface.BOLD); setOnClickListener { finish() } }
        content.addView(title)
        status = label("جارٍ تحميل الصفحات…")
        content.addView(status)
        downloadButton = label("↓ تنزيل الفصل للقراءة بدون إنترنت",16f,mint)
        downloadButton.gravity = Gravity.CENTER
        downloadButton.setOnClickListener { startDownload() }
        content.addView(downloadButton)
        recycler = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@ReaderActivity)
            setBackgroundColor(Color.BLACK)
        }
        content.addView(recycler, LinearLayout.LayoutParams(-1,0,1f))
        if (onlyOffline) {
            displayLocal()
        } else {
            Thread {
                val res = runCatching { SourceEngine.pageUrls(source, chapterUrl) }
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    res.onSuccess { list ->
                        pageUrls = list
                        val local = DownloadUtil.offlinePages(this, chapterUrl)
                        status.text = "الصفحات: ${list.size}" + if (local.isNotEmpty()) " • المحفوظة ${local.size}" else ""
                        if (list.isEmpty()) status.text = "لم تُكتشف صور. راجع CSS الخاص بصفحات الفصل."
                        recycler.adapter = PageAdapter(list, chapterUrl)
                    }.onFailure { e ->
                        val local = DownloadUtil.offlinePages(this,chapterUrl)
                        if (local.isNotEmpty()) displayLocal()
                        else status.text = "تعذر التحميل: ${e.message ?: "خطأ شبكة"}"
                    }
                }
            }.start()
        }
    }
    private fun displayLocal() {
        val files = DownloadUtil.offlinePages(this,chapterUrl)
        status.text = "القراءة دون إنترنت • ${files.size} صفحة"
        downloadButton.visibility = View.GONE
        recycler.adapter = PageAdapter(files.map { it.absolutePath }, chapterUrl, true)
    }
    private fun startDownload() {
        if (pageUrls.isEmpty()) { Toast.makeText(this,"لا توجد صفحات للتنزيل",Toast.LENGTH_SHORT).show(); return }
        downloadButton.isEnabled = false
        downloadButton.text = "جارٍ التنزيل…"
        Thread {
            val result = runCatching {
                DownloadUtil.download(applicationContext,source,chapterTitle,chapterUrl,pageUrls) { count,total ->
                    runOnUiThread { if (!isFinishing) downloadButton.text = "تنزيل $count / $total" }
                }
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                downloadButton.isEnabled = true
                result.onSuccess {
                    downloadButton.text = "✓ تم حفظ الفصل محليًا"
                    Toast.makeText(this,"اكتمل تنزيل الفصل",Toast.LENGTH_LONG).show()
                }.onFailure { e ->
                    downloadButton.text = "↓ إعادة محاولة التنزيل"
                    Toast.makeText(this,"فشل التنزيل: ${e.message}",Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }
    inner class PageAdapter(private val items: List<String>, private val referer: String, private val localOnly: Boolean = false) : RecyclerView.Adapter<PageAdapter.Holder>() {
        inner class Holder(val column: LinearLayout, val image: ImageView) : RecyclerView.ViewHolder(column)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val box = LinearLayout(parent.context).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg) }
            val image = ImageView(parent.context).apply {
                adjustViewBounds = true; scaleType = ImageView.ScaleType.FIT_CENTER
                minimumHeight = dp(180)
            }
            box.addView(image, LinearLayout.LayoutParams(-1,-2))
            return Holder(box,image)
        }
        override fun getItemCount() = items.size
        override fun onBindViewHolder(holder: Holder, position: Int) {
            val local = if (localOnly) File(items[position]) else DownloadUtil.pageFile(this@ReaderActivity,chapterUrl,position)
            val model: Any = if (local.isFile && local.length()>0) local else
                GlideUrl(items[position],LazyHeaders.Builder().addHeader("Referer",referer)
                    .addHeader("User-Agent",SourceEngine.userAgent()).build())
            Glide.with(holder.image).load(model).diskCacheStrategy(DiskCacheStrategy.DATA)
                .error(android.R.drawable.ic_delete).into(holder.image)
        }
        override fun onViewRecycled(holder: Holder) {
            Glide.with(holder.image).clear(holder.image)
            super.onViewRecycled(holder)
        }
    }
}
