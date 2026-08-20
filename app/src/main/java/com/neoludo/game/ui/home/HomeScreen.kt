package com.neoludo.game.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.neoludo.game.core.designsystem.NeoLudoButton
import com.neoludo.game.core.designsystem.NeoLudoCard
import com.neoludo.game.core.designsystem.NeoLudoColors
import com.neoludo.game.core.model.UserProfile
import com.neoludo.game.core.model.UserStats
import com.neoludo.game.engine.ai.Difficulty
import com.neoludo.game.engine.model.PlayerColor

@Composable
fun HomeScreen(
    profile: UserProfile,
    stats: UserStats,
    onStartOnline: (playerCount: Int) -> Unit,
    onNavigateFriends: () -> Unit,
    onStartLocal: (playerCount: Int) -> Unit,
    onStartAi: (difficulty: String, playerCount: Int, color: String) -> Unit,
    onNavigateProfile: () -> Unit,
    onNavigateSettings: () -> Unit,
    onNavigateRules: () -> Unit,
    onNavigateFriendsList: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAiSetupDialog by remember { mutableStateOf(false) }
    var showLocalSetupDialog by remember { mutableStateOf(false) }
    var showOnlineSetupDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NeoLudoColors.ObsidianBackground)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(36.dp))
                HomeHeader(
                    profile = profile,
                    stats = stats,
                    onProfileClick = onNavigateProfile,
                    onSettingsClick = onNavigateSettings,
                    onRulesClick = onNavigateRules
                )
            }

            item {
                Text(
                    text = "SELECT GAME MODE",
                    color = NeoLudoColors.ObsidianTextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
            }

            item {
                GameModeCard(
                    title = "Play Online",
                    subtitle = "Real-time match with players worldwide",
                    icon = Icons.Default.Public,
                    accentColor = NeoLudoColors.CobaltBlue,
                    badgeText = "MULTIPLAYER",
                    onClick = { showOnlineSetupDialog = true }
                )
            }

            item {
                GameModeCard(
                    title = "Play with Friends",
                    subtitle = "Create private room or join with 6-char code",
                    icon = Icons.Default.Group,
                    accentColor = NeoLudoColors.EmeraldGreen,
                    badgeText = "CUSTOM ROOM",
                    onClick = onNavigateFriends
                )
            }

            item {
                GameModeCard(
                    title = "Pass & Play",
                    subtitle = "2, 3, or 4 players on this device offline",
                    icon = Icons.Default.SportsEsports,
                    accentColor = NeoLudoColors.AmberYellow,
                    badgeText = "LOCAL OFFLINE",
                    onClick = { showLocalSetupDialog = true }
                )
            }

            item {
                GameModeCard(
                    title = "Vs Computer",
                    subtitle = "Choose Easy, Normal, or Hard AI & 2-4 Players",
                    icon = Icons.Default.SmartToy,
                    accentColor = NeoLudoColors.RubyRed,
                    badgeText = "SOLO BOT",
                    onClick = { showAiSetupDialog = true }
                )
            }

            item {
                QuickStatsCard(
                    stats = stats,
                    onFriendsClick = onNavigateFriendsList
                )
                Spacer(modifier = Modifier.height(28.dp))
            }
        }

        // 1. Vs Computer Setup Dialog (Difficulty, Players, Color)
        if (showAiSetupDialog) {
            VsComputerSetupDialog(
                onDismiss = { showAiSetupDialog = false },
                onStartGame = { diff, count, color ->
                    showAiSetupDialog = false
                    onStartAi(diff.name, count, color.name)
                }
            )
        }

        // 2. Pass & Play Setup Dialog (2, 3, 4 Players)
        if (showLocalSetupDialog) {
            LocalMatchSetupDialog(
                onDismiss = { showLocalSetupDialog = false },
                onStartGame = { count ->
                    showLocalSetupDialog = false
                    onStartLocal(count)
                }
            )
        }

        // 3. Online Matchmaking Setup Dialog
        if (showOnlineSetupDialog) {
            OnlineMatchSetupDialog(
                onDismiss = { showOnlineSetupDialog = false },
                onQuickMatch = { count ->
                    showOnlineSetupDialog = false
                    onStartOnline(count)
                },
                onCreateCustomRoom = {
                    showOnlineSetupDialog = false
                    onNavigateFriends()
                }
            )
        }
    }
}

