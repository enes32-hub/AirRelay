import io.github.jqssun.airplay.renderer.SmartVideoQuality

fun main() {
    val policy = SmartVideoQuality()
    check(!policy.dropped(0, 1, 10))
    check(!policy.dropped(0, 12, 0))
    check(!policy.dropped(0, 100, 60_000))
    check(!policy.dropped(0, 12, 5_000))
    check(policy.dropped(0, 12, 2_000))
    check(policy.constrained)
    check(!policy.dropped(500, 12, 2_000))
    for (time in 1_000L..59_000L step 1_000) check(!policy.tick(time, true))
    check(!policy.tick(60_000, false))
    for (time in 61_000L..119_000L step 1_000) check(!policy.tick(time, true))
    check(policy.tick(120_000, true))
    check(!policy.constrained)
    check(policy.dropped(121_000, 20, 1_000))
    check(!policy.tick(300_000, true)) // Missing callbacks are not healthy playback.
    check(policy.constrained)
    policy.reset()
    check(!policy.constrained)
    check(!policy.tick(400_000, true))
    check(policy.dropped(410_000, 100, 10_000)) // Sustained overload, not only short bursts.
    println("PASS smart quality: pressure filtering, hysteresis, paused/buffering recovery, sparse ticks, reset")
}
