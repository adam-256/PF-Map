package com.pfmap.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.pfmap.app.data.Club

/** Hand-offs to other apps: navigation, Google Maps reviews, phone, web, share. */
object Launch {
    private const val GOOGLE_MAPS = "com.google.android.apps.maps"

    /** Turn-by-turn in Google Maps (falls back to the Maps website if the app isn't installed). */
    fun navigateGoogle(ctx: Context, club: Club) {
        val nav = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=${club.lat},${club.lng}")).setPackage(GOOGLE_MAPS)
        if (!tryStart(ctx, nav)) {
            open(ctx, "https://www.google.com/maps/dir/?api=1&destination=${club.lat},${club.lng}&travelmode=driving")
        }
    }

    /** Any installed navigation app (Waze, OsmAnd, HERE, Organic Maps, Google Maps …) via a geo: chooser. */
    fun navigateOther(ctx: Context, club: Club) {
        val label = Uri.encode("${club.name}, ${club.fullAddress}")
        val geo = Intent(Intent.ACTION_VIEW, Uri.parse("geo:${club.lat},${club.lng}?q=${club.lat},${club.lng}($label)"))
        val chooser = Intent.createChooser(geo, "Navigate with…")
        if (!tryStart(ctx, chooser)) toast(ctx, "No navigation app installed")
    }

    /** The club's Google Maps listing — where the full set of Google reviews lives. */
    fun openGoogleListing(ctx: Context, club: Club, placeId: String?, mapsUri: String?) {
        if (!mapsUri.isNullOrBlank()) return open(ctx, mapsUri)
        val q = Uri.encode("Planet Fitness ${club.fullAddress}")
        val url = "https://www.google.com/maps/search/?api=1&query=$q" + (placeId?.let { "&query_place_id=$it" } ?: "")
        open(ctx, url)
    }

    fun call(ctx: Context, phone: String) {
        if (!tryStart(ctx, Intent(Intent.ACTION_DIAL, Uri.parse("tel:${phone.filter { it.isDigit() || it == '+' }}")))) toast(ctx, "No phone app")
    }

    fun open(ctx: Context, url: String) {
        if (!tryStart(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(url)))) toast(ctx, "No app can open this link")
    }

    fun share(ctx: Context, club: Club) {
        val text = "${club.name}\n${club.fullAddress}\nhttps://www.google.com/maps/search/?api=1&query=${club.lat},${club.lng}"
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        tryStart(ctx, Intent.createChooser(send, "Share club"))
    }

    private fun tryStart(ctx: Context, intent: Intent): Boolean = try {
        ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    } catch (_: ActivityNotFoundException) {
        false
    }

    private fun toast(ctx: Context, msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
}
