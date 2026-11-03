// SPDX-License-Identifier: GPL-3.0-only
package com.hgtv.star.core

import org.junit.Assert.*
import org.junit.Test

class FavoriteRefreshTest {
    private fun row(id:String)=AccountFavorite(Series(id,"剧$id","https://example.com/$id.jpg","简介","全20集","剧情"),1,20)
    @Test fun `cover metadata refresh does not rebuild an unchanged collection`() {
        val old=row("1")
        val fresh=old.copy(series=old.series.copy(cover="https://example.com/1.jpg?signature=new",tags="新标签"))
        assertFalse(FavoriteRefresh.catalogChanged(listOf(old),listOf(fresh)))
    }
    @Test fun `new removed and reordered collections update the page`() {
        val old=listOf(row("1"),row("2"))
        assertTrue(FavoriteRefresh.catalogChanged(old,old+row("3")))
        assertTrue(FavoriteRefresh.catalogChanged(old,old.take(1)))
        assertTrue(FavoriteRefresh.catalogChanged(old,old.reversed()))
    }
    @Test fun `title and episode changes update the page`() {
        val old=row("1")
        assertTrue(FavoriteRefresh.catalogChanged(listOf(old),listOf(old.copy(episodes=21))))
        assertTrue(FavoriteRefresh.catalogChanged(listOf(old),listOf(old.copy(series=old.series.copy(title="新剧名")))))
    }
    @Test fun `poll waits thirty seconds after response`() {
        val gate=FavoriteRefresh();val ticket=gate.begin(0)!!
        assertTrue(gate.finish(ticket,2_000))
        assertNull(gate.begin(31_999));assertNotNull(gate.begin(32_000))
    }
    @Test fun `entering bypasses poll interval but deduplicates redraws`() {
        val gate=FavoriteRefresh();val ticket=gate.begin(0)!!
        gate.finish(ticket,1_000)
        assertNull(gate.begin(5_999,true));assertNotNull(gate.begin(6_000,true))
    }
    @Test fun `slow or failing request never starts overlapping reads`() {
        val gate=FavoriteRefresh();val ticket=gate.begin(0)!!
        assertNull(gate.begin(90_000,true))
        gate.finish(ticket,90_000)
        assertNull(gate.begin(91_000));assertNotNull(gate.begin(120_000))
    }
    @Test fun `old account callback cannot finish new account request`() {
        val gate=FavoriteRefresh();val old=gate.begin(0)!!
        gate.reset();val next=gate.begin(100)!!
        assertFalse(gate.finish(old,200));assertNull(gate.begin(50_000,true))
        assertTrue(gate.finish(next,50_000));assertNotNull(gate.begin(80_000))
    }
    @Test fun `full cloud response can mark freshness independently of disk cache`() {
        val gate=FavoriteRefresh();gate.markFresh(10_000)
        assertNull(gate.begin(39_999));assertNotNull(gate.begin(40_000))
    }
}
