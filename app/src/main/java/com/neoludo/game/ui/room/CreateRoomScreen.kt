package com.neoludo.game.ui.room

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.neoludo.game.engine.model.LudoRuleSet
import com.neoludo.game.engine.model.PlayerColor
import kotlinx.coroutines.launch

@Composable
fun CreateRoomScreen(
    onCreateRoom: suspend (playerCount: Int, fillBots: Boolean, rules: LudoRuleSet, color: PlayerColor) -> Result<String>,
    onNavigateJoin: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var playerCount by remember { mutableIntStateOf(4) }
    var selectedColor by remember { mutableStateOf(PlayerColor.RED) }
    var timerSeconds by remember { mutableIntStateOf(30) }
    var penalty3xSix by remember { mutableStateOf(true) }
    var autoMoveSingle by remember { mutableStateOf(true) }
    var fillBots by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

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

            if (errorMessage != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFEF4444).copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, Color(0xFFEF4444)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errorMessage ?: "",
                        color = Color(0xFFFCA5A5),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

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
                            .clickable { playerCount = count },
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) NeoLudoColors.CobaltBlue else NeoLudoColors.ObsidianSurfaceCard,
                        border = BorderStroke(1.dp, if (isSelected) NeoLudoColors.CobaltBlue else NeoLudoColors.ObsidianBorder)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "$count Players",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Preferred Color
            Text(
                text = "YOUR TOKEN COLOR",
                color = NeoLudoColors.ObsidianTextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                listOf(
                    PlayerColor.RED to NeoLudoColors.RubyRed,
                    PlayerColor.GREEN to NeoLudoColors.EmeraldGreen,
                    PlayerColor.YELLOW to NeoLudoColors.AmberYellow,
                    PlayerColor.BLUE to NeoLudoColors.CobaltBlue
                ).forEach { (color, displayColor) ->
                    val isSelected = selectedColor == color
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { selectedColor = color },
                        shape = RoundedCornerShape(12.dp),
                        color = displayColor.copy(alpha = if (isSelected) 0.9f else 0.25f),
                        border = BorderStroke(2.dp, if (isSelected) Color.White else Color.Transparent)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Rules Card
            NeoLudoCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(
                        text = "MATCH RULES",
                        color = NeoLudoColors.ObsidianTextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    // Turn Timer
                    Text("Turn Timer", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(15, 30, 45).forEach { sec ->
                            val isSelected = timerSeconds == sec
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { timerSeconds = sec },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) NeoLudoColors.EmeraldGreen else Color(0xFF1E293B)
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${sec}s",
                                        color = if (isSelected) Color.Black else Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 3x Six Penalty Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("3x Consecutive Sixes Penalty", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("Turn forfeits on 3 sixes in a row", color = NeoLudoColors.ObsidianTextMuted, fontSize = 11.sp)
                        }
                        Switch(
                            checked = penalty3xSix,
                            onCheckedChange = { penalty3xSix = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = NeoLudoColors.CobaltBlue)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Auto-Move Single Piece Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-Move Single Piece", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("Auto-hop when only 1 move is legal", color = NeoLudoColors.ObsidianTextMuted, fontSize = 11.sp)
                        }
                        Switch(
                            checked = autoMoveSingle,
                            onCheckedChange = { autoMoveSingle = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = NeoLudoColors.CobaltBlue)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Fill with bots
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Fill Empty Seats with Bots", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("Fill unfilled seats with AI when match starts", color = NeoLudoColors.ObsidianTextMuted, fontSize = 11.sp)
                        }
                        Switch(
                            checked = fillBots,
                            onCheckedChange = { fillBots = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = NeoLudoColors.EmeraldGreen)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = NeoLudoColors.CobaltBlue)
                }
            } else {
                NeoLudoButton(
                    text = "Create Room",
                    accentColor = NeoLudoColors.CobaltBlue,
                    onClick = {
                        isLoading = true
                        errorMessage = null
                        scope.launch {
                            val rules = LudoRuleSet(
                                maxPlayers = playerCount,
                                autoMoveSinglePiece = autoMoveSingle,
                                penalty3xSix = penalty3xSix,
                                turnTimerSeconds = timerSeconds
                            )
                            val result = onCreateRoom(playerCount, fillBots, rules, selectedColor)
                            isLoading = false
                            result.onFailure {
                                errorMessage = it.message ?: "Failed to create room"
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Have a room code? ",
                    color = NeoLudoColors.ObsidianTextMuted,
                    fontSize = 13.sp
                )
                Text(
                    text = "Join Room",
                    color = NeoLudoColors.CobaltBlue,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.clickable(onClick = onNavigateJoin)
                )
            }

            Spacer(modifier = Modifier.height(36.dp))
        }
    }
}
