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
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders

/** Independently implemented visual reader: drawer, cover grid, detail, library and source manager. */
class MainActivity:Activity() {
    private val ink=Color.rgb(237,240,249)
    private val soft=Color.rgb(162,173,195)
    private val bg=Color.rgb(11,13,21)
    private val surface=Color.rgb(23,27,39)
    private val line=Color.rgb(46,52,69)
    private val neon=Color.rgb(117,126,255)
    private val aqua=Color.rgb(58,220,203)
    private lateinit var store:SourceStore
    private lateinit var frame:FrameLayout
    private lateinit var shell:LinearLayout
    private var backAction:(()->Unit)?=null
    private var drawer:View?=null

    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        store=SourceStore(this)
        frame=FrameLayout(this).apply{setBackgroundColor(bg)}
        shell=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        frame.addView(shell,FrameLayout.LayoutParams(-1,-1));setContentView(frame)
        showHome()
    }
    @Deprecated("Deprecated in Android")
    override fun onBackPressed() {
        if(drawer!=null){hideDrawer();return}
        backAction?.invoke() ?: super.onBackPressed()
    }
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
    private fun background(color:Int=surface,stroke:Int=line,corner:Int=14)=GradientDrawable().apply{
        setColor(color);cornerRadius=dp(corner).toFloat();setStroke(dp(1),stroke)
    }
    private fun txt(t:String,size:Float=15f,c:Int=ink,bold:Boolean=false)=TextView(this).apply{
        text=t;textSize=size;setTextColor(c);gravity=Gravity.CENTER_VERTICAL
        if(bold)setTypeface(null,Typeface.BOLD)
    }
    private fun pad(v:View,h:Int=12,vertical:Int=10){v.setPadding(dp(h),dp(vertical),dp(h),dp(vertical))}
    private fun add(parent:LinearLayout,view:View,bottom:Int=7) {
        parent.addView(view,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(bottom)})
    }
    private fun action(text:String,primary:Boolean=false,fn:()->Unit)=txt(text,14f,if(primary)Color.WHITE else ink,true).apply{
        gravity=Gravity.CENTER;pad(this,12,12)
        background=background(if(primary)neon else surface,if(primary)neon else line)
        setOnClickListener{fn()}
    }
    private fun titleSmall(s:String)=txt(s,18f,ink,true).apply{pad(this,0,7)}
    private fun textEntry(hint:String,initial:String="")=EditText(this).apply{
        setText(initial);this.hint=hint;setSingleLine(true);textSize=14f
        setTextColor(ink);setHintTextColor(soft);background=background();pad(this)
    }
    private fun toast(s:String){Toast.makeText(this,s,Toast.LENGTH_LONG).show()}
    private fun <T> async(work:()->T,done:(Result<T>)->Unit){
        Thread{val value=runCatching{work()};runOnUiThread{if(!isFinishing&&!isDestroyed)done(value)}}.start()
    }
    private fun failure(e:Throwable):String=e.message?:"حدث خطأ أثناء الطلب"
    private fun visibleSource(s:SourceDef)=s.enabled&&(!s.adult||store.adultsEnabled)
    private fun page(title:String,subtitle:String="",back:(()->Unit)?=null,showNav:Boolean=true):LinearLayout {
        hideDrawer();backAction=back;shell.removeAllViews()
        val bar=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;pad(this,10,12)}
        val menu=txt(if(back==null)"☰" else "‹",26f,aqua,true).apply{gravity=Gravity.CENTER;setOnClickListener{if(back!=null)back() else showDrawer()}}
        bar.addView(menu,LinearLayout.LayoutParams(dp(44),dp(44)))
        val headings=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        add(headings,txt(title,19f,ink,true),1);if(subtitle.isNotBlank())add(headings,txt(subtitle,11f,soft),0)
        bar.addView(headings,LinearLayout.LayoutParams(0,-2,1f))
        val search=txt("⌕",27f,ink).apply{gravity=Gravity.CENTER;setOnClickListener{globalSearch()}}
        bar.addView(search,LinearLayout.LayoutParams(dp(42),dp(44)))
        shell.addView(bar)
        val stroke=View(this).apply{setBackgroundColor(line)}
        shell.addView(stroke,LinearLayout.LayoutParams(-1,dp(1)))
        val scroll=ScrollView(this).apply{setFillViewport(true);isVerticalScrollBarEnabled=false}
        val body=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;pad(this,12,14)}
        scroll.addView(body)
        shell.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        if(showNav) navBar()
        return body
    }
    private fun navBar(){
        val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;background=background(bg,line,0);pad(this,4,7)}
        val entries=listOf("⌂\nالرئيسية" to {showHome()},"▦\nالمصادر" to {sources()},"♡\nالمكتبة" to {library()},"↓\nالتنزيلات" to {downloads()},"⚙\nالإعدادات" to {settings()})
        entries.forEach{(label,click)->
            val v=txt(label,12f,ink).apply{gravity=Gravity.CENTER;setOnClickListener{click()}}
            row.addView(v,LinearLayout.LayoutParams(0,dp(58),1f))
        }
        shell.addView(row)
    }
    private fun hideDrawer(){drawer?.let{frame.removeView(it)};drawer=null}
    private fun showDrawer(){
        hideDrawer()
        val layer=FrameLayout(this).apply{setBackgroundColor(0xB9000000.toInt());setOnClickListener{hideDrawer()}}
        val panel=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;background=background(surface,line,0);pad(this,17,22)
            setOnClickListener{} }
        add(panel,txt("RRT  ◈  NEON READER",21f,aqua,true),4)
        add(panel,txt("مكتبة مانجا ومانهوا • مصادر متعددة",12f,soft),20)
        val sections=listOf(
            "⌂  الرئيسية" to {showHome()},"▦  تصفح المصادر" to {sources()},"♡  المكتبة" to {library()},
            "◷  سجل القراءة" to {history()},"↓  التنزيلات" to {downloads()},"◉  الإحصائيات" to {statistics()},
            "⚙  الإعدادات" to {settings()},"＋  إضافة موقع" to {quickAdd()}
        )
        sections.forEach{(name,click)->add(panel,action(name){hideDrawer();click()},8)}
        add(panel,txt("المصادر المفعلة",12f,soft,true),6)
        store.visibleSources().take(12).forEach{s->add(panel,action("🌐  ${s.name}"){browse(s)},4)}
        val side=FrameLayout.LayoutParams(dp(if(resources.configuration.screenWidthDp>700)360 else 302),-1,Gravity.START)
        layer.addView(panel,side);frame.addView(layer,FrameLayout.LayoutParams(-1,-1));drawer=layer
    }
    private fun header(parent:LinearLayout,title:String,desc:String="") {
        add(parent,titleSmall(title),0)
        if(desc.isNotBlank())add(parent,txt(desc,12f,soft),12)
    }
    private fun infoCard(parent:LinearLayout,heading:String,description:String,onClick:()->Unit){
        val card=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;background=background();pad(this,15,15);setOnClickListener{onClick()}}
        add(card,txt(heading,16f,ink,true),4);add(card,txt(description,12f,soft),0);add(parent,card,10)
    }
    private fun showHome(){
        val body=page("RRT Neon Reader","قارئ متعدد المصادر • الوضع الليلي")
        val hero=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;background=background(Color.rgb(28,30,56),neon);pad(this,18,20)}
        add(hero,txt("مكتبتك في مكان واحد",23f,Color.WHITE,true),5)
        add(hero,txt("أضف مواقعك، تابع فصولك، واقرأ من دون اتصال.",13f,soft),14)
        add(hero,action("＋  إضافة موقع مانجا أو مانهوا",true){quickAdd()},4)
        add(body,hero,18)
        val quick=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        val actions=listOf("المصادر" to {sources()},"سجل القراءة" to {history()},"بحث شامل" to {globalSearch()})
        actions.forEach{(name,cb)->
            val v=action(name,false,cb)
            quick.addView(v,LinearLayout.LayoutParams(0,-2,1f).apply{setMargins(dp(2),0,dp(2),0)})
        }
        add(body,quick,16)
        header(body,"مؤخرًا","الأعمال التي فتحتها مؤخرًا")
        val recent=store.history().filter{m->store.visibleSources().any{it.name==m.sourceName}}.take(12)
        if(recent.isEmpty())add(body,txt("لم تبدأ القراءة بعد. افتح مصدرًا واختر عملًا.",13f,soft),15)
        else coverGrid(body,recent){m->openFavorite(m)}
        header(body,"المصادر المتاحة","اختر مصدرًا لعرض الأغلفة والفصول")
        val available=store.visibleSources()
        if(available.isEmpty())infoCard(body,"لا توجد مصادر مفعّلة","ابدأ بإضافة رابط موقع HTTPS، ثم اختبر استخراج العناوين والفصول."){quickAdd()}
        available.forEach{s->infoCard(body,"${if(s.adult)"18+  " else "🌐  "}${s.name}",s.baseUrl){browse(s)}}
    }
    private fun columns():Int=if(resources.configuration.screenWidthDp>=760) store.gridColumns+2 else store.gridColumns
    private fun coverGrid(parent:LinearLayout,entries:List<Manga>,clicked:(Manga)->Unit){
        val c=columns().coerceAtLeast(2)
        val grid=GridLayout(this).apply{columnCount=c;alignmentMode=GridLayout.ALIGN_BOUNDS;useDefaultMargins=false}
        val screenWidth=resources.displayMetrics.widthPixels - dp(24)
        val width=(screenWidth/c)-dp(7)
        entries.forEachIndexed{idx,manga->
            val item=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;pad(this,2,3);setOnClickListener{clicked(manga)}}
            val cover=ImageView(this).apply{scaleType=ImageView.ScaleType.CENTER_CROP;background=background()}
            item.addView(cover,LinearLayout.LayoutParams(-1,(width*1.36).toInt().coerceAtLeast(dp(120))))
            if(manga.cover.isNotBlank()) Glide.with(this).load(GlideUrl(manga.cover,LazyHeaders.Builder()
                .addHeader("User-Agent",SourceEngine.userAgent()).addHeader("Referer",manga.url).build())).centerCrop()
                .error(android.R.drawable.ic_menu_report_image).into(cover)
            val name=txt(manga.title,12f,ink,true).apply{maxLines=2;ellipsize=android.text.TextUtils.TruncateAt.END}
            item.addView(name,LinearLayout.LayoutParams(-1,dp(39)))
            val badge=txt(manga.sourceName,10f,soft).apply{maxLines=1}
            item.addView(badge)
            grid.addView(item,GridLayout.LayoutParams(GridLayout.spec(idx/c),GridLayout.spec(idx%c)).apply{this.width=width;setMargins(dp(3),dp(2),dp(3),dp(8))})
        }
        add(parent,grid,12)
    }
    private fun sources(){
        val body=page("المصادر","إضافة • تفعيل • اختبار • تعديل")
        add(body,action("＋ إضافة موقع جديد",true){quickAdd()},12)
        add(body,action("⇩ استيراد إعدادات JSON"){importJson()},12)
        add(body,txt("الحفظ متاح لكل موقع HTTPS عام، حتى مصادر 18+؛ نجاح الكشف التلقائي ليس شرطًا. تشغيل المصادر المصنّفة 18+ يحتاج تفعيلها من الإعدادات. لا يتم تجاوز CAPTCHA أو تسجيل الدخول.",12f,soft),12)
        store.sources().forEach{s->
            val panel=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;background=background();pad(this)}
            add(panel,txt("${if(s.adult)"18+  " else "◈  "}${s.name}${if(!s.enabled)"  · متوقف" else ""}",17f,ink,true),3)
            add(panel,txt("${s.baseUrl}  ·  ${s.template}",11f,soft),8)
            val group=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
            val acts=listOf("تصفح" to {browse(s)},"اختبار" to {testSource(s)},"تعديل" to {editSource(s)},"⋯" to {sourceMenu(s)})
            acts.forEach{(t,cb)->group.addView(action(t,false,cb),LinearLayout.LayoutParams(0,-2,1f).apply{setMargins(dp(2),0,dp(2),0)})}
            add(panel,group,0);add(body,panel,10)
        }
    }
    private fun quickAdd(){
        val form=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;pad(this,15,10)}
        val name=textEntry("اسم المصدر (اختياري)")
        val url=textEntry("https://example.com")
        val adult=CheckBox(this).apply{text="مصدر للبالغين 18+";setTextColor(ink)}
        val help=txt("تُحفظ المواقع حتى إن كان محتواها للبالغين أو فشل استخراج الفهرس. الكشف التلقائي اختياري لتحديد القالب فقط، وليس موافقة على الموقع. لا يمكن تجاوز حماية المواقع.",12f,soft)
        add(form,name);add(form,url);add(form,adult);add(form,help)
        fun prepare():SourceDef=SourceEngine.prepareSource(
            url.text.toString(),name.text.toString(),adult.isChecked
        )
        AlertDialog.Builder(this).setTitle("إضافة موقع جديد — بلا فلترة محتوى")
            .setView(form).setNegativeButton("إلغاء",null)
            .setNeutralButton("حفظ بدون فحص") { _,_->
                runCatching { prepare() }.onSuccess { candidate ->
                    saveSource(candidate)
                    sources()
                    toast("تمت إضافة ${candidate.name}. اختبر استخراج الفهرس من القائمة لاحقًا.")
                }.onFailure{toast(failure(it))}
            }
            .setPositiveButton("حفظ ثم كشف تلقائي") { _,_->
                runCatching { prepare() }.onSuccess { candidate ->
                    // Save FIRST. A network challenge, unsupported HTML or site classification
                    // must not undo this registration.
                    saveSource(candidate)
                    sources()
                    toast("تم حفظ الموقع. جارٍ الكشف الاختياري عن نوع الصفحات…")
                    async({SourceEngine.detect(candidate.baseUrl,candidate.name,candidate.adult)}) { outcome ->
                        outcome.onSuccess { result ->
                            val saved=store.sources().firstOrNull {
                                it.name==candidate.name && it.baseUrl==candidate.baseUrl
                            }
                            // Don't overwrite a source the user edited or deleted while detecting.
                            if(saved!=null && saved.template=="generic") {
                                saveSource(result.source.copy(enabled=saved.enabled))
                                sources()
                            }
                            toast("الموقع محفوظ. ${result.status}")
                        }.onFailure { problem ->
                            toast("الموقع محفوظ رغم تعذّر الكشف: ${failure(problem)}. يمكنك ضبط CSS من تعديل المصدر.")
                        }
                    }
                }.onFailure{toast(failure(it))}
            }.show()
    }
    private fun saveSource(source:SourceDef){
        val all=store.sources();all.removeAll{it.name==source.name};all.add(source);store.saveSources(all)
    }
    private fun sourceMenu(s:SourceDef){
        AlertDialog.Builder(this).setTitle(s.name)
            .setItems(arrayOf(if(s.enabled)"إيقاف المصدر" else "تفعيل المصدر","فتح الموقع في المتصفح","نسخ JSON","حذف المصدر")){_,id->
                when(id){
                    0->{saveSource(s.copy(enabled=!s.enabled));sources()}
                    1->runCatching {
                        startActivity(Intent(Intent.ACTION_VIEW,android.net.Uri.parse(s.baseUrl)))
                    }.onFailure { toast("تعذّر فتح المتصفح: ${failure(it)}") }
                    2->{val cb=getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cb.setPrimaryClip(ClipData.newPlainText(s.name,s.toJson().toString(2)));toast("تم النسخ")}
                    3->AlertDialog.Builder(this).setTitle("حذف المصدر؟")
                        .setNegativeButton("إلغاء",null).setPositiveButton("حذف"){_,_->store.saveSources(store.sources().filterNot{it.name==s.name});sources()}.show()
                }
            }.show()
    }
    private fun testSource(s:SourceDef){
        toast("جارٍ اختبار الفهرس")
        async({SourceEngine.catalog(s).size}){r->r.onSuccess{toast("عدد الأعمال المكتشفة: $it")}.onFailure{toast(failure(it))}}
    }
    private fun editSource(old:SourceDef){
        val base=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;pad(this)}
        val fields=listOf(
            "اسم المصدر" to old.name,"الرابط" to old.baseUrl,"مسار الفهرس" to old.listingPath,
            "مسار البحث {query}" to old.searchPath,"CSS بطاقة" to old.itemSelector,"CSS عنوان" to old.titleSelector,
            "CSS رابط" to old.linkSelector,"CSS غلاف" to old.coverSelector,"CSS الفصول" to old.chapterSelector,
            "CSS صور الصفحات" to old.pageSelector,"CSS الصفحة التالية" to old.nextSelector
        )
        val edits=fields.map{(label,value)->add(base,txt(label,12f,aqua),2);textEntry(label,value).also{add(base,it,9)}}
        val adult=CheckBox(this).apply{text="مصدر 18+";setTextColor(ink);isChecked=old.adult};add(base,adult)
        val scroll=ScrollView(this).apply{addView(base)}
        AlertDialog.Builder(this).setTitle("تعديل محددات المصدر").setView(scroll)
            .setNegativeButton("إلغاء",null).setPositiveButton("حفظ"){_,_->
                runCatching{
                    val t=edits.map{it.text.toString().trim()};require(t[0].isNotBlank()){"اكتب اسم المصدر"}
                    val changed=old.copy(name=t[0],baseUrl=SourceEngine.normalizeBase(t[1]),adult=adult.isChecked,
                        listingPath=t[2],searchPath=t[3],itemSelector=t[4],titleSelector=t[5],linkSelector=t[6],
                        coverSelector=t[7],chapterSelector=t[8],pageSelector=t[9],nextSelector=t[10])
                    val all=store.sources();all.removeAll{it.name==old.name};all.add(changed);store.saveSources(all)
                }.onSuccess{sources()}.onFailure{toast(failure(it))}
            }.show()
    }
    private fun importJson(){
        val input=EditText(this).apply{minLines=7;maxLines=14;hint="الصق إعدادات JSON";setTextColor(ink);setHintTextColor(soft);pad(this)}
        AlertDialog.Builder(this).setTitle("استيراد مصادر").setView(input).setNegativeButton("إلغاء",null)
            .setPositiveButton("استيراد"){_,_->runCatching{store.importJson(input.text.toString())}
                .onSuccess{toast("تم استيراد $it مصدر");sources()}.onFailure{toast(failure(it))}}.show()
    }
    private fun browse(s:SourceDef,query:String="",pages:Int=1){
        if(!visibleSource(s)){toast("فعّل المصدر أو خيار 18+ من الإعدادات");return}
        val body=page(s.name,"فهرس • بحث • شبكة أغلفة",::sources)
        val field=textEntry("ابحث عن عنوان…",query);add(body,field,6)
        add(body,action("⌕ ابحث في ${s.name}",true){browse(s,field.text.toString())},12)
        val result=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};add(body,result)
        add(result,txt("جارٍ تحميل الأعمال…",14f,soft))
        async({SourceEngine.catalog(s,query,pages)}){r->
            result.removeAllViews()
            r.onSuccess{list->
                add(result,txt("النتائج: ${list.size}",13f,aqua,true),10)
                if(list.isEmpty())add(result,txt("لم يتم اكتشاف أعمال؛ اختبر الموقع أو عدل المحددات في المصادر.",13f,soft))
                else coverGrid(result,list){detail(s,it)}
                if(s.nextSelector.isNotBlank() && pages<3)add(result,action("تحميل صفحات إضافية"){browse(s,query,pages+1)})
            }.onFailure{add(result,txt("تعذر فتح الفهرس: ${failure(it)}",14f,soft),12)
                add(result,action("ضبط محددات CSS") {editSource(s)})}
        }
    }
    private fun openFavorite(m:Manga){
        val source=store.visibleSources().firstOrNull{it.name==m.sourceName}
        if(source!=null)detail(source,m) else toast("المصدر غير متاح أو مخفي من الإعدادات")
    }
    private fun detail(source:SourceDef,m:Manga){
        store.addHistory(m)
        val body=page(m.title,"${source.name}  •  تفاصيل العمل",{browse(source)})
        val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        val cover=ImageView(this).apply{scaleType=ImageView.ScaleType.CENTER_CROP;background=background()}
        row.addView(cover,LinearLayout.LayoutParams(dp(115),dp(168)).apply{rightMargin=dp(12)})
        if(m.cover.isNotBlank())Glide.with(this).load(GlideUrl(m.cover,LazyHeaders.Builder().addHeader("Referer",m.url).build())).centerCrop().into(cover)
        val col=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        add(col,txt(m.title,19f,ink,true),8)
        add(col,txt("المصدر: ${source.name}",12f,soft),12)
        val saved=store.favorites().any{it.url==m.url}
        add(col,action(if(saved)"♥  إزالة من مكتبتي" else "♡  أضف إلى مكتبتي",true){
            val f=store.favorites();if(f.any{it.url==m.url})f.removeAll{it.url==m.url}else f.add(m)
            store.saveFavorites(f);detail(source,m)
        },7)
        row.addView(col,LinearLayout.LayoutParams(0,-2,1f));add(body,row,16)
        header(body,"الفصول","اختر الفصل للقراءة أو التنزيل")
        val listing=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};add(body,listing)
        add(listing,txt("جارٍ جلب الفصول…",13f,soft))
        async({SourceEngine.chapters(source,m.url)}){r->
            listing.removeAllViews()
            r.onSuccess{chapters->
                add(listing,txt("${chapters.size} فصل",13f,aqua,true),11)
                if(chapters.isEmpty())add(listing,txt("لا توجد فصول مطابقة؛ راجع محدد CSS للفصول.",13f,soft))
                chapters.forEachIndexed{index,ch->
                    infoCard(listing,"${index+1}  •  ${ch.title}","اضغط للقراءة") {openReader(source,ch,chapters)}
                }
            }.onFailure{add(listing,txt("تعذر جلب الفصول: ${failure(it)}",13f,soft))}
        }
    }
    private fun openReader(s:SourceDef,ch:Chapter,chapters:List<Chapter> = emptyList()){
        startActivity(Intent(this,ReaderActivity::class.java).apply{
            putExtra("source",s.toJson().toString());putExtra("chapterUrl",ch.url);putExtra("chapterTitle",ch.title)
            putStringArrayListExtra("chapterUrls",ArrayList(chapters.map{it.url}))
            putStringArrayListExtra("chapterTitles",ArrayList(chapters.map{it.title}))
        })
    }
    private fun library(){
        val body=page("المكتبة","أعمالك المحفوظة")
        val all=store.favorites().filter{m->store.visibleSources().any{it.name==m.sourceName}}
        if(all.isEmpty())infoCard(body,"مكتبتك فارغة","من صفحة العمل، اضغط أضف إلى مكتبتي."){sources()}
        else coverGrid(body,all){openFavorite(it)}
    }
    private fun history(){
        val body=page("سجل القراءة","الأعمال المفتوحة سابقًا")
        val all=store.history().filter{m->store.visibleSources().any{it.name==m.sourceName}}
        if(all.isEmpty())add(body,txt("لا يوجد سجل بعد.",14f,soft))else coverGrid(body,all){openFavorite(it)}
    }
    private fun downloads(){
        val body=page("التنزيلات","الفصول المحفوظة للقراءة دون إنترنت")
        val list=DownloadUtil.list(this).filter{!it.source.adult || store.adultsEnabled}
        if(list.isEmpty())add(body,txt("لا توجد تنزيلات محفوظة.",14f,soft))
        list.forEach{s->infoCard(body,"↓ ${s.title}","${s.pageCount} صفحة • ${s.source.name}"){
            startActivity(Intent(this,ReaderActivity::class.java).apply{
                putExtra("source",s.source.toJson().toString());putExtra("chapterUrl",s.chapterUrl)
                putExtra("chapterTitle",s.title);putExtra("offline",true)
            })
        }}
    }
    private fun globalSearch(){
        val body=page("البحث الشامل","بحث موحد في جميع المصادر النشطة",::showHome)
        val entry=textEntry("عنوان المانجا / المانهوا")
        add(body,entry,8)
        val result=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        add(body,action("⌕ ابحث",true){
            val query=entry.text.toString().trim();if(query.isBlank()){toast("اكتب كلمة بحث");return@action}
            result.removeAllViews();add(result,txt("جارٍ البحث…",13f,soft))
            val sources=store.visibleSources()
            async({sources.map { s->s to runCatching { SourceEngine.catalog(s,query) } }}){r->
                result.removeAllViews();r.onSuccess{groups->
                    groups.forEach{(s,out)->
                        add(result,titleSmall(s.name),4)
                        out.onSuccess{if(it.isEmpty())add(result,txt("لا توجد نتائج",12f,soft))
                            else coverGrid(result,it.take(25)){m->detail(s,m)}}
                            .onFailure{add(result,txt(failure(it),12f,soft))}
                    }
                }.onFailure{add(result,txt(failure(it),13f,soft))}
            }
        },12)
        add(body,result)
    }
    private fun statistics(){
        val body=page("الإحصائيات","ملخص محلي لنشاط القارئ")
        val values=listOf("المصادر المسجلة" to store.sources().size,"المصادر المفعلة" to store.visibleSources().size,
            "العناوين المحفوظة" to store.favorites().size,"الأعمال المفتوحة" to store.history().size,
            "الفصول المنزّلة" to DownloadUtil.list(this).size)
        values.forEach{(k,v)->infoCard(body,"$v",k){}}
    }
    private fun settings(){
        val body=page("الإعدادات","المظهر • عرض الأغلفة • القارئ • الخصوصية")
        val adult=CheckBox(this).apply{text="إظهار مصادر للبالغين 18+";isChecked=store.adultsEnabled;setTextColor(ink)}
        add(body,adult);adult.setOnCheckedChangeListener{_,v->store.adultsEnabled=v;toast("تم تحديث إعداد المحتوى")}
        add(body,txt("الإعداد اختياري ومغلق افتراضيًا؛ لا يمثل نظام تحقق من العمر.",12f,soft),17)
        header(body,"عرض الأغلفة")
        add(body,action("عدد الأعمدة: ${store.gridColumns}  (+)"){
            store.gridColumns=if(store.gridColumns>=5)2 else store.gridColumns+1
            settings()
        },13)
        header(body,"وضع القارئ")
        val horizontal=CheckBox(this).apply{text="تقليب الصفحات أفقيًا";isChecked=store.horizontalReader;setTextColor(ink)}
        add(body,horizontal);horizontal.setOnCheckedChangeListener{_,v->store.horizontalReader=v}
        val rtl=CheckBox(this).apply{text="اتجاه أفقي من اليمين إلى اليسار";isChecked=store.rightToLeft;setTextColor(ink)}
        add(body,rtl);rtl.setOnCheckedChangeListener{_,v->store.rightToLeft=v}
        header(body,"النسخ الاحتياطي للمصادر")
        add(body,action("نسخ إعدادات المصادر JSON"){
            val clipboard=getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("rrt-sources",store.exportJson()));toast("تم نسخ إعدادات المصادر")
        })
        add(body,action("استيراد إعدادات المصادر"){importJson()},15)
        add(body,txt("المواقع التي تستخدم JavaScript أو تتطلب تسجيل دخول أو تحققًا أمنيًا قد تحتاج محولًا متوافقًا. التطبيق لا يتجاوز وسائل الحماية.",12f,soft),8)
        add(body,txt("RRT Neon Reader 0.2.0 • مشروع Kotlin مستقل مبني على المراجعة الوظيفية، وليس نسخة مفكوكة من التطبيق الأصلي.",11f,soft),8)
    }
}
