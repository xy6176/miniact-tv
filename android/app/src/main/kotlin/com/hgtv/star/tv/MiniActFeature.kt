// SPDX-License-Identifier: GPL-3.0-only
package com.hgtv.star.tv

import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.*
import com.hgtv.star.R
import com.hgtv.star.core.Series
import com.hgtv.star.compat.lowEndTv

/** Missing landscape artwork changes the crop, never the image surface or layout. */
internal class MiniActFeature(
    context:Context,series:Series,loadCover:(ImageView,String)->Unit,
    open:()->Unit,watch:()->Unit,resume:Boolean
) : FrameLayout(context) {
    companion object {
        fun preferredHeight(context:Context)=context.designPx(if(context.resources.configuration.fontScale>1.2f) 450 else 340)
    }
    private fun dp(n:Number)=context.designPx(n)
    val primary=TvButton(context,if(series.upcoming) "剧集详情" else if(resume) "继续观看" else "开始观看",
        ButtonSize.NORMAL,ButtonTone.PRIMARY,action=if(series.upcoming) open else watch,pad=24,iconRes=R.drawable.ic_action_play)
    val secondary:TvButton?=if(series.upcoming) null else TvButton(context,"剧集详情",ButtonSize.NORMAL,ButtonTone.SECONDARY,
        action=open,pad=22,iconRes=R.drawable.ic_menu_info)
    private val info=LinearLayout(context).apply { orientation=LinearLayout.VERTICAL }
    private val actions=LinearLayout(context).apply {
        orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;clipChildren=false;clipToPadding=false
    }
    private val synopsis=context.tvText(series.description.replace(Regex("\\s+")," ").trim(),14f,MiniActTokens.secondary).apply {
        maxLines=1;ellipsize=TextUtils.TruncateAt.END;setLineSpacing(0f,1.2f)
    }
    private val artwork=FrameLayout(context)
    init {
        clipChildren=false;clipToPadding=false
        addView(artwork)
        fun source(url:String,ready:(Boolean)->Unit={}):HeroArtwork {
            val image=HeroArtwork(context,ready)
            artwork.addView(image,LayoutParams(-1,-1));loadCover(image,url)
            return image
        }
        val poster=source(series.cover)
        if(series.heroCover.isNotBlank() && series.heroCover!=series.cover) source(series.heroCover) { ready ->
            poster.visibility=if(ready) View.INVISIBLE else View.VISIBLE
        }
        val eyebrow=LinearLayout(context).apply { orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL }
        eyebrow.addView(View(context).apply { background=TvStyle.shape(context,MiniActTokens.accent,radius=1) },LinearLayout.LayoutParams(dp(3),dp(15)))
        eyebrow.addView(context.tvText("今日精选",14f,MiniActTokens.secondary).apply { setPadding(dp(12),0,0,0) })
        info.addView(eyebrow,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(14) })
        val split=series.title.indexOfFirst { it=='，' || it=='：' }.takeIf { it in 3 until series.title.lastIndex }
        val main=split?.let { series.title.take(it) } ?: series.title
        val subtitle=split?.let { series.title.substring(it+1).trim() }.orEmpty()
        info.addView(context.tvText(main,40f,MiniActTokens.text).apply {
            typeface=Typeface.create("sans-serif-medium",0);maxLines=2;ellipsize=TextUtils.TruncateAt.END;setLineSpacing(0f,1.06f)
        },LinearLayout.LayoutParams(-1,-2))
        if(subtitle.isNotEmpty()) info.addView(context.tvText(subtitle,22f,MiniActTokens.text).apply {
            typeface=Typeface.create("sans-serif-medium",0);maxLines=2;ellipsize=TextUtils.TruncateAt.END;setLineSpacing(0f,1.1f)
        },LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(7) })
        val tags=series.tags.split('·').map { it.trim() }.filter { it.isNotEmpty() }.take(3)
        val meta=(listOfNotNull(series.episodeCount.takeIf { it>0 }?.let { "$it 集" })+tags).joinToString("  ·  ")
        info.addView(context.tvText(meta,14f,MiniActTokens.secondary).apply { maxLines=1;ellipsize=TextUtils.TruncateAt.END },LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(16) })
        info.addView(synopsis,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(18) })
        actions.addView(primary,LinearLayout.LayoutParams(-2,-2))
        secondary?.let {
            actions.addView(it,LinearLayout.LayoutParams(-2,-2).apply { leftMargin=dp(12) })
            primary.nextFocusRightId=it.id;it.nextFocusLeftId=primary.id
        }
        addView(info);addView(actions)
    }
    override fun onMeasure(widthMeasureSpec:Int,heightMeasureSpec:Int) {
        val width=MeasureSpec.getSize(widthMeasureSpec)
        val height=resolveSize(preferredHeight(context),heightMeasureSpec)
        setMeasuredDimension(width,height)
        val contentWidth=(width*.56f).toInt().coerceAtLeast(dp(240)).coerceAtMost(width-dp(12))
        actions.measure(MeasureSpec.makeMeasureSpec(width-dp(12),MeasureSpec.AT_MOST),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED))
        val room=(height-dp(64)-actions.measuredHeight).coerceAtLeast(0)
        synopsis.visibility=if(synopsis.text.isBlank()) View.GONE else View.VISIBLE
        val exact=MeasureSpec.makeMeasureSpec(contentWidth,MeasureSpec.EXACTLY)
        info.measure(exact,MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED))
        if(info.measuredHeight>room && synopsis.visibility==View.VISIBLE) {
            synopsis.visibility=View.GONE;info.measure(exact,MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED))
        }
        artwork.measure(MeasureSpec.makeMeasureSpec(width,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(height,MeasureSpec.EXACTLY))
    }
    override fun onLayout(changed:Boolean,left:Int,top:Int,right:Int,bottom:Int) {
        artwork.layout(0,0,width,height)
        val actionTop=height-dp(26)-actions.measuredHeight
        val infoTop=dp(24)
        info.layout(dp(6),infoTop,dp(6)+info.measuredWidth,infoTop+info.measuredHeight)
        actions.layout(dp(6),actionTop,dp(6)+actions.measuredWidth,actionTop+actions.measuredHeight)
    }
    /** Masks are baked once into a bounded bitmap. Scrolling only draws that bitmap. */
    private class HeroArtwork(context:Context,private val ready:(Boolean)->Unit):ImageView(context) {
        private val maxWidth=if(context.lowEndTv()) 640 else 960
        private val brush=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private var cached:Bitmap?=null
        private var source:Bitmap?=null
        private var cachedWidth=0
        private var cachedHeight=0
        private val destination=Rect()
        init { tag="miniact:hero-artwork";importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO }
        override fun setImageBitmap(bitmap:Bitmap?) { super.setImageBitmap(bitmap);rebuild();invalidate() }
        override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int) { super.onSizeChanged(w,h,oldw,oldh);destination.set(0,0,w,h);rebuild() }
        private fun rebuild() {
            val bitmap=(drawable as? BitmapDrawable)?.bitmap
            if(bitmap==null || bitmap.isRecycled || width<=0 || height<=0) { cached=null;source=null;ready(false);return }
            if(source===bitmap && cachedWidth==width && cachedHeight==height && cached!=null)return
            source=bitmap;cachedWidth=width;cachedHeight=height
            val w=minOf(width,maxWidth);val h=(height.toLong()*w/width).toInt().coerceAtLeast(1)
            val render=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
            val canvas=Canvas(render)
            val wide=bitmap.width>bitmap.height*1.15f
            val area=RectF(if(wide) 0f else w*.32f,0f,w.toFloat(),h.toFloat())
            val scale=maxOf(area.width()/bitmap.width,area.height()/bitmap.height)
            val cropW=area.width()/scale;val cropH=area.height()/scale
            val x=((bitmap.width-cropW)*.5f).coerceAtLeast(0f)
            val y=(bitmap.height*(if(wide) .5f else .38f)-cropH*.5f).coerceIn(0f,(bitmap.height-cropH).coerceAtLeast(0f))
            val crop=Rect(x.toInt(),y.toInt(),(x+cropW).toInt().coerceAtMost(bitmap.width),(y+cropH).toInt().coerceAtMost(bitmap.height))
            brush.shader=null;brush.xfermode=null;brush.color=Color.WHITE
            canvas.drawBitmap(bitmap,crop,area,brush)
            brush.xfermode=PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            brush.shader=LinearGradient(0f,0f,w.toFloat(),0f,
                intArrayOf(Color.TRANSPARENT,0x10FFFFFF,0x52FFFFFF,0xEEFFFFFF.toInt(),Color.WHITE,Color.TRANSPARENT),
                floatArrayOf(0f,.3f,.5f,.76f,.98f,1f),Shader.TileMode.CLAMP)
            canvas.drawRect(0f,0f,w.toFloat(),h.toFloat(),brush)
            brush.shader=LinearGradient(0f,0f,0f,h.toFloat(),
                intArrayOf(Color.TRANSPARENT,Color.WHITE,Color.WHITE,Color.TRANSPARENT),
                floatArrayOf(0f,.12f,.66f,1f),Shader.TileMode.CLAMP)
            canvas.drawRect(0f,0f,w.toFloat(),h.toFloat(),brush)
            brush.shader=null;brush.xfermode=null;cached=render;ready(true)
        }
        override fun onDraw(canvas:Canvas) {
            if((drawable as? BitmapDrawable)?.bitmap!==source)rebuild()
            cached?.let { canvas.drawBitmap(it,null,destination,brush) }
        }
    }
}
