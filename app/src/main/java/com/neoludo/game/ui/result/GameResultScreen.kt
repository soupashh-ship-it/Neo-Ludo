package com.neoludo.game.ui.result

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoludo.game.core.designsystem.NeoLudoButton
import com.neoludo.game.core.designsystem.NeoLudoCard
import com.neoludo.game.core.designsystem.NeoLudoColors
import com.neoludo.game.engine.model.PlayerColor
import kotlin.math.sin
import kotlin.random.Random

private data class ConfettiParticle(
    val xRatio: Float,
    val speed: Float,
    val size: Float,
    val color: Color,
    val phaseOffset: Float
)

@Composable
fun GameResultScreen(
    winnerColor: String,
    captures: Int,
    sixes: Int,
    onPlayAgain: () -> Unit,
    onMainMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    val parsedColor = runCatching { PlayerColor.valueOf(winnerColor) }.getOrDefault(PlayerColor.RED)
    val accentColor = NeoLudoColors.getPlayerColor(parsedColor)

    // Confetti particles definition
    val confettiList = remember {
        val colors = listOf(
            NeoLudoColors.RubyRed,
            NeoLudoColors.EmeraldGreen,
            NeoLudoColors.AmberYellow,
            NeoLudoColors.CobaltBlue,
            Color(0xFFFF007F),
            Color(0xFF00F0FF),
            Color(0xFFFFD700)
        )
        List(50) {
            ConfettiParticle(
                xRatio = Random.nextFloat(),
                speed = Random.nextFloat() * 0.6f + 0.4f,
                size = Random.nextFloat() * 8f + 5f,
                color = colors.random(),
                phaseOffset = Random.nextFloat() * 6.28f
            )
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "confetti_anim")
    val fallProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "fall"
    )

    // Podium entry animation
    val podiumHeightAnim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        podiumHeightAnim.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NeoLudoColors.ObsidianBackground)
    ) {
        // 1. Particle Confetti Falling Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            confettiList.forEach { p ->
                val currentY = ((fallProgress * p.speed * h * 1.5f) + (p.phaseOffset * 50f)) % (h + 30f) - 20f
                val swayX = sin(fallProgress * 12f + p.phaseOffset) * 20f
                val currentX = (p.xRatio * w) + swayX

                drawRoundRect(
                    color = p.color.copy(alpha = 0.85f),
                    topLeft = Offset(currentX, currentY),
                    size = Size(p.size, p.size * 0.6f),
                    cornerRadius = CornerRadius(2f, 2f)
                )
            }
        }

        // 2. Victory Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(44.dp))

            // Header Banner
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = accentColor.copy(alpha = 0.2f),
                border = BorderStroke(1.2.dp, accentColor)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Stars,
                        contentDescription = null,
                        tint = NeoLudoColors.AmberYellow,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "MATCH CONCLUDED",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp,
                        letterSpacing = 1.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "VICTORY!",
                color = Color.White,
                fontSize = 34.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )

            Text(
                text = "${parsedColor.name.lowercase().replaceFirstChar { it.uppercase() }} Player Champion",
                color = accentColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 3. Animated 3-Tier Victory Podium
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.Bottom
            ) {
                // 2nd Place (Silver - Left)
                PodiumColumn(
                    place = "2nd",
                    targetHeight = 85.dp * podiumHeightAnim.value,
                    color = Color(0xFF94A3B8),
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))

                // 1st Place (Gold / Winner Color - Center)
                PodiumColumn(
                    place = "1st",
                    targetHeight = 125.dp * podiumHeightAnim.value,
                    color = accentColor,
                    isWinner = true,
                    modifier = Modifier.weight(1.2f)
                )
                Spacer(modifier = Modifier.width(8.dp))

                // 3rd Place (Bronze - Right)
                PodiumColumn(
                    place = "3rd",
                    targetHeight = 65.dp * podiumHeightAnim.value,
                    color = Color(0xFFD97706),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 4. Match Performance Highlights Card
            NeoLudoCard(modifier = Modifier.fillMaxWidth()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "MATCH PERFORMANCE HIGHLIGHTS",
                        color = NeoLudoColors.ObsidianTextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        // Captures
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = NeoLudoColors.RubyRed,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "$captures",
                                    color = NeoLudoColors.RubyRed,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                            Text(
                                text = "Captures",
                                color = NeoLudoColors.ObsidianTextSecondary,
                                fontSize = 12.sp
                            )
                        }

                        // Sixes Rolled
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Casino,
                                    contentDescription = null,
                                    tint = NeoLudoColors.AmberYellow,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "$sixes",
                                    color = NeoLudoColors.AmberYellow,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                            Text(
                                text = "Sixes Rolled",
                                color = NeoLudoColors.ObsidianTextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // 5. Action Buttons
            NeoLudoButton(
                text = "Play Again",
                accentColor = accentColor,
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = Color.White)
                },
                onClick = onPlayAgain,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onMainMenu)
                    .border(1.dp, NeoLudoColors.ObsidianBorder, RoundedCornerShape(16.dp)),
                color = NeoLudoColors.ObsidianSurfaceCard
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "Main Menu",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(36.dp))
        }
    }
}

@Composable
private fun PodiumColumn(
    place: String,
    targetHeight: androidx.compose.ui.unit.Dp,
    color: Color,
    isWinner: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom
    ) {
        if (isWinner) {
            Icon(
                imageVector = Icons.Default.EmojiEvents,
                contentDescription = null,
                tint = NeoLudoColors.AmberYellow,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(targetHeight.coerceAtLeast(10.dp))
                .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                .border(1.2.dp, color, RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)),
            color = color.copy(alpha = if (isWinner) 0.35f else 0.18f)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = place,
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = if (isWinner) 18.sp else 14.sp
                )
            }
        }
    }
}
