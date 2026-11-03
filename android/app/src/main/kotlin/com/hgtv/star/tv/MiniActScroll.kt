// SPDX-License-Identifier: GPL-3.0-only
package com.hgtv.star.tv

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.HorizontalScrollView
import android.widget.ScrollView
import com.hgtv.star.R

/** Remote navigation retargets from the visible position, even during key repeat.
 * Android's stock smoothScrollTo switches to an instant jump for closely-spaced calls. */
internal fun View.moveMiniActViewport(x: Int,y: Int,animated: Boolean=true) {
    val motion=(getTag(R.id.miniact_scroll_motion) as? MiniActScroll) ?: MiniActScroll(this).also {
        setTag(R.id.miniact_scroll_motion,it)
    }
    motion.move(x,y,animated && TvStyle.animate && TvStyle.television)
}

private class MiniActScroll(private val view: View) : View.OnAttachStateChangeListener {
    private var animator: ValueAnimator?=null
    private var targetX=0
    private var targetY=0
    init { view.addOnAttachStateChangeListener(this) }
    fun move(x: Int,y: Int,animated: Boolean) {
        if(animator!=null && x==targetX && y==targetY && animated) return
        animator?.cancel(); animator=null
        targetX=x; targetY=y
        if(!animated || view.scrollX==x && view.scrollY==y) { view.scrollTo(x,y); return }
        // Stop any stock focus scroll before starting the interruptible remote movement.
        when(view) { is ScrollView -> view.fling(0); is HorizontalScrollView -> view.fling(0) }
        val fromX=view.scrollX; val fromY=view.scrollY
        val next=ValueAnimator.ofFloat(0f,1f).apply {
            duration=180; interpolator=DecelerateInterpolator(1.6f)
            addUpdateListener {
                val progress=it.animatedValue as Float
                view.scrollTo((fromX+(x-fromX)*progress).toInt(),(fromY+(y-fromY)*progress).toInt())
            }
        }
        next.addListener(object:AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) { if(animator===animation) animator=null }
        })
        animator=next; next.start()
    }
    override fun onViewAttachedToWindow(view: View) {}
    override fun onViewDetachedFromWindow(view: View) { animator?.cancel(); animator=null }
}
