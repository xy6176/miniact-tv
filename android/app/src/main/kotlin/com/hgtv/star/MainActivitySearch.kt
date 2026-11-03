// SPDX-License-Identifier: GPL-3.0-only
package com.hgtv.star

import android.app.Activity
import android.graphics.Typeface
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.*
import android.text.TextUtils
import com.hgtv.star.core.*
import com.hgtv.star.tv.IconButton
import com.hgtv.star.tv.ButtonTone
import com.hgtv.star.tv.MiniActControlSurface
import com.hgtv.star.compat.vectorDrawable
import com.hgtv.star.tv.ButtonSize
import com.hgtv.star.tv.SoftStyle
import com.hgtv.star.tv.ToggleChip
import com.hgtv.star.tv.TvStyle
import com.hgtv.star.tv.designPx
import com.hgtv.star.tv.designTextSize
import com.hgtv.star.list.gridCards
import com.hgtv.star.list.showCatalog
import com.hgtv.star.compat.attachedToWindow

// 本文件是从 MainActivity.kt 拆出来的「搜索页：输入框、热词与联想提示、搜索历史」这一块。
// 这些成员仍然属于 MainActivity，只是以扩展成员（MainActivity.xxx）的形式放在这里，
// 逻辑与拆之前逐行一致；改这里等于改 Activity 本体。

