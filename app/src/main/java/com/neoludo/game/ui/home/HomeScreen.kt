package com.neoludo.game.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.neoludo.game.core.designsystem.CappedDialog
import com.neoludo.game.core.designsystem.NeoLudoButton
import com.neoludo.game.core.designsystem.NeoLudoColors
import com.neoludo.game.core.designsystem.NeoLudoSectionLabel
import com.neoludo.game.core.designsystem.NeoLudoSpacing
import com.neoludo.game.core.designsystem.StadiumBackground
import com.neoludo.game.core.designsystem.StadiumColors
import com.neoludo.game.core.designsystem.StadiumDimens
import com.neoludo.game.core.designsystem.isTablet
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
    val tablet = isTablet()
    val cap = if (tablet) StadiumDimens.ContentMaxTablet else StadiumDimens.ContentMaxPhone

    StadiumBackground(modifier = modifier) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = cap)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = if (tablet) 28.dp else 20.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(NeoLudoSpacing.md))
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
                    Spacer(modifier = Modifier.height(NeoLudoSpacing.lg))
                    PlayWithFriendsCard(
                        onCreateRoom = onNavigateFriends,
                        onJoinRoom = onNavigateJoinRoom
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(NeoLudoSpacing.xxl))
                    NeoLudoSectionLabel(text = "Game modes")
                    Spacer(modifier = Modifier.height(NeoLudoSpacing.md))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(NeoLudoSpacing.md)
                    ) {
                        GameModeTile(
                            title = "Vs Computer",
                            subtitle = "Play with AI bots",
                            icon = Icons.Default.SmartToy,
                            accentColor = StadiumColors.Accent,
                            onClick = { showAiSetupDialog = true },
                            modifier = Modifier.weight(1f)
                        )

                        GameModeTile(
                            title = "Pass & Play",
                            subtitle = "Local multiplayer",
                            icon = Icons.Default.SportsEsports,
                            accentColor = StadiumColors.Accent,
                            onClick = { showLocalSetupDialog = true },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(NeoLudoSpacing.xxl))
                    // Daily Reward Card — gold is reward-only, never a CTA elsewhere.
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = StadiumColors.Card,
                        border = BorderStroke(1.dp, StadiumColors.Border)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(NeoLudoSpacing.lg),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = StadiumColors.Gold.copy(alpha = 0.15f),
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.CardGiftcard,
                                            contentDescription = null,
                                            tint = StadiumColors.Gold,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(NeoLudoSpacing.md))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Daily Login Reward",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = StadiumColors.TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (dailyClaimed) "Claimed (+200 Coins)" else "+200 Coins + 10 Gems",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (dailyClaimed) StadiumColors.Success else StadiumColors.Gold
                                    )
                                }
                            }

                            if (!dailyClaimed) {
                                Surface(
                                    onClick = {
                                        dailyClaimed = true
                                        onClaimDailyBonus()
                                    },
                                    shape = MaterialTheme.shapes.small,
                                    color = StadiumColors.Gold
                                ) {
                                    Text(
                                        text = "Claim",
                                        color = Color.Black,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Claimed",
                                    tint = StadiumColors.Success,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(NeoLudoSpacing.xl))
                    CareerStatsSummaryCard(
                        stats = stats,
                        onNavigateStats = onNavigateProfile
                    )
                    Spacer(modifier = Modifier.height(NeoLudoSpacing.xxxl))
                }
            }
        }
    }

    // AI Setup Dialog — same state machine, capped width for tablets.
    if (showAiSetupDialog) {
        var selectedDifficulty by remember { mutableStateOf(Difficulty.NORMAL) }
        var selectedCount by remember { mutableIntStateOf(4) }
        var selectedColor by remember { mutableStateOf(PlayerColor.RED) }

        CappedDialog(onDismissRequest = { showAiSetupDialog = false }) {
            Column {
                Text(
                    text = "Vs Computer Match",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = StadiumColors.TextPrimary
                )
                Spacer(modifier = Modifier.height(NeoLudoSpacing.lg))

                NeoLudoSectionLabel(text = "Difficulty")
                Spacer(modifier = Modifier.height(NeoLudoSpacing.sm))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(Difficulty.EASY, Difficulty.NORMAL, Difficulty.HARD).forEach { diff ->
                        val isSelected = selectedDifficulty == diff
                        Surface(
                            onClick = { selectedDifficulty = diff },
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.small,
                            color = if (isSelected) StadiumColors.Accent else StadiumColors.Card,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) StadiumColors.AccentBright else StadiumColors.Border
                            )
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = diff.name.take(4),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = StadiumColors.TextPrimary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(NeoLudoSpacing.lg))
                NeoLudoSectionLabel(text = "Players")
                Spacer(modifier = Modifier.height(NeoLudoSpacing.sm))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(NeoLudoSpacing.sm)
                ) {
                    listOf(2, 3, 4).forEach { count ->
                        val isSelected = selectedCount == count
                        Surface(
                            onClick = { selectedCount = count },
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.small,
                            color = if (isSelected) StadiumColors.Accent else StadiumColors.Card,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) StadiumColors.AccentBright else StadiumColors.Border
                            )
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$count Players",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = StadiumColors.TextPrimary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(NeoLudoSpacing.xl))
                NeoLudoButton(
                    text = "Start AI Game",
                    accentColor = NeoLudoColors.BrutalistBlue,
                    onClick = {
                        showAiSetupDialog = false
                        onStartAi(selectedDifficulty.name, selectedCount, selectedColor.name)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    // Local Pass & Play Setup Dialog — same state machine, capped width.
    if (showLocalSetupDialog) {
        var selectedCount by remember { mutableIntStateOf(4) }

        CappedDialog(onDismissRequest = { showLocalSetupDialog = false }) {
            Column {
                Text(
                    text = "Pass & Play Setup",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = StadiumColors.TextPrimary
                )
                Spacer(modifier = Modifier.height(NeoLudoSpacing.sm))
                Text(
                    text = "Play together on this single phone offline.",
                    style = MaterialTheme.typography.bodySmall,
                    color = StadiumColors.TextSecondary
                )

                Spacer(modifier = Modifier.height(NeoLudoSpacing.lg))
                NeoLudoSectionLabel(text = "Players")
                Spacer(modifier = Modifier.height(NeoLudoSpacing.sm))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(NeoLudoSpacing.sm)
                ) {
                    listOf(2, 3, 4).forEach { count ->
                        val isSelected = selectedCount == count
                        Surface(
                            onClick = { selectedCount = count },
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.small,
                            color = if (isSelected) StadiumColors.Accent else StadiumColors.Card,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) StadiumColors.AccentBright else StadiumColors.Border
                            )
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$count Players",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = StadiumColors.TextPrimary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(NeoLudoSpacing.xl))
                NeoLudoButton(
                    text = "Start Local Game",
                    accentColor = NeoLudoColors.BrutalistBlue,
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
