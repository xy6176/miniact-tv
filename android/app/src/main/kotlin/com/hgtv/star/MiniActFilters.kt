// SPDX-License-Identifier: GPL-3.0-only
package com.hgtv.star

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.text.TextUtils
import android.view.*
import android.widget.*
import com.hgtv.star.core.*
import com.hgtv.star.list.showCatalog
import com.hgtv.star.tv.*

/** One quiet toolbar instead of exposing the entire selector schema above the films. */
internal fun MainActivity.miniActFilterBar(parent:LinearLayout) {
    val panel=categoryPanel
    val toolbar=row().apply {
        background=MiniActGlass(this,18,.68f)
        setPadding(dp(7),dp(5),dp(9),dp(5))
    }
    val controls=mutableListOf<View>()
    fun field(type:String,label:String,fallback:String,width:Int) {
        val value=panel?.labelOf(type,categorySelection.valueOf(type)) ?: fallback
        val field=FrameLayout(this).apply { id=View.generateViewId(); isFocusable=true; isFocusableInTouchMode=true }
        val material=MiniActSelection(field,12,0)
        material.state(false,ghost=true); field.background=material
        val caption=text(label,11f,TvStyle.muted)
        val title=text(value,16f,TvStyle.text).apply { typeface=Typeface.create("sans-serif-medium",0); maxLines=1; ellipsize=TextUtils.TruncateAt.END }
        val arrow=text("⌄",18f,TvStyle.textDim).apply { gravity=Gravity.CENTER }
        field.addView(caption,FrameLayout.LayoutParams(-1,dp(19)).apply { leftMargin=dp(16); rightMargin=dp(35); topMargin=dp(5) })
        field.addView(title,FrameLayout.LayoutParams(-1,dp(25)).apply { leftMargin=dp(16); rightMargin=dp(35); topMargin=dp(24) })
        field.addView(arrow,FrameLayout.LayoutParams(dp(30),-1,Gravity.RIGHT))
        field.contentDescription="$label，$value。按确定选择"
        field.setOnFocusChangeListener { _,focus ->
            material.state(focus,ghost=true)
            caption.setTextColor(if(focus) 0xFF586273.toInt() else TvStyle.muted)
            title.setTextColor(if(focus) 0xFF152034.toInt() else TvStyle.text)
            arrow.setTextColor(if(focus) 0xFF586273.toInt() else TvStyle.textDim)
        }
        field.setOnClickListener {
            panel?.row(type)?.let { miniActChooseFilter(it,field) } ?: showHint("筛选条件正在加载")
        }
        val mapping=categoryFilterButtons.getOrPut(type) { mutableMapOf() }
        mapping.clear(); mapping[""]=field
        panel?.row(type)?.options?.forEach { mapping[it.id]=field }
        toolbar.addView(field,lp(dp(width),dp(54))); controls+=field
    }
    field(CategorySelection.TYPE_THEME,"题材","全部题材",236)
    toolbar.addView(View(this).apply { setBackgroundColor(0x18FFFFFF) },lp(dp(1),dp(26)).apply { leftMargin=dp(10); rightMargin=dp(10) })
    field(CategorySelection.TYPE_SORT,"排序","综合推荐",206)
    toolbar.addView(View(this),lp(0,1).apply { weight=1f })
    val extras=panel?.rows.orEmpty().filter { it.type !in setOf(CategorySelection.TYPE_GENRE,CategorySelection.TYPE_THEME,CategorySelection.TYPE_SORT) }
    val count=extras.count { categorySelection.valueOf(it.type)!=null }
    val more=TvButton(this,if(count>0) "筛选 · $count" else "筛选",ButtonSize.COMPACT,ButtonTone.GHOST,action={
        if(extras.isEmpty()) showHint("暂无其他筛选条件")
        else miniActOptionSheet("更多筛选",extras.map { row -> row.title.removePrefix("全部")+"  ·  "+(row.labelOf(categorySelection.valueOf(row.type)) ?: "全部") },-1,controls.lastOrNull()) { index -> miniActChooseFilter(extras[index],controls.lastOrNull()) }
    },iconRes=R.drawable.ic_toolbar_settings)
    toolbar.addView(more,lp(-2,-2)); controls+=more
    extras.forEach { row -> categoryFilterButtons.getOrPut(row.type) { mutableMapOf() }.apply { clear(); put("",more); row.options.forEach { put(it.id,more) } } }
    val active=panel?.rows.orEmpty().any { it.type!=CategorySelection.TYPE_GENRE && categorySelection.valueOf(it.type)!=null }
    if(active) {
        val clear=TvButton(this,"清除",ButtonSize.COMPACT,ButtonTone.GHOST,action={
            categorySelection=CategorySelection(genre=categorySelection.genre)
            tabState.remove(TAB_CATEGORY); tabFocus.remove(TAB_CATEGORY); releaseCatalogViews()
            page=1; catalogFocus=""; chipFocusKey=categoryChipKey(CategorySelection.TYPE_THEME,null)
            showCatalog(load=true)
        })
        toolbar.addView(clear,lp(-2,-2)); controls+=clear
    }
    controls.forEachIndexed { index,view ->
        view.nextFocusLeftId=controls.getOrNull(index-1)?.id ?: navEntry()?.id ?: view.id
        view.nextFocusRightId=controls.getOrNull(index+1)?.id ?: view.id
        view.nextFocusUpId=navEntry()?.id ?: view.id
    }
    parent.addView(toolbar,lp(-1,dp(64)).apply { leftMargin=pageBleed(); rightMargin=pageBleed(); bottomMargin=dp(14) })
    registerTopAreaRow(toolbar)
    restoreChipFocus("filter:")
}
private fun MainActivity.miniActChooseFilter(row:CategoryFilterRow,anchor:View?) {
    val choices=listOf(CategoryFilterOption("","全部"))+row.options
    val selected=choices.indexOfFirst { it.id==categorySelection.valueOf(row.type).orEmpty() }
    miniActOptionSheet(row.title.removePrefix("全部"),choices.map { it.label },selected,anchor) { index ->
        val id=choices[index].id.takeIf { it.isNotEmpty() }
        if(id!=categorySelection.valueOf(row.type)) selectCategoryFilter(row.type,id)
    }
}
/** Compact four-column chooser with the same focus material as the rest of the app. */
private fun MainActivity.miniActOptionSheet(title:String,labels:List<String>,selected:Int,anchor:View?,pick:(Int)->Unit) {
    val panel=column().apply { clipChildren=true; clipToPadding=true; setPadding(dp(30),dp(26),dp(30),dp(22)); background=MiniActGlass(this,26,.72f) }
    panel.addView(text(title,25f,TvStyle.text).apply { typeface=Typeface.create("sans-serif-medium",0) })
    panel.addView(text("选择后更新结果",13f,TvStyle.muted),lp(-1,-2).apply { topMargin=dp(7); bottomMargin=dp(20) })
    val scroll=ScrollView(this).apply { isVerticalScrollBarEnabled=false }
    val grid=column(); scroll.addView(grid)
    val buttons=mutableListOf<View>()
    val dialog=AlertDialog.Builder(this).setView(panel).create()
    val columns=if(labels.size<=4) 2 else 4
    labels.chunked(columns).forEachIndexed { rowIndex,chunk ->
        val line=row()
        chunk.forEachIndexed { column,label ->
            val index=rowIndex*columns+column
            val option=text(label,15f,TvStyle.text).apply {
                id=View.generateViewId(); isFocusable=true; isFocusableInTouchMode=true
                gravity=Gravity.CENTER; maxLines=2; ellipsize=TextUtils.TruncateAt.END
                setPadding(dp(12),0,dp(12),0); typeface=Typeface.create("sans-serif-medium",0)
            }
            val plate=MiniActSelection(option,12,0)
            plate.state(false,index==selected); option.background=plate
            option.contentDescription=label+if(index==selected) "，已选择" else ""
            option.setOnFocusChangeListener { _,focus -> plate.state(focus,index==selected); option.setTextColor(if(focus) 0xFF152034.toInt() else TvStyle.text) }
            option.setOnClickListener { dialog.dismiss(); pick(index) }
            line.addView(option,lp(0,dp(48)).apply { weight=1f; if(column>0) leftMargin=dp(8) })
            buttons+=option
        }
        repeat(columns-chunk.size) { line.addView(View(this),lp(0,1).apply { weight=1f; leftMargin=dp(8) }) }
        grid.addView(line,lp(-1,-2).apply { if(rowIndex>0) topMargin=dp(8) })
    }
    val rows=(labels.size+columns-1)/columns
    panel.addView(scroll,lp(-1,minOf(dp(rows*56-8),dp(320))))
    panel.addView(text("方向键选择    确认应用    返回关闭",12f,TvStyle.muted),lp(-1,-2).apply { topMargin=dp(22) })
    dialog.setOnKeyListener { _,key,_ -> key==KeyEvent.KEYCODE_SEARCH }
    dialog.setOnDismissListener { if(anchor?.windowToken!=null && root.findFocus()==null) anchor.requestFocus() }
    dialog.show()
    dialog.window?.apply {
        decorView.setTag(R.id.miniact_glass_scene,window.decorView.getTag(R.id.miniact_glass_scene))
        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT)); setDimAmount(.5f)
        setLayout(dp(780).coerceAtMost(resources.displayMetrics.widthPixels-dp(80)),ViewGroup.LayoutParams.WRAP_CONTENT)
    }
    buttons.forEachIndexed { index,button ->
        button.nextFocusLeftId=buttons.getOrNull(index-1)?.id ?: button.id
        button.nextFocusRightId=buttons.getOrNull(index+1)?.id ?: button.id
        button.nextFocusUpId=buttons.getOrNull(index-columns)?.id ?: button.id
        button.nextFocusDownId=buttons.getOrNull(index+columns)?.id ?: button.id
    }
    buttons.getOrNull(selected.coerceAtLeast(0))?.requestFocus()
}
