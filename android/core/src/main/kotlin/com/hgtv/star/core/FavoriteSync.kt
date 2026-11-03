// SPDX-License-Identifier: GPL-3.0-only
package com.hgtv.star.core

/** An acknowledgement alone is insufficient: some shelf routes return success
 * without changing the video collection. Confirm against the account collection. */
object FavoriteSync {
    enum class Outcome { CONFIRMED, REJECTED, UNVERIFIED }
    fun update(id:String,added:Boolean,write:()->Boolean,read:()->Set<String>,pause:()->Unit={ Thread.sleep(400) },before:(Boolean)->Unit={}):Outcome {
        require(id.isNotBlank())
        val initial=try { id in read() } catch(interrupted:InterruptedException) { throw interrupted } catch(_:Exception) { null }
        initial?.let { before(it);if(it==added)return Outcome.CONFIRMED }
        val accepted=try { write() } catch(interrupted:InterruptedException) { throw interrupted } catch(_:Exception) { false }
        var observed:Boolean?=null
        repeat(2) { attempt ->
            if(attempt>0) pause()
            try {
                observed=id in read()
                if(observed==added) return Outcome.CONFIRMED
            } catch(interrupted:InterruptedException) { throw interrupted } catch(_:Exception) { }
        }
        return if(!accepted && observed!=null) Outcome.REJECTED else Outcome.UNVERIFIED
    }
}
