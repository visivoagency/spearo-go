package com.spearotracker.spearogo.sync

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Table-driven over fixtures/spot-sync/codec-cases.json (the Swift harness runs the same file). */
class SpotCodecTest {
    private data class Case(val file: String, val origin: SpotOrigin, val expect: RemoteSpot?)

    private fun resource(path: String): String =
        (javaClass.classLoader!!.getResourceAsStream(path) ?: error("fixture $path not on classpath"))
            .reader().use { it.readText() }

    @Test
    fun fixtureCasesAllPass() {
        val cases = Gson().fromJson(resource("spot-sync/codec-cases.json"), Array<Case>::class.java)
        assertTrue("no codec cases", cases.isNotEmpty())
        for (c in cases) {
            val doc = JsonParser.parseString(resource("spot-sync/docs/${c.file}.json")).asJsonObject
            assertEquals(c.file, c.expect, SpotCodec.decode(doc, c.origin))
        }
    }

    @Test
    fun missingLatitudeIsNullNotDefaulted() {
        val doc = JsonParser.parseString(resource("spot-sync/docs/missing-latitude.json")).asJsonObject
        assertNull(SpotCodec.decode(doc, SpotOrigin.ACCOUNT))
    }

    private val spot = LocalSpot("g1", null, "Reef", 37.5, -8.25, 1_700_000_000_000, 1_700_000_100_000,
        false, true, SpotOrigin.GO)

    @Test
    fun encodeUpsertHasNoTrackerKeys() {
        val f = SpotCodec.encodeUpsert(spot, "U1", "2026-10-23T12:00:00Z")
        val keys = f.keySet()
        assertTrue(!keys.contains("isDeleted") && !keys.contains("lastModifiedAt"))
        assertEquals(setOf("user_id", "name", "latitude", "longitude", "is_private", "is_deleted",
            "created_at", "last_modified_at", "last_synced_at", "cloud_id", "source", "sync_status"), keys)
        assertEquals("U1", f["user_id"].asJsonObject["stringValue"].asString)
        assertEquals("go", f["source"].asJsonObject["stringValue"].asString)
        assertEquals("synced", f["sync_status"].asJsonObject["stringValue"].asString)
        assertEquals("g1", f["cloud_id"].asJsonObject["stringValue"].asString)
        assertEquals(true, f["is_private"].asJsonObject["booleanValue"].asBoolean)
        assertEquals(false, f["is_deleted"].asJsonObject["booleanValue"].asBoolean)
        assertEquals(37.5, f["latitude"].asJsonObject["doubleValue"].asDouble, 0.0)
        assertEquals("2023-11-14T22:13:20Z", f["created_at"].asJsonObject["stringValue"].asString)
        assertEquals("2023-11-14T22:15:00Z", f["last_modified_at"].asJsonObject["stringValue"].asString)
        assertEquals("2026-10-23T12:00:00Z", f["last_synced_at"].asJsonObject["stringValue"].asString)
    }

    private fun remote(camel: Boolean) = RemoteSpot("d1", SpotOrigin.ACCOUNT, "n", 1.0, 2.0, null, null, false, camel)

    @Test
    fun encodeSoftDeleteKeepsCasing() {
        val (cf, cm) = SpotCodec.encodeSoftDelete(remote(true), "2026-10-23T12:00:00Z")
        assertEquals(listOf("isDeleted", "lastModifiedAt"), cm)
        assertEquals(true, cf["isDeleted"].asJsonObject["booleanValue"].asBoolean)
        assertEquals("2026-10-23T12:00:00Z", cf["lastModifiedAt"].asJsonObject["timestampValue"].asString)
        val (sf, sm) = SpotCodec.encodeSoftDelete(remote(false), "2026-10-23T12:00:00Z")
        assertEquals(listOf("is_deleted", "last_modified_at"), sm)
        assertEquals(true, sf["is_deleted"].asJsonObject["booleanValue"].asBoolean)
        assertEquals("2026-10-23T12:00:00Z", sf["last_modified_at"].asJsonObject["stringValue"].asString)
    }
}
