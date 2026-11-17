package com.pfmap.app.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Parses All the Places GeoJSON (the same open dataset the GitHub data job uses) into clubs.
 * Mirrors make_club() in scripts/update_clubs.py; used on-device only as a last-resort fallback.
 */
object AtpParser {
    private val US_STATES = setOf(
        "AL", "AK", "AZ", "AR", "CA", "CO", "CT", "DE", "DC", "FL", "GA", "HI", "ID", "IL", "IN", "IA",
        "KS", "KY", "LA", "ME", "MD", "MA", "MI", "MN", "MS", "MO", "MT", "NE", "NV", "NH", "NJ", "NM",
        "NY", "NC", "ND", "OH", "OK", "OR", "PA", "RI", "SC", "SD", "TN", "TX", "UT", "VT", "VA", "WA",
        "WV", "WI", "WY", "PR", "GU", "VI", "AS", "MP",
    )

    fun parse(geojson: String): List<Club> {
        val root = AppJson.parseToJsonElement(geojson).jsonObject
        val features = root["features"]?.jsonArray ?: return emptyList()
        return features.mapNotNull { f ->
            val obj = f.jsonObject
            val geom = obj["geometry"] as? JsonObject ?: return@mapNotNull null
            if (geom["type"]?.jsonPrimitive?.contentOrNull != "Point") return@mapNotNull null
            val coords = geom["coordinates"]?.jsonArray ?: return@mapNotNull null
            val lng = coords[0].jsonPrimitive.doubleOrNull ?: return@mapNotNull null
            val lat = coords[1].jsonPrimitive.doubleOrNull ?: return@mapNotNull null
            val props = (obj["properties"] as? JsonObject)
                ?.mapValues { it.value.jsonPrimitiveOrNull() }.orEmpty()
            makeClub(props, lat, lng)
        }.distinctBy { it.id }
    }

    private fun kotlinx.serialization.json.JsonElement.jsonPrimitiveOrNull(): String =
        runCatching { jsonPrimitive.contentOrNull }.getOrNull().orEmpty()

    private fun clean(s: String?) = s.orEmpty().replace(Regex("\\s+"), " ").trim()

    fun normalizePhone(p: String): String {
        var d = p.filter { it.isDigit() }
        if (d.length == 11 && d.startsWith("1")) d = d.substring(1)
        return if (d.length == 10) "(${d.substring(0, 3)}) ${d.substring(3, 6)}-${d.substring(6)}" else clean(p)
    }

    fun makeClub(p: Map<String, String>, lat: Double, lng: Double): Club? {
        val state = clean(p["addr:state"]).uppercase()
        val country = clean(p["addr:country"]).uppercase()
        if (country.isNotEmpty() && country !in setOf("US", "USA", "PR")) return null
        if (state.isNotEmpty() && state !in US_STATES) return null
        if (lng !in -180.0..-60.0 || lat !in 13.0..72.0) return null
        val street = clean(p["addr:street_address"]).ifEmpty {
            listOf(clean(p["addr:housenumber"]), clean(p["addr:street"])).filter { it.isNotEmpty() }.joinToString(" ")
        }
        val website = clean(p["website"] ?: p["contact:website"])
        var name = clean(p["branch"]).ifEmpty { clean(p["name"]) }.ifEmpty { "Planet Fitness" }
        if (!name.lowercase().startsWith("planet fitness")) name = "Planet Fitness $name".trim()
        val slug = Regex("/gyms/([-\\w]+)").find(website)?.groupValues?.get(1).orEmpty()
        val id = clean(p["ref"]).ifEmpty { slug }.ifEmpty { "%.5f,%.5f".format(lat, lng) }
        return Club(
            id = id, name = name, street = street, city = clean(p["addr:city"]), state = state,
            zip = clean(p["addr:postcode"]).take(10), lat = lat, lng = lng,
            phone = normalizePhone(clean(p["phone"] ?: p["contact:phone"])), url = website,
            hours = clean(p["opening_hours"]),
        )
    }
}
