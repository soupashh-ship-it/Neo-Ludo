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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddReaction
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
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
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import com.neoludo.game.engine.rules.MoveValidator
import com.neoludo.game.engine.coordinate.BoardCoordinates
import com.neoludo.game.engine.model.PiecePosition
import com.neoludo.game.engine.ai.Difficulty
import com.neoludo.game.engine.ai.LudoBotEngine
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
    reducedMotion: Boolean = false,
    onUpdateTheme: (BoardTheme) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val gameState by client.gameState.collectAsState()
    val chatEvents by client.chatEvents.collectAsState(initial = null)
    val connectionState by client.connectionState.collectAsState()
    val scope = rememberCoroutineScope()
    // Clean up MultiplayerClient when GameScreen is disposed (navigated away)
    DisposableEffect(client) {
        onDispose {
            client.release()
        }
    }

    var showSurrenderDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showEmotePicker by remember { mutableStateOf(false) }
    var activeFloatingEmote by remember { mutableStateOf<ChatEvent?>(null) }
    var isRollingAnimation by remember { mutableStateOf(false) }
    var isExecutingMove by remember { mutableStateOf(false) }
    var totalCaptures by remember { mutableIntStateOf(0) }
    var totalSixes by remember { mutableIntStateOf(0) }

    // System back during a live match asks to surrender first — no silent abandon.
    BackHandler(enabled = gameState?.isGameOver == false) {
        showSurrenderDialog = true
    }

    // Turn timer progress
    val timerProgress = remember { Animatable(1f) }
    var autoActedNotice by remember { mutableStateOf(false) }
    // Last tap rejection ("not your turn", link hiccup) — shown briefly so
    // taps never die silently.
    var actionError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(actionError) {
        if (actionError != null) {
            delay(2500)
            actionError = null
        }
    }

    // Listen to active turn changes to reset timer animation and handle AFK timeout
    LaunchedEffect(gameState?.activePlayerIndex, gameState?.diceState?.value, gameState?.turnPhase) {
        val currentTimer = gameState?.ruleSet?.turnTimerSeconds ?: 30
        autoActedNotice = false
        timerProgress.snapTo(1f)
        // Run the countdown animation concurrently with the AFK wait below.
        launch {
            timerProgress.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = currentTimer * 1000, easing = LinearEasing)
            )
        }
        // Auto-action only AFTER the full turn timeout (AFK), not instantly —
        // previous code auto-rolled/moved immediately, robbing the human turn.
        val current = gameState ?: return@LaunchedEffect
        val isLocalActive = when (client) {
            is com.neoludo.game.multiplayer.FirebaseMultiplayerClient -> client.currentUid == current.activePlayer.id
            else -> !current.activePlayer.isBot
        }
        if (!current.isGameOver && isLocalActive) {
            delay(currentTimer * 1000L)
            // Re-read: turn may have advanced while waiting.
            val latest = gameState
            if (latest == null || latest.isGameOver || latest.version != current.version) return@LaunchedEffect
            when (latest.turnPhase) {
                TurnPhase.WAITING_FOR_ROLL -> {
                    if (latest.diceState.canRoll && !isRollingAnimation) {
                        isRollingAnimation = true
                        autoActedNotice = true
                        client.rollDice()
                        delay(300)
                        isRollingAnimation = false
                    }
                }
                TurnPhase.WAITING_FOR_MOVE -> {
                    if (!isExecutingMove) {
                        val legalMoves = MoveValidator.getLegalMoves(latest.activePlayer, latest.diceState.value, latest.players)
                        if (legalMoves.isNotEmpty()) {
                            val bestMove = LudoBotEngine.pickBestMove(latest, Difficulty.EASY) ?: legalMoves.first().piece
                            isExecutingMove = true
                            autoActedNotice = true
                            client.movePiece(bestMove.id)
                            delay(300)
                            isExecutingMove = false
                        }
                    }
                }
                else -> Unit
            }
        }
    }

    // Turn notification sound when turn shifts to a human player
    LaunchedEffect(gameState?.activePlayerIndex) {
        val current = gameState ?: return@LaunchedEffect
        if (!current.isGameOver && !current.activePlayer.isBot && current.turnPhase == TurnPhase.WAITING_FOR_ROLL) {
            soundController.play(SoundEffect.TURN_NOTIFY)
            hapticController.perform(HapticType.LIGHT_TICK)
        }
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
                if (event.to is PiecePosition.Path && BoardCoordinates.isSafeCell(event.player, event.to.step)) {
                    soundController.play(SoundEffect.SAFE_ZONE)
                    hapticController.perform(HapticType.SUCCESS_DOUBLE)
                }
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

    // Auto-move single piece when ruleSet.autoMoveSinglePiece is enabled
    LaunchedEffect(gameState?.turnPhase, gameState?.activePlayerIndex, gameState?.diceState?.value, gameState?.diceState?.isRolled) {
        val current = gameState ?: return@LaunchedEffect
        if (!current.isGameOver && !current.activePlayer.isBot && current.turnPhase == TurnPhase.WAITING_FOR_MOVE && current.ruleSet.autoMoveSinglePiece) {
            val legalMoves = MoveValidator.getLegalMoves(current.activePlayer, current.diceState.value, current.players)
            if (legalMoves.size == 1 && !isExecutingMove) {
                delay(350)
                isExecutingMove = true
                client.movePiece(legalMoves.first().piece.id)
                delay(300)
                isExecutingMove = false
            }
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
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // 1. Top HUD
            GameTopHud(
                connectionState = connectionState,
                onSurrenderClick = { showSurrenderDialog = true },
                onEmoteClick = { showEmotePicker = !showEmotePicker },
                onSettingsClick = { showSettingsDialog = true },
                soundController = soundController
            )
            Spacer(modifier = Modifier.height(10.dp))

            if (actionError != null) {
                Text(
                    text = actionError ?: "",
                    color = Color(0xFFFCA5A5),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFEF4444).copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

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
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = "Ludo board. ${state.activePlayer.name}'s turn. " +
                            "Dice showing ${state.diceState.value}. " +
                            "Phase: ${state.turnPhase}. Tap a glowing piece to move."
                    },
                contentAlignment = Alignment.Center
            ) {
                CanvasLudoBoard(
                    gameState = state,
                    onPieceClick = { pieceId ->
                        if (!isExecutingMove && !state.isGameOver && !state.activePlayer.isBot && state.turnPhase == TurnPhase.WAITING_FOR_MOVE) {
                            scope.launch {
                                isExecutingMove = true
                                client.movePiece(pieceId).onFailure { actionError = it.message }
                                delay(300)
                                isExecutingMove = false
                            }
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
                    motionEnabled = !reducedMotion,
                    onRollDice = {
                        if (!isRollingAnimation && !state.isGameOver && !state.activePlayer.isBot && state.turnPhase == TurnPhase.WAITING_FOR_ROLL && state.diceState.canRoll) {
                            scope.launch {
                                isRollingAnimation = true
                                soundController.play(SoundEffect.BUTTON_CLICK)
                                hapticController.perform(HapticType.MEDIUM_CLICK)
                                client.rollDice().onFailure { actionError = it.message }
                                delay(300)
                                isRollingAnimation = false
                            }
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
                    secondsLeft = ((state.ruleSet.turnTimerSeconds * timerProgress.value).toInt().coerceIn(0, state.ruleSet.turnTimerSeconds)),
                    autoActed = autoActedNotice,
                    motionEnabled = !reducedMotion,
                    onRollDice = {
                        if (!isRollingAnimation && !state.isGameOver && !state.activePlayer.isBot && state.turnPhase == TurnPhase.WAITING_FOR_ROLL && state.diceState.canRoll) {
                            scope.launch {
                                isRollingAnimation = true
                                soundController.play(SoundEffect.BUTTON_CLICK)
                                hapticController.perform(HapticType.MEDIUM_CLICK)
                                client.rollDice().onFailure { actionError = it.message }
                                delay(300)
                                isRollingAnimation = false
                            }
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
    connectionState: com.neoludo.game.multiplayer.model.ConnectionState,
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
                .size(48.dp)
                .clip(CircleShape)
                .background(NeoLudoColors.BrutalistInkSoft)
                .border(2.dp, NeoLudoColors.BrutalistLine, CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.Flag,
                contentDescription = "Surrender",
                tint = NeoLudoColors.BrutalistRed,
                modifier = Modifier.size(20.dp)
            )
        }
        if (connectionState == com.neoludo.game.multiplayer.model.ConnectionState.RECONNECTING) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = NeoLudoColors.AmberYellow.copy(alpha = 0.2f),
                border = androidx.compose.foundation.BorderStroke(1.dp, NeoLudoColors.AmberYellow)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    CircularProgressIndicator(
                        color = NeoLudoColors.AmberYellow,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Reconnecting...",
                        color = NeoLudoColors.AmberYellow,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(
                onClick = onEmoteClick,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(NeoLudoColors.BrutalistInkSoft)
                    .border(2.dp, NeoLudoColors.BrutalistLine, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.AddReaction,
                    contentDescription = "Emotes",
                    tint = NeoLudoColors.BrutalistAmber,
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(NeoLudoColors.BrutalistInkSoft)
                    .border(2.dp, NeoLudoColors.BrutalistLine, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = NeoLudoColors.BrutalistBlue,
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(
                onClick = { soundController.toggleSound() },
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(NeoLudoColors.BrutalistInkSoft)
                    .border(2.dp, NeoLudoColors.BrutalistLine, CircleShape)
            ) {
                Icon(
                    imageVector = if (soundController.isSoundEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeMute,
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
    onRollDice: () -> Unit,
    secondsLeft: Int = -1,
    autoActed: Boolean = false,
    motionEnabled: Boolean = true
) {
    val active = state.activePlayer
    val playerColor = NeoLudoColors.getBrutalistPlayerColor(active.color)

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

    // Urgency color for the countdown: paper → amber (<10s) → red (<5s).
    val timerColor = when {
        secondsLeft in 0..5 -> NeoLudoColors.BrutalistRed
        secondsLeft in 6..10 -> NeoLudoColors.BrutalistAmber
        else -> NeoLudoColors.BrutalistTextMutedOnInk
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Player Turn Badge Capsule with Consecutive Sixes warning & Bonus indicators
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = NeoLudoColors.BrutalistInkSoft,
            border = androidx.compose.foundation.BorderStroke(2.dp, NeoLudoColors.BrutalistLine)
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

                // Countdown seconds with urgency color + screen-reader description.
                if (secondsLeft >= 0 && !state.isGameOver) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "00:${secondsLeft.toString().padStart(2, '0')}",
                        color = timerColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }

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
                                    .background(if (isFilled) NeoLudoColors.BrutalistAmber else NeoLudoColors.BrutalistLine)
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
            color = if (!active.isBot && state.turnPhase == TurnPhase.WAITING_FOR_ROLL) NeoLudoColors.BrutalistAmber else NeoLudoColors.BrutalistTextMutedOnInk,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp
        )
        if (autoActed) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Auto-played — you ran out of time",
                color = NeoLudoColors.BrutalistTextMutedOnInk,
                fontSize = 11.sp
            )
        }

        // Classic die — one look for everyone.
        val canInteract = !active.isBot && state.turnPhase == TurnPhase.WAITING_FOR_ROLL && state.diceState.canRoll && !isRolling
        ClassicDice(
            value = state.diceState.value,
            rolling = isRolling,
            enabled = canInteract,
            onClick = { if (canInteract) onRollDice() },
            sizeDp = 78.dp
        )
    }
}

@Composable
private fun TwoPlayerArcadeBottomBar(
    state: com.neoludo.game.engine.model.GameState,
    isRolling: Boolean,
    diceSkin: DiceSkin,
    onRollDice: () -> Unit,
    motionEnabled: Boolean = true
) {
    val p1 = state.players.getOrNull(0) ?: return
    val p2 = state.players.getOrNull(1) ?: return
    val isP1Turn = state.activePlayerIndex == 0
    val isP2Turn = state.activePlayerIndex == 1

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp)),
        color = NeoLudoColors.BrutalistInkSoft,
        border = androidx.compose.foundation.BorderStroke(2.dp, NeoLudoColors.BrutalistLine),
        shape = RoundedCornerShape(14.dp)
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
                    val canInteract = !state.activePlayer.isBot && state.turnPhase == TurnPhase.WAITING_FOR_ROLL && state.diceState.canRoll && !isRolling
                    ClassicDice(
                        value = state.diceState.value,
                        rolling = isRolling,
                        enabled = canInteract,
                        onClick = { if (canInteract) onRollDice() },
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
