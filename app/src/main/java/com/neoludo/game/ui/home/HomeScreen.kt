package com.neoludo.game.ui.home

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
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MenuBook
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoludo.game.core.designsystem.NeoLudoCard
import com.neoludo.game.core.designsystem.NeoLudoColors
import com.neoludo.game.core.model.UserProfile
import com.neoludo.game.core.model.UserStats

@Composable
fun HomeScreen(
    profile: UserProfile,
    stats: UserStats,
    onNavigateOnline: () -> Unit,
    onNavigateFriends: () -> Unit,
    onNavigateLocal: () -> Unit,
    onNavigateAi: () -> Unit,
    onNavigateProfile: () -> Unit,
    onNavigateSettings: () -> Unit,
    onNavigateRules: () -> Unit,
    onNavigateFriendsList: () -> Unit,
    modifier: Modifier = Modifier
) {
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
                    onClick = onNavigateOnline
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
                    onClick = onNavigateLocal
                )
            }

            item {
                GameModeCard(
                    title = "Vs Computer",
                    subtitle = "Practice against Easy, Normal, or Hard AI",
                    icon = Icons.Default.SmartToy,
                    accentColor = NeoLudoColors.RubyRed,
                    badgeText = "SOLO BOT",
                    onClick = onNavigateAi
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
                        .background(NeoLudoColors.CobaltBlue),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = profile.displayName.take(1).uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
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
                        text = "Win Rate: ${stats.winRate.toInt()}% (${stats.totalWins}W)",
                        color = NeoLudoColors.EmeraldGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Actions: Rules & Settings
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(
                onClick = onRulesClick,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(NeoLudoColors.ObsidianSurfaceCard)
                    .border(1.dp, NeoLudoColors.ObsidianBorder, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.MenuBook,
                    contentDescription = "Rules Guide",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier
                    .size(44.dp)
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
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(20.dp)),
        color = NeoLudoColors.ObsidianSurfaceCard,
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.radialGradient(
                            listOf(accentColor.copy(alpha = 0.35f), accentColor.copy(alpha = 0.12f))
                        )
                    )
                    .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(accentColor.copy(alpha = 0.18f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = badgeText,
                        color = accentColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
                Text(
                    text = subtitle,
                    color = NeoLudoColors.ObsidianTextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1
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
                    text = "LIFETIME PERFORMANCE",
                    color = NeoLudoColors.ObsidianTextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "View Friends →",
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
                StatMetric(label = "Matches", value = "${stats.totalMatches}")
                StatMetric(label = "Victories", value = "${stats.totalWins}")
                StatMetric(label = "Captures", value = "${stats.totalCaptures}")
                StatMetric(label = "Sixes", value = "${stats.totalSixes}")
            }
        }
    }
}

@Composable
private fun StatMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )
        Text(
            text = label,
            color = NeoLudoColors.ObsidianTextSecondary,
            fontSize = 11.sp
        )
    }
}
