// SPDX-License-Identifier: GPL-3.0-only
package com.hgtv.star.list

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.*
import android.widget.*
import com.hgtv.star.core.*
import com.hgtv.star.compat.onScrollYChanged
import com.hgtv.star.compat.attachedToWindow
import com.hgtv.star.tv.ButtonSize
import com.hgtv.star.showCollection
import com.hgtv.star.showMiniActSettings
import com.hgtv.star.updateSearchSummary
import com.hgtv.star.miniActLibraryBar
import com.hgtv.star.miniActHeader
import com.hgtv.star.miniActFilterBar
import com.hgtv.star.miniActPageIntro
import com.hgtv.star.miniActSearchDiscovery
import com.hgtv.star.tv.NavigationButton
import com.hgtv.star.tv.IconButton
import com.hgtv.star.tv.ToggleChip
import com.hgtv.star.openRecommend
import com.hgtv.star.findTagged
import com.hgtv.star.tv.TvStyle
import com.hgtv.star.tv.focusBleed
import com.hgtv.star.tv.tvScrollHost
import com.hgtv.star.ACCOUNT_TABS
import com.hgtv.star.CHIP_ROW_HEIGHT
import com.hgtv.star.FILTER_ROW_HEIGHT
import com.hgtv.star.gridColumns
import com.hgtv.star.LOAD_MORE
import com.hgtv.star.MainActivity
import com.hgtv.star.TAB_CATEGORY
import com.hgtv.star.TAB_HOME
import com.hgtv.star.TAB_PROFILE
import com.hgtv.star.TAB_RANK
import com.hgtv.star.TAB_SEARCH
import com.hgtv.star.TAB_SETTINGS
import com.hgtv.star.accountItems
import com.hgtv.star.accountNotice
import com.hgtv.star.registerDeviceOnce
import com.hgtv.star.addButton
import com.hgtv.star.appendCatalog
import com.hgtv.star.autoAppendCatalog
import com.hgtv.star.ACCOUNT_LOGIN_MODES
import com.hgtv.star.base
import com.hgtv.star.noteDebugProblem
import com.hgtv.star.deviceRejectionOf
import com.hgtv.star.healDeviceIdentity
import com.hgtv.star.clearDebugProblem
import com.hgtv.star.chipControl
import com.hgtv.star.column
import com.hgtv.star.dp
import com.hgtv.star.firstHeadControl
import com.hgtv.star.categoryDescription
import com.hgtv.star.categoryFilterWidth
import com.hgtv.star.fitSearchRow
import com.hgtv.star.focusHead
import com.hgtv.star.fullList
import com.hgtv.star.gapInto
import com.hgtv.star.NAV_KEYS
import com.hgtv.star.NAV_KEY_RECOMMEND
import com.hgtv.star.gridContentWidth
import com.hgtv.star.homeShelves
import com.hgtv.star.inlineHeadChips
import com.hgtv.star.invalidatePage
import com.hgtv.star.loadAccount
import com.hgtv.star.refreshAccountFavorites
import com.hgtv.star.loadHotSearches
import com.hgtv.star.localCatalog
import com.hgtv.star.localItems
import com.hgtv.star.lp
import com.hgtv.star.message
import com.hgtv.star.historyClearButton
import com.hgtv.star.navEntry
import com.hgtv.star.navIcons
import com.hgtv.star.pageBleed
import com.hgtv.star.pageTitle
import com.hgtv.star.profileHead
import com.hgtv.star.profileHeadControl
import com.hgtv.star.releaseCatalogViews
import com.hgtv.star.renderHome
import com.hgtv.star.restoreChipFocus
import com.hgtv.star.row
import com.hgtv.star.scheduleSuggestions
import com.hgtv.star.searchBar
import com.hgtv.star.settings
import com.hgtv.star.SETTINGS_TABS
import com.hgtv.star.SETTINGS_TAG_TAB_PREFIX
import com.hgtv.star.SETTINGS_CHIP_INLINE_MIN_WIDTH
import com.hgtv.star.widthDp
import com.hgtv.star.switchSettingsTab
import com.hgtv.star.showHint
import com.hgtv.star.selectLoginMode
import com.hgtv.star.switchAccountTab
import com.hgtv.star.selectCategoryFilter
import com.hgtv.star.switchRankBoard
import com.hgtv.star.switchTab
import com.hgtv.star.text
import com.hgtv.star.wireChipRow
import com.hgtv.star.wireHeadNavUp
import com.hgtv.star.work
import com.hgtv.star.error

// 本文件是从 MainActivity.kt 拆出来的「列表页（分类 / 搜索 / 榜单）整页渲染与栅格排布」这一块。
// 这些成员仍然属于 MainActivity，只是以扩展成员（MainActivity.xxx）的形式放在这里，
// 逻辑与拆之前逐行一致；改这里等于改 Activity 本体。

/**
 * 把一排芯片拉成**同宽**：按各自自然宽量一次，取最宽的那个给每一枚都设上。
 *
 * 为什么取最宽而不是最窄：「二维码登录」「手机登录」只差一个字，取最宽时短的那一枚两侧
 * 只是留白变宽、文字一个不少；取最窄则长的那一枚会被省略号截掉一个汉字，
 * 两个入口反而读不出区别。量宽用 `UNSPECIFIED`（芯片还没进父容器也能量），
 * 量完把 `LayoutParams.width` 改成固定值——再按 `WRAP_CONTENT` 排回去就前功尽弃了。
 */
private fun equalChipWidths(chips: List<View>) {
    if(chips.size<2) return
    val spec=View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED)
    val widest=chips.maxOf { chip -> chip.measure(spec,spec); chip.measuredWidth }
    if(widest<=0) return
    chips.forEach { chip ->
        val params=chip.layoutParams as? LinearLayout.LayoutParams ?: return@forEach
        if(params.width==widest) return@forEach
        params.width=widest
        chip.layoutParams=params
    }
}

