import io.github.jqssun.airplay.receiver.ReceiverInput
import io.github.jqssun.airplay.receiver.ReceiverName
import io.github.jqssun.airplay.receiver.ReceiverPortGuard
import io.github.jqssun.airplay.receiver.ReceiverPortPolicy
import java.net.ServerSocket
import java.net.DatagramSocket

fun main() {
    var checks = 0
    fun expect(value: Boolean, message: String) { check(value) { message }; checks++ }
    val name = ReceiverName.defaultName("AirRelay", "Xiaomi TV Box", "2F9A7B61")
    expect(name == "AirRelay-2F9A7B61", "compact device name without hardware model or spaces")
    expect(name == ReceiverName.defaultName("AirRelay", "Xiaomi TV Box", "2F9A7B61"), "same installation stable")
    expect(name != ReceiverName.defaultName("AirRelay", "Xiaomi TV Box", "345FED12"), "two TVs differ")
    expect(ReceiverInput.validName(ReceiverName.defaultName("AirRelay", "電視📺".repeat(40), "2F9A7B61")), "unicode DNS byte limit")
    expect(ReceiverName.needsDefault(null), "new install named")
    expect(ReceiverName.needsDefault("AynaTV Receiver"), "legacy default migrated")
    expect(!ReceiverName.needsDefault("Living Room"), "custom name retained")
    expect(!ReceiverName.needsDefault("Living Room", name), "custom override retained")
    expect(ReceiverName.needsDefault(name, name), "generated name may follow product rename")
    val endpoints = ReceiverPortGuard.endpoints(7000)
    expect(endpoints.map { it.toString() } == listOf("TCP 7000", "TCP 7100", "UDP 7011", "UDP 6001", "UDP 6000"), "fixed bridge contract retained")
    ServerSocket(0).use { listener ->
        val endpoint = ReceiverPortGuard.Endpoint("TCP", listener.localPort)
        expect(ReceiverPortGuard.firstUnavailable(listOf(endpoint)) == endpoint, "existing TCP listener detected")
        expect(!listener.isClosed, "other app TCP listener untouched")
    }
    DatagramSocket(0).use { listener ->
        val endpoint = ReceiverPortGuard.Endpoint("UDP", listener.localPort)
        expect(ReceiverPortGuard.firstUnavailable(listOf(endpoint)) == endpoint, "existing UDP listener detected")
        expect(!listener.isClosed, "other app UDP listener untouched")
    }
    val ephemeral = ReceiverPortGuard.Endpoint("TCP", 0)
    expect(ReceiverPortGuard.firstUnavailable(listOf(ephemeral)) == null, "free probe completes")
    expect(ReceiverPortPolicy.nativePort(7000, emulator = false, conflict = false) == 7000, "physical TV free fixed port retained")
    expect(ReceiverPortPolicy.nativePort(8765, emulator = false, conflict = false) == 8765, "custom free port retained")
    expect(ReceiverPortPolicy.nativePort(7000, emulator = false, conflict = true) == 0, "physical TV conflict delegates all ports to native")
    expect(ReceiverPortPolicy.nativePort(7100, emulator = false, conflict = true) == 0, "overlapping configured ports recover on physical TV")
    expect(ReceiverPortPolicy.nativePort(7000, emulator = true, conflict = false) == 7000, "emulator fixed bridge retained")
    expect(ReceiverPortPolicy.nativePort(7000, emulator = true, conflict = true) == null, "emulator never silently selects unreachable ephemeral ports")
    expect(ReceiverPortPolicy.isEmulator("ranchu", "google/sdk_gphone64_x86_64", "sdk_gphone64_x86_64"), "known emulator recognized")
    expect(ReceiverPortPolicy.isEmulator("goldfish", "", ""), "legacy emulator recognized")
    expect(!ReceiverPortPolicy.isEmulator("amlogic", "Xiaomi/TVBox/production", "Xiaomi TV Box"), "physical TV recognized")
    expect(runCatching { ReceiverPortPolicy.nativePort(0, false, false) }.isFailure, "user cannot bypass policy with zero setting")
    println("PASS ReceiverLifecycleChecks: $checks checks (real host sockets; no Android boot/device claim)")
}
