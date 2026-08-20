package com.neoludo.game.ui.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.InsertEmoticon
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoludo.game.core.audio.HapticController
import com.neoludo.game.core.audio.HapticType
import com.neoludo.game.core.audio.SoundController
import com.neoludo.game.core.audio.SoundEffect
import com.neoludo.game.core.designsystem.NeoLudoColors
import com.neoludo.game.core.designsystem.PlayerPlate
import com.neoludo.game.engine.model.GameEngineEvent
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
    modifier: Modifier = Modifier
) {
    val gameState by client.gameState.collectAsState()
    val chatEvents by client.chatEvents.collectAsState(initial = null)
    val scope = rememberCoroutineScope()

    var showSurrenderDialog by remember { mutableStateOf(false) }
    var showEmotePicker by remember { mutableStateOf(false) }
    var activeFloatingEmote by remember { mutableStateOf<ChatEvent?>(null) }
    var isRollingAnimation by remember { mutableStateOf(false) }

    var totalCaptures by remember { mutableIntStateOf(0) }
    var totalSixes by remember { mutableIntStateOf(0) }

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
                soundController.play(SoundEffect.PIECE_STEP)
                hapticController.perform(HapticType.LIGHT_TICK)
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
        delay(2500)
        activeFloatingEmote = null
    }

    val state = gameState ?: return

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NeoLudoColors.ObsidianBackground)
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
                soundController = soundController
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Top Player Plates (Players 0 & 1 or Red & Green)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (state.players.isNotEmpty()) {
                    PlayerPlate(
                        player = state.players[0],
                        isActiveTurn = state.activePlayerIndex == 0,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                if (state.players.size > 1) {
                    PlayerPlate(
                        player = state.players[1],
                        isActiveTurn = state.activePlayerIndex == 1,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // 3. Canvas Ludo Game Board
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
                    modifier = Modifier.fillMaxWidth()
                )

                // Floating Emote Display
                this@Column.AnimatedVisibility(
                    visible = activeFloatingEmote != null,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut()
                ) {
                    val emote = activeFloatingEmote
                    if (emote != null) {
                        FloatingEmoteBubble(event = emote)
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // 4. Bottom Player Plates (Players 3 & 2 or Blue & Yellow)
            if (state.players.size > 2) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val pBottomLeft = if (state.players.size >= 4) state.players[3] else state.players[2]
                    PlayerPlate(
                        player = pBottomLeft,
                        isActiveTurn = state.activePlayerIndex == (if (state.players.size >= 4) 3 else 2),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    if (state.players.size >= 4) {
                        PlayerPlate(
                            player = state.players[2],
                            isActiveTurn = state.activePlayerIndex == 2,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 5. Turn Action Guidance & 3D Dice Tray
            TurnActionTray(
                state = state,
                isRolling = isRollingAnimation,
                onRollDice = {
                    scope.launch {
                        isRollingAnimation = true
                        client.rollDice()
                        delay(250)
                        isRollingAnimation = false
                    }
                }
            )

            Spacer(modifier = Modifier.height(28.dp))
        }

        // Quick Emote Picker Overlay
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
                    modifier = Modifier.padding(bottom = 120.dp)
                )
            }
        }

        // Surrender Confirmation Dialog
        if (showSurrenderDialog) {
            AlertDialog(
                onDismissRequest = { showSurrenderDialog = false },
                title = { Text(text = "Leave Match?", color = Color.White, fontWeight = FontWeight.Bold) },
                text = { Text(text = "Are you sure you want to forfeit this match and return to the main menu?", color = NeoLudoColors.ObsidianTextSecondary) },
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
                containerColor = NeoLudoColors.ObsidianSurfaceCard
            )
        }
    }
}

@Composable
private fun GameTopHud(
    onSurrenderClick: () -> Unit,
    onEmoteClick: () -> Unit,
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
                .size(40.dp)
                .clip(CircleShape)
                .background(NeoLudoColors.ObsidianSurfaceCard)
                .border(1.dp, NeoLudoColors.ObsidianBorder, CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Surrender",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(
                onClick = {
                    soundController.soundEnabled = !soundController.soundEnabled
                },
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(NeoLudoColors.ObsidianSurfaceCard)
                    .border(1.dp, NeoLudoColors.ObsidianBorder, CircleShape)
            ) {
                Icon(
                    imageVector = if (soundController.soundEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeMute,
                    contentDescription = "Sound",
                    tint = if (soundController.soundEnabled) NeoLudoColors.EmeraldGreen else Color.Gray,
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(
                onClick = onEmoteClick,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(NeoLudoColors.ObsidianSurfaceCard)
                    .border(1.dp, NeoLudoColors.ObsidianBorder, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.InsertEmoticon,
                    contentDescription = "Emotes",
                    tint = NeoLudoColors.AmberYellow,
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
    onRollDice: () -> Unit
) {
    val active = state.activePlayer
    val promptText = when {
        active.isBot -> "${active.name} is thinking..."
        state.turnPhase == TurnPhase.WAITING_FOR_ROLL -> "Your Turn • Tap Dice to Roll"
        state.turnPhase == TurnPhase.WAITING_FOR_MOVE -> "Select a highlighted piece"
        else -> ""
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = promptText,
            color = if (!active.isBot && state.turnPhase == TurnPhase.WAITING_FOR_ROLL) Color.White else NeoLudoColors.ObsidianTextSecondary,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        Dice3DRenderer(
            diceState = state.diceState,
            playerColor = active.color,
            isRolling = isRolling,
            onRollClick = onRollDice,
            sizeDp = 76.dp
        )
    }
}

@Composable
private fun QuickEmotePicker(
    onSelectEmote: (String) -> Unit,
    onSelectChat: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val emotes = listOf("🔥", "😂", "👏", "😭", "🎯", "👑")
    val quickChats = listOf("Good Luck!", "Nice move!", "GG", "Hurry up!")

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .border(1.5.dp, NeoLudoColors.ObsidianBorder, RoundedCornerShape(20.dp)),
        color = NeoLudoColors.ObsidianSurfaceCard
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Emote Emojis
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                emotes.forEach { emote ->
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(NeoLudoColors.ObsidianSurface)
                            .clickable { onSelectEmote(emote) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = emote, fontSize = 22.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Quick Chat Messages
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                quickChats.forEach { msg ->
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
                            fontSize = 12.sp,
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
        shape = RoundedCornerShape(16.dp),
        color = NeoLudoColors.getPlayerContainer(event.senderColor),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, NeoLudoColors.getPlayerColor(event.senderColor))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = event.emoteId ?: event.message ?: "",
                fontSize = if (event.emoteId != null) 32.sp else 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}
