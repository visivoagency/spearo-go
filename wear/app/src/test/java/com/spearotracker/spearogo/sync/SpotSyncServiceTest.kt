package com.spearotracker.spearogo.sync

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.spearotracker.spearogo.models.SavedLocation
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.Collections
import java.util.concurrent.TimeUnit

/** In-memory stand-in for the Room DAO; counts writes so "applied nothing" is checkable. */
class FakeSpotStore(vararg initial: SavedLocation) : SpotStore {
    val rows = LinkedHashMap<String, SavedLocation>().apply { initial.forEach { put(it.id, it) } }
    var writes = 0
    var onWrite: () -> Unit = {}

    override suspend fun getAllIncludingDeleted(): List<SavedLocation> = rows.values.toList()
    override suspend fun upsertAll(rows: List<SavedLocation>) {
        writes++; onWrite(); rows.forEach { this.rows[it.id] = it }
    }
    override suspend fun hardDelete(ids: List<String>) {
        writes++; onWrite(); ids.forEach { rows.remove(it) }
    }
    override suspend fun detachAll() {
        writes++; onWrite()
        rows.values.filter { it.isDeleted }.forEach { rows.remove(it.id) }
        rows.replaceAll { _, r -> r.copy(cloudId = null, pendingSync = false) }
    }
}

class InMemoryMarkerStore(var at: Long? = null) : SyncMarkerStore {
    override fun lastWrittenAt(): Long? = at
    override fun setLastWrittenAt(ms: Long) { at = ms }
    override fun clear() { at = null }
}

class SpotSyncServiceTest {
    private lateinit var server: MockWebServer
    private var now = Instant.parse("2026-10-23T12:00:00Z").toEpochMilli()
    private val tokens = InMemoryTokenStore()
    private val marker = InMemoryMarkerStore()
    private val requests: MutableList<RecordedRequest> = Collections.synchronizedList(mutableListOf())

    // What the server answers; tests replace these.
    private var accountDocs = listOf<JsonObject>()
    private var visionDocs = listOf<JsonObject>()
    private var listCode = 200
    private var queryCode = 200
    private var spotPatchCode = 200

    private val http = OkHttpClient.Builder().readTimeout(2, TimeUnit.SECONDS).retryOnConnectionFailure(false).build()
    private val base get() = server.url("/v1/projects/p/databases/(default)/documents").toString()

    private fun signedIn() {
        tokens.stored = Session("U1", "id-token", "refresh", now + 30L * 24 * 3_600_000L, null)
    }

    private fun service(dao: SpotStore): SpotSyncService {
        val auth = AuthClient(
            http = http, baseIdentity = server.url("/identity").toString().trimEnd('/'),
            baseSecureToken = server.url("/securetoken").toString().trimEnd('/'),
            store = tokens, clock = { now }, apiKey = "k", log = {},
        )
        val firestore = FirestoreClient(http, base, mapOf("X-Android-Package" to "com.example.go"))
        var n = 0
        return SpotSyncService(auth, firestore, dao, marker, clock = { now }, newId = { "new-${n++}" }, log = {})
    }

    private fun doc(path: String, name: String, lat: Double, lon: Double, modified: String) = JsonObject().apply {
        addProperty("name", "projects/p/databases/(default)/documents/$path")
        add("fields", JsonParser.parseString("""
            {"name":{"stringValue":"$name"},"latitude":{"doubleValue":$lat},"longitude":{"doubleValue":$lon},
             "is_deleted":{"booleanValue":false},"last_modified_at":{"stringValue":"$modified"}}
        """).asJsonObject)
    }

    private fun json(code: Int, body: String) =
        MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

