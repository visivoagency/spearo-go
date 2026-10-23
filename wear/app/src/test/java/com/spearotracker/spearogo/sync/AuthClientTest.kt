package com.spearotracker.spearogo.sync

import com.google.gson.JsonParser
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

/**
 * Identity Toolkit / Secure Token REST behaviour, against MockWebServer.
 * SpearoGo/Sync/AuthClient.swift follows the same rules (no Swift test target yet).
 */
class AuthClientTest {
    private lateinit var server: MockWebServer
    private val store = InMemoryTokenStore()
    private var now = 1_000_000_000_000L

    private val http = OkHttpClient.Builder()
        .readTimeout(2, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    private fun client() = AuthClient(
        http = http,
        baseIdentity = server.url("/identity").toString().trimEnd('/'),
        baseSecureToken = server.url("/securetoken").toString().trimEnd('/'),
        store = store,
        clock = { now },
        apiKey = "test-key",
        appHeaders = mapOf("X-Android-Package" to "com.example.go", "X-Android-Cert" to "ABCDEF"),
        log = {},
    )

    private fun json(code: Int, body: String) =
        MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

    private val okSignIn = """
        {"localId":"uid-1","idToken":"id-1","refreshToken":"refresh-1","expiresIn":"3600","email":"diver@example.com"}
    """.trimIndent()

    private fun storedSession(expiresAt: Long) = Session(
        uid = "uid-1", idToken = "old-id", refreshToken = "refresh-1", expiresAt = expiresAt, email = null,
    )

    @Before fun setUp() { server = MockWebServer().also { it.start() } }
    @After fun tearDown() { server.shutdown() }

    @Test
    fun successStoresSession() = runBlocking {
        server.enqueue(json(200, okSignIn))

        val result = client().signInWithIdp("google.com", "google-id-token", null)

        val expected = Session("uid-1", "id-1", "refresh-1", now + 3_600_000L, "diver@example.com")
        assertEquals(SignInResult.Success(expected), result)
        assertEquals(expected, store.stored)
    }

    @Test
    fun signInRequestShape() = runBlocking {
        server.enqueue(json(200, okSignIn))

        client().signInWithIdp("apple.com", "apple+token/=", "raw-nonce")

        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/identity/v1/accounts:signInWithIdp?key=test-key", req.path)
        assertEquals("com.example.go", req.getHeader("X-Android-Package"))
        assertEquals("ABCDEF", req.getHeader("X-Android-Cert"))
        val body = JsonParser.parseString(req.body.readUtf8()).asJsonObject
        assertEquals("http://localhost", body.get("requestUri").asString)
        assertTrue(body.get("returnSecureToken").asBoolean)
        assertTrue(body.get("returnIdpCredential").asBoolean)
        val postBody = body.get("postBody").asString.split("&").associate {
            val (k, v) = it.split("=", limit = 2)
            k to URLDecoder.decode(v, "UTF-8")
        }
        assertEquals(mapOf("id_token" to "apple+token/=", "providerId" to "apple.com", "nonce" to "raw-nonce"), postBody)
    }

    @Test
    fun needConfirmationIsAccountExists() = runBlocking {
        server.enqueue(json(200, """{"needConfirmation":true,"email":"diver@example.com"}"""))

        assertEquals(SignInResult.AccountExistsWithDifferentMethod, client().signInWithIdp("google.com", "t", null))
        assertNull(store.stored)
    }

    @Test
    fun emailExistsIsAccountExists() = runBlocking {
        server.enqueue(json(400, """{"error":{"code":400,"message":"EMAIL_EXISTS"}}"""))
        assertEquals(SignInResult.AccountExistsWithDifferentMethod, client().signInWithIdp("apple.com", "t", "n"))

        server.enqueue(json(200, """{"errorMessage":"FEDERATED_USER_ID_ALREADY_LINKED"}"""))
        assertEquals(SignInResult.AccountExistsWithDifferentMethod, client().signInWithIdp("apple.com", "t", "n"))
        assertNull(store.stored)
    }

    @Test
    fun otherErrorIsFailedAndKeepsNoSession() = runBlocking {
        server.enqueue(json(400, """{"error":{"code":400,"message":"INVALID_IDP_RESPONSE : Invalid id_token"}}"""))

        val result = client().signInWithIdp("google.com", "t", null)

        // Plain copy, never the raw Firebase code.
        assertEquals(SignInResult.Failed("Sign-in failed. Try again."), result)
        assertNull(store.stored)

        server.enqueue(json(200, """{"errorMessage":"INVALID_IDP_RESPONSE : bad"}"""))
        assertEquals(SignInResult.Failed("Sign-in failed. Try again."), client().signInWithIdp("google.com", "t", null))

        server.enqueue(json(503, ""))
        assertEquals(SignInResult.Failed("Sign-in failed. Try again."), client().signInWithIdp("google.com", "t", null))
    }

    @Test
    fun freshTokenNeedsNoNetwork() = runBlocking {
        store.stored = storedSession(expiresAt = now + 6 * 60_000L)

        assertEquals("old-id", client().currentIdToken())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun refreshesWhenExpiring() = runBlocking {
        store.stored = storedSession(expiresAt = now + 60_000L)
        server.enqueue(json(200, """
            {"id_token":"new-id","refresh_token":"refresh-2","expires_in":"3600","user_id":"uid-1","token_type":"Bearer"}
        """.trimIndent()))

        assertEquals("new-id", client().currentIdToken())

        assertEquals(1, server.requestCount)
        val req = server.takeRequest()
        assertEquals("/securetoken/v1/token?key=test-key", req.path)
        assertEquals("com.example.go", req.getHeader("X-Android-Package"))
        val form = req.body.readUtf8().split("&").associate {
            val (k, v) = it.split("=", limit = 2)
            k to URLDecoder.decode(v, "UTF-8")
        }
        assertEquals("refresh_token", form["grant_type"])
        assertEquals("refresh-1", form["refresh_token"])
        assertEquals(Session("uid-1", "new-id", "refresh-2", now + 3_600_000L, null), store.stored)
    }

    @Test
    fun refreshFailureSignsOut() = runBlocking {
        store.stored = storedSession(expiresAt = now + 60_000L)
        server.enqueue(json(400, """{"error":{"code":400,"message":"TOKEN_EXPIRED"}}"""))

        val c = client()
        assertNull(c.currentIdToken())
        assertNull(c.session())
    }

    @Test
    fun networkErrorKeepsSession() = runBlocking {
        store.stored = storedSession(expiresAt = now + 60_000L)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START)
        }

        val c = client()
        assertNull(c.currentIdToken())
        assertNotNull(c.session())
    }

