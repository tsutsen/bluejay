package com.tsutsen.platformplayer.sabr

import com.tsutsen.platformplayer.sabr.proto.CuepointEvent
import com.tsutsen.platformplayer.sabr.proto.CuepointList

/**
 * Tracks active ad cuepoints and their expiry. Verbatim state/handling from
 * SabrSession (adCuepoints / onCuepointList / expireCuepoints).
 */
internal class SabrCuepointTracker {
    private val cuepoints = java.util.concurrent.ConcurrentSkipListMap<String, Long>()

    val isEmpty: Boolean get() = cuepoints.isEmpty()
    val size: Int get() = cuepoints.size
    val ids: Collection<String> get() = cuepoints.keys

    fun clear() {
        cuepoints.clear()
    }

    fun onCuepointList(list: CuepointList) {
        for (info in list.cuepointInfoList) {
            val cuepoint = info.cuepoint ?: continue
            val id = cuepoint.identifier
            if (id.isNullOrEmpty()) continue

            if (cuepoint.event == CuepointEvent.CUEPOINT_EVENT_STOP) {
                if (cuepoints.remove(id) != null)
                    SabrSession.sabrLog("Cuepoint $id stopped")
                continue
            }

            val endMs = info.timeRange?.takeIf { it.timescale > 0 }?.let {
                (it.startTicks + it.durationTicks) * 1000 / it.timescale
            } ?: 0
            val expiryMs = maxOf(endMs, (cuepoint.offsetSec + cuepoint.durationSec).toLong() * 1000)

            if (cuepoints.put(id, expiryMs) == null) {
                SabrSession.sabrLog("Cuepoint $id (${cuepoint.type}, ${cuepoint.event}, ${cuepoint.durationSec}s)")
            }
        }
    }

    fun expire(positionMs: Long) {
        if (cuepoints.isEmpty()) return
        val iterator = cuepoints.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value in 1 until positionMs) {
                SabrSession.sabrLog("Cuepoint ${entry.key} has passed (${entry.value}ms < ${positionMs}ms); dropping it")
                iterator.remove()
            }
        }
    }
}
