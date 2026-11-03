// SPDX-License-Identifier: GPL-3.0-only
package com.hgtv.star.tv

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.view.View
import com.hgtv.star.R
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import kotlinx.coroutines.*

/** A shared, poster-derived frosted scene. Never captures the window or video surface.
 * Only a 192 × 108 image is blurred, off the UI thread, once when the poster changes.
 * Works on API 19; every panel samples the same image in window coordinates. */
internal class MiniActGlassScene(root: View, private val scope: CoroutineScope,val browsing:Boolean=false) {
    private val rootRef=WeakReference(root)
    private val observers=WeakHashMap<View,Boolean>()
    private var job: Job?=null
    private var generation=0
    private var source=WeakReference<Bitmap>(null)
    var reduced=root.context.glassReduced(); private set
    var theme=MiniActThemes.current;private set
    var image: Bitmap?=if(browsing) MiniActAtmosphere.image(theme) else null; private set
    var revision=0; private set
    fun observe(view: View) { observers[view]=true }
    fun origin(out: IntArray) { rootRef.get()?.getLocationOnScreen(out) ?: out.fill(0) }
    fun width()=rootRef.get()?.width?.coerceAtLeast(1) ?: 1
    fun height()=rootRef.get()?.height?.coerceAtLeast(1) ?: 1
    fun setReduced(value: Boolean) { reduced=value; publish() }
    fun applyTheme(value:MiniActTheme) {
        if(theme.id==value.id)return
        theme=value;if(browsing)image=MiniActAtmosphere.image(value);publish()
    }
    private fun publish() {
        revision++; rootRef.get()?.invalidate()
        observers.keys.toList().forEach { it.invalidate() }
    }
    fun poster(bitmap: Bitmap) {
        if(browsing) return
        if(bitmap.isRecycled || source.get()===bitmap) return
        source=WeakReference(bitmap)
        job?.cancel(); val token=++generation
        // Read the loader's bitmap while its callback owns it. Work thereafter uses our small copy.
        val tiny=Bitmap.createBitmap(192,108,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(tiny)
        val scale=maxOf(192f/bitmap.width,108f/bitmap.height)
        val w=bitmap.width*scale; val h=bitmap.height*scale
        val brush=Paint(Paint.FILTER_BITMAP_FLAG)
        brush.colorFilter=ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(.65f) })
        canvas.drawBitmap(bitmap,null,RectF((192-w)/2,(108-h)/2,(192+w)/2,(108+h)/2),brush)
        job=scope.launch {
            val result=withContext(Dispatchers.Default) { blur(tiny) }
            if(token==generation && isActive) { image=result; publish() }
        }
    }
    private fun blur(bitmap: Bitmap): Bitmap {
        val w=bitmap.width; val h=bitmap.height
        var pixels=IntArray(w*h); var scratch=IntArray(w*h)
        bitmap.getPixels(pixels,0,w,0,0,w,h)
        // Three separable box passes approximate a Gaussian. No full-resolution allocations.
        repeat(3) {
            for(axis in 0..1) {
                val length=if(axis==0) w else h; val lines=if(axis==0) h else w
                for(line in 0 until lines) {
                    fun at(pos: Int)=if(axis==0) line*w+pos.coerceIn(0,w-1) else pos.coerceIn(0,h-1)*w+line
                    var r=0; var g=0; var b=0
                    for(k in -6..6) { val c=pixels[at(k)]; r+=Color.red(c); g+=Color.green(c); b+=Color.blue(c) }
                    for(p in 0 until length) {
                        scratch[at(p)]=Color.rgb(r/13,g/13,b/13)
                        val a=pixels[at(p-6)]; val z=pixels[at(p+7)]
                        r+=Color.red(z)-Color.red(a); g+=Color.green(z)-Color.green(a); b+=Color.blue(z)-Color.blue(a)
                    }
                }
                val swap=pixels; pixels=scratch; scratch=swap
            }
        }
        bitmap.setPixels(pixels,0,w,0,0,w,h)
        return bitmap
    }
}

internal fun Context.glassReduced()=getSharedPreferences("miniact_materials",Context.MODE_PRIVATE).getBoolean("reduce_transparency",false)
internal fun View.glassScene(): MiniActGlassScene? {
    var current: View?=this
    while(current!=null) {
        (current.getTag(R.id.miniact_glass_scene) as? MiniActGlassScene)?.let { return it }
        current=current.parent as? View
    }
    return null
}

/** Optical material, not a translucent flat fill: shared blur + tint + glaze + edge light.
 * The blur is of the decorative artwork, not of moving foreground text or video. */
