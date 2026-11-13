package io.github.jqssun.airplay.receiver

/** A readable per-installation name within the RAOP DNS label's UTF-8 limit. */
object ReceiverName {
    @Suppress("UNUSED_PARAMETER")
    fun defaultName(brand: String, model: String, suffix: String): String {
        val id = suffix.filter { it.isLetterOrDigit() }.take(8).ifBlank { "TV" }
        val base = brand.trim().replace(Regex("[^A-Za-z0-9]+"), "-").trim('-').ifBlank { "Cast" }
        val end = "-$id"
        val budget = ReceiverInput.NAME_MAX_UTF8_BYTES - end.toByteArray(Charsets.UTF_8).size
        val prefix = StringBuilder()
        var bytes = 0
        base.codePoints().forEachOrdered { point ->
            val text = String(Character.toChars(point))
            val size = text.toByteArray(Charsets.UTF_8).size
            if (bytes + size <= budget) { prefix.append(text); bytes += size }
            else bytes = budget // do not resume after an over-budget character
        }
        return prefix.toString().trimEnd() + end
    }

    // Recognise an unset or upstream placeholder default name and regenerate it as
    // "AirRelay-<id>", while preserving any custom name chosen by the user.
    fun needsDefault(current: String?, previousGenerated: String? = null): Boolean =
        current.isNullOrBlank() || current == "AirPlay Receiver" ||
            (previousGenerated != null && current == previousGenerated)
}
