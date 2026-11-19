package com.tsutsen.platformplayer.core.data.repository.impl

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Session-scoped counters for the transparent source-recovery machinery
 * ([SourceRecoveryManager]). Diagnostics only — no network, no analytics
 * pipeline; read via [snapshot] and logged at INFO by the owner.
 *
 * Counter names:
 * - `errorRecoveries` — total error-triggered recoveries started.
 * - `errorRecovery:<cause>` — per-cause breakdown, `<cause>` being
 *   `marker:<RecoverableSourceError subclass>` (SABR po-token rejection)
 *   or `io:<PlaybackException.errorCodeName>` (progressive streams).
 * - `resumeRefreshes` — expiry-aware pre-emptive re-resolves on resume.
 * - `recoveryFailures` — resolve produced no MediaSource or threw.
 * - `budgetExhausted` — recoveries refused because the play's budget ran out.
 */
internal class SourceRecoveryTelemetry {
    private val counters = ConcurrentHashMap<String, AtomicLong>()

    fun recordErrorRecovery(causeKey: String) {
        bump("errorRecoveries")
        bump("errorRecovery:$causeKey")
    }

    fun recordResumeRefresh() {
        bump("resumeRefreshes")
    }

    fun recordRecoveryFailure() {
        bump("recoveryFailures")
    }

    fun recordBudgetExhausted() {
        bump("budgetExhausted")
    }

    fun snapshot(): Map<String, Long> =
        counters.entries.associate { it.key to it.value.get() }.toSortedMap()

    private fun bump(name: String) {
        counters.getOrPut(name) { AtomicLong() }.incrementAndGet()
    }
}
