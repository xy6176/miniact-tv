// SPDX-License-Identifier: GPL-3.0-only
package com.hgtv.star

import android.graphics.*
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.*
import android.widget.*
import com.hgtv.star.core.CategorySelection
import com.hgtv.star.core.Series
import com.hgtv.star.core.RankBoard
import com.hgtv.star.list.HomeScreen
import com.hgtv.star.list.openSeries
import com.hgtv.star.list.seriesMenu
import com.hgtv.star.list.ensureFocusView
import com.hgtv.star.list.showCatalog
import com.hgtv.star.tv.MiniActClock
import com.hgtv.star.tv.*
import com.hgtv.star.compat.vectorDrawable

/** Browsing uses one neutral backdrop; only drama detail scenes add artwork. */
internal class MiniActBackdrop(private val scene: MiniActGlassScene) : Drawable() {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    private var layers=emptyList<Shader>()
    private val destination=RectF()
    private var cached: Bitmap?=null
    private var revision=-1
    private var themeId=""
    override fun onBoundsChange(bounds: Rect) {
        destination.set(bounds); revision=-1
        val theme=scene.theme;themeId=theme.id
        val w=bounds.width().coerceAtLeast(1).toFloat(); val h=bounds.height().coerceAtLeast(1).toFloat()
        layers=listOf(
            LinearGradient(0f,0f,w,h,intArrayOf(theme.top,theme.middle,theme.bottom),null,Shader.TileMode.CLAMP),
            RadialGradient(w*.84f,h*.04f,w*.72f,intArrayOf(theme.upperLight,Color.TRANSPARENT),null,Shader.TileMode.CLAMP),
            RadialGradient(w*.03f,h*.88f,w*.56f,intArrayOf(theme.lowerLight,Color.TRANSPARENT),null,Shader.TileMode.CLAMP),
            RadialGradient(w*.53f,h*.42f,w*.40f,intArrayOf(if(theme.id=="cinema") Color.TRANSPARENT else MiniActThemes.alpha(theme.cool,58),Color.TRANSPARENT),null,Shader.TileMode.CLAMP),
            LinearGradient(0f,0f,0f,h,intArrayOf(MiniActThemes.alpha(theme.bottom,0),MiniActThemes.alpha(theme.bottom,32),MiniActThemes.alpha(theme.bottom,theme.floorOpacity)),floatArrayOf(0f,.5f,1f),Shader.TileMode.CLAMP))
    }
    override fun draw(canvas: Canvas) {
        if(themeId!=scene.theme.id)onBoundsChange(bounds)
        if(revision!=scene.revision || cached==null) {
            val buffer=cached ?: Bitmap.createBitmap(192,108,Bitmap.Config.ARGB_8888).also { cached=it }
            val target=Canvas(buffer)
            target.scale(192f/bounds.width().coerceAtLeast(1),108f/bounds.height().coerceAtLeast(1))
            paint.shader=layers.firstOrNull(); target.drawRect(bounds,paint); paint.shader=null
            if(!scene.reduced && !scene.browsing) scene.image?.let { paint.alpha=42;target.drawBitmap(it,null,destination,paint);paint.alpha=255 }
            for(i in 1 until layers.size) { paint.shader=layers[i]; target.drawRect(bounds,paint) }; paint.shader=null
            revision=scene.revision
        }
        cached?.let { canvas.drawBitmap(it,null,destination,paint) }
    }
    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(filter: ColorFilter?) {}
    @Deprecated("Deprecated in Android") override fun getOpacity()=PixelFormat.OPAQUE
}
internal class AmbientReceiver(context: android.content.Context, private val scene: MiniActGlassScene) : ImageView(context) {
    override fun setImageBitmap(bitmap: Bitmap?) { if(bitmap!=null) scene.poster(bitmap) }
    override fun onDraw(canvas: Canvas) {}
}
/** Every browsing destination shares a neutral scene. Drama artwork stays in detail/playback. */
internal fun MainActivity.selectMiniActScene(key:String):MiniActGlassScene {
    val existing=root.getTag(R.id.miniact_glass_scene) as? MiniActGlassScene
    if(miniSceneKey==key && existing!=null) { existing.applyTheme(MiniActThemes.current);return existing }
    val scene=if(key=="browse") miniBrowseScene ?: MiniActGlassScene(root,appScope,browsing=true).also { miniBrowseScene=it }
        else MiniActGlassScene(root,appScope)
    miniSceneKey=key
    scene.applyTheme(MiniActThemes.current)
    root.setTag(R.id.miniact_glass_scene,scene)
    window.decorView.setTag(R.id.miniact_glass_scene,scene)
    miniAmbient=AmbientReceiver(this,scene).apply {
        importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO; isFocusable=false
        layout(0,0,dp(48),dp(72))
    }
    return scene
}
internal fun MainActivity.installMiniActBackdrop() {
    val key=when {
        screen=="detail" -> "detail:${detail?.series?.id ?: pendingDetail?.id.orEmpty()}"
        else -> "browse"
    }
    val scene=selectMiniActScene(key)
    root.background=MiniActBackdrop(scene)
    root.addView(miniAmbient,FrameLayout.LayoutParams(dp(48),dp(72)))
}
internal fun MainActivity.bindMiniActAmbient(cover: String) {
    // Browsing chrome must not depend on a featured poster, the category, or load order.
    if(miniSceneKey=="browse") return
    if(cover.isNotBlank()) miniAmbient?.let { loadCover(it,cover) }
}
internal fun MainActivity.miniActBrand(): View = row().apply {
    val mark=ImageView(this@miniActBrand).apply {
        setImageDrawable(this@miniActBrand.vectorDrawable(R.drawable.app_icon))
        importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    addView(mark,lp(dp(24),dp(28)))
    addView(text("MiniAct",21f,0xFFF1EDE5.toInt()).apply {
        typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
        if(Build.VERSION.SDK_INT>=21) letterSpacing=-.035f
        setPadding(dp(8),0,0,0)
    },lp(-2,-2))
}
internal fun MainActivity.miniActGenre(genre: String) {
    if(screen=="catalog" && tab==TAB_CATEGORY && categorySelection.genre==genre && collection==null) return
    tabVisited.add(TAB_CATEGORY); categorySelection=CategorySelection(genre=genre); categoryPanel=null; miniFiltersExpanded=false
    tabState.remove(TAB_CATEGORY); tabFocus.remove(TAB_CATEGORY)
    // Switching between the three genre destinations replaces the current category page.
    // switchTab would otherwise save and restore that same page's stale result list.
    if(tab==TAB_CATEGORY) {
        collection=null; page=1; catalog=emptyList(); hasMore=false; ranking=null
        catalogFocus=""; chipFocusKey=null; focusFirstCardOnLanding=false
        showCatalog(load=true,focusNav=true)
    } else switchTab(TAB_CATEGORY)
}
/** Shared navigation, including on details. Focus and the selected section remain distinct. */
internal fun MainActivity.miniActHeader(parent: LinearLayout): View {
    nav.clear(); navKeyed.clear(); recommendIcon=null
    (findTagged(root,"miniact:rail")?.parent as? ViewGroup)?.removeView(findTagged(root,"miniact:rail"))
    val rail=column().apply {
        tag="miniact:rail"; setPadding(dp(12),dp(20),dp(12),dp(16))
        background=MiniActGlass(this,22,.70f,opacity=.72f)
    }
    rail.addView(miniActBrand().apply { setPadding(dp(5),0,0,0) },lp(-1,dp(60)))
    val current=when {
        collection!=null -> "history"
        tab==TAB_PROFILE -> "profile"
        tab==TAB_CATEGORY -> when(categorySelection.genre) { "short_play" -> "real"; "ai_series" -> "ai"; else -> "comic" }
        tab==TAB_SEARCH -> "search"
        tab==TAB_RANK -> "rank"
        tab==TAB_SETTINGS -> "settings"
        else -> "home"
    }
    data class Destination(val key: String,val label: String,val icon: Int,val action: ()->Unit)
    val destinations=listOf(
        Destination("home","精选",R.drawable.ic_miniact_nav_grid) { switchTab(TAB_HOME) },
        Destination("recommend","推荐",R.drawable.ic_miniact_nav_recommend) { openRecommend() },
        Destination("search","搜索",R.drawable.ic_miniact_nav_search) { switchTab(TAB_SEARCH) },
        Destination("real","真人剧",R.drawable.ic_miniact_nav_play) { miniActGenre("short_play") },
        Destination("comic","漫剧",R.drawable.ic_miniact_nav_comic) { miniActGenre("comic_series") },
        Destination("ai","AI 剧",R.drawable.ic_miniact_nav_star) { miniActGenre("ai_series") },
        Destination("history","片单",R.drawable.ic_miniact_nav_library) { tab=TAB_HOME; showCollection("history") },
        Destination("rank","榜单",R.drawable.ic_miniact_nav_chart) { switchTab(TAB_RANK) },
        Destination("settings","设置",R.drawable.ic_miniact_nav_settings) { switchTab(TAB_SETTINGS) },
        Destination("profile","账号",R.drawable.ic_miniact_nav_account) { switchTab(TAB_PROFILE) })
    destinations.forEach { destination ->
        val item=NavigationButton(this,destination.label,destination.icon) { destination.action() }
        item.setActive(destination.key==current); item.tag="nav:${destination.key}"
        rail.addView(item,lp(-1,dp(44)).apply { topMargin=dp(if(destination.key=="settings") 20 else 3) })
        nav+=item; navKeyed[destination.key]=item
        if(destination.key=="recommend") recommendIcon=item
        if(destination.key==current && tab==TAB_CATEGORY) navKeyed["category"]=item
    }
    if(navKeyed["category"]==null) navKeyed["category"]=navKeyed["comic"] ?: rail
    nav.forEachIndexed { index,view ->
        view.nextFocusUpId=nav[(index-1).coerceAtLeast(0)].id
        view.nextFocusDownId=nav[(index+1).coerceAtMost(nav.lastIndex)].id
        view.nextFocusLeftId=view.id
    }
    rail.addView(View(this),lp(-1,0).apply { weight=1f })
    root.addView(rail,FrameLayout.LayoutParams(dp(166),-1,Gravity.LEFT).apply {
        leftMargin=dp(16); topMargin=dp(16); bottomMargin=dp(16)
    })
    val top=row().apply { setPadding(pageBleed(),0,pageBleed(),0) }
    top.addView(text("MINIACT",11f,MiniActTokens.muted),lp(0,-2).apply { weight=1f })
    val hint=text("",11f,0xFFA2A7B5.toInt()).apply { setPadding(0,0,dp(22),0) }
    fun updateHint() {
        val next=when {
            screen=="detail" -> "按返回键回到上一页"
            miniActRailFocused() -> if(navKeyed[current]?.hasFocus()==true) "按右键浏览内容"
                else "按确认键进入${(root.findFocus() as? TextView)?.text?.toString().orEmpty()}"
            else -> "按返回键打开菜单"
        }
        if(hint.text.toString()!=next) hint.text=next
    }
    miniNavigationObserver?.let { root.viewTreeObserver.removeOnGlobalFocusChangeListener(it) }
    miniNavigationObserver=ViewTreeObserver.OnGlobalFocusChangeListener { _,_ -> updateHint() }.also {
        root.viewTreeObserver.addOnGlobalFocusChangeListener(it)
    }
    updateHint()
    top.addView(hint)
    top.addView(MiniActClock(this))
    parent.addView(top,lp(-1,dp(30)))
    return top
}
internal fun MainActivity.miniActPageIntro(parent: LinearLayout,title: String,subtitle: String) {
    val block=column().apply { setPadding(pageBleed(),dp(18),pageBleed(),dp(16)) }
    val line=row()
    line.addView(text(title,MiniActTokens.title,TvStyle.text).apply { typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL) },lp(0,-2).apply { weight=1f })
    
    block.addView(line)
    block.addView(text(subtitle,13f,TvStyle.muted).apply { setPadding(0,dp(7),0,0) })
    parent.addView(block,lp(-1,-2)); registerTopAreaRow(block)
}

