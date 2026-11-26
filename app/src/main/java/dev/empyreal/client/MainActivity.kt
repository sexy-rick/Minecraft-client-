package dev.empyreal.client

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.empyreal.client.core.state.ThemePrefs
import dev.empyreal.client.ui.nav.AppRoot
import dev.empyreal.client.ui.theme.EmpyrealTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = ThemePrefs(this)

        setContent {
            val systemDark = isSystemInDarkTheme()
            var dark by remember {
                mutableStateOf(
                    when (prefs.darkMode) {
                        ThemePrefs.MODE_LIGHT -> false
                        ThemePrefs.MODE_DARK -> true
                        else -> systemDark
                    },
                )
            }
            var useDynamicColor by remember { mutableStateOf(prefs.dynamicColor) }

            EmpyrealTheme(useDarkTheme = dark, dynamicColor = useDynamicColor) {
                AppRoot(
                    onThemeChanged = { dark = it },
                    onDynamicChanged = { useDynamicColor = it },
                )
            }
        }
    }
}
