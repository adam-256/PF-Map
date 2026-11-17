package com.pfmap.app.ui

import android.location.Location
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.automirrored.filled.StarHalf
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pfmap.app.data.Club
import com.pfmap.app.data.Hours
import com.pfmap.app.data.Place
import com.pfmap.app.data.PlaceReview
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClubDetails(
    club: Club,
    google: GoogleState,
    userLocation: Location?,
    onOpenSettings: () -> Unit,
    onRetryGoogle: () -> Unit,
) {
    val ctx = LocalContext.current
    val place = (google as? GoogleState.Loaded)?.place
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
    ) {
        Text(club.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(club.street, style = MaterialTheme.typography.bodyLarge)
        Text(club.cityLine, style = MaterialTheme.typography.bodyLarge)
        val meta = buildList {
            userLocation?.let { add(formatDistance(MapViewModel.distanceTo(it, club))) }
            place?.rating?.let { add("★ %.1f".format(it)) }
        }
        if (meta.isNotEmpty()) {
            Text(
                meta.joinToString("  ·  "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Spacer(Modifier.height(16.dp))
        Button(onClick = { Launch.navigateGoogle(ctx, club) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Icon(Icons.Filled.Directions, null)
            Spacer(Modifier.width(8.dp))
            Text("Navigate with Google Maps", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { Launch.navigateOther(ctx, club) }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Icon(Icons.AutoMirrored.Filled.DirectionsRun, null)
            Spacer(Modifier.width(8.dp))
            Text("Navigate with another app…")
        }
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (club.phone.isNotBlank()) {
                AssistChip(onClick = { Launch.call(ctx, club.phone) }, label = { Text(club.phone) },
                    leadingIcon = { Icon(Icons.Filled.Call, null, Modifier.size(18.dp)) })
            }
            if (club.url.isNotBlank()) {
                AssistChip(onClick = { Launch.open(ctx, club.url) }, label = { Text("Club page") },
                    leadingIcon = { Icon(Icons.Filled.Language, null, Modifier.size(18.dp)) })
            }
            AssistChip(onClick = { Launch.share(ctx, club) }, label = { Text("Share") },
                leadingIcon = { Icon(Icons.Filled.Share, null, Modifier.size(18.dp)) })
        }

        val hours = Hours.humanize(club.hours)
        if (hours.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Filled.Schedule, null, Modifier.padding(top = 2.dp).size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column { hours.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) } }
            }
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))
        Text("Google reviews", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        when (google) {
            GoogleState.NoKey -> {
                Text(
                    "Add a Google Places API key in Settings to see the rating and reviews right here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onOpenSettings) { Text("Add API key") }
            }
            GoogleState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Loading Google reviews…", style = MaterialTheme.typography.bodyMedium)
            }
            is GoogleState.Error -> {
                Text(google.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRetryGoogle) { Text("Retry") }
            }
            is GoogleState.Loaded -> GoogleSection(place)
        }
        OutlinedButton(
            onClick = { Launch.openGoogleListing(ctx, club, place?.id, place?.googleMapsUri) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            Icon(Icons.Filled.Map, null)
            Spacer(Modifier.width(8.dp))
            Text(if (place?.userRatingCount != null) "All ${MapViewModel.fmt(place.userRatingCount)} reviews on Google Maps" else "Open reviews in Google Maps")
            Spacer(Modifier.width(4.dp))
            Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(16.dp))
        }
    }
}

@Composable
private fun GoogleSection(place: Place?) {
    if (place == null) {
        Text("This club wasn't found on Google.", style = MaterialTheme.typography.bodyMedium)
        return
    }
    if (place.rating != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("%.1f".format(place.rating), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(12.dp))
            Column {
                Stars(place.rating, 22)
                Text(
                    "${MapViewModel.fmt(place.userRatingCount ?: 0)} Google reviews",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    place.reviews.forEach { ReviewItem(it) }
    Text(
        "Ratings and reviews from Google",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun ReviewItem(r: PlaceReview) {
    val ctx = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val author = r.authorAttribution
    Column(Modifier.padding(top = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(32.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    author?.displayName?.firstOrNull()?.uppercase() ?: "?",
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    author?.displayName ?: "Google user",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = if (!author?.uri.isNullOrBlank()) Modifier.clickable { Launch.open(ctx, author!!.uri) } else Modifier,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Stars(r.rating, 14)
                    Spacer(Modifier.width(6.dp))
                    Text(r.relativePublishTimeDescription, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (r.body.isNotBlank()) {
            Text(
                r.body,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp).clickable { expanded = !expanded },
            )
        }
    }
}

@Composable
fun Stars(rating: Double, sizeDp: Int) {
    val gold = Color(0xFFF4B400)
    Row {
        for (i in 1..5) {
            val icon = when {
                rating >= i - 0.25 -> Icons.Filled.Star
                rating >= i - 0.75 -> Icons.AutoMirrored.Filled.StarHalf
                else -> Icons.Filled.StarOutline
            }
            Icon(icon, null, Modifier.size(sizeDp.dp), tint = gold)
        }
    }
}

fun formatDistance(meters: Float): String {
    val miles = meters / 1609.344f
    return if (miles < 0.1f) "${(meters * 3.28084f).toInt()} ft away"
    else String.format(Locale.US, if (miles < 10) "%.1f mi away" else "%.0f mi away", miles)
}
