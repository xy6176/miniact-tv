// SPDX-License-Identifier: GPL-3.0-only
package com.hgtv.star.player

import android.app.Activity
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.hgtv.star.core.EpisodeBrowse
import com.hgtv.star.tv.*

/** Paging, locating and playing are separate actions inside the same inspector. */
internal class MiniActEpisodePicker(private val activity:Activity,count:Int,current:Int,pageSize:Int,
    private val back:View,private val select:(Int)->Unit):LinearLayout(activity) {
    private enum class Mode { EPISODES, RANGES, NUMBER }
    private fun dp(n:Number)=activity.designPx(n)
    private val browse=EpisodeBrowse(count,current,pageSize)
    private var mode=Mode.EPISODES
    private val levels=mutableListOf<EpisodeBrowse.Range>()
    private var rangeFocusPage=0
    private var draft=""
    private val heading=activity.tvText("",MiniActTokens.caption,MiniActTokens.secondary)
    private val quick=activity.tvRow()
    private val pages=activity.tvRow()
    private val grid=activity.tvColumn()
    private val jump=TvButton(activity,"跳到集数",ButtonSize.COMPACT,ButtonTone.GHOST,action={ openNumber() },pad=10)
    private val playing=TvButton(activity,"定位当前集",ButtonSize.COMPACT,ButtonTone.GHOST,action={
        browse.locateCurrent();mode=Mode.EPISODES;render();focusCurrent()
    },pad=10)
    private val previous=TvButton(activity,"上一页",ButtonSize.COMPACT,ButtonTone.GHOST,action={ turn(-1) },pad=8)
    private val next=TvButton(activity,"下一页",ButtonSize.COMPACT,ButtonTone.GHOST,action={ turn(1) },pad=8)
    private val range=TvButton(activity,"",ButtonSize.COMPACT,ButtonTone.GHOST,action={ openRanges() },pad=5)
    private var pagerAnchor:View?=null
    private var cells=emptyList<EpisodeCell>()
    private var choices=emptyList<View>()
    private var numberDisplay:TextView?=null
    private var numberHint:TextView?=null
    private var numberConfirm:View?=null
    init {
        orientation=VERTICAL
        addView(heading,LayoutParams(-1,-2).apply { bottomMargin=dp(10) })
        quick.addView(jump,LayoutParams(0,dp(44)).apply { weight=1f;rightMargin=dp(6) })
        quick.addView(playing,LayoutParams(0,dp(44)).apply { weight=1f })
        addView(quick,LayoutParams(-1,-2).apply { bottomMargin=dp(8) })
        pages.addView(previous,LayoutParams(-2,-1))
        pages.addView(range,LayoutParams(0,-1).apply { weight=1f;leftMargin=dp(4);rightMargin=dp(4) })
        pages.addView(next,LayoutParams(-2,-1))
        addView(pages,LayoutParams(-1,dp(46)).apply { bottomMargin=dp(10) })
        addView(grid,LayoutParams(-1,-2))
        listOf(previous,range,next).forEach { control -> control.onFocus={ focused ->
            if(focused) { pagerAnchor=control;wireEpisodeUp() };boundaryAppearance()
        } }
        render()
    }
    fun focusCurrent() {
        if(mode!=Mode.EPISODES) { focusEntry();return }
        (cells.firstOrNull { it.tag==browse.focused } ?: cells.firstOrNull() ?: jump).requestFocus()
    }
    fun focusEntry() { (if(mode==Mode.EPISODES) jump else choices.firstOrNull() ?: back).requestFocus() }
    fun update(total:Int,playing:Int) {
        if(total==browse.count && playing==browse.current) return
        val changedCount=total!=browse.count
        val wanted=findFocus()?.tag as? Int
        browse.update(total,playing)
        if(mode==Mode.NUMBER) { updateNumber();return }
        if(mode==Mode.RANGES && !changedCount) return
        if(mode==Mode.RANGES) levels.clear()
        render();if(wanted!=null) focusCurrent()
    }
    fun back():Boolean = when(mode) {
        Mode.EPISODES -> false
        Mode.NUMBER -> { mode=Mode.EPISODES;render();jump.requestFocus();true }
        Mode.RANGES -> {
            if(levels.size>1) { rangeFocusPage=levels.removeAt(levels.lastIndex).fromPage;render();focusRange() }
            else { mode=Mode.EPISODES;render();range.requestFocus() };true
        }
    }
    override fun dispatchKeyEvent(event:KeyEvent):Boolean {
        if(event.keyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) {
            if(event.action==KeyEvent.ACTION_DOWN && event.repeatCount==0) {
                if(mode!=Mode.NUMBER) openNumber()
                appendDigit(event.keyCode-KeyEvent.KEYCODE_0);numberConfirm?.requestFocus()
            };return true
        }
        if(mode==Mode.NUMBER && event.keyCode==KeyEvent.KEYCODE_DEL) {
            if(event.action==KeyEvent.ACTION_DOWN) { draft=draft.dropLast(1);updateNumber() };return true
        }
        return super.dispatchKeyEvent(event)
    }
    private fun turn(delta:Int) {
        val anchor=if(delta<0) previous else next
        if(browse.turn(delta)) { mode=Mode.EPISODES;render() }
        anchor.requestFocus()
    }
    private fun boundaryAppearance() {
        // Dim at boundaries, but keep controls focusable for repeated paging.
        previous.alpha=if(previous.hasFocus() || browse.page>0) 1f else .45f
        next.alpha=if(next.hasFocus() || browse.page+1<browse.pageCount) 1f else .45f
        previous.contentDescription=if(browse.page>0) "上一页" else "上一页，已到第一页"
        next.contentDescription=if(browse.page+1<browse.pageCount) "下一页" else "下一页，已到最后一页"
    }
    private fun render() {
        grid.removeAllViews();cells=emptyList();choices=emptyList()
        numberDisplay=null;numberHint=null;numberConfirm=null
        quick.visibility=if(mode==Mode.EPISODES) View.VISIBLE else View.GONE;pages.visibility=quick.visibility
        when(mode) { Mode.EPISODES -> renderEpisodes();Mode.RANGES -> renderRanges();Mode.NUMBER -> renderNumber() }
    }
    private fun renderEpisodes() {
        heading.text=if(browse.count>0) "正在看第 ${browse.current+1} 集 · 共 ${browse.count} 集" else "暂无可选集数"
        range.text=if(browse.count>0) "${browse.first+1}—${browse.end} 集  ▾" else "暂无集数"
        range.contentDescription="分段选集，${range.text}"
        jump.nextFocusUpId=back.id;playing.nextFocusUpId=back.id
        jump.nextFocusLeftId=jump.id;jump.nextFocusRightId=playing.id
        playing.nextFocusLeftId=jump.id;playing.nextFocusRightId=playing.id
        jump.nextFocusDownId=range.id;playing.nextFocusDownId=range.id
        previous.nextFocusLeftId=previous.id;previous.nextFocusRightId=range.id
        range.nextFocusLeftId=previous.id;range.nextFocusRightId=next.id
        next.nextFocusLeftId=range.id;next.nextFocusRightId=next.id
        previous.nextFocusUpId=jump.id;range.nextFocusUpId=jump.id;next.nextFocusUpId=playing.id
        cells=(browse.first until browse.end).map { index ->
            EpisodeCell(activity,"${index+1}",index==browse.current) { select(index) }.apply {
                allowCompactHeight();tag=index
                contentDescription="第 ${index+1} 集"+(if(index==browse.current) "，当前播放" else "")+"，确认播放"
                onFocus={ focused -> if(focused) { browse.remember(index);wirePagerDown() } }
            }
        }
        addRows(cells,4);wireGrid(cells,4,{ (pagerAnchor ?: range).id })
        wirePagerDown();wireEpisodeUp();boundaryAppearance()
    }
    private fun wirePagerDown() {
        val target=cells.firstOrNull { it.tag==browse.focused } ?: cells.firstOrNull() ?: jump
        listOf(previous,range,next).forEach { it.nextFocusDownId=target.id }
    }
    private fun wireEpisodeUp() { cells.take(4).forEach { it.nextFocusUpId=(pagerAnchor ?: range).id } }
    private fun openRanges() {
        if(browse.pageCount==0) return
        mode=Mode.RANGES;levels.clear();levels+=EpisodeBrowse.Range(0,browse.pageCount)
        rangeFocusPage=browse.page;render();focusRange()
    }
    private fun renderRanges() {
        val limit=levels.lastOrNull() ?: EpisodeBrowse.Range(0,browse.pageCount).also { levels+=it }
        heading.text=if(levels.size==1) "分段选集 · 共 ${browse.count} 集" else "${browse.rangeLabel(limit)} · 选择范围"
        choices=browse.sections(limit).map { section ->
            TvButton(activity,browse.rangeLabel(section),ButtonSize.COMPACT,ButtonTone.GHOST,action={
                if(section.untilPage-section.fromPage==1) {
                    browse.showPage(section.fromPage);mode=Mode.EPISODES;render();range.requestFocus()
                } else { levels+=section;rangeFocusPage=section.fromPage;render();focusRange() }
            },pad=8).apply {
                tag=section
                contentDescription="${browse.rangeLabel(section)}，"+if(section.untilPage-section.fromPage==1) "查看这一页" else "展开分段"
                setSelectedState(browse.page in section.fromPage until section.untilPage)
            }
        };addRows(choices,2);wireGrid(choices,2,{ back.id })
    }
    private fun focusRange() {
        (choices.firstOrNull { val s=it.tag as? EpisodeBrowse.Range;s!=null && rangeFocusPage in s.fromPage until s.untilPage }
            ?: choices.firstOrNull() ?: back).requestFocus()
    }
    private fun openNumber() { mode=Mode.NUMBER;draft="";render();choices.firstOrNull()?.requestFocus() }
    private fun renderNumber() {
        heading.text="跳到集数 · 共 ${browse.count} 集"
        numberDisplay=activity.tvText("",MiniActTokens.title,MiniActTokens.text).apply {
            gravity=Gravity.CENTER;setPadding(dp(12),dp(10),dp(12),dp(10));background=MiniActControlSurface(this,radius=14)
        }.also { grid.addView(it,LayoutParams(-1,dp(58)).apply { bottomMargin=dp(8) }) }
        numberHint=activity.tvText("",MiniActTokens.caption,MiniActTokens.secondary).also {
            grid.addView(it,LayoutParams(-1,-2).apply { bottomMargin=dp(12) })
        }
        choices=listOf("1","2","3","4","5","6","7","8","9","删除","0","定位").map { label ->
            TvButton(activity,label,ButtonSize.COMPACT,ButtonTone.GHOST,action={
                when(label) { "删除" -> { draft=draft.dropLast(1);updateNumber() };"定位" -> locateNumber();else -> appendDigit(label.toInt()) }
            },pad=8).apply { contentDescription=when(label) { "删除" -> "删除一位";"定位" -> "定位到输入的集数，不自动播放";else -> label } }
        }
        numberConfirm=choices.last();addRows(choices,3);wireGrid(choices,3,{ back.id });updateNumber()
    }
    private fun appendDigit(digit:Int) {
        val next=(draft+digit).trimStart('0').ifBlank { "0" }
        if(next.length>browse.count.toString().length) return
        draft=next;updateNumber()
    }
    private fun updateNumber() {
        numberDisplay?.text=if(draft.isEmpty()) "输入集数" else "第 $draft 集"
        numberHint?.text=if(browse.count>0) "可输入 1—${browse.count}，定位后确认播放" else "暂无可选集数"
    }
    private fun locateNumber() {
        if(!browse.locate(draft)) { numberHint?.text="请输入 1—${browse.count} 之间的集数";return }
        mode=Mode.EPISODES;render();focusCurrent()
    }
    private fun addRows(items:List<View>,columns:Int) {
        items.chunked(columns).forEachIndexed { index,views ->
            val row=activity.tvRow()
            views.forEachIndexed { column,view ->
                val height=maxOf(dp(50),(view as? TextView)?.lineHeight?.plus(dp(20)) ?: 0)
                row.addView(view,LayoutParams(0,height).apply { weight=1f;if(column>0) leftMargin=dp(6) })
            }
            repeat(columns-views.size) { row.addView(View(activity),LayoutParams(0,dp(1)).apply { weight=1f;leftMargin=dp(6) }) }
            grid.addView(row,LayoutParams(-1,-2).apply { if(index>0) topMargin=dp(6) })
        }
    }
    private fun wireGrid(items:List<View>,columns:Int,up:()->Int) {
        items.forEachIndexed { index,view ->
            val col=index%columns
            view.nextFocusLeftId=if(col>0) items[index-1].id else view.id
            view.nextFocusRightId=if(col<columns-1 && index<items.lastIndex) items[index+1].id else view.id
            view.nextFocusUpId=if(index>=columns) items[index-columns].id else up()
            view.nextFocusDownId=when { index+columns<items.size -> items[index+columns].id;index/columns<items.lastIndex/columns -> items.last().id;else -> view.id }
            view.setOnKeyListener { _,key,event ->
                if(event.action!=KeyEvent.ACTION_DOWN) false else {
                    val id=when(key) { KeyEvent.KEYCODE_DPAD_LEFT -> view.nextFocusLeftId;KeyEvent.KEYCODE_DPAD_RIGHT -> view.nextFocusRightId;KeyEvent.KEYCODE_DPAD_UP -> view.nextFocusUpId;KeyEvent.KEYCODE_DPAD_DOWN -> view.nextFocusDownId;else -> return@setOnKeyListener false }
                    activity.findViewById<View>(id)?.requestFocus();true
                }
            }
        }
    }
}
