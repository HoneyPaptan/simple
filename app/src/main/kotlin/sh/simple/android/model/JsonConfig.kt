package sh.simple.android.model

import kotlinx.serialization.json.Json

val WireJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
    coerceInputValues = true
}
