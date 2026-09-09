package com.neoludo.game.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoludo.game.engine.model.PlayerColor
import com.neoludo.game.engine.model.PlayerState

/**
 * Stadium primary button — the single button system.
 * Solid fill + 14dp radius. Press = scale 0.96.
 * One accent (stadium blue). 52dp height (>= 48dp touch target).
 */
@Composable
fun NeoLudoButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = StadiumColors.Accent,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val contentColor = when (accentColor) {
        NeoLudoColors.AmberYellow, NeoLudoColors.BrutalistAmber, StadiumColors.Gold -> NeoLudoColors.BrutalistInk
        else -> Color.White
    }

    Surface(
        modifier = modifier
            .height(52.dp)
            .scale(if (pressed && enabled) 0.96f else 1f)
            .clip(MaterialTheme.shapes.medium)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            ),
        shape = MaterialTheme.shapes.medium,
        color = if (enabled) accentColor else NeoLudoColors.BrutalistDisabledFill,
        border = BorderStroke(1.dp, StadiumColors.BorderBright)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 24.dp),
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
                    color = if (enabled) contentColor else NeoLudoColors.BrutalistDisabledText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Sentence-case section label — replaces ALL-CAPS muted headers. Max 1 per 3 sections. */
@Composable
fun NeoLudoSectionLabel(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        color = StadiumColors.TextMuted
    )
}

@Composable
fun NeoLudoCard(
    modifier: Modifier = Modifier,
    borderColor: Color = StadiumColors.Border,
    backgroundColor: Color = StadiumColors.Card,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        border = BorderStroke(2.dp, borderColor)
    ) {
        Box(modifier = Modifier.padding(NeoLudoSpacing.lg)) {
            content()
        }
    }
}

@Composable
fun PlayerPlate(
    player: PlayerState,
    isActiveTurn: Boolean,
    turnProgress: Float = 1.0f,
    modifier: Modifier = Modifier,
    motionEnabled: Boolean = false
) {
    // Stadium: quiet card, player-color border only on the active turn.
    val playerColor = NeoLudoColors.getBrutalistPlayerColor(player.color)

    val borderModifier = if (isActiveTurn) {
        Modifier.border(1.dp, playerColor, MaterialTheme.shapes.medium)
    } else {
        Modifier.border(1.dp, StadiumColors.Border, MaterialTheme.shapes.medium)
    }

    Surface(
        modifier = modifier
            .then(borderModifier)
            .clip(MaterialTheme.shapes.medium),
        color = if (isActiveTurn) StadiumColors.CardElevated else StadiumColors.Card,
        shape = MaterialTheme.shapes.medium,
        border = null
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
                        color = StadiumColors.TextPrimary,
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
                            tint = StadiumColors.Gold,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "#${player.rank}",
                            color = StadiumColors.Gold,
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
                                .background(if (isScored) playerColor else StadiumColors.Border)
                        )
                    }
                }
            }
        }
    }
}
