package com.pfmap.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.pfmap.app.ui.MainScreen
import com.pfmap.app.ui.MapViewModel

class MainActivity : ComponentActivity() {
    private val vm: MapViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AppTheme { MainScreen(vm) } }
    }
}

private val Light = lightColorScheme(
    primary = Color(0xFF6A2C91),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEBDCFF),
    onPrimaryContainer = Color(0xFF2A0B44),
    secondary = Color(0xFF8A6D00),
    secondaryContainer = Color(0xFFFFE08A),
    surface = Color(0xFFFFFBFF),
)
private val Dark = darkColorScheme(
    primary = Color(0xFFD7B8FF),
    onPrimary = Color(0xFF3E1260),
    primaryContainer = Color(0xFF55207A),
    onPrimaryContainer = Color(0xFFEBDCFF),
    secondary = Color(0xFFFFD23F),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