/** Only rearranges existing views: the official QR flow and its callbacks are unchanged. */
internal fun MainActivity.miniActQrLayout(card: LinearLayout) {
    val children=(0 until card.childCount).map { card.getChildAt(it) }
    val codeAt=children.indexOfFirst { it is FrameLayout }
    if(codeAt<0) return
    card.removeAllViews(); card.orientation=LinearLayout.HORIZONTAL
    card.background=MiniActContentSurface(card,20)
    card.gravity=Gravity.CENTER_VERTICAL
    card.setPadding(dp(36),dp(28),dp(36),dp(28))
    val story=column().apply { setPadding(0,0,dp(40),0) }
    children.take(codeAt).forEach { view -> story.addView(view) }
    val instructions=listOf("01  打开手机上的官方 App","02  扫描右侧二维码","03  在手机上确认登录")
    instructions.forEach { line -> story.addView(text(line,17f,TvStyle.textDim).apply { setPadding(0,dp(12),0,0) }) }
    story.addView(text("把喜欢的故事，留在你的片单里。",15f,TvStyle.muted).apply { setPadding(0,dp(28),0,0) })
    card.addView(story,lp(0,-2).apply { weight=1f })
    val scan=column().apply { gravity=Gravity.CENTER_HORIZONTAL }
    children.drop(codeAt).forEach { view ->
        // MATCH_PARENT is intentional: old WRAP_CONTENT text could measure outside the QR column.
        val old=view.layoutParams as? LinearLayout.LayoutParams
        scan.addView(view,lp(-1,-2).apply { topMargin=old?.topMargin ?: 0; bottomMargin=old?.bottomMargin ?: 0 })
    }
    card.addView(scan,lp(dp(390),-2))
}

