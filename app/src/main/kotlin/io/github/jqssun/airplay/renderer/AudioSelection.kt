package io.github.jqssun.airplay.renderer

/** Missing or ambiguous metadata never means 'choose the first audio track'. */
data class AudioSelection(val id: String?, val language: String?, val name: String?, val group: String?) {
    companion object {
        fun fromJson(json: String): AudioSelection? {
            if (json.length > 65536) return null
            return runCatching {
                val root = org.json.JSONTokener(json).nextValue()
                val array = when (root) {
                    is org.json.JSONArray -> root
                    is org.json.JSONObject -> root.optJSONArray("selectedMediaArray") ?: root.optJSONArray("value")
                    else -> null
                } ?: return null
                val audio = (0 until array.length()).mapNotNull { array.optJSONObject(it) }.filter {
                    it.optString("type", it.optString("mediaType")) in listOf("soun", "audio")
                }.singleOrNull() ?: return null
                fun text(vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
                    if (audio.isNull(key)) null else audio.optString(key).takeIf { it.isNotBlank() && it.length <= 256 }
                }
                AudioSelection(text("trackID", "id"), text("language", "extendedLanguageTag"),
                    text("displayName", "name"), text("groupID"))
                    .takeIf { listOf(it.id, it.language, it.name, it.group).any { value -> value != null } }
            }.getOrNull()
        }
    }
    fun matches(trackId: String?, trackLanguage: String?, trackName: String?, trackGroup: String?): Boolean {
        if (listOf(id, language, name, group).all { it.isNullOrBlank() }) return false
        return (id == null || id == trackId) &&
            (language == null || language.equals(trackLanguage, ignoreCase = true)) &&
            (name == null || name == trackName) && (group == null || group == trackGroup)
    }
}
