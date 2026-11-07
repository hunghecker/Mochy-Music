package com.mochyy.mochymusic.importer

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/** Reverses lossless UTF-8 mojibake only. Never guesses replacement characters or song identity. */
object MetadataTextRepair {
    private val legacy = listOf("windows-1252", "ISO-8859-1", "windows-1258", "GB18030")
    private val vietnamese = "àáảãạăằắẳẵặâầấẩẫậèéẻẽẹêềếểễệìíỉĩịòóỏõọôồốổỗộơờớởỡợùúủũụưừứửữựỳýỷỹỵđ".toSet()
    private fun accents(s: String) = s.lowercase(java.util.Locale.ROOT).count { it in vietnamese }
    private fun noise(s: String) = listOf("Ã", "Â", "Ä", "Æ", "áº", "á»", "ðŸ").sumOf { token ->
        Regex(Regex.escape(token)).findAll(s).count()
    } + Regex("Ă(?=[\\u0080-\\u00ff\\u0192])").findAll(s).count()
    private fun cjk(s: String) = s.count { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN }

    fun repair(value: String): String {
        if ('\uFFFD' in value || value.length > 4096) return value
        var text = value
        repeat(2) {
            val before = text
            val candidates = legacy.mapNotNull { name ->
                runCatching {
                    val charset = Charset.forName(name)
                    val bytes = charset.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(before))
                    val raw = ByteArray(bytes.remaining()).also { bytes.get(it) }
                    // Verify the legacy mapping itself is reversible (some mappings are aliases).
                    if (charset.newDecoder().decode(ByteBuffer.wrap(raw)).toString() != before) return@runCatching null
                    Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(raw)).toString()
                }.getOrNull()
            }.filterNotNull().filter { candidate ->
                candidate != before && candidate.none { it == '\uFFFD' || (it.isISOControl() && it != '\n' && it != '\t') } &&
                    (noise(candidate) < noise(before) ||
                        (cjk(before) > 0 && cjk(candidate) == 0 && accents(candidate) > accents(before) &&
                            Regex("[A-Za-z]{2,}").containsMatchIn(before)))
            }
            text = candidates.minWithOrNull(compareBy<String> { noise(it) }.thenBy { cjk(it) }) ?: before
        }
        return text
    }
}
