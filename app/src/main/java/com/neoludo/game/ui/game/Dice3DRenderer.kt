package com.neoludo.game.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.neoludo.game.core.designsystem.NeoLudoColors
import com.neoludo.game.engine.model.DiceState
import com.neoludo.game.engine.model.PlayerColor
import kotlinx.coroutines.delay

@Composable
fun Dice3DRenderer(
    diceState: DiceState,
    playerColor: PlayerColor,
    isRolling: Boolean,
    onRollClick: () -> Unit,
    modifier: Modifier = Modifier,
    sizeDp: Dp = 72.dp
) {
    val accentColor = NeoLudoColors.getPlayerColor(playerColor)
    val rotationAnim = remember { Animatable(0f) }
    val bounceAnim = remember { Animatable(0f) }
    var displayedValue by remember { mutableIntStateOf(diceState.value) }

    val infiniteTransition = rememberInfiniteTransition(label = "dice_glow")
    val glowScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_scale"
    )

    LaunchedEffect(isRolling) {
        if (isRolling) {
            // Rapid tumbling animation
            for (i in 0..6) {
                displayedValue = (1..6).random()
                rotationAnim.animateTo(
                    targetValue = (i + 1) * 90f,
                    animationSpec = tween(50, easing = FastOutSlowInEasing)
                )
                delay(40)
            }
            bounceAnim.animateTo(-25f, tween(100, easing = FastOutSlowInEasing))
            bounceAnim.animateTo(0f, tween(150, easing = FastOutSlowInEasing))
        } else {
            displayedValue = diceState.value
            rotationAnim.snapTo(0f)
            bounceAnim.snapTo(0f)
        }
    }

    LaunchedEffect(diceState.value) {
        displayedValue = diceState.value
    }

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .size(sizeDp)
            .graphicsLayer {
                translationY = bounceAnim.value
                rotationZ = rotationAnim.value % 360f
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = diceState.canRoll && !isRolling,
                onClick = onRollClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(sizeDp)) {
            val dSize = size.minDimension
            val cornerRadius = dSize * 0.22f

            // 1. Outer Interactive Glow (when player can roll)
            if (diceState.canRoll) {
                drawRoundRect(
                    color = accentColor.copy(alpha = 0.4f),
                    topLeft = Offset(-dSize * 0.08f, -dSize * 0.08f),
                    size = Size(dSize * 1.16f * glowScale, dSize * 1.16f * glowScale),
                    cornerRadius = CornerRadius(cornerRadius * 1.3f, cornerRadius * 1.3f)
                )
            }

            // 2. Drop Shadow
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.5f),
                topLeft = Offset(4f, 8f),
                size = Size(dSize - 4f, dSize - 4f),
                cornerRadius = CornerRadius(cornerRadius, cornerRadius)
            )

            // 3. 3D Beveled Cube Surface Gradient
            drawRoundRect(
                brush = Brush.linearGradient(
                    listOf(Color(0xFFFFFFFF), Color(0xFFE2E8F0), Color(0xFFCBD5E1)),
                    start = Offset(0f, 0f),
                    end = Offset(dSize, dSize)
                ),
                topLeft = Offset(0f, 0f),
                size = Size(dSize, dSize),
                cornerRadius = CornerRadius(cornerRadius, cornerRadius)
            )

            // 4. Glowing Accent Border
            drawRoundRect(
                color = if (diceState.canRoll) accentColor else NeoLudoColors.ObsidianBorder,
                topLeft = Offset(0f, 0f),
                size = Size(dSize, dSize),
                cornerRadius = CornerRadius(cornerRadius, cornerRadius),
                style = Stroke(width = if (diceState.canRoll) 3.5f else 1.5f)
            )

            // 5. Pip Dots (1..6)
            drawPipDots(displayedValue, dSize, accentColor)
        }
    }
}

private fun DrawScope.drawPipDots(value: Int, size: Float, dotColor: Color) {
    val radius = size * 0.085f
    val center = size / 2f
    val p1 = size * 0.28f
    val p2 = size * 0.72f

    fun drawPip(x: Float, y: Float) {
        // Pip inner shadow + color dot
        drawCircle(
            color = Color.Black.copy(alpha = 0.2f),
            radius = radius * 1.15f,
            center = Offset(x + 1f, y + 1.5f)
        )
        drawCircle(
            color = dotColor,
            radius = radius,
            center = Offset(x, y)
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.6f),
            radius = radius * 0.35f,
            center = Offset(x - radius * 0.3f, y - radius * 0.3f)
        )
    }

    when (value) {
        1 -> {
            drawPip(center, center)
        }
        2 -> {
            drawPip(p1, p1)
            drawPip(p2, p2)
        }
        3 -> {
            drawPip(p1, p1)
            drawPip(center, center)
            drawPip(p2, p2)
        }
        4 -> {
            drawPip(p1, p1)
            drawPip(p2, p1)
            drawPip(p1, p2)
            drawPip(p2, p2)
        }
        5 -> {
            drawPip(p1, p1)
            drawPip(p2, p1)
            drawPip(center, center)
            drawPip(p1, p2)
            drawPip(p2, p2)
        }
        6 -> {
            drawPip(p1, p1)
            drawPip(p2, p1)
            drawPip(p1, center)
            drawPip(p2, center)
            drawPip(p1, p2)
            drawPip(p2, p2)
        }
    }
}
