// SPDX-License-Identifier: GPL-3.0-only
package com.hgtv.star.core

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeFocusKeyTest {
    @Test fun returnFromDetailKeepsTheOriginalShelfRatherThanAnEarlierDuplicate() {
        assertEquals("rank-real:70",HomeFocusKey.resolve("70","rank-real:70"))
        assertEquals("history:70",HomeFocusKey.resolve("70","history:70"))
    }
    @Test fun aDifferentExplicitShowWinsAndEmptyRequestsRestoreTheSavedCard() {
        assertEquals("80",HomeFocusKey.resolve("80","rank-real:70"))
        assertEquals("rank-real:70",HomeFocusKey.resolve("","rank-real:70"))
        assertEquals("rank-real:70",HomeFocusKey.resolve("rank-real:70","rank-real:70"))
        assertEquals("70",HomeFocusKey.resolve("70",""))
    }
}
