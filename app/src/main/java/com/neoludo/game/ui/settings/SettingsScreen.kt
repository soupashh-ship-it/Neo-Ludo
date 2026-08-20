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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoludo.game.core.designsystem.NeoLudoCard
import com.neoludo.game.core.designsystem.NeoLudoColors
import com.neoludo.game.core.model.GameSettings
import com.neoludo.game.core.model.ThemeMode

@Composable
fun SettingsScreen(
    settings: GameSettings,
    onUpdateSettings: (GameSettings) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NeoLudoColors.ObsidianBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(36.dp))

            // Top Bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(NeoLudoColors.ObsidianSurfaceCard)
                        .border(1.dp, NeoLudoColors.ObsidianBorder, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Settings",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Appearance Theme
            Text(
                text = "APPEARANCE",
                color = NeoLudoColors.ObsidianTextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

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
                                if (isSelected) NeoLudoColors.CobaltBlue else NeoLudoColors.ObsidianBorder,
                                RoundedCornerShape(14.dp)
                            ),
                        color = if (isSelected) NeoLudoColors.CobaltBlueContainer else NeoLudoColors.ObsidianSurfaceCard
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = label,
                                color = if (isSelected) Color.White else NeoLudoColors.ObsidianTextSecondary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 2. Board Theme Selector
            Text(
                text = "BOARD THEME",
                color = NeoLudoColors.ObsidianTextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
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
                                if (isSelected) NeoLudoColors.EmeraldGreen else NeoLudoColors.ObsidianBorder,
                                RoundedCornerShape(14.dp)
                            ),
                        color = if (isSelected) NeoLudoColors.EmeraldGreenContainer.copy(alpha = 0.6f) else NeoLudoColors.ObsidianSurfaceCard
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
                                    color = if (isSelected) Color.White else NeoLudoColors.ObsidianTextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = bTheme.description,
                                    color = NeoLudoColors.ObsidianTextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(NeoLudoColors.EmeraldGreen),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = Color.Black,
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
            Text(
                text = "3D DICE SKIN",
                color = NeoLudoColors.ObsidianTextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
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
                                if (isSelected) NeoLudoColors.RubyRed else NeoLudoColors.ObsidianBorder,
                                RoundedCornerShape(12.dp)
                            ),
                        color = if (isSelected) NeoLudoColors.RubyRedContainer else NeoLudoColors.ObsidianSurfaceCard
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = dSkin.displayName.replace(" ", "\n"),
                                color = if (isSelected) Color.White else NeoLudoColors.ObsidianTextSecondary,
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
            Text(
                text = "PAWN TOKEN STYLE",
                color = NeoLudoColors.ObsidianTextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
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
                                if (isSelected) NeoLudoColors.AmberYellow else NeoLudoColors.ObsidianBorder,
                                RoundedCornerShape(12.dp)
                            ),
                        color = if (isSelected) NeoLudoColors.AmberYellowContainer else NeoLudoColors.ObsidianSurfaceCard
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = pSkin.displayName.replace(" ", "\n"),
                                color = if (isSelected) Color.White else NeoLudoColors.ObsidianTextSecondary,
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
            Text(
                text = "AUDIO & HAPTICS",
                color = NeoLudoColors.ObsidianTextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            NeoLudoCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Sound Effects Volume", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("${(soundVolume * 100).toInt()}%", color = NeoLudoColors.EmeraldGreen, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Slider(
                            value = soundVolume,
                            onValueChange = {
                                soundVolume = it
                                syncSettings()
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = NeoLudoColors.EmeraldGreen,
                                activeTrackColor = NeoLudoColors.EmeraldGreen
                            )
                        )
                    }

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Music Volume", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("${(musicVolume * 100).toInt()}%", color = NeoLudoColors.CobaltBlue, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Slider(
                            value = musicVolume,
                            onValueChange = {
                                musicVolume = it
                                syncSettings()
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = NeoLudoColors.CobaltBlue,
                                activeTrackColor = NeoLudoColors.CobaltBlue
                            )
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Haptic Vibration Feedback", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Switch(
                            checked = hapticsEnabled,
                            onCheckedChange = {
                                hapticsEnabled = it
                                syncSettings()
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = NeoLudoColors.EmeraldGreen)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Rule Defaults
            Text(
                text = "GAMEPLAY DEFAULTS",
                color = NeoLudoColors.ObsidianTextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            NeoLudoCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Auto-Move Single Legal Piece", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Switch(
                            checked = autoMoveSingle,
                            onCheckedChange = {
                                autoMoveSingle = it
                                syncSettings()
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = NeoLudoColors.EmeraldGreen)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("3x Consecutive Sixes Penalty", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Switch(
                            checked = penalty3xSix,
                            onCheckedChange = {
                                penalty3xSix = it
                                syncSettings()
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = NeoLudoColors.EmeraldGreen)
                        )
                    }
                }
            }

            // App About Info
            NeoLudoCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(text = "Neo Ludo v1.3.0", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(text = "100% Ad-Free • Pure Play Multiplayer", color = NeoLudoColors.ObsidianTextSecondary, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(36.dp))
        }
    }
}
