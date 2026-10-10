package io.github.jqssun.airplay.discovery

/** Event-driven recovery only; never a periodic receiver restart. Uses elapsed realtime. */
class DiscoveryResumePolicy {
    private var lastRegistration: Long? = null
    fun registrationStarted(now: Long) { lastRegistration = now }
    fun shouldRefresh(now: Long, running: Boolean, lan: Boolean, busy: Boolean,
                      registering: Boolean): Boolean = running && lan && !busy && !registering &&
        (lastRegistration?.let { now - it >= 30_000L } ?: true)
}
