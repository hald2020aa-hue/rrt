package com.rrt.neonreader

import android.app.Activity
import android.content.Intent
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
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import org.json.JSONObject
import java.io.File

/** Vertical/webtoon or horizontal-page reader with saved page position and offline downloads. */
class ReaderActivity:Activity() {
    private val bg=Color.rgb(8,9,14)
    private val pane=Color.rgb(21,24,37)
    private val ink=Color.rgb(238,241,251)
    private val accent=Color.rgb(114,128,255)
    private lateinit var store:SourceStore
    private lateinit var source:SourceDef
    private lateinit var chapterUrl:String
    private lateinit var chapterTitle:String
    private lateinit var frame:LinearLayout
    private lateinit var recycler:RecyclerView
    private lateinit var head:LinearLayout
    private lateinit var bottom:LinearLayout
    private lateinit var label:TextView
    private lateinit var progress:ProgressBar
    private lateinit var download:TextView
    private var urls:List<String> = emptyList()
    private var offline=false
    private var menuVisible=true
    private var chapterUrls:ArrayList<String> = arrayListOf()
    private var chapterTitles:ArrayList<String> = arrayListOf()
    private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
    private fun text(v:String,size:Float=14f,tint:Int=ink)=TextView(this).apply {
        this.text=v;textSize=size;setTextColor(tint);setPadding(dp(10),dp(10),dp(10),dp(10));gravity=Gravity.CENTER_VERTICAL
    }
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        store=SourceStore(this)
        try {
            source=SourceDef.fromJson(JSONObject(intent.getStringExtra("source")?:error("مصدر مفقود")))
            chapterUrl=intent.getStringExtra("chapterUrl")?:error("رابط فصل مفقود")
            chapterTitle=intent.getStringExtra("chapterTitle")?:"الفصل"
            offline=intent.getBooleanExtra("offline",false)
            chapterUrls=intent.getStringArrayListExtra("chapterUrls")?: arrayListOf()
            chapterTitles=intent.getStringArrayListExtra("chapterTitles")?: arrayListOf()
        } catch(e:Exception){Toast.makeText(this,e.message,Toast.LENGTH_LONG).show();finish();return}
        frame=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setBackgroundColor(bg)}
        setContentView(frame)
        head=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;setBackgroundColor(pane);gravity=Gravity.CENTER_VERTICAL}
        val close=text("‹",25f,accent).apply{setOnClickListener{finish()}}
        head.addView(close)
        val title=text(chapterTitle,15f,ink).apply{setTypeface(null,Typeface.BOLD);maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END}
        head.addView(title,LinearLayout.LayoutParams(0,-2,1f))
        val mode=text(if(store.horizontalReader)"▤" else "▥",22f,accent).apply{
            setOnClickListener{store.horizontalReader=!store.horizontalReader; recreate()}
        }
        head.addView(mode)
        frame.addView(head)
        recycler=RecyclerView(this).apply{setBackgroundColor(Color.BLACK);itemAnimator=null}
        val horizontal=store.horizontalReader
        recycler.layoutManager=LinearLayoutManager(this,
            if(horizontal)RecyclerView.HORIZONTAL else RecyclerView.VERTICAL,horizontal && store.rightToLeft)
        if(horizontal)PagerSnapHelper().attachToRecyclerView(recycler)
        frame.addView(recycler,LinearLayout.LayoutParams(-1,0,1f))
        bottom=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(pane)}
        label=text("جارٍ تحميل الفصل…",12f,ink).apply{gravity=Gravity.CENTER}
        bottom.addView(label,LinearLayout.LayoutParams(-1,dp(36)))
        progress=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply{
            progressTintList=android.content.res.ColorStateList.valueOf(accent)
            max=100;this.progress=0
        }
        bottom.addView(progress,LinearLayout.LayoutParams(-1,dp(3)))
        val controls=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER}
        val prev=text("‹ السابق",13f,ink).apply{gravity=Gravity.CENTER;setOnClickListener{goPage(-1)}}
        controls.addView(prev,LinearLayout.LayoutParams(0,dp(49),1f))
        download=text("↓ تنزيل الفصل",13f,accent).apply{gravity=Gravity.CENTER;setOnClickListener{downloadChapter()}}
        controls.addView(download,LinearLayout.LayoutParams(0,dp(49),1.6f))
        val next=text("التالي ›",13f,ink).apply{gravity=Gravity.CENTER;setOnClickListener{goPage(1)}}
        controls.addView(next,LinearLayout.LayoutParams(0,dp(49),1f))
        bottom.addView(controls)
        frame.addView(bottom)
        recycler.addOnScrollListener(object:RecyclerView.OnScrollListener(){
            override fun onScrolled(rv:RecyclerView,dx:Int,dy:Int){updatePosition()}
        })
        if(offline)showOffline() else asyncPages()
    }
    private fun asyncPages(){
        Thread{
            val result=runCatching{SourceEngine.pageUrls(source,chapterUrl)}
            runOnUiThread{
                if(isFinishing || isDestroyed)return@runOnUiThread
                result.onSuccess{list->
                    urls=list
                    if(list.isEmpty()){
                        val files=DownloadUtil.offlinePages(this,chapterUrl)
                        if(files.isNotEmpty()){showOffline();return@onSuccess}
                        label.text="لم تُكتشف صور؛ راجع محدد CSS للصفحات"
                        return@onSuccess
                    }
                    bindPages(list,false)
                }.onFailure{e->
                    val local=DownloadUtil.offlinePages(this,chapterUrl)
                    if(local.isNotEmpty())showOffline() else label.text="تعذّر تحميل الفصل: ${e.message}"
                }
            }
        }.start()
    }
    private fun showOffline(){
        val files=DownloadUtil.offlinePages(this,chapterUrl)
        urls=emptyList()
        download.visibility=View.GONE
        bindPages(files.map{it.absolutePath},true)
    }
    private fun bindPages(entries:List<String>,onlyLocal:Boolean){
        recycler.adapter=ImageAdapter(entries,onlyLocal)
        val remember=store.progress(chapterUrl).coerceIn(0,(entries.size-1).coerceAtLeast(0))
        recycler.scrollToPosition(remember)
        updatePosition()
        if(onlyLocal)download.visibility=View.GONE
    }
    private fun updatePosition(){
        val layout=recycler.layoutManager as? LinearLayoutManager ?:return
        val total=recycler.adapter?.itemCount?:return
        if(total==0)return
        val page=layout.findFirstVisibleItemPosition().coerceAtLeast(0)
        store.setProgress(chapterUrl,page)
        label.text="${if(offline || urls.isEmpty())"بدون اتصال • " else ""}$chapterTitle — الصفحة ${page+1} / $total"
        progress.progress=((page+1)*100/total).coerceIn(0,100)
    }
    private fun goPage(dir:Int){
        val layout=recycler.layoutManager as? LinearLayoutManager ?:return
        val max=(recycler.adapter?.itemCount?:0)-1
        val current=layout.findFirstVisibleItemPosition().coerceAtLeast(0)
        val target=current+dir
        if(target in 0..max){recycler.smoothScrollToPosition(target);return}
        if(dir>0 && current>=max && max>=0)moveChapter(1)
        else if(dir<0 && current<=0)moveChapter(-1)
    }
    private fun moveChapter(direction:Int){
        val idx=chapterUrls.indexOf(chapterUrl)
        val new=idx+direction
        if(idx<0 || new !in chapterUrls.indices){
            Toast.makeText(this,"لا يوجد فصل مجاور في القائمة",Toast.LENGTH_SHORT).show();return
        }
        val intent=Intent(this,ReaderActivity::class.java).apply{
            putExtra("source",source.toJson().toString())
            putExtra("chapterUrl",chapterUrls[new]);putExtra("chapterTitle",chapterTitles.getOrElse(new){"فصل"})
            putStringArrayListExtra("chapterUrls",chapterUrls)
            putStringArrayListExtra("chapterTitles",chapterTitles)
        }
        startActivity(intent);finish()
    }
    private fun toggleBars(){
        menuVisible=!menuVisible
        head.visibility=if(menuVisible)View.VISIBLE else View.GONE
        bottom.visibility=if(menuVisible)View.VISIBLE else View.GONE
    }
    private fun downloadChapter(){
        if(urls.isEmpty()){Toast.makeText(this,"لا توجد صفحات متاحة للتنزيل",Toast.LENGTH_SHORT).show();return}
        download.isEnabled=false;download.text="جاري التنزيل…"
        Thread{
            val result=runCatching{DownloadUtil.download(applicationContext,source,chapterTitle,chapterUrl,urls){index,total->
                runOnUiThread {if(!isFinishing)download.text="↓ $index / $total"}
            }}
            runOnUiThread{
                if(isFinishing || isDestroyed)return@runOnUiThread
                download.isEnabled=true
                result.onSuccess{download.text="✓ تم حفظ الفصل"}
                    .onFailure{e->download.text="↓ إعادة المحاولة";Toast.makeText(this,e.message,Toast.LENGTH_LONG).show()}
            }
        }.start()
    }
    inner class ImageAdapter(private val items:List<String>,private val localOnly:Boolean):RecyclerView.Adapter<ImageAdapter.Holder>(){
        inner class Holder(val picture:ImageView):RecyclerView.ViewHolder(picture)
        override fun onCreateViewHolder(parent:ViewGroup,type:Int):Holder{
            val view=ImageView(parent.context).apply{
                adjustViewBounds=!store.horizontalReader
                scaleType=ImageView.ScaleType.FIT_CENTER
                setBackgroundColor(Color.BLACK)
                minimumHeight=dp(230)
                setOnClickListener{toggleBars()}
            }
            val lp=if(store.horizontalReader)RecyclerView.LayoutParams(recycler.width.coerceAtLeast(dp(270)), -1)
                else RecyclerView.LayoutParams(-1,-2)
            view.layoutParams=lp
            return Holder(view)
        }
        override fun getItemCount()=items.size
        override fun onBindViewHolder(holder:Holder,pos:Int){
            val pageFile=if(localOnly)File(items[pos]) else DownloadUtil.pageFile(this@ReaderActivity,chapterUrl,pos)
            val image:Any=if(pageFile.isFile&&pageFile.length()>0)pageFile else
                GlideUrl(items[pos],LazyHeaders.Builder().addHeader("Referer",chapterUrl)
                    .addHeader("User-Agent",SourceEngine.userAgent()).build())
            Glide.with(holder.picture).load(image).diskCacheStrategy(DiskCacheStrategy.DATA)
                .error(android.R.drawable.ic_menu_report_image).into(holder.picture)
        }
        override fun onViewRecycled(h:Holder){Glide.with(h.picture).clear(h.picture);super.onViewRecycled(h)}
    }
}
