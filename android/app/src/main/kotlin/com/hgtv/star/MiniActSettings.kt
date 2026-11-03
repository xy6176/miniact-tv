// SPDX-License-Identifier: GPL-3.0-only
package com.hgtv.star

import android.graphics.Typeface
import android.view.*
import android.widget.*
import com.hgtv.star.core.*
import com.hgtv.star.list.showCatalog
import com.hgtv.star.tv.*
import com.hgtv.star.compat.lowEndTv

/** TV settings are a readable list, with one value and one action per row. */
internal fun MainActivity.showMiniActSettings(parent: LinearLayout,focusNav: Boolean) {
    val sections=listOf("播放体验","遥控与导航","画面与性能","推荐与数据")
    settingsTab=settingsTab.coerceIn(sections.indices)
    settingsTabChips.clear()
    val shell=row().apply { gravity=Gravity.TOP; setPadding(pageBleed(),dp(10),pageBleed(),dp(12)) }
    parent.addView(shell,lp(-1,0).apply { weight=1f })
    val menu=column().apply { setPadding(0,dp(8),dp(24),0) }
    shell.addView(menu,lp(dp(192),-1))
    val pane=FrameLayout(this)
    shell.addView(pane,lp(0,-1).apply { weight=1f })
    var firstRow: View?=null
    var render: (Int)->Unit={}
    val chips=sections.mapIndexed { index,label ->
        NavigationButton(this,label) { settingsTab=index; render(index) }.also { item ->
            item.tag="miniact:settings-section:$index"
            menu.addView(item,lp(-1,dp(48)).apply { bottomMargin=dp(5) })
            settingsTabChips+=item
            item.setOnKeyListener { _,key,event ->
                if(event.action!=KeyEvent.ACTION_DOWN) false
                else when(key) {
                    KeyEvent.KEYCODE_DPAD_RIGHT -> { if(settingsTab!=index) { settingsTab=index; render(index) }; firstRow?.requestFocus(); true }
                    KeyEvent.KEYCODE_DPAD_UP -> { settingsTabChips.getOrNull(index-1)?.requestFocus() ?: focusMiniActRail(); true }
                    KeyEvent.KEYCODE_DPAD_DOWN -> { settingsTabChips.getOrNull(index+1)?.requestFocus(); true }
                    KeyEvent.KEYCODE_DPAD_LEFT -> { focusMiniActRail(); true }
                    else -> false
                }
            }
        }
    }
    menu.addView(text("选择设置分类\n按右键查看选项",12f,TvStyle.muted).apply { setLineSpacing(0f,1.5f); setPadding(dp(15),dp(24),0,0) })
    render={ index ->
        pane.removeAllViews(); firstRow=null
        chips.forEachIndexed { i,item -> item.setActive(i==index) }
        val scroll=ScrollView(this).apply { isVerticalScrollBarEnabled=false }
        settingsScroll=scroll
        val content=column().apply { setPadding(dp(6),0,dp(6),dp(20)) }
        scroll.addView(content); pane.addView(scroll,FrameLayout.LayoutParams(-1,-1))
        content.addView(text(sections[index],23f,TvStyle.text).apply { typeface=Typeface.create("sans-serif-medium",0) },lp(-1,-2).apply { bottomMargin=dp(8) })
        val subtitle=when(index) {
            0 -> "画质、连播与播放节奏。修改自动保存。"
            1 -> "选择启动页面，调整遥控按键。"
            2 -> "调整画面显示与界面效果。"
            else -> "调整推荐偏好，管理账号与本机记录。"
        }
        content.addView(text(subtitle,12f,TvStyle.muted),lp(-1,-2).apply { bottomMargin=dp(22) })
        val list=column().apply { setPadding(dp(6),dp(6),dp(6),dp(6)) }
        content.addView(list,lp(-1,-2))
        val rows=mutableListOf<View>()
        fun setting(title: String,note: String,value: String,tagValue: String="",action: (View,(String)->Unit)->Unit) {
            val cell=row().apply {
                id=View.generateViewId(); isFocusable=true; isFocusableInTouchMode=true
                tag=tagValue; setPadding(dp(22),dp(15),dp(20),dp(15))
                minimumHeight=dp(74)
            }
            val labels=column()
            val name=text(title,16f,TvStyle.text).apply { typeface=Typeface.create("sans-serif-medium",0) }
            val noteView=text(note,12f,TvStyle.muted).apply { maxLines=2; setLineSpacing(0f,1.2f); setPadding(0,dp(5),0,0) }
            labels.addView(name); labels.addView(noteView)
            cell.addView(labels,lp(0,-2).apply { weight=1f; rightMargin=dp(14) })
            val status=text(value,14f,TvStyle.textDim).apply { gravity=Gravity.RIGHT or Gravity.CENTER_VERTICAL; maxLines=2 }
            cell.addView(status,lp(dp(146),-2))
            val arrow=text("›",23f,TvStyle.muted).apply { gravity=Gravity.RIGHT }
            cell.addView(arrow,lp(dp(24),-2))
            cell.contentDescription="$title，$value。$note"
            val focusPlate=MiniActSelection(cell,14,0)
            focusPlate.state(false,ghost=true); cell.background=focusPlate
            cell.setOnFocusChangeListener { _,focus ->
                focusPlate.state(focus,ghost=true)
                name.setTextColor(if(focus) 0xFF111216.toInt() else TvStyle.text)
                noteView.setTextColor(if(focus) 0xFF555760.toInt() else TvStyle.muted)
                status.setTextColor(if(focus) 0xFF22242B.toInt() else TvStyle.textDim)
                arrow.setTextColor(if(focus) 0xFF555760.toInt() else TvStyle.muted)
            }
            cell.setOnClickListener { action(cell) { next -> status.text=next; cell.contentDescription="$title，$next。$note" } }
            if(rows.isNotEmpty()) list.addView(View(this).apply { setBackgroundColor(0x14FFFFFF) },lp(-1,dp(1)).apply { leftMargin=dp(22); rightMargin=dp(22) })
            list.addView(cell,lp(-1,-2)); rows+=cell
        }
        fun choose(title: String,labels: List<String>,selected: Int,anchor: View,onPick:(Int)->Unit) {
            val dialog=TvDialog(this,title).choices(labels,selected,onChoice=onPick)
            dialog.button("取消"); dialog.show(anchor)
        }
        fun toggle(title: String,note: String,get:()->Boolean,set:(Boolean)->Unit,tag:String="") {
            setting(title,note,if(get()) "开启" else "关闭",tag) { _,update -> set(!get()); update(if(get()) "开启" else "关闭") }
        }
        when(index) {
            0 -> {
                setting("默认画质","实际可用画质取决于片源。","${library.maxQuality}P") { anchor,update ->
                    val values=PlaybackQuality.options
                    choose("默认画质",values.map { "${it}P" },values.indexOf(library.maxQuality),anchor) { selected ->
                        library.maxQuality=values[selected]; sessionPlayback.onManualQualityChange(); update("${library.maxQuality}P")
                    }
                }
                setting("默认倍速","每次开始观看时使用。",PlaybackSpeed.label(library.playbackSpeed)) { anchor,update ->
                    val values=PlaybackSpeed.options
                    choose("默认倍速",values.map(PlaybackSpeed::label),values.indexOf(library.playbackSpeed),anchor) { selected -> library.playbackSpeed=values[selected]; update(PlaybackSpeed.label(library.playbackSpeed)) }
                }
                toggle("自动连播","追剧时播完一集继续下一集。",{library.autoNext},{ library.autoNext=it; if(!it) dropQueuedEpisode() })
                setting("快进与快退","左右键每次移动的时长。","${library.seekStep} 秒") { anchor,update ->
                    val values=listOf(5,10,15,20)
                    choose("快进与快退",values.map { "$it 秒" },values.indexOf(library.seekStep),anchor) { selected -> library.seekStep=values[selected]; update("${library.seekStep} 秒") }
                }
                setting("每页集数","在详情与播放面板中生效。","${library.episodePageSize} 集") { anchor,update ->
                    val values=listOf(10,20,30,40,50)
                    choose("每页集数",values.map { "$it 集" },values.indexOf(library.episodePageSize),anchor) { selected -> library.episodePageSize=values[selected]; update("${library.episodePageSize} 集") }
                }
            }
            1 -> {
                setting("启动页面","下次打开应用时进入所选页面。",if(library.launchRecommend) "推荐视频" else "精选主页","miniact:startup-page") { anchor,update ->
                    choose("启动页面",listOf("精选主页（默认）","推荐视频"),if(library.launchRecommend) 1 else 0,anchor) { selected ->
                        library.launchRecommend=selected==1
                        update(if(library.launchRecommend) "推荐视频" else "精选主页")
                    }
                }
                content.addView(text("浏览推荐：上下键换剧，确认键开始观看\n播放剧集：上下键切集，确认键暂停或继续，长按确认键打开播放选项\n浏览页面：返回键打开左侧菜单，右键进入内容区",12f,TvStyle.muted).apply { setLineSpacing(0f,1.6f); setPadding(dp(8),dp(20),dp(8),0) })
                PlayerKey.entries.forEach { key ->
                    setting(key.label,"仅用于剧集播放；浏览推荐时，上下键换剧、确认键开始观看。",library.keyMap.action(key).label) { anchor,update ->
                        showKeyActionPicker(key,anchor) { update(library.keyMap.action(key).label) }
                    }
                }
                setting("恢复默认按键","上/下切集，左右快进退，确认暂停。","恢复") { _,_ -> library.keyMap=KeyMap.DEFAULT; render(index); firstRow?.requestFocus() }
            }
            2 -> {
                setting("外观主题","背景与玻璃材质一起切换，选择自动保存。",MiniActThemes.current.label,"miniact:theme") { anchor,_ ->
                    val themes=MiniActThemes.all
                    choose("外观主题",themes.map { it.label+if(it.id==MiniActThemes.DEFAULT_ID) "（默认）" else "" },themes.indexOf(MiniActThemes.current),anchor) { selected ->
                        MiniActThemes.select(this,themes[selected].id)
                        miniBrowseScene?.applyTheme(MiniActThemes.current);root.glassScene()?.applyTheme(MiniActThemes.current)
                        settingsPendingFocus="miniact:theme"
                        main.post { showCatalog() }
                    }
                }
                setting("画面比例","完整显示可保留字幕与全部画面。",library.frameMode.label) { anchor,update ->
                    val values=VideoFrameMode.entries
                    choose("画面比例",values.map { it.label },values.indexOf(library.frameMode),anchor) { selected -> library.frameMode=values[selected]; update(library.frameMode.label) }
                }
                setting("视频解码","花屏或黑屏有声时可尝试软件解码。",library.decoderMode.label) { anchor,update ->
                    val values=VideoDecoderMode.entries
                    choose("视频解码",values.map { it.label },values.indexOf(library.decoderMode),anchor) { selected -> library.decoderMode=values[selected]; sessionPlayback.onManualDecoderChange(); update(library.decoderMode.label) }
                }
                toggle("减少动画",if(lowEndTv()) "当前设备自动启用性能优化。" else "保留明确焦点，减少移动与缩放。",{lowEndTv() || library.reduceAnim},{ library.reduceAnim=it; applyPerformanceProfile() },SETTINGS_TAG_REDUCE_ANIM)
                toggle("减少透明效果","使用更实的面板，提升文字对比度。",{glassReduced()},{ value ->
                    getSharedPreferences("miniact_materials",android.content.Context.MODE_PRIVATE).edit().putBoolean("reduce_transparency",value).apply()
                    root.glassScene()?.setReduced(value)
                })
                toggle("显示剧名","正式播放时显示顶部剧名。",{library.playerTitle},{library.playerTitle=it})
                toggle("显示集数","正式播放时显示当前集数。",{library.playerEpisode},{library.playerEpisode=it})
                setting("设备类型","默认自动识别电视与遥控器。",deviceMode().label,SETTINGS_TAG_DEVICE_FORM) { anchor,_ ->
                    val values=listOf("auto","tv","tablet","phone")
                    choose("设备类型",listOf("自动识别","电视","平板","手机"),values.indexOf(library.deviceForm).coerceAtLeast(0),anchor) { selected ->
                        library.deviceForm=values[selected]; applyDeviceForm(); settingsPendingFocus=SETTINGS_TAG_DEVICE_FORM
                        main.post { showCatalog() }
                    }
                }
            }
            else -> {
                setting("推荐模式","综合推荐兼顾横竖屏；横屏优先调整顺序，仍保留其他画幅。",if(miniTv.preferLandscape) "横屏优先" else "综合推荐", "miniact:recommend-mode") { anchor,update ->
                    choose("推荐模式",listOf("综合推荐（默认）","横屏优先"),if(miniTv.preferLandscape) 1 else 0,anchor) { selected ->
                        val landscape=selected==1
                        if(miniTv.preferLandscape!=landscape) {
                            miniTv.preferLandscape=landscape
                            resetMiniActRecommendations()
                            showHint("推荐偏好已更新，下次进入推荐时生效")
                        }
                        update(if(landscape) "横屏优先" else "综合推荐")
                    }
                }
                listOf("short_play" to "真人短剧","comic_series" to "漫剧","ai_series" to "AI 剧").forEach { (id,label) ->
                    toggle("推荐$label","至少保留一种内容；下次进入推荐时生效。",{library.recommendGenreEnabled(id)},{ on ->
                        val previous=library.recommendGenreEnabled(id)
                        library.setRecommendGenre(id,on)
                        if(!on && library.recommendGenreEnabled(id)) showHint("至少保留一种推荐内容")
                        if(previous!=library.recommendGenreEnabled(id)) resetMiniActRecommendations()
                    })
                }
                setting("账号与登录","管理云端账号与片单。",if(repository.signedIn) "已登录" else "未登录") { _,_ -> switchTab(TAB_PROFILE) }
                setting("备份与恢复","通过同一网络的手机导出或恢复片单。","打开") { anchor,_ ->
                    runCatching { LibraryBackup.encode(library.snapshot()) }.onSuccess { encoded ->
                        tvTools.libraryTransfer(encoded,anchor) { payload,restoreSettings ->
                            favoriteMonitor.stop(); library.restore(payload,restoreSettings); applyPerformanceProfile()
                            tabState.clear(); tabFocus.clear(); catalogFocus=""; page=1
                            showCatalog(); favoriteMonitor.check()
                        }
                    }.onFailure { tvTools.info("无法生成备份","请稍后重试。",anchor) }
                }
                setting("本机数据","历史、收藏与偏好；重置前会再次确认。","管理") { anchor,_ -> clearLocalData(anchor) }
                setting("关于 MiniAct","基于 HGTV 的独立客户端。","${BuildConfig.VERSION_NAME}") { anchor,_ ->
                    val dialog=TvDialog(this,"MiniAct").message("好故事，不落幕。\n\n基于 HGTV v1.2.9.1，遵循 GPL-3.0-only。MiniAct 重新设计电视界面与遥控交互；内容与播放能力沿用上游。\n\n本应用不是内容平台官方客户端。源码随安装包一并提供。\n\n感谢 HGTV、N3urda/hongguoTV 及相关开源项目的贡献者。")
                    dialog.button("知道了"); dialog.show(anchor)
                }
            }
        }
        firstRow=rows.firstOrNull()
        rows.forEachIndexed { i,view ->
            view.nextFocusLeftId=chips[index].id
            view.nextFocusUpId=rows[(i-1).coerceAtLeast(0)].id
            view.nextFocusDownId=rows[(i+1).coerceAtMost(rows.lastIndex)].id
        }
    }
    render(settingsTab)
    val pending=settingsPendingFocus
    settingsPendingFocus=null
    if(focusNav) navKeyed["settings"]?.requestFocus()
    else parent.post { (pending?.let { findTagged(root,it) } ?: chips[settingsTab]).requestFocus() }
}