internal fun MainActivity.searchBar(top:LinearLayout,requestFocus:Boolean) {
    val material=TvStyle.miniactMaterial
    val searchBand=dp(TvStyle.SOFT_BAND)
    val input=EditText(this).apply {
        id=View.generateViewId()
        hint=if(material) "输入剧名或关键词" else SEARCH_HINT
        setText(query)
        designTextSize(SEARCH_INPUT_TEXT)
        typeface=Typeface.create(if(material) "sans-serif" else "sans-serif-medium",Typeface.NORMAL)
        setTextColor(TvStyle.text); setHintTextColor(if(material) 0xFFB5BDCD.toInt() else TvStyle.muted)
        isSingleLine=true; maxLines=1; includeFontPadding=false
        filters=arrayOf(android.text.InputFilter.LengthFilter(80))
        imeOptions=android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
        if(material) {
            setPadding(dp(23),dp(10),dp(65),dp(10))
            val plate=MiniActControlSurface(this,field=true,radius=18)
            plate.appearance(false); background=plate
            val searchIcon=vectorDrawable(R.drawable.ic_toolbar_search)?.mutate()
            searchIcon?.apply {
                setBounds(0,0,dp(21),dp(21))
                setColorFilter(0xFFD2DAE9.toInt(),android.graphics.PorterDuff.Mode.SRC_IN)
            }
            setCompoundDrawables(searchIcon,null,null,null); compoundDrawablePadding=dp(13)
            setOnFocusChangeListener { _,focused -> plate.appearance(focused) }
        } else {
            setPadding(dp(TvStyle.BUTTON_PAD_COMPACT)+searchBand,searchBand,dp(TvStyle.BUTTON_PAD_COMPACT)+searchBand,searchBand)
            background=softPanel(TvStyle.well,TvStyle.RADIUS_PILL,style=SoftStyle.INSET,ring=TvStyle.outline,ringWidth=1)
            setOnFocusChangeListener { view,focused ->
                view.background=softPanel(TvStyle.well,TvStyle.RADIUS_PILL,style=SoftStyle.INSET,
                    ring=if(focused) TvStyle.focusLine else TvStyle.outline,
                    ringWidth=if(focused) TvStyle.RING else 1,depth=if(focused) 1.6f else 1.1f)
            }
        }
        // EditText normally consumes up/down as cursor keys. Keep the remote route open.
        setOnKeyListener { view,keyCode,event ->
            if(event.action!=KeyEvent.ACTION_DOWN || (keyCode!=KeyEvent.KEYCODE_DPAD_UP && keyCode!=KeyEvent.KEYCODE_DPAD_DOWN)) false
            else {
                val target=when {
                    keyCode==KeyEvent.KEYCODE_DPAD_DOWN -> suggestButtons.firstOrNull(::canTakeFocusSoon)
                        ?: root.findViewById<View>(view.nextFocusDownId)?.takeIf(::canTakeFocusNow)
                        ?: gridCards().firstOrNull(::canTakeFocusNow) ?: navEntry()?.takeIf(::canTakeFocusNow)
                    else -> root.findViewById<View>(view.nextFocusUpId)?.takeIf(::canTakeFocusNow)
                        ?: navEntry()?.takeIf(::canTakeFocusNow)
                }
                if(target==null) false else {
                    (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(view.windowToken,0)
                    view.clearFocus(); target.requestFocus(); true
                }
            }
        }
    }
    val metrics=input.paint.fontMetricsInt
    val inputHeight=if(material) maxOf(dp(54),metrics.bottom-metrics.top+dp(22)) else dp(SEARCH_INPUT_HEIGHT+2*TvStyle.SOFT_BAND)
    val clear=if(material) IconButton(this,R.drawable.ic_menu_close,"清除关键词",diameter=28,quiet=true) { resetSearch() } else null
    if(clear!=null) {
        val slot=FrameLayout(this)
        slot.addView(input,FrameLayout.LayoutParams(-1,-1))
        slot.addView(clear,FrameLayout.LayoutParams(dp(42),dp(42),Gravity.RIGHT or Gravity.CENTER_VERTICAL).apply { rightMargin=dp(8) })
        top.addView(slot,lp(0,inputHeight).apply { weight=1f })
        searchActions+=clear
    } else top.addView(input,lp(dp(SEARCH_INPUT_WIDTH),inputHeight).apply {
        leftMargin=dp(TvStyle.GAP_BLOCK); rightMargin=dp(TvStyle.GAP_TIGHT)
    })
    fun search() {
        val next=input.text.toString().trim()
        if(next.isBlank()) {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(input.windowToken,0)
            showSearchHistory(input)
        } else runSearch(next)
    }
    searchInput=input
    searchButton=addButton(top,"搜索",tone=if(material) ButtonTone.PRIMARY else ButtonTone.SECONDARY,
        size=if(material) ButtonSize.NORMAL else ButtonSize.TIGHT,gap=if(material) 12 else SEARCH_BUTTON_GAP,
        icon=if(material) null else R.drawable.ic_toolbar_search) { search() }
    val phone=addButton(top,"手机输入",size=if(material) ButtonSize.NORMAL else ButtonSize.TIGHT,
        gap=if(material) 8 else SEARCH_BUTTON_GAP,icon=R.drawable.ic_action_mobile_input) {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(input.windowToken,0)
        tvTools.phoneInput(input) { word -> showHint("已从手机收到「$word」"); runSearch(word) }
    }
    searchActions+=phone
    input.nextFocusRightId=searchButton!!.id
    searchButton!!.nextFocusLeftId=input.id
    searchButton!!.nextFocusRightId=phone.id
    phone.nextFocusLeftId=searchButton!!.id
    phone.nextFocusRightId=phone.id
    fun syncClear() {
        if(clear==null) return
        val visible=input.text.isNotEmpty()
        clear.visibility=if(visible) View.VISIBLE else View.GONE
        input.nextFocusRightId=if(visible) clear.id else searchButton!!.id
        searchButton!!.nextFocusLeftId=if(visible) clear.id else input.id
        clear.nextFocusLeftId=input.id; clear.nextFocusRightId=searchButton!!.id
    }
    syncClear()
    if(!material) top.addView(Space(this),lp(0,1).apply { weight=1f })
    input.setOnEditorActionListener { _,_,event ->
        if(event?.action==KeyEvent.ACTION_DOWN) true else { search(); true }
    }
    input.addTextChangedListener(object:android.text.TextWatcher {
        override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
        override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) {}
        override fun afterTextChanged(s:android.text.Editable?) { syncClear(); scheduleSuggestions(s?.toString().orEmpty()) }
    })
    suppressSearchFocusOnce=false
    if(requestFocus) input.post { if(input.attachedToWindow) input.requestFocus() }
}
/** Legacy layouts reserve space for their inline navigation. The MiniAct sidebar layout
 * uses a weighted input instead, so it needs no second width-allocation pass. */
