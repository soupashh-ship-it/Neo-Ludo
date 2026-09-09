package com.neoludo.game.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.neoludo.game.core.designsystem.NeoLudoSpacing
import com.neoludo.game.core.designsystem.StadiumColors
import com.neoludo.game.core.designsystem.TicketCard
import com.neoludo.game.core.model.UserProfile
import com.neoludo.game.core.model.UserStats

/** Stadium header — same destinations, capped avatar, 48dp icon targets. */
@Composable
fun HomeHeader(
    profile: UserProfile,
    onNavigateProfile: () -> Unit,
    onNavigateLocker: () -> Unit,
    onNavigateFriends: () -> Unit,
    onNavigateSettings: () -> Unit,
    onNavigateRules: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = NeoLudoSpacing.md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Profile Info
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f, fill = false)
                .clip(MaterialTheme.shapes.small)
                .clickable(onClick = onNavigateProfile)
                .padding(NeoLudoSpacing.xs)
        ) {
            Surface(
                shape = CircleShape,
                color = StadiumColors.Accent,
                border = BorderStroke(2.dp, StadiumColors.AccentBright),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = profile.displayName.take(1).uppercase(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.width(NeoLudoSpacing.md))

            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = profile.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = StadiumColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(NeoLudoSpacing.sm)
                ) {
                    // Coins Pill
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(StadiumColors.Gold.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = "Coins",
                            tint = StadiumColors.Gold,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${profile.coins}",
                            color = StadiumColors.Gold,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }

                    // Gems Pill
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(StadiumColors.Accent.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = "Gems",
                            tint = StadiumColors.AccentBright,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${profile.gems}",
                            color = StadiumColors.AccentBright,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }

        // Action Icons — wrap on narrow screens instead of overflowing.
        Row(
            horizontalArrangement = Arrangement.spacedBy(NeoLudoSpacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HeaderIconButton(
                icon = Icons.Default.Palette,
                contentDescription = "Locker",
                onClick = onNavigateLocker
            )
            HeaderIconButton(
                icon = Icons.Default.Group,
                contentDescription = "Friends",
                onClick = onNavigateFriends
            )
            HeaderIconButton(
                icon = Icons.AutoMirrored.Filled.MenuBook,
                contentDescription = "Rules",
                onClick = onNavigateRules
            )
            HeaderIconButton(
                icon = Icons.Default.Settings,
                contentDescription = "Settings",
                onClick = onNavigateSettings
            )
        }
    }
}

@Composable
fun HeaderIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(48.dp),
        shape = CircleShape,
        color = StadiumColors.Card,
        border = BorderStroke(1.dp, StadiumColors.Border)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = StadiumColors.TextPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** Private-room hero — ticket card with a single accent CTA. */
@Composable
fun PlayWithFriendsCard(
    onCreateRoom: () -> Unit,
    onJoinRoom: () -> Unit,
    modifier: Modifier = Modifier
) {
    TicketCard(modifier = modifier, accent = StadiumColors.Accent) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Play with friends",
                        style = MaterialTheme.typography.labelMedium,
                        color = StadiumColors.AccentBright
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Private Room Match",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = StadiumColors.TextPrimary
                    )
                    Text(
                        text = "Play together in real-time with room code",
                        style = MaterialTheme.typography.bodySmall,
                        color = StadiumColors.TextSecondary
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = StadiumColors.AccentContainer,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Group,
                            contentDescription = null,
                            tint = StadiumColors.AccentBright,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(NeoLudoSpacing.xl))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(NeoLudoSpacing.md)
            ) {
                Surface(
                    onClick = onCreateRoom,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = StadiumColors.Accent
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Create Room",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Surface(
                    onClick = onJoinRoom,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = StadiumColors.CardElevated,
                    border = BorderStroke(1.dp, StadiumColors.BorderBright)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = StadiumColors.TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Join Room",
                            color = StadiumColors.TextPrimary,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/** Single-accent mode tile — icon chip carries the stadium blue. */
@Composable
fun GameModeTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // accentColor kept in signature for call-site compatibility; the stadium
    // system uses one accent — the chip below ignores per-tile rainbows.
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = StadiumColors.Card,
        border = BorderStroke(1.dp, StadiumColors.Border)
    ) {
        Column(
            modifier = Modifier.padding(NeoLudoSpacing.lg),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = StadiumColors.AccentContainer,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = StadiumColors.AccentBright,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(NeoLudoSpacing.lg))

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = StadiumColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = StadiumColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun CareerStatsSummaryCard(
    stats: UserStats,
    onNavigateStats: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onNavigateStats,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = StadiumColors.Card,
        border = BorderStroke(1.dp, StadiumColors.Border)
    ) {
        Column(modifier = Modifier.padding(NeoLudoSpacing.lg)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.EmojiEvents,
                        contentDescription = null,
                        tint = StadiumColors.Gold,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(NeoLudoSpacing.sm))
                    Text(
                        text = "Career Stats",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = StadiumColors.TextPrimary
                    )
                }

                Text(
                    text = "Win Rate: ${stats.winRate.toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = StadiumColors.Success
                )
            }

            Spacer(modifier = Modifier.height(NeoLudoSpacing.md))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem(label = "Matches", value = "${stats.totalMatches}")
                StatItem(label = "Wins", value = "${stats.totalWins}")
                StatItem(label = "Captures", value = "${stats.totalCaptures}")
                StatItem(label = "Sixes", value = "${stats.totalSixes}")
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            color = StadiumColors.TextPrimary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = StadiumColors.TextMuted
        )
    }
}
