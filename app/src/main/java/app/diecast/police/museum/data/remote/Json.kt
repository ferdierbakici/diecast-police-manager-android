package app.diecast.police.museum.data.remote

import kotlinx.serialization.json.Json

object Json {
    val default: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
        coerceInputValues = true
    }
}
