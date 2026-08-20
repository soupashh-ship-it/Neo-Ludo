package com.neoludo.game

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.neoludo.game.core.designsystem.NeoLudoColors
import com.neoludo.game.core.designsystem.NeoLudoTheme
import com.neoludo.game.core.model.GameSettings
import com.neoludo.game.ui.navigation.NeoLudoNavHost

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as NeoLudoApplication

        setContent {
            val settings by app.settingsRepository.settings.collectAsState(initial = GameSettings())

            // Synchronize audio and haptic controller settings
            app.soundController.soundVolume = settings.soundVolume
            app.soundController.soundEnabled = settings.soundEnabled
            app.hapticController.hapticsEnabled = settings.hapticsEnabled

            NeoLudoTheme(themeMode = settings.themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = when (settings.themeMode) {
                        com.neoludo.game.core.model.ThemeMode.DARK_OLED -> NeoLudoColors.ObsidianBackground
                        com.neoludo.game.core.model.ThemeMode.LIGHT_TITANIUM -> NeoLudoColors.TitaniumBackground
                        com.neoludo.game.core.model.ThemeMode.SYSTEM -> androidx.compose.material3.MaterialTheme.colorScheme.background
                    }
                ) {
                    NeoLudoNavHost(app = app)
                }
            }
        }
    }
}
