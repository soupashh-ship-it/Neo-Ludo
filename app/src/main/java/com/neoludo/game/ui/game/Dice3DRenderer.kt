package com.neoludo.game.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.neoludo.game.core.designsystem.NeoLudoColors
import com.neoludo.game.core.model.DiceSkin
import com.neoludo.game.engine.model.DiceState
import com.neoludo.game.engine.model.PlayerColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun Dice3DRenderer(
    diceState: DiceState,
    playerColor: PlayerColor,
    isRolling: Boolean,
    onRollClick: () -> Unit,
    skin: DiceSkin = DiceSkin.PRISM_CRYSTAL,
    modifier: Modifier = Modifier,
    sizeDp: Dp = 72.dp
) {
    val accentColor = NeoLudoColors.getPlayerColor(playerColor)
    val rotationZAnim = remember { Animatable(0f) }
    val rotationXAnim = remember { Animatable(0f) }
    val rotationYAnim = remember { Animatable(0f) }
    val bounceAnim = remember { Animatable(0f) }
    val squashXAnim = remember { Animatable(1f) }
    val squashYAnim = remember { Animatable(1f) }

    var displayedValue by remember { mutableIntStateOf(diceState.value) }

    val infiniteTransition = rememberInfiniteTransition(label = "dice_glow")
    val glowScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_scale"
    )
    val sparklePulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sparkle_pulse"
    )

    LaunchedEffect(isRolling) {
        if (isRolling) {
            // Rapid tumbling animation with 3D roll and squash bounce
            for (i in 0..7) {
                displayedValue = (1..6).random()
                rotationZAnim.animateTo(
                    targetValue = (i + 1) * 75f,
                    animationSpec = tween(45, easing = FastOutSlowInEasing)
                )
                rotationXAnim.animateTo(
                    targetValue = (i % 2) * 20f - 10f,
                    animationSpec = tween(45, easing = FastOutSlowInEasing)
                )
                rotationYAnim.animateTo(
                    targetValue = ((i + 1) % 2) * 20f - 10f,
                    animationSpec = tween(45, easing = FastOutSlowInEasing)
                )
                delay(35)
            }

            // Floor impact bounce with dynamic squash
            bounceAnim.animateTo(-32f, tween(110, easing = FastOutSlowInEasing))
            bounceAnim.animateTo(0f, tween(130, easing = FastOutSlowInEasing))

            // Impact squash
            squashXAnim.animateTo(1.18f, tween(60, easing = FastOutSlowInEasing))
            squashYAnim.animateTo(0.84f, tween(60, easing = FastOutSlowInEasing))

            // Spring restitution to normal 1.0f
            squashXAnim.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
            squashYAnim.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        } else {
            displayedValue = diceState.value
            rotationZAnim.snapTo(0f)
            rotationXAnim.snapTo(0f)
            rotationYAnim.snapTo(0f)
            bounceAnim.snapTo(0f)
            squashXAnim.snapTo(1f)
            squashYAnim.snapTo(1f)
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
                rotationZ = rotationZAnim.value % 360f
                rotationX = rotationXAnim.value
                rotationY = rotationYAnim.value
                scaleX = squashXAnim.value
                scaleY = squashYAnim.value
                cameraDistance = 12f * density
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
                    color = accentColor.copy(alpha = 0.45f),
                    topLeft = Offset(-dSize * 0.08f, -dSize * 0.08f),
                    size = Size(dSize * 1.16f * glowScale, dSize * 1.16f * glowScale),
                    cornerRadius = CornerRadius(cornerRadius * 1.35f, cornerRadius * 1.35f)
                )
            }

            // Rolling speed trail particles
            if (isRolling) {
                drawRollingParticles(dSize, accentColor)
            }

            // 2. Drop Shadow
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.55f),
                topLeft = Offset(4f, 8f),
                size = Size(dSize - 4f, dSize - 4f),
                cornerRadius = CornerRadius(cornerRadius, cornerRadius)
            )

            // 3. Render Custom Dice Skin
            when (skin) {
                DiceSkin.RUBY_ARCADE -> {
                    // Bold Arcade Crimson Red Body (Matching Reference Image #1)
                    drawRoundRect(
                        brush = Brush.linearGradient(
                            listOf(Color(0xFFE53935), Color(0xFFD32F2F), Color(0xFFC62828)),
                            start = Offset(0f, 0f),
                            end = Offset(dSize, dSize)
                        ),
                        topLeft = Offset(0f, 0f),
                        size = Size(dSize, dSize),
                        cornerRadius = CornerRadius(cornerRadius, cornerRadius)
                    )
                    // Crisp white glossy top sheen
                    val glintPath = Path().apply {
                        moveTo(dSize * 0.15f, dSize * 0.15f)
                        lineTo(dSize * 0.85f, dSize * 0.15f)
                        lineTo(dSize * 0.15f, dSize * 0.85f)
                        close()
                    }
                    drawPath(glintPath, Color.White.copy(alpha = 0.22f))

                    // Smooth outer border
                    drawRoundRect(
                        color = if (diceState.canRoll) Color(0xFFFFD54F) else Color(0xFFB71C1C),
                        topLeft = Offset(0f, 0f),
                        size = Size(dSize, dSize),
                        cornerRadius = CornerRadius(cornerRadius, cornerRadius),
                        style = Stroke(width = if (diceState.canRoll) 3.5f else 2f)
                    )

                    // Crisp White Pips
                    drawPipDots(displayedValue, dSize, Color.White)
                }
                DiceSkin.PRISM_CRYSTAL -> {
                    drawRoundRect(
                        brush = Brush.linearGradient(
                            listOf(Color(0xFFE0F7FA), Color(0xFF80DEEA), Color(0xFF26C6DA), Color(0xFF00838F)),
                            start = Offset(0f, 0f),
                            end = Offset(dSize, dSize)
                        ),
                        topLeft = Offset(0f, 0f),
                        size = Size(dSize, dSize),
                        cornerRadius = CornerRadius(cornerRadius, cornerRadius)
                    )
                    // Specular Glint Diagonal Line
                    val glintPath = Path().apply {
                        moveTo(dSize * 0.15f, dSize * 0.85f)
                        lineTo(dSize * 0.85f, dSize * 0.15f)
                    }
                    drawPath(glintPath, Color.White.copy(alpha = 0.5f), style = Stroke(width = 3.5f))

                    // Glowing Neon Refractive Rim
                    drawRoundRect(
                        color = if (diceState.canRoll) accentColor else Color(0xFF80DEEA),
                        topLeft = Offset(0f, 0f),
                        size = Size(dSize, dSize),
                        cornerRadius = CornerRadius(cornerRadius, cornerRadius),
                        style = Stroke(width = if (diceState.canRoll) 3.5f else 1.8f)
                    )

                    // Neon Pips
                    drawPipDots(displayedValue, dSize, if (diceState.canRoll) accentColor else Color(0xFF00E5FF), isNeon = true)
                }
                DiceSkin.CARBON_CYBER -> {
                    // Carbon Fiber Dark Weave
                    drawRoundRect(
                        brush = Brush.linearGradient(
                            listOf(Color(0xFF1F2937), Color(0xFF111827), Color(0xFF0F172A)),
                            start = Offset(0f, 0f),
                            end = Offset(dSize, dSize)
                        ),
                        topLeft = Offset(0f, 0f),
                        size = Size(dSize, dSize),
                        cornerRadius = CornerRadius(cornerRadius, cornerRadius)
                    )
                    // Carbon weave grid lines
                    val step = dSize / 5f
                    for (i in 1..4) {
                        drawLine(
                            color = Color.White.copy(alpha = 0.08f),
                            start = Offset(i * step, 0f),
                            end = Offset(i * step, dSize),
                            strokeWidth = 1f
                        )
                        drawLine(
                            color = Color.White.copy(alpha = 0.08f),
                            start = Offset(0f, i * step),
                            end = Offset(dSize, i * step),
                            strokeWidth = 1f
                        )
                    }
                    // Electric Cyber Cyan Rim
                    drawRoundRect(
                        color = if (diceState.canRoll) accentColor else Color(0xFF00F0FF),
                        topLeft = Offset(0f, 0f),
                        size = Size(dSize, dSize),
                        cornerRadius = CornerRadius(cornerRadius, cornerRadius),
                        style = Stroke(width = if (diceState.canRoll) 3.5f else 2f)
                    )
                    // Laser Cyan Pips
                    drawPipDots(displayedValue, dSize, Color(0xFF00F0FF), isNeon = true)
                }
                DiceSkin.ROYAL_GOLD -> {
                    // Metallic 24k Polished Gold
                    drawRoundRect(
                        brush = Brush.linearGradient(
                            listOf(Color(0xFFFFF8E1), Color(0xFFFFD54F), Color(0xFFFFB300), Color(0xFFC79100)),
                            start = Offset(0f, 0f),
                            end = Offset(dSize, dSize)
                        ),
                        topLeft = Offset(0f, 0f),
                        size = Size(dSize, dSize),
                        cornerRadius = CornerRadius(cornerRadius, cornerRadius)
                    )
                    // Inner Embossed Gold Rim
                    drawRoundRect(
                        color = Color(0xFFFFE082),
                        topLeft = Offset(dSize * 0.06f, dSize * 0.06f),
                        size = Size(dSize * 0.88f, dSize * 0.88f),
                        cornerRadius = CornerRadius(cornerRadius * 0.8f, cornerRadius * 0.8f),
                        style = Stroke(width = 1.5f)
                    )
                    drawRoundRect(
                        color = if (diceState.canRoll) accentColor else Color(0xFFB78103),
                        topLeft = Offset(0f, 0f),
                        size = Size(dSize, dSize),
                        cornerRadius = CornerRadius(cornerRadius, cornerRadius),
                        style = Stroke(width = if (diceState.canRoll) 3.5f else 2f)
                    )
                    // Ruby Gemstone Pips
                    drawPipDots(displayedValue, dSize, Color(0xFFD50000), isRuby = true)
                }
                DiceSkin.CLASSIC_IVORY -> {
                    // Warm Ivory Resin
                    drawRoundRect(
                        brush = Brush.linearGradient(
                            listOf(Color(0xFFFFFDF9), Color(0xFFF7F3EE), Color(0xFFEBE3DB)),
                            start = Offset(0f, 0f),
                            end = Offset(dSize, dSize)
                        ),
                        topLeft = Offset(0f, 0f),
                        size = Size(dSize, dSize),
                        cornerRadius = CornerRadius(cornerRadius, cornerRadius)
                    )
                    // Smooth Ivory Beveled Border
                    drawRoundRect(
                        color = if (diceState.canRoll) accentColor else Color(0xFFD7CCC8),
                        topLeft = Offset(0f, 0f),
                        size = Size(dSize, dSize),
                        cornerRadius = CornerRadius(cornerRadius, cornerRadius),
                        style = Stroke(width = if (diceState.canRoll) 3.5f else 1.8f)
                    )
                    // Classic Recessed Obsidian Pips
                    drawPipDots(displayedValue, dSize, Color(0xFF1C1917))
                }
            }
        }
    }
}

