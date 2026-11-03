// SPDX-License-Identifier: GPL-3.0-only
package com.hgtv.star.core

import org.junit.Assert.*
import org.junit.Test
import com.hgtv.star.core.DirectionalFocus.Box
import com.hgtv.star.core.DirectionalFocus.Direction.*

class DirectionalFocusTest {
    private val play=Box(1,300,420,780,500)
    @Test fun `down ignores nearby sidebar and reaches episodes`() {
        assertEquals(3,DirectionalFocus.next(play,listOf(Box(2,40,568,255,634,true),Box(3,306,689,393,743)),DOWN))
    }
    @Test fun `up at content top never falls into sidebar`() {
        assertNull(DirectionalFocus.next(play,listOf(Box(2,40,150,255,214,true)),UP))
    }
    @Test fun `aligned content wins over a diagonal shortcut`() {
        assertEquals(3,DirectionalFocus.next(play,listOf(Box(2,900,520,1000,590),Box(3,350,690,500,740)),DOWN))
    }
    @Test fun `closest row then closest column wins`() {
        assertEquals(3,DirectionalFocus.next(play,listOf(Box(2,300,800,440,850),Box(3,450,600,600,650),Box(4,310,600,390,650)),DOWN))
    }
    @Test fun `right never wraps left to a sidebar`() {
        assertNull(DirectionalFocus.next(play,listOf(Box(2,40,420,255,500,true),Box(3,100,420,200,500)),RIGHT))
    }
    @Test fun `right selects next content control`() {
        assertEquals(2,DirectionalFocus.next(play,listOf(Box(2,800,420,1180,500),Box(3,900,650,1000,700)),RIGHT))
    }
    @Test fun `empty hidden sized or same row targets cannot steal down`() {
        assertNull(DirectionalFocus.next(play,listOf(play,Box(2,800,420,1180,500),Box(3,300,600,300,650)),DOWN))
    }
    @Test fun `up chooses the previous aligned control`() {
        val cell=Box(3,320,790,460,860)
        assertEquals(2,DirectionalFocus.next(cell,listOf(play,Box(2,306,690,393,743)),UP))
    }
}
