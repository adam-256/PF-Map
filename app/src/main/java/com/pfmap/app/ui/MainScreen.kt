package com.pfmap.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pfmap.app.data.Club
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: MapViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val camera = remember { MutableSharedFlow<CameraCommand>(extraBufferCapacity = 4) }
    val screenHeightPx = with(LocalDensity.current) { LocalConfiguration.current.screenHeightDp.dp.toPx() }

    fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    var locationEnabled by remember { mutableStateOf(hasLocationPermission()) }
    var flyToUserWhenKnown by rememberSaveable { mutableStateOf(true) }
    var showSettings by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) {
            locationEnabled = true
            flyToUserWhenKnown = true
        }
    }

    fun flyToClub(club: Club) {
        // Keep the pin visible above the bottom sheet.
        camera.tryEmit(CameraCommand.FlyTo(club.lat, club.lng, 13.5, bottomPaddingPx = (screenHeightPx * 0.45f).toInt()))
    }

    fun openClub(club: Club) {
        focus.clearFocus()
        vm.setQuery("")
        vm.select(club)
        flyToClub(club)
    }

    LaunchedEffect(state.userLocation) {
        val loc = state.userLocation ?: return@LaunchedEffect
        if (flyToUserWhenKnown && state.selected == null) {
            flyToUserWhenKnown = false
            camera.tryEmit(CameraCommand.FlyTo(loc.latitude, loc.longitude, 10.5))
        }
    }

    Box(Modifier.fillMaxSize()) {
        ClubMap(
            clubs = state.clubs,
            selectedId = state.selected?.id,
            locationEnabled = locationEnabled,
            commands = camera,
            onClubTapped = { id -> state.clubs.firstOrNull { it.id == id }?.let { vm.select(it); flyToClub(it) } },
            onMapTapped = { focus.clearFocus() },
            onUserLocation = vm::setUserLocation,
            modifier = Modifier.fillMaxSize(),
        )

        // Search bar + results + status.
        Column(
            Modifier
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .fillMaxWidth(),
        ) {
            Surface(shape = RoundedCornerShape(28.dp), shadowElevation = 6.dp, tonalElevation = 2.dp) {
                TextField(
                    value = state.query,
                    onValueChange = vm::setQuery,
                    placeholder = { Text("Search city, state, ZIP or address") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) {
                            IconButton(onClick = { vm.setQuery(""); focus.clearFocus() }) { Icon(Icons.Filled.Clear, "Clear") }
                        } else {
                            IconButton(onClick = { showSettings = true }) { Icon(Icons.Filled.Settings, "Settings") }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (state.query.trim().length >= 2) {
                Spacer(Modifier.size(6.dp))
                Surface(shape = RoundedCornerShape(16.dp), shadowElevation = 6.dp) {
                    if (state.results.isEmpty()) {
                        Text("No clubs match “${state.query.trim()}”", Modifier.padding(16.dp))
                    } else {
                        LazyColumn(Modifier.heightIn(max = 380.dp)) {
                            item {
                                Text(
                                    "${MapViewModel.fmt(state.results.size)}${if (state.results.size == 100) "+" else ""} clubs" +
                                        if (state.userLocation != null) " · nearest first" else "",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 4.dp),
                                )
                            }
                            items(state.results, key = { it.id }) { club ->
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { openClub(club) }
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                ) {
                                    Text(club.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        listOfNotNull(
                                            club.fullAddress,
                                            state.userLocation?.let { formatDistance(MapViewModel.distanceTo(it, club)) },
                                        ).joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
            AnimatedVisibility(visible = state.status != null || state.loading, enter = fadeIn(), exit = fadeOut()) {
                val status = state.status ?: Status("Loading clubs…", busy = true)
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
                    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.inverseSurface, shadowElevation = 4.dp) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (status.busy) {
                                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.inverseOnSurface)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(status.text, color = MaterialTheme.colorScheme.inverseOnSurface, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }

        // Map buttons.
        Column(
            Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 16.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.userLocation != null) {
                SmallFloatingActionButton(onClick = { vm.nearest().firstOrNull()?.let { openClub(it) } }) {
                    Icon(Icons.Filled.NearMe, "Nearest club")
                }
            }
            FloatingActionButton(onClick = {
                if (!hasLocationPermission()) {
                    permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                } else {
                    locationEnabled = true
                    val loc = state.userLocation
                    if (loc != null) camera.tryEmit(CameraCommand.FlyTo(loc.latitude, loc.longitude, 11.0)) else flyToUserWhenKnown = true
                }
            }) { Icon(Icons.Filled.MyLocation, "My location") }
        }

        if (state.query.isNotEmpty()) BackHandler { vm.setQuery(""); focus.clearFocus() }
    }

    state.selected?.let { club ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
        ModalBottomSheet(onDismissRequest = { vm.select(null) }, sheetState = sheetState) {
            ClubDetails(
                club = club,
                google = state.google,
                userLocation = state.userLocation,
                onOpenSettings = { showSettings = true },
                onRetryGoogle = vm::retryGoogle,
            )
        }
    }

    if (showSettings) {
        SettingsDialog(
            state = state,
            onSaveKey = vm::setApiKey,
            onCheckUpdates = { vm.checkForUpdates(manual = true) },
            onDismiss = { showSettings = false },
        )
    }
}

@Composable
private fun SettingsDialog(state: UiState, onSaveKey: (String) -> Unit, onCheckUpdates: () -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var key by remember { mutableStateOf(state.apiKey) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Google reviews", fontWeight = FontWeight.SemiBold)
                Text(
                    "Paste a Google Maps Platform API key with “Places API (New)” enabled. It stays on this device. " +
                        "Without it, the “Open reviews in Google Maps” button still works.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(value = key, onValueChange = { key = it }, label = { Text("Places API key") }, singleLine = true)
                TextButton(onClick = { Launch.open(ctx, "https://developers.google.com/maps/documentation/places/web-service/get-api-key") }) {
                    Text("How to get a key")
                }
                HorizontalDivider()
                Text("Club list", fontWeight = FontWeight.SemiBold)
                val d = state.data
                Text(
                    if (d == null) "No club list loaded yet." else
                        "${MapViewModel.fmt(d.clubs.size)} clubs · version ${d.version.take(10)}\nPublished ${d.generated.take(10)}\nSource: ${d.source}",
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = onCheckUpdates) { Text("Check for updates now") }
                Text(
                    "Map © OpenFreeMap, © OpenStreetMap contributors. Club data from All the Places / OpenStreetMap. " +
                        "Not affiliated with Planet Fitness.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSaveKey(key); onDismiss() }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
