// SPDX-License-Identifier: GPL-3.0-only
package com.hgtv.star.player

import android.app.Activity
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.hgtv.star.R
import com.hgtv.star.compat.vectorDrawable
import com.hgtv.star.tv.*

/** Bounded TV inspector: show current values first, reveal one setting at a time.
 * Row indices belong to the existing playback controller; grouping never changes them. */
internal class MiniActPlaybackPanel(private val activity:Activity):LinearLayout(activity) {
    private fun dp(n:Number)=activity.designPx(n)
    private data class Section(
        val title:String,val choices:List<PlaybackMenu.Choice> = emptyList(),
        var value:Float=0f,val format:(Float)->String={ "${it.toInt()}%" },
        val slider:SettingSlider?=null
    )
    private val sections=mutableListOf<Section>()
    private val summaries=mutableMapOf<Int,Cell>()
    private val options=mutableListOf<Cell>()
    private val tabs=mutableListOf<TvButton>()
    private val title=activity.tvText("播放选项",MiniActTokens.title).apply { typeface=Typeface.create("sans-serif-medium",0); maxLines=1; ellipsize=TextUtils.TruncateAt.END }
    private val contextLine=activity.tvText("",13f,TvStyle.muted).apply { maxLines=2; ellipsize=TextUtils.TruncateAt.END; setLineSpacing(0f,1.15f) }
    private val back=IconButton(activity,R.drawable.ic_playback_back,"返回播放选项",32) { back() }
    private val close=IconButton(activity,R.drawable.ic_menu_close,"关闭播放选项",32) { onClose() }
    private val tabRow=activity.tvRow()
    private val body=LinearLayout(activity).apply { orientation=VERTICAL; setPadding(0,dp(5),0,dp(8)) }
    private val scroll=ScrollView(activity).apply { isVerticalScrollBarEnabled=false; isFillViewport=false; clipToPadding=false; addView(body) }
    private val footer=activity.tvText("上下键选择 · 确认键调整 · 返回键关闭",11f,TvStyle.muted)
    private var group=0
    private var editing:Int?=null
    private var episodePicker:MiniActEpisodePicker?=null
    private var nowPlaying=""
    private var reading:TextView?=null
    var onClose:()->Unit={}
    private var onEpisodes:()->Unit={}
    private val episodes=Cell("选集","选择剧集",R.drawable.ic_player_episodes) { onEpisodes() }
    private var onFavorite:()->Unit={}
    private val favorite=Cell("收藏这部剧","同步至账号",R.drawable.ic_action_favorite) { onFavorite() }
    fun favoriteAction(label:String,value:String,selected:Boolean,action:()->Unit) {
        onFavorite=action;favorite.actionState(label,value,selected)
    }
    fun episodeAction(label:String,action:()->Unit) { episodes.value(label); onEpisodes=action }
    fun episodeLabel(label:String) { episodes.value(label) }
    fun overview() { showGroup(0); episodes.requestFocus() }
    val isEditing get()=editing!=null || episodePicker!=null
    fun showEpisodes(count:Int,current:Int,pageSize:Int,select:(Int)->Unit) {
        editing=null; reading=null; options.clear()
        title.text="选集"; contextLine.text=nowPlaying
        back.visibility=View.VISIBLE; tabRow.visibility=View.GONE
        footer.text="方向键选择 · 确认键操作 · 返回上一级"
        body.removeAllViews()
        val picker=MiniActEpisodePicker(activity,count,current,pageSize,back,select)
        episodePicker=picker
        body.addView(picker,LayoutParams(-1,-2))
        back.nextFocusUpId=back.id; back.nextFocusLeftId=back.id; back.nextFocusRightId=close.id
        close.nextFocusLeftId=back.id
        picker.post {
            picker.focusCurrent()
            val target=picker.findFocus()?.id ?: back.id
            back.nextFocusDownId=target; close.nextFocusDownId=target
        }
        scroll.scrollTo(0,0)
    }
    fun episodeState(count:Int,current:Int) {
        episodeLabel("第 ${current+1} / $count 集")
        episodePicker?.update(count,current)
    }

