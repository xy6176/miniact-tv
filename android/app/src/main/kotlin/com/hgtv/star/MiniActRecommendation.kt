// SPDX-License-Identifier: GPL-3.0-only
package com.hgtv.star

import android.view.View

/** Return to discovery without seeking, rebuilding the decoder, or losing the saved episode. */
internal fun MainActivity.returnToMiniActBrowse() {
    saveProgress(); library.flush()
    dropQueuedEpisode(); cancelPrefetch()
    if(playbackMenu!=null) hidePlaybackOverlay()
    val data=detail
    if(data==null || playerView==null) { exitRecommendToHome(); return }
    screen="recommend"; playbackFromRecommend=false
    recommendCommitId=null; recommendDetailPending=false; recommendHistoryPending=false
    recommendWatchingId=data.series.id
    recommendWatchStart=android.os.SystemClock.elapsedRealtime()
    hud.visibility=View.GONE
    attachRecommendOverlay(data.series)
    syncPlaybackInfo()
    if(!playError) requestPlayback(true)
    scheduleRecommendPreload(slideCache.firstFrameDelay(),"return-to-discovery")
    scheduleRecommendDwell()
}
