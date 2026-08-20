package com.neoludo.game.core.designsystem

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoludo.game.engine.model.PlayerColor
import com.neoludo.game.engine.model.PlayerState

@Composable
fun NeoLudoButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = NeoLudoColors.CobaltBlue,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    val brush = if (enabled) {
        Brush.horizontalGradient(
            listOf(accentColor, accentColor.copy(alpha = 0.8f))
        )
    } else {
        Brush.horizontalGradient(
            listOf(Color.DarkGray, Color.Gray)
        )
    }

    Surface(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, if (enabled) accentColor.copy(alpha = 0.5f) else Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .background(brush)
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (leadingIcon != null) {
                    leadingIcon()
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = text,
                    color = if (enabled) Color.White else Color.LightGray,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }
}

@Composable
fun NeoLudoCard(
    modifier: Modifier = Modifier,
    borderColor: Color = NeoLudoColors.ObsidianBorder,
    backgroundColor: Color = NeoLudoColors.ObsidianSurfaceCard,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        border = BorderStroke(1.5.dp, borderColor)
    ) {
        Box(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}

@Composable
fun PlayerPlate(
    player: PlayerState,
    isActiveTurn: Boolean,
    turnProgress: Float = 1.0f,
    modifier: Modifier = Modifier
) {
    val playerColor = NeoLudoColors.getPlayerColor(player.color)
    val infiniteTransition = rememberInfiniteTransition(label = "halo")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val borderModifier = if (isActiveTurn) {
        Modifier
            .scale(pulseScale)
            .border(2.dp, playerColor, RoundedCornerShape(16.dp))
    } else {
        Modifier.border(1.dp, NeoLudoColors.ObsidianBorder, RoundedCornerShape(16.dp))
    }

    Surface(
        modifier = modifier
            .then(borderModifier)
            .clip(RoundedCornerShape(16.dp)),
        color = if (isActiveTurn) NeoLudoColors.getPlayerContainer(player.color) else NeoLudoColors.ObsidianSurfaceCard,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar with turn progress ring
            Box(contentAlignment = Alignment.Center) {
                if (isActiveTurn) {
                    CircularProgressIndicator(
                        progress = { turnProgress },
                        modifier = Modifier.size(36.dp),
                        color = playerColor,
                        strokeWidth = 2.5.dp,
                        trackColor = Color.Transparent
                    )
                }
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(playerColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = player.name.take(1).uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = player.name,
                        color = if (isActiveTurn) Color.White else NeoLudoColors.ObsidianTextPrimary,
                        fontWeight = if (isActiveTurn) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (player.rank != null) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Rank",
                            tint = NeoLudoColors.AmberYellow,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "#${player.rank}",
                            color = NeoLudoColors.AmberYellow,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Pieces in Home tracker
                Row(
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    (0..3).forEach { idx ->
                        val isScored = idx < player.piecesInHome
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (isScored) playerColor else Color.Gray.copy(alpha = 0.4f))
                        )
                    }
                }
            }
        }
    }
}