internal fun MainActivity.showCatalog(load: Boolean=false,focusNav: Boolean=false,focusType: Boolean=false,keepChip: Boolean=false) {
    // 推荐页是独立的一层（整屏播放、没有列表）：后台的账号同步 / 会话失效之类
    // 顺手调过来的重建不能把用户从推荐页踢回列表。要离开推荐页只有两条路：
    // 返回键（[exitRecommendToHome]）与确定键（[enterFormalPlayback]），两者都先把 screen 改掉。
    if(screen=="recommend") return
    if(collection=="favorites" || tab==TAB_PROFILE) refreshAccountFavorites(entering=true)
    // 不取远端内容的这一次重建没有「内容落地」这一步，意图要清掉：
    // 否则它会留到下一次无关的落地，把焦点莫名其妙地推到第一张卡上。
    //
    // [keepChip] 是例外：「我的」页切栏目（收藏 / 历史 / 点赞 / 预约）内容全在内存里，
    // 不需要联网，但焦点必须**留在刚点的那枚芯片上**。少了这个开关，这次重建会把
    // [chipFocusKey] 清掉，焦点于是跟着栅格兜底跑到第一张卡上——用户读到的就是
    // 「切一栏焦点就跳进内容区」。
    if(!load && !keepChip) { focusFirstCardOnLanding=false; chipFocusKey=null }
    initialCatalogPending=false; restoreStoppedPage=false; pendingDetail=null
    // 榜单页与搜索结果从第 2 页起是「向下追加」：保留已经显示的剧集，只把新的一页接在后面。
    val appending=load && appendCatalog() && page>1 && catalog.isNotEmpty()
    // 首页的四个剧架不是「一页列表」：它们来自四个榜单各自的第 1 页，
    // 因此首屏先把上次缓存铺上（四个榜单各一份），后台再更新。
    val home=tab==TAB_HOME && collection==null && page==1
    // Never cache a new page number with the previous page's results while a request is pending.
    if(load) {
        if(home) {
            val cached=RankBoard.entries.mapNotNull { board -> library.cachedHome(board)?.let { board to it } }.toMap()
            homeFeed=LinkedHashMap(cached)
            homeFromCache=cached.isNotEmpty()
            catalog=emptyList(); hasMore=false; ranking=null
        } else if(!appending && chipFocusKey==null) { catalog=emptyList(); hasMore=false; ranking=null }
    }
    // 换页（换榜单 / 换分类 / 换标签）时把「连取」预算归零：新列表从头开始算。
    if(!keepTopAreaState) prefetchChain=0
    invalidatePage(); screen="catalog"; releaseCatalogViews(); val container=base()
    // 换榜单 / 换分类的请求还在路上时切走了标签（或在搜索页重新搜）：那一次的落地会被
    // [invalidatePage] 作废，落点意图因此永远等不到消费，必须在这里清掉——
    // 留着它会让新页面一直「芯片握着焦点」（不预取下一页、不恢复位置）。
    // 判定按「这一页认不认这枚芯片」来：分类页的意图不能跟到榜单页上，反之亦然。
    val ownsChip=if(collection!=null) chipFocusKey?.startsWith("library:")==true else when(tab) {
        TAB_RANK -> chipFocusKey?.startsWith("board:")==true
        TAB_CATEGORY -> chipFocusKey?.startsWith("filter:")==true
        TAB_PROFILE -> chipFocusKey?.startsWith("account:")==true
        else -> false
    }
    if(!ownsChip) chipFocusKey=null
    if(foreground && library.isLoaded && !favoriteMonitor.running) main.post { if(foreground && screen=="catalog") favoriteMonitor.check() }
    // 顶部导航栏：左侧是页面名（首页是品牌「HGTV」，二级界面与榜单页显示自己的名字），
    // 右侧是这一页自己的动作与 4 个圆形图标入口（刷新 / 搜索 / 排行榜 / 设置）。
    // 整条**不画底板**：没有边框、也没有可以按进去的面板，文字直接落在内容线上；
    // 品牌是标识而不是按钮，因此不参与焦点链（不可选中、不可点击），回主界面一律走返回键。
    // 高度固定 TOOLBAR_HEIGHT：页头收展动画直接改这一行的高度，不用再跟 minimumHeight 抢。
    val top=miniActHeader(container)
    registerTopAreaRow(top)
    val filterSlot=row()
    if(collection!=null) miniActLibraryBar(filterSlot)
    when {
        tab==TAB_PROFILE -> miniActPageIntro(container,"账号与片单","喜欢的故事，在这里接着看。")
        collection=="history" -> miniActPageIntro(container,"继续上次的故事","最近看过的短剧 · 长按确定管理记录")
        collection=="favorites" -> miniActPageIntro(container,"我的收藏","留住喜欢的故事，随时回来接着看。")
        collection=="later" -> miniActPageIntro(container,"稍后观看","先留在这里，想看时再开始。")
        tab==TAB_SEARCH -> miniActPageIntro(container,"搜索","找到你想看的下一部。")
        tab==TAB_SETTINGS -> miniActPageIntro(container,"让观影更顺手","账号、播放与遥控偏好")
        tab==TAB_RANK -> miniActPageIntro(container,"正在热播","从真实榜单里，找到下一部好剧。")
        tab==TAB_CATEGORY -> miniActPageIntro(container,when(categorySelection.genre) {
            "short_play" -> "真人剧"; "ai_series" -> "AI 剧"; else -> "漫剧"
        },"发现热门与新作")
    }
    filterSlot.setPadding(pageBleed(),0,pageBleed(),0)
    container.addView(filterSlot,lp(-1,-2))
    registerTopAreaRow(filterSlot)
    if(collection!=null) restoreChipFocus("library:")
    filterSlot.post { filterSlot.visibility=if(filterSlot.childCount==0) View.GONE else View.VISIBLE }
    var searchLine: LinearLayout?=null
    if(tab==TAB_SEARCH) {
        val line=row().apply { setPadding(pageBleed(),0,pageBleed(),dp(8)) }
        searchBar(line,query.isBlank() && !load && !focusNav)
        container.addView(line,lp(-1,-2))
        registerTopAreaRow(line)
        searchLine=line
        wireHeadNavUp()
        searchSummary=text("",12f,TvStyle.muted).apply {
            maxLines=1; ellipsize=android.text.TextUtils.TruncateAt.END
            setPadding(pageBleed(),dp(2),pageBleed(),dp(6))
            visibility=if(query.isBlank()) View.GONE else View.VISIBLE
        }.also { summary -> container.addView(summary,lp(-1,-2)); registerTopAreaRow(summary) }
        updateSearchSummary(loading=load)
    }
    // 搜索页这一行的宽度按实测分配：输入框只拿「圆形入口与按钮之外剩下的那一段」，
    // 因此无论屏多宽，右边那排圆形入口都不会被顶出屏幕（见 [fitSearchRow]）。
    searchLine?.let { fitSearchRow(it) }
    // 页面内提示行：应用里不弹系统 Toast，需要「刚刚发生了什么」这类反馈时显示这一行淡字。
    // 它不参与焦点、不盖画面，几秒后自己消失；页面重建时按 [catalogHint] 复原。
    val hintView=text("",14f,TvStyle.textDim).apply {
        setPadding(pageBleed(),dp(4),pageBleed(),dp(2))
        visibility=if(catalogHint==null) View.GONE else View.VISIBLE
        text=catalogHint.orEmpty()
    }
    catalogHintView=hintView
    container.addView(hintView,lp(-1,-2))
    // 「我的」页的身份行：圆形头像 + 昵称 + 退出 / 刷新。
    // 放在导航栏下面（与分类页的二级分类同一层）：上面一行是「看哪一栏」，这一行是「这是谁」。
    if(tab==TAB_PROFILE) {
        // 未登录：这一页就是登录界面。
        //
        // 身份行整行不建——没有账号就没有「这是谁」可显示，留一个灰头像和一句「未登录」
        // 只是把登录卡往下顶，用户还得先读一行无用的字。四个栏目芯片同样不建。
        // 登录卡放进一块占满剩余高度的舞台里，**水平垂直都居中**，一眼就是「这一页让你登录」。
        if(library.account().takeIf { it.configured }==null) {
            // 登录方式那两枚入口放进**顶部导航栏**（标题右边、圆形入口左边），与历史集合页
            // 的「已看完 / 未看完」同一格：这一页顶部只有「这是谁 / 怎么登录」这件事要说，
            // 单独占一行会把它推到页面中间，看起来像内容而不是入口。
            // 默认选中的是 [MainActivity.loginMode]（首次进来就是二维码）。
            val modeChips=mutableListOf<View>()
            loginModeChips.clear()
            ACCOUNT_LOGIN_MODES.forEach { (mode,label) ->
                val chip=ToggleChip(this,label,loginMode==mode) { selectLoginMode(mode,loginModeChips[mode]) }
                loginModeChips[mode]=chip
                gapInto(filterSlot,chip,lp(-2,-2),TvStyle.GAP_INLINE)
                modeChips+=chip
            }
            // 两枚入口**同宽**。
            //
            // 「二维码登录」五个字、「手机登录」四个字：按内容宽排出来的一长一短，
            // 并排在这一格里读起来像两枚不同档的控件（宽度差正好一个汉字 + 一段内边距）。
            // 这里按两枚各自的自然宽量一次，取**宽的那一个**给两枚都设上：
            // 短的那一枚两侧留白变宽，长的那一枚文字不会被省略，两枚因此完全对齐。
            equalChipWidths(modeChips)
            // 两枚之间左右直连、按上停在原地；最后一枚往右接同一行里的圆形入口。
            // 不接的话右移会被更右边的圆形入口抢走，表现出来就是「右键切不动」。
            wireChipRow(modeChips,{ modeChips[it] },recommendIcon ?: nav.firstOrNull())
            val panelHost=column().apply {
                visibility=View.GONE
                gravity=Gravity.CENTER_HORIZONTAL
                clipChildren=false
                clipToPadding=false
            }
            accountPanelHost=panelHost
            val stage=FrameLayout(this).apply {
                clipChildren=false
                clipToPadding=false
                addView(panelHost,FrameLayout.LayoutParams(-2,-2,Gravity.CENTER))
            }
            container.addView(stage,lp(-1,0).apply { weight=1f })
            // 建面板 + 接焦点链一起走 [selectLoginMode]：芯片的按下键、导航栏的按下键、
            // 面板里第一个可聚焦控件三处的接线只维护一份。
            selectLoginMode(loginMode,loginModeChips[loginMode] ?: modeChips.firstOrNull())
            // 页面重建后焦点是空的（旧控件已经离树）：落在**当前选中的那枚登录方式**上，
            // 与分类 / 榜单页「首次进来先落在页头那枚芯片」同一条规则——用户第一眼看到
            // 的两个入口就是「二维码 / 手机号」，按向下才进登录卡。
            // 只在真的没有焦点时才补，不抢用户已经放好的位置。
            val landing=loginModeChips[loginMode]
            if(landing!=null) landing.post {
                if(!landing.attachedToWindow) return@post
                if(root.findFocus()==null) focusHead(landing)
            }
            return
        }
        val headRow=row().apply { setPadding(pageBleed(),0,pageBleed()-dp(TvStyle.SOFT_BAND),dp(4)) }
        profileHead(headRow)
        profileHeadRow=headRow
        container.addView(headRow); registerTopAreaRow(headRow)
        // 手机号登录 / 导入账号参数的内嵌面板：就在身份行下方，默认收起。
        // 以前这里是两个弹窗；改成页面内的一块之后，选哪个就在下面显示哪个，不再挡住列表。
        val panelHost=column().apply { visibility=View.GONE }
        accountPanelHost=panelHost
        container.addView(panelHost,lp(-1,-2).apply { topMargin=dp(TvStyle.GAP_INLINE) })
    }
    if(tab==TAB_SETTINGS) {
        showMiniActSettings(container,focusNav)
        return
    }
    if(tab==TAB_PROFILE) {
        val chips=mutableListOf<View>()
        ACCOUNT_TABS.forEach { entry ->
            val chip=ToggleChip(this,entry.second,accountTab==entry.first) { switchAccountTab(entry.first) }
            accountButtons[entry.first]=chip
            chips+=chip
        }
        // 芯片在导航栏那一行时，往右走到最后一枚接同一视觉行里紧跟着的那排圆形入口。
        val afterChips=if(inlineHeadChips()) recommendIcon ?: nav.firstOrNull() else null
        wireChipRow(chips,{ chips[it] },afterChips)
        if(inlineHeadChips()) {
            chips.forEach { gapInto(filterSlot,it,lp(-2,-2),TvStyle.GAP_INLINE) }
        } else {
            val chipRow=row().apply { setPadding(pageBleed(),0,pageBleed(),dp(4)) }
            chips.forEach { gapInto(chipRow,it,lp(-2,-2)) }
            container.addView(chipRow); registerTopAreaRow(chipRow)
        }
        // 导航栏那一行的按下键一律落到**身份行**（头像 / 「导入账号参数」/ 重新导入 …），
        // 那一行就在导航栏正下方。指到同一行的芯片上会让焦点在导航栏那一行里打转，
        // 指到内容第一张卡上又会跳过身份行——未登录时这里只有「导入账号参数」一个入口，
        // 用户读到的就是「焦点在顶部的按钮上，按向下到不了导入按钮」。
        // 芯片同一条：按下键也进身份行，不再指向自己（内容为空时没有任何后续接线能改它）。
        val headDown=profileHeadControl()?.id ?: accountButtons.getValue(accountTab).id
        navIcons().forEach { it.nextFocusDownId=headDown }
        findTagged(root,"nav:recommend")?.let { it.nextFocusDownId=headDown }
        accountButtons.values.forEach { it.nextFocusDownId=headDown }
        restoreChipFocus("account:")
    }
    // 榜单页的四个榜单切换芯片：放进榜单名那一行（屏幕窄 / 字号大时退回下面单独一行）。
    // 首页不再有「漫剧 / 短剧」开关：四个榜单本身就是首页的四栏，分类由栏目标题表达。
    if(tab==TAB_RANK) {
        val boards=row().apply { setPadding(pageBleed(),0,pageBleed(),dp(4)) }
        val boardChips=mutableListOf<View>()
        RankBoard.entries.forEach { board ->
            // 四个按钮分别是 漫剧 / 真人 / AI剧 / 上新；页面主标题固定写「榜单」（见 [pageTitle]）。
            val chip=ToggleChip(this,board.label,rankBoard==board) { switchRankBoard(board) }
            boardButtons[board]=chip
            boardChips+=chip
            gapInto(boards,chip,lp(-2,-2))
        }
        // 这一行就在页头最上面：按上键停在原地（以前会跳到导航栏的「榜单」图标上，
        // 用户读到的就是「已经在最上面了还换焦点」）。右移在四枚之间切换，
        // 走到最后一枚接同一视觉行里紧跟着的那排圆形入口。
        // 芯片就在导航栏里时，往右走到最后一枚接着是那排圆形入口（同一视觉行，方向连续）；
        // 退回下面单独一行时不接（那时它在自己的一行里，右到头就是到头）。
        val afterBoards=if(inlineHeadChips()) recommendIcon ?: nav.firstOrNull() else null
        wireChipRow(boardChips,{ boardChips[it] },afterBoards)
        if(inlineHeadChips()) {
            boards.setPadding(0,0,0,0); filterSlot.addView(boards)
        } else {
            container.addView(boards)
        }
        navIcons().forEach { it.nextFocusDownId=boardButtons.getValue(rankBoard).id }
        // 没有内容可落时（空榜单 / 加载中）这一下也不会变成「按下没反应」：
        // 先让它指向自己，内容铺好之后由 `wireGridFocus` 改成第一张卡。
        boardButtons.values.forEach { it.nextFocusDownId=it.id }
        // 点了另一枚榜单芯片：焦点立刻交回那一枚，不等内容落地、也不经过别的控件。
        restoreChipFocus("board:")
    }
    if(tab==TAB_SEARCH) {
        // 输入框下面那一行：**没有输入时是热搜词，打字时换成提示词**。
        // 固定高度、宽度与内容区对齐（见 [CHIP_ROW_HEIGHT]），因此这一行换来换去
        // 不会让下面的卡片行上下跳；两者都没有时整行隐藏，不留空槽。
        // 与分类页筛选行同一套「可显示区域」：
        // 行宽 = 内容区宽度；外面套一层硬裁剪容器（[ClipFrameLayout]），
        // 里面是可横向滚动的芯片行。芯片画到视口之外时被这一层直接 clipRect 掉，
        // 不会像以前那样把半枚芯片画到页面边缘之外（滚动容器自己 clipChildren 在
        // 部分机型 / 焦点缩放动画下靠不住）。
        val hintScroll=HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled=false
            // 裁剪交给外层 viewport：这里打开会让焦点放大溢出的描边被切平。
            clipChildren=false; clipToPadding=false
            isFillViewport=true
            // 滚到两端时不画越界的边缘光，避免看起来像「滚出了范围」。
            overScrollMode=View.OVER_SCROLL_NEVER
        }
        val hintLine=row().apply { gravity=Gravity.CENTER_VERTICAL }
        hintScroll.addView(hintLine)
        val hintViewport=ClipFrameLayout(this).apply {
            addView(hintScroll,FrameLayout.LayoutParams(-1,-1))
            visibility=View.GONE
        }
        container.addView(
            hintViewport,
            lp(gridContentWidth(),dp(CHIP_ROW_HEIGHT)).apply { leftMargin=pageBleed() }
        )
        registerTopAreaRow(hintViewport)
        suggestRow=hintScroll; suggestLine=hintLine
        suggestViewport=hintViewport
        // 进搜索页先把热搜词铺上（还没有输入时这一行就是它）；输入框变化时在下面接管。
        if(query.isBlank()) loadHotSearches() else scheduleSuggestions(query)
    } else if(tab==TAB_CATEGORY) {
        if(TvStyle.television) { miniActFilterBar(container) } else {
        // 分类页的筛选条件全部来自接口的 `selector_rows`（见 [CategoryPanel]）：
        // 体裁 / 主题 / 设定 / 背景 / 推荐 / 受众 / 时间 / 状态。
        //
        // 布局按需求拆成两层：
        // * **体裁**（真人剧 / 漫剧 / AI剧）放进最顶部导航栏，和页面标题同一行；
        // * 其余每一行左边一个固定宽度的小标签（题材 / 设定 / 背景…），右边是可横向滚动的
        //   小号芯片。行高、字号、横向与纵向间距都比正文按钮小一档，尽量把高度让给内容。
        // 体裁四枚按钮**常驻**：不论面板有没有到手、切换体裁时面板是否在刷新，
        // 它们都按固定顺序 全部 / 漫剧 / AI剧 / 真人 画在导航栏里，不跟着面板消失。
        val genreChips=mutableListOf<View>()
        if(categorySelection.genre==null) run {
            // 与榜单页同一套排法：芯片放在一个 row 里整体进导航栏，间距走默认档。
            val genreGroup=row()
            val buttons=categoryFilterButtons.getOrPut(CategorySelection.TYPE_GENRE) { mutableMapOf() }
            buttons.clear()
            CategoryGenres.options.forEach { option ->
                // 与榜单页的四枚榜单芯片同档（NORMAL），两页的「体裁 / 榜单」入口一样大。
                val chip=ToggleChip(this,option.label,categorySelection.genre==option.id,ButtonSize.NORMAL) {
                    selectCategoryFilter(CategorySelection.TYPE_GENRE,option.id)
                }
                buttons[option.id.orEmpty()]=chip
                genreChips+=chip
                gapInto(genreGroup,chip,lp(-2,-2))
            }
            filterSlot.addView(genreGroup)
            wireChipRow(genreChips,{ genreChips[it] },recommendIcon ?: nav.firstOrNull())
        }
        val panel=categoryPanel
        val rowChips=mutableListOf<MutableList<View>>()
        if(panel==null) {
            val tip=text("正在读取筛选条件…",13f,muted)
                .apply { setPadding(pageBleed(),0,pageBleed(),dp(8)) }
            container.addView(tip); registerTopAreaRow(tip)
        } else {
            val filterRows=panel.rows.filter { it.type!=CategorySelection.TYPE_GENRE && (miniFiltersExpanded || it.type in setOf(CategorySelection.TYPE_THEME,CategorySelection.TYPE_SORT)) }
            filterRows.forEach { filterRow ->
                // 每行是一个**硬裁剪容器**：行宽固定为「标签左缘 → 我的按钮可见右缘」，
                // 右侧滚动区的宽度由这个行宽减掉标签宽得到，不再依赖 weight 或布局后的二次调整。
                val rowWidth=categoryFilterWidth()
                val labelWidth=dp(46)
                val line=FrameLayout(this).apply {
                    clipChildren=true; clipToPadding=true
                }
                // 左侧标签：只写这一栏管什么（「全部主题」→「主题」），不参与焦点。
                val title=filterRow.title.removePrefix("全部").ifBlank { filterRow.title }
                val label=text(title,12f,muted).apply {
                    gravity=Gravity.CENTER_VERTICAL
                    maxLines=1
                    ellipsize=android.text.TextUtils.TruncateAt.END
                    setPadding(0,0,dp(6),0)
                }
                line.addView(label,FrameLayout.LayoutParams(labelWidth,-1))
                val chipsLine=row().apply { gravity=Gravity.CENTER_VERTICAL }
                val chips=mutableListOf<View>()
                val buttons=categoryFilterButtons.getOrPut(filterRow.type) { mutableMapOf() }
                buttons.clear()
                // 每行开头一枚「全部」：语义就是不传这个维度。
                // 「推荐」的「全部」就是接口的默认推荐序（`sort` 不传），默认也停在这一枚。
                run {
                    val active=categorySelection.valueOf(filterRow.type)==null
                    val chip=ToggleChip(this,"全部",active,ButtonSize.DENSE) {
                        selectCategoryFilter(filterRow.type,null)
                    }
                    buttons[""]=chip
                    chips+=chip
                    gapInto(chipsLine,chip,lp(-2,-2),6)
                }
                filterRow.options.forEach { option ->
                    val active=categorySelection.valueOf(filterRow.type)==option.id
                    val chip=ToggleChip(this,option.label,active,ButtonSize.DENSE) {
                        selectCategoryFilter(filterRow.type,option.id)
                    }
                    buttons[option.id]=chip
                    chips+=chip
                    gapInto(chipsLine,chip,lp(-2,-2),6)
                }
                val scroll=HorizontalScrollView(this).apply {
                    isHorizontalScrollBarEnabled=false
                    // 严格裁在「标签左侧 → 我的按钮右缘」这条范围里：芯片不准画到范围外。
                    clipChildren=true; clipToPadding=true
                    // 滚到两端时不画越界的边缘光，避免看起来像「滚出了范围」。
                    overScrollMode=View.OVER_SCROLL_NEVER
                    isFillViewport=true
                }
                scroll.addView(chipsLine)
                // 滚动区外面再套一层**硬裁剪**容器：它的边界就是显示边界。
                // 只靠 HorizontalScrollView 的 clipChildren 在某些机型 / 焦点缩放动画下
                // 仍会让非焦点芯片画到视口外；这里在 dispatchDraw 里直接 clipRect，
                // 滚到哪儿都只显示这一段。
                val viewport=ClipFrameLayout(this)
                viewport.addView(scroll,FrameLayout.LayoutParams(-1,-1))
                line.addView(
                    viewport,
                    FrameLayout.LayoutParams(rowWidth-labelWidth,-1).apply { leftMargin=labelWidth }
                )
                container.addView(
                    line,
                    lp(rowWidth,dp(FILTER_ROW_HEIGHT)).apply { leftMargin=pageBleed() }
                )
                registerTopAreaRow(line)
                rowChips+=chips
            }
        }
        // 体裁芯片按下键进第一行筛选；行与行之间上下走，最后一行由 `wireGridFocus`
        // 接到内容第一张卡。左右与「上」交给 [wireChipRow]。
        val moreLine=row().apply { setPadding(pageBleed(),dp(4),pageBleed(),dp(4)) }
        val more=addButton(moreLine,if(miniFiltersExpanded) "收起筛选  ↑" else "更多筛选  ↓",size=ButtonSize.COMPACT) {
            miniFiltersExpanded=!miniFiltersExpanded
            showCatalog(keepChip=true)
        }
        container.addView(moreLine); registerTopAreaRow(moreLine)
        rowChips+=mutableListOf(more)
        val firstFilter=rowChips.firstOrNull()?.firstOrNull()
        genreChips.forEach { chip -> firstFilter?.let { chip.nextFocusDownId=it.id } ?: run { chip.nextFocusDownId=chip.id } }
        rowChips.forEachIndexed { index, chips ->
            val up=if(index==0) genreChips.firstOrNull() else rowChips.getOrNull(index-1)?.firstOrNull()
            wireChipRow(chips,{ up ?: chips[it] })
            chips.forEach { chip ->
                chip.nextFocusDownId=rowChips.getOrNull(index+1)?.firstOrNull()?.id ?: chip.id
            }
        }
        // 导航栏的圆形入口与体裁芯片在同一行：按下键进体裁当前选中的那一枚。
        val down=categoryFilterButtons[CategorySelection.TYPE_GENRE]?.get(categorySelection.genre.orEmpty())
            ?: genreChips.firstOrNull()
        if(down!=null) {
            navIcons().forEach { it.nextFocusDownId=down.id }
            findTagged(root,"nav:recommend")?.let { it.nextFocusDownId=down.id }
        }
        // 点了某一枚筛选芯片：焦点立刻交回那一枚。
        restoreChipFocus("filter:")
        }
    } else if(tab==TAB_RANK) {
        // 榜单名已经进了导航栏，这一行只剩更新时间（接口没给就整行不占位）；
        // 「刷新榜单」按钮去掉：进页面、切榜单、滚到顶都会重新取，不需要单独一个入口。
        val subtitle=text(if(load) "正在读取最新榜单…" else ranking?.updatedText.orEmpty(),13f,muted)
            .apply {
                setPadding(pageBleed(),0,pageBleed(),dp(8))
                visibility=if(text.isBlank()) View.GONE else View.VISIBLE
            }
        rankSubtitle=subtitle
        container.addView(subtitle); registerTopAreaRow(subtitle)
    }
    val body=column(); catalogBody=body; container.addView(body,lp(-1,0).apply { weight=1f })
    if(localCatalog()) {
        // 本机集合页：一次取全（本机 + 账号合并），不分页。
        // 账号那半份可能还没拉到（首次进这一页），那就先用本机的，等 [loadAccount] 落地后重建。
        catalog=localItems(); ranking=null; page=1; hasMore=false
        if(collection!=null && accountData==null && accountError==null) loadAccount()
    }
    // 本机集合页没有远端列表要取（内容是「本机 + 账号」合并出来的），load 对它只表示
    // 「重建一次」：直接走下面的栅格分支，别掉进远端请求那条路（那会把搜索结果塞进 catalog）。
    if(load && !localCatalog()) {
        // 换榜单 / 换分类（点了芯片）时**不把列表清空**：旧的一屏留在原位，等新的一页落地再换。
        // 清空的话这一整段时间页面上只剩一行「正在加载…」，观感就是「每次选分类都闪一下」。
        // 位置也照旧：[releaseCatalogViews] 刚把旧列表的滚动位置记在它自己的 key 上
        // （此刻 [catalogPageKey] 还没换），照搬过来才不会先跳回顶部再被新内容替换。
        val stale=chipFocusKey!=null && appendCatalog() && catalog.isNotEmpty()
        // 芯片握着焦点时（[chipFocusKey] 非空）一律不许把焦点交给页头 / 顶部入口：
        // 用户刚点的那一枚芯片就是落点，这一页剩下的重建都不该动它。
        val holdChip=chipFocusKey!=null
        if(appending) seriesList(body,focusNav,focusType,loadingMore=true)
        else if(home) seriesList(body,focusNav,focusType,loading=true)
        else if(stale) seriesList(body,if(holdChip) false else focusNav,focusType,keepScroll=scrollPositions[catalogPageKey])
        else {
            // 搜索页第一次进来、设备还在注册：这句要说清「在初始化」，
            // 否则用户看到的是加载后又失败，只会反复按重试。
            message(body,if(tab==TAB_SEARCH && !library.deviceRegistered()) "正在初始化设备…" else "正在加载…")
            // 加载期间焦点必须有一个**确定的落点**：整页重建之后，刚按下的那枚芯片已经离树，
            // 不管的话系统会把焦点交给新布局里第一个可聚焦控件（页头那排入口 / 芯片）。
            // 点了芯片就把焦点交回那枚芯片（从头到尾不动）；否则落在页头控件上，
            // 内容一到再由 [focusFirstCardOnLanding] 把焦点交给第一张卡。
            val chip=chipControl(chipFocusKey)
            if(chip!=null) chip.requestFocus()
            else if(focusType || focusFirstCardOnLanding) firstHeadControl()?.requestFocus()
        }
        val requestTab=tab; val requestPage=page; val requestQuery=query; val requestBoard=rankBoard
        // 搜索源在主线程取一份快照：`library` 是主线程持有的，工作线程只该拿值、不该回读。
        val requestSource=library.searchSource
        val requestCategory=categorySelection
        // 面板没到手（首次进分类页 / 刚换过体裁）时，这一次请求顺带把整份 `selector_rows` 带回来。
        val needPanel=categoryPanel==null
        val panelRef=java.util.concurrent.atomic.AtomicReference<CategoryPanel?>()
        work({
            // 首页一次取四个榜单（并发，见 homeFeed）；榜单页只取当前这一个；
            // 分类页取当前小类的第 N 页；搜索页走搜索。四者都用同一个「请求 key → 页面」的形状，
            // 因此下面的落地逻辑（追加 / 换页 / 报错）只有一份。
            when(requestTab) {
                TAB_HOME -> repository.homeFeed()
                TAB_RANK -> mapOf(requestBoard to repository.rank(requestPage,requestBoard))
                TAB_CATEGORY -> {
                    val feed=repository.categoryFeed(requestCategory,requestPage,needPanel)
                    panelRef.set(feed.panel)
                    mapOf(requestBoard to feed.page)
                }
                else -> {
                    // 新装 / 换设备的第一次搜索：平台只给**注册过**的设备放行（见 `appSearch`），
                    // 没注册时接口回 200 + 空 body，界面就只能报「重试」。
                    // 这里先把设备注册上再搜，用户看到的是「正在初始化」而不是一次失败；
                    // 注册已在途时 [registerDeviceOnce] 会在锁上等那一次完成，不会重复发请求。
                    if(!library.deviceRegistered()) registerDeviceOnce()
                    val searchStarted=android.os.SystemClock.elapsedRealtime()
                    val found=repository.search(requestQuery,requestPage,requestSource)
                    android.util.Log.i("MiniAct-Search","Completed source=$requestSource page=$requestPage items=${found.items.size} elapsedMs=${android.os.SystemClock.elapsedRealtime()-searchStarted}")
                    mapOf(requestBoard to found)
                }
            }
        }, { result ->
            if(requestTab==TAB_HOME) {
                // 四个剧架一起换。首屏可能是上次缓存（[homeFromCache]），
                // 新内容到了**就地更新剧架**（[HomeScreen.updateShelves] 会保留当前卡片与滚动位置），
                // 而不是整页重画——用户正在看的那一栏不会跳。
                val first=homeFeed.isEmpty()
                // 首页这一轮拿到了内容：上一轮的「加载失败」已经不作数，面板上擦掉。
                clearDebugProblem()
                homeFeed=LinkedHashMap(result)
                result.forEach { (board,page) -> library.cacheHome(board,page.items,page.hasMore) }
                val keepNav=nav.any { it.hasFocus() }
                val homeView=homeScreen
                // 首屏（还没有任何剧架）整页铺；已经有剧架就地更新——`updateShelves` 默认
                // 「焦点本来在剧架上就把焦点按回原卡片」，因此用户正在看的那一栏不会跳、也不会丢焦点。
                focusFirstCardOnLanding=false
                if(first || homeView==null) {
                    // 首页四栏：焦点交给第一栏第一张卡（keepNav 为真时留在顶部入口）。
                    body.removeAllViews(); seriesList(body,keepNav || focusNav)
                } else {
                    // 结构没变就**只改文字**：`updateShelves` 会 `removeAllViews()` 重建整块，
                    // 70 多张卡全部重新建一遍（首页首帧那一两秒的主线程工作），而新内容
                    // 往往只是同一批剧的集数 / 热度变了。key 集合一致时走就地刷新。
                    val shelves=homeShelves()
                    val structural=shelves.flatMap { shelf -> shelf.entries.map { "${shelf.key}:${it.series.id}" } }.toSet()
                    if(structural==homeView.cardKeys()) homeView.refreshEntries(shelves)
                    else homeView.updateShelves(shelves)
                }
                return@work
            }
            val page=result[requestBoard] ?: CatalogPage(emptyList(),false)
            // 分类页：面板第一次到手（或换体裁后的新面板）时，把内容先写进状态，
            // 再整页重建一次——筛选项是页面结构的一部分，不能在内容区里补。
            // `load=false` 保证这次重建不会再发请求（内容就是刚拿到的这一页）。
            if(requestTab==TAB_CATEGORY) {
                val fetched=panelRef.get()
                if(fetched!=null && tab==TAB_CATEGORY && categorySelection==requestCategory) {
                    val firstPanel=categoryPanel==null
                    categoryPanel=fetched
                    if(firstPanel) {
                        catalog=page.items; ranking=page.ranking; hasMore=page.hasMore
                        showCatalog(load=false,keepChip=true)
                        // 面板到手后的这次重建只为把筛选行画出来；焦点已经在上面的
                        // [restoreChipFocus] 里交回芯片了，这里必须清掉「芯片握着焦点」的标记——
                        // 否则 `requestNextPage` / `prefetchNextPage` 会一直以为用户还在挑芯片，
                        // 表现为「只有第一页 20 部，点展示更多没反应」。
                        chipFocusKey=null
                        return@work
                    }
                }
            }
            if(appending) {
                // 追加：只接住还没出现过的剧集，并把这一页的名次与热度并进已有榜单。
                val added=page.items.filterNot { item -> catalog.any { it.id==item.id } }
                val wanted=moreFocus; moreFocus=null
                catalog=catalog+added; ranking=mergeRanking(ranking,page.ranking); hasMore=page.hasMore
                setRankSubtitle(ranking?.updatedText.orEmpty())
                // 栅格还活着（空页那次重建之后又追加）就只往下接，不整页重画。
                if(gridList!=null && catalogBody===body) appendGridItems(added,wanted)
                else { body.removeAllViews(); seriesList(body,false) }
            } else {
                catalog=page.items; ranking=page.ranking; hasMore=page.hasMore
                // 拿到内容 = 上一次的加载失败已经过去；空结果那条由下面的 seriesList 重新记。
                clearDebugProblem()
                setRankSubtitle(ranking?.updatedText.orEmpty())
                // 焦点落点按**显式意图**决定（见 [focusFirstCardOnLanding]）：换了一份列表
                // （换榜单 / 换分类 / 从首页进榜单）就不要留在页头——`focusNav` 置 false 之后，
                // seriesList 末尾的兜底会把焦点交给第一张卡（`catalogFocus` 在换列表时已清空）。
                // 其余情况保留用户当前在页头的位置（切标签回来看同一页等）。
                val landFirst=focusFirstCardOnLanding; focusFirstCardOnLanding=false
                // 点了芯片的这一次（[chipFocusKey] 非空）同样不要把焦点交给第一张卡：
                // 用户还在挑榜单 / 挑分类，落点就是他刚点的那一枚芯片。
                val holdChip=chipFocusKey!=null
                val keepNav=!holdChip && !landFirst && nav.any { it.hasFocus() }
                // [keepFocus]：新的一页铺好之后**一个焦点都不许动**——用户刚点的那一枚芯片
                // 就是落点，内容落地不该把焦点带去第一张卡（那正是「焦点跳到别处」的来源）。
                body.removeAllViews(); seriesList(body,if(holdChip) false else keepNav || focusNav,keepFocus=holdChip)
                // 新的一页已经铺好、焦点也已交回芯片，这一轮的意图到此结束。
                chipFocusKey=null
                // 上面那次重建刻意跳过了预取（那时屏上还是旧列表），这里补一次：
                // 用户第一次按下键时下一页的内容已经在手上。
                if(holdChip) prefetchNextPage()
            }
        }, { problem ->
            if(requestTab==TAB_HOME) {
                if(homeFeed.isNotEmpty()) { showHint("热门暂时取不到，仍在显示上次内容"); return@work }
                // 一栏都没有：只剩提示行会是一个空页面，这里给出错误说明与「重试」。
                body.removeAllViews(); error(body,problem,stage="content_home") { showCatalog(true) }
                return@work
            }
            if(appending) {
                // 追加失败不动已经显示的剧集，页码退回待加载的那一页，底部按钮可以直接重试。
                page=requestPage-1; moreFocus=LOAD_MORE
                setRankSubtitle("加载更多失败 · 当前显示 ${catalog.size} 部")
                noteDebugProblem("加载更多失败 · 第 ${requestPage} 页 · 已显示 ${catalog.size} 部")
                body.removeAllViews(); seriesList(body,false)
                return@work
            }
            catalog=emptyList(); hasMore=false; ranking=null
            setRankSubtitle(if(tab==TAB_SEARCH) "搜索失败" else "榜单加载失败")
            if(tab==TAB_SEARCH) searchSummary?.text="搜索未完成，可重试或换个关键词"
            // 服务端不认本机这份设备身份（搜索空响应 / 播放 101000）时**先自愈一次**：
            // 丢掉旧身份重注册，成功之后由 [healDeviceIdentity] 把这一页重取一遍。
            // 这是「升级到 1.2.3 后搜不出东西、清缓存才好」那条反馈的正面修复——
            // 以前这里只会显示「设备正在初始化」或一句「暂时无法加载」，用户只能自己去清缓存。
            if(deviceRejectionOf(problem) && healDeviceIdentity("搜索被拒") { showCatalog(true) }) return@work
            if(problem is SearchSessionExpiredException) {
                page=1; catalogFocus=""
                showHint("搜索结果已过期，已回到第 1 页刷新")
                showCatalog(true)
            } else {
                body.removeAllViews()
                val retry=error(body,problem,stage=if(tab==TAB_SEARCH) "content_search" else "content_rank") { showCatalog(true) }
                // 取不到内容时焦点仍然留在用户刚点的那一枚芯片上（不跳到「重试」上），
                // 但「重试」要够得着：芯片按下键就是它，它按上键回芯片。
                chipControl(chipFocusKey)?.let { chip ->
                    chip.nextFocusDownId=retry.id
                    retry.nextFocusUpId=chip.id
                    chip.requestFocus()
                }
                chipFocusKey=null
            }
        })
    } else seriesList(body,focusNav,focusType)
}

