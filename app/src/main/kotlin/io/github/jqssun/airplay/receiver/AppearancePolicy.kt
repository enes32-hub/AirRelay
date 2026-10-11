package io.github.jqssun.airplay.receiver

/** Only the saved user choice determines the theme. */
object AppearancePolicy {
    /**
     * The shipping themes: the dark brand palette ("classic") and its light counterpart. A stored
     * "dark" value from an older install is no longer a theme and resolves to classic, so existing
     * users keep a dark interface instead of falling back to light.
     */
    val themes = setOf("classic", "light")
    fun resolve(selected: String): String =
        selected.takeIf { it in themes } ?: "classic"
}
