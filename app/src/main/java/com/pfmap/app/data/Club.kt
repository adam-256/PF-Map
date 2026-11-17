package com.pfmap.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class Club(
    val id: String,
    val name: String,
    val street: String = "",
    val city: String = "",
    val state: String = "",
    val zip: String = "",
    val lat: Double,
    val lng: Double,
    val phone: String = "",
    val url: String = "",
    val hours: String = "",
) {
    val cityLine: String
        get() = listOf(city, listOf(state, zip).filter { it.isNotBlank() }.joinToString(" "))
            .filter { it.isNotBlank() }.joinToString(", ")

    val fullAddress: String
        get() = listOf(street, cityLine).filter { it.isNotBlank() }.joinToString(", ")

    /** Lower-cased text used by search. */
    val searchText: String by lazy { "$name $street $city $state $zip".lowercase() }
}

/** The whole club list as published by scripts/update_clubs.py. */
@Serializable
data class ClubsFile(
    val version: String,
    val generated: String,
    val source: String = "",
    val count: Int = 0,
    val clubs: List<Club> = emptyList(),
)

/** Tiny manifest polled on every startup to see whether the list changed. */
@Serializable
data class ClubsMeta(
    val version: String,
    val generated: String,
    val source: String = "",
    val count: Int = 0,
)

internal val AppJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
    coerceInputValues = true
}
