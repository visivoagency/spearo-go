package com.spearotracker.spearogo.sync

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Runs every case in the shared fixture file (fixtures/spot-sync/merge-cases.json), which the
 * Swift harness also runs. newId choice: the lambda returns the fixed placeholder "NEW"; rows
 * carrying it are compared as "new:<cloudId>", so the expected "new:<docId>" entries are checked
 * without a counter or queue.
 */
class SpotMergeTest {
    private data class Expect(
        val upsertLocal: List<String>, val deleteLocal: List<String>,
        val pushUpsert: List<String>, val pushDelete: List<String>)

    private data class Case(
        val name: String, val local: List<LocalSpot>, val remote: List<RemoteSpot>, val expect: Expect)

    private fun loadCases(): List<Case> {
        val stream = javaClass.classLoader!!.getResourceAsStream("spot-sync/merge-cases.json")
            ?: error("fixture spot-sync/merge-cases.json not on the test classpath")
        val type = object : TypeToken<List<Case>>() {}.type
        return stream.reader().use { Gson().fromJson(it, type) }
    }

    @Test
    fun fixtureCasesAllPass() {
        val cases = loadCases()
        assertTrue("no fixture cases loaded", cases.isNotEmpty())
        for (c in cases) {
            val plan = planSync(c.local, c.remote) { "NEW" }
            val up = plan.upsertLocal.map { if (it.id == "NEW") "new:${it.cloudId}" else it.id }
            assertEquals("${c.name}: upsertLocal", c.expect.upsertLocal.toSet(), up.toSet())
            assertEquals("${c.name}: upsertLocal size", c.expect.upsertLocal.size, up.size)
            assertEquals("${c.name}: deleteLocal", c.expect.deleteLocal.toSet(), plan.deleteLocal.toSet())
            assertEquals("${c.name}: deleteLocal size", c.expect.deleteLocal.size, plan.deleteLocal.size)
            val pu = plan.pushUpsert.map { it.id }
            assertEquals("${c.name}: pushUpsert", c.expect.pushUpsert.toSet(), pu.toSet())
            assertEquals("${c.name}: pushUpsert size", c.expect.pushUpsert.size, pu.size)
            val pd = plan.pushDelete.map { it.docId }
            assertEquals("${c.name}: pushDelete", c.expect.pushDelete.toSet(), pd.toSet())
            assertEquals("${c.name}: pushDelete size", c.expect.pushDelete.size, pd.size)
        }
    }

    @Test
    fun pushedNewSpotUsesItsIdAsDocId() {
        val plan = planSync(
            listOf(LocalSpot("u1", null, "Reef", 1.0, 2.0, 0, 0, false, true, SpotOrigin.GO)),
            emptyList()) { "NEW" }
        assertEquals("u1", plan.pushUpsert.single().cloudId)
    }

    @Test
    fun linkedSpotIsMarkedClean() {
        val plan = planSync(
            listOf(LocalSpot("u1", null, "Reef", 1.0, 2.0, 0, 0, false, true, SpotOrigin.GO)),
            listOf(RemoteSpot("d1", SpotOrigin.VISION, "reef", 1.0, 2.0, null, null, false, true))) { "NEW" }
        val row = plan.upsertLocal.single()
        assertEquals("d1", row.cloudId)
        assertEquals(SpotOrigin.VISION, row.origin)
        assertEquals(false, row.pendingSync)
    }
}
