// SPDX-License-Identifier: GPL-3.0-only
package com.hgtv.star.core

/** Video metadata only. Poster dimensions say nothing about a video's display shape. */
enum class VideoShape { LANDSCAPE, UNKNOWN, PORTRAIT;
    companion object {
        fun fromVertical(value: Any?): VideoShape = when(value?.toString()?.trim()?.lowercase()) {
            "false", "0" -> LANDSCAPE
            "true", "1" -> PORTRAIT
            else -> UNKNOWN
        }
        fun fromSize(width: Int,height: Int): VideoShape = when {
            width<=0 || height<=0 || width==height -> UNKNOWN
            width>height -> LANDSCAPE
            else -> PORTRAIT
        }
    }
}

/** Stable preference, never a filter. Unknown records remain unknown, including old backups. */
object TvRecommendationOrder {
    fun shape(series: Series)=when {
        series.vertical -> VideoShape.PORTRAIT
        series.orientationKnown -> VideoShape.LANDSCAPE
        else -> VideoShape.UNKNOWN
    }
    fun prioritize(items: List<Series>,shape: (Series)->VideoShape = ::shape): List<Series> {
        val groups=Array(3) { ArrayList<Series>() }
        items.forEach { groups[shape(it).ordinal].add(it) }
        return groups.flatMap { it }
    }
    /** On a new visit, prefer unseen candidates before considering aspect ratio.
     * Otherwise rotating a cache would promote yesterday's landscape videos again. */
    fun entry(items: List<Series>,start: Int,seen: (Series)->Boolean,shape: (Series)->VideoShape = ::shape): List<Series> {
        val pivot=start.coerceIn(0,items.size)
        val rotated=items.drop(pivot)+items.take(pivot)
        val fresh=rotated.filterNot(seen)
        return prioritize(fresh.ifEmpty { rotated },shape)
    }
    /** Keep the played prefix and current item fixed when a page arrives during playback. */
    fun pending(items: List<Series>,current: Int,shape: (Series)->VideoShape = ::shape): List<Series> {
        val count=(current+1).coerceIn(0,items.size)
        return items.take(count)+prioritize(items.drop(count),shape)
    }
}
