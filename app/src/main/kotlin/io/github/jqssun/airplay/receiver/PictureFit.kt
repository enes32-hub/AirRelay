package io.github.jqssun.airplay.receiver

enum class PictureFit { FIT, FILL, STRETCH;
    fun next(): PictureFit = entries[(ordinal + 1) % entries.size]
    /** All dimensions use the same coordinate units. */
    fun size(width: Float, height: Float, aspect: Float): Pair<Float, Float> {
        val ratio = aspect.takeIf { it.isFinite() && it > 0f } ?: (16f / 9f)
        if (this == STRETCH) return width to height
        val scaledWidth = if (this == FILL) maxOf(width, height * ratio) else minOf(width, height * ratio)
        return scaledWidth to scaledWidth / ratio
    }
}