internal class MiniActGlass(private val owner: View, radius: Int=20, private val weight: Float=.58f,private val opacity:Float=1f) : Drawable() {
    private val radius=owner.context.designPx(radius).toFloat()
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val plate=RectF()
    private val matrix=Matrix()
    private val location=IntArray(2)
    private val origin=IntArray(2)
    private var scene: MiniActGlassScene?=null
    private var shader: BitmapShader?=null
    private var version=-1
    private var placementDirty=true
    private var glaze: Shader?=null
    private var edge: Shader?=null
    private var reflection:Shader?=null
    private val inner=RectF()
    private val contact=RectF()
    private var focused=false
    private var normalTint=tint(weight,MiniActThemes.current)
    private var focusTint=tint(weight*.75f,MiniActThemes.current)
    /** Collapse base + translucent artwork + dark tint into one opaque shader pass.
     * Same blend equation as the layered material, with two fewer full-panel fills. */
    private fun tint(value: Float,theme:MiniActTheme): ColorMatrixColorFilter {
        val amount=(value*255).toInt()/255f
        val artwork=170f/255f
        val scale=artwork*(1-amount)
        fun offset(base: Int,dark: Int)=base*(1-artwork)*(1-amount)+dark*amount
        return ColorMatrixColorFilter(floatArrayOf(
            scale,0f,0f,0f,offset(Color.red(theme.glassBase),Color.red(theme.glassShade)),
            0f,scale,0f,0f,offset(Color.green(theme.glassBase),Color.green(theme.glassShade)),
            0f,0f,scale,0f,offset(Color.blue(theme.glassBase),Color.blue(theme.glassShade)),
            0f,0f,0f,opacity.coerceIn(0f,1f),0f))
    }
    fun focus(value: Boolean) { if(focused!=value) { focused=value; invalidateSelf() } }
    override fun onBoundsChange(bounds: Rect) {
        placementDirty=true
        val half=owner.context.designPx(1)*.5f
        plate.set(bounds.left+half,bounds.top+half,bounds.right-half,bounds.bottom-half)
        inner.set(plate);inner.inset(half*2,half*2)
        contact.set(plate);contact.offset(0f,half*5)
        glaze=LinearGradient(plate.left,plate.top,plate.right,plate.bottom,intArrayOf(0x0CF8F5EF,0x00FFFFFF,0x03CFD9EB),floatArrayOf(0f,.42f,1f),Shader.TileMode.CLAMP)
        edge=LinearGradient(plate.left,plate.top,plate.right,plate.bottom,intArrayOf(0x42ECEFF8,0x05F5F2E9,0x18B4BDCE),floatArrayOf(0f,.36f,1f),Shader.TileMode.CLAMP)
        reflection=LinearGradient(plate.left,plate.top,plate.left+plate.width()*.72f,plate.top+plate.height()*.36f,
            intArrayOf(0x48FFFFFF,Color.TRANSPARENT),null,Shader.TileMode.CLAMP)
    }
    override fun draw(canvas: Canvas) {
        val current=owner.glassScene()
        if(scene!==current || version!=(current?.revision ?: 0)) {
            scene=current; version=current?.revision ?: 0; current?.observe(owner)
            val theme=current?.theme ?: MiniActThemes.current
            normalTint=tint(weight,theme);focusTint=tint(weight*.75f,theme)
            placementDirty=true
            shader=current?.image?.let { BitmapShader(it,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP) }
        }
        val reduced=current?.reduced ?: owner.context.glassReduced()
        paint.style=Paint.Style.FILL; paint.shader=null
        // A small contact shadow separates the sheet from its environment. It
        // is recorded with the panel, with no software blur or offscreen layer.
        paint.color=0x30000000
        canvas.drawRoundRect(contact,radius,radius,paint)
        paint.color=MiniActThemes.alpha((current?.theme ?: MiniActThemes.current).glassShade,if(reduced) 255 else (opacity.coerceIn(0f,1f)*255).toInt())
        val texture=shader.takeUnless { reduced }
        if(texture!=null) {
                // Pin a panel's decorative texture at layout time. Scroll transforms move
                // the already-recorded panel; no window traversal or shader mutation per frame.
                if(placementDirty) {
                    owner.getLocationOnScreen(location); current?.origin(origin)
                    matrix.setScale((current?.width() ?: 1)/192f,(current?.height() ?: 1)/108f)
                    matrix.postTranslate((origin[0]-location[0]).toFloat(),(origin[1]-location[1]).toFloat())
                    texture.setLocalMatrix(matrix); placementDirty=false
                }
                paint.shader=texture
                paint.color=Color.WHITE
                paint.colorFilter=if(focused) focusTint else normalTint
                canvas.drawRoundRect(plate,radius,radius,paint)
                paint.colorFilter=null; paint.shader=null
        } else {
            canvas.drawRoundRect(plate,radius,radius,paint)
        }
        if(!reduced) {
            paint.color=Color.WHITE; paint.shader=glaze
            canvas.drawRoundRect(plate,radius,radius,paint); paint.shader=null
        }
        if(focused) { paint.color=0x18FFFFFF; canvas.drawRoundRect(plate,radius,radius,paint) }
        paint.style=Paint.Style.STROKE
        paint.strokeWidth=owner.context.designPx(if(focused) 2 else 1).toFloat()
        if(focused) paint.color=0xE6FFFFFF.toInt() else { paint.color=Color.WHITE; paint.shader=edge }
        canvas.drawRoundRect(plate,radius,radius,paint); paint.shader=null; paint.style=Paint.Style.FILL
        if(!reduced) {
            paint.style=Paint.Style.STROKE;paint.strokeWidth=owner.context.designScale()*.5f
            paint.color=Color.WHITE;paint.shader=reflection
            canvas.drawRoundRect(inner,(radius-owner.context.designScale()).coerceAtLeast(0f),(radius-owner.context.designScale()).coerceAtLeast(0f),paint)
            paint.shader=null;paint.style=Paint.Style.FILL
        }
    }
    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(filter: ColorFilter?) {}
    @Deprecated("Deprecated in Android") override fun getOpacity()=PixelFormat.TRANSLUCENT
}
