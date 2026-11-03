// SPDX-License-Identifier: GPL-3.0-only
package com.hgtv.star.core

import org.junit.Assert.*
import org.junit.Test

class EpisodeBrowseTest {
    @Test fun `paging does not change playback and remembers selection`() {
        val browse=EpisodeBrowse(500,64,20)
        assertEquals(3,browse.page);assertEquals(64,browse.focused)
        browse.turn(1);assertEquals(4,browse.page);assertEquals(64,browse.current)
        browse.remember(87);browse.turn(-1);assertEquals(64,browse.focused)
        browse.turn(1);assertEquals(87,browse.focused)
    }
    @Test fun `page boundaries are no ops including empty series`() {
        val empty=EpisodeBrowse(0,0,20);assertFalse(empty.turn(1));assertFalse(empty.locate("1"))
        val browse=EpisodeBrowse(81,80,20)
        assertFalse(browse.turn(1));assertEquals(80,browse.first);assertEquals(81,browse.end)
        assertFalse(browse.showPage(-1));assertFalse(browse.showPage(5))
    }
    @Test fun `exact location validates input without playing`() {
        val browse=EpisodeBrowse(500,64,20)
        for(value in listOf("","0","501","-1","2.5","999999999999999999")) assertFalse(browse.locate(value))
        assertEquals(3,browse.page)
        assertTrue(browse.locate("385"));assertEquals(19,browse.page);assertEquals(384,browse.focused)
        assertEquals(64,browse.current)
    }
    @Test fun `playback advance does not drag the browsing page away`() {
        val browse=EpisodeBrowse(500,64,20);browse.showPage(20);browse.remember(403)
        browse.update(500,65)
        assertEquals(20,browse.page);assertEquals(403,browse.focused);assertEquals(65,browse.current)
        browse.locateCurrent();assertEquals(3,browse.page);assertEquals(65,browse.focused)
    }
    @Test fun `shrinking episode list clamps stale pages safely`() {
        val browse=EpisodeBrowse(500,480,20);browse.update(32,31)
        assertEquals(1,browse.page);assertTrue(browse.focused in 20..31)
        browse.update(0,0);assertEquals(0,browse.page);assertEquals(0,browse.pageCount)
    }
    @Test fun `five hundred episodes expose hundred episode blocks`() {
        val browse=EpisodeBrowse(500,64,20);val sections=browse.sections()
        assertEquals(5,sections.size)
        assertEquals("1—100 集",browse.rangeLabel(sections.first()))
        assertEquals("401—500 集",browse.rangeLabel(sections.last()))
        assertEquals(5,browse.sections(sections.last()).size)
        assertEquals("481—500 集",browse.rangeLabel(browse.sections(sections.last()).last()))
    }
    @Test fun `all range levels are bounded contiguous and narrower`() {
        for(total in listOf(1,81,500,1000,9999,Int.MAX_VALUE)) {
            val browse=EpisodeBrowse(total,0,20)
            var range=EpisodeBrowse.Range(0,browse.pageCount)
            var depth=0
            while(range.untilPage-range.fromPage>1) {
                val children=browse.sections(range)
                assertTrue(children.size in 2..8)
                assertEquals(range.fromPage,children.first().fromPage)
                assertEquals(range.untilPage,children.last().untilPage)
                children.zipWithNext().forEach { (a,b) -> assertEquals(a.untilPage,b.fromPage) }
                assertTrue(children.last().untilPage-children.last().fromPage<range.untilPage-range.fromPage)
                range=children.last();assertTrue(++depth<20)
            }
        }
    }
}
