// SPDX-License-Identifier: GPL-3.0-only
package com.hgtv.star.core

/** Detail navigation remembers a bare series id; the home has shelf-qualified keys.
 * Preserve the saved shelf when the same show appears in history and a ranking. */
object HomeFocusKey {
    fun resolve(requested:String,saved:String):String = when {
        saved.isNotBlank() && (requested.isBlank() || requested==saved || requested==saved.substringAfter(':',"")) -> saved
        requested.isNotBlank() -> requested
        else -> saved
    }
}
