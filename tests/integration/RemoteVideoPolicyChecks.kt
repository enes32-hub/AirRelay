import io.github.jqssun.airplay.receiver.StreamProfile
import io.github.jqssun.airplay.renderer.RemoteVideoPolicy

fun main() {
    check(RemoteVideoPolicy.bitrateCeiling(StreamProfile.CUSTOM, 0) == Int.MAX_VALUE)
    check(RemoteVideoPolicy.bitrateCeiling(StreamProfile.QUALITY, 0) == 12_000_000)
    check(RemoteVideoPolicy.bitrateCeiling(StreamProfile.RESPONSIVE, 20_000_000) == 20_000_000)
    check(RemoteVideoPolicy.bitrateCeiling(StreamProfile.CUSTOM, 4_000_000) == 4_000_000)
    for (bad in listOf(-1, Int.MIN_VALUE, 1, Int.MAX_VALUE)) {
        check(RemoteVideoPolicy.bitrateCeiling(StreamProfile.BALANCED, bad) == 8_000_000)
    }
    println("PASS: auto, explicit bitrate override, invalid persisted values, and custom profile")
}
