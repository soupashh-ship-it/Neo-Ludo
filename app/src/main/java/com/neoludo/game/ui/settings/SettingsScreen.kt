package com.neoludo.game.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoludo.game.core.designsystem.AdaptiveScroll
import com.neoludo.game.core.designsystem.NeoLudoCard
import com.neoludo.game.core.designsystem.NeoLudoColors
import com.neoludo.game.core.designsystem.NeoLudoSectionLabel
import com.neoludo.game.core.designsystem.NeoLudoSpacing
import com.neoludo.game.core.designsystem.ScreenHeader
import com.neoludo.game.core.designsystem.StadiumBackground
import com.neoludo.game.core.designsystem.StadiumColors
import com.neoludo.game.core.model.GameSettings
import com.neoludo.game.core.model.ThemeMode

@Composable
fun SettingsScreen(
    settings: GameSettings,
    onUpdateSettings: (GameSettings) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // Real build version (both phones must match for online rooms).
    val appVersion = remember {
        runCatching {
            val pm = context.packageManager
            val info = if (android.os.Build.VERSION.SDK_INT >= 33) {
                pm.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, 0)
            }
            info.versionName ?: "?"
        }.getOrNull() ?: "?"
    }
    var themeMode by remember { mutableStateOf(settings.themeMode) }
    var boardTheme by remember { mutableStateOf(settings.boardTheme) }
    var diceSkin by remember { mutableStateOf(settings.diceSkin) }
    var pawnSkin by remember { mutableStateOf(settings.pawnSkin) }
    var soundVolume by remember { mutableFloatStateOf(settings.soundVolume) }
    var musicVolume by remember { mutableFloatStateOf(settings.musicVolume) }
    var hapticsEnabled by remember { mutableStateOf(settings.hapticsEnabled) }
    var autoMoveSingle by remember { mutableStateOf(settings.autoMoveSinglePiece) }
    var penalty3xSix by remember { mutableStateOf(settings.penalty3xSix) }

    fun syncSettings() {
        onUpdateSettings(
            settings.copy(
                themeMode = themeMode,
                boardTheme = boardTheme,
                diceSkin = diceSkin,
                pawnSkin = pawnSkin,
                soundVolume = soundVolume,
                musicVolume = musicVolume,
                hapticsEnabled = hapticsEnabled,
                autoMoveSinglePiece = autoMoveSingle,
                penalty3xSix = penalty3xSix
            )
        )
    }

    StadiumBackground(modifier = modifier) {
        AdaptiveScroll {
            Spacer(modifier = Modifier.height(NeoLudoSpacing.md))

            ScreenHeader(title = "Settings", onBack = onBack)

            Spacer(modifier = Modifier.height(NeoLudoSpacing.xl))

            // Appearance Theme
            NeoLudoSectionLabel(text = "Appearance")

            Spacer(modifier = Modifier.height(10.dp))

            // 1. App Theme Mode
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                listOf(
                    ThemeMode.DARK_OLED to "Dark OLED",
                    ThemeMode.LIGHT_TITANIUM to "Titanium",
                    ThemeMode.SYSTEM to "System"
                ).forEach { (mode, label) ->
                    val isSelected = themeMode == mode
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                themeMode = mode
                                syncSettings()
                            }
                            .border(
                                1.5.dp,
                                if (isSelected) StadiumColors.Accent else StadiumColors.Border,
                                RoundedCornerShape(14.dp)
                            ),
                        color = if (isSelected) StadiumColors.AccentContainer else StadiumColors.Card
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = label,
                                color = if (isSelected) StadiumColors.TextPrimary else StadiumColors.TextSecondary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 2. Board Theme Selector
            NeoLudoSectionLabel(text = "Board theme")
            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                com.neoludo.game.core.model.BoardTheme.entries.forEach { bTheme ->
                    val isSelected = boardTheme == bTheme
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                boardTheme = bTheme
                                syncSettings()
                            }
                            .border(
                                1.5.dp,
                                if (isSelected) StadiumColors.Accent else StadiumColors.Border,
                                RoundedCornerShape(14.dp)
                            ),
                        color = if (isSelected) StadiumColors.AccentContainer.copy(alpha = 0.6f) else StadiumColors.Card
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = bTheme.displayName,
                                    color = StadiumColors.TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = bTheme.description,
                                    color = StadiumColors.TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(StadiumColors.Accent),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 3. 3D Dice Skin Selector
            NeoLudoSectionLabel(text = "Dice skin")
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                com.neoludo.game.core.model.DiceSkin.entries.forEach { dSkin ->
                    val isSelected = diceSkin == dSkin
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                diceSkin = dSkin
                                syncSettings()
                            }
                            .border(
                                1.5.dp,
                                if (isSelected) StadiumColors.Accent else StadiumColors.Border,
                                RoundedCornerShape(12.dp)
                            ),
                        color = if (isSelected) StadiumColors.AccentContainer else StadiumColors.Card
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = dSkin.displayName.replace(" ", "\n"),
                                color = if (isSelected) StadiumColors.TextPrimary else StadiumColors.TextSecondary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 11.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 4. Pawn Token Skin Selector
            NeoLudoSectionLabel(text = "Pawn style")
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                com.neoludo.game.core.model.PawnSkin.entries.forEach { pSkin ->
                    val isSelected = pawnSkin == pSkin
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                pawnSkin = pSkin
                                syncSettings()
                            }
                            .border(
                                1.5.dp,
                                if (isSelected) StadiumColors.Accent else StadiumColors.Border,
                                RoundedCornerShape(12.dp)
                            ),
                        color = if (isSelected) StadiumColors.AccentContainer else StadiumColors.Card
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = pSkin.displayName.replace(" ", "\n"),
                                color = if (isSelected) StadiumColors.TextPrimary else StadiumColors.TextSecondary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 11.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Audio & Haptics
            NeoLudoSectionLabel(text = "Audio & haptics")

            Spacer(modifier = Modifier.height(10.dp))

            NeoLudoCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Sound Effects Volume", color = StadiumColors.TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("${(soundVolume * 100).toInt()}%", color = StadiumColors.Success, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Slider(
                            value = soundVolume,
                            onValueChange = { soundVolume = it },
                            onValueChangeFinished = { syncSettings() },
                            colors = SliderDefaults.colors(
                                thumbColor = StadiumColors.Success,
                                activeTrackColor = StadiumColors.Success
                            )
                        )
                    }

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Music Volume", color = StadiumColors.TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("${(musicVolume * 100).toInt()}%", color = StadiumColors.AccentBright, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Slider(
                            value = musicVolume,
                            onValueChange = { musicVolume = it },
                            onValueChangeFinished = { syncSettings() },
                            colors = SliderDefaults.colors(
                                thumbColor = StadiumColors.Accent,
                                activeTrackColor = StadiumColors.Accent
                            )
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Haptic Vibration Feedback", color = StadiumColors.TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Switch(
                            checked = hapticsEnabled,
                            onCheckedChange = {
                                hapticsEnabled = it
                                syncSettings()
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = StadiumColors.Success)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Rule Defaults
            NeoLudoSectionLabel(text = "Gameplay defaults")

            Spacer(modifier = Modifier.height(10.dp))

            NeoLudoCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Auto-Move Single Legal Piece", color = StadiumColors.TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Switch(
                            checked = autoMoveSingle,
                            onCheckedChange = {
                                autoMoveSingle = it
                                syncSettings()
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = StadiumColors.Success)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("3x Consecutive Sixes Penalty", color = StadiumColors.TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Switch(
                            checked = penalty3xSix,
                            onCheckedChange = {
                                penalty3xSix = it
                                syncSettings()
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = StadiumColors.Success)
                        )
                    }
                }
            }

            // App About Info
            NeoLudoCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(text = "Neo Ludo v$appVersion", color = StadiumColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(text = "100% Ad-Free • Pure Play Multiplayer", color = StadiumColors.TextSecondary, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(36.dp))
        }
    }
}
