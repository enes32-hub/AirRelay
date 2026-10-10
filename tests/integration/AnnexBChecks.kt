import io.github.jqssun.airplay.renderer.AnnexBAccessUnit

fun main() {
    fun nal(type: Int, short: Boolean = false) =
        (if (short) byteArrayOf(0, 0, 1) else byteArrayOf(0, 0, 0, 1)) + byteArrayOf(type.toByte(), 42)
    val avc = AnnexBAccessUnit()
    check(!avc.prepare(nal(7), false).first) // SPS alone never starts playback.
    check(!avc.prepare(nal(8, true), false).first)
    check(!avc.prepare(nal(1), false).first)
    val (key, assembled) = avc.prepare(nal(5, true), false)
    check(key && assembled.contentEquals(nal(7) + nal(8, true) + nal(5, true)))
    val complete = nal(7) + nal(8) + nal(5)
    check(avc.prepare(complete, false).second.contentEquals(complete))
    avc.clear()
    check(!avc.prepare(nal(5), false).first) // Old resolution's configuration cannot be reused.
    check(!avc.prepare(byteArrayOf(0, 0, 1), false).first)
    val hevc = AnnexBAccessUnit()
    check(!hevc.prepare(nal(32 shl 1) + nal(33 shl 1) + nal(34 shl 1), true).first)
    check(hevc.prepare(nal(19 shl 1), true).first)
    println("Annex B regression checks: 9 passed; no decoder/network claim")
}
