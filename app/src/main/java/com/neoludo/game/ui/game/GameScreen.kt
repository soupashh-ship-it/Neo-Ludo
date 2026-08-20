package com.neoludo.game.ui.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.material.icons.filled.AddReaction
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoludo.game.core.audio.HapticController
import com.neoludo.game.core.audio.HapticType
import com.neoludo.game.core.audio.SoundController
import com.neoludo.game.core.audio.SoundEffect
import com.neoludo.game.core.designsystem.NeoLudoColors
import com.neoludo.game.core.designsystem.PlayerPlate
import com.neoludo.game.core.model.BoardTheme
import com.neoludo.game.core.model.DiceSkin
import com.neoludo.game.core.model.PawnSkin
import com.neoludo.game.engine.model.GameEngineEvent
import com.neoludo.game.engine.model.GameState
import com.neoludo.game.engine.model.PlayerColor
import com.neoludo.game.engine.model.TurnPhase
import com.neoludo.game.multiplayer.MultiplayerClient
import com.neoludo.game.multiplayer.model.ChatEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
@Composable
fun GameScreen(
    client: MultiplayerClient,
    soundController: SoundController,
    hapticController: HapticController,
    onGameFinished: (winnerColor: PlayerColor, captures: Int, sixes: Int) -> Unit,
    onExitGame: () -> Unit,
    boardTheme: BoardTheme = BoardTheme.CYBER_OBSIDIAN,
    diceSkin: DiceSkin = DiceSkin.PRISM_CRYSTAL,
    pawnSkin: PawnSkin = PawnSkin.CYBER_PIPS,
    onUpdateTheme: (BoardTheme) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val gameState by client.gameState.collectAsState()
    val chatEvents by client.chatEvents.collectAsState(initial = null)
    val scope = rememberCoroutineScope()

    var showSurrenderDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showEmotePicker by remember { mutableStateOf(false) }
    var activeFloatingEmote by remember { mutableStateOf<ChatEvent?>(null) }
    var isRollingAnimation by remember { mutableStateOf(false) }

    var totalCaptures by remember { mutableIntStateOf(0) }
    var totalSixes by remember { mutableIntStateOf(0) }

    // Turn timer progress
    val timerProgress = remember { Animatable(1f) }

    // Listen to active turn changes to reset timer animation
    LaunchedEffect(gameState?.activePlayerIndex, gameState?.diceState?.value) {
        timerProgress.snapTo(1f)
        timerProgress.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 30000, easing = LinearEasing)
        )
    }

    // Listen to Engine Events for audio and haptics
    LaunchedEffect(gameState?.lastEvent) {
        val event = gameState?.lastEvent ?: return@LaunchedEffect
        when (event) {
            is GameEngineEvent.DiceRolled -> {
                soundController.play(SoundEffect.DICE_ROLL)
                hapticController.perform(HapticType.LIGHT_TICK)
                if (event.value == 6) totalSixes++
            }
            is GameEngineEvent.PieceMoved -> {
                // Handled smoothly by CanvasLudoBoard onStepHop
            }
            is GameEngineEvent.PieceCaptured -> {
                soundController.play(SoundEffect.PIECE_CAPTURE)
                hapticController.perform(HapticType.HEAVY_IMPACT)
                totalCaptures++
            }
            is GameEngineEvent.PieceReachedHome -> {
                soundController.play(SoundEffect.HOME_ENTER)
                hapticController.perform(HapticType.SUCCESS_DOUBLE)
            }
            is GameEngineEvent.GameOver -> {
                soundController.play(SoundEffect.VICTORY)
                hapticController.perform(HapticType.VICTORY_PULSE)
                delay(800)
                onGameFinished(event.winner, totalCaptures, totalSixes)
            }
            else -> Unit
        }
    }

    // Floating Emote listener
    LaunchedEffect(chatEvents) {
        val chat = chatEvents ?: return@LaunchedEffect
        activeFloatingEmote = chat
        delay(2600)
        activeFloatingEmote = null
    }

    val state = gameState
    val palette = NeoLudoColors.getBoardColors(boardTheme)

    if (state == null) {
        // Connecting / Initializing Arena Card
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(palette.background),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    color = palette.blue,
                    modifier = Modifier.size(48.dp),
                    strokeWidth = 4.dp
                )
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "Entering Arena...",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Connecting players & initializing board",
                    color = palette.textSecondary,
                    fontSize = 13.sp
                )
            }
        }
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(36.dp))

            // 1. Top HUD
            GameTopHud(
                onSurrenderClick = { showSurrenderDialog = true },
                onEmoteClick = { showEmotePicker = !showEmotePicker },
                onSettingsClick = { showSettingsDialog = true },
                soundController = soundController
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Top Player Plates (Only for 3-4 Player Games)
            if (state.players.size > 2) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (state.players.isNotEmpty()) {
                        PlayerPlate(
                            player = state.players[0],
                            isActiveTurn = state.activePlayerIndex == 0,
                            turnProgress = if (state.activePlayerIndex == 0) timerProgress.value else 1.0f,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    if (state.players.size > 1) {
                        PlayerPlate(
                            player = state.players[1],
                            isActiveTurn = state.activePlayerIndex == 1,
                            turnProgress = if (state.activePlayerIndex == 1) timerProgress.value else 1.0f,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.weight(1f))

            // 3. Canvas Ludo Game Board with Step-by-Step Hopping Physics & Custom Skins
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CanvasLudoBoard(
                    gameState = state,
                    onPieceClick = { pieceId ->
                        scope.launch {
                            client.movePiece(pieceId)
                        }
                    },
                    onStepHop = {
                        soundController.play(SoundEffect.PIECE_STEP)
                        hapticController.perform(HapticType.LIGHT_TICK)
                    },
                    boardTheme = boardTheme,
                    pawnSkin = pawnSkin,
                    modifier = Modifier.fillMaxWidth()
                )

                // Floating Emote Display with Spring Fade
                this@Column.AnimatedVisibility(
                    visible = activeFloatingEmote != null,
                    enter = fadeIn(tween(200)) + scaleIn(tween(250)) + slideInVertically { it / 2 },
                    exit = fadeOut(tween(300)) + scaleOut(tween(250)) + slideOutVertically { -it / 2 }
                ) {
                    val emote = activeFloatingEmote
                    if (emote != null) {
                        FloatingEmoteBubble(event = emote)
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            if (state.players.size == 2) {
                // 4. Dedicated 2-Player Arcade Bottom Bar (Matching Reference Image #1)
                TwoPlayerArcadeBottomBar(
                    state = state,
                    isRolling = isRollingAnimation,
                    diceSkin = diceSkin,
                    onRollDice = {
                        scope.launch {
                            isRollingAnimation = true
                            client.rollDice()
                            delay(280)
                            isRollingAnimation = false
                        }
                    }
                )
            } else {
                // 4-Player Bottom Plates
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val pBottomLeft = if (state.players.size >= 4) state.players[3] else state.players[2]
                    val idxLeft = if (state.players.size >= 4) 3 else 2
                    PlayerPlate(
                        player = pBottomLeft,
                        isActiveTurn = state.activePlayerIndex == idxLeft,
                        turnProgress = if (state.activePlayerIndex == idxLeft) timerProgress.value else 1.0f,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    if (state.players.size >= 4) {
                        PlayerPlate(
                            player = state.players[2],
                            isActiveTurn = state.activePlayerIndex == 2,
                            turnProgress = if (state.activePlayerIndex == 2) timerProgress.value else 1.0f,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 4-Player Turn Action Guidance & 3D Dice Tray
                TurnActionTray(
                    state = state,
                    isRolling = isRollingAnimation,
                    diceSkin = diceSkin,
                    onRollDice = {
                        scope.launch {
                            isRollingAnimation = true
                            client.rollDice()
                            delay(280)
                            isRollingAnimation = false
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Quick Emote & Chat Picker Overlay
        if (showEmotePicker) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { showEmotePicker = false },
                contentAlignment = Alignment.BottomCenter
            ) {
                QuickEmotePicker(
                    onSelectEmote = { emote ->
                        scope.launch {
                            client.sendEmote(emote)
                            showEmotePicker = false
                        }
                    },
                    onSelectChat = { msg ->
                        scope.launch {
                            client.sendChat(msg)
                            showEmotePicker = false
                        }
                    },
                    modifier = Modifier.padding(bottom = 130.dp)
                )
            }
        }

        // In-Game Quick Settings Dialog
        if (showSettingsDialog) {
            InGameQuickSettingsDialog(
                currentTheme = boardTheme,
                onThemeSelected = onUpdateTheme,
                soundController = soundController,
                onSurrenderClick = {
                    showSettingsDialog = false
                    showSurrenderDialog = true
                },
                onDismiss = { showSettingsDialog = false }
            )
        }

        // Surrender Confirmation Dialog
        if (showSurrenderDialog) {
            AlertDialog(
                onDismissRequest = { showSurrenderDialog = false },
                title = { Text(text = "Leave Match?", color = Color.White, fontWeight = FontWeight.Bold) },
                text = { Text(text = "Are you sure you want to forfeit this match and return to the main menu?", color = palette.textSecondary) },
                confirmButton = {
                    TextButton(onClick = {
                        showSurrenderDialog = false
                        scope.launch { client.leaveRoom() }
                        onExitGame()
                    }) {
                        Text(text = "Leave", color = NeoLudoColors.RubyRed, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSurrenderDialog = false }) {
                        Text(text = "Resume", color = Color.White)
                    }
                },
                containerColor = palette.cardSurface
            )
        }
    }
}

@Composable
private fun GameTopHud(
    onSurrenderClick: () -> Unit,
    onEmoteClick: () -> Unit,
    onSettingsClick: () -> Unit,
    soundController: SoundController
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onSurrenderClick,
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(NeoLudoColors.ObsidianSurfaceCard)
                .border(1.dp, NeoLudoColors.ObsidianBorder, CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.Flag,
                contentDescription = "Surrender",
                tint = NeoLudoColors.RubyRed,
                modifier = Modifier.size(20.dp)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(
                onClick = onEmoteClick,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(NeoLudoColors.ObsidianSurfaceCard)
                    .border(1.dp, NeoLudoColors.ObsidianBorder, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.AddReaction,
                    contentDescription = "Emotes",
                    tint = NeoLudoColors.AmberYellow,
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(NeoLudoColors.ObsidianSurfaceCard)
                    .border(1.dp, NeoLudoColors.ObsidianBorder, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = NeoLudoColors.CobaltBlue,
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(
                onClick = { soundController.toggleSound() },
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(NeoLudoColors.ObsidianSurfaceCard)
                    .border(1.dp, NeoLudoColors.ObsidianBorder, CircleShape)
            ) {
                Icon(
                    imageVector = if (soundController.isSoundEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeMute,
                    contentDescription = "Mute",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun TurnActionTray(
    state: com.neoludo.game.engine.model.GameState,
    isRolling: Boolean,
    diceSkin: DiceSkin,
    onRollDice: () -> Unit
) {
    val active = state.activePlayer
    val playerColor = NeoLudoColors.getPlayerColor(active.color)

    val promptTitle = when {
        active.isBot -> "${active.name}'s Turn"
        else -> "Your Turn • ${active.name}"
    }

    val promptInstruction = when (state.turnPhase) {
        TurnPhase.WAITING_FOR_ROLL -> {
            if (active.isBot) "Rolling the dice..." else "Tap Dice to Roll!"
        }
        TurnPhase.WAITING_FOR_MOVE -> {
            val diceVal = state.diceState.value
            val stepWord = if (diceVal == 1) "1 step" else "$diceVal steps"
            if (active.isBot) "${active.name} advancing $stepWord" else "Rolled $diceVal • Tap glowing piece to advance $stepWord"
        }
        TurnPhase.AUTO_ADVANCING -> "Advancing piece..."
        TurnPhase.GAME_OVER -> "Game Over!"
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Player Turn Badge Capsule with Consecutive Sixes warning & Bonus indicators
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = NeoLudoColors.ObsidianSurfaceCard,
            border = androidx.compose.foundation.BorderStroke(1.5.dp, playerColor.copy(alpha = 0.7f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(playerColor)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = promptTitle,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )

                // Consecutive Sixes indicator dots
                if (state.diceState.consecutiveSixes > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (i in 1..2) {
                            val isFilled = i <= state.diceState.consecutiveSixes
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (isFilled) NeoLudoColors.AmberYellow else NeoLudoColors.ObsidianBorder)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Action Guidance Text
        Text(
            text = promptInstruction,
            color = if (!active.isBot && state.turnPhase == TurnPhase.WAITING_FOR_ROLL) NeoLudoColors.AmberYellow else NeoLudoColors.ObsidianTextSecondary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 3D Animated Dice with Selected Skin
        Dice3DRenderer(
            diceState = state.diceState,
            playerColor = active.color,
            isRolling = isRolling,
            skin = diceSkin,
            onRollClick = onRollDice,
            sizeDp = 78.dp
        )
    }
}

@Composable
private fun TwoPlayerArcadeBottomBar(
    state: com.neoludo.game.engine.model.GameState,
    isRolling: Boolean,
    diceSkin: DiceSkin,
    onRollDice: () -> Unit
) {
    val p1 = state.players.getOrNull(0) ?: return
    val p2 = state.players.getOrNull(1) ?: return
    val isP1Turn = state.activePlayerIndex == 0
    val isP2Turn = state.activePlayerIndex == 1

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp)),
        color = Color(0xFF003F8A),
        border = androidx.compose.foundation.BorderStroke(2.5.dp, Color(0xFFFFD54F)),
        shape = RoundedCornerShape(22.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Player 1 (Left Side - "You")
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isP1Turn) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFFD54F).copy(alpha = 0.3f))
                                .border(2.dp, Color(0xFFFFD54F), CircleShape)
                        )
                    }
                    MiniMapPinIcon(
                        color = NeoLudoColors.getPlayerColor(p1.color),
                        sizeDp = 36.dp
                    )
                }

                Column {
                    Text(
                        text = if (p1.isBot) p1.name else "You",
                        color = if (isP1Turn) Color(0xFFFFD54F) else Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp
                    )
                    if (isP1Turn) {
                        Text(
                            text = if (state.turnPhase == TurnPhase.WAITING_FOR_ROLL) "Tap Dice" else "Move",
                            color = NeoLudoColors.EmeraldGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Center Embedded Die Panel
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .padding(horizontal = 6.dp),
                color = Color(0xFFFBE9E7),
                border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFFFCCBC)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(
                    modifier = Modifier.padding(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Dice3DRenderer(
                        diceState = state.diceState,
                        playerColor = state.activePlayer.color,
                        isRolling = isRolling,
                        skin = diceSkin,
                        onRollClick = onRollDice,
                        sizeDp = 64.dp
                    )
                }
            }

            // Player 2 (Right Side - "Com")
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.weight(1f)
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (p2.isBot) "Com" else p2.name,
                        color = if (isP2Turn) Color(0xFFFFD54F) else Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp
                    )
                    if (isP2Turn) {
                        Text(
                            text = if (state.turnPhase == TurnPhase.WAITING_FOR_ROLL) "Rolling..." else "Moving",
                            color = NeoLudoColors.EmeraldGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Box(contentAlignment = Alignment.Center) {
                    if (isP2Turn) {
                        Box(
                            modifier = Modifier
                                 .size(46.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFFD54F).copy(alpha = 0.3f))
                                .border(2.dp, Color(0xFFFFD54F), CircleShape)
                        )
                    }
                    MiniMapPinIcon(
                        color = NeoLudoColors.getPlayerColor(p2.color),
                        sizeDp = 36.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniMapPinIcon(
    color: Color,
    sizeDp: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.Canvas(modifier = modifier.size(sizeDp)) {
        val w = size.width
        val h = size.height
        val pinTop = Offset(w / 2f, h * 0.38f)
        val pinBottom = Offset(w / 2f, h * 0.95f)
        val headR = w * 0.36f

        // Base disc
        drawCircle(
            color = color,
            radius = w * 0.36f,
            center = Offset(w / 2f, h * 0.82f)
        )
        drawCircle(
            color = Color.White,
            radius = w * 0.36f,
            center = Offset(w / 2f, h * 0.82f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
        )

        // White Pin Body
        val pinPath = androidx.compose.ui.graphics.Path().apply {
            moveTo(pinTop.x, pinTop.y - headR)
            cubicTo(
                pinTop.x + headR * 1.05f, pinTop.y - headR,
                pinTop.x + headR * 1.05f, pinTop.y + headR * 0.4f,
                pinBottom.x, pinBottom.y
            )
            cubicTo(
                pinTop.x - headR * 1.05f, pinTop.y + headR * 0.4f,
                pinTop.x - headR * 1.05f, pinTop.y - headR,
                pinTop.x, pinTop.y - headR
            )
            close()
        }
        drawPath(pinPath, Color.White)
        drawPath(pinPath, Color(0xFF263238), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))

        // Colored Core
        drawCircle(color, headR * 0.55f, pinTop)
        drawCircle(Color.White, headR * 0.18f, Offset(pinTop.x - headR * 0.2f, pinTop.y - headR * 0.2f))
    }
}
@Composable
private fun QuickEmotePicker(
    onSelectEmote: (String) -> Unit,
    onSelectChat: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val emotes = listOf("🔥", "😎", "😂", "😭", "⚡", "🎉", "💀", "👑")
    val quickChats = listOf("Good Luck!", "Well Played!", "Nice move!", "Oops!", "Hurry up!", "GG!")

    Surface(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(22.dp))
            .border(1.5.dp, NeoLudoColors.ObsidianBorder, RoundedCornerShape(22.dp)),
        color = NeoLudoColors.ObsidianSurfaceCard
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "REACTIONS & CHAT",
                color = NeoLudoColors.ObsidianTextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(10.dp))

            // 8 Animated Reaction Emojis
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                emotes.forEach { emote ->
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(NeoLudoColors.ObsidianSurface)
                            .clickable { onSelectEmote(emote) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = emote, fontSize = 20.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 6 Tactical Chat Pills
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                quickChats.take(3).forEach { msg ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(NeoLudoColors.ObsidianSurface)
                            .clickable { onSelectChat(msg) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = msg,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                quickChats.drop(3).forEach { msg ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(NeoLudoColors.ObsidianSurface)
                            .clickable { onSelectChat(msg) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = msg,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FloatingEmoteBubble(event: ChatEvent) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = NeoLudoColors.getPlayerContainer(event.senderColor),
        border = androidx.compose.foundation.BorderStroke(2.dp, NeoLudoColors.getPlayerColor(event.senderColor))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = event.emoteId ?: event.message ?: "",
                fontSize = if (event.emoteId != null) 36.sp else 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}
@Composable
private fun InGameQuickSettingsDialog(
    currentTheme: BoardTheme,
    onThemeSelected: (BoardTheme) -> Unit,
    soundController: SoundController,
    onSurrenderClick: () -> Unit,
    onDismiss: () -> Unit
) {
    var soundVolume by remember { mutableFloatStateOf(soundController.soundVolume) }
    var soundEnabled by remember { mutableStateOf(soundController.soundEnabled) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Settings, contentDescription = null, tint = NeoLudoColors.CobaltBlue)
                Spacer(modifier = Modifier.width(8.dp))
                Text("In-Match Settings", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column {
                Text(
                    text = "BOARD THEME",
                    color = NeoLudoColors.ObsidianTextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BoardTheme.values().forEach { theme ->
                        val isSelected = theme == currentTheme
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) NeoLudoColors.CobaltBlue.copy(alpha = 0.3f) else NeoLudoColors.ObsidianSurface)
                                .border(
                                    1.2.dp,
                                    if (isSelected) NeoLudoColors.CobaltBlue else NeoLudoColors.ObsidianBorder,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { onThemeSelected(theme) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = theme.displayName.split(" ").first(),
                                color = if (isSelected) Color.White else NeoLudoColors.ObsidianTextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Sound Effects Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Sound Effects", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Switch(
                        checked = soundEnabled,
                        onCheckedChange = {
                            soundEnabled = it
                            soundController.soundEnabled = it
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = NeoLudoColors.EmeraldGreen
                        )
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Sound Effects Volume Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Volume", color = NeoLudoColors.ObsidianTextSecondary, fontSize = 12.sp)
                    Text("${(soundVolume * 100).toInt()}%", color = NeoLudoColors.EmeraldGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = soundVolume,
                    onValueChange = {
                        soundVolume = it
                        soundController.soundVolume = it
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = NeoLudoColors.EmeraldGreen,
                        activeTrackColor = NeoLudoColors.EmeraldGreen
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Leave Match button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(NeoLudoColors.RubyRed.copy(alpha = 0.15f))
                        .border(1.dp, NeoLudoColors.RubyRed.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .clickable { onSurrenderClick() }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Leave Match", color = NeoLudoColors.RubyRed, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = NeoLudoColors.CobaltBlue, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = NeoLudoColors.ObsidianSurfaceCard
    )
}
