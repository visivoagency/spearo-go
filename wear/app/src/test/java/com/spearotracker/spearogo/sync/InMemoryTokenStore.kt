package com.spearotracker.spearogo.sync

/** Test double for [TokenStore]: holds the session in a field. */
class InMemoryTokenStore(var stored: Session? = null) : TokenStore {
    override fun load(): Session? = stored
    override fun save(session: Session) { stored = session }
    override fun clear() { stored = null }
}
