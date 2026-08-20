package com.neoludo.game.ui.locker

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Token
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoludo.game.core.designsystem.NeoLudoButton
import com.neoludo.game.core.designsystem.NeoLudoCard
import com.neoludo.game.core.designsystem.NeoLudoColors
import com.neoludo.game.core.model.BoardTheme
import com.neoludo.game.core.model.DiceSkin
import com.neoludo.game.core.model.PawnSkin
import com.neoludo.game.engine.model.DiceState
import com.neoludo.game.engine.model.PlayerColor
import com.neoludo.game.ui.game.Dice3DRenderer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

enum class LockerTab(val title: String, val icon: ImageVector) {
    BOARDS("Board Themes", Icons.Default.Dashboard),
    DICE("3D Dice Skins", Icons.Default.Casino),
    PAWNS("Pawn Tokens", Icons.Default.Token)
}

@Composable
fun LockerScreen(
    currentBoardTheme: BoardTheme,
    currentDiceSkin: DiceSkin,
    currentPawnSkin: PawnSkin,
    onSelectBoardTheme: (BoardTheme) -> Unit,
    onSelectDiceSkin: (DiceSkin) -> Unit,
    onSelectPawnSkin: (PawnSkin) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(LockerTab.BOARDS) }
    var testDiceValue by remember { mutableIntStateOf(6) }
    var isTestRolling by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NeoLudoColors.ObsidianBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(36.dp))

            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(NeoLudoColors.ObsidianSurfaceCard)
                        .border(1.dp, NeoLudoColors.ObsidianBorder, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = "COSMETICS LOCKER",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "Customize your Neo-Ludo visual arena",
                        color = NeoLudoColors.ObsidianTextSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Category Tab Selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(NeoLudoColors.ObsidianSurface)
                    .border(1.dp, NeoLudoColors.ObsidianBorder, RoundedCornerShape(16.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                LockerTab.values().forEach { tab ->
                    val isSelected = tab == selectedTab
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { selectedTab = tab },
                        color = if (isSelected) NeoLudoColors.CobaltBlue else Color.Transparent,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = null,
                                tint = if (isSelected) Color.White else NeoLudoColors.ObsidianTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = tab.title.split(" ").first(),
                                color = if (isSelected) Color.White else NeoLudoColors.ObsidianTextSecondary,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Live Interactive Preview Hero Section
            NeoLudoCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = NeoLudoColors.CobaltBlue.copy(alpha = 0.4f),
                backgroundColor = NeoLudoColors.ObsidianSurfaceCard
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (selectedTab) {
                        LockerTab.BOARDS -> {
                            val palette = NeoLudoColors.getBoardColors(currentBoardTheme)
                            Text(
                                text = "ACTIVE BOARD THEME PREVIEW",
                                color = NeoLudoColors.ObsidianTextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Box(
                                modifier = Modifier
                                    .size(160.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(palette.background)
                                    .border(2.dp, palette.boardBorderGlow, RoundedCornerShape(16.dp))
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val bSize = size.minDimension
                                    val cSize = bSize / 5f
                                    // Mini board quadrant preview
                                    drawRoundRect(
                                        color = palette.red,
                                        topLeft = Offset(4f, 4f),
                                        size = Size(cSize * 2f, cSize * 2f),
                                        cornerRadius = CornerRadius(8f, 8f)
                                    )
                                    drawRoundRect(
                                        color = palette.green,
                                        topLeft = Offset(bSize - cSize * 2f - 4f, 4f),
                                        size = Size(cSize * 2f, cSize * 2f),
                                        cornerRadius = CornerRadius(8f, 8f)
                                    )
                                    drawRoundRect(
                                        color = palette.yellow,
                                        topLeft = Offset(bSize - cSize * 2f - 4f, bSize - cSize * 2f - 4f),
                                        size = Size(cSize * 2f, cSize * 2f),
                                        cornerRadius = CornerRadius(8f, 8f)
                                    )
                                    drawRoundRect(
                                        color = palette.blue,
                                        topLeft = Offset(4f, bSize - cSize * 2f - 4f),
                                        size = Size(cSize * 2f, cSize * 2f),
                                        cornerRadius = CornerRadius(8f, 8f)
                                    )
                                    // Center diamond
                                    drawCircle(palette.starSafeColor, cSize * 0.7f, Offset(bSize / 2f, bSize / 2f))
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = currentBoardTheme.displayName,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }

                        LockerTab.DICE -> {
                            Text(
                                text = "INTERACTIVE 3D DICE PREVIEW",
                                color = NeoLudoColors.ObsidianTextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Dice3DRenderer(
                                diceState = DiceState(value = testDiceValue, canRoll = true),
                                playerColor = PlayerColor.RED,
                                isRolling = isTestRolling,
                                skin = currentDiceSkin,
                                onRollClick = {
                                    scope.launch {
                                        isTestRolling = true
                                        testDiceValue = (1..6).random()
                                        delay(300)
                                        isTestRolling = false
                                    }
                                },
                                sizeDp = 80.dp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Tap Dice to Test Roll 🎲",
                                color = NeoLudoColors.AmberYellow,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        LockerTab.PAWNS -> {
                            Text(
                                text = "PAWN TOKEN SET PREVIEW",
                                color = NeoLudoColors.ObsidianTextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                listOf(
                                    Triple(PlayerColor.RED, NeoLudoColors.RubyRed, "Red"),
                                    Triple(PlayerColor.GREEN, NeoLudoColors.EmeraldGreen, "Green"),
                                    Triple(PlayerColor.YELLOW, NeoLudoColors.AmberYellow, "Yellow"),
                                    Triple(PlayerColor.BLUE, NeoLudoColors.CobaltBlue, "Blue")
                                ).forEach { (_, color, _) ->
                                    Box(
                                        modifier = Modifier
                                            .padding(horizontal = 8.dp)
                                            .size(46.dp)
                                    ) {
                                        Canvas(modifier = Modifier.fillMaxSize()) {
                                            val rad = size.minDimension * 0.45f
                                            val c = Offset(size.width / 2f, size.height / 2f)
                                            drawPawnPreview(c, rad, color, currentPawnSkin)
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = currentPawnSkin.displayName,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Item Cards List
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
                label = "locker_content"
            ) { tab ->
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    when (tab) {
                        LockerTab.BOARDS -> {
                            items(BoardTheme.values().size) { idx ->
                                val theme = BoardTheme.values()[idx]
                                val isEquipped = theme == currentBoardTheme
                                CosmeticCard(
                                    title = theme.displayName,
                                    description = theme.description,
                                    isEquipped = isEquipped,
                                    accentColor = when (theme) {
                                        BoardTheme.CYBER_OBSIDIAN -> NeoLudoColors.CobaltBlue
                                        BoardTheme.ROYAL_PARCHMENT -> Color(0xFFD4AF37)
                                        BoardTheme.SYNTHWAVE_NEON -> Color(0xFFFF007F)
                                        BoardTheme.FROST_TITANIUM -> Color(0xFF00E5FF)
                                    },
                                    onEquip = { onSelectBoardTheme(theme) }
                                )
                            }
                        }

                        LockerTab.DICE -> {
                            items(DiceSkin.values().size) { idx ->
                                val skin = DiceSkin.values()[idx]
                                val isEquipped = skin == currentDiceSkin
                                CosmeticCard(
                                    title = skin.displayName,
                                    description = skin.description,
                                    isEquipped = isEquipped,
                                    accentColor = when (skin) {
                                        DiceSkin.PRISM_CRYSTAL -> Color(0xFF00E5FF)
                                        DiceSkin.CARBON_CYBER -> Color(0xFF00F0FF)
                                        DiceSkin.ROYAL_GOLD -> Color(0xFFFFD700)
                                        DiceSkin.CLASSIC_IVORY -> Color(0xFFD7CCC8)
                                    },
                                    onEquip = { onSelectDiceSkin(skin) }
                                )
                            }
                        }

                        LockerTab.PAWNS -> {
                            items(PawnSkin.values().size) { idx ->
                                val skin = PawnSkin.values()[idx]
                                val isEquipped = skin == currentPawnSkin
                                CosmeticCard(
                                    title = skin.displayName,
                                    description = skin.description,
                                    isEquipped = isEquipped,
                                    accentColor = when (skin) {
                                        PawnSkin.CYBER_PIPS -> NeoLudoColors.CobaltBlue
                                        PawnSkin.ROYAL_CROWNS -> Color(0xFFFFD700)
                                        PawnSkin.CRYSTAL_GEMS -> Color(0xFF00F0FF)
                                    },
                                    onEquip = { onSelectPawnSkin(skin) }
                                )
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CosmeticCard(
    title: String,
    description: String,
    isEquipped: Boolean,
    accentColor: Color,
    onEquip: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(
                1.5.dp,
                if (isEquipped) accentColor else NeoLudoColors.ObsidianBorder,
                RoundedCornerShape(18.dp)
            )
            .clickable { onEquip() },
        color = if (isEquipped) accentColor.copy(alpha = 0.12f) else NeoLudoColors.ObsidianSurfaceCard,
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accentColor.copy(alpha = 0.2f))
                    .border(1.dp, accentColor, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isEquipped) Icons.Default.CheckCircle else Icons.Default.Token,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    color = NeoLudoColors.ObsidianTextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (isEquipped) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = accentColor.copy(alpha = 0.25f),
                    border = BorderStroke(1.dp, accentColor)
                ) {
                    Text(
                        text = "EQUIPPED",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = NeoLudoColors.ObsidianSurface,
                    border = BorderStroke(1.dp, NeoLudoColors.ObsidianBorder)
                ) {
                    Text(
                        text = "EQUIP",
                        color = NeoLudoColors.ObsidianTextSecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawPawnPreview(
    center: Offset,
    radius: Float,
    color: Color,
    pawnSkin: PawnSkin
) {
    // Drop shadow
    drawCircle(
        color = Color.Black.copy(alpha = 0.45f),
        radius = radius * 1.05f,
        center = Offset(center.x + 2f, center.y + 3f)
    )

    when (pawnSkin) {
        PawnSkin.CYBER_PIPS -> {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(color.copy(alpha = 0.95f), color, Color.Black.copy(alpha = 0.35f)),
                    center = Offset(center.x - radius * 0.3f, center.y - radius * 0.3f),
                    radius = radius * 1.2f
                ),
                radius = radius,
                center = center
            )
            drawCircle(Color.White.copy(alpha = 0.65f), radius * 0.65f, center, style = Stroke(2f))
            drawCircle(Color.White, radius * 0.26f, center)
        }
        PawnSkin.ROYAL_CROWNS -> {
            val goldLight = Color(0xFFFFE082)
            val goldDark = Color(0xFFC79100)
            drawCircle(
                brush = Brush.radialGradient(listOf(goldLight, goldDark), center, radius),
                radius = radius,
                center = center
            )
            drawCircle(color, radius * 0.72f, center)
            val crownPath = Path().apply {
                moveTo(center.x - radius * 0.45f, center.y + radius * 0.3f)
                lineTo(center.x - radius * 0.45f, center.y - radius * 0.25f)
                lineTo(center.x - radius * 0.2f, center.y + radius * 0.05f)
                lineTo(center.x, center.y - radius * 0.42f)
                lineTo(center.x + radius * 0.2f, center.y + radius * 0.05f)
                lineTo(center.x + radius * 0.45f, center.y - radius * 0.25f)
                lineTo(center.x + radius * 0.45f, center.y + radius * 0.3f)
                close()
            }
            drawPath(crownPath, goldLight)
            drawCircle(Color.White, radius * 0.16f, Offset(center.x, center.y + radius * 0.15f))
        }
        PawnSkin.CRYSTAL_GEMS -> {
            val hexPath = Path()
            val points = 6
            for (i in 0 until points) {
                val angle = i * (2.0 * Math.PI / points) - Math.PI / 2.0
                val x = (center.x + radius * cos(angle)).toFloat()
                val y = (center.y + radius * sin(angle)).toFloat()
                if (i == 0) hexPath.moveTo(x, y) else hexPath.lineTo(x, y)
            }
            hexPath.close()

            drawPath(
                hexPath,
                brush = Brush.linearGradient(
                    listOf(color.copy(alpha = 0.95f), color, Color.Black.copy(alpha = 0.4f)),
                    start = Offset(center.x - radius, center.y - radius),
                    end = Offset(center.x + radius, center.y + radius)
                )
            )
            drawPath(hexPath, Color.White.copy(alpha = 0.85f), style = Stroke(width = 2f))
            drawCircle(Color.White, radius * 0.2f, center)
        }
    }
}
