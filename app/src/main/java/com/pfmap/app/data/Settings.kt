package com.pfmap.app.data

import android.content.Context
import com.pfmap.app.BuildConfig

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var placesApiKey: String
        get() = prefs.getString("places_api_key", null)?.takeIf { it.isNotBlank() } ?: BuildConfig.PLACES_API_KEY
        set(value) = prefs.edit().putString("places_api_key", value.trim()).apply()

    val hasUserKey: Boolean get() = !prefs.getString("places_api_key", null).isNullOrBlank()
}