    init {
        orientation=VERTICAL; isClickable=true
        setPadding(dp(24),dp(24),dp(24),dp(20))
        background=MiniActGlass(this,28,.68f,opacity=.80f)
        val heading=activity.tvRow()
        back.visibility=View.GONE
        heading.addView(back,LayoutParams(dp(44),dp(44)).apply { rightMargin=dp(8) })
        heading.addView(title,LayoutParams(0,-2).apply { weight=1f })
        heading.addView(close,LayoutParams(dp(44),dp(44)))
        addView(heading,LayoutParams(-1,-2))
        val largeType=resources.configuration.fontScale>1.15f
        addView(contextLine,LayoutParams(-1,dp(if(largeType) 58 else 44)).apply { topMargin=dp(9) })
        listOf("播放","弹幕","更多").forEachIndexed { index,label ->
            val tab=TvButton(activity,label,ButtonSize.COMPACT,ButtonTone.GHOST,action={ showGroup(index); tabFocus(index) },pad=18,tabStyle=true)
            tab.onFocus={ focus -> if(focus && editing==null && group!=index) showGroup(index) }
            tabs+=tab; tabRow.addView(tab,LayoutParams(0,dp(if(largeType) 54 else 46)).apply { weight=1f })
            tab.setOnKeyListener { _,key,event ->
                if(event.action!=KeyEvent.ACTION_DOWN) false else when(key) {
                    KeyEvent.KEYCODE_DPAD_DOWN -> { showGroup(index); firstControl()?.requestFocus(); true }
                    KeyEvent.KEYCODE_DPAD_LEFT -> { tabs[(index-1).coerceAtLeast(0)].requestFocus(); true }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> { tabs[(index+1).coerceAtMost(tabs.lastIndex)].requestFocus(); true }
                    else -> false
                }
            }
        }
        addView(tabRow,LayoutParams(-1,-2).apply { topMargin=dp(12); bottomMargin=dp(12) })
        addView(activity.tvScrollHost(scroll),LayoutParams(-1,0).apply { weight=1f })
        addView(View(activity).apply { setBackgroundColor(0x18FFFFFF) },LayoutParams(-1,dp(1)).apply { topMargin=dp(14); bottomMargin=dp(12) })
        addView(footer,LayoutParams(-1,-2))
        close.nextFocusUpId=close.id; close.nextFocusRightId=close.id
        listOf(back,close).forEach { control ->
            control.setOnKeyListener { _,key,event ->
                if(event.action==KeyEvent.ACTION_DOWN && key==KeyEvent.KEYCODE_DPAD_DOWN && episodePicker!=null) {
                    episodePicker?.focusEntry(); true
                } else false
            }
        }
    }
    private fun category(title:String)=when {
        title.startsWith("弹幕") -> 1
        title=="解码" || title=="定时" -> 2
        else -> 0
    }
    private fun groupRows()=sections.indices.filter { category(sections[it].title)==group }
    private fun firstControl():View?=if(group==0) episodes else summaries[groupRows().firstOrNull()]
    private fun current(index:Int):String {
        val s=sections[index]
        return if(s.slider!=null) s.format(s.value) else s.choices.firstOrNull { it.selected }?.label ?: "自动"
    }
    fun nowPlaying(value:String) { nowPlaying=value; if(editing==null) contextLine.text=value }
    fun addRow(name:String,choices:List<PlaybackMenu.Choice>) {
        val index=sections.size; sections+=Section(name,choices)
        summaries[index]=Cell(name,current(index),icon(name)) { open(index) }
    }
    fun addSlider(name:String,min:Float,max:Float,step:Float,value:Float,format:(Float)->String,onValue:(Float)->Unit) {
        val index=sections.size
        val slider=SettingSlider(activity,name,min,max,step,value,{ next ->
            sections[index].value=next; summaries[index]?.value(current(index)); reading?.takeIf { editing==index }?.text=format(next); onValue(next)
        },format,ButtonSize.NORMAL,showName=false)
        sections+=Section(name,value=value,format=format,slider=slider)
        summaries[index]=Cell(name,current(index),icon(name)) { open(index) }
    }
    fun select(row:Int,index:Int) {
        val section=sections.getOrNull(row) ?: return
        section.choices.forEachIndexed { i,c -> c.selected=i==index }
        summaries[row]?.value(current(row))
        if(editing==row) options.forEachIndexed { i,cell -> cell.chosen(i==index) }
    }
    fun sliderValue(row:Int,value:Float) {
        val section=sections.getOrNull(row) ?: return
        section.value=value; section.slider?.value=value; summaries[row]?.value(current(row))
        if(editing==row) reading?.text=section.format(value)
    }
    fun focus(row:Int,choice:Int?=null) {
        if(row !in sections.indices) return
        group=category(sections[row].title)
        if(choice==null) { showGroup(group); focusSummary(row) } else open(row,choice)
    }
    fun wire() { showGroup(group) }
    fun back():Boolean {
        if(episodePicker?.back()==true) return true
        if(episodePicker!=null) { showGroup(0); episodes.requestFocus(); return true }
        val row=editing ?: return false
        group=category(sections[row].title); showGroup(group); focusSummary(row); return true
    }
    private fun tabFocus(index:Int) { tabs[index].requestFocus() }
    private fun focusSummary(index:Int?) { index?.let { summaries[it]?.requestFocus() } }
    private fun showGroup(index:Int) {
        group=index; editing=null; episodePicker=null; reading=null; options.clear()
        title.text="播放选项"; contextLine.text=nowPlaying; back.visibility=View.GONE; tabRow.visibility=View.VISIBLE
        footer.text="上下键选择 · 确认键调整 · 返回键关闭"
        tabs.forEachIndexed { i,tab -> tab.setSelectedState(i==group); tab.nextFocusUpId=close.id }
        body.removeAllViews()
        val rows=groupRows()
        rows.forEach { summaries.getValue(it).value(current(it)) }
        val cells=(if(group==0) listOf(episodes,favorite) else emptyList())+rows.map { summaries.getValue(it) }
        cells.forEachIndexed { i,cell ->
            (cell.parent as? ViewGroup)?.removeView(cell)
            body.addView(cell,LayoutParams(-1,dp(62)).apply { if(i>0) topMargin=dp(5) })
            cell.nextFocusUpId=if(i==0) tabs[group].id else cells[i-1].id
            cell.nextFocusDownId=cells.getOrNull(i+1)?.id ?: cell.id
            cell.nextFocusLeftId=tabs[group].id; cell.nextFocusRightId=cell.id
            cell.setOnKeyListener { _,key,event ->
                if(event.action!=KeyEvent.ACTION_DOWN) false else when(key) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> { tabs[group].requestFocus(); true }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> { cell.performClick(); true }
                    else -> false
                }
            }
        }
        close.nextFocusLeftId=tabs[group].id; close.nextFocusDownId=tabs[group].id
        scroll.scrollTo(0,0)
    }
    private fun open(row:Int,choice:Int?=null) {
        val section=sections.getOrNull(row) ?: return
        editing=row; episodePicker=null; reading=null; options.clear()
        title.text=section.title; contextLine.text=hint(section.title); back.visibility=View.VISIBLE; tabRow.visibility=View.GONE
        footer.text=if(section.slider==null) "上下键选择 · 确认键应用 · 返回键回上一级" else "左右键调节 · 返回键回上一级"
        body.removeAllViews()
        val slider=section.slider
        val focus:View?
        if(slider!=null) {
            reading=activity.tvText(section.format(section.value),46f).apply { gravity=Gravity.CENTER; typeface=Typeface.create("sans-serif-medium",0) }
            body.addView(reading,LayoutParams(-1,dp(92)).apply { topMargin=dp(38); bottomMargin=dp(22) })
            (slider.parent as? ViewGroup)?.removeView(slider)
            body.addView(slider,LayoutParams(-1,dp(76)))
            slider.nextFocusLeftId=slider.id; slider.nextFocusRightId=slider.id; slider.nextFocusUpId=back.id; slider.nextFocusDownId=slider.id
            focus=slider
        } else {
            section.choices.forEachIndexed { index,c ->
                val cell=Cell(c.label,"",null) { c.action() }.apply { chosen(c.selected) }
                options+=cell
                body.addView(cell,LayoutParams(-1,dp(58)).apply { if(index>0) topMargin=dp(6) })
            }
            options.forEachIndexed { i,cell ->
                cell.nextFocusUpId=options.getOrNull(i-1)?.id ?: back.id
                cell.nextFocusDownId=options.getOrNull(i+1)?.id ?: cell.id
                cell.nextFocusLeftId=back.id; cell.nextFocusRightId=cell.id
                cell.setOnKeyListener { _,key,event -> if(event.action==KeyEvent.ACTION_DOWN && key==KeyEvent.KEYCODE_DPAD_LEFT) { back(); true } else false }
            }
            val selected=choice ?: section.choices.indexOfFirst { it.selected }.coerceAtLeast(0)
            focus=options.getOrNull(selected) ?: options.firstOrNull()
        }
        back.nextFocusLeftId=back.id; back.nextFocusUpId=back.id; back.nextFocusRightId=close.id; back.nextFocusDownId=focus?.id ?: back.id
        close.nextFocusLeftId=back.id; close.nextFocusDownId=focus?.id ?: back.id
        scroll.scrollTo(0,0)
        focus?.requestFocus()
    }
    private fun hint(title:String)=when(title) {
        "清晰度" -> "按片源支持的档位播放，切换时可能短暂缓冲。"
        "倍速" -> "选择舒适的播放节奏。"
        "画面模式" -> "调整视频如何适应电视画面。"
        "画面缩放" -> "左右调整视频大小，弹幕保持原位。"
        "解码" -> "遇到花屏或音画异常时，可尝试更换解码方式。"
        "定时" -> "到时间后停止播放。"
        else -> "调整弹幕在画面中的显示效果。"
    }
    private fun icon(title:String)=when {
        title=="清晰度" -> R.drawable.ic_playback_quality
        title=="画面模式" -> R.drawable.ic_playback_frame
        title=="画面缩放" -> R.drawable.ic_playback_scale
        title=="解码" -> R.drawable.ic_playback_decoder
        title=="倍速" -> R.drawable.ic_menu_speed
        title.startsWith("弹幕") -> R.drawable.ic_menu_danmaku
        title=="定时" -> R.drawable.ic_menu_clock
        else -> R.drawable.ic_menu_frame
    }
    private inner class Cell(label:String,value:String,iconRes:Int?,action:()->Unit):FrameLayout(activity) {
        private val plate=MiniActControlSurface(this,radius=14)
        private val name=activity.tvText(label,MiniActTokens.body).apply { maxLines=1; ellipsize=TextUtils.TruncateAt.END; typeface=Typeface.create("sans-serif-medium",0) }
        private val summary=activity.tvText(if(value.isNotEmpty()) "$value  ›" else "",14f,TvStyle.muted).apply { gravity=Gravity.RIGHT or Gravity.CENTER_VERTICAL; maxLines=1; ellipsize=TextUtils.TruncateAt.END }
        private val glyph=iconRes?.let { activity.vectorDrawable(it)?.mutate() }
        private var selected=false
        init {
            id=View.generateViewId(); isFocusable=true; isFocusableInTouchMode=true
            background=plate
            val inset=if(glyph!=null) 50 else 18
            glyph?.let { d -> addView(ImageView(activity).apply { setImageDrawable(d); importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO },LayoutParams(dp(22),dp(22),Gravity.CENTER_VERTICAL).apply { leftMargin=dp(16) }) }
            addView(name,LayoutParams(-1,-1).apply { leftMargin=dp(inset); rightMargin=dp(if(value.isNotEmpty()) 122 else 50) })
            name.gravity=Gravity.CENTER_VERTICAL
            addView(summary,LayoutParams(dp(if(value.isNotEmpty()) 116 else 30),-1,Gravity.RIGHT).apply { rightMargin=dp(18) })
            contentDescription=if(value.isNotEmpty()) "$label，$value" else label
            setOnClickListener { action() }; setOnFocusChangeListener { _,_ -> paint() }; paint()
        }
        fun value(text:String) { summary.text="$text  ›"; contentDescription="${name.text}，$text" }
        fun actionState(label:String,value:String,chosen:Boolean) {
            name.text=label;summary.text=value;selected=chosen;contentDescription="$label，$value";paint()
        }
        fun chosen(value:Boolean) { selected=value; summary.text=if(value) "✓" else ""; contentDescription="${name.text}"+if(value) "，当前选择" else ""; paint() }
        private fun paint() {
            plate.appearance(isFocused,ghost=true)
            val color=if(isFocused) 0xFF182031.toInt() else if(selected) 0xFFEAD4AF.toInt() else 0xFFF1F3F8.toInt()
            name.setTextColor(color); summary.setTextColor(if(isFocused) 0xFF46536A.toInt() else if(selected) 0xFFEAD4AF.toInt() else 0xFFB9C3D3.toInt())
            glyph?.setColorFilter(color,PorterDuff.Mode.SRC_IN)
        }
    }
}
