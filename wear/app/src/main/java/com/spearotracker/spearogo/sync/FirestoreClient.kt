package com.spearotracker.spearogo.sync

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/** A Firestore call that did not succeed. [code] is the HTTP status, or null for no answer / an unreadable one. */
class FirestoreException(val code: Int?, message: String) : IOException(message)

/**
 * Firestore over REST (no SDK). [baseUrl] is the `.../databases/(default)/documents` root;
 * injected so tests can point it at MockWebServer. Every request carries the ID token and
 * the same API-key restriction headers as [AuthClient] ([AppIdentityHeaders.of]).
 * Twin of SpearoGo/Sync/FirestoreClient.swift. Throws [FirestoreException] on any failure;
 * messages hold status codes only, never paths (they contain the uid).
 */
class FirestoreClient(
    private val http: OkHttpClient,
    private val baseUrl: String,
    private val appHeaders: Map<String, String> = emptyMap(),
) {
    /** `users/{uid}/spots`, every page (pageSize=300, following nextPageToken). */
    suspend fun listUserSpots(uid: String, token: String): List<JsonObject> {
        val out = mutableListOf<JsonObject>()
        var pageToken: String? = null
        do {
            val url = docUrl("users/$uid/spots").newBuilder()
                .addQueryParameter("pageSize", PAGE_SIZE.toString())
                .apply { pageToken?.let { addQueryParameter("pageToken", it) } }
                .build()
            val json = parse(send(request(url, token).get().build()))
            val page = json?.takeIf { it.isJsonObject }?.asJsonObject
                ?: throw FirestoreException(null, "list: unreadable response")
            page.get("documents")?.takeIf { it.isJsonArray }?.asJsonArray
                ?.forEach { if (it.isJsonObject) out.add(it.asJsonObject) }
            pageToken = page.get("nextPageToken")?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotEmpty() }
        } while (pageToken != null)
        return out
    }

    /** Vision's top-level `spots` where `userId == uid`, via `:runQuery`. */
    suspend fun queryVisionSpots(uid: String, token: String): List<JsonObject> {
        val body = JsonObject().apply {
            add("structuredQuery", JsonObject().apply {
                add("from", JsonArray().apply { add(JsonObject().apply { addProperty("collectionId", "spots") }) })
                add("where", JsonObject().apply {
                    add("fieldFilter", JsonObject().apply {
                        add("field", JsonObject().apply { addProperty("fieldPath", "userId") })
                        addProperty("op", "EQUAL")
                        add("value", JsonObject().apply { addProperty("stringValue", uid) })
                    })
                })
            })
        }
        val url = "$baseUrl:runQuery".toHttpUrl()
        val json = parse(send(request(url, token).post(body.toString().toRequestBody(JSON)).build()))
        val results = json?.takeIf { it.isJsonArray }?.asJsonArray
            ?: throw FirestoreException(null, "runQuery: unreadable response")
        return results.mapNotNull { el ->
            el.takeIf { it.isJsonObject }?.asJsonObject?.get("document")?.takeIf { it.isJsonObject }?.asJsonObject
        }
    }

    /**
     * PATCH `{base}/{path}` with `fields`; with a [mask], only those field paths are written.
     * [mustExist] adds `currentDocument.exists=true`: the write fails (404 / FAILED_PRECONDITION)
     * instead of creating the doc.
     */
    suspend fun patch(path: String, fields: JsonObject, mask: List<String>?, token: String, mustExist: Boolean = false) {
        val url = docUrl(path).newBuilder()
            .apply { mask?.forEach { addQueryParameter("updateMask.fieldPaths", it) } }
            .apply { if (mustExist) addQueryParameter("currentDocument.exists", "true") }
            .build()
        val body = JsonObject().apply { add("fields", fields) }
        send(request(url, token).patch(body.toString().toRequestBody(JSON)).build())
    }

    /**
     * The "Go connected" marker. Only ever UPDATES `users/{uid}`: the website creates that doc with
     * the profile fields its rules require, and a marker-only doc would block that creation.
     */
    suspend fun patchMarker(uid: String, nowIso: String, token: String) =
        patch("users/$uid", markerFields(nowIso), listOf(MARKER_PATH), token, mustExist = true)

    private fun docUrl(path: String): HttpUrl = baseUrl.toHttpUrl().newBuilder()
        .apply { path.split('/').filter { it.isNotEmpty() }.forEach { addPathSegment(it) } }
        .build()

    private fun request(url: HttpUrl, token: String): Request.Builder = Request.Builder().url(url).apply {
        appHeaders.forEach { (k, v) -> header(k, v) }
        header("Authorization", "Bearer $token")
    }

    private suspend fun send(request: Request): String = withContext(Dispatchers.IO) {
        val (code, text) = try {
            http.newCall(request).execute().use { it.code to (it.body?.string() ?: "") }
        } catch (e: IOException) {
            throw FirestoreException(null, "network error (${e.javaClass.simpleName})")
        }
        if (code !in 200..299) throw FirestoreException(code, "HTTP $code")
        text
    }

    private fun parse(text: String) = try { JsonParser.parseString(text) } catch (e: Exception) { null }

    companion object {
        private const val PAGE_SIZE = 300
        private val JSON = "application/json; charset=utf-8".toMediaType()

        /** `fields` for PATCH users/{uid}?updateMask.fieldPaths=[MARKER_PATH]. */
        const val MARKER_PATH = "apps.go.last_seen_at"

        fun markerFields(nowIso: String): JsonObject {
            fun map(key: String, inner: JsonObject) = JsonObject().apply {
                add(key, JsonObject().apply { add("mapValue", JsonObject().apply { add("fields", inner) }) })
            }
            val seen = JsonObject().apply {
                add("last_seen_at", JsonObject().apply { addProperty("timestampValue", nowIso) })
            }
            return map("apps", map("go", seen))
        }
    }
}
