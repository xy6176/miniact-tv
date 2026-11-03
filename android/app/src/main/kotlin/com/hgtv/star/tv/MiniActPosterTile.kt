// SPDX-License-Identifier: GPL-3.0-only
package com.hgtv.star.tv

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.text.TextPaint
import android.text.TextUtils
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import com.hgtv.star.core.Series
import com.hgtv.star.core.SeriesSeasons

/** A fixed-size home tile has two views instead of a nested poster/labels/badges tree.
 * Artwork arrival only invalidates this canvas; bitmap intrinsic sizes never relayout a shelf.
 * Text shaping/ellipsis is cached when data or width changes, never during focus/scroll. */
internal class MiniActPosterTile(context:Context,private val tileWidth:Int,private val posterHeight:Int,private val loadCover:(ImageView,String)->Unit,private val landscape:Boolean=false,private val history:Boolean=false):FrameLayout(context) {
    private fun dp(n:Number)=context.designPx(n).toFloat()
    private val ink=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val titlePaint=TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize=context.designFontPx(16f); color=TvStyle.text; typeface=Typeface.create("sans-serif-medium",0)
    }
    private val metaPaint=TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize=context.designFontPx(12f); color=0xFFB4C0D2.toInt() }
    private val badgePaint=TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize=context.designFontPx(11f); color=Color.WHITE; typeface=Typeface.create("sans-serif-medium",0) }
    private val photo=RectF()
    private val ring=RectF()
    private val badge=RectF()
    private val matrix=Matrix()
    private var bitmap:Bitmap?=null
    private var texture:BitmapShader?=null
    private var item:Series?=null
    private var url=""
    private var focused=false
    private var progress=0
    private var lines=emptyList<String>()
    private var metadata=""
    private var statusText=""
    private var episode=""
    private var season=""
    var coverSuspended=false; private set
    val image=object:ImageView(context) {
        override fun setImageBitmap(next:Bitmap?) {
            bitmap=next
            texture=next?.takeUnless { it.isRecycled }?.let { BitmapShader(it,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP) }
            fitImage(); this@MiniActPosterTile.invalidate()
        }
        override fun setImageDrawable(drawable:Drawable?) {
            if(drawable==null) { bitmap=null; texture=null; this@MiniActPosterTile.invalidate() }
        }
        override fun onDraw(canvas:Canvas) {}
    }
    init {
        setWillNotDraw(false)
        setPadding(dp(6).toInt(),dp(6).toInt(),dp(6).toInt(),0)
        image.importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
        addView(image,LayoutParams(-1,posterHeight))
        // Allow binding/prefetch to decode before this fixed-size slot first enters the viewport.
        image.layout(dp(6).toInt(),dp(6).toInt(),tileWidth-dp(6).toInt(),posterHeight+dp(6).toInt())
    }
    fun bind(series:Series,status:String,unusedBadge:String,percent:Int?,unusedRank:Boolean) {
        item=series; progress=(percent ?: 0).coerceIn(0,100)
        episode=series.episodeCount.takeIf { it>0 }?.let { "$it 集" }.orEmpty()
        season=if(SeriesSeasons.season(series)>1) SeriesSeasons.badge(series) else ""
        statusText=status
        shapeText(); setCover(series.cover); invalidate()
    }
    fun setCover(next:String) {
        if(next==url && bitmap!=null && !coverSuspended) return
        url=next; coverSuspended=false; loadCover(image,next)
    }
    fun releaseCover() { url=""; coverSuspended=false; loadCover(image,"") }
    fun suspendCover():Boolean {
        if(url.isBlank() || coverSuspended || hasFocus()) return false
        coverSuspended=true; loadCover(image,""); return true
    }
    fun resumeCover():Boolean {
        if(!coverSuspended) return false
        coverSuspended=false; loadCover(image,url); return true
    }
    fun setFocused(value:Boolean) { if(focused!=value) { focused=value; invalidate() } }
    override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int) {
        super.onSizeChanged(w,h,oldw,oldh)
        photo.set(dp(6),dp(6),w-dp(6),posterHeight+dp(6))
        ring.set(photo); ring.inset(-dp(2),-dp(2))
        shapeText(); fitImage()
    }
    private fun shapeText() {
        val width=(width-dp(18)).coerceAtLeast(0f)
        if(width<=0f) return
        val title=item?.title.orEmpty()
        var end=titlePaint.breakText(title,true,width,null)
        if(end>0 && end<title.length && Character.isHighSurrogate(title[end-1])) end--
        // Respect a useful Latin word boundary while keeping Chinese full-width wrapping.
        val space=title.lastIndexOf(' ',(end-1).coerceAtLeast(0))
        if(space>0 && space>=end*.65f && end<title.length) end=space
        val first=title.take(end).trimEnd()
        val rest=title.drop(end).trimStart()
        lines=if(rest.isEmpty()) listOf(first) else listOf(first,TextUtils.ellipsize(rest,titlePaint,width,TextUtils.TruncateAt.END).toString())
        val label=if(history || landscape) statusText else item?.tags?.ifBlank { statusText }.orEmpty()
        metadata=TextUtils.ellipsize(label,metaPaint,width,TextUtils.TruncateAt.END).toString()
    }
    private fun fitImage() {
        val source=bitmap ?: return
        if(photo.width()<=0 || photo.height()<=0 || source.isRecycled) return
        val scale=if(landscape && source.width<source.height) minOf(photo.width()/source.width,photo.height()/source.height) else maxOf(photo.width()/source.width,photo.height()/source.height)
        matrix.setScale(scale,scale)
        matrix.postTranslate(photo.centerX()-source.width*scale/2,photo.centerY()-source.height*scale/2)
        texture?.setLocalMatrix(matrix)
    }
    override fun onDraw(canvas:Canvas) {
        super.onDraw(canvas)
        val radius=dp(14)
        ink.style=Paint.Style.FILL; ink.color=MiniActTokens.surface; ink.shader=null
        canvas.drawRoundRect(photo,radius,radius,ink)
        val source=bitmap
        if(landscape && source!=null && source.width<source.height && !source.isRecycled) {
            // Full portrait artwork in a wide slot: preserve the actual cover, with quiet
            // side space, rather than manufacturing a film still by cropping faces/text.
            val scale=minOf(photo.width()/source.width,photo.height()/source.height)
            val imageWidth=source.width*scale
            badge.set(photo.centerX()-imageWidth/2,photo.top,photo.centerX()+imageWidth/2,photo.bottom)
            canvas.drawBitmap(source,null,badge,ink)
        } else { ink.shader=texture; canvas.drawRoundRect(photo,radius,radius,ink); ink.shader=null }
        if(history || landscape || progress>0) {
            badge.set(photo.left+dp(8),photo.bottom-dp(4),photo.right-dp(8),photo.bottom-dp(1))
            ink.color=0x40FFFFFF; canvas.drawRoundRect(badge,dp(2),dp(2),ink)
            if(progress>0) {
                ink.color=MiniActTokens.accent
                badge.right=badge.left+(photo.width()-dp(16))*progress/100f
                canvas.drawRoundRect(badge,dp(2),dp(2),ink)
            }
        }
        if(!landscape && episode.isNotBlank()) drawBadge(canvas,episode,false)
        if(!landscape && season.isNotBlank()) drawBadge(canvas,season,true)
        if(focused) {
            ink.style=Paint.Style.STROKE; ink.strokeWidth=dp(2); ink.color=0xFFF2F6FF.toInt()
            canvas.drawRoundRect(ring,radius+dp(2),radius+dp(2),ink); ink.style=Paint.Style.FILL
        }
        val step=titlePaint.fontSpacing*1.1f
        val top=photo.bottom+dp(13)
        var baseline=top-titlePaint.ascent()
        titlePaint.color=if(focused) Color.WHITE else TvStyle.text
        lines.forEach { canvas.drawText(it,dp(8),baseline,titlePaint); baseline+=step }
        // Reserve two title lines so all metadata baselines remain aligned.
        val metaY=top+step*2+dp(5)-metaPaint.ascent()
        canvas.drawText(metadata,dp(8),metaY,metaPaint)
    }
    private fun drawBadge(canvas:Canvas,label:String,top:Boolean) {
        val w=badgePaint.measureText(label)+dp(14)
        val h=badgePaint.fontSpacing+dp(8)
        val right=photo.right-dp(8)
        val y=if(top) photo.top+dp(8) else photo.bottom-dp(8)-h
        badge.set(right-w,y,right,y+h)
        ink.color=0xC0121925.toInt(); canvas.drawRoundRect(badge,dp(6),dp(6),ink)
        canvas.drawText(label,badge.left+dp(7),badge.top+dp(4)-badgePaint.ascent(),badgePaint)
    }
}
