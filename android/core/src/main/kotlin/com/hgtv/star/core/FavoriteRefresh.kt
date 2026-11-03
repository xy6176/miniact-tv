// SPDX-License-Identifier: GPL-3.0-only
package com.hgtv.star.core

/** Main-thread gate for cloud reads. Cache writes and playback progress must not
 * postpone remote collection refreshes. Reset also invalidates old callbacks. */
class FavoriteRefresh {
    companion object {
        /** A membership read updates metadata without rebuilding the page for
         * changing cover URLs or auxiliary tags. Navigation uses fresh metadata. */
        fun catalogChanged(previous:List<AccountFavorite>,fresh:List<AccountFavorite>):Boolean {
            fun key(row:AccountFavorite)=listOf(row.series.id,row.series.title,row.series.badge,row.episodes.toString())
            return previous.map(::key)!=fresh.map(::key)
        }
    }
    private var sequence=0L
    private var active:Long?=null
    private var attemptedAt:Long?=null

    fun begin(now:Long,entering:Boolean=false):Long? {
        if(active!=null) return null
        val interval=if(entering) 5_000L else 30_000L
        if(attemptedAt?.let { now-it in 0 until interval }==true) return null
        attemptedAt=now
        return (++sequence).also { active=it }
    }
    fun finish(ticket:Long,now:Long):Boolean {
        if(active!=ticket) return false
        active=null;attemptedAt=now
        return true
    }
    fun markFresh(now:Long) { attemptedAt=now }
    fun reset() { sequence++;active=null;attemptedAt=null }
}
