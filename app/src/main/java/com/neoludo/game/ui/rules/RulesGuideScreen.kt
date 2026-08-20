package com.neoludo.game.ui.rules

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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

@Composable
fun RulesGuideScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
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

            // Top Bar
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
                    text = "Ludo Rules Handbook",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            RuleChapterCard(
                stepNumber = "1",
                title = "Entering the Board",
                description = "All pieces start in your corner Yard. Rolling a 6 allows you to release one piece onto your starting tile. Rolling a 6 also grants you an immediate bonus dice roll!",
                icon = Icons.Default.Casino,
                accentColor = NeoLudoColors.EmeraldGreen
            )

            Spacer(modifier = Modifier.height(14.dp))

            RuleChapterCard(
                stepNumber = "2",
                title = "8 Safe Zones & Stars",
                description = "The 4 colored starting cells and the 4 star tiles are designated Safe Zones. Opponent pieces on safe cells cannot be captured and will coexist peacefully.",
                icon = Icons.Default.Star,
                accentColor = NeoLudoColors.AmberYellow
            )

            Spacer(modifier = Modifier.height(14.dp))

            RuleChapterCard(
                stepNumber = "3",
                title = "Capturing Opponents",
                description = "Landing on an opponent's piece on an unsafe tile captures that piece, resetting it all the way back to their yard. Capturing an opponent grants you an extra bonus turn!",
                icon = Icons.Default.Security,
                accentColor = NeoLudoColors.RubyRed
            )

            Spacer(modifier = Modifier.height(14.dp))

            RuleChapterCard(
                stepNumber = "4",
                title = "Private Home Stretch",
                description = "After circling the perimeter, pieces enter their private colored home column (5 cells). Opponents cannot enter your home stretch.",
                icon = Icons.Default.Shield,
                accentColor = NeoLudoColors.CobaltBlue
            )

            Spacer(modifier = Modifier.height(14.dp))

            RuleChapterCard(
                stepNumber = "5",
                title = "Reaching Home & Victory",
                description = "An exact dice roll is required to score a piece into the center Home triangle. Scoring a piece grants a bonus roll. The first player to bring all 4 pieces home wins 1st place!",
                icon = Icons.Default.EmojiEvents,
                accentColor = NeoLudoColors.AmberYellow
            )

            Spacer(modifier = Modifier.height(36.dp))
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
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accentColor.copy(alpha = 0.2f))
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

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "RULE #$stepNumber: ",
                        color = accentColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = description,
                    color = NeoLudoColors.ObsidianTextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}
