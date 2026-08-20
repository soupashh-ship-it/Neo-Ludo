package com.neoludo.game.ui.room

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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.neoludo.game.core.designsystem.NeoLudoButton
import com.neoludo.game.core.designsystem.NeoLudoCard
import com.neoludo.game.core.designsystem.NeoLudoColors
import com.neoludo.game.engine.model.PlayerColor

@Composable
fun CreateRoomScreen(
    onRoomCreated: (roomId: String, playerCount: Int, color: PlayerColor) -> Unit,
    onNavigateJoin: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var playerCount by remember { mutableIntStateOf(4) }
    var selectedColor by remember { mutableStateOf(PlayerColor.RED) }
    var timerSeconds by remember { mutableIntStateOf(30) }
    var penalty3xSix by remember { mutableStateOf(true) }
    var autoMoveSingle by remember { mutableStateOf(true) }

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

            // Top bar
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
                    text = "Create Private Room",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Player Count
            Text(
                text = "PLAYER COUNT",
                color = NeoLudoColors.ObsidianTextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                listOf(2, 3, 4).forEach { count ->
                    val isSelected = playerCount == count
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { playerCount = count }
                            .border(
                                1.5.dp,
                                if (isSelected) NeoLudoColors.EmeraldGreen else NeoLudoColors.ObsidianBorder,
                                RoundedCornerShape(14.dp)
                            ),
                        color = if (isSelected) NeoLudoColors.EmeraldGreenContainer else NeoLudoColors.ObsidianSurfaceCard
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "$count Players",
                                color = if (isSelected) Color.White else NeoLudoColors.ObsidianTextSecondary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Color Preference
            Text(
                text = "YOUR COLOR",
                color = NeoLudoColors.ObsidianTextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PlayerColor.entries.forEach { color ->
                    val isSelected = selectedColor == color
                    val pColor = NeoLudoColors.getPlayerColor(color)
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { selectedColor = color }
                            .border(
                                2.dp,
                                if (isSelected) pColor else Color.Transparent,
                                RoundedCornerShape(14.dp)
                            ),
                        color = NeoLudoColors.getPlayerContainer(color)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Rule Settings
            Text(
                text = "RULE PREFERENCES",
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
                        Column {
                            Text(text = "Turn Timer", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text(text = "$timerSeconds seconds per turn", color = NeoLudoColors.ObsidianTextSecondary, fontSize = 12.sp)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(15, 30, 45).forEach { sec ->
                                val isSelected = timerSeconds == sec
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) NeoLudoColors.CobaltBlue else NeoLudoColors.ObsidianSurface)
                                        .clickable { timerSeconds = sec }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "${sec}s",
                                        color = if (isSelected) Color.White else NeoLudoColors.ObsidianTextSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "3x Six Penalty", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text(text = "Forfeit turn on 3 consecutive sixes", color = NeoLudoColors.ObsidianTextSecondary, fontSize = 12.sp)
                        }
                        Switch(
                            checked = penalty3xSix,
                            onCheckedChange = { penalty3xSix = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = NeoLudoColors.EmeraldGreen)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "Auto-Move Single Piece", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text(text = "Fast-forward when only 1 legal move", color = NeoLudoColors.ObsidianTextSecondary, fontSize = 12.sp)
                        }
                        Switch(
                            checked = autoMoveSingle,
                            onCheckedChange = { autoMoveSingle = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = NeoLudoColors.EmeraldGreen)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            NeoLudoButton(
                text = "Generate Room Code",
                accentColor = NeoLudoColors.EmeraldGreen,
                onClick = {
                    val code = "NL-" + (1000..9999).random()
                    onRoomCreated(code, playerCount, selectedColor)
                },
            )

            Spacer(modifier = Modifier.height(14.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onNavigateJoin)
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Have a code? Join Existing Room →",
                    color = NeoLudoColors.CobaltBlue,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(36.dp))
        }
    }
}
