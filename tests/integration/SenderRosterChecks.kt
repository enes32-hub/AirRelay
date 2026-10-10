import io.github.jqssun.airplay.prototype.SenderRoster
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

fun main() {
    var checks = 0
    fun expect(ok: Boolean) { check(ok); checks++ }
    val roster = SenderRoster()
    val leases = (1..4).map {
        (roster.admitVerified("key-$it", "Device $it") as SenderRoster.Admission.Accepted).lease
    }
    expect(roster.snapshot().devices.size == 4)
    expect(roster.snapshot().showSwitcher)
    expect(roster.snapshot().selected == leases[0])
    val before = roster.snapshot()
    expect(roster.admitVerified("key-5", "Device 5") == SenderRoster.Admission.Full)
    expect(roster.admitVerified("key-6", "Device 6") == SenderRoster.Admission.Full)
    expect(roster.snapshot() == before)
    leases.forEach { expect(roster.select(it)); expect(roster.snapshot().selected == it) }
    val replacement = roster.admitVerified("key-4", "Renamed") as SenderRoster.Admission.Accepted
    expect(replacement.replaced == leases[3])
    expect(roster.snapshot().selected == replacement.lease)
    expect(!roster.disconnect(leases[3]))
    expect(!roster.select(leases[3]))
    expect(roster.snapshot().devices.size == 4)
    expect(roster.disconnect(replacement.lease))
    expect(roster.snapshot().selected == leases[0])
    expect(roster.admitVerified("key-5", "Device 5") is SenderRoster.Admission.Accepted)
    roster.clear()
    val restarted = (roster.admitVerified("key-1", "Device 1") as SenderRoster.Admission.Accepted).lease
    expect(!roster.disconnect(leases[0]))
    expect(!roster.snapshot().showSwitcher)
    expect(roster.snapshot().selected == restarted)

    // Six simultaneous admissions must never oversubscribe or evict an owner.
    repeat(100) {
        val concurrent = SenderRoster()
        val pool = Executors.newFixedThreadPool(6)
        val start = CountDownLatch(1)
        val attempts = (1..6).map { n -> pool.submit<SenderRoster.Admission> {
            start.await()
            concurrent.admitVerified("key-$n", "Same display name")
        } }
        start.countDown()
        val results = attempts.map { it.get(5, TimeUnit.SECONDS) }
        pool.shutdown()
        expect(results.count { it is SenderRoster.Admission.Accepted } == 4)
        expect(results.count { it == SenderRoster.Admission.Full } == 2)
        expect(concurrent.snapshot().devices.size == 4)
    }
    println("PASS: $checks admission/selection model checks; NOT AirPlay device tests")
}