    @Before fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests.add(request)
                val path = request.requestUrl!!.encodedPath
                return when {
                    request.method == "GET" && path.endsWith("/users/U1/spots") -> {
                        if (listCode != 200) json(listCode, "{}")
                        else json(200, JsonObject().apply {
                            add("documents", com.google.gson.JsonArray().apply { accountDocs.forEach { add(it) } })
                        }.toString())
                    }
                    request.method == "POST" && path.endsWith(":runQuery") -> {
                        if (queryCode != 200) json(queryCode, "{}")
                        else json(200, com.google.gson.JsonArray().apply {
                            visionDocs.forEach { d -> add(JsonObject().apply { add("document", d) }) }
                            add(JsonObject().apply { addProperty("readTime", "2026-10-23T12:00:00Z") })
                        }.toString())
                    }
                    request.method == "PATCH" && path.contains("/users/U1/spots/") -> json(spotPatchCode, "{}")
                    request.method == "PATCH" && path.endsWith("/users/U1") -> json(200, "{}")
                    else -> json(404, "{}")
                }
            }
        }
        server.start()
    }

    @After fun tearDown() { server.shutdown() }

    private fun spotPatches() = requests.filter { it.method == "PATCH" && it.requestUrl!!.encodedPath.contains("/spots/") }
    private fun markerPatches() = requests.filter { it.method == "PATCH" && it.requestUrl!!.encodedPath.endsWith("/users/U1") }

    @Test
    fun fetchFailureAppliesNothing() = runBlocking {
        signedIn()
        val pending = SavedLocation(id = "L1", name = "Reef", latitude = 1.0, longitude = 2.0, createdAt = 1, pendingSync = true)
        val dao = FakeSpotStore(pending)
        accountDocs = listOf(doc("users/U1/spots/W1", "Wreck", 20.0, 20.0, "2026-10-01T00:00:00Z"))

        listCode = 500
        val svc = service(dao)
        svc.sync()
        assertTrue(svc.state.value is SyncState.Failed)
        assertEquals(0, dao.writes)
        assertTrue(spotPatches().isEmpty())

        listCode = 200; queryCode = 503
        svc.sync()
        assertTrue(svc.state.value is SyncState.Failed)
        assertEquals(0, dao.writes)
        assertTrue(spotPatches().isEmpty())
        assertEquals(pending, dao.rows["L1"])
    }

    @Test
    fun pushesThenPulls() = runBlocking {
        signedIn()
        val dao = FakeSpotStore(
            SavedLocation(id = "L1", name = "Home reef", latitude = 10.0, longitude = 10.0, createdAt = 1000, pendingSync = true)
        )
        var requestsAtFirstWrite = -1
        dao.onWrite = { if (requestsAtFirstWrite < 0) requestsAtFirstWrite = spotPatches().size }
        accountDocs = listOf(doc("users/U1/spots/W1", "Wreck", 20.0, 20.0, "2026-10-01T00:00:00Z"))

        val svc = service(dao)
        svc.sync()

        val patches = spotPatches()
        assertEquals(1, patches.size)
        val req = patches[0]
        assertTrue(req.requestUrl!!.encodedPath.endsWith("/users/U1/spots/L1"))
        assertEquals("Bearer id-token", req.getHeader("Authorization"))
        assertEquals("com.example.go", req.getHeader("X-Android-Package"))
        val fields = JsonParser.parseString(req.body.readUtf8()).asJsonObject.getAsJsonObject("fields")
        assertEquals("go", fields.getAsJsonObject("source").get("stringValue").asString)
        assertFalse(fields.has("isDeleted"))
        assertFalse(fields.has("lastModifiedAt"))
        assertEquals(1, requestsAtFirstWrite)  // the push went out before anything was applied locally

        val local = dao.rows["L1"]!!
        assertEquals("L1", local.cloudId)
        assertFalse(local.pendingSync)
        val pulled = dao.rows.values.single { it.cloudId == "W1" }
        assertEquals("Wreck", pulled.name)
        assertEquals("ACCOUNT", pulled.origin)
        assertFalse(pulled.pendingSync)
        assertEquals(SyncState.Synced(now), svc.state.value)
    }

    @Test
    fun failedPushStaysPending() = runBlocking {
        signedIn()
        val pending = SavedLocation(id = "L1", name = "Home reef", latitude = 10.0, longitude = 10.0, createdAt = 1000, pendingSync = true)
        val dao = FakeSpotStore(pending)
        accountDocs = listOf(doc("users/U1/spots/W1", "Wreck", 20.0, 20.0, "2026-10-01T00:00:00Z"))
        spotPatchCode = 500

        val svc = service(dao)
        svc.sync()

        assertEquals(pending, dao.rows["L1"])           // still pending, still unlinked
        assertTrue(dao.rows.values.any { it.cloudId == "W1" })  // what succeeded is applied
        assertEquals(SyncState.Failed("Couldn't upload 1 spot"), svc.state.value)
    }

    @Test
    fun pendingDeletePushesThenHardDeletes() = runBlocking {
        signedIn()
        val dao = FakeSpotStore(
            SavedLocation(id = "L1", name = "Wreck", latitude = 20.0, longitude = 20.0, createdAt = 1000,
                cloudId = "W1", isDeleted = true, pendingSync = true, origin = "ACCOUNT")
        )
        accountDocs = listOf(doc("users/U1/spots/W1", "Wreck", 20.0, 20.0, "2026-10-01T00:00:00Z"))

        service(dao).sync()

        val req = spotPatches().single()
        assertEquals(listOf("is_deleted", "last_modified_at"), req.requestUrl!!.queryParameterValues("updateMask.fieldPaths"))
        assertTrue(dao.rows.isEmpty())
    }

    @Test
    fun activeSpotSurvivesSync() = runBlocking {
        signedIn()
        val dao = FakeSpotStore(
            SavedLocation(id = "A", name = "Old name", latitude = 20.0, longitude = 20.0, createdAt = 1000,
                isActive = true, cloudId = "W1", lastModifiedAt = 1000, origin = "ACCOUNT")
        )
        accountDocs = listOf(doc("users/U1/spots/W1", "New name", 20.0, 20.0, "2026-10-01T00:00:00Z"))

        service(dao).sync()

        val row = dao.rows["A"]!!
        assertEquals("New name", row.name)
        assertTrue(row.isActive)
    }

    @Test
    fun markerOncePerDay() = runBlocking {
        signedIn()
        val svc = service(FakeSpotStore())
        svc.sync()
        now += 60 * 60_000L
        svc.sync()

        val markers = markerPatches()
        assertEquals(1, markers.size)
        assertEquals(listOf("apps.go.last_seen_at"), markers[0].requestUrl!!.queryParameterValues("updateMask.fieldPaths"))
        val body = JsonParser.parseString(markers[0].body.readUtf8()).asJsonObject
        val ts = body.getAsJsonObject("fields").getAsJsonObject("apps").getAsJsonObject("mapValue")
            .getAsJsonObject("fields").getAsJsonObject("go").getAsJsonObject("mapValue")
            .getAsJsonObject("fields").getAsJsonObject("last_seen_at").get("timestampValue").asString
        assertEquals("2026-10-23T12:00:00Z", ts)

        now += 24 * 60 * 60_000L
        svc.sync()
        assertEquals(2, markerPatches().size)
    }

    @Test
    fun onSignedInWritesMarkerThenSyncs() = runBlocking {
        marker.at = now   // even a marker written moments ago is rewritten on sign-in
        val session = Session("U1", "id-token", "refresh", now + 3_600_000L, null)
        tokens.stored = session
        val svc = service(FakeSpotStore())
        svc.onSignedIn(session)
        assertEquals(1, markerPatches().size)
        assertTrue(requests.indexOf(markerPatches()[0]) < requests.indexOfFirst { it.method == "GET" })
        assertEquals(SyncState.Synced(now), svc.state.value)
    }

    @Test
    fun visionFirstWinsDuplicateDocId() = runBlocking {
        signedIn()
        val dao = FakeSpotStore()
        visionDocs = listOf(doc("spots/D1", "Vision name", 5.0, 5.0, "2026-10-01T00:00:00Z"))
        accountDocs = listOf(doc("users/U1/spots/D1", "Account name", 5.0, 5.0, "2026-10-02T00:00:00Z"))

        service(dao).sync()

        val row = dao.rows.values.single()
        assertEquals("D1", row.cloudId)
        assertEquals("VISION", row.origin)
        assertEquals("Vision name", row.name)
        assertTrue(spotPatches().isEmpty())
    }

    @Test
    fun signedOutSyncIsNoOp() = runBlocking {
        val dao = FakeSpotStore(SavedLocation(id = "L1", name = "x", latitude = 1.0, longitude = 2.0, pendingSync = true))
        val svc = service(dao)
        svc.sync()
        assertEquals(0, server.requestCount)
        assertEquals(0, dao.writes)
        assertEquals(SyncState.SignedOut, svc.state.value)
    }

    @Test
    fun signOutDetachesAndClears() = runBlocking {
        signedIn()
        marker.at = now
        val dao = FakeSpotStore(
            SavedLocation(id = "A", name = "a", latitude = 1.0, longitude = 2.0, cloudId = "W1", pendingSync = true, origin = "ACCOUNT"),
            SavedLocation(id = "B", name = "b", latitude = 1.0, longitude = 2.0, cloudId = "W2", isDeleted = true, pendingSync = true),
        )
        val svc = service(dao)
        svc.signOut()

        assertNull(tokens.stored)
        assertNull(marker.at)
        assertEquals(listOf("A"), dao.rows.keys.toList())
        assertNull(dao.rows["A"]!!.cloudId)
        assertFalse(dao.rows["A"]!!.pendingSync)
        assertEquals(SyncState.SignedOut, svc.state.value)
        assertEquals(0, server.requestCount)
    }
}
