package com.neoludo.game.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.neoludo.game.core.designsystem.NeoLudoButton
import com.neoludo.game.core.designsystem.NeoLudoColors
import com.neoludo.game.core.model.UserProfile
import com.neoludo.game.core.model.UserStats
import com.neoludo.game.engine.ai.Difficulty
import com.neoludo.game.engine.model.PlayerColor

@Composable
fun HomeScreen(
    profile: UserProfile,
    stats: UserStats,
    onNavigateFriends: () -> Unit,
    onNavigateJoinRoom: () -> Unit = onNavigateFriends,
    onStartLocal: (playerCount: Int) -> Unit,
    onStartAi: (difficulty: String, playerCount: Int, color: String) -> Unit,
    onNavigateProfile: () -> Unit,
    onNavigateSettings: () -> Unit,
    onNavigateRules: () -> Unit,
    onNavigateFriendsList: () -> Unit,
    onNavigateLocker: () -> Unit,
    onClaimDailyBonus: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showAiSetupDialog by remember { mutableStateOf(false) }
    var showLocalSetupDialog by remember { mutableStateOf(false) }
    var dailyClaimed by rememberSaveable { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF070A12),
                        Color(0xFF0C1322),
                        Color(0xFF060910)
                    )
                )
            )
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                Spacer(modifier = Modifier.height(28.dp))
                HomeHeader(
                    profile = profile,
                    onNavigateProfile = onNavigateProfile,
                    onNavigateLocker = onNavigateLocker,
                    onNavigateFriends = onNavigateFriendsList,
                    onNavigateSettings = onNavigateSettings,
                    onNavigateRules = onNavigateRules
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                PlayWithFriendsCard(
                    onCreateRoom = onNavigateFriends,
                    onJoinRoom = onNavigateJoinRoom
                )
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Text(
                        text = "GAME MODES",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeoLudoColors.ObsidianTextMuted,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        GameModeTile(
                            title = "Vs Computer",
                            subtitle = "Play with AI bots",
                            icon = Icons.Default.SmartToy,
                            accentColor = NeoLudoColors.EmeraldGreen,
                            onClick = { showAiSetupDialog = true },
                            modifier = Modifier.weight(1f)
                        )

                        GameModeTile(
                            title = "Pass & Play",
                            subtitle = "Local multiplayer",
                            icon = Icons.Default.SportsEsports,
                            accentColor = NeoLudoColors.AmberYellow,
                            onClick = { showLocalSetupDialog = true },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
                // Daily Reward Card
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = NeoLudoColors.ObsidianSurfaceCard,
                    border = BorderStroke(1.dp, NeoLudoColors.ObsidianBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = NeoLudoColors.AmberYellow.copy(alpha = 0.2f),
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.CardGiftcard,
                                        contentDescription = null,
                                        tint = NeoLudoColors.AmberYellow,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Daily Login Reward",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = if (dailyClaimed) "Claimed (+200 Coins)" else "+200 Coins + 10 Gems",
                                    fontSize = 12.sp,
                                    color = if (dailyClaimed) NeoLudoColors.EmeraldGreen else NeoLudoColors.AmberYellow
                                )
                            }
                        }

                        if (!dailyClaimed) {
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        dailyClaimed = true
                                        onClaimDailyBonus()
                                    },
                                shape = RoundedCornerShape(12.dp),
                                color = NeoLudoColors.AmberYellow
                            ) {
                                Text(
                                    text = "Claim",
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                )
                            }
                        } else {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Claimed",
                                tint = NeoLudoColors.EmeraldGreen,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(20.dp))
                CareerStatsSummaryCard(
                    stats = stats,
                    onNavigateStats = onNavigateProfile
                )
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }

    // AI Setup Dialog
    if (showAiSetupDialog) {
        var selectedDifficulty by remember { mutableStateOf(Difficulty.NORMAL) }
        var selectedCount by remember { mutableIntStateOf(4) }
        var selectedColor by remember { mutableStateOf(PlayerColor.RED) }

        Dialog(onDismissRequest = { showAiSetupDialog = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = NeoLudoColors.ObsidianSurfaceCard,
                border = BorderStroke(1.dp, NeoLudoColors.ObsidianBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Vs Computer Match",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Text("DIFFICULTY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NeoLudoColors.ObsidianTextMuted)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(Difficulty.EASY, Difficulty.NORMAL, Difficulty.HARD).forEach { diff ->
                            val isSelected = selectedDifficulty == diff
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { selectedDifficulty = diff },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) NeoLudoColors.EmeraldGreen else Color(0xFF1E293B),
                                border = BorderStroke(1.dp, if (isSelected) NeoLudoColors.EmeraldGreen else Color.Transparent)
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = diff.name.take(4),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.Black else Color.White
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("PLAYERS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NeoLudoColors.ObsidianTextMuted)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(2, 3, 4).forEach { count ->
                            val isSelected = selectedCount == count
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { selectedCount = count },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) NeoLudoColors.CobaltBlue else Color(0xFF1E293B)
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "$count Players",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    NeoLudoButton(
                        text = "Start AI Game",
                        accentColor = NeoLudoColors.EmeraldGreen,
                        onClick = {
                            showAiSetupDialog = false
                            onStartAi(selectedDifficulty.name, selectedCount, selectedColor.name)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    // Local Pass & Play Setup Dialog
    if (showLocalSetupDialog) {
        var selectedCount by remember { mutableIntStateOf(4) }

        Dialog(onDismissRequest = { showLocalSetupDialog = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = NeoLudoColors.ObsidianSurfaceCard,
                border = BorderStroke(1.dp, NeoLudoColors.ObsidianBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Pass & Play Setup",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Play together on this single phone offline.",
                        fontSize = 12.sp,
                        color = NeoLudoColors.ObsidianTextMuted
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("PLAYERS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NeoLudoColors.ObsidianTextMuted)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(2, 3, 4).forEach { count ->
                            val isSelected = selectedCount == count
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { selectedCount = count },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) NeoLudoColors.AmberYellow else Color(0xFF1E293B)
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "$count Players",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.Black else Color.White
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    NeoLudoButton(
                        text = "Start Local Game",
                        accentColor = NeoLudoColors.AmberYellow,
                        onClick = {
                            showLocalSetupDialog = false
                            onStartLocal(selectedCount)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