internal fun MainActivity.fitSearchRow(top: LinearLayout) {
    top.addOnLayoutChangeListener { view,left,_,right,_,_,_,_,_ ->
        val input=searchInput as? EditText ?: return@addOnLayoutChangeListener
        if((input.layoutParams as? LinearLayout.LayoutParams)?.weight?.let { it>0f }==true) return@addOnLayoutChangeListener
        val available=(right-left)-view.paddingLeft-view.paddingRight
        if(available<=0) return@addOnLayoutChangeListener
        var used=0
        for(index in 0 until top.childCount) {
            val child=top.getChildAt(index)
            if(child===input) continue
            val params=child.layoutParams as? LinearLayout.LayoutParams ?: continue
            // 弹性空白不占宽：它本来就是「剩下的那一段」，算进来会把输入框挤到 0。
            if(params.weight>0f) continue
            used+=child.measuredWidth+params.leftMargin+params.rightMargin
        }
        val params=input.layoutParams as? LinearLayout.LayoutParams ?: return@addOnLayoutChangeListener
        val room=available-used-params.leftMargin-params.rightMargin
        val width=when {
            room>=dp(SEARCH_INPUT_WIDTH) -> dp(SEARCH_INPUT_WIDTH)
            room>=dp(SEARCH_INPUT_MIN_WIDTH) -> room
            // 连下限都放不下（极窄屏 / 系统字号放大）：宁可输入框再窄一点，
            // 也不许把右边那排圆形入口顶出屏幕——它们才是这一行的固定成员。
            else -> room.coerceAtLeast(0)
        }
        if(params.width!=width) {
            params.width=width
            input.layoutParams=params
        }
    }
}

/**
 * 搜索页导航栏里的控件「按上键」都回到那一排圆形入口。
 *
 * 必须在**圆形入口建好之后**调：导航栏是先建标题与搜索控件、最后才加四个圆形入口的，
 * 建搜索控件时 `navEntry()` 还是 null，那时设上键会把焦点指向自己。
 */
internal fun MainActivity.wireHeadNavUp() {
    val up=navEntry()?.id ?: return
    searchInput?.nextFocusUpId=up
    searchButton?.nextFocusUpId=up
    searchActions.forEach { it.nextFocusUpId=up }
}
/**
 * 输入变化 → 去抖取提示词。
 *
 * 去抖 220ms：每敲一个字都发请求会把接口打满，也会让那一行一直闪。
 * 清空输入时立刻清掉那一行（不发请求）。
 */
internal fun MainActivity.scheduleSuggestions(text: String) {
    suggestRun.getAndSet(null)?.let { main.removeCallbacks(it) }
    val clean=text.trim()
    // 清空输入：换回热搜词（而不是留一行空白）。
    if(clean.isEmpty()) { suggestToken.incrementAndGet(); loadHotSearches(); return }
    val run=Runnable { fetchSuggestions(clean) }
    suggestRun.set(run)
    main.postDelayed(run,SUGGEST_DEBOUNCE)
}
/**
 * 焦点进入搜索结果**第二行**时，把输入框里的词清空。
 *
 * 第一行是「刚搜完、先扫一眼」的那一屏：输入框里留着自己搜的词，用户能确认
 * 「我搜的是这个」。往下走到第二行，说明他已经在看内容、准备换词了，
 * 这时清空才不打断阅读，按上键回到输入框也直接是空的。
 *
 * 按**行号**判断而不是数卡片：行号来自栅格自己（[MainActivity.GridRow.index]），
 * 不受列数、来回上下移动影响，也不会因为重复经过同一张卡而数错。
 *
 * 为什么只清控件、不清 [query]：这一份结果、页码、预取与「展示更多」全都按 [query]
 * 取下一页（见 `requestNextPage` / `catalogPageKey`）。清掉它，已经显示的结果就成了一份
 * 「没有关键词的列表」，按展示更多会去搜一个空词。
 *
 * 清空之后 TextWatcher 会把输入框下面那一行换回搜索记录（[loadHotSearches]）——
 * 那正是空输入时该显示的内容，也方便用户直接点回上一个词。
 */
