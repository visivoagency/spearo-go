package com.spearotracker.spearogo.sync

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.spearotracker.spearogo.utils.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder
import java.security.MessageDigest

sealed interface SignInResult {
    data class Success(val session: Session) : SignInResult
    data object AccountExistsWithDifferentMethod : SignInResult
    data object Cancelled : SignInResult
    data class Failed(val message: String) : SignInResult
}

/**
 * Firebase Auth over REST: exchanges a provider ID token for a Firebase session
 * (Identity Toolkit `accounts:signInWithIdp`) and keeps its ID token fresh (Secure Token
 * `token`). No Firebase SDK. Twin of SpearoGo/Sync/AuthClient.swift; the rules are pinned
 * by AuthClientTest:
 *  - the ID token is refreshed when it expires within 5 minutes;
 *  - a refresh answered with HTTP 400 means the grant is dead: the store is cleared;
 *  - any other refresh failure (network, 5xx, bad body) returns null and keeps the session;
 *  - one refresh at a time: concurrent callers share its result, success or failure;
 *  - a refresh only saves or clears if the store still holds the session it started from,
 *    so it can never undo a sign-out or overwrite/clear a newer sign-in.
 * Never logs tokens, uid or email.
 */
class AuthClient(
    private val http: OkHttpClient,
    private val baseIdentity: String,
    private val baseSecureToken: String,
    private val store: TokenStore,
    private val clock: () -> Long,
    private val apiKey: String = Constants.Firebase.API_KEY,
    /** API-key restriction headers ([AppIdentityHeaders.of]); FirestoreClient sends the same. */
    private val appHeaders: Map<String, String> = emptyMap(),
    /** Warning sink; never given tokens, uid or email. Injectable because android.util.Log is absent in JVM tests. */
    private val log: (String) -> Unit = { Log.w(TAG, it) },
) {
    /** Guards [inFlight] and every store write, so check-then-write is atomic. */
    private val gate = Any()
    private var inFlight: Deferred<String?>? = null
    private val refreshScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun signInWithIdp(providerId: String, idToken: String, nonce: String?): SignInResult {
        val postBody = buildString {
            append("id_token=").append(enc(idToken))
            append("&providerId=").append(enc(providerId))
            if (nonce != null) append("&nonce=").append(enc(nonce))
        }
        val body = JsonObject().apply {
            addProperty("postBody", postBody)
            addProperty("requestUri", "http://localhost")
            addProperty("returnSecureToken", true)
            addProperty("returnIdpCredential", true)
        }
        val request = Request.Builder()
            .url("$baseIdentity/v1/accounts:signInWithIdp?key=$apiKey")
            .withAppHeaders()
            .post(body.toString().toRequestBody(JSON))
            .build()

        val (code, text) = try {
            execute(request)
        } catch (e: IOException) {
            log("signInWithIdp: network error (${e.javaClass.simpleName})")
            return SignInResult.Failed("No connection. Try again.")
        }
        val json = parse(text)

        if (code !in 200..299) {
            val message = (json?.get("error") as? JsonObject)?.str("message").orEmpty()
            log("signInWithIdp: HTTP $code ${message.substringBefore(' ')}")
            if (isAccountExists(message)) return SignInResult.AccountExistsWithDifferentMethod
            return SignInResult.Failed(message.ifBlank { "Sign-in failed (HTTP $code)" })
        }
        if (json == null) return SignInResult.Failed("Sign-in failed: unreadable response")

        val needConfirmation = json.get("needConfirmation")
            ?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isBoolean }?.asBoolean == true
        val errorMessage = json.str("errorMessage").orEmpty()
        if (needConfirmation || isAccountExists(errorMessage)) {
            return SignInResult.AccountExistsWithDifferentMethod
        }
        if (errorMessage.isNotBlank()) return SignInResult.Failed(errorMessage)

        val uid = json.str("localId")
        val firebaseIdToken = json.str("idToken")
        val refreshToken = json.str("refreshToken")
        val expiresIn = json.str("expiresIn")?.toLongOrNull()
        if (uid.isNullOrBlank() || firebaseIdToken.isNullOrBlank() || refreshToken.isNullOrBlank() || expiresIn == null) {
            return SignInResult.Failed("Sign-in failed: incomplete response")
        }
        val session = Session(
            uid = uid,
            idToken = firebaseIdToken,
            refreshToken = refreshToken,
            expiresAt = clock() + expiresIn * 1000L,
            email = json.str("email")?.takeIf { it.isNotBlank() },
        )
        synchronized(gate) { store.save(session) }
        return SignInResult.Success(session)
    }

    /** Valid ID token, refreshing if expiring within 5 min; null and store cleared if refresh is rejected. */
    suspend fun currentIdToken(): String? {
        val refresh = synchronized(gate) {
            inFlight ?: run {
                val session = store.load() ?: return null
                if (isFresh(session)) return session.idToken
                refreshScope.async(start = CoroutineStart.LAZY) { refresh(session) }.also { inFlight = it }
            }
        }
        refresh.start()
        try {
            return refresh.await()
        } finally {
            synchronized(gate) { if (inFlight === refresh) inFlight = null }
        }
    }

    fun session(): Session? = store.load()

    fun signOut() = synchronized(gate) { store.clear() }

    private fun isFresh(session: Session) = clock() < session.expiresAt - REFRESH_WINDOW_MS

    /**
     * Saves or clears only if the store still holds [started] (same refresh token).
     * Otherwise someone signed out or in meanwhile: hand back their token if it is fresh.
     */
    private fun commitIfUnchanged(started: Session, write: () -> String?): String? = synchronized(gate) {
        val current = store.load()
        if (current?.refreshToken != started.refreshToken) {
            return current?.takeIf(::isFresh)?.idToken
        }
        write()
    }

    private suspend fun refresh(session: Session): String? {
        val request = Request.Builder()
            .url("$baseSecureToken/v1/token?key=$apiKey")
            .withAppHeaders()
            .post(
                FormBody.Builder()
                    .add("grant_type", "refresh_token")
                    .add("refresh_token", session.refreshToken)
                    .build()
            )
            .build()

        val (code, text) = try {
            execute(request)
        } catch (e: IOException) {
            log("refresh: network error (${e.javaClass.simpleName}); keeping session")
            return null
        }
        if (code == 400) {
            log("refresh: rejected (HTTP 400); signing out")
            return commitIfUnchanged(session) { store.clear(); null }
        }
        if (code !in 200..299) {
            log("refresh: HTTP $code; keeping session")
            return null
        }
        val json = parse(text) ?: return null
        val idToken = json.str("id_token")
        val expiresIn = json.str("expires_in")?.toLongOrNull()
        if (idToken.isNullOrBlank() || expiresIn == null) return null
        val refreshToken = json.str("refresh_token")?.takeIf { it.isNotBlank() } ?: session.refreshToken
        return commitIfUnchanged(session) {
            store.save(
                session.copy(idToken = idToken, refreshToken = refreshToken, expiresAt = clock() + expiresIn * 1000L)
            )
            idToken
        }
    }

    private fun Request.Builder.withAppHeaders(): Request.Builder = apply {
        appHeaders.forEach { (k, v) -> header(k, v) }
    }

    private suspend fun execute(request: Request): Pair<Int, String> = withContext(Dispatchers.IO) {
        http.newCall(request).execute().use { it.code to (it.body?.string() ?: "") }
    }

    private fun parse(text: String): JsonObject? = try {
        JsonParser.parseString(text).takeIf { it.isJsonObject }?.asJsonObject
    } catch (e: Exception) {
        null
    }

    private fun JsonObject.str(key: String): String? =
        get(key)?.takeIf(JsonElement::isJsonPrimitive)?.asString

    companion object {
        private const val TAG = "AuthClient"
        private const val REFRESH_WINDOW_MS = 5 * 60_000L
        private val JSON = "application/json; charset=utf-8".toMediaType()

        private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

        private fun isAccountExists(message: String): Boolean =
            message.contains("FEDERATED_USER_ID_ALREADY_LINKED") || message.contains("EMAIL_EXISTS")
    }
}

/**
 * Headers that satisfy an Android-restricted Google API key: the package name and the
 * SHA-1 of the signing certificate (uppercase hex, no colons), read at runtime so the
 * debug, sideload and Play-signed builds each send their own.
 */
object AppIdentityHeaders {
    fun of(context: Context): Map<String, String> {
        val pkg = context.packageName
        val cert = signingCertSha1(context)
        return buildMap {
            put("X-Android-Package", pkg)
            if (cert != null) put("X-Android-Cert", cert)
        }
    }

    private fun signingCertSha1(context: Context): String? = try {
        val info = context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        // The certificate the APK is signed with now — what an Android-restricted key checks.
        val current = info.signingInfo?.apkContentsSigners?.firstOrNull()
        current?.let { sig ->
            MessageDigest.getInstance("SHA-1").digest(sig.toByteArray())
                .joinToString("") { "%02X".format(it) }
        }
    } catch (e: Exception) {
        Log.w(TAG, "signing certificate unavailable (${e.javaClass.simpleName})")
        null
    }

    private const val TAG = "AppIdentityHeaders"
}
