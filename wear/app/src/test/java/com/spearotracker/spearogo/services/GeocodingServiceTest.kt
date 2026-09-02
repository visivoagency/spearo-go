package com.spearotracker.spearogo.services

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Recorded from the live Open-Meteo geocoding API on 2026-09-01 for "Lagos".
 *
 * Four places share the name. The one an Algarve diver wants is FOURTH, which
 * is the entire reason results carry a region and a country: a list of four
 * rows reading "Lagos" would be worse than no search at all.
 */
class GeocodingServiceTest {

    private val lagosResponse = """
        {"results":[
          {"name":"Lagos","admin1":"Lagos","country_code":"NG","latitude":6.4541,"longitude":3.3947},
          {"name":"Lagos","admin1":"New Aquitaine","country_code":"FR","latitude":43.2088,"longitude":-0.2248},
          {"name":"Lagos","admin1":"Andalusia","country_code":"ES","latitude":36.7948,"longitude":-3.4359},
          {"name":"Lagos","admin1":"Faro District","country_code":"PT","latitude":37.1020,"longitude":-8.6742}
        ]}
    """.trimIndent()

    private fun parse(json: String) =
        GeocodingService.parse(Gson().fromJson(json, GeocodingResponse::class.java))

    @Test
    fun `four places named Lagos stay distinguishable`() {
        val places = parse(lagosResponse)
        assertEquals(4, places.size)
        assertEquals(
            listOf("NG", "FR", "ES", "PT"),
            places.map { it.country }
        )
        // Every row must render something that tells it from the others.
        assertEquals(4, places.map { it.label }.toSet().size)
    }

    @Test
    fun `the Portuguese Lagos is present with the customer's coordinates`() {
        val portugal = parse(lagosResponse).first { it.country == "PT" }
        assertEquals(37.1020, portugal.latitude, 0.001)
        assertEquals(-8.6742, portugal.longitude, 0.001)
        assertEquals("Faro District", portugal.region)
        assertEquals("Lagos, Faro District", portugal.savedName)
    }

    @Test
    fun `no results is an empty list, not a failure`() {
        assertTrue(parse("""{"results":null}""").isEmpty())
        assertTrue(parse("""{}""").isEmpty())
        assertTrue(parse("""{"results":[]}""").isEmpty())
    }

    @Test
    fun `a place with no region still renders`() {
        val places = parse(
            """{"results":[{"name":"Malpelo","country_code":"CO","latitude":4.0,"longitude":-81.6}]}"""
        )
        assertEquals(1, places.size)
        assertNull(places[0].region)
        assertEquals("CO", places[0].label)
        assertEquals("Malpelo", places[0].savedName)
    }

    @Test
    fun `entries without coordinates are dropped rather than defaulted to zero`() {
        // 0,0 is a real place in the Atlantic. A missing coordinate must never
        // become one, which is the same rule the tide and marine parsing follow.
        val places = parse(
            """{"results":[
                 {"name":"Nowhere","country_code":"XX"},
                 {"name":"Somewhere","country_code":"PT","latitude":37.1,"longitude":-8.7}
               ]}"""
        )
        assertEquals(1, places.size)
        assertEquals("Somewhere", places[0].name)
    }
}
