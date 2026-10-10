import io.github.jqssun.airplay.discovery.DiscoveryResumePolicy

fun main() {
    val p = DiscoveryResumePolicy()
    fun eligible(now: Long, busy: Boolean = false, running: Boolean = true,
                 lan: Boolean = true, registering: Boolean = false) =
        p.shouldRefresh(now, running, lan, busy, registering)
    check(eligible(0))
    p.registrationStarted(0)
    check(!eligible(29_999))
    check(eligible(30_000))
    check(!eligible(60_000, busy = true))
    check(!eligible(60_000, running = false))
    check(!eligible(60_000, lan = false))
    check(!eligible(60_000, registering = true))
    // Suppressed wake/resume events must not consume the next idle recovery.
    check(eligible(60_000))
    p.registrationStarted(60_000)
    check(!eligible(60_001))
    check(eligible(90_000))
    println("PASS: resume/wake recovery, cooldown, stopped/offline/active/registration guards")
}
