package com.spearotracker.spearogo.sync

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/** Firestore REST shapes. The Swift twin is checked by the harness `firestore` mode, same cases. */
class FirestoreClientTest {
    private lateinit var server: MockWebServer
    private val http = OkHttpClient.Builder().readTimeout(2, TimeUnit.SECONDS).retryOnConnectionFailure(false).build()

    private fun client() = FirestoreClient(
        http, server.url("/v1/projects/p/databases/(default)/documents").toString(),
        mapOf("X-Android-Package" to "com.example.go"),
    )

    private fun json(code: Int, body: String) =
        MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

    @Before fun setUp() { server = MockWebServer().also { it.start() } }
    @After fun tearDown() { server.shutdown() }

    @Test
    fun listFollowsNextPageToken() = runBlocking {
        server.enqueue(json(200, """{"documents":[{"name":"a/1"},{"name":"a/2"}],"nextPageToken":"tok+2 /="}"""))
        server.enqueue(json(200, """{"documents":[{"name":"a/3"}]}"""))

        val docs = client().listUserSpots("U1", "T")

        assertEquals(listOf("a/1", "a/2", "a/3"), docs.map { it.get("name").asString })
        val first = server.takeRequest()
        assertEquals("GET", first.method)
        assertTrue(first.requestUrl!!.encodedPath.endsWith("/documents/users/U1/spots"))
        assertEquals("300", first.requestUrl!!.queryParameter("pageSize"))
        assertNull(first.requestUrl!!.queryParameter("pageToken"))
        assertEquals("Bearer T", first.getHeader("Authorization"))
        assertEquals("com.example.go", first.getHeader("X-Android-Package"))
        assertEquals("tok+2 /=", server.takeRequest().requestUrl!!.queryParameter("pageToken"))
    }

    @Test
    fun emptyCollectionIsEmptyList() = runBlocking {
        server.enqueue(json(200, "{}"))
        assertEquals(0, client().listUserSpots("U1", "T").size)
    }

    @Test
    fun runQueryShapeAndSkipsElementsWithoutDocument() = runBlocking {
        server.enqueue(json(200, """[{"document":{"name":"s/1"},"readTime":"x"},{"readTime":"x"},{"document":{"name":"s/2"}}]"""))

        val docs = client().queryVisionSpots("U1", "T")

        assertEquals(listOf("s/1", "s/2"), docs.map { it.get("name").asString })
        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertTrue(req.requestUrl!!.encodedPath.endsWith("/documents:runQuery"))
        val q = JsonParser.parseString(req.body.readUtf8()).asJsonObject.getAsJsonObject("structuredQuery")
        assertEquals("spots", q.getAsJsonArray("from")[0].asJsonObject.get("collectionId").asString)
        val f = q.getAsJsonObject("where").getAsJsonObject("fieldFilter")
        assertEquals("userId", f.getAsJsonObject("field").get("fieldPath").asString)
        assertEquals("EQUAL", f.get("op").asString)
        assertEquals("U1", f.getAsJsonObject("value").get("stringValue").asString)
        assertEquals("Bearer T", req.getHeader("Authorization"))
    }

    @Test
    fun patchSendsFieldsAndMask() = runBlocking {
        server.enqueue(json(200, "{}"))
        val fields = JsonObject().apply { add("is_deleted", JsonObject().apply { addProperty("booleanValue", true) }) }

        client().patch("users/U1/spots/D1", fields, listOf("is_deleted", "last_modified_at"), "T")

        val req = server.takeRequest()
        assertEquals("PATCH", req.method)
        assertTrue(req.requestUrl!!.encodedPath.endsWith("/documents/users/U1/spots/D1"))
        assertEquals(listOf("is_deleted", "last_modified_at"), req.requestUrl!!.queryParameterValues("updateMask.fieldPaths"))
        assertEquals(fields, JsonParser.parseString(req.body.readUtf8()).asJsonObject.getAsJsonObject("fields"))
        assertEquals("com.example.go", req.getHeader("X-Android-Package"))
    }

    @Test
    fun markerFieldsShape() {
        val f = FirestoreClient.markerFields("2026-10-23T12:00:00Z")
        val ts = f.getAsJsonObject("apps").getAsJsonObject("mapValue").getAsJsonObject("fields")
            .getAsJsonObject("go").getAsJsonObject("mapValue").getAsJsonObject("fields")
            .getAsJsonObject("last_seen_at").get("timestampValue").asString
        assertEquals("2026-10-23T12:00:00Z", ts)
    }

    @Test
    fun httpErrorThrows() = runBlocking {
        server.enqueue(json(403, """{"error":{"code":403}}"""))
        try {
            client().listUserSpots("U1", "T")
            fail("expected FirestoreException")
        } catch (e: FirestoreException) {
            assertEquals(403, e.code)
        }
    }
}
