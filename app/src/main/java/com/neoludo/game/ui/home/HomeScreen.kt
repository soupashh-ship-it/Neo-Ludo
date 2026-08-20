package com.neoludo.game.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
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
    onNavigateLocker: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAiSetupDialog by remember { mutableStateOf(false) }
    var showLocalSetupDialog by remember { mutableStateOf(false) }
    var showOnlineSetupDialog by remember { mutableStateOf(false) }

    var userCoins by remember { mutableIntStateOf(25400) }
    var userGems by remember { mutableIntStateOf(120) }
    var dailyClaimed by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF090D16),
                        Color(0xFF0D1424),
                        Color(0xFF070B12)
                    )
                )
            )
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(36.dp))
                // 1. AAA Top Header with Avatar Persona & Dual Economy Trackers
                AAAHomeHeader(
                    profile = profile,
                    stats = stats,
                    coins = userCoins,
                    gems = userGems,
                    onProfileClick = onNavigateProfile,
                    onLockerClick = onNavigateLocker,
                    onSettingsClick = onNavigateSettings,
                    onRulesClick = onNavigateRules
                )
            }

            item {
                // 2. Featured Cinematic Hero Card — "PLAY ONLINE"
                FeaturedHeroOnlineCard(
                    onClick = { showOnlineSetupDialog = true }
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "GAME MODES",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.2.sp
                    )
                    Text(
                        text = "2–4 PLAYERS",
                        color = NeoLudoColors.AmberYellow,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            item {
                // 3. Polished 2x2 Interactive Game Modes Grid
                GameModes2x2Grid(
                    onPlayWithFriends = onNavigateFriends,
                    onPassAndPlay = { showLocalSetupDialog = true },
                    onVsComputer = { showAiSetupDialog = true },
                    onCosmeticsLocker = onNavigateLocker
                )
            }

            item {
                // 4. Daily Quests & Fortune Chest Widget
                DailyQuestsAndRewardsCard(
                    dailyClaimed = dailyClaimed,
                    onClaimReward = {
                        if (!dailyClaimed) {
                            dailyClaimed = true
                            userCoins += 500
                        }
                    }
                )
            }

            item {
                // 5. Career KPI & Trophy Showcase Card
                CareerShowcaseCard(
                    stats = stats,
                    onFriendsClick = onNavigateFriendsList
                )
                Spacer(modifier = Modifier.height(84.dp)) // Bottom padding for floating nav bar
            }
        }

        // 6. Floating Bottom Navigation Bar
        FloatingBottomNavBar(
            currentRoute = "play",
            onTabSelected = { route ->
                when (route) {
                    "play" -> Unit
                    "locker" -> onNavigateLocker()
                    "friends" -> onNavigateFriendsList()
                    "rules" -> onNavigateRules()
                    "settings" -> onNavigateSettings()
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp, start = 20.dp, end = 20.dp)
        )

        // Setup Dialogs
        if (showAiSetupDialog) {
            VsComputerSetupDialog(
                onDismiss = { showAiSetupDialog = false },
                onStartGame = { diff, count, color ->
                    showAiSetupDialog = false
                    onStartAi(diff.name, count, color.name)
                }
            )
        }

        if (showLocalSetupDialog) {
            LocalMatchSetupDialog(
                onDismiss = { showLocalSetupDialog = false },
                onStartGame = { count ->
                    showLocalSetupDialog = false
                    onStartLocal(count)
                }
            )
        }

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
private fun AAAHomeHeader(
    profile: UserProfile,
    stats: UserStats,
    coins: Int,
    gems: Int,
    onProfileClick: () -> Unit,
    onLockerClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onRulesClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Row 1: Profile Badge (Left) & Economy Trackers (Right)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Profile Persona Capsule
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = Color(0xFF131A29),
                border = BorderStroke(1.5.dp, Color(0xFF283650)),
                modifier = Modifier
                    .clip(RoundedCornerShape(26.dp))
                    .clickable(onClick = onProfileClick)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Avatar Frame with Level Badge
                    Box(contentAlignment = Alignment.BottomEnd) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(NeoLudoColors.CobaltBlue, NeoLudoColors.EmeraldGreen)
                                    )
                                )
                                .border(1.5.dp, Color(0xFFFFD54F), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = profile.displayName.take(1).uppercase(),
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp
                            )
                        }
                        // LV Badge
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFFFD54F),
                            modifier = Modifier.size(16.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "24",
                                    color = Color.Black,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = profile.displayName,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = profile.playerTitle,
                                color = NeoLudoColors.AmberYellow,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${stats.winRate.toInt()}% Win",
                                color = NeoLudoColors.EmeraldGreen,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Economy Pills: Coins & Gems
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Coins Pill
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFF261D05),
                    border = BorderStroke(1.5.dp, Color(0xFFFFD54F).copy(alpha = 0.8f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "🪙", fontSize = 13.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "%,d".format(coins),
                            color = Color(0xFFFFE082),
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp
                        )
                    }
                }

                // Gems Pill
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFF0A223D),
                    border = BorderStroke(1.5.dp, Color(0xFF00E5FF).copy(alpha = 0.8f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "💎", fontSize = 13.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "$gems",
                            color = Color(0xFF80DEEA),
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FeaturedHeroOnlineCard(
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "hero_shimmer")
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_pulse"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(26.dp),
        color = Color.Transparent,
        border = BorderStroke(2.dp, Color(0xFF2979FF).copy(alpha = glowPulse))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF0E2A5E),
                            Color(0xFF0D1B36),
                            Color(0xFF0A1224)
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(1000f, 600f)
                    )
                )
                .padding(20.dp)
        ) {
            Column {
                // Top Tag Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF2979FF).copy(alpha = 0.25f),
                        border = BorderStroke(1.dp, Color(0xFF2979FF))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFD54F), modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "FEATURED MODE",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }

                    // Live players online badge
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF0D3823),
                        border = BorderStroke(1.dp, NeoLudoColors.EmeraldGreen.copy(alpha = 0.6f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(NeoLudoColors.EmeraldGreen)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "4,820 LIVE",
                                color = NeoLudoColors.EmeraldGreen,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Play Online",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 24.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Instant global matchmaking • 2 & 4 Player rooms",
                            color = Color(0xFF90CAF9),
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // 3D Visual Graphic Mini Preview
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                Brush.radialGradient(
                                    listOf(Color(0xFF2979FF).copy(alpha = 0.4f), Color.Transparent),
                                    radius = 100f
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(52.dp)) {
                            val w = size.width
                            val h = size.height
                            // Red Die isometric
                            drawRoundRect(
                                color = Color(0xFFE53935),
                                topLeft = Offset(4f, 4f),
                                size = androidx.compose.ui.geometry.Size(w - 8f, h - 8f),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(14f, 14f)
                            )
                            drawRoundRect(
                                color = Color(0xFFFFD54F),
                                topLeft = Offset(4f, 4f),
                                size = androidx.compose.ui.geometry.Size(w - 8f, h - 8f),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(14f, 14f),
                                style = Stroke(2f)
                            )
                            // 5 pips
                            val r = w * 0.08f
                            drawCircle(Color.White, r, Offset(w * 0.3f, h * 0.3f))
                            drawCircle(Color.White, r, Offset(w * 0.7f, h * 0.3f))
                            drawCircle(Color.White, r, Offset(w * 0.5f, h * 0.5f))
                            drawCircle(Color.White, r, Offset(w * 0.3f, h * 0.7f))
                            drawCircle(Color.White, r, Offset(w * 0.7f, h * 0.7f))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Big Glowing Play CTA Button
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF2979FF),
                    border = BorderStroke(1.5.dp, Color(0xFF82B1FF)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = Color(0xFFFFD54F),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "PLAY ONLINE NOW",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GameModes2x2Grid(
    onPlayWithFriends: () -> Unit,
    onPassAndPlay: () -> Unit,
    onVsComputer: () -> Unit,
    onCosmeticsLocker: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Row 1: Friends Room & Pass and Play
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ModeGridCard(
                title = "Play with Friends",
                subtitle = "Private Code Room",
                badge = "CUSTOM",
                icon = Icons.Default.Group,
                accentColor = NeoLudoColors.EmeraldGreen,
                gradientColors = listOf(Color(0xFF0F3824), Color(0xFF091F14)),
                onClick = onPlayWithFriends,
                modifier = Modifier.weight(1f)
            )

            ModeGridCard(
                title = "Pass & Play",
                subtitle = "2–4 Offline Players",
                badge = "LOCAL",
                icon = Icons.Default.SportsEsports,
                accentColor = NeoLudoColors.AmberYellow,
                gradientColors = listOf(Color(0xFF382E0A), Color(0xFF1F1A05)),
                onClick = onPassAndPlay,
                modifier = Modifier.weight(1f)
            )
        }

        // Row 2: Vs Computer & Cosmetics Locker
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ModeGridCard(
                title = "Vs Computer",
                subtitle = "3 Smart AI Tiers",
                badge = "SOLO BOT",
                icon = Icons.Default.SmartToy,
                accentColor = NeoLudoColors.RubyRed,
                gradientColors = listOf(Color(0xFF3D1120), Color(0xFF210912)),
                onClick = onVsComputer,
                modifier = Modifier.weight(1f)
            )

            ModeGridCard(
                title = "Cosmetics Locker",
                subtitle = "Themes, Dice & Pawns",
                badge = "VAULT",
                icon = Icons.Default.Palette,
                accentColor = Color(0xFFFF007F),
                gradientColors = listOf(Color(0xFF360D2D), Color(0xFF1F071A)),
                onClick = onCosmeticsLocker,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ModeGridCard(
    title: String,
    subtitle: String,
    badge: String,
    icon: ImageVector,
    accentColor: Color,
    gradientColors: List<Color>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = Color.Transparent,
        border = BorderStroke(1.5.dp, accentColor.copy(alpha = 0.45f)),
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .background(Brush.verticalGradient(gradientColors))
                .padding(16.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(accentColor.copy(alpha = 0.2f))
                            .border(1.dp, accentColor.copy(alpha = 0.6f), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = accentColor.copy(alpha = 0.25f),
                        border = BorderStroke(0.5.dp, accentColor)
                    ) {
                        Text(
                            text = badge,
                            color = accentColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = NeoLudoColors.ObsidianTextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )
            }
        }
    }
}

@Composable
private fun DailyQuestsAndRewardsCard(
    dailyClaimed: Boolean,
    onClaimReward: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFF111726),
        border = BorderStroke(1.dp, Color(0xFF23304A)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CardGiftcard,
                        contentDescription = null,
                        tint = NeoLudoColors.AmberYellow,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "DAILY QUESTS & FORTUNE",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                }

                Text(
                    text = "Refreshes 14h",
                    color = NeoLudoColors.ObsidianTextMuted,
                    fontSize = 10.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Quest 1
            QuestRow(
                title = "Roll a 6 in Match",
                progressText = "3 / 5",
                progressFraction = 0.6f,
                rewardText = "🪙 +250"
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Quest 2
            QuestRow(
                title = "Capture 2 Opponent Pawns",
                progressText = "1 / 2",
                progressFraction = 0.5f,
                rewardText = "💎 +10"
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Daily Free Reward Button
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (dailyClaimed) Color(0xFF1E283D) else Color(0xFF0D3823),
                border = BorderStroke(1.dp, if (dailyClaimed) Color(0xFF324263) else NeoLudoColors.EmeraldGreen),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(enabled = !dailyClaimed, onClick = onClaimReward)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (dailyClaimed) "✅ DAILY CHEST CLAIMED" else "🎁 CLAIM FREE 500 COINS",
                        color = if (dailyClaimed) NeoLudoColors.ObsidianTextSecondary else NeoLudoColors.EmeraldGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun QuestRow(
    title: String,
    progressText: String,
    progressFraction: Float,
    rewardText: String
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = NeoLudoColors.ObsidianTextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = progressText,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF1B2436)
                ) {
                    Text(
                        text = rewardText,
                        color = NeoLudoColors.AmberYellow,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        LinearProgressIndicator(
            progress = { progressFraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = NeoLudoColors.CobaltBlue,
            trackColor = Color(0xFF1C273D)
        )
    }
}

@Composable
private fun CareerShowcaseCard(
    stats: UserStats,
    onFriendsClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFF111726),
        border = BorderStroke(1.dp, Color(0xFF23304A)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.EmojiEvents,
                        contentDescription = null,
                        tint = Color(0xFFFFD54F),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "CAREER SHOWCASE",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = NeoLudoColors.EmeraldGreen.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, NeoLudoColors.EmeraldGreen.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "${stats.winRate.toInt()}% WIN RATE",
                        color = NeoLudoColors.EmeraldGreen,
                        fontWeight = FontWeight.Black,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 4 Key Stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                CareerStatColumn(label = "Matches", value = stats.totalMatches.toString(), accent = Color.White)
                CareerStatColumn(label = "Victories", value = stats.totalWins.toString(), accent = NeoLudoColors.EmeraldGreen)
                CareerStatColumn(label = "Captures", value = stats.totalCaptures.toString(), accent = NeoLudoColors.RubyRed)
                CareerStatColumn(label = "Sixes", value = stats.totalSixes.toString(), accent = NeoLudoColors.AmberYellow)
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Friends Action Row
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF182236),
                border = BorderStroke(1.dp, Color(0xFF283A5A)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onFriendsClick)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(NeoLudoColors.EmeraldGreen)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Friends Hub & Social Rooms",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "VIEW >",
                        color = NeoLudoColors.CobaltBlue,
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun CareerStatColumn(label: String, value: String, accent: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = accent,
            fontWeight = FontWeight.Black,
            fontSize = 18.sp
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = NeoLudoColors.ObsidianTextSecondary,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun FloatingBottomNavBar(
    currentRoute: String,
    onTabSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = Color(0xE6101626), // Glassmorphic dark navy
        border = BorderStroke(1.5.dp, Color(0xFF283652)),
        shadowElevation = 12.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomNavItem(
                label = "Play",
                icon = Icons.Default.SportsEsports,
                isSelected = currentRoute == "play",
                accentColor = Color(0xFFFFD54F),
                onClick = { onTabSelected("play") }
            )

            BottomNavItem(
                label = "Locker",
                icon = Icons.Default.Palette,
                isSelected = currentRoute == "locker",
                accentColor = Color(0xFFFF007F),
                onClick = { onTabSelected("locker") }
            )

            BottomNavItem(
                label = "Friends",
                icon = Icons.Default.Group,
                isSelected = currentRoute == "friends",
                accentColor = NeoLudoColors.EmeraldGreen,
                onClick = { onTabSelected("friends") }
            )

            BottomNavItem(
                label = "Rules",
                icon = Icons.AutoMirrored.Filled.MenuBook,
                isSelected = currentRoute == "rules",
                accentColor = Color(0xFF00E5FF),
                onClick = { onTabSelected("rules") }
            )

            BottomNavItem(
                label = "Settings",
                icon = Icons.Default.Settings,
                isSelected = currentRoute == "settings",
                accentColor = Color.White,
                onClick = { onTabSelected("settings") }
            )
        }
    }
}

@Composable
private fun BottomNavItem(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (isSelected) accentColor.copy(alpha = 0.18f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) accentColor else NeoLudoColors.ObsidianTextMuted,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                color = if (isSelected) Color.White else NeoLudoColors.ObsidianTextMuted,
                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium,
                fontSize = 10.sp
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
            color = Color(0xFF121827),
            border = BorderStroke(1.5.dp, NeoLudoColors.RubyRed.copy(alpha = 0.6f)),
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
                                .background(if (isSelected) color.copy(alpha = 0.2f) else Color(0xFF1B2338))
                                .border(
                                    1.5.dp,
                                    if (isSelected) color else Color(0xFF2B3A5A),
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { selectedDifficulty = diff }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) color else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                val diffDescription = when (selectedDifficulty) {
                    Difficulty.EASY -> "Relaxed AI • Casual and fun for quick games"
                    Difficulty.NORMAL -> "Tactical AI • Balanced captures and safe-zone focus"
                    Difficulty.HARD -> "Master AI • Deep danger heatmaps & high threat"
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
                    listOf(2, 3, 4).forEach { count ->
                        val isSelected = selectedPlayerCount == count
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) NeoLudoColors.CobaltBlue.copy(alpha = 0.25f) else Color(0xFF1B2338))
                                .border(
                                    1.5.dp,
                                    if (isSelected) NeoLudoColors.CobaltBlue else Color(0xFF2B3A5A),
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { selectedPlayerCount = count }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$count Players",
                                color = if (isSelected) Color.White else NeoLudoColors.ObsidianTextSecondary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Section 3: Player Color
                Text(
                    text = "YOUR TOKEN COLOR",
                    color = NeoLudoColors.ObsidianTextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val colors = listOf(
                        PlayerColor.RED to NeoLudoColors.RubyRed,
                        PlayerColor.GREEN to NeoLudoColors.EmeraldGreen,
                        PlayerColor.YELLOW to NeoLudoColors.AmberYellow,
                        PlayerColor.BLUE to NeoLudoColors.CobaltBlue
                    )

                    colors.forEach { (colorEnum, composeColor) ->
                        val isSelected = selectedColor == colorEnum
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(composeColor.copy(alpha = if (isSelected) 0.35f else 0.15f))
                                .border(
                                    if (isSelected) 2.dp else 1.dp,
                                    if (isSelected) composeColor else composeColor.copy(alpha = 0.4f),
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { selectedColor = colorEnum },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(composeColor)
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = if (colorEnum == PlayerColor.YELLOW) Color.Black else Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Start Button
                NeoLudoButton(
                    text = "START MATCH",
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
            color = Color(0xFF121827),
            border = BorderStroke(1.5.dp, NeoLudoColors.AmberYellow.copy(alpha = 0.6f)),
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

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Play together offline on one device with zero latency.",
                    color = NeoLudoColors.ObsidianTextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "NUMBER OF PLAYERS",
                    color = NeoLudoColors.ObsidianTextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(2, 3, 4).forEach { count ->
                        val isSelected = selectedPlayerCount == count
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isSelected) NeoLudoColors.AmberYellow.copy(alpha = 0.2f) else Color(0xFF1B2338))
                                .border(
                                    1.5.dp,
                                    if (isSelected) NeoLudoColors.AmberYellow else Color(0xFF2B3A5A),
                                    RoundedCornerShape(14.dp)
                                )
                                .clickable { selectedPlayerCount = count }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "$count",
                                    color = if (isSelected) NeoLudoColors.AmberYellow else Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 18.sp
                                )
                                Text(
                                    text = "Players",
                                    color = NeoLudoColors.ObsidianTextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                NeoLudoButton(
                    text = "START LOCAL MATCH",
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
            color = Color(0xFF121827),
            border = BorderStroke(1.5.dp, NeoLudoColors.CobaltBlue.copy(alpha = 0.6f)),
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

                // Option 1: Quick Matchmaking
                Text(
                    text = "QUICK MATCHMAKING",
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
                    listOf(2, 4).forEach { count ->
                        val isSelected = selectedPlayerCount == count
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) NeoLudoColors.CobaltBlue.copy(alpha = 0.25f) else Color(0xFF1B2338))
                                .border(
                                    1.5.dp,
                                    if (isSelected) NeoLudoColors.CobaltBlue else Color(0xFF2B3A5A),
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { selectedPlayerCount = count }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$count Players",
                                color = if (isSelected) Color.White else NeoLudoColors.ObsidianTextSecondary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                NeoLudoButton(
                    text = "FIND QUICK MATCH",
                    onClick = { onQuickMatch(selectedPlayerCount) },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Option 2: Friends Custom Room
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF1B2338),
                    border = BorderStroke(1.dp, Color(0xFF2B3A5A)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onCreateCustomRoom)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Create / Join Friends Room",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "Private 6-character room codes",
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
}
