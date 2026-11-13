package sh.simple.android.voice

import java.util.regex.Pattern

data class VoiceReplacement(val id: String, val heard: String, val write: String)

fun applyVoiceReplacements(text: String, rules: List<VoiceReplacement>): String {
    if (text.isEmpty() || rules.isEmpty()) return text
    var out = text
    for (rule in rules) {
        val heard = rule.heard.trim()
        if (heard.isEmpty()) continue
        val pattern = Pattern.compile("\\b${Pattern.quote(heard)}\\b", Pattern.CASE_INSENSITIVE)
        out = pattern.matcher(out).replaceAll(rule.write)
    }
    return out
}
