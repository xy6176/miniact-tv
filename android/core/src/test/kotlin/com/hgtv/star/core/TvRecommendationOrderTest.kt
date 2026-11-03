// SPDX-License-Identifier: GPL-3.0-only
package com.hgtv.star.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class TvRecommendationOrderTest {
    private fun show(id: String,shape: VideoShape)=Series(id,id,vertical=shape==VideoShape.PORTRAIT,orientationKnown=shape!=VideoShape.UNKNOWN)
    @Test fun landscapeFirstWithoutDiscardingFallbacksOrChangingOrderWithinGroups() {
        val rows=listOf(show("p1",VideoShape.PORTRAIT),show("u1",VideoShape.UNKNOWN),show("l1",VideoShape.LANDSCAPE),show("p2",VideoShape.PORTRAIT),show("l2",VideoShape.LANDSCAPE))
        assertEquals(listOf("l1","l2","u1","p1","p2"),TvRecommendationOrder.prioritize(rows).map { it.id })
    }
    @Test fun arrivingPageCannotReplaceCurrentVideoOrReorderBackHistory() {
        val rows=listOf(show("played",VideoShape.PORTRAIT),show("current",VideoShape.UNKNOWN),show("p",VideoShape.PORTRAIT),show("l",VideoShape.LANDSCAPE))
        assertEquals(listOf("played","current","l","p"),TvRecommendationOrder.pending(rows,1).map { it.id })
        assertEquals(rows,TvRecommendationOrder.pending(rows,99))
    }
    @Test fun oldFalseFlagDoesNotInventLandscapeMetadata() {
        val old=Series.fromJson(JSONObject().put("id","old").put("vertical",false))
        assertEquals(VideoShape.UNKNOWN,TvRecommendationOrder.shape(old))
        val horizontal=show("new",VideoShape.LANDSCAPE)
        assertEquals(VideoShape.LANDSCAPE,TvRecommendationOrder.shape(Series.fromJson(horizontal.toJson())))
        assertEquals(VideoShape.PORTRAIT,TvRecommendationOrder.shape(Series.fromJson(JSONObject().put("vertical",true))))
    }
    @Test fun metadataAcceptsBooleanAndNumericFlagsButKeepsMissingUnknown() {
        listOf(false,0,"0","false").forEach { assertEquals(VideoShape.LANDSCAPE,VideoShape.fromVertical(it)) }
        listOf(true,1,"1","true").forEach { assertEquals(VideoShape.PORTRAIT,VideoShape.fromVertical(it)) }
        listOf(null,JSONObject.NULL,"unknown","").forEach { assertEquals(VideoShape.UNKNOWN,VideoShape.fromVertical(it)) }
    }
    @Test fun actualVideoShapeCanOverrideListMetadata() {
        val rows=listOf(show("wrong",VideoShape.LANDSCAPE),show("wide",VideoShape.UNKNOWN))
        val measured=mapOf("wrong" to VideoShape.fromSize(720,1280),"wide" to VideoShape.fromSize(1920,1080))
        assertEquals(listOf("wide","wrong"),TvRecommendationOrder.prioritize(rows) { measured.getValue(it.id) }.map { it.id })
        assertEquals(VideoShape.UNKNOWN,VideoShape.fromSize(0,1080))
        assertEquals(VideoShape.UNKNOWN,VideoShape.fromSize(100,100))
    }
    @Test fun revisitingDoesNotPromoteAlreadyWatchedLandscapeAheadOfFreshStories() {
        val old=show("old",VideoShape.LANDSCAPE)
        val fresh=show("fresh",VideoShape.PORTRAIT)
        assertEquals(listOf(fresh),TvRecommendationOrder.entry(listOf(old,fresh),1,{ it.id=="old" }))
        assertEquals(listOf(old,fresh),TvRecommendationOrder.entry(listOf(old,fresh),1,{ true }))
        assertTrue(TvRecommendationOrder.entry(emptyList(),0,{ false }).isEmpty())
    }
}