private fun DrawScope.drawRollingParticles(size: Float, color: Color) {
    val center = size / 2f
    val particleCount = 6
    for (i in 0 until particleCount) {
        val angle = (i * (360.0 / particleCount)) * Math.PI / 180.0
        val dist = size * 0.65f
        val px = (center + dist * cos(angle)).toFloat()
        val py = (center + dist * sin(angle)).toFloat()
        drawCircle(
            color = color.copy(alpha = 0.6f),
            radius = 3.5f,
            center = Offset(px, py)
        )
    }
}

private fun DrawScope.drawPipDots(
    value: Int,
    size: Float,
    dotColor: Color,
    isNeon: Boolean = false,
    isRuby: Boolean = false
) {
    val radius = size * 0.088f
    val center = size / 2f
    val p1 = size * 0.28f
    val p2 = size * 0.72f

    fun drawPip(x: Float, y: Float) {
        val pipCenter = Offset(x, y)
        if (isRuby) {
            // Inlaid Ruby Gem Facet
            drawCircle(
                color = Color.Black.copy(alpha = 0.35f),
                radius = radius * 1.15f,
                center = Offset(x + 1f, y + 1.5f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(0xFFFF5252), Color(0xFFD50000), Color(0xFF8B0000)),
                    center = Offset(x - radius * 0.25f, y - radius * 0.25f),
                    radius = radius
                ),
                radius = radius,
                center = pipCenter
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.85f),
                radius = radius * 0.3f,
                center = Offset(x - radius * 0.3f, y - radius * 0.3f)
            )
        } else if (isNeon) {
            // Glowing Neon Core
            drawCircle(
                color = dotColor.copy(alpha = 0.45f),
                radius = radius * 1.45f,
                center = pipCenter
            )
            drawCircle(
                color = dotColor,
                radius = radius,
                center = pipCenter
            )
            drawCircle(
                color = Color.White,
                radius = radius * 0.4f,
                center = pipCenter
            )
        } else {
            // Classic Recessed Pip with Inner Shadow
            drawCircle(
                color = Color.Black.copy(alpha = 0.25f),
                radius = radius * 1.15f,
                center = Offset(x + 1f, y + 1.5f)
            )
            drawCircle(
                color = dotColor,
                radius = radius,
                center = pipCenter
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.4f),
                radius = radius * 0.3f,
                center = Offset(x - radius * 0.3f, y - radius * 0.3f)
            )
        }
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
