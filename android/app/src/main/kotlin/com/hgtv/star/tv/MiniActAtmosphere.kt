// SPDX-License-Identifier: GPL-3.0-only
package com.hgtv.star.tv

import android.graphics.*

/** Fixed optical environment for browsing. Large, defocused light sources give
 * glass something to transmit without making a category depend on a poster.
 * Created once at the same tiny resolution as the existing artwork sampler. */
internal object MiniActAtmosphere {
    private val images=mutableMapOf<String,Bitmap>()
    fun image(theme:MiniActTheme):Bitmap=images.getOrPut(theme.id) {
        Bitmap.createBitmap(192,108,Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas=Canvas(bitmap)
            val paint=Paint(Paint.ANTI_ALIAS_FLAG)
            paint.shader=LinearGradient(0f,0f,192f,108f,
                intArrayOf(theme.atlasTop,theme.atlasMiddle,theme.atlasBottom),
                floatArrayOf(0f,.48f,1f),Shader.TileMode.CLAMP)
            canvas.drawRect(0f,0f,192f,108f,paint)
            fun light(x:Float,y:Float,r:Float,color:Int) {
                paint.shader=RadialGradient(x,y,r,intArrayOf(color,Color.TRANSPARENT),null,Shader.TileMode.CLAMP)
                canvas.drawRect(0f,0f,192f,108f,paint)
            }
            light(-8f,6f,100f,theme.cool)
            light(32f,99f,79f,theme.warm)
            light(151f,-28f,125f,MiniActThemes.alpha(theme.atlasTop,92))
            // A long soft reflection, rather than another rounded UI container.
            canvas.save();canvas.rotate(-24f,12f,50f)
            paint.shader=LinearGradient(-30f,0f,32f,0f,
                intArrayOf(Color.TRANSPARENT,0x227F92A9,Color.TRANSPARENT),null,Shader.TileMode.CLAMP)
            canvas.drawRect(-30f,-100f,32f,210f,paint);canvas.restore()
        }
    }
}
