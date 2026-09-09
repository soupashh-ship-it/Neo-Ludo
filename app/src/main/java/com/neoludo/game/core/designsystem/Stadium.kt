package com.neoludo.game.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * Midnight Stadium identity — Phase 1 (dark-only, phones + tablets).
 *
 * One accent (stadium floodlight blue), one texture (spotlight glow over
 * obsidian), ticket-stub cards. Everything routes through these tokens so
 * no screen hardcodes gradients or hex literals.
 *
 * Gameplay code (engine, multiplayer, BoardCoordinates, CanvasLudoBoard
 * geometry) is untouched — this file is pure chrome.
 */
object StadiumColors {
    /** Single brand accent. The only saturated CTA color in Phase 1. */
    val Accent = Color(0xFF3B82F6)
    val AccentBright = Color(0xFF60A5FA)
    val AccentDeep = Color(0xFF1D4ED8)
    val AccentContainer = Color(0xFF16294D)

    /** Spotlight wash behind the board / hero cards. */
    val Spotlight = Color(0xFF1E3A8A)

    /** Card + border ramp on obsidian. */
    val Card = Color(0xFF141B2A)
    val CardElevated = Color(0xFF1A2334)
    val Border = Color(0xFF28344A)
    val BorderBright = Color(0xFF3B4D6E)

    /** Text ramp. */
    val TextPrimary = Color(0xFFF1F5F9)
    val TextSecondary = Color(0xFF94A3B8)
    val TextMuted = Color(0xFF64748B)

    /** Reward-only gold. Never a CTA — daily bonus, trophies, dice pips. */
    val Gold = Color(0xFFFFD600)
    val GoldDeep = Color(0xFFB26A00)

    /** Status-only green/red. Timer urgency, reconnect, errors. */
    val Success = Color(0xFF00E676)
    val Danger = Color(0xFFFF3366)
}

object StadiumDimens {
    /** Phone content column cap. */
    val ContentMaxPhone = 560.dp
    /** Tablet content column cap. */
    val ContentMaxTablet = 720.dp
    /** Dialog/sheet cap so tablets don't stretch full width. */
    val DialogMax = 480.dp
    /** Board cap on tablets — leaves room for HUD + tray. */
    val BoardMax = 520.dp
    /** Minimum touch target. */
    val TouchMin = 48.dp
}

/** True on 600dp+ widths (tablets, unfolded foldables). No new dependency. */
@Composable
fun isTablet(): Boolean {
    return LocalConfiguration.current.screenWidthDp >= 600
}

/**
 * Full-screen stadium backdrop: obsidian base + floodlight spotlight
 * blooming from the top edge. Replaces per-screen hardcoded gradients.
 */
@Composable
fun StadiumBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NeoLudoColors.ObsidianBackground)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            StadiumColors.Spotlight.copy(alpha = 0.30f),
                            Color.Transparent
                        ),
                        center = Offset(600f, 0f),
                        radius = 1100f
                    )
                )
        )
        content()
    }
}

/**
 * Responsive page column: safe-area aware, horizontally centered with a
 * width cap that grows on tablets. Replaces fixed
 * Spacer(28/36.dp) + padding(horizontal=20.dp) patterns.
 */
@Composable
fun AdaptiveContent(
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val tablet = isTablet()
    val cap = if (tablet) StadiumDimens.ContentMaxTablet else StadiumDimens.ContentMaxPhone
    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = cap)
                .fillMaxWidth()
                .padding(horizontal = if (tablet) 28.dp else horizontalPadding),
            content = content
        )
    }
}

/**
 * Ticket-stub card: single accent keyline on top, quiet border everywhere
 * else. Distinctive without brutalist noise; one shape scale (14dp).
 */
@Composable
fun TicketCard(
    modifier: Modifier = Modifier,
    accent: Color = StadiumColors.Accent,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = StadiumColors.Card),
        border = BorderStroke(1.dp, StadiumColors.Border)
    ) {
        Column {
            // 2dp accent keyline — the ticket-stub signature.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(accent, accent.copy(alpha = 0.25f), Color.Transparent)
                        )
                    )
            )
            Box(modifier = Modifier.padding(NeoLudoSpacing.lg)) {
                content()
            }
        }
    }
}

/**
 * Width-capped dialog shell for phones + tablets.
 */
@Composable
fun CappedDialog(
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            modifier = Modifier.widthIn(max = StadiumDimens.DialogMax).fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = StadiumColors.CardElevated),
            border = BorderStroke(1.dp, StadiumColors.BorderBright)
        ) {
            Box(modifier = Modifier.padding(NeoLudoSpacing.xl)) {
                content()
            }
        }
    }
}

/**
 * Board slot that caps width on tablets/landscape while staying full-bleed
 * on phones. Board canvas geometry itself is untouched.
 */@Composable
fun BoardSlot(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        val density = LocalDensity.current
        // Cap the board so HUD + tray keep their budget on large screens.
        val boardWidth = minOf(maxWidth, StadiumDimens.BoardMax)
        // On very short screens (landscape) also respect height budget.
        val maxH = with(density) { constraints.maxHeight.toDp() }
        val boardSize = if (maxH < 560.dp && maxH > 0.dp) {
            minOf(boardWidth, (maxH * 0.52f).coerceAtLeast(240.dp))
        } else {
            boardWidth
        }
        Box(
            modifier = Modifier
                .widthIn(max = boardSize)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

/**
 * Scrollable sibling of [AdaptiveContent] for form screens.
 */
@Composable
fun AdaptiveScroll(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val tablet = isTablet()
    val cap = if (tablet) StadiumDimens.ContentMaxTablet else StadiumDimens.ContentMaxPhone
    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = cap)
                .fillMaxWidth()
                .padding(horizontal = if (tablet) 28.dp else 20.dp)
                .verticalScroll(rememberScrollState()),
            content = content
        )
    }
}

/**
 * Shared sub-screen header: 48dp back target + title. Centers content
 * column via the caller (pair with AdaptiveContent or a capped Column).
 */@Composable
fun ScreenHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth()
    ) {
        Surface(
            onClick = onBack,
            modifier = Modifier.size(48.dp),
            shape = androidx.compose.foundation.shape.CircleShape,
            color = StadiumColors.Card,
            border = BorderStroke(1.dp, StadiumColors.Border)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = StadiumColors.TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(NeoLudoSpacing.lg))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = StadiumColors.TextPrimary
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = StadiumColors.TextSecondary
                )
            }
        }
    }
}
