package com.neoludo.game.ui.game

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val ClassicDieFace = Color(0xFFFFFFFF)
private val ClassicDieEdge = Color(0xFFB0BEC5)
private val ClassicPip = Color(0xFF212121)

/**
 * The one classic die used everywhere: ivory-white rounded square, black
 * pips, gentle wobble while rolling. No skins — same look for everyone.
 */
@Composable
fun ClassicDice(
    value: Int,
    rolling: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sizeDp: Dp = 72.dp
) {
    val wobble = rememberInfiniteTransition(label = "classic_dice_wobble")
    val angle by wobble.animateFloat(
        initialValue = if (rolling) -9f else 0f,
        targetValue = if (rolling) 9f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (rolling) 140 else 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dice_angle"
    )
    val shown = value.coerceIn(1, 6)
    Canvas(
        modifier = modifier
            .size(sizeDp)
            .shadow(6.dp, RoundedCornerShape(22))
            .clip(RoundedCornerShape(22))
            .graphicsLayer { rotationZ = angle }
            .clickable(
                enabled = enabled,
                role = Role.Button,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onClick() }
    ) {
        val s = size.minDimension
        drawRoundRect(
            color = if (enabled) ClassicDieFace else ClassicDieFace.copy(alpha = 0.75f),
            topLeft = Offset.Zero,
            size = size,
            cornerRadius = CornerRadius(s * 0.22f, s * 0.22f)
        )
        drawRoundRect(
            color = ClassicDieEdge,
            topLeft = Offset.Zero,
            size = size,
            cornerRadius = CornerRadius(s * 0.22f, s * 0.22f),
            style = Stroke(width = s * 0.035f)
        )
        drawClassicPips(shown, s)
    }
}

private fun DrawScope.drawClassicPips(value: Int, s: Float) {
    val o = s * 0.29f // outer grid line
    val c = s * 0.5f // center
    val r = s * 0.078f
    fun pip(x: Float, y: Float) {
        drawCircle(color = ClassicPip, radius = r, center = Offset(x, y))
    }
    when (value) {
        1 -> pip(c, c)
        2 -> {
            pip(o, o)
            pip(s - o, s - o)
        }
        3 -> {
            pip(o, o)
            pip(c, c)
            pip(s - o, s - o)
        }
        4 -> {
            pip(o, o)
            pip(s - o, o)
            pip(o, s - o)
            pip(s - o, s - o)
        }
        5 -> {
            pip(o, o)
            pip(s - o, o)
            pip(c, c)
            pip(o, s - o)
            pip(s - o, s - o)
        }
        else -> {
            pip(o, o)
            pip(o, c)
            pip(o, s - o)
            pip(s - o, o)
            pip(s - o, c)
            pip(s - o, s - o)
        }
    }
}