/** Keep known artwork and metadata visible while only the episode request is pending. */
internal fun MainActivity.miniActPendingDetail(parent: LinearLayout,series: Series): LinearLayout {
    val scroll=ScrollView(this).apply { isVerticalScrollBarEnabled=false }
    val content=column().apply { setPadding(pageBleed(),dp(26),pageBleed(),dp(24)) }
    val line=row().apply { gravity=Gravity.TOP }
    val cover=ImageView(this).apply { scaleType=ImageView.ScaleType.CENTER_CROP }
    roundCorners(cover,16); loadCover(cover,series.cover)
    line.addView(cover,lp(dp(138),dp(196)))
    val info=column().apply { setPadding(dp(6),dp(18),dp(18),dp(18)) }
    info.addView(text("MINIACT / 短剧",11f,TvStyle.accent))
    info.addView(text(series.title,MiniActTokens.display,TvStyle.text).apply {
        typeface=Typeface.create("sans-serif-medium",0); maxLines=2; ellipsize=android.text.TextUtils.TruncateAt.END
        setPadding(0,dp(10),0,dp(12))
    },lp(-1,-2))
    val meta=listOfNotNull(series.episodeCount.takeIf { it>0 }?.let { "全 $it 集" },series.tags.takeIf { it.isNotBlank() }).joinToString(" · ")
    if(meta.isNotBlank()) info.addView(text(meta,15f,TvStyle.muted),lp(-1,-2))
    if(series.description.isNotBlank()) info.addView(text(series.description,14f,TvStyle.textDim).apply {
        maxLines=2; ellipsize=android.text.TextUtils.TruncateAt.END; setLineSpacing(0f,1.2f)
        setPadding(0,dp(16),0,dp(16))
    },lp(-1,-2))
    val status=column()
    info.addView(status,lp(-1,-2))
    line.addView(info,0,lp(0,-2).apply { weight=1f })
    (cover.layoutParams as LinearLayout.LayoutParams).leftMargin=dp(42)
    content.addView(line,lp(-1,-2)); scroll.addView(content)
    parent.addView(scroll,lp(-1,0).apply { weight=1f })
    return status
}

