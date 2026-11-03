// SPDX-License-Identifier: GPL-3.0-only
package com.hgtv.star.tv

import android.graphics.*
import android.graphics.drawable.Drawable
import android.view.View

/** Small, translucent controls sit above the shared blurred scene. Their state changes
 * only repaint this surface: no backdrop capture, layout animation, or shadow bitmap. */
internal class MiniActControlSurface(
    owner: View,
    private val field: Boolean = false,
    private val radius: Int = 16,
    private val band: Int = 3,
    private val rail: Boolean = false,
    private val episode: Boolean = false
) : Drawable() {
    private val unit=owner.context.designScale()
    private val area=RectF()
    private val focusRing=RectF()
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val marker=Path()
    private val contact=RectF()
    private var fill: Shader?=null
    private var edge: Shader?=null
    private var focused=false
    private var primary=false
    private var ghost=false
    private var selected=false
    private var enabled=true
    private var pressed=false

    fun appearance(focused:Boolean,primary:Boolean=false,ghost:Boolean=false,selected:Boolean=false,enabled:Boolean=true) {
        if(this.focused==focused && this.primary==primary && this.ghost==ghost && this.selected==selected && this.enabled==enabled && fill!=null) return
        this.focused=focused; this.primary=primary; this.ghost=ghost; this.selected=selected; this.enabled=enabled
        rebuild(); invalidateSelf()
    }
    override fun isStateful()=true
    override fun onStateChange(state:IntArray):Boolean {
        val next=state.contains(android.R.attr.state_pressed)
        if(next==pressed) return false
        pressed=next; rebuild(); invalidateSelf(); return true
    }
    override fun onBoundsChange(bounds:Rect) {
        val inset=unit*(band+.5f)
        area.set(bounds.left+inset,bounds.top+inset,bounds.right-inset,bounds.bottom-inset)
        focusRing.set(area); focusRing.inset(-unit*2,-unit*2)
        contact.set(area); contact.offset(0f,unit*1.5f)
        marker.reset()
        marker.moveTo(area.centerX()-unit*2,area.bottom-unit*9)
        marker.lineTo(area.centerX()+unit*3,area.bottom-unit*6)
        marker.lineTo(area.centerX()-unit*2,area.bottom-unit*3)
        marker.close()
        rebuild()
    }
    private fun rebuild() {
        if(area.width()<=0 || area.height()<=0) return
        // White means the remote is here. Importance must never impersonate focus.
        val bright=enabled && !field && focused
        val theme=MiniActThemes.current
        val colors=when {
            !enabled -> intArrayOf(0x452E3746,0x35202936)
            field && focused -> intArrayOf(0xD03D424C.toInt(),0xD0252932.toInt())
            field -> intArrayOf(0xA62F333E.toInt(),0xB01D212A.toInt())
            bright && pressed -> intArrayOf(0xFFE0E5ED.toInt(),0xFFCBD3DF.toInt())
            bright -> intArrayOf(0xFFFFF6E6.toInt(),0xFFE6DCCB.toInt())
            pressed -> intArrayOf(0xB060626A.toInt(),0xA03B3E47.toInt())
            primary -> if(theme.id=="cinema") intArrayOf(0xC14C525E.toInt(),0xB02C313C.toInt()) else intArrayOf(MiniActThemes.alpha(MiniActThemes.mix(theme.glassBase,Color.WHITE,.13f),193),MiniActThemes.alpha(theme.glassShade,176))
            selected -> intArrayOf(0x694B4945,0x45332F2D)
            else -> if(theme.id=="cinema") intArrayOf(0x80363B45.toInt(),0x60232830) else intArrayOf(MiniActThemes.alpha(theme.glassBase,128),MiniActThemes.alpha(theme.glassShade,96))
        }
        fill=LinearGradient(0f,area.top,0f,area.bottom,colors,null,Shader.TileMode.CLAMP)
        edge=LinearGradient(area.left,area.top,area.right,area.bottom,
            intArrayOf(if(bright) 0xAFFFFFFF.toInt() else 0x26E9EDF4,if(bright) 0x40D6D9E0 else 0x05DBE5F8),null,Shader.TileMode.CLAMP)
    }
    override fun draw(canvas:Canvas) {
        if(area.isEmpty) return
        val curve=minOf(radius*unit,area.height()/2)
        if(ghost && !focused && !pressed && !primary) {
            if(selected) drawSelection(canvas)
            return
        }
        if(ghost && !focused && !selected && !pressed && !primary) return
        paint.style=Paint.Style.FILL; paint.shader=null; paint.color=if(focused) 0x26000000 else 0x18000000
        canvas.drawRoundRect(contact,curve,curve,paint)
        paint.shader=fill; paint.color=Color.WHITE
        canvas.drawRoundRect(area,curve,curve,paint)
        paint.style=Paint.Style.STROKE; paint.strokeWidth=unit*.5f; paint.shader=edge
        canvas.drawRoundRect(area,curve,curve,paint)
        paint.shader=null
        if(focused && enabled && field) {
            paint.color=0xFFDFE4ED.toInt()
            paint.strokeWidth=unit
            canvas.drawRoundRect(focusRing,curve+unit*2,curve+unit*2,paint)
        }
        if(selected) {
            paint.style=Paint.Style.FILL
            drawSelection(canvas)
        }
        paint.shader=null; paint.style=Paint.Style.FILL
    }
    private fun drawSelection(canvas:Canvas) {
        if(episode) {
            paint.shader=null; paint.style=Paint.Style.FILL
            paint.color=if(focused) 0xFF465264.toInt() else 0xFFE5D2B4.toInt()
            canvas.drawPath(marker,paint)
            return
        }
        paint.shader=null; paint.style=Paint.Style.STROKE; paint.strokeCap=Paint.Cap.ROUND
        paint.strokeWidth=unit*2
        paint.color=if(focused) 0xFF52627B.toInt() else 0xFFE5D2B4.toInt()
        if(rail) canvas.drawLine(area.left+unit*3,area.centerY()-unit*7,area.left+unit*3,area.centerY()+unit*7,paint)
        else canvas.drawLine(area.centerX()-unit*10,area.bottom-unit*5,area.centerX()+unit*10,area.bottom-unit*5,paint)
        paint.strokeCap=Paint.Cap.BUTT; paint.style=Paint.Style.FILL
    }
    override fun setAlpha(alpha:Int) {}
    override fun setColorFilter(filter:ColorFilter?) {}
    @Deprecated("Deprecated in Android") override fun getOpacity()=PixelFormat.TRANSLUCENT
}
