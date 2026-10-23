package com.spearotracker.spearogo.sync

import android.content.Context

/** The Firebase session Go holds after sign-in. [expiresAt] is epoch milliseconds. */
data class Session(
    val uid: String,
    val idToken: String,
    val refreshToken: String,
    val expiresAt: Long,
    val email: String?,
)

/** Where the session lives between launches. Twin of SpearoGo/Sync/TokenStore.swift. */
interface TokenStore {
    fun load(): Session?
    fun save(session: Session)
    fun clear()
}

/**
 * SharedPreferences "auth", MODE_PRIVATE. Excluded from cloud backup and device transfer
 * (res/xml/backup_rules.xml, res/xml/data_extraction_rules.xml): a restored refresh token
 * on another device would be a credential copied off this one.
 */
class SharedPrefsTokenStore(context: Context) : TokenStore {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun load(): Session? {
        val uid = prefs.getString(UID, null) ?: return null
        val idToken = prefs.getString(ID_TOKEN, null) ?: return null
        val refreshToken = prefs.getString(REFRESH_TOKEN, null) ?: return null
        if (!prefs.contains(EXPIRES_AT)) return null
        return Session(uid, idToken, refreshToken, prefs.getLong(EXPIRES_AT, 0L), prefs.getString(EMAIL, null))
    }

    override fun save(session: Session) {
        prefs.edit()
            .putString(UID, session.uid)
            .putString(ID_TOKEN, session.idToken)
            .putString(REFRESH_TOKEN, session.refreshToken)
            .putLong(EXPIRES_AT, session.expiresAt)
            .putString(EMAIL, session.email)
            .commit()
    }

    override fun clear() {
        prefs.edit().clear().commit()
    }

    private companion object {
        const val PREFS = "auth"
        const val UID = "uid"
        const val ID_TOKEN = "id_token"
        const val REFRESH_TOKEN = "refresh_token"
        const val EXPIRES_AT = "expires_at"
        const val EMAIL = "email"
    }
}
