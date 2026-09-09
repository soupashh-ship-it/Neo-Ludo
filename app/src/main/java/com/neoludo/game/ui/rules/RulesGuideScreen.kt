package com.neoludo.game.ui.rules

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoludo.game.core.designsystem.NeoLudoCard
import com.neoludo.game.core.designsystem.NeoLudoColors
import com.neoludo.game.core.designsystem.NeoLudoSpacing
import com.neoludo.game.core.designsystem.ScreenHeader
import com.neoludo.game.core.designsystem.StadiumBackground
import com.neoludo.game.core.designsystem.StadiumColors
import com.neoludo.game.core.designsystem.StadiumDimens
import com.neoludo.game.core.designsystem.isTablet

@Composable
fun RulesGuideScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tablet = isTablet()
    val cap = if (tablet) StadiumDimens.ContentMaxTablet else StadiumDimens.ContentMaxPhone
    StadiumBackground(modifier = modifier) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = cap)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = if (tablet) 28.dp else 20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(NeoLudoSpacing.md))

                ScreenHeader(title = "Rules handbook", onBack = onBack)

                Spacer(modifier = Modifier.height(NeoLudoSpacing.xl))

                // Chapter accents are semantic (safe = green, capture = red…), kept.
                RuleChapterCard(
                    stepNumber = "1",
                    title = "Entering the Board",
                    description = "All pieces start in your corner Yard. Rolling a 6 allows you to release one piece onto your starting tile. Rolling a 6 also grants you an immediate bonus dice roll!",
                    icon = Icons.Default.Casino,
                    accentColor = NeoLudoColors.EmeraldGreen
                )

                Spacer(modifier = Modifier.height(NeoLudoSpacing.md))

                RuleChapterCard(
                    stepNumber = "2",
                    title = "8 Safe Zones & Stars",
                    description = "The 4 colored starting cells and the 4 star tiles are designated Safe Zones. Opponent pieces on safe cells cannot be captured and will coexist peacefully.",
                    icon = Icons.Default.Star,
                    accentColor = StadiumColors.Gold
                )

                Spacer(modifier = Modifier.height(NeoLudoSpacing.md))

                RuleChapterCard(
                    stepNumber = "3",
                    title = "Capturing Opponents",
                    description = "Landing on an opponent's piece on an unsafe tile captures that piece, resetting it all the way back to their yard. Capturing an opponent grants you an extra bonus turn!",
                    icon = Icons.Default.Security,
                    accentColor = StadiumColors.Danger
                )

                Spacer(modifier = Modifier.height(NeoLudoSpacing.md))

                RuleChapterCard(
                    stepNumber = "4",
                    title = "Private Home Stretch",
                    description = "After circling the perimeter, pieces enter their private colored home column (5 cells). Opponents cannot enter your home stretch.",
                    icon = Icons.Default.Shield,
                    accentColor = StadiumColors.AccentBright
                )

                Spacer(modifier = Modifier.height(NeoLudoSpacing.md))

                RuleChapterCard(
                    stepNumber = "5",
                    title = "Reaching Home & Victory",
                    description = "An exact dice roll is required to score a piece into the center Home triangle. Scoring a piece grants a bonus roll. The first player to bring all 4 pieces home wins 1st place!",
                    icon = Icons.Default.EmojiEvents,
                    accentColor = StadiumColors.Gold
                )

                Spacer(modifier = Modifier.height(NeoLudoSpacing.xxxl))
            }
        }
    }
}

@Composable
private fun RuleChapterCard(
    stepNumber: String,
    title: String,
    description: String,
    icon: ImageVector,
    accentColor: Color
) {
    NeoLudoCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = accentColor.copy(alpha = 0.35f)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accentColor.copy(alpha = 0.15f))
                    .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(NeoLudoSpacing.lg))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Rule $stepNumber · $title",
                    color = StadiumColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = description,
                    color = StadiumColors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