@Composable
private fun VsComputerSetupDialog(
    onDismiss: () -> Unit,
    onStartGame: (difficulty: Difficulty, playerCount: Int, color: PlayerColor) -> Unit
) {
    var selectedDifficulty by remember { mutableStateOf(Difficulty.NORMAL) }
    var selectedPlayerCount by remember { mutableIntStateOf(4) }
    var selectedColor by remember { mutableStateOf(PlayerColor.RED) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = NeoLudoColors.ObsidianSurfaceCard,
            border = androidx.compose.foundation.BorderStroke(1.5.dp, NeoLudoColors.RubyRed.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Vs Computer Match",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Section 1: AI Difficulty
                Text(
                    text = "AI DIFFICULTY",
                    color = NeoLudoColors.ObsidianTextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val difficulties = listOf(
                        Triple(Difficulty.EASY, "Easy", NeoLudoColors.EmeraldGreen),
                        Triple(Difficulty.NORMAL, "Normal", NeoLudoColors.AmberYellow),
                        Triple(Difficulty.HARD, "Hard", NeoLudoColors.RubyRed)
                    )

                    difficulties.forEach { (diff, label, color) ->
                        val isSelected = selectedDifficulty == diff
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) color.copy(alpha = 0.2f) else NeoLudoColors.ObsidianSurface)
                                .border(
                                    1.5.dp,
                                    if (isSelected) color else NeoLudoColors.ObsidianBorder,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { selectedDifficulty = diff }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = label,
                                    color = if (isSelected) color else Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }

                // Difficulty Description
                Spacer(modifier = Modifier.height(6.dp))
                val diffDescription = when (selectedDifficulty) {
                    Difficulty.EASY -> "Relaxed AI • Simple moves, ideal for casual play"
                    Difficulty.NORMAL -> "Tactical AI • Balanced captures, safe-zone focus"
                    Difficulty.HARD -> "Master AI • Deep threat analysis & danger heatmaps"
                }
                Text(
                    text = diffDescription,
                    color = NeoLudoColors.ObsidianTextSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Section 2: Player Count
                Text(
                    text = "NUMBER OF PLAYERS",
                    color = NeoLudoColors.ObsidianTextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(2 to "2 Players (1v1)", 3 to "3 Players", 4 to "4 Players").forEach { (count, label) ->
                        val isSelected = selectedPlayerCount == count
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) NeoLudoColors.RubyRed.copy(alpha = 0.2f) else NeoLudoColors.ObsidianSurface)
                                .border(
                                    1.5.dp,
                                    if (isSelected) NeoLudoColors.RubyRed else NeoLudoColors.ObsidianBorder,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { selectedPlayerCount = count }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (count == 2) "1 vs 1" else "$count Players",
                                color = if (isSelected) NeoLudoColors.RubyRed else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Section 3: Choose Your Color
                Text(
                    text = "CHOOSE YOUR TEAM COLOR",
                    color = NeoLudoColors.ObsidianTextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    PlayerColor.entries.forEach { color ->
                        val isSelected = selectedColor == color
                        val c = NeoLudoColors.getPlayerColor(color)
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(c)
                                .border(
                                    if (isSelected) 3.dp else 1.dp,
                                    if (isSelected) Color.White else Color.Transparent,
                                    CircleShape
                                )
                                .clickable { selectedColor = color },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Start Button
                NeoLudoButton(
                    text = "Start Match",
                    accentColor = NeoLudoColors.RubyRed,
                    onClick = { onStartGame(selectedDifficulty, selectedPlayerCount, selectedColor) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun LocalMatchSetupDialog(
    onDismiss: () -> Unit,
    onStartGame: (playerCount: Int) -> Unit
) {
    var selectedPlayerCount by remember { mutableIntStateOf(4) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = NeoLudoColors.ObsidianSurfaceCard,
            border = androidx.compose.foundation.BorderStroke(1.5.dp, NeoLudoColors.AmberYellow.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Pass & Play (Local)",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "SELECT NUMBER OF PLAYERS",
                    color = NeoLudoColors.ObsidianTextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))

                listOf(
                    2 to "2 Players (Red vs Green)",
                    3 to "3 Players (Red, Green, Yellow)",
                    4 to "4 Players (Full Board 4-Way)"
                ).forEach { (count, description) ->
                    val isSelected = selectedPlayerCount == count
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) NeoLudoColors.AmberYellow.copy(alpha = 0.15f) else NeoLudoColors.ObsidianSurface)
                            .border(
                                1.5.dp,
                                if (isSelected) NeoLudoColors.AmberYellow else NeoLudoColors.ObsidianBorder,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { selectedPlayerCount = count }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = description,
                                color = if (isSelected) NeoLudoColors.AmberYellow else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            if (isSelected) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = NeoLudoColors.AmberYellow)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Spacer(modifier = Modifier.height(16.dp))

                NeoLudoButton(
                    text = "Start Game ($selectedPlayerCount Players)",
                    accentColor = NeoLudoColors.AmberYellow,
                    onClick = { onStartGame(selectedPlayerCount) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun OnlineMatchSetupDialog(
    onDismiss: () -> Unit,
    onQuickMatch: (playerCount: Int) -> Unit,
    onCreateCustomRoom: () -> Unit
) {
    var selectedPlayerCount by remember { mutableIntStateOf(4) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = NeoLudoColors.ObsidianSurfaceCard,
            border = androidx.compose.foundation.BorderStroke(1.5.dp, NeoLudoColors.CobaltBlue.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Play Online",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Quick Match Option
                Text(
                    text = "QUICK MATCH",
                    color = NeoLudoColors.ObsidianTextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(2 to "2 Players (1v1)", 4 to "4 Players").forEach { (count, label) ->
                        val isSelected = selectedPlayerCount == count
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) NeoLudoColors.CobaltBlue.copy(alpha = 0.2f) else NeoLudoColors.ObsidianSurface)
                                .border(
                                    1.5.dp,
                                    if (isSelected) NeoLudoColors.CobaltBlue else NeoLudoColors.ObsidianBorder,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { selectedPlayerCount = count }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) NeoLudoColors.CobaltBlue else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                NeoLudoButton(
                    text = "Quick Match ($selectedPlayerCount Players)",
                    accentColor = NeoLudoColors.CobaltBlue,
                    onClick = { onQuickMatch(selectedPlayerCount) },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Custom Room Option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(NeoLudoColors.ObsidianSurface)
                        .border(1.dp, NeoLudoColors.ObsidianBorder, RoundedCornerShape(12.dp))
                        .clickable { onCreateCustomRoom() }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Play with Friends",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Create or join 6-character room code",
                            color = NeoLudoColors.ObsidianTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                    Icon(Icons.Default.Group, contentDescription = null, tint = NeoLudoColors.EmeraldGreen)
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(
    profile: UserProfile,
    stats: UserStats,
    onProfileClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onRulesClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // User Profile Capsule
        Surface(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .clickable(onClick = onProfileClick)
                .border(1.dp, NeoLudoColors.ObsidianBorder, RoundedCornerShape(24.dp)),
            color = NeoLudoColors.ObsidianSurfaceCard
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(NeoLudoColors.CobaltBlue, NeoLudoColors.EmeraldGreen)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Profile",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = profile.displayName,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Win Rate: ${if (stats.totalMatches > 0) (stats.totalWins * 100 / stats.totalMatches) else 0}%",
                        color = NeoLudoColors.EmeraldGreen,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp
                    )
                }
            }
        }

        // Action Icons (Rules & Settings)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(
                onClick = onRulesClick,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(NeoLudoColors.ObsidianSurfaceCard)
                    .border(1.dp, NeoLudoColors.ObsidianBorder, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = "Rules Guide",
                    tint = NeoLudoColors.AmberYellow,
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(NeoLudoColors.ObsidianSurfaceCard)
                    .border(1.dp, NeoLudoColors.ObsidianBorder, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun GameModeCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    badgeText: String,
    onClick: () -> Unit
) {
    NeoLudoCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon Badge
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(accentColor.copy(alpha = 0.15f))
                    .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Title & Description
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = accentColor.copy(alpha = 0.2f),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, accentColor)
                    ) {
                        Text(
                            text = badgeText,
                            color = accentColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    color = NeoLudoColors.ObsidianTextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
private fun QuickStatsCard(
    stats: UserStats,
    onFriendsClick: () -> Unit
) {
    NeoLudoCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CAREER STATS",
                    color = NeoLudoColors.ObsidianTextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "View Friends",
                    color = NeoLudoColors.CobaltBlue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(onClick = onFriendsClick)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem(label = "Matches", value = stats.totalMatches.toString(), accent = Color.White)
                StatItem(label = "Wins", value = stats.totalWins.toString(), accent = NeoLudoColors.EmeraldGreen)
                StatItem(label = "Captures", value = stats.totalCaptures.toString(), accent = NeoLudoColors.RubyRed)
                StatItem(label = "Sixes", value = stats.totalSixes.toString(), accent = NeoLudoColors.AmberYellow)
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, accent: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = accent,
            fontWeight = FontWeight.Black,
            fontSize = 18.sp
        )
        Text(
            text = label,
            color = NeoLudoColors.ObsidianTextSecondary,
            fontSize = 11.sp
        )
    }
}
