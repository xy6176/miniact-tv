package com.hgtv.star.core

import org.junit.Assert.*
import org.junit.Test

class SearchRankingTest {
    private fun item(id:String,title:String)=Series(id,title,"")
    @Test fun exactThenPrefixThenContainsAndKeepOtherResultsStable() {
        val items=listOf(item("a","平台相关甲"),item("b","归墟第一季"),item("c","重返归墟"),item("d","归墟"),item("e","平台相关乙"))
        assertEquals(listOf("d","b","c","a","e"),SearchRanking.order(items,"归墟").map { it.id })
        assertEquals(items.size,SearchRanking.order(items,"归墟").size)
    }
    @Test fun typographyDoesNotHideMatchingTitles() {
        assertTrue(SearchRanking.matches("Ｍｉｎｉ Ａｃｔ：归墟","mini act归墟"))
        assertTrue(SearchRanking.matches("归 墟 · 第一季","归墟"))
        assertFalse(SearchRanking.matches("其他作品","归墟"))
    }
    @Test fun noTitleMatchesPreservesThePlatformOrder() {
        val items=listOf(item("1","甲"),item("2","乙"))
        assertEquals(items,SearchRanking.order(items,"未找到的剧名"))
        assertEquals(items,SearchRanking.order(items," 🪐 — "))
        assertFalse(SearchRanking.matches("任何作品"," — "))
    }
}
