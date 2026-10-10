package com.gizlidepo.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

object Session {
    var unlocked by mutableStateOf(false)
    var skip = false
}

class MainActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        // Son uygulamalar ekraninda ve ekran goruntusunde icerik gorunmesin
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        val s = Store(this)
        setContent { App(s) }
    }
    override fun onStart() { super.onStart(); Session.skip = false }
    override fun onStop() { super.onStop(); if (!Session.skip) Session.unlocked = false }
}

@Composable
fun App(s: Store) {
    var dark by remember { mutableStateOf(s.dark) }
    var setup by remember { mutableStateOf(false) }
    val scheme = if (dark) darkColorScheme(
        primary = Color(0xFF8FA4FF), onPrimary = Color(0xFF0F1117),
        background = Color(0xFF0F1117), onBackground = Color(0xFFE6E8F0),
        surface = Color(0xFF1A1D27), onSurface = Color(0xFFE6E8F0),
        onSurfaceVariant = Color(0xFF9AA0B4)
    ) else lightColorScheme(
        primary = Color(0xFF4F6BED), onPrimary = Color.White,
        background = Color(0xFFF3F5FA), onBackground = Color(0xFF1B1E28),
        surface = Color.White, onSurface = Color(0xFF1B1E28),
        onSurfaceVariant = Color(0xFF6B7185)
    )
    MaterialTheme(colorScheme = scheme) {
        Surface(Modifier.fillMaxSize(), color = scheme.background) {
            if (!Session.unlocked || setup) {
                AuthScreen(s, setup) { Session.unlocked = true; setup = false }
            } else {
                Home(s, dark, { dark = it; s.dark = it }, { setup = true })
            }
        }
    }
}
