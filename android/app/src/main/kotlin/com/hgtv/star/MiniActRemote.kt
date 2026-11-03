// SPDX-License-Identifier: GPL-3.0-only
package com.hgtv.star

import android.view.KeyEvent

internal fun MainActivity.cancelMiniActCenter() {
    centerPress?.let { main.removeCallbacks(it) }
    centerPress=null
    centerHeld=false
}

internal fun MainActivity.dispatchMiniActCenter(event: KeyEvent): Boolean {
    when(event.action) {
        KeyEvent.ACTION_DOWN -> if(event.isLongPress) {
            // Some remotes report a flagged long press without waiting for our timer.
            // Consume its release as well, so opening the menu cannot also pause playback
            // or activate the menu's first control.
            cancelMiniActCenter()
            if(screen=="player" && foreground && !panel) {
                centerHeld=true
                showPlaybackMenu()
            }
        } else if(event.repeatCount==0) {
            cancelMiniActCenter()
            val pending=Runnable {
                centerPress=null
                if(screen=="player" && foreground && !panel) {
                    centerHeld=true
                    showPlaybackMenu()
                }
            }
            centerPress=pending
            main.postDelayed(pending,500)
        }
        KeyEvent.ACTION_UP -> {
            val shortPress=centerPress!=null && !centerHeld && !event.isCanceled
            cancelMiniActCenter()
            if(shortPress && screen=="player" && foreground && !panel) togglePlayback()
        }
    }
    return true
}
