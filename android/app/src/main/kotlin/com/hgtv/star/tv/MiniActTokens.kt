// SPDX-License-Identifier: GPL-3.0-only
package com.hgtv.star.tv

import android.graphics.*
import android.graphics.drawable.Drawable
import android.view.View

/** Content stays quiet; glass belongs to navigation and transient task surfaces. */
internal object MiniActTokens {
    val background get()=MiniActThemes.current.middle
    val surface get()=if(MiniActThemes.current.id=="cinema") 0xFF171B22.toInt() else MiniActThemes.mix(MiniActThemes.current.glassShade,Color.WHITE,.025f)
    val text=0xFFF2F2F4.toInt()
    val secondary=0xFFB9BEC8.toInt()
    val muted=0xFF9EA5B1.toInt()
    val accent=0xFFE3CBA5.toInt()
    val ink=0xFF16191F.toInt()
    const val display=34f
    const val title=27f
    const val section=20f
    const val body=16f
    const val caption=13f
}

/** Plain content well, with an image-style focus edge. No artwork sampling or blur. */
internal class MiniActContentSurface(owner:View,radius:Int=16):Drawable() {
    private val unit=owner.context.designScale()
    private val radius=radius*unit
    private val area=RectF()
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private var focused=false
    fun focus(value:Boolean) { if(value!=focused) { focused=value; invalidateSelf() } }
    override fun onBoundsChange(bounds:Rect) {
        area.set(bounds.left+unit*1.5f,bounds.top+unit*1.5f,bounds.right-unit*1.5f,bounds.bottom-unit*1.5f)
    }
    override fun draw(canvas:Canvas) {
        paint.style=Paint.Style.FILL; paint.color=MiniActTokens.surface
        canvas.drawRoundRect(area,radius,radius,paint)
        if(focused) {
            paint.style=Paint.Style.STROKE; paint.strokeWidth=unit*2; paint.color=MiniActTokens.text
            canvas.drawRoundRect(area,radius,radius,paint); paint.style=Paint.Style.FILL
        }
    }
    override fun setAlpha(alpha:Int) {}
    override fun setColorFilter(filter:ColorFilter?) {}
    @Deprecated("Deprecated in Android") override fun getOpacity()=PixelFormat.TRANSLUCENT
}
