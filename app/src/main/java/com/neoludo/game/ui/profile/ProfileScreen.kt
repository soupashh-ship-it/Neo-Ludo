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
import com.neoludo.game.core.designsystem.NeoLudoButton
import com.neoludo.game.core.designsystem.NeoLudoCard
import com.neoludo.game.core.designsystem.NeoLudoColors
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
        NeoLudoColors.CobaltBlue, NeoLudoColors.EmeraldGreen, NeoLudoColors.RubyRed, NeoLudoColors.AmberYellow,
        Color(0xFF9C27B0), Color(0xFFFF5722), Color(0xFF00BCD4), Color(0xFFE91E63),
        Color(0xFF3F51B5), Color(0xFF4CAF50), Color(0xFFFF9800), Color(0xFF795548),
        Color(0xFF607D8B), Color(0xFF673AB7), Color(0xFF8BC34A), Color(0xFF009688)
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NeoLudoColors.ObsidianBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
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
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(NeoLudoColors.ObsidianSurfaceCard)
                        .border(1.dp, NeoLudoColors.ObsidianBorder, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = "PLAYER CAREER PROFILE",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "Manage identity, badges & career stats",
                        color = NeoLudoColors.ObsidianTextSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Identity Card: Avatar + Name + Title
            NeoLudoCard(modifier = Modifier.fillMaxWidth()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(avatarColors.getOrElse(selectedAvatarId - 1) { NeoLudoColors.CobaltBlue })
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

                    // Player Title Badge
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = NeoLudoColors.AmberYellow.copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, NeoLudoColors.AmberYellow)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.MilitaryTech,
                                contentDescription = null,
                                tint = NeoLudoColors.AmberYellow,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = selectedTitle,
                                color = NeoLudoColors.AmberYellow,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { if (it.length <= 16) displayName = it },
                        label = { Text("Display Name", color = NeoLudoColors.ObsidianTextSecondary) },
                        textStyle = TextStyle(color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeoLudoColors.CobaltBlue,
                            unfocusedBorderColor = NeoLudoColors.ObsidianBorder,
                            focusedContainerColor = NeoLudoColors.ObsidianSurface,
                            unfocusedContainerColor = NeoLudoColors.ObsidianSurface
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Select Player Title Chips
                    Text(
                        text = "SELECT PLAYER TITLE",
                        color = NeoLudoColors.ObsidianTextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
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
                                    .background(if (isSelected) NeoLudoColors.AmberYellow.copy(alpha = 0.2f) else NeoLudoColors.ObsidianSurface)
                                    .border(
                                        1.2.dp,
                                        if (isSelected) NeoLudoColors.AmberYellow else NeoLudoColors.ObsidianBorder,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable { selectedTitle = title }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = title,
                                    color = if (isSelected) NeoLudoColors.AmberYellow else Color.White,
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
                                    .background(if (isSelected) NeoLudoColors.AmberYellow.copy(alpha = 0.2f) else NeoLudoColors.ObsidianSurface)
                                    .border(
                                        1.2.dp,
                                        if (isSelected) NeoLudoColors.AmberYellow else NeoLudoColors.ObsidianBorder,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable { selectedTitle = title }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = title,
                                    color = if (isSelected) NeoLudoColors.AmberYellow else Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 16 Curated Avatar Themes
                    Text(
                        text = "SELECT AVATAR THEME",
                        color = NeoLudoColors.ObsidianTextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
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
            Text(
                text = "LIFETIME CAREER STATISTICS",
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
                CareerKpiCard(
                    title = "Matches",
                    value = "${stats.totalMatches}",
                    color = NeoLudoColors.CobaltBlue,
                    modifier = Modifier.weight(1f)
                )
                CareerKpiCard(
                    title = "Victories",
                    value = "${stats.totalWins}",
                    color = NeoLudoColors.EmeraldGreen,
                    modifier = Modifier.weight(1f)
                )
                CareerKpiCard(
                    title = "Win Rate",
                    value = "${stats.winRate.toInt()}%",
                    color = NeoLudoColors.AmberYellow,
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
            Text(
                text = "RECENT MATCH HISTORY",
                color = NeoLudoColors.ObsidianTextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
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
                            tint = NeoLudoColors.ObsidianTextMuted,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "No matches recorded yet",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Complete matches to build your career log!",
                            color = NeoLudoColors.ObsidianTextSecondary,
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
                accentColor = NeoLudoColors.CobaltBlue,
                onClick = {
                    onSaveProfile(
                        profile.copy(
                            displayName = displayName,
                            avatarId = selectedAvatarId,
                            playerTitle = selectedTitle
                        )
                    )
                    onBack()
                },
                modifier = Modifier.fillMaxWidth()
            )

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
    val accentColor = if (isWin) NeoLudoColors.EmeraldGreen else NeoLudoColors.RubyRed
    val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    val dateStr = runCatching { sdf.format(Date(match.timestamp)) }.getOrDefault("Recent")

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, NeoLudoColors.ObsidianBorder, RoundedCornerShape(14.dp)),
        color = NeoLudoColors.ObsidianSurfaceCard,
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
                    color = NeoLudoColors.ObsidianTextMuted,
                    fontSize = 11.sp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "Captures",
                        tint = NeoLudoColors.RubyRed,
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
                        tint = NeoLudoColors.AmberYellow,
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
        Text(text = label, color = NeoLudoColors.ObsidianTextSecondary, fontSize = 14.sp)
        Text(text = value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}
