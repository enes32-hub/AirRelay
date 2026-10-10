package io.github.jqssun.airplay.receiver

/** Only the saved user choice determines the theme. */
object AppearancePolicy {
    val themes = setOf("classic", "light", "dark")
    fun resolve(selected: String): String =
        selected.takeIf { it in themes } ?: "classic"
}
