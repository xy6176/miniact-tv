// SPDX-License-Identifier: GPL-3.0-only
package com.hgtv.star.core

import java.text.Normalizer
import java.util.Locale

/** Order visible results without dropping platform aliases or changing paging cursors. */
object SearchRanking {
    private fun key(value:String)=Normalizer.normalize(value,Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }
    fun matches(title:String,query:String):Boolean {
        val queryKey=key(query)
        return queryKey.isNotEmpty() && key(title).contains(queryKey)
    }
    fun order(items:List<Series>,query:String):List<Series> {
        val queryKey=key(query)
        if(queryKey.isEmpty()) return items
        return items.sortedBy { item ->
            val title=key(item.title)
            when { title==queryKey -> 0; title.startsWith(queryKey) -> 1; title.contains(queryKey) -> 2; else -> 3 }
        }
    }
}
