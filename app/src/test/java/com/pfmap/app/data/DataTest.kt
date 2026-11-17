package com.pfmap.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DataTest {
    private val atpSample = """
    {"type":"FeatureCollection","features":[
      {"type":"Feature","geometry":{"type":"Point","coordinates":[-73.9857,40.7484]},
       "properties":{"ref":"0001","name":"Planet Fitness","branch":"Midtown","addr:street_address":"350 5th Ave",
         "addr:city":"New York","addr:state":"NY","addr:postcode":"10118","addr:country":"US",
         "phone":"+1 212-555-0100","website":"https://www.planetfitness.com/gyms/midtown-ny","opening_hours":"24/7"}},
      {"type":"Feature","geometry":{"type":"Point","coordinates":[-79.38,43.65]},
       "properties":{"ref":"ca1","name":"Planet Fitness","addr:state":"ON","addr:country":"CA"}},
      {"type":"Feature","geometry":null,"properties":{"ref":"nogeo"}},
      {"type":"Feature","geometry":{"type":"Point","coordinates":[-87.62,41.88]},
       "properties":{"name":"Planet Fitness","addr:housenumber":"1","addr:street":"Main St","addr:city":"Chicago",
         "addr:state":"IL","website":"https://www.planetfitness.com/gyms/chicago-loop"}}
    ]}
    """.trimIndent()

    @Test fun parsesAllThePlaces() {
        val clubs = AtpParser.parse(atpSample)
        assertEquals(2, clubs.size)
        val ny = clubs[0]
        assertEquals("0001", ny.id)
        assertEquals("Planet Fitness Midtown", ny.name)
        assertEquals("(212) 555-0100", ny.phone)
        assertEquals("350 5th Ave, New York, NY 10118", ny.fullAddress)
        val chi = clubs[1]
        assertEquals("chicago-loop", chi.id) // falls back to the URL slug
        assertEquals("1 Main St", chi.street)
    }

    @Test fun rejectsNonUs() {
        assertNull(AtpParser.makeClub(mapOf("addr:country" to "CA"), 43.6, -79.3))
        assertNull(AtpParser.makeClub(mapOf("addr:state" to "ON"), 43.6, -79.3))
        assertNull(AtpParser.makeClub(emptyMap(), 51.5, -0.1)) // London
    }

    @Test fun humanizesHours() {
        assertEquals(listOf("Open 24/7"), Hours.humanize("24/7"))
        assertEquals(
            listOf("Mon–Thu 5 AM–11 PM", "Fri 5 AM–9 PM", "Sat,Sun 7 AM–7 PM"),
            Hours.humanize("Mo-Th 05:00-23:00; Fr 05:00-21:00; Sa,Su 07:00-19:00"),
        )
        assertEquals(listOf("Mon–Fri 12 AM–12 AM"), Hours.humanize("Mo-Fr 00:00-24:00"))
        assertEquals(listOf("Sun 9:30 AM–6 PM"), Hours.humanize("Su 09:30-18:00"))
        assertEquals(emptyList<String>(), Hours.humanize(""))
    }

    @Test fun decodesPublishedFile() {
        val json = """{"version":"abc","generated":"2026-11-17T00:00:00Z","source":"x","count":1,
            "clubs":[{"id":"1","name":"Planet Fitness Test","lat":40.0,"lng":-75.0,"extra":"ignored"}]}"""
        val f = AppJson.decodeFromString<ClubsFile>(json)
        assertEquals(1, f.clubs.size)
        assertEquals("", f.clubs[0].street)
    }
}
