// SPDX-License-Identifier: GPL-3.0-only
package com.hgtv.star.core

/** Browsing never changes the playing episode. Per-page focus survives paging. */
class EpisodeBrowse(total:Int,playing:Int,pageSize:Int) {
    val perPage=pageSize.coerceIn(10,50)/10*10
    var count=total.coerceAtLeast(0);private set
    var current=playing.coerceIn(0,(count-1).coerceAtLeast(0));private set
    var page=current/perPage;private set
    private val remembered=linkedMapOf(page to current)
    val pageCount get()=if(count==0) 0 else (count-1)/perPage+1
    val first get()=page*perPage
    val end get()=minOf(first.toLong()+perPage,count.toLong()).toInt()
    val focused get()=if(count==0) 0 else (remembered[page] ?: first).coerceIn(first,end-1)
    fun remember(index:Int) {
        if(index !in first until end) return
        remembered[page]=index
        if(remembered.size>32) remembered.remove(remembered.keys.first())
    }
    fun showPage(target:Int):Boolean {
        if(target !in 0 until pageCount || target==page) return false
        page=target
        return true
    }
    fun turn(delta:Int)=showPage((page.toLong()+delta).coerceIn(0,(pageCount-1).coerceAtLeast(0).toLong()).toInt())
    fun locate(number:String):Boolean {
        val oneBased=number.trim().toIntOrNull() ?: return false
        if(oneBased !in 1..count) return false
        page=(oneBased-1)/perPage;remember(oneBased-1)
        return true
    }
    fun locateCurrent() { if(count>0) { page=current/perPage;remember(current) } }
    fun update(total:Int,playing:Int) {
        count=total.coerceAtLeast(0);current=playing.coerceIn(0,(count-1).coerceAtLeast(0))
        page=page.coerceIn(0,(pageCount-1).coerceAtLeast(0))
        remembered.keys.filter { it>=pageCount }.forEach { remembered.remove(it) }
        if(count>0) remember(focused)
    }
    data class Range(val fromPage:Int,val untilPage:Int)
    fun rangeLabel(range:Range):String =
        "${range.fromPage.toLong()*perPage+1}—${minOf(range.untilPage.toLong()*perPage,count.toLong())} 集"
    /** At most eight choices per level, so a long series never builds an
     * unbounded row of pages or requires walking every intervening page. */
    fun sections(range:Range=Range(0,pageCount)):List<Range> {
        val start=range.fromPage.coerceIn(0,pageCount)
        val end=range.untilPage.coerceIn(start,pageCount)
        val size=end-start
        if(size==0) return emptyList()
        var step=1L
        while((size+step-1)/step>8) step*=5
        return generateSequence(start.toLong()) { it+step }.takeWhile { it<end }
            .map { Range(it.toInt(),minOf(it+step,end.toLong()).toInt()) }.toList()
    }
}