/**
 * 榜单页页头那一行说明（正在读取 / 更新时间 / 失败原因）。
 *
 * 接口不给更新时间时**整行不占位**：早先这一行会退回「来源：…热播榜」，
 * 用户看着像一行没用的字，现在按需求去掉来源文案——只有真有话要说时才显示。
 */
internal fun MainActivity.setRankSubtitle(value: String) {
    val view=rankSubtitle ?: return
    view.text=value
    view.visibility=if(value.isBlank()) View.GONE else View.VISIBLE
}

/**
 * **全站唯一的列表入口**。
 *
 * 分类页、榜单页、搜索页、我的页、本机集合页、首页四栏（走 [renderHome] 那一支）都由这里铺出来，
 * 因此「哪些剧、怎么排、怎么翻页、焦点怎么走」全站只有这一份实现：
 *
 * * 卡片本身：`tv/PosterCard`（全站同一张海报卡，封面取图走 `MainActivity.loadCover`）；
 * * 排版：[SeriesGrid] 的窗口化六列栅格（列表页），或 [HomeScreen] 的横排剧架（首页 / 相关推荐）；
 * * 动作：点卡片 = [openSeries]（进详情 / 续播），长按或菜单键 = [seriesMenu]（剧卡菜单）；
 * * 行为：向下滚动到底按 [requestNextPage] 取下一页；焦点、滚动位置、页头收展都在这一套里。
 *
 * 新加一个「剧集列表」页面时**不要**另写一套列表：把数据交给这里，用 tab / collection 决定来源即可。
 */
