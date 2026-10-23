package com.spearotracker.spearogo.ui

import com.spearotracker.spearogo.sync.SyncState
import com.spearotracker.spearogo.ui.pages.syncStatusText
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncStatusTextTest {
    private val t0 = 1_000_000_000L

    @Test fun justNow() = assertEquals("Synced · just now", syncStatusText(SyncState.Synced(t0), t0 + 59_000))
    @Test fun minutes() = assertEquals("Synced · 5 min ago", syncStatusText(SyncState.Synced(t0), t0 + 5 * 60_000))
    @Test fun hours() = assertEquals("Synced · 2 h ago", syncStatusText(SyncState.Synced(t0), t0 + 125 * 60_000))
    @Test fun clockSkewNeverNegative() = assertEquals("Synced · just now", syncStatusText(SyncState.Synced(t0), t0 - 5 * 60_000))
    @Test fun syncing() = assertEquals("Syncing…", syncStatusText(SyncState.Syncing, t0))
    @Test fun failureShowsItsMessage() = assertEquals("Sync failed. Try again.", syncStatusText(SyncState.Failed("Sync failed. Try again."), t0))
}