    @Test
    fun serverErrorKeepsSession() = runBlocking {
        store.stored = storedSession(expiresAt = now + 60_000L)
        server.enqueue(json(503, """{"error":{"code":503,"message":"UNAVAILABLE"}}"""))

        val c = client()
        assertNull(c.currentIdToken())
        assertNotNull(c.session())
    }

    // ── Races: a refresh in flight must never undo a sign-out or a newer sign-in ──

    @Test
    fun signOutDuringRefreshStaysSignedOut() = runBlocking {
        store.stored = storedSession(expiresAt = now + 60_000L)
        val c = client()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                c.signOut()
                return json(200, """{"id_token":"new-id","refresh_token":"refresh-2","expires_in":"3600"}""")
            }
        }

        assertNull(c.currentIdToken())
        assertNull(c.session())
    }

    @Test
    fun signInDuringRejectedRefreshSurvives() = runBlocking {
        store.stored = storedSession(expiresAt = now + 60_000L)
        val c = client()
        val newSignIn = """
            {"localId":"uid-2","idToken":"id-new","refreshToken":"refresh-new","expiresIn":"3600"}
        """.trimIndent()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.path!!.startsWith("/identity")) json(200, newSignIn)
                else {
                    // A sign-in completes while the refresh is on the wire; then the old grant is rejected.
                    runBlocking { c.signInWithIdp("google.com", "t", null) }
                    json(400, """{"error":{"code":400,"message":"TOKEN_EXPIRED"}}""")
                }
        }

        assertEquals("id-new", c.currentIdToken())
        assertEquals(Session("uid-2", "id-new", "refresh-new", now + 3_600_000L, null), c.session())
    }

    // ── One refresh at a time: concurrent callers share its result, success or failure ──

    @Test
    fun concurrentCallersShareOneRefresh() = runBlocking {
        store.stored = storedSession(expiresAt = now + 60_000L)
        server.enqueue(json(200, """{"id_token":"new-id","refresh_token":"refresh-2","expires_in":"3600"}""")
            .setBodyDelay(200, TimeUnit.MILLISECONDS))
        val c = client()

        val tokens = listOf(async { c.currentIdToken() }, async { c.currentIdToken() }).awaitAll()

        assertEquals(listOf("new-id", "new-id"), tokens)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun concurrentCallersShareOneFailedRefresh() = runBlocking {
        store.stored = storedSession(expiresAt = now + 60_000L)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                Thread.sleep(200)
                return MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START)
            }
        }
        val c = client()

        val tokens = listOf(async { c.currentIdToken() }, async { c.currentIdToken() }).awaitAll()

        assertEquals(listOf(null, null), tokens)
        assertEquals(1, server.requestCount)
        assertNotNull(c.session())
    }

    @Test
    fun signOutClearsStore() {
        store.stored = storedSession(expiresAt = now + 60_000L)
        val c = client()
        c.signOut()
        assertNull(c.session())
    }
}
