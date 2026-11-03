// SPDX-License-Identifier: GPL-3.0-only
package com.hgtv.star.tv
import android.content.Context
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
internal class MiniActClock(context: Context): TextView(context) {
    private val format=SimpleDateFormat("HH:mm",Locale.getDefault())
    private val tick=object:Runnable {
        override fun run() {
            text=format.format(Date())
            postDelayed(this,60_000-System.currentTimeMillis()%60_000)
        }
    }
    init { designTextSize(12f); setTextColor(0xFFB5B6BE.toInt()); includeFontPadding=false }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); removeCallbacks(tick); tick.run() }
    override fun onDetachedFromWindow() { removeCallbacks(tick); super.onDetachedFromWindow() }
}
