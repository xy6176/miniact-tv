// SPDX-License-Identifier: GPL-3.0-only
package com.hgtv.star.tv
import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.view.*
import android.widget.TextView
import com.hgtv.star.compat.vectorDrawable

internal class NavigationButton(context: Context,label: String,iconRes: Int?=null,action: ()->Unit) : TextView(context) {
    private var active=false
    private val selection=MiniActSelection(this,12,0,rail=true)
    private val icon=iconRes?.let { context.vectorDrawable(it)?.mutate() }
    private fun dp(value: Number)=context.designPx(value).toFloat()
    init {
        id=View.generateViewId(); isFocusable=true; isFocusableInTouchMode=true
        text=label; contentDescription=label; gravity=Gravity.CENTER
        includeFontPadding=false; maxLines=1; designTextSize(15f)
        // Fixed icon column + a separately centered label column. Variable-length
        // labels never shift neighboring icons or their own shared optical center.
        setPadding(context.designPx(if(icon!=null) 43 else 12),0,context.designPx(12),0)
        background=selection
        setOnFocusChangeListener { _,_ -> paint() }; setOnClickListener { action() }; paint()
    }
    fun setActive(value: Boolean) { active=value; paint() }
    override fun onDraw(canvas:Canvas) {
        super.onDraw(canvas)
        icon?.let {
            val size=context.designPx(18); val left=context.designPx(15)
            val top=(height-size)/2
            it.setBounds(left,top,left+size,top+size); it.draw(canvas)
        }
    }
    private fun paint() {
        selection.state(isFocused,active,ghost=true)
        val color=if(isFocused) 0xFF111216.toInt() else if(active) 0xFFF1F0EC.toInt() else 0xFFBCC0CC.toInt()
        setTextColor(color); icon?.setColorFilter(color,PorterDuff.Mode.SRC_IN)
        if(typeface==null || typeface!=Typeface.create("sans-serif-medium",Typeface.NORMAL)) typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
        invalidate()
    }
    override fun setPressed(pressed: Boolean) { super.setPressed(pressed); alpha=if(pressed) .8f else 1f }
}