/** An empty search offers real cached stories, without issuing another content request. */
internal fun MainActivity.miniActSearchDiscovery(parent: LinearLayout) {
    val hidden=library.hidden()
    val pages=if(homeFeed.isNotEmpty()) homeFeed else RankBoard.entries.mapNotNull { board -> library.cachedHome(board)?.let { board to it } }.toMap()
    val seen=HashSet<String>()
    val stories=pages.values.flatMap { it.items }.filter { it.id !in hidden && seen.add(it.id) }.take(12)
    val target=searchInput ?: navKeyed["search"] ?: root
    if(stories.isEmpty()) {
        message(parent,"输入剧名、角色或关键词，发现下一部好故事。")
        gridRestoring=false; ensureFocusView { target }
        return
    }
    val home=HomeScreen(this,target,pageInset(),::loadCover,
        { item,resume -> openSeries(item,resume) },{ item,anchor,shelf -> seriesMenu(item,anchor,shelf) },{ catalogFocus=it },columns=5,viewportWidth=miniActContentWidth()+2*pageBleed())
    homeScreen=home
    parent.addView(home,lp(-1,0).apply { weight=1f })
    val entries=stories.map { HomeScreen.Entry(it,it.tags) }
    home.renderCompact(listOf(HomeScreen.Shelf("discover","也许你会喜欢",entries)),"",false)
    searchInput?.nextFocusDownId=home.firstId()
    searchButton?.nextFocusDownId=home.firstId()
    gridRestoring=false; focusFirstCardOnLanding=false
    ensureFocusView { target }
}
