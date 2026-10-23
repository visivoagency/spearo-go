package com.spearotracker.spearogo.sync

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.spearotracker.spearogo.models.SavedLocation
import kotlinx.coroutines.launch
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
class FakeSpotStore(vararg initial: SavedLocation) : SpotStore, SpotTransactor {
    val rows = Collections.synchronizedMap(LinkedHashMap<String, SavedLocation>().apply { initial.forEach { put(it.id, it) } })
    var writes = 0
    var onWrite: () -> Unit = {}
    var inTransaction = false
    var transactions = 0
    /** Sync-path writes that happened outside a transaction (detachAll runs before sync, so excluded). */
    var writesOutsideTransaction = 0
    var detachCalls = 0
    /** When set, detachAll throws it (a failing Room write). */
    var detachFailure: Exception? = null

    override suspend fun run(block: suspend () -> Unit) {
        transactions++; inTransaction = true
        try { block() } finally { inTransaction = false }
    }
    private fun syncWrite() { writes++; if (!inTransaction) writesOutsideTransaction++; onWrite() }

    override suspend fun getAllIncludingDeleted(): List<SavedLocation> = rows.values.toList()
    override suspend fun upsertAll(rows: List<SavedLocation>) {
        syncWrite(); rows.forEach { this.rows[it.id] = it }
    }
    override suspend fun hardDelete(ids: List<String>) {
        syncWrite(); ids.forEach { rows.remove(it) }
    }
    override suspend fun linkCloudId(id: String, cloudId: String) {
        syncWrite(); rows[id]?.takeIf { it.cloudId == null }?.let { rows[id] = it.copy(cloudId = cloudId) }
    }
    override suspend fun markDeleted(id: String, now: Long) {
        writes++; onWrite()
        rows[id]?.let { rows[id] = it.copy(isDeleted = true, pendingSync = true, lastModifiedAt = now, isActive = false) }
    }
    override suspend fun detachAll() {
        detachCalls++
        detachFailure?.let { throw it }
        writes++; onWrite()
        rows.values.filter { it.isDeleted }.toList().forEach { rows.remove(it.id) }
        rows.replaceAll { _, r -> r.copy(cloudId = null, pendingSync = false) }
    }
}

class InMemoryMarkerStore(var at: Long? = null, var linked: String? = null) : SyncMarkerStore {
    override fun lastWrittenAt(): Long? = at
    override fun setLastWrittenAt(ms: Long) { at = ms }
    override fun clear() { at = null }
    override fun linkedUid(): String? = linked
    override fun setLinkedUid(uid: String) { linked = uid }
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
    /** Runs on the server thread when a spot PATCH arrives, before it is answered. */
    private var onSpotPatch: (RecordedRequest) -> Unit = {}
    /** When true, spot PATCHes are written into [accountDocs], so a later sync reads them back. */
    private var persistPatches = false
    /** `users/U2/spots`: a second account's collection. */
    private var u2Docs = listOf<JsonObject>()
    /** What the Secure Token refresh answers. */
    private var refreshCode = 503
    /** What the account marker PATCH answers. */
    private var markerCode = 200

    private val http = OkHttpClient.Builder().readTimeout(2, TimeUnit.SECONDS).retryOnConnectionFailure(false).build()
    private val base get() = server.url("/v1/projects/p/databases/(default)/documents").toString()

    private fun signedIn() {
        tokens.stored = Session("U1", "id-token", "refresh", now + 30L * 24 * 3_600_000L, null)
    }

