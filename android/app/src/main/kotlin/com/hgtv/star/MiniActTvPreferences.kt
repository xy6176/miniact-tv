// SPDX-License-Identifier: GPL-3.0-only
package com.hgtv.star

import android.content.Context
import com.hgtv.star.core.Series
import com.hgtv.star.core.VideoShape
import com.hgtv.star.core.TvRecommendationOrder
import com.hgtv.star.tv.TvStyle

internal class MiniActTvPreferences(context: Context) {
    private val prefs=context.getSharedPreferences("miniact_tv",Context.MODE_PRIVATE)
    var preferLandscape: Boolean
        get()=prefs.getBoolean("landscape_first",false)
        set(value) { prefs.edit().putBoolean("landscape_first",value).apply() }
    /** Migrate the old automatic video start once, then respect saved choices. */
    fun applyStartupDefault(setDefault: ()->Unit) {
        if(prefs.getBoolean("featured_home_v1",false)) return
        setDefault()
        prefs.edit().putBoolean("featured_home_v1",true).apply()
    }
    fun shape(series: Series): VideoShape = when(prefs.getInt("shape:${series.id}",0)) {
        1 -> VideoShape.LANDSCAPE
        -1 -> VideoShape.PORTRAIT
        else -> TvRecommendationOrder.shape(series)
    }
    /** Actual display dimensions, after pixel aspect ratio and rotation have been applied. */
    fun remember(id: String,width: Int,height: Int) {
        if(id.isBlank()) return
        val value=when(VideoShape.fromSize(width,height)) {
            VideoShape.LANDSCAPE -> 1
            VideoShape.PORTRAIT -> -1
            VideoShape.UNKNOWN -> return
        }
        val key="shape:$id"
        if(prefs.getInt(key,0)==value) return
        val edit=prefs.edit().putInt(key,value)
        val keys=prefs.all.keys.filter { it.startsWith("shape:") && it!=key }
        if(keys.size>=320) keys.take(keys.size-287).forEach { edit.remove(it) }
        edit.apply()
    }
}

internal fun MainActivity.orderMiniActRecommend(items: List<Series>): List<Series> =
    if(TvStyle.television && miniTv.preferLandscape) TvRecommendationOrder.prioritize(items,miniTv::shape) else items

/** A new discovery visit may reorder candidates; playback's already-consumed prefix never moves. */
internal fun MainActivity.miniActRecommendEntry(items: List<Series>,resume: Int): Pair<List<Series>,Int> {
    if(!TvStyle.television || !miniTv.preferLandscape) return items to resume
    val seen=library.recommendSeen()
    return TvRecommendationOrder.entry(items,resume,{ it.id in seen || library.recommendSeenTitle(it.title) },miniTv::shape) to 0
}

internal fun MainActivity.orderMiniActPending(items: List<Series>): List<Series> =
    if(TvStyle.television && miniTv.preferLandscape) TvRecommendationOrder.pending(items,recommendIndex,miniTv::shape) else items

/** A landscape-sorted snapshot must never serve as the first page of mixed recommendations. */
internal fun MainActivity.miniActRecommendContext(): String =
    "miniact-v2:${TvStyle.television && miniTv.preferLandscape}:"+
        library.recommendGenres().sorted().joinToString(",")+":"+library.recommendGender

/** Leave history, favorites and media files intact; only discard the obsolete discovery queue. */
internal fun MainActivity.resetMiniActRecommendations() {
    recommendToken.incrementAndGet(); recommendPageToken.incrementAndGet()
    recommendWaiters.clear(); recommendAdvancePending=0
    closeRecommendCache()
    recommendItems=emptyList(); recommendDetails.clear()
    recommendIndex=0; recommendPage=0; recommendHasMore=true; recommendLoading=false
    recommendRestored=false; recommendStarted=false; recommendTasteCache=null
    recommendSession=""; recommendNext=0; recommendFirstLoadAt=0L; recommendLookaheadAt=0L
    library.recommendCursor=""
    com.hgtv.star.core.RecommendCache.clear()
}
