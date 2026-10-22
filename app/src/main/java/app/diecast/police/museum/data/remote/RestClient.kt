package app.diecast.police.museum.data.remote

import app.diecast.police.museum.core.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/** Raw HTTP result. */
data class HttpResult(
    val code: Int,
    val body: String,
    val headers: Map<String, String>
) {
    val isSuccess: Boolean get() = code in 200..299

    /** For PostgREST `Prefer: count=exact`: `Content-Range: 0-49/1489` -> 1489 */
    val totalCount: Int
        get() = headers.entries.firstOrNull { it.key.equals("Content-Range", true) }
            ?.value?.substringAfterLast('/')?.trim()?.toIntOrNull() ?: 0

    val errorMessage: String
        get() = runCatching {
            val el = Json.default.parseToJsonElement(body).let { it as? kotlinx.serialization.json.JsonObject }
            el?.get("error_description")?.toString()?.trim('"')
                ?: el?.get("msg")?.toString()?.trim('"')
                ?: el?.get("message")?.toString()?.trim('"')
                ?: el?.get("error")?.toString()?.trim('"')
        }.getOrNull() ?: "HTTP $code"
}

/**
 * A minimal PostgREST + GoTrue client.
 * Direct REST calls instead of supabase-js, so mobile uses the same contract.
 */
class RestClient(private val http: OkHttpClient) {

    suspend fun get(
        path: String,
        params: Map<String, String> = emptyMap(),
        token: String? = null,
        headers: Map<String, String> = emptyMap()
    ): HttpResult = withContext(Dispatchers.IO) {
        val url = buildUrl(Config.REST_URL + path, params)
        val request = Request.Builder()
            .url(url)
            .get()
            .applyHeaders(token, headers)
            .build()
        http.newCall(request).execute().toResult()
    }

    /** GET on an absolute URL (for non-PostgREST endpoints such as GoTrue /auth/v1/user). */
    suspend fun getAbsolute(
        url: String,
        token: String? = null,
        headers: Map<String, String> = emptyMap()
    ): HttpResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .get()
            .applyHeaders(token, headers)
            .build()
        http.newCall(request).execute().toResult()
    }

    suspend fun post(
        fullUrl: String,
        jsonBody: String? = null,
        token: String? = null,
        headers: Map<String, String> = emptyMap()
    ): HttpResult = withContext(Dispatchers.IO) {
        val body = jsonBody?.toRequestBody(JSON_MEDIA)
        val request = Request.Builder()
            .url(fullUrl)
            .post(body ?: EMPTY_BODY)
            .applyHeaders(token, headers)
            .build()
        http.newCall(request).execute().toResult()
    }

    suspend fun delete(
        fullUrl: String,
        token: String? = null,
        headers: Map<String, String> = emptyMap()
    ): HttpResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(fullUrl)
            .delete()
            .applyHeaders(token, headers)
            .build()
        http.newCall(request).execute().toResult()
    }

    private fun Request.Builder.applyHeaders(
        token: String?,
        extra: Map<String, String>
    ): Request.Builder {
        header("apikey", Config.ANON_KEY)
        header("Authorization", "Bearer ${token ?: Config.ANON_KEY}")
        extra.forEach { (k, v) -> header(k, v) }
        return this
    }

    private fun buildUrl(raw: String, params: Map<String, String>): HttpUrl {
        val builder = raw.toHttpUrl().newBuilder()
        params.forEach { (k, v) -> builder.addQueryParameter(k, v) }
        return builder.build()
    }

    private fun Response.toResult(): HttpResult {
        val bodyText = body?.string().orEmpty()
        val headerMap = headers.toMultimap().mapValues { it.value.joinToString(", ") }
        return HttpResult(code, bodyText, headerMap)
    }

    companion object {
        val JSON_MEDIA: okhttp3.MediaType =
            requireNotNull("application/json; charset=utf-8".toMediaTypeOrNull())

        val EMPTY_BODY: okhttp3.RequestBody = "".toRequestBody(null)
    }
}