    private fun service(dao: FakeSpotStore): SpotSyncService {
        val auth = AuthClient(
            http = http, baseIdentity = server.url("/identity").toString().trimEnd('/'),
            baseSecureToken = server.url("/securetoken").toString().trimEnd('/'),
            store = tokens, clock = { now }, apiKey = "k", log = {},
        )
        val firestore = FirestoreClient(http, base, mapOf("X-Android-Package" to "com.example.go"))
        var n = 0
        return SpotSyncService(auth, firestore, dao, marker, transactor = dao, clock = { now }, newId = { "new-${n++}" }, log = {})
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
                    path.endsWith("/v1/token") -> json(refreshCode, "{}")
                    request.method == "GET" && (path.endsWith("/users/U1/spots") || path.endsWith("/users/U2/spots")) -> {
                        val docs = if (path.endsWith("/users/U1/spots")) accountDocs else u2Docs
                        if (listCode != 200) json(listCode, "{}")
                        else json(200, JsonObject().apply {
                            add("documents", com.google.gson.JsonArray().apply { docs.forEach { add(it) } })
                        }.toString())
                    }
                    request.method == "POST" && path.endsWith(":runQuery") -> {
                        if (queryCode != 200) json(queryCode, "{}")
                        else json(200, com.google.gson.JsonArray().apply {
                            visionDocs.forEach { d -> add(JsonObject().apply { add("document", d) }) }
                            add(JsonObject().apply { addProperty("readTime", "2026-10-23T12:00:00Z") })
                        }.toString())
                    }
                    request.method == "PATCH" && path.contains("/spots/") -> {
                        onSpotPatch(request)
                        if (spotPatchCode == 200 && persistPatches && path.contains("/users/U1/")) persist(request)
                        json(spotPatchCode, "{}")
                    }
                    request.method == "PATCH" && (path.endsWith("/users/U1") || path.endsWith("/users/U2")) ->
                        json(markerCode, "{}")
                    else -> json(404, "{}")
                }
            }
        }
        server.start()
    }

    @After fun tearDown() { server.shutdown() }

    private fun persist(request: RecordedRequest) {
        val docId = request.requestUrl!!.pathSegments.last()
        val sent = JsonParser.parseString(request.body.clone().readUtf8()).asJsonObject.getAsJsonObject("fields")
        val existing = accountDocs.firstOrNull { it.get("name").asString.endsWith("/spots/$docId") }
        val fields = existing?.getAsJsonObject("fields")?.deepCopy() ?: JsonObject()
        sent.entrySet().forEach { (k, v) -> fields.add(k, v) }
        val doc = JsonObject().apply {
            addProperty("name", "projects/p/databases/(default)/documents/users/U1/spots/$docId")
            add("fields", fields)
        }
        accountDocs = accountDocs.filter { it !== existing } + doc
    }

    private fun spotPatches() = requests.filter { it.method == "PATCH" && it.requestUrl!!.encodedPath.contains("/spots/") }
    private fun markerPatches() = requests.filter {
        it.method == "PATCH" && it.requestUrl!!.encodedPath.let { p -> p.endsWith("/users/U1") || p.endsWith("/users/U2") }
    }
    private fun session(uid: String) = Session(uid, "id-token", "refresh-$uid", now + 30L * 24 * 3_600_000L, null)
    private fun isDeletePatch(r: RecordedRequest) =
        r.requestUrl!!.queryParameterValues("updateMask.fieldPaths").let { it.size == 2 && "is_deleted" in it }

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

    /** Task 4's signOutDetachesEverySpot, under the account-scoped design: sign-out keeps every link. */
    @Test
    fun signOutKeepsSpotsAndLinks() = runBlocking {
        signedIn()
        marker.at = now
        marker.linked = "U1"
        val a = SavedLocation(id = "A", name = "a", latitude = 1.0, longitude = 2.0, cloudId = "W1", pendingSync = true, origin = "ACCOUNT")
        val b = SavedLocation(id = "B", name = "b", latitude = 1.0, longitude = 2.0, cloudId = "W2", isDeleted = true, pendingSync = true)
        val dao = FakeSpotStore(a, b)
        val svc = service(dao)
        svc.signOut()

        assertNull(tokens.stored)
        assertNull(marker.at)
        assertEquals("U1", marker.linked)          // the links still belong to U1
        assertEquals(mapOf("A" to a, "B" to b), dao.rows.toMap())
        assertEquals(0, dao.writes)
        assertEquals(SyncState.SignedOut, svc.state.value)
        assertEquals(0, server.requestCount)
    }

    /** C1: a 400 refresh clears the session; a different account then signs in. Nothing is lost. */
    @Test
    fun refresh400ThenOtherAccountKeepsSpots() = runBlocking {
        marker.linked = "U1"
        tokens.stored = Session("U1", "old", "dead", now - 1, null)   // expired: the next sync refreshes
        refreshCode = 400
        val dao = FakeSpotStore(
            SavedLocation(id = "A", name = "Reef", latitude = 1.0, longitude = 2.0, createdAt = 1, cloudId = "W1", origin = "ACCOUNT"),
            SavedLocation(id = "B", name = "Wreck", latitude = 3.0, longitude = 4.0, createdAt = 2, cloudId = "B", pendingSync = true),
            SavedLocation(id = "C", name = "Gone", latitude = 5.0, longitude = 6.0, createdAt = 3, cloudId = "W3",
                isDeleted = true, pendingSync = true, origin = "ACCOUNT"),
        )
        val svc = service(dao)
        svc.sync()
        assertNull(tokens.stored)
        assertEquals(SyncState.SignedOut, svc.state.value)
        assertEquals(3, dao.rows.size)                 // the 400 changed nothing locally

        val u2 = session("U2")
        tokens.stored = u2
        svc.onSignedIn(u2)

        assertTrue(requests.none { it.requestUrl!!.encodedPath.contains("/users/U1") })  // nothing sent to U1
        val patches = spotPatches()
        assertTrue(patches.none(::isDeletePatch))     // C's pending delete was U1's: dropped, not pushed
        assertEquals(setOf("/users/U2/spots/A", "/users/U2/spots/B"),
            patches.map { it.requestUrl!!.encodedPath.substringAfter("/documents") }.toSet())
        assertEquals(setOf("A", "B"), dao.rows.keys)  // A and B kept, C purged with its stale link
        assertEquals("A", dao.rows["A"]!!.cloudId)    // re-linked to the new U2 docs
        assertEquals("B", dao.rows["B"]!!.cloudId)
        assertTrue(dao.rows.values.none { it.pendingSync })
        assertEquals("U2", marker.linked)
        assertEquals(SyncState.Synced(now), svc.state.value)
    }

    @Test
    fun sameAccountReSignInNoDuplicates() = runBlocking {
        signedIn()
        persistPatches = true
        val dao = FakeSpotStore(
            SavedLocation(id = "L1", name = "Home reef", latitude = 10.0, longitude = 10.0, createdAt = 1000, pendingSync = true)
        )
        val svc = service(dao)
        svc.sync()
        assertEquals("L1", dao.rows["L1"]!!.cloudId)
        val pushedBefore = spotPatches().size

        svc.signOut()
        val u1 = session("U1")
        tokens.stored = u1
        svc.onSignedIn(u1)

        assertEquals(0, dao.detachCalls)
        assertEquals(pushedBefore, spotPatches().size) // nothing new pushed
        assertEquals(1, accountDocs.size)
        assertEquals(listOf("L1"), dao.rows.keys.toList())
        assertEquals("L1", dao.rows["L1"]!!.cloudId)
        assertEquals(SyncState.Synced(now), svc.state.value)
    }

    @Test
    fun signedOutDeleteOfUnlinkedRowIsHard() = runBlocking {
        val dao = FakeSpotStore(SavedLocation(id = "L1", name = "x", latitude = 1.0, longitude = 2.0, pendingSync = true))
        service(dao).deleteSpot("L1")
        assertTrue(dao.rows.isEmpty())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun signedOutDeleteOfLinkedRowWaitsForSameAccount() = runBlocking {
        marker.linked = "U1"
        val dao = FakeSpotStore(
            SavedLocation(id = "A", name = "Wreck", latitude = 20.0, longitude = 20.0, createdAt = 1000,
                cloudId = "W1", lastModifiedAt = 1000, origin = "ACCOUNT")
        )
        accountDocs = listOf(doc("users/U1/spots/W1", "Wreck", 20.0, 20.0, "2026-10-01T00:00:00Z"))
        val svc = service(dao)
        svc.deleteSpot("A")

        val row = dao.rows["A"]!!
        assertTrue(row.isDeleted && row.pendingSync)  // kept, hidden, waiting for U1
        assertEquals("W1", row.cloudId)
        assertEquals(0, server.requestCount)

        val u1 = session("U1")
        tokens.stored = u1
        svc.onSignedIn(u1)

        val delete = spotPatches().single()
        assertTrue(isDeletePatch(delete))
        assertTrue(delete.requestUrl!!.encodedPath.endsWith("/users/U1/spots/W1"))
        assertTrue(dao.rows.isEmpty())
    }

    @Test
    fun signedOutVisionDeleteIsLocal() = runBlocking {
        val dao = FakeSpotStore(SavedLocation(id = "V", name = "v", latitude = 1.0, longitude = 2.0, origin = "VISION"))
        service(dao).deleteSpot("V")
        assertTrue(dao.rows.isEmpty())
    }

    @Test
    fun signedInVisionDeleteIsIgnored() = runBlocking {
        signedIn()
        val v = SavedLocation(id = "V", name = "v", latitude = 1.0, longitude = 2.0, cloudId = "D1", origin = "VISION")
        val dao = FakeSpotStore(v)
        service(dao).deleteSpot("V")
        assertEquals(v, dao.rows["V"])
    }

    /** The session changed without onSignedIn (e.g. the app died right after sign-in): sync still detaches first. */
    @Test
    fun syncUnderNewAccountDetachesFirst() = runBlocking {
        marker.linked = "U1"
        tokens.stored = session("U2")
        val dao = FakeSpotStore(
            SavedLocation(id = "A", name = "Reef", latitude = 1.0, longitude = 2.0, createdAt = 1, cloudId = "W1", origin = "ACCOUNT")
        )
        service(dao).sync()
        assertEquals(1, dao.detachCalls)
        assertTrue(spotPatches().single().requestUrl!!.encodedPath.endsWith("/users/U2/spots/A"))
        assertEquals("U2", marker.linked)
    }

    @Test
    fun detachFailureFailsSignIn() = runBlocking {
        marker.linked = "U1"
        val a = SavedLocation(id = "A", name = "Reef", latitude = 1.0, longitude = 2.0, createdAt = 1, cloudId = "W1", origin = "ACCOUNT")
        val dao = FakeSpotStore(a)
        dao.detachFailure = IllegalStateException("disk full")
        val u2 = session("U2")
        tokens.stored = u2
        val svc = service(dao)
        svc.onSignedIn(u2)

        assertEquals(SyncState.Failed("Sync failed. Try again."), svc.state.value)
        assertNull(tokens.stored)                     // the sign-in did not stick
        assertEquals("U1", marker.linked)
        assertEquals(a, dao.rows["A"])
        assertEquals(0, server.requestCount)
    }

    /** A linked doc that exists but can't be decoded (no latitude) is not "gone": the row is left alone. */
    @Test
    fun undecodableLinkedDocLeavesRowUntouched() = runBlocking {
        signedIn()
        marker.linked = "U1"
        val clean = SavedLocation(id = "A", name = "Reef", latitude = 1.0, longitude = 2.0, createdAt = 1,
            cloudId = "W1", origin = "ACCOUNT")
        val pending = SavedLocation(id = "B", name = "Wreck", latitude = 3.0, longitude = 4.0, createdAt = 2,
            cloudId = "W2", pendingSync = true, lastModifiedAt = now, origin = "ACCOUNT")
        val dao = FakeSpotStore(clean, pending)
        fun broken(id: String) = JsonObject().apply {
            addProperty("name", "projects/p/databases/(default)/documents/users/U1/spots/$id")
            add("fields", JsonParser.parseString("""{"name":{"stringValue":"x"},"longitude":{"doubleValue":2.0}}""").asJsonObject)
        }
        accountDocs = listOf(broken("W1"), broken("W2"))

        val svc = service(dao)
        svc.sync()

        assertEquals(clean, dao.rows["A"])            // not deleted by rule 2
        assertEquals(pending, dao.rows["B"])          // not pushed over the doc either
        assertTrue(spotPatches().isEmpty())
        assertEquals(SyncState.Synced(now), svc.state.value)
    }

    @Test
    fun deleteDuringPushIsNotUndone() = runBlocking {
        signedIn()
        val dao = FakeSpotStore(
            SavedLocation(id = "L1", name = "Home reef", latitude = 10.0, longitude = 10.0, createdAt = 1000, pendingSync = true)
        )
        persistPatches = true
        // The user deletes L1 while its upload is on the wire (what LocationDao.markDeleted does).
        onSpotPatch = {
            dao.rows["L1"] = dao.rows["L1"]!!.copy(isDeleted = true, pendingSync = true, lastModifiedAt = now, isActive = false)
            onSpotPatch = {}
        }
        val svc = service(dao)
        svc.sync()

        // The edit survived, and the row still learned which doc its push created.
        val row = dao.rows["L1"]!!
        assertTrue(row.isDeleted && row.pendingSync)
        assertEquals("L1", row.cloudId)

        svc.sync()
        val deletes = spotPatches().filter { "is_deleted" in it.requestUrl!!.queryParameterValues("updateMask.fieldPaths") &&
            it.requestUrl!!.queryParameterValues("updateMask.fieldPaths").size == 2 }
        assertEquals(1, deletes.size)
        assertTrue(deletes[0].requestUrl!!.encodedPath.endsWith("/users/U1/spots/L1"))
        assertTrue(dao.rows.isEmpty())
        assertEquals(0, dao.writesOutsideTransaction)
    }

    @Test
    fun signOutMidSyncAppliesNothing() = runBlocking {
        signedIn()
        val dao = FakeSpotStore(
            SavedLocation(id = "L1", name = "Home reef", latitude = 10.0, longitude = 10.0, createdAt = 1000, pendingSync = true)
        )
        accountDocs = listOf(doc("users/U1/spots/W1", "Wreck", 20.0, 20.0, "2026-10-01T00:00:00Z"))
        lateinit var svc: SpotSyncService
        var signOutJob: kotlinx.coroutines.Job? = null
        onSpotPatch = {
            signOutJob = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch { svc.signOut() }
            val deadline = System.currentTimeMillis() + 2000
            while (tokens.stored != null && System.currentTimeMillis() < deadline) Thread.sleep(5)
        }
        svc = service(dao)
        svc.sync()

        assertEquals(0, dao.writes)              // apply touched nothing
        assertTrue(dao.rows.values.none { it.cloudId == "W1" })
        signOutJob!!.join()
        assertNull(tokens.stored)
        assertEquals(SyncState.SignedOut, svc.state.value)
        val row = dao.rows.values.single()
        assertNull(row.cloudId)
        assertTrue(row.pendingSync)              // sign-out keeps the row exactly as it was
        assertEquals(0, dao.writes)
    }

    @Test
    fun neverPushesOverCamelCaseAccountDoc() = runBlocking {
        signedIn()
        val dao = FakeSpotStore(
            SavedLocation(id = "A", name = "Local edit", latitude = 20.0, longitude = 20.0, createdAt = 1000,
                isActive = true, cloudId = "C1", lastModifiedAt = Instant.parse("2026-10-20T00:00:00Z").toEpochMilli(),
                pendingSync = true, origin = "ACCOUNT")
        )
        accountDocs = listOf(JsonObject().apply {
            addProperty("name", "projects/p/databases/(default)/documents/users/U1/spots/C1")
            add("fields", JsonParser.parseString("""
                {"name":{"stringValue":"Tracker name"},"latitude":{"doubleValue":20.0},"longitude":{"doubleValue":20.0},
                 "isDeleted":{"booleanValue":false},"lastModifiedAt":{"timestampValue":"2026-10-01T00:00:00Z"}}
            """).asJsonObject)
        })

        val svc = service(dao)
        svc.sync()

        assertTrue(spotPatches().isEmpty())
        val row = dao.rows["A"]!!
        assertEquals("Tracker name", row.name)
        assertFalse(row.pendingSync)
        assertEquals("C1", row.cloudId)
        assertTrue(row.isActive)
        assertEquals(SyncState.Synced(now), svc.state.value)
    }

    @Test
    fun applyRunsInOneTransaction() = runBlocking {
        signedIn()
        val dao = FakeSpotStore(
            SavedLocation(id = "L1", name = "Home reef", latitude = 10.0, longitude = 10.0, createdAt = 1000, pendingSync = true)
        )
        accountDocs = listOf(doc("users/U1/spots/W1", "Wreck", 20.0, 20.0, "2026-10-01T00:00:00Z"))
        service(dao).sync()
        assertEquals(1, dao.transactions)
        assertTrue(dao.writes > 0)
        assertEquals(0, dao.writesOutsideTransaction)
    }
}
