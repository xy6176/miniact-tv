// SPDX-License-Identifier: GPL-3.0-only
package com.hgtv.star

import android.view.KeyEvent
import android.view.View
import android.widget.LinearLayout
import com.hgtv.star.list.focusGridIndex
import com.hgtv.star.list.showCatalog
import com.hgtv.star.tv.*

/** Library sections are destinations, viewing state is a filter, clearing is a separate
 * action. Every control belongs to the same explicit, two-way remote focus route. */
internal fun MainActivity.miniActLibraryBar(parent:LinearLayout) {
    val controls=mutableListOf<View>()
    val line=row().apply { setPadding(0,dp(2),0,dp(7)) }
    val sections=row()
    listOf("history" to "最近观看","favorites" to "我的收藏","later" to "稍后观看").forEach { (key,label) ->
        val button=TvButton(this,label,ButtonSize.COMPACT,ButtonTone.GHOST,action={ showCollection(key) },pad=18,tabStyle=true)
        button.setSelectedState(collection==key)
        button.contentDescription=label+if(collection==key) "，当前栏目" else ""
        collectionButtons[key]=button; controls+=button
        sections.addView(button,lp(-2,-2).apply { if(sections.childCount>0) leftMargin=dp(6) })
    }
    line.addView(sections,lp(-2,-2))
    line.addView(View(this),lp(0,1).apply { weight=1f })
    if(collection=="history") {
        val labels=listOf("全部记录","已看完","未看完")
        val values=listOf<Boolean?>(null,true,false)
        val selected=values.indexOf(accountHistoryFilter).coerceAtLeast(0)
        lateinit var status:TvButton
        status=TvButton(this,"${labels[selected]}  ⌄",ButtonSize.COMPACT,ButtonTone.GHOST,pad=16,action={
            val dialog=TvDialog(this,"观看状态").choices(labels,selected,onChoice={ index ->
                accountHistoryFilter=values[index]
                catalogFocus=""; chipFocusKey="library:status"
                showCatalog(keepChip=true)
            })
            dialog.button("取消")
            dialog.show(status)
        })
        status.contentDescription="观看状态，${labels[selected]}。按确定选择"
        line.addView(status,lp(-2,-2)); controls+=status; collectionButtons["status"]=status
        historyFilterButtons[true]=status; historyFilterButtons[false]=status
        historyClearButton(line,ButtonSize.COMPACT,gap=8,label="清空记录")?.let {
            collectionButtons["clear"]=it; controls+=it
        }
    }
    controls.forEachIndexed { index,control ->
        (control as? TvButton)?.onFocus={ focused ->
            if(focused) collectionControlKey=collectionButtons.entries.firstOrNull { it.value===control }?.key
        }
        control.nextFocusLeftId=controls.getOrNull(index-1)?.id ?: navKeyed["history"]?.id ?: control.id
        control.nextFocusRightId=controls.getOrNull(index+1)?.id ?: control.id
        control.nextFocusUpId=navKeyed["history"]?.id ?: control.id
        control.setOnKeyListener { _,key,event ->
            if(event.action!=KeyEvent.ACTION_DOWN) false else when(key) {
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    controls.getOrNull(index-1)?.let(::focusHead) ?: focusMiniActRail(); true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    controls.getOrNull(index+1)?.let(::focusHead); true
                }
                KeyEvent.KEYCODE_DPAD_UP -> { focusMiniActRail(); true }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if(gridItems.isNotEmpty()) { focusGridIndex(0,false); true } else true
                }
                else -> false
            }
        }
    }
    parent.addView(line,lp(-1,-2))
}
