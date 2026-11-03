// SPDX-License-Identifier: GPL-3.0-only
package com.hgtv.star.core

import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class SearchFanoutTest {
    private fun repository(respond:(Request)->String)=ContentRepository(
        OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(respond(chain.request()).toResponseBody(null)).build()
        }.build(),System::currentTimeMillis,0L,0L)
    private fun body(tab:Int)=JSONObject().put("code",0).put("search_tabs",JSONArray().put(
        JSONObject().put("tab_type",tab).put("has_more",false).put("data",JSONArray().put(JSONObject().put("video_data",JSONArray().put(
            JSONObject().put("series_id",tab.toString()).put("title","剧集 $tab").put("episode_cnt",8))))))).toString()

    @Test fun registeredTabsRunTogetherKeepOrderAndShareCancellationScope() {
        val both=CountDownLatch(2);val scope=RequestScope()
        val repo=repository { request ->
            assertSame(scope,RequestScope.current())
            both.countDown();assertTrue("both independent tabs must start before either finishes",both.await(2,TimeUnit.SECONDS))
            body(request.url.queryParameter("tab_type")!!.toInt())
        }
        repo.deviceIdentity=mapOf("device_id" to "12345","install_id" to "67890")
        val items=scope.run { repo.search("剧集").items }
        assertEquals(ContentType.entries.filter { it in ContentType.searchable }.map { it.searchTab.toString() },items.map { it.id })
    }
    @Test fun oneFailedTabDoesNotDiscardTheSuccessfulTab() {
        val repo=repository { request ->
            val tab=request.url.queryParameter("tab_type")!!.toInt()
            if(tab==11) throw IOException("temporary failure")
            body(tab)
        }
        repo.deviceIdentity=mapOf("device_id" to "12345")
        assertEquals(listOf("19"),repo.search("剧集").items.map { it.id })
    }
    @Test fun unregisteredBlankDeviceStillStopsBeforeAnotherTab() {
        val requests=AtomicInteger();val repo=repository { requests.incrementAndGet();"" }
        assertThrows(IOException::class.java) { repo.search("剧集") }
        assertEquals(3,requests.get())
    }
}
