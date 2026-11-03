// SPDX-License-Identifier: GPL-3.0-only
package com.hgtv.star

import android.util.Log
import com.hgtv.star.core.FavoriteSync
import com.hgtv.star.core.FavoriteRefresh
import com.hgtv.star.core.Series
import com.hgtv.star.core.SignInRequiredException
import com.hgtv.star.tv.TvButton
import android.widget.TextView

/** The signed-in collection is authoritative. Keep legacy local records on disk
 * for offline use; never delete them just because another account is active. */
internal fun MainActivity.accountFavoriteItems():List<Series>? = accountData?.takeIf {
    repository.signedIn && "收藏" !in it.errors
}?.favorites?.map { it.series }

/** Phone-side changes have no push channel here. Pull only the collection,
 * independently of the five-minute account snapshot and local cache writes. */
internal fun MainActivity.refreshAccountFavorites(entering:Boolean=false) {
    if(!foreground || isDestroyed || !library.isLoaded || !repository.signedIn ||
        accountLoading || favoritePending.isNotEmpty()) return
    val now=android.os.SystemClock.elapsedRealtime()
    val ticket=favoriteRefresh.begin(now,entering) ?: return
    if(accountData==null) {
        favoriteRefresh.finish(ticket,now)
        loadAccount(force=true)
        return
    }
    val epoch=favoriteAccountEpoch
    val revision=favoriteRevision
    val credentials=repository.credentials;val token=repository.sessionToken
    val expectedUserId=accountData?.profile?.userId.orEmpty()
    val session=repository.favoriteSession()
    io.submit {
        val result=runCatching {
            session.verifyFavoriteAccount(expectedUserId)
            session.accountFavorites()
        }
        main.post {
            if(isDestroyed || !favoriteRefresh.finish(ticket,android.os.SystemClock.elapsedRealtime())) return@post
            if(epoch!=favoriteAccountEpoch || credentials!=repository.credentials || token!=repository.sessionToken ||
                revision!=favoriteRevision || favoritePending.isNotEmpty()) return@post
            result.onSuccess { rows ->
                val previous=accountData ?: return@onSuccess
                val next=previous.copy(favorites=rows,errors=previous.errors-"收藏")
                val changed=FavoriteRefresh.catalogChanged(previous.favorites,rows)
                favoriteRevision++
                // A collection-only read must not extend the five-minute
                // freshness of profile/history/likes/reservations.
                val accountSavedAt=library.accountCache()?.savedAt ?: System.currentTimeMillis()
                accountData=next;library.cacheAccount(next,accountSavedAt)
                if(foreground && (changed || "收藏" in previous.errors)) refreshAccountViews(setOf("收藏"))
                Log.i("MiniAct-Favorite","Cloud refresh confirmed count=${rows.size} changed=$changed")
            }.onFailure {
                // Keep the last confirmed list, including when an expired login
                // was silently downgraded to an anonymous empty shelf.
                Log.i("MiniAct-Favorite","Cloud refresh unavailable; cached collection retained")
            }
        }
    }
}

internal fun MainActivity.favoriteLabel(id:String):String = when {
    id in favoritePending -> "正在同步…"
    favoriteFailures[id]==true -> "重试收藏"
    favoriteFailures[id]==false -> "重试取消"
    isFavorite(id) -> "已收藏"
    else -> "收藏这部剧"
}
internal fun MainActivity.favoriteHint(id:String):String = when {
    id in favoritePending -> "同步中"
    id in favoriteFailures -> "请重试"
    !repository.signedIn -> "登录后同步"
    isFavorite(id) -> if(accountFavoriteItems()!=null) "账号收藏" else "待确认同步"
    else -> "同步至账号"
}
internal fun MainActivity.refreshFavoriteControls() {
    val series=detail?.series ?: pendingDetail ?: return
    root.findViewWithTag<TvButton>("favorite:${series.id}")?.let {
        it.text=favoriteLabel(series.id)
        it.contentDescription="${it.text}，${favoriteHint(series.id)}"
        it.setSelectedState(isFavorite(series.id))
    }
    root.findViewWithTag<TextView>("favorite-status:${series.id}")?.text=favoriteDescription(series.id)
    playbackMenu?.favoriteAction(favoriteLabel(series.id),favoriteHint(series.id),isFavorite(series.id)) { toggleAccountFavorite(series) }
}
internal fun MainActivity.favoriteDescription(id:String):String = when {
    id in favoritePending -> "正在更新账号收藏…"
    id in favoriteFailures -> "收藏同步尚未确认，选择上方按钮重试"
    !repository.signedIn -> "登录账号后，收藏可在手机与电视间同步"
    else -> "收藏同步至当前账号，可在手机与电视上查看"
}
/** All entry points use the same confirmed account mutation; no optimistic cache
 * can claim that a failed write synchronized. Keep focus while pending. */
internal fun MainActivity.toggleAccountFavorite(series:Series) {
    if(series.id in favoritePending) return
    if(!repository.signedIn) { showActionFeedback("请先在账号页登录，收藏将同步至账号");return }
    val added=favoriteFailures[series.id] ?: !isFavorite(series.id)
    val epoch=favoriteAccountEpoch
    val credentials=repository.credentials;val token=repository.sessionToken
    val expectedUserId=accountData?.profile?.userId.orEmpty()
    val session=repository.favoriteSession()
    favoriteFailures.remove(series.id);favoritePending.add(series.id);refreshFavoriteControls()
    io.submit {
        var needsSignIn=false
        val outcome=runCatching {
            session.verifyFavoriteAccount(expectedUserId)
            FavoriteSync.update(series.id,added,
            { if(added) session.accountAddFavorite(series.id) else session.accountRemoveFavorite(series.id) },
            { session.accountFavorites().map { it.series.id }.toSet() },
            before={ saved -> Log.i("MiniAct-Favorite","Before id=${series.id} saved=$saved") })
        }.onFailure { if(it is SignInRequiredException) needsSignIn=true }.getOrDefault(FavoriteSync.Outcome.UNVERIFIED)
        main.post {
            if(isDestroyed) return@post
            if(epoch!=favoriteAccountEpoch || credentials!=repository.credentials || token!=repository.sessionToken) {
                if(epoch==favoriteAccountEpoch) { favoritePending.remove(series.id);refreshFavoriteControls() }
                return@post
            }
            favoritePending.remove(series.id)
            if(outcome==FavoriteSync.Outcome.CONFIRMED) {
                favoriteRevision++
                if(library.favorite(series.id)!=added) library.toggle(series)
                if(added && series.episodeCount>0) library.observeFavorite(series.id,series.episodeCount,System.currentTimeMillis(),acknowledge=true)
                applyAccountFavorite(series,added)
                refreshLibraryCards(series.id,!added && collection=="favorites")
                showActionFeedback(if(added) "已收藏，已同步至账号" else "已取消收藏，账号已同步")
                Log.i("MiniAct-Favorite","Confirmed id=${series.id} added=$added")
            } else {
                favoriteFailures[series.id]=added
                showActionFeedback(if(needsSignIn) "登录已失效，请在账号页重新登录" else if(outcome==FavoriteSync.Outcome.REJECTED) "账号收藏未更新，请重试" else "暂未确认账号收藏，请重试")
                Log.i("MiniAct-Favorite","Unconfirmed id=${series.id} added=$added")
            }
            refreshFavoriteControls()
        }
    }
}
