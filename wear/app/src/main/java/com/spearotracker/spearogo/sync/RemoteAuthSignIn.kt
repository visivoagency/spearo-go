package com.spearotracker.spearogo.sync

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.wear.phone.interactions.authentication.CodeChallenge
import androidx.wear.phone.interactions.authentication.CodeVerifier
import androidx.wear.phone.interactions.authentication.OAuthRequest
import androidx.wear.phone.interactions.authentication.OAuthResponse
import androidx.wear.phone.interactions.authentication.RemoteAuthClient
import com.google.gson.JsonParser
import com.spearotracker.spearogo.BuildConfig
import com.spearotracker.spearogo.utils.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.coroutines.resume

/**
 * Google sign-in for Wear: the OAuth page opens in the paired phone's browser (RemoteAuth,
 * PKCE), the watch exchanges the returned code for a Google ID token, and [AuthClient]
 * swaps that for a Firebase session. On-watch GoogleSignIn is never used — it fails with
 * 12501 on Wear.
 *
 * Ported from Spearo Apnea's FirestoreSyncClient.signInWithRemoteAuth/exchangeCodeForTokens,
 * minus `prompt=consent` and `access_type=offline` (Firebase issues its own refresh token),
 * minus the `profile` scope (Go collects no name or photo) and minus all identity logging.
 */
class RemoteAuthSignIn(
    private val context: Context,
    private val http: OkHttpClient,
    private val auth: AuthClient,
) {
    // RemoteAuth adds this exact redirect itself, from the running package — so the
    // .sideload build uses its own (both are registered on the web client, setup P1).
    private val redirectUri = "https://wear.googleapis.com/3p_auth/${context.packageName}"

    suspend fun signIn(): SignInResult {
        if (BuildConfig.WEB_CLIENT_SECRET.isBlank()) {
            Log.w(TAG, "WEB_CLIENT_SECRET missing from local.properties")
            return SignInResult.Failed("Sign-in isn't set up in this build")
        }

        val verifier = CodeVerifier()
        val authUrl = Uri.parse(GOOGLE_AUTH_URL).buildUpon()
            .appendQueryParameter("client_id", Constants.Firebase.WEB_CLIENT_ID)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", SCOPES)
            .build()
        val request = OAuthRequest.Builder(context)
            .setAuthProviderUrl(authUrl)
            .setCodeChallenge(CodeChallenge(verifier))
            .build()

        // RemoteAuth can stay silent forever (phone asleep, browser abandoned): give up after 5 min.
        val outcome = withTimeoutOrNull(PHONE_TIMEOUT_MS) { requestOnPhone(request) }
            ?: return SignInResult.Failed("Sign-in timed out. Try again.")
        val responseUrl = when (outcome) {
            is PhoneOutcome.Error -> return outcome.result
            is PhoneOutcome.Response -> outcome.response.responseUrl
        } ?: return SignInResult.Failed("No answer from your phone")

        val code = responseUrl.getQueryParameter("code")
        if (code.isNullOrBlank()) {
            val error = responseUrl.getQueryParameter("error")
            Log.w(TAG, "no authorization code (error=$error)")
            return if (error == "access_denied") SignInResult.Cancelled
            else SignInResult.Failed("Google sign-in didn't complete")
        }

        val idToken = exchangeCodeForIdToken(code, verifier)
            ?: return SignInResult.Failed("Couldn't finish Google sign-in")
        return auth.signInWithIdp("google.com", idToken, null)
    }

    private sealed interface PhoneOutcome {
        data class Response(val response: OAuthResponse) : PhoneOutcome
        data class Error(val result: SignInResult) : PhoneOutcome
    }

    private suspend fun requestOnPhone(request: OAuthRequest): PhoneOutcome {
        val client = RemoteAuthClient.create(context)
        try {
            return suspendCancellableCoroutine { cont ->
                client.sendAuthorizationRequest(
                    request,
                    { runnable -> runnable.run() },
                    object : RemoteAuthClient.Callback() {
                        override fun onAuthorizationResponse(request: OAuthRequest, response: OAuthResponse) {
                            if (cont.isActive) cont.resume(PhoneOutcome.Response(response))
                        }

                        override fun onAuthorizationError(request: OAuthRequest, errorCode: Int) {
                            Log.w(TAG, "RemoteAuth error $errorCode")
                            val result = when (errorCode) {
                                RemoteAuthClient.ERROR_PHONE_UNAVAILABLE ->
                                    SignInResult.Failed("Keep your phone nearby and try again")
                                RemoteAuthClient.ERROR_UNSUPPORTED ->
                                    SignInResult.Failed("This watch can't sign in through your phone")
                                else -> SignInResult.Failed("Sign-in on your phone failed")
                            }
                            if (cont.isActive) cont.resume(PhoneOutcome.Error(result))
                        }
                    }
                )
            }
        } finally {
            client.close()
        }
    }

    /** Authorization code -> Google ID token. Logs status codes only, never bodies (they hold tokens). */
    private suspend fun exchangeCodeForIdToken(code: String, verifier: CodeVerifier): String? =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(GOOGLE_TOKEN_URL)
                .post(
                    FormBody.Builder()
                        .add("code", code)
                        .add("client_id", Constants.Firebase.WEB_CLIENT_ID)
                        .add("client_secret", BuildConfig.WEB_CLIENT_SECRET)
                        .add("redirect_uri", redirectUri)
                        .add("grant_type", "authorization_code")
                        .add("code_verifier", verifier.value)
                        .build()
                )
                .build()
            try {
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "token exchange HTTP ${response.code}")
                        return@withContext null
                    }
                    val json = JsonParser.parseString(response.body?.string().orEmpty())
                        .takeIf { it.isJsonObject }?.asJsonObject ?: return@withContext null
                    json.get("id_token")?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }
                }
            } catch (e: Exception) {
                Log.w(TAG, "token exchange failed (${e.javaClass.simpleName})")
                null
            }
        }

    private companion object {
        const val TAG = "RemoteAuthSignIn"
        const val GOOGLE_AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth"
        const val GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token"
        /** No `profile`: Go collects no name or photo (store privacy answers depend on it). */
        const val SCOPES = "openid email"
        const val PHONE_TIMEOUT_MS = 5 * 60_000L
    }
}
