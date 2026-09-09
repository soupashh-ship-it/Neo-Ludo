package com.neoludo.game.ui.profile

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
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoludo.game.core.designsystem.AdaptiveScroll
import com.neoludo.game.core.designsystem.NeoLudoButton
import com.neoludo.game.core.designsystem.NeoLudoCard
import com.neoludo.game.core.designsystem.NeoLudoColors
import com.neoludo.game.core.designsystem.NeoLudoSectionLabel
import com.neoludo.game.core.designsystem.NeoLudoSpacing
import com.neoludo.game.core.designsystem.ScreenHeader
import com.neoludo.game.core.designsystem.StadiumBackground
import com.neoludo.game.core.designsystem.StadiumColors
import com.neoludo.game.core.model.MatchRecord
import com.neoludo.game.core.model.UserProfile
import com.neoludo.game.core.model.UserStats
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProfileScreen(
    profile: UserProfile,
    stats: UserStats,
    onSaveProfile: (UserProfile) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var displayName by remember { mutableStateOf(profile.displayName) }
    var selectedAvatarId by remember { mutableIntStateOf(profile.avatarId) }
    var selectedTitle by remember { mutableStateOf(profile.playerTitle) }

    val titles = listOf(
        "Grandmaster",
        "Dice Sorcerer",
        "Board Conqueror",
        "Pawn Crusher",
        "Casual Roller",
        "Speed Demon"
    )

    val avatarColors = listOf(
        StadiumColors.Accent, StadiumColors.Success, StadiumColors.Danger, StadiumColors.Gold,
        Color(0xFF9C27B0), Color(0xFFFF5722), Color(0xFF00BCD4), Color(0xFFE91E63),
        Color(0xFF3F51B5), Color(0xFF4CAF50), Color(0xFFFF9800), Color(0xFF795548),
        Color(0xFF607D8B), Color(0xFF673AB7), Color(0xFF8BC34A), Color(0xFF009688)
    )

    StadiumBackground(modifier = modifier) {
        AdaptiveScroll {
            Spacer(modifier = Modifier.height(NeoLudoSpacing.md))

            ScreenHeader(
                title = "Career profile",
                subtitle = "Manage identity, badges & career stats",
                onBack = onBack
            )

            Spacer(modifier = Modifier.height(NeoLudoSpacing.xl))

            // Identity Card: Avatar + Name + Title
            NeoLudoCard(modifier = Modifier.fillMaxWidth()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(avatarColors.getOrElse(selectedAvatarId - 1) { StadiumColors.Accent })
                            .border(3.dp, Color.White.copy(alpha = 0.8f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = displayName.take(1).uppercase(),
                            color = Color.White,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Black
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Player Title Badge (prestige = gold, reward-only color)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = StadiumColors.Gold.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, StadiumColors.Gold)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.MilitaryTech,
                                contentDescription = null,
                                tint = StadiumColors.Gold,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = selectedTitle,
                                color = StadiumColors.Gold,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { if (it.length <= 16) displayName = it },
                        label = { Text("Display Name", color = StadiumColors.TextSecondary) },
                        textStyle = TextStyle(color = StadiumColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = StadiumColors.Accent,
                            unfocusedBorderColor = StadiumColors.Border,
                            focusedContainerColor = StadiumColors.CardElevated,
                            unfocusedContainerColor = StadiumColors.CardElevated
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Select Player Title Chips
                    NeoLudoSectionLabel(text = "Player title")
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        titles.take(3).forEach { title ->
                            val isSelected = title == selectedTitle
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) StadiumColors.Gold.copy(alpha = 0.15f) else StadiumColors.CardElevated)
                                    .border(
                                        1.2.dp,
                                        if (isSelected) StadiumColors.Gold else StadiumColors.Border,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable { selectedTitle = title }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = title,
                                    color = if (isSelected) StadiumColors.Gold else StadiumColors.TextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        titles.drop(3).forEach { title ->
                            val isSelected = title == selectedTitle
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) StadiumColors.Gold.copy(alpha = 0.15f) else StadiumColors.CardElevated)
                                    .border(
                                        1.2.dp,
                                        if (isSelected) StadiumColors.Gold else StadiumColors.Border,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable { selectedTitle = title }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = title,
                                    color = if (isSelected) StadiumColors.Gold else StadiumColors.TextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 16 Curated Avatar Themes
                    NeoLudoSectionLabel(text = "Avatar theme")
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        (1..8).forEach { id ->
                            val isSelected = selectedAvatarId == id
                            val c = avatarColors[id - 1]
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(c)
                                    .clickable { selectedAvatarId = id }
                                    .border(
                                        2.dp,
                                        if (isSelected) Color.White else Color.Transparent,
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        (9..16).forEach { id ->
                            val isSelected = selectedAvatarId == id
                            val c = avatarColors[id - 1]
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(c)
                                    .clickable { selectedAvatarId = id }
                                    .border(
                                        2.dp,
                                        if (isSelected) Color.White else Color.Transparent,
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Career Performance Breakdown KPI Grid
            NeoLudoSectionLabel(text = "Lifetime career statistics")
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CareerKpiCard(
                    title = "Matches",
                    value = "${stats.totalMatches}",
                    color = StadiumColors.Accent,
                    modifier = Modifier.weight(1f)
                )
                CareerKpiCard(
                    title = "Victories",
                    value = "${stats.totalWins}",
                    color = StadiumColors.Success,
                    modifier = Modifier.weight(1f)
                )
                CareerKpiCard(
                    title = "Win Rate",
                    value = "${stats.winRate.toInt()}%",
                    color = StadiumColors.Gold,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            NeoLudoCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProfileStatRow("Enemy Tokens Captured", "${stats.totalCaptures}")
                    ProfileStatRow("Total Sixes Rolled", "${stats.totalSixes}")
                    ProfileStatRow("Tokens Reached Home", "${stats.totalPiecesHome}")
                    ProfileStatRow("Solo Bot Wins", "${stats.aiWins}")
                    ProfileStatRow("Online Multiplayer Wins", "${stats.onlineWins}")
                    ProfileStatRow("Local Offline Wins", "${stats.localWins}")
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Match History List
            NeoLudoSectionLabel(text = "Recent match history")
            Spacer(modifier = Modifier.height(10.dp))

            if (stats.matchHistory.isEmpty()) {
                NeoLudoCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = StadiumColors.TextMuted,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "No matches recorded yet",
                            color = StadiumColors.TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Complete matches to build your career log!",
                            color = StadiumColors.TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                stats.matchHistory.take(8).forEach { match ->
                    MatchHistoryCard(match = match)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            NeoLudoButton(
                text = "Save Changes",
                enabled = displayName.trim().isNotBlank(),
                onClick = {
                    onSaveProfile(
                        profile.copy(
                            displayName = displayName.trim().take(16),
                            avatarId = selectedAvatarId,
                            playerTitle = selectedTitle
                        )
                    )
                    onBack()
                },
                modifier = Modifier.fillMaxWidth()
            )
            if (displayName.trim().isBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Display name can't be empty.",
                    color = StadiumColors.Danger,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(36.dp))
        }
    }
}

@Composable
private fun CareerKpiCard(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .border(1.2.dp, color.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = title,
                color = color,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun MatchHistoryCard(match: MatchRecord) {
    val isWin = match.isWin
    val accentColor = if (isWin) StadiumColors.Success else StadiumColors.Danger
    val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    val dateStr = runCatching { sdf.format(Date(match.timestamp)) }.getOrDefault("Recent")

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, StadiumColors.Border, RoundedCornerShape(14.dp)),
        color = StadiumColors.Card,
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = accentColor.copy(alpha = 0.2f),
                border = BorderStroke(1.dp, accentColor)
            ) {
                Text(
                    text = if (isWin) "VICTORY" else "DEFEAT",
                    color = accentColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${match.mode} Match",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Text(
                    text = dateStr,
                    color = StadiumColors.TextMuted,
                    fontSize = 11.sp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "Captures",
                        tint = StadiumColors.Danger,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "${match.captures}",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Casino,
                        contentDescription = "Sixes",
                        tint = StadiumColors.Gold,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "${match.sixes}",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileStatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = StadiumColors.TextSecondary, fontSize = 14.sp)
        Text(text = value, color = StadiumColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}