internal fun MainActivity.noteSearchRowFocused(row: Int) {
    if(isDestroyed || screen!="catalog" || tab!=TAB_SEARCH) return
    if(row<1) return
    clearSearchBoxOnResults()
}
/**
 * 把输入框里的词清空（只清控件，其余状态一概不动）。
 *
 * 清掉之后用户按上键回到输入框就是空的，直接敲新词，不用一路删干净。
 * 输入框本来为空时什么都不做：焦点在卡片之间移动不会反复触发。
 */
internal fun MainActivity.clearSearchBoxOnResults() {
    // Browsing results must not erase the query or restart suggestions.
}
/**
 * 搜索页空输入时，输入框下面那一行铺**本机搜索记录**。
 *
 * 与提示词共用 [suggestRow] 那一行与同一套令牌：搜索记录是「没有输入」时的默认内容，
 * 一旦开始打字就换成提示词。纯本机数据，不走网络；没有记录时整行隐藏。
 *
 * 记录条数本来就限到 20 条（见 `SearchHistory.remember`），一行放不下时横向滚动，
 * 因此这里直接整份铺出来，不再截断。
 */
internal fun MainActivity.loadHotSearches() {
    suggestJob?.cancel(true)
    // 作废在途的联想请求：清空输入那一刻回来的旧结果不能盖住搜索记录。
    suggestToken.incrementAndGet()
    if(isDestroyed || screen!="catalog" || tab!=TAB_SEARCH) return
    renderHintRow(library.searches().map { it.query })
}
/**
 * 取一次提示词。
 *
 * 请求走自己的线程与令牌（不占用 [work] 那条「一次导航一个请求」的通道）：
 * 它是**边打字边发**的，绝不能把页面重排、更不能取消用户正在等的搜索。
 * 结果回来时先比对令牌与当前输入框内容——晚回来的旧结果直接丢掉。
 */
internal fun MainActivity.fetchSuggestions(keyword: String) {
    suggestJob?.cancel(true)
    val ticket=suggestToken.incrementAndGet()
    suggestJob=io.submit {
        val result=runCatching { repository.suggestions(keyword,SUGGEST_COUNT,library.searchSource) }
            .onFailure { DebugLog.record("搜索","提示词失败 · ${it.message.orEmpty().take(120)}") }
            .getOrDefault(emptyList())
        main.post {
            if(isDestroyed || screen!="catalog" || tab!=TAB_SEARCH) return@post
            if(ticket!=suggestToken.get()) return@post
            // 输入框里的词已经变了（用户又敲了字）：这一批已经过时。
            val current=(searchInput as? EditText)?.text?.toString()?.trim().orEmpty()
            if(current!=keyword) return@post
            renderHintRow(result.map { it.name })
        }
    }
}
/**
 * 重建输入框下面那一行（热搜词 / 提示词共用）。
 *
 * **只动这一行**：换关键词、热搜换成提示词都不重画整页，因此输入时页面不会闪。
 * 每一枚芯片都是**一个搜索词**：点了就把这个词填进输入框并搜（见 [runSearch]）——
 * 即使服务端在这一条上给了剧集信息（`short_play_name`），也不直接跳简介页：
 * 那一行是「搜索建议」，用户点它是想搜这个词，不是想跳过搜索结果。
 * 空列表时整行隐藏，不留空槽。
 */
