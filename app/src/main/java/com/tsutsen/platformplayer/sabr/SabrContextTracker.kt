package com.tsutsen.platformplayer.sabr

import com.tsutsen.platformplayer.sabr.proto.SabrContext
import com.tsutsen.platformplayer.sabr.proto.SabrContextSendingPolicy
import com.tsutsen.platformplayer.sabr.proto.SabrContextUpdate
import java.util.concurrent.ConcurrentHashMap

/**
 * Holds the SABR contexts received from the server and the set of context
 * types that must be sent back. Verbatim state/handling from SabrSession.
 */
internal class SabrContextTracker {
    private val contexts = ConcurrentHashMap<Int, SabrContext>()
    private val active = java.util.Collections.newSetFromMap(ConcurrentHashMap<Int, Boolean>())

    fun snapshot(): Map<Int, SabrContext> = contexts.toMap()
    fun activeSnapshot(): Set<Int> = active.toSet()
    fun sentSnapshot(): List<Int> = active.sorted()
    fun heldSnapshot(): List<Int> = contexts.keys.sorted()
    fun activeHeld(): Collection<SabrContext> = contexts.filterKeys { it in active }.values
    fun inactiveKeys(): Set<Int> = contexts.keys.filter { it !in active }.toSet()

    fun inherit(contexts: Map<Int, SabrContext>, active: Set<Int>) {
        this.contexts.putAll(contexts)
        this.active.addAll(active)
    }

    fun applyUpdate(update: SabrContextUpdate) {
        SabrSession.sabrLog("SabrContextUpdate type=${update.type} scope=${update.scope} " +
            "sendByDefault=${update.sendByDefault} valueBytes=${update.value.size()}")

        contexts[update.type] = SabrContext.newBuilder()
            .setType(update.type)
            .setValue(update.value)
            .build()

        if (update.sendByDefault) active.add(update.type)
    }

    fun applySendingPolicy(policy: SabrContextSendingPolicy) {
        SabrSession.sabrLog("SabrContextSendingPolicy start=${policy.startPolicyList} " +
            "stop=${policy.stopPolicyList} discard=${policy.discardPolicyList}")

        active.addAll(policy.startPolicyList)
        active.removeAll(policy.stopPolicyList.toSet())
        for (type in policy.discardPolicyList) {
            contexts.remove(type)
            active.remove(type)
        }
    }
}