internal fun MainActivity.seriesList(body: LinearLayout,focusNav: Boolean,focusType: Boolean=false,loading: Boolean=false,loadingMore: Boolean=false,keepScroll: Int?=null,keepFocus: Boolean=false) {
    // 「这一次重建是点了榜单 / 分类芯片引起的」要在这里就定下来：下面的焦点落点是
    // `post` 里跑的，等它执行时 [chipFocusKey] 可能已经被落地逻辑消费掉了，
    // 那时再读就守不住「不许抢焦点」这条规则。
    val chipHeld=chipFocusKey!=null || keepFocus
    // 这一页会新建自己的栅格：旧的活引用先作废（首页剧架不是栅格，这里同样清干净），往下重建。
    gridList=null; gridScroll=null; gridPaging=null; moreCount=null; pagingHasMore=false
    gridWindow=null; gridTopSpacer=null; gridBottomSpacer=null
    gridItems=emptyList(); gridRowStride=0; gridWindowStart=-1
    // 摘掉窗口与回收池里的封面（见 [recycleGridRow]）：它们不在缓存账上，
    // 只清引用不清 View 的话，旧图要等 GC 才走。
    gridRows.forEach { row -> row.cards.forEach { it.releaseCover() } }
    gridRowPool.forEach { row -> row.cards.forEach { it.releaseCover() } }
    gridRows.clear(); gridRowPool.clear(); gridCardWidth=0; gridGap=0; gridPosterHeight=0
    gridRestoring=true
    if(tab==TAB_HOME && page==1 && collection==null) { renderHome(body,focusNav,focusType,loading); return }
    if(tab==TAB_SEARCH && collection==null && query.isBlank()) { miniActSearchDiscovery(body); return }
    // 「我的」页：把当前栏目的条目填进 [catalog]，下面走**同一套**卡片栅格
    // （[PosterCard] + 窗口化 + 六列排版），因此它与榜单页的卡片列表完全一致。
    // 资料还没拿到 / 没通过验证时 catalog 是空的，下面的空列表分支会铺 [accountNotice]。
    if(tab==TAB_PROFILE) { catalog=accountItems(accountTab); hasMore=false; ranking=null }
    // 这一页会新建自己的页头行与底部按钮，先把上一页留下的引用清掉：
    // 让已经离树的控件继续当焦点目标，焦点请求会失败，焦点就可能落到页头的按钮上，
    // 页头于是被重新展开——观感就是「加载更多之后顶部那排按钮又显示出来」。
    val wanted=moreFocus; moreFocus=null
    moreButton=null
    // 原地重建列表（refreshLibraryCards）会把上一次的页头行摘下来，这里顺手清掉，别留着已经离树的引用。
    topArea.filter { it.parent==null }.forEach { row ->
        topAreaAnimators.remove(row)?.cancel()
        topAreaHeights.remove(row); topAreaRestore.remove(row)
    }
    topArea.removeAll { it.parent==null }
    // 集合页的标题搬进了顶部导航栏，这里只剩收藏页的更新状态行。
    if(collection=="favorites") {
        val status=text(favoriteMonitor.status,13f,muted).apply { setPadding(pageBleed(),0,pageBleed(),dp(4)) }
        favoriteStatus=status
        body.addView(status); registerTopAreaRow(status)
    }
    // 历史集合页的「清空全部记录」在导航栏里（上面已经建好，[historyClear] 已赋值）；
    // 「我的 → 历史」那一枚在身份行里（见 [profileHead]）。这里不再补内容区的行。
    // 远端长列表是向下追加的同一份内容，滚动位置不能跟着页码走，否则每次「展示更多」都会跳回顶部。
    // 向下追加的长列表（榜单 / 分类 / 搜索）位置不跟着页码走，因此 key 里页码固定为 1；
    // 分类页还要带上当前小类，否则换分类会沿用上一条的滚动位置。
    catalogPageKey=buildString {
        append(tab); append(':'); append(collection.orEmpty()); append(':')
        append(if(appendCatalog() || fullList()) 1 else page); append(':')
        if(tab==TAB_CATEGORY) append(categorySelection.key()).append(':')
        if(tab==TAB_PROFILE) append(accountTab).append(':')
        append(query)
    }
    val scroll=ScrollView(this).apply { isFillViewport=false; isVerticalScrollBarEnabled=false }.focusBleed(); catalogScroll=scroll
    val list=column(); scroll.addView(list); body.addView(tvScrollHost(scroll),lp(-1,0).apply { weight=1f })
    // 页头收展：恢复上次滚动位置的那一次不算，免得一进页面页头就闪一下。
    listScrollY=scroll.scrollY
    // 列表重建期间产生的滚动（位置恢复、焦点回带、内容夹取）都不是用户滚动：
    // 静默期内不改页头状态，否则每次「展示更多」重建页面都会把这些滚动当成「滚到顶了」，
    // 页头于是又展开一次。
    topAreaSettleUntil=android.os.SystemClock.elapsedRealtime()+TvStyle.DURATION+120
    scroll.onScrollYChanged { y ->
        if(catalogScroll!==scroll || gridRestoring) return@onScrollYChanged
        // 先按新的滚动位置把窗口挪好，再交给页头收展 / 预取那套逻辑。
        // 窗口外的那几百行根本不存在，因此这一步与「已加载多少」无关。
        syncGridWindow()
        onListScroll(y)
    }
    // 远端长列表不切页：catalog 就是已经向下追加出来的整份列表，一次全画出来。
    // 首页与热门隐藏掉本机标为「不感兴趣」的剧；搜索结果不做这层过滤（搜到就是要看）。
    val displayed=when {
        // 集合页与「我的」页：一次全量铺出来（窗口化只物化视口附近那几行），不分页、不截断。
        // 这两类列表不过滤「不感兴趣」：本机记录与账号里的收藏 / 历史是用户自己的东西，
        // 本机那套隐藏规则是给推荐位用的。
        fullList() -> catalog
        tab==TAB_HOME || tab==TAB_RANK || tab==TAB_CATEGORY -> catalog.filterNot { it.id in library.hidden() }
        tab==TAB_SEARCH && collection==null -> SearchRanking.order(catalog,query)
        appendCatalog() -> catalog
        else -> catalog.take(30)
    }
    if(tab==TAB_SEARCH && collection==null) updateSearchSummary(loading)
    if(displayed.isEmpty()) {
        // 搜索页空结果与「接口失败」是两件事：这里只记「真的搜了、真的没有」，
        // 失败那条由错误页（[error]）自己记，别把网络问题写成「没有找到相关剧集」。
        // 搜索页空结果：**按接口状态判读**（确实没匹配 / 设备未注册 / 业务码 / 网络），
        // 而不是把页面上那句「没有找到相关剧集」抄进面板——那句话四种原因都长一样。
        if(tab==TAB_SEARCH && collection==null && query.isNotBlank() && !loading)
            noteDebugProblem("搜索「${query.take(16)}」· ${com.hgtv.star.core.SearchDiag.explain()}")
        // 「我的」页：**资料没拿到**（未导入 / 读取中 / 读取失败 / 参数无效）时铺那一套说明与按钮；
        // 资料拿到了只是这一栏为空，就走下面那句按栏目区分的空文案，不再摆「参数无效」。
        if(tab==TAB_PROFILE && accountData?.profile==null) {
            accountNotice(list)
            focusFirstCardOnLanding=false
            ensureFocusView { chipControl(chipFocusKey) ?: firstHeadControl() ?: navEntry() }
            return
        }
        message(list,if(loading) "正在加载推荐…" else when {
            // 搜索页的「怎么打字」在这里也说一次：这是没有结果时用户唯一能看到的说明。
            tab==TAB_SEARCH -> if(query.isBlank()) "输入关键词后按「搜索」；遥控器用方向键选字，也可外接键盘" else "没有找到相关剧集，换个关键词试试"
            tab==TAB_CATEGORY -> "${categoryDescription()}暂时没有内容"
            collection=="favorites" -> "在剧集详情中选择收藏，喜欢的剧就会出现在这里"
            collection=="later" -> "长按一张剧集卡片，选择「稍后观看」，想看的故事会留在这里。"
            // 筛选生效时不能说「会自动保存在这里」：这一栏是空的，但本机其实有记录，
            // 只是都被另一档筛掉了。
            collection=="history" -> when(accountHistoryFilter) {
                true -> "没有已看完的剧集，可在「观看状态」中选择全部记录"
                false -> "没有未看完的剧集，可在「观看状态」中选择全部记录"
                else -> "播放过的剧集会自动保存在这里"
            }
            // 没导入凭据时不能写「账号里还没有…」——那句话读起来像「这个账号是空的」，
            // 而真实原因是**这些功能要凭据才能用**（见 [SignInRequiredException]）。
            tab==TAB_PROFILE -> if(!repository.signedIn) "导入账号参数后，这里会显示云端的内容" else when(accountTab) {
                "favorites" -> "账号里还没有收藏的剧集"
                "history" -> "账号里还没有观看记录"
                "likes" -> "账号里还没有点赞的剧集"
                else -> "账号里还没有预约的剧集"
            }
            else -> "本页没有更多内容"
        })
        // 一页内容都没有（空页）时没有栅格可追加，这里的入口退回整页重建。
        // 搜索结果不给按钮：往下滚就自动接上，这里只提示还剩多少没显示。
        if(appendCatalog() && hasMore && !autoAppendCatalog()) addButton(list,moreLabel()) { loadMore() }
        if(autoAppendCatalog() && hasMore) message(list,"向下滚动会自动加载更多")
        // 空页没有卡片可落。**只在焦点真的丢了的时候**才补一个落点（见 [ensureFocus]）：
        // 全站原则是不主动抢焦点，内容变空不是用户动作，不该把焦点从用户正按着的地方搬走。
        focusFirstCardOnLanding=false
        ensureFocusView { chipControl(chipFocusKey) ?: firstHeadControl() ?: navEntry() }
        return
    }
    val gap=dp(6)
    val gridWidth=gridContentWidth()
    val columns=gridColumns
    val width=((gridWidth-gap*(columns-1))/columns).toInt()
    // 与 HomeScreen.slotHeight 同一算法：先扣掉卡片 5dp 内边距，再按 134:196 换算。
    val posterHeight=((width-dp(2*TvStyle.SOFT_BAND+8))/TvStyle.POSTER_ASPECT).toInt()
    gridList=list; gridScroll=scroll; gridCardWidth=width; gridGap=gap; gridPosterHeight=posterHeight
    gridItems=displayed
    gridRowStride=0
    gridWindowStart=-1
    // 列表高度由两个 spacer 撑出来：中间只放视口附近那几行（见 [syncGridWindow]）。
    // spacer 必须是真正的 Space：它只占高度、不参与绘制、也不进焦点链。
    val topSpacer=Space(this); val window=column(); val bottomSpacer=Space(this)
    gridTopSpacer=topSpacer; gridWindow=window; gridBottomSpacer=bottomSpacer
    list.addView(topSpacer,lp(-1,0))
    list.addView(window,lp(-1,-2))
    list.addView(bottomSpacer,lp(-1,0))
    syncGridWindow(force=true)
    val paging=row(); paging.gravity=Gravity.CENTER
    var prev: View?=null; var next: View?=null
    if(appendCatalog()) {
        // 榜单页与搜索结果去掉「上一页 / 下一页 / 下一批」：只向下追加，底部留一个向下的按钮按需加载下一页。
        gridPaging=paging
        fillPagingRow(paging)
        list.addView(paging,lp(-1,dp(TvStyle.BUTTON_NORMAL+2*TvStyle.SOFT_BAND+TvStyle.GAP_TIGHT)))
        next=moreButton?.takeIf { it.isFocusable }
    } else if(tab==TAB_SEARCH) {
        // 只有搜索页还留翻页按钮（它的第 2 页起走的是片库目录补页，不是向下追加）。
        // 集合页与「我的」页是一次全量铺出来的长列表，没有页码这个概念。
        prev=addButton(paging,"上一页") { if(page>1) { page--; catalogFocus=""; showCatalog() } }.apply { isEnabled=page>1; isFocusable=page>1 }
        gapInto(paging,text("第 $page 页",14f,muted).apply { gravity=Gravity.CENTER },lp(dp(95),dp(TvStyle.BUTTON_NORMAL+2*TvStyle.SOFT_BAND)))
        next=addButton(paging,"下一页") { if(hasMore) { page++; catalogFocus=""; showCatalog() } }.apply { isEnabled=hasMore; isFocusable=hasMore }
        list.addView(paging,lp(-1,dp(TvStyle.BUTTON_NORMAL+2*TvStyle.SOFT_BAND+TvStyle.GAP_TIGHT)))
    }
    wireGridFocus(prev,next)
    // 追加一页之后把焦点交给新出现的第一张卡片；没有新卡片或加载失败就留在底部按钮上。
    // 窗口化之后新卡片往往还没物化，因此这里记的是**索引**，落地时由 [focusGridIndex] 补窗口。
    val appendedIndex=when {
        wanted==null -> null
        wanted==LOAD_MORE -> null
        else -> gridItems.indexOfFirst { it.id==wanted }.takeIf { it>=0 }
    }
    // 三种情况：换榜单 / 换分类时屏上还是**旧列表**（[keepScroll] 把它原来的位置带过来，
    // 免得点完芯片先跳一下）；换列表的新内容落地时从头开始（不沿用上一条的位置）；
    // 其余重建按这个 key 记住的位置恢复。
    val savedScroll=keepScroll ?: if(chipHeld) null else scrollPositions[catalogPageKey]
    // 位置恢复要等视口高度量到再算：视口还没测量时窗口只能按估算铺，
    // 恢复位置也会被 ScrollView 夹成 0（clamp 里 my >= child 直接返回 0），
    // 换页之后列表就跳回顶部、页头跟着展开。
    scroll.post {
        if(catalogScroll!==scroll) return@post
        // 6 列时按整屏高度收口；4 / 5 列是用户要的大卡片，不再把封面压回 6 列的高度，
        // 否则只放大了文字、图片没放大（与首页同一套规则，见 HomeScreen.fitArtwork）。
        val fitted=if(columns>=6) minOf(posterHeight,(scroll.height-dp(56)).coerceAtLeast(dp(120))) else posterHeight
        gridPosterHeight=fitted
        gridRowStride=0
        gridRows.forEach { holder -> holder.cards.forEach { card -> card.resizePoster(fitted) } }
        // 行高先量准，再恢复位置：反过来的话 spacer 还按估算高度撑，恢复出来的位置是错的。
        // 海报高度刚改过，这里量到的行高才是最终的；resizePoster 内部对同值不重设，不会白花布局。
        measureRowStride()
        savedScroll?.let { scroll.scrollTo(0,it) }
        syncGridWindow(force=true,focusIndex=gridFocusIndex())
        gridRestoring=false
        // 位置恢复期间（`gridRestoring`）的滚动不上报，这里按最终位置把页头状态摆正。
        syncHeaderState()
    }
    // 兜底：视口尺寸万一没变化、上面的 post 没跑到，静默期不能一直挂着。
    list.postDelayed({ if(catalogScroll===scroll) gridRestoring=false },400)
    // 焦点落点与位置恢复都要等视口量到：spacer 撑出来的高度、窗口范围、行高
    // 都以 `scroll.height` 为准，第一帧还没量到时算出来的是错的。
    // 页头 / 顶部入口这类「确定不依赖视口」的落点直接给，不等。
    when {
        // 这两个是**显式意图**（「加载占位时把焦点放在页头」「把焦点留在页头」），
        // 不是异步落地抢焦点，因此照旧直接落；但要先把收起的页头展开，
        // 否则焦点会停在一条高度为 0 的行上（用户读到的就是「按上没反应」）。
        focusType -> firstHeadControl()?.let { focusHead(it) }
        focusNav -> navEntry()?.let { focusHead(it) }
        else -> scroll.post {
            if(catalogScroll!==scroll) return@post
            // 点了榜单 / 分类芯片的这一次，焦点已经交回那枚芯片（见 [restoreChipFocus]），
            // 这里不许再抢：抢了就是「选完分类焦点又跳到内容区」。
            if(chipHeld) return@post
            when {
                loadingMore -> ensureFocus { moreButton?.requestFocus() }
                wanted==LOAD_MORE -> {
                    ensureFocus { moreButton?.requestFocus() }
                    revealRow(gridRowCount()-1,false)
                }
                appendedIndex!=null -> ensureFocus { focusGridIndex(appendedIndex,false) }
                // 记住过焦点的卡片（同一页重建）优先；其次落到第一张卡——
                // 「换榜单 / 换分类 / 从首页进榜单」这类换列表的动作走的就是这一条。
                else -> ensureFocus { focusGridIndex(gridItems.indexOfFirst { it.id==catalogFocus }.takeIf { it>=0 } ?: 0,false) }
            }
            // 焦点落定之后才让「回到顶部」浮层露面（见 `list/BackToTop.kt` 的 updateBackToTop）。
            // 上面那次 `syncHeaderState()` 排在焦点落地**之前**，那时整棵树还没有焦点，
            // 浮层会被 `focusableViewAvailable` 抢成「首个可聚焦控件」——必须在这里补一次，
            // 否则「始终显示」档下浮层要等用户真正滚动一下才出现。
            updateBackToTop()
        }
    }
    // 首屏刚铺好就先把下一页取回来：用户第一次按下键时内容已经在手上。
    // 换榜单 / 换分类期间屏上还是旧列表，这一下要跳过：不能拿旧列表去预取新列表的下一页。
    if(!chipHeld) prefetchNextPage()
}
/**
 * 分类页筛选行的**硬裁剪视口**。
 *
 * `clipChildren` 只约束「子 View 不画到父 View 外面」，在焦点缩放动画 / 硬件层上并不可靠。
 * 这里在 `dispatchDraw` 里直接把画布裁到自己的矩形：滚动内容再多，也只会显示这一段的像素。
 */
