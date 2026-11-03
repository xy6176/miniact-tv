// SPDX-License-Identifier: GPL-3.0-only
package com.hgtv.star.tv

import android.graphics.*
import android.graphics.drawable.Drawable
import android.view.View

/** Selection controls share the action-button material, with a marker for their role.
 * Keep this adapter so navigation, filters and settings retain their focus contracts. */
internal class MiniActSelection(owner:View,radius:Int=12,band:Int=6,rail:Boolean=false):Drawable() {
    private val surface=MiniActControlSurface(owner,radius=radius,band=band,rail=rail)
    fun state(focus:Boolean,selection:Boolean=false,primary:Boolean=false,ghost:Boolean=false) {
        surface.appearance(focus,primary,ghost,selection)
        invalidateSelf()
    }
    override fun onBoundsChange(bounds:Rect) { surface.bounds=bounds }
    override fun isStateful()=true
    override fun onStateChange(state:IntArray):Boolean {
        val changed=surface.setState(state)
        if(changed) invalidateSelf()
        return changed
    }
    override fun draw(canvas:Canvas)=surface.draw(canvas)
    override fun setAlpha(alpha:Int) { surface.alpha=alpha }
    override fun setColorFilter(filter:ColorFilter?) { surface.colorFilter=filter }
    @Deprecated("Deprecated in Android") override fun getOpacity()=PixelFormat.TRANSLUCENT
}