/**
 * 导航栏里那几件（输入框 / 搜索 / 手机输入）的「按下」出口。
 *
 * **必须永远指向一个真的能接收焦点的控件。** 框架的 `FocusFinder` 拿到显式
 * `nextFocusDownId` 时会**直接返回那个视图**，不做任何可聚焦性检查；输入框在
 * `TextView.onKeyUp` 里对回车/确认的处理是「拿不到焦点就抛 IllegalStateException」
 * （`focus search returned a view that wasn't able to take focus!`）。
 * 也就是说，只要这条出口指向了已经 `GONE` 的提示词芯片，用户在搜索框按确认就崩溃。
 *
 * 因此这里逐级兜底，并且**每个候选都要现查 `isShown && isFocusable && isEnabled`**：
 * 内容第一张卡 → 搜索按钮 → 顶部圆形入口 → 输入框自己（指自己一定成功，最差就是按下不动）。
 * 指向自己不是偷懒：它保证「按下去没反应」而不是「按下去崩掉」。
 */
/**
 * 「不考虑尺寸」的可聚焦判断：可见、可用、可聚焦，且页头没收起。
 *
 * 提示词那一行专用。它是**异步铺出来的**：`renderHintRow` 里 `addView` 之后
 * 这一帧还没量过尺寸，用 [canTakeFocusNow] 的尺寸判据去筛会把它一次性筛掉，
 * 于是「按下」出口落到内容第一张卡上——用户按向下就跳过联想词直接进结果。
 *
 * 而「页头收起导致这一行 0 高」这种真正拿不到焦点的情况，由 [topAreaHidden]
 * 兜住（收起时该标志立刻置位），不需要再靠尺寸判断。
 */
private fun MainActivity.canTakeFocusSoon(view: View?): Boolean {
    val target=view ?: return false
    if(!target.isFocusable || !target.isEnabled) return false
    var node: View?=target
    while(node!=null) {
        if(node.visibility!=View.VISIBLE) return false
        if(node===suggestRow && topAreaHidden) return false
        if(node===root) break
        node=node.parent as? View
    }
    return true
}
/**
 * 这个控件**此刻**能不能真的拿到焦点。
 *
 * 比 `isShown` / `isFocusable` 更严的一条是尺寸：框架的 `View.canTakeFocus()`
 * 要求 `hasSize()`（宽高都 > 0），**尺寸为 0 的控件 `requestFocus` 必然返回 false**。
 * 而页头收起时（[applyTopAreaState] 把行高写成 0）提示词那一行正好就是这个状态——
 * 行还在、`visibility` 还是 VISIBLE、芯片自己也还是 focusable，但它量出来是 0 高。
 *
 * 尺寸判断只对**已经布局过**的控件做：页面是在挂上窗口之前建好的，那时尺寸都还是 0，
 * 用尺寸去卡会把所有候选一次性筛光（与 [navIcons] 不用 `isShown` 是同一个道理）。
 * 祖先也要一并查：提示词芯片自己的高度跟着父行一起被压成 0，只看它自己就漏了。
 */
private fun MainActivity.canTakeFocusNow(view: View?): Boolean {
    val target=view ?: return false
    if(!target.isFocusable || !target.isEnabled) return false
    // 页面还没布局过（刚建好、还没挂上窗口）时所有控件都是 0×0，这时不能拿尺寸判死。
    // 一旦根容器有了尺寸，就说明这一轮布局跑过了，0 高的控件是真的拿不到焦点。
    val laidOut=root.width>0 && root.height>0
    var node: View?=target
    while(node!=null) {
        if(node.visibility!=View.VISIBLE) return false
        if(laidOut && (node.width<=0 || node.height<=0)) return false
        if(node===suggestRow && topAreaHidden) return false
        if(node===root) break
        node=node.parent as? View
    }
    return true
}
/**
 * 导航栏里那几件（输入框 / 搜索 / 手机输入）的「按下」出口。
 *
 * **必须永远指向一个真的能接收焦点的控件。** 框架的 `FocusFinder` 在找
 * `nextFocusDownId` 时只检查「可聚焦 + 可见 + 触摸模式下可聚焦」，**不检查尺寸**；
 * 而输入框在 `TextView.onKeyUp` 里对回车/确认的处理是「拿不到焦点就抛
 * IllegalStateException」（`focus search returned a view that wasn't able to take focus!`）。
 * 也就是说：只要这条出口指向了一个 0 高的控件（页头收起时的提示词行就是），
 * 用户在搜索框按确认就崩溃。
 *
 * 因此这里逐级兜底，并且每个候选都现查 [canTakeFocusNow]：
 * 提示词芯片 → 内容第一张卡 → 搜索按钮 → 顶部圆形入口 → 输入框自己。
 * 指向自己不是偷懒：它保证「按下去没反应」而不是「按下去崩掉」。
 */
