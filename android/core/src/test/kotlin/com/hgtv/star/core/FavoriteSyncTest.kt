// SPDX-License-Identifier: GPL-3.0-only
package com.hgtv.star.core

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

class FavoriteSyncTest {
    private fun repository(reply:String)=ContentRepository(OkHttpClient.Builder().addInterceptor { chain ->
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(reply.toResponseBody()).build()
    }.build()).apply { credentials=AccountCredentials("sessionid=test") }
    @Test(expected=IOException::class) fun businessErrorIsNotAnEmptyCollection() {
        repository("""{"code":100103,"message":"expired"}""").accountFavorites()
    }
    @Test(expected=SignInRequiredException::class) fun anonymousEmptyShelfCannotConfirmAccountMutation() {
        repository("""{"code":0,"data":{"user_name":"用户12345678","has_set_username":false}}""").verifyFavoriteAccount()
    }
    @Test(expected=SignInRequiredException::class) fun aDifferentAccountCannotConfirmTheCurrentAccountMutation() {
        repository("""{"code":0,"data":{"user_name":"Example","biz_user_id":"222"}}""").verifyFavoriteAccount("111")
    }
    @Test fun successAcknowledgementWithoutARealCollectionChangeIsNotConfirmed() {
        val result=FavoriteSync.update("12",true,{ true },{ emptySet() },{})
        assertEquals(FavoriteSync.Outcome.UNVERIFIED,result)
    }
    @Test fun timeoutAfterServerCommitCanBeConfirmedByReadback() {
        val remote=mutableSetOf<String>()
        val result=FavoriteSync.update("12",true,{ remote.add("12");throw IOException("response lost") },{ remote.toSet() },{})
        assertEquals(FavoriteSync.Outcome.CONFIRMED,result)
    }
    @Test fun delayedVisibilityIsReadAgainWithoutRepeatingTheWrite() {
        var writes=0;var reads=0
        val result=FavoriteSync.update("12",true,{ writes++;true },{ if(++reads<3) emptySet() else setOf("12") },{})
        assertEquals(FavoriteSync.Outcome.CONFIRMED,result);assertEquals(1,writes);assertEquals(3,reads)
    }
    @Test fun unavailableReadbackDoesNotPretendToHaveSynced() {
        assertEquals(FavoriteSync.Outcome.UNVERIFIED,FavoriteSync.update("12",true,{ true },{ throw IOException("offline") },{}))
    }
    @Test fun alreadyMatchingAccountStateDoesNotWriteAgain() {
        var writes=0
        val result=FavoriteSync.update("12",true,{ writes++;true },{ setOf("12") },{})
        assertEquals(FavoriteSync.Outcome.CONFIRMED,result);assertEquals(0,writes)
    }
    @Test fun failedCancellationPreservesTheServerFavorite() {
        assertEquals(FavoriteSync.Outcome.REJECTED,FavoriteSync.update("12",false,{ false },{ setOf("12") },{}))
    }
    @Test fun cancellationIsConfirmedWithoutAffectingOtherTitles() {
        val remote=mutableSetOf("12","13")
        val result=FavoriteSync.update("12",false,{ remote.remove("12") },{ remote.toSet() },{})
        assertEquals(FavoriteSync.Outcome.CONFIRMED,result);assertEquals(setOf("13"),remote)
    }
    @Test fun accountSessionIsFrozenWhenTheMainRepositoryChangesAccount() {
        val repository=ContentRepository()
        val first=AccountCredentials("sessionid=first")
        repository.credentials=first;repository.deviceIdentity=mapOf("device_id" to "123")
        val session=repository.favoriteSession()
        repository.credentials=AccountCredentials("sessionid=second")
        repository.deviceIdentity=mapOf("device_id" to "456")
        assertEquals(first,session.credentials);assertEquals("123",session.deviceIdentity["device_id"])
        repository.http.dispatcher.executorService.shutdownNow();repository.http.connectionPool.evictAll()
    }
    @Test(expected=InterruptedException::class) fun interruptionIsNotRetriedAsANetworkFailure() {
        FavoriteSync.update("12",true,{ throw InterruptedException() },{ emptySet() },{})
    }
}
