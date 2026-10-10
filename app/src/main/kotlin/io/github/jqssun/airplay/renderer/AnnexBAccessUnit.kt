package io.github.jqssun.airplay.renderer

/** Parameter sets are configuration, not independently decodable pictures. */
class AnnexBAccessUnit {
    private val parameters = linkedMapOf<Int, ByteArray>()
    fun clear() = parameters.clear()

    fun prepare(data: ByteArray, hevc: Boolean): Pair<Boolean, ByteArray> {
        val starts = ArrayList<Pair<Int, Int>>()
        var i = 0
        while (i + 3 <= data.size) {
            val prefix = when {
                i + 4 <= data.size && data[i] == 0.toByte() && data[i + 1] == 0.toByte() &&
                    data[i + 2] == 0.toByte() && data[i + 3] == 1.toByte() -> 4
                data[i] == 0.toByte() && data[i + 1] == 0.toByte() && data[i + 2] == 1.toByte() -> 3
                else -> 0
            }
            if (prefix > 0) { starts.add(i to prefix); i += prefix } else i++
        }
        var key = false
        val present = mutableSetOf<Int>()
        starts.forEachIndexed { index, (start, prefix) ->
            if (start + prefix >= data.size) return@forEachIndexed
            val type = if (hevc) (data[start + prefix].toInt() shr 1) and 63 else data[start + prefix].toInt() and 31
            if (if (hevc) type in 16..21 else type == 5) key = true
            if (if (hevc) type in 32..34 else type == 7 || type == 8) {
                parameters[type] = data.copyOfRange(start, starts.getOrNull(index + 1)?.first ?: data.size)
                present.add(type)
            }
        }
        val required = if (hevc) listOf(32, 33, 34) else listOf(7, 8)
        if (!key || !required.all { parameters.containsKey(it) }) return false to data
        val prefix = required.filter { it !in present }.fold(ByteArray(0)) { acc, type -> acc + parameters.getValue(type) }
        return true to (prefix + data)
    }
}