internal fun MainActivity.wireSearchHeadDown(target: View?) {
    // 提示词芯片刚 `addView` 时还没量过尺寸，用 [canTakeFocusNow] 会被尺寸判据筛掉；
    // 它们只在「页头收起」时才真的拿不到焦点，而那件事由 [topAreaHidden] 判断。
    // 因此行内目标走 [canTakeFocusSoon]，其余目标仍走严格判据。
    val inSuggestRow=target!=null && suggestRow!=null && run {
        var node: View?=target
        while(node!=null) { if(node===suggestRow) return@run true; node=node.parent as? View }
        false
    }
    val candidate=target?.takeIf { if(inSuggestRow) canTakeFocusSoon(it) else canTakeFocusNow(it) }
    val fallback=listOfNotNull(
        candidate,
        gridCards().firstOrNull(::canTakeFocusNow),
        searchButton?.takeIf(::canTakeFocusNow),
        navEntry()?.takeIf(::canTakeFocusNow),
        searchInput
    ).firstOrNull()
    val id=fallback?.id ?: View.NO_ID
    if(id==View.NO_ID) return
    searchInput?.nextFocusDownId=id
    searchButton?.nextFocusDownId=id
    searchActions.forEach { it.nextFocusDownId=id }
}
internal fun MainActivity.renderHintRow(items: List<String>) {
    val line=suggestLine ?: return
    val scroll=suggestRow ?: return
    // 显隐与高度都改**硬裁剪视口**（与分类页筛选行同一个类）：
    // 它是这一行真正的显示边界，滚动容器只是它的子控件。
    val viewport=suggestViewport
    suggestButtons.clear()
    line.removeAllViews()
    if(items.isEmpty()) {
        (viewport ?: scroll).visibility=View.GONE
        // 这一行收起来之后，导航栏里那几件按下键要落到内容第一张卡；没有内容时
        // 由 [wireSearchHeadDown] 逐级兜底——**绝不能留着指向刚被 GONE 掉的芯片**。
        wireSearchHeadDown(null)
        return
    }
    // 从 GONE 恢复：高度按固定档写回去（页头收展动画会改这个 LayoutParams，
    // 因此这里每次都重新写一遍，不能只改 visibility）。
    (viewport ?: scroll).layoutParams=((viewport ?: scroll).layoutParams as LinearLayout.LayoutParams).apply {
        height=dp(CHIP_ROW_HEIGHT)
    }
    val chips=mutableListOf<View>()
    items.forEach { label ->
        // 与分类页筛选行同一档芯片（DENSE：30dp 底板 / 13sp），
        // 一行能放下的条数更多，长关键词也不会一枚就吃掉半屏。
        val chip=ToggleChip(this,label,false,ButtonSize.DENSE) {
            // 点这一条：输入框里换成这个词，然后**搜索**它。
            (searchInput as? EditText)?.setText(label)
            runSearch(label)
        }
        chip.contentDescription="$label，搜索"
        val history=(searchInput as? EditText)?.text?.isEmpty()==true && library.searches().any { it.query==label }
        if(history) {
            fun manage() {
                tvTools.choose("搜索记录",listOf("搜索这条记录","删除这条记录"),chip,{ index ->
                    if(index==0) runSearch(label) else {
                        library.removeSearch(label); loadHotSearches()
                        searchInput?.requestFocus()
                    }
                },danger=setOf(1))
            }
            chip.contentDescription="$label，搜索。长按或菜单键管理这条记录"
            chip.setOnLongClickListener { manage(); true }
        }
        chips+=chip
        // 芯片之间用 6dp（与分类页筛选行一致）：它们是同一组候选，靠近一点才读得出「一组」。
        gapInto(line,chip,lp(-2,-2),6)
    }
    // 先让这一行可见，再接焦点链：下面 `wireSearchHeadDown` 会按 `isShown` 过滤，
    // 行还藏着的时候接出来只会落到兜底项上（按向下不进提示词行）。
    (viewport ?: scroll).visibility=View.VISIBLE
    // 左右在行内走（走到两端停在原地），向上回到输入框（它就在这一行正上方）。
    wireChipRow(chips,{ searchInput })
    chips.forEachIndexed { index,view ->
        val label=items[index]
        val history=(searchInput as? EditText)?.text?.isEmpty()==true && library.searches().any { it.query==label }
        if(history) view.setOnKeyListener { _,key,event ->
            when {
                key==KeyEvent.KEYCODE_MENU -> {
                    if(event.action==KeyEvent.ACTION_UP) view.performLongClick()
                    true
                }
                event.action!=KeyEvent.ACTION_DOWN -> false
                key==KeyEvent.KEYCODE_DPAD_LEFT -> { chips.getOrNull(index-1)?.requestFocus() ?: focusMiniActRail(); true }
                key==KeyEvent.KEYCODE_DPAD_RIGHT -> { chips.getOrNull(index+1)?.requestFocus(); true }
                key==KeyEvent.KEYCODE_DPAD_UP -> { searchInput?.requestFocus(); true }
                else -> false
            }
        }
    }
    // 向下进内容第一张卡；没有结果时停在原地，不会变成「按下没反应」。
    val first=gridCards().firstOrNull()?.id
    chips.forEach { it.nextFocusDownId=first ?: it.id }
    // 导航栏里那几件按下键先落进这一行（这一行就在它们正下方）。
    wireSearchHeadDown(chips.first())
    // 这一行是异步铺出来的，铺好的那一刻栅格可能还没建（那时只能指向自己）；
    // 等这一帧布局跑完再补一次，把下键接到内容第一张卡上。
    scroll.post {
        if(suggestRow!==scroll || (viewport ?: scroll).visibility!=View.VISIBLE) return@post
        gridCards().firstOrNull()?.let { card -> chips.forEach { it.nextFocusDownId=card.id } }
        // 这一帧布局跑完、芯片有了尺寸，再按真实可聚焦状态接一次「按下」出口：
        // 上面那次接线时芯片还是 0×0，会被尺寸判据筛掉，出口因此留在内容第一张卡上。
        wireSearchHeadDown(chips.first())
    }
    suggestButtons.addAll(chips)
}
internal fun MainActivity.runSearch(value: String) {
    val clean=value.trim(); if(clean.isEmpty() || clean.length>80) return
    (searchInput as? EditText)?.let { (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(it.windowToken,0) }
    library.rememberSearch(clean); query=clean; tab=1; page=1; catalogFocus=""; showCatalog(true)
}
/**
 * 搜索源：App / 官网。
 *
 * 只影响搜索这一件事（结果列表、联想词、热搜词）。切换之后下一次搜索就用新源，
 * 已经在屏幕上的那一页结果不会被就地换掉——用户按「搜索」才换。
 *
 * 两档的差别写进每一行的说明里：App 结果全、能一直翻页，但要求这台设备注册过；
 * 官网免登录，一次 10 条按页翻。
 */
internal fun MainActivity.showSearchSourcePicker(anchor: TextView) {
    val labels=SearchSource.entries.map { source ->
        if(source==library.searchSource) "${source.label} · ${source.hint}（当前）" else "${source.label} · ${source.hint}"
    }
    tvTools.choose("搜索源",labels,anchor,{ index ->
        val selected=SearchSource.entries[index]
        val changed=library.searchSource!=selected
        library.searchSource=selected
        anchor.text="搜索源  ·  ${selected.label}"
        anchor.contentDescription="搜索源，当前${selected.label}"
        if(changed) {
            showHint("搜索源已切换为${selected.label}，下次搜索生效")
            // 搜索页缓存的那一份结果来自**旧的源**：留着它会让「展示更多」接着问新源的第 2 页，
            // 两套数据拼在一起。丢掉这一页的缓存，回到搜索页时按新源重新取第 1 页。
            tabState.remove(TAB_SEARCH); tabFocus.remove(TAB_SEARCH)
            // 搜索页正开着且输入框为空：那一行是热搜词，换源之后重新铺一份。
            if(tab==TAB_SEARCH && (searchInput as? EditText)?.text?.toString()?.trim().isNullOrEmpty()) loadHotSearches()
        }
    },subtitle="只影响搜索，分类 / 榜单 / 播放不受影响")
}
internal fun MainActivity.showSearchHistory(anchor: View) {
    val history=library.searches()
    if(history.isEmpty()) { tvTools.info("搜索历史","搜索过的剧名会保存在这里，最多保留 20 条。",anchor); return }
    tvTools.choose("搜索历史",history.map { it.query },anchor,{ index -> runSearch(history[index].query) },"清空历史") {
        tvTools.confirm("清空搜索历史","清除本机保存的搜索词？",anchor,danger=true) { library.clearSearches() }
    }
}
/**
 * 本机记录 → 云端同步（尽力而为）。
 *
 * 账号参数可用时，本机的「收藏」与「观看记录」同时推给平台：收藏增删走
 * `bookshelf/add` / `bookshelf/video/update`，观看进度走 `read_progress/upload`
 * （`write_mode=1` 会一并写进云端历史）。没配参数、或请求失败都**静默放弃**：
 * 本机记录已经落盘，云端只是补一份，不该在遥控器上弹提示打断播放。
 *
 * **先改本地那份账号数据、再发网络请求**：账号侧的列表（「我的」页四栏、首页那两栏、
 * 集合页补在后面的那一段）都读 [accountData]，只在第一次进页面时联网取过一次。
 * 不就地改它的话，在本机收藏 / 播放 / 删记录之后再回到「我的」页，读到的还是那份旧快照——
 * 明明已经同步到云端了，页面上却还是旧样子。
 */

/** Clear the current query and results while retaining search history and account state. */
internal fun MainActivity.resetSearch() {
    if(screen!="catalog" || tab!=TAB_SEARCH) return
    (searchInput as? EditText)?.let {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(it.windowToken,0)
    }
    query=""; catalogFocus=""; page=1; catalog=emptyList(); hasMore=false; ranking=null
    tabState.remove(TAB_SEARCH); tabFocus.remove(TAB_SEARCH)
    showCatalog()
}

/** Distinguish title matches from the platform's fallback/alias results. */
internal fun MainActivity.updateSearchSummary(loading:Boolean=false) {
    val view=searchSummary ?: return
    if(query.isBlank()) { view.visibility=View.GONE; return }
    view.visibility=View.VISIBLE
    val matches=catalog.count { SearchRanking.matches(it.title,query) }
    view.text=when {
        loading && catalog.isEmpty() -> "正在查找剧集…"
        catalog.isEmpty() -> "未找到相关剧集"
        matches==0 -> "未找到剧名匹配 · 以下为平台返回的其他作品"
        matches==catalog.size -> "已加载 $matches 部剧名匹配的作品"
        else -> "已加载 $matches 部剧名匹配 · ${catalog.size-matches} 部其他相关作品"
    }
}