private class ClipFrameLayout(context: android.content.Context) : FrameLayout(context) {
    init {
        clipChildren=true
        clipToPadding=true
    }
    override fun dispatchDraw(canvas: Canvas) {
        val save=canvas.save()
        canvas.clipRect(0,0,width,height)
        super.dispatchDraw(canvas)
        canvas.restoreToCount(save)
    }
}
/**
 * 底部按钮文案。
 *
 * 静默预取不打扰用户（还是「展示更多 ↓」），只有用户自己按出来的加载才显示「正在加载更多…」。
 * 分类页与榜单页都有这个按钮（它们不是自动追加）；搜索结果没有按钮，滚到底就自动接。
 */

/** 容器里**最后一枚**可聚焦控件（深度优先、按视觉顺序）；容器自己可聚焦时就是它自己。 */
private fun lastFocusable(view: View): View? {
    if(view.visibility!=View.VISIBLE) return null
    if(view is android.view.ViewGroup) {
        for(index in view.childCount-1 downTo 0) {
            lastFocusable(view.getChildAt(index))?.let { return it }
        }
    }
    return if(view.isFocusable) view else null
}
/** 容器里第一枚可聚焦控件（深度优先）；容器自己可聚焦时就是它自己。 */
private fun firstFocusable(view: View): View? {
    if(view.visibility!=View.VISIBLE) return null
    if(view.isFocusable) return view
    if(view !is android.view.ViewGroup) return null
    for(index in 0 until view.childCount) {
        firstFocusable(view.getChildAt(index))?.let { return it }
    }
    return null
}
