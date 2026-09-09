package com.neoludo.game.ui.room

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoludo.game.core.designsystem.AdaptiveScroll
import com.neoludo.game.core.designsystem.NeoLudoButton
import com.neoludo.game.core.designsystem.NeoLudoCard
import com.neoludo.game.core.designsystem.NeoLudoColors
import com.neoludo.game.core.designsystem.NeoLudoSectionLabel
import com.neoludo.game.core.designsystem.NeoLudoSpacing
import com.neoludo.game.core.designsystem.ScreenHeader
import com.neoludo.game.core.designsystem.StadiumBackground
import com.neoludo.game.core.designsystem.StadiumColors
import com.neoludo.game.engine.model.PlayerColor
import com.neoludo.game.multiplayer.OnlineRoomClient
import com.neoludo.game.multiplayer.model.ConnectionState
import com.neoludo.game.multiplayer.model.PlayerPresence
import com.neoludo.game.multiplayer.model.RoomStatus
import kotlinx.coroutines.launch

@Composable
fun LobbyWaitingRoomScreen(
    roomId: String,
    client: OnlineRoomClient,
    localPlayerId: String,
    onStartGame: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    val roomState by client.roomState.collectAsState()
    val connectionState by client.connectionState.collectAsState()
    val presences = roomState?.players ?: emptyList()
    val meta = roomState?.meta
    val maxPlayers = meta?.maxPlayers ?: 4

    val self = presences.find { it.id == client.currentUid || it.id == localPlayerId }
    val isHost = meta?.hostId == client.currentUid
    val isSelfReady = self?.isReady ?: true

    var isStarting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var copiedCodeToast by remember { mutableStateOf(false) }
    var isRetrying by remember { mutableStateOf(false) }
    var isLeaving by remember { mutableStateOf(false) }

    val leaveAndBack: () -> Unit = {
        if (!isLeaving) {
            isLeaving = true
            scope.launch {
                client.leaveRoom()
                onBack()
            }
        }
    }
    BackHandler { leaveAndBack() }

    // Auto-navigate to game when match starts on ANY connected device
    LaunchedEffect(roomState?.meta?.status, roomState?.gameState) {
        if (roomState?.meta?.status == RoomStatus.IN_GAME || roomState?.gameState != null) {
            onStartGame()
        }
    }

    StadiumBackground(modifier = modifier) {
        AdaptiveScroll {
            Spacer(modifier = Modifier.height(NeoLudoSpacing.md))

            ScreenHeader(title = "Lobby · ${presences.size} of $maxPlayers", onBack = leaveAndBack)

            Spacer(modifier = Modifier.height(20.dp))

            // Relay connection banner: backgrounding the app (e.g. to share
            // the code) can drop the socket; auto-retry runs, this explains
            // the state and offers a manual retry.
            if (connectionState == ConnectionState.RECONNECTING ||
                connectionState == ConnectionState.DISCONNECTED ||
                connectionState == ConnectionState.CONNECTING
            ) {
                val isDown = connectionState == ConnectionState.DISCONNECTED
                NeoLudoCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isRetrying || !isDown) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = StadiumColors.Gold
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (isDown) "Disconnected from relay." else "Reconnecting to room relay…",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        if (isDown && !isRetrying) {
                            Text(
                                text = "RETRY",
                                color = StadiumColors.Gold,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.clickable {
                                    isRetrying = true
                                    scope.launch {
                                        val res = client.refreshConnection()
                                        isRetrying = false
                                        if (res.isFailure) {
                                            errorMessage = res.exceptionOrNull()?.message
                                                ?: "Still unreachable. Try again."
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Room Code Card
            NeoLudoCard(modifier = Modifier.fillMaxWidth()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    NeoLudoSectionLabel(text = "Room code")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = roomId,
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Copy Button
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    clipboardManager.setText(AnnotatedString(roomId))
                                    copiedCodeToast = true
                                },
                            shape = RoundedCornerShape(12.dp),
                            color = if (copiedCodeToast) StadiumColors.Success.copy(alpha = 0.2f) else StadiumColors.CardElevated,
                            border = BorderStroke(1.dp, if (copiedCodeToast) StadiumColors.Success else StadiumColors.Border)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = if (copiedCodeToast) Icons.Default.Check else Icons.Default.ContentCopy,
                                    contentDescription = "Copy",
                                    tint = if (copiedCodeToast) StadiumColors.Success else StadiumColors.TextPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (copiedCodeToast) "Copied!" else "Copy Code",
                                    color = if (copiedCodeToast) StadiumColors.Success else StadiumColors.TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Share Button
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(
                                            Intent.EXTRA_TEXT,
                                            "Join my NeoLudo game!\nRoom code: $roomId"
                                        )
                                        type = "text/plain"
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Share Room Code"))
                                },
                            shape = RoundedCornerShape(12.dp),
                            color = StadiumColors.Accent,
                            border = BorderStroke(1.dp, StadiumColors.AccentBright)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Share",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Share Code",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Diagnostics line: lets support compare identity + relay across
            // two phones when a join misbehaves.
            Text(
                text = "You: ${self?.name ?: "…"} • ${client.currentUid.takeLast(4)} • via ${client.transportDebug}",
                color = StadiumColors.TextMuted,
                fontSize = 11.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (errorMessage != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = StadiumColors.Danger.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, StadiumColors.Danger),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errorMessage ?: "",
                        color = StadiumColors.Danger,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Player Slots
            NeoLudoSectionLabel(text = "Players ($maxPlayers slots)")
            Spacer(modifier = Modifier.height(10.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Occupied Slots
                presences.forEach { player ->
                    LobbyPlayerCard(
                        player = player,
                        isLocal = player.id == client.currentUid || player.id == localPlayerId,
                        isHost = player.id == meta?.hostId
                    )
                }

                // Empty Slots
                val emptySlotsCount = (maxPlayers - presences.size).coerceAtLeast(0)
                repeat(emptySlotsCount) { idx ->
                    LobbyEmptySlotCard(slotIndex = presences.size + idx + 1)
                }
            }

            // Host Bot Toggle
            if (isHost && presences.size < maxPlayers) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(StadiumColors.Card)
                        .border(1.dp, StadiumColors.Border, RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.SmartToy,
                            contentDescription = null,
                            tint = StadiumColors.Success,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Fill Empty Seats with Bots", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("Start match without waiting for full room", color = NeoLudoColors.ObsidianTextMuted, fontSize = 11.sp)
                        }
                    }
                    Switch(
                        checked = meta?.fillBots ?: false,
                        onCheckedChange = { fill ->
                            scope.launch { client.setFillBots(fill) }
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = StadiumColors.Accent)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Bottom Action
            if (isHost) {
                val connected = presences.filter { it.isConnected && !it.isAi }
                val allReady = connected.all { it.isReady }
                val canStart = if (meta?.fillBots == true) {
                    connected.isNotEmpty() && allReady
                } else {
                    connected.size == maxPlayers && allReady
                }
                if (isStarting) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = StadiumColors.Accent)
                    }
                } else {
                    NeoLudoButton(
                        text = if (canStart) "Start Game" else "Waiting for Players...",
                        enabled = canStart,
                        onClick = {
                            isStarting = true
                            errorMessage = null
                            scope.launch {
                                val result = client.startMatch()
                                isStarting = false
                                result.onFailure {
                                    errorMessage = it.message ?: "Failed to start game"
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                // Guest Ready Toggle
                NeoLudoButton(
                    text = if (isSelfReady) "You Are Ready (Tap to Unready)" else "Ready Up",
                    onClick = {
                        scope.launch { client.setReady(!isSelfReady) }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
fun LobbyPlayerCard(
    player: PlayerPresence,
    isLocal: Boolean,
    isHost: Boolean = player.isHost,
    modifier: Modifier = Modifier
) {
    val playerColor = when (player.color) {
        PlayerColor.RED -> NeoLudoColors.RubyRed
        PlayerColor.GREEN -> NeoLudoColors.EmeraldGreen
        PlayerColor.YELLOW -> NeoLudoColors.AmberYellow
        PlayerColor.BLUE -> NeoLudoColors.CobaltBlue
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = StadiumColors.Card,
        border = BorderStroke(1.dp, if (isLocal) playerColor else StadiumColors.Border)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Color Avatar
                Surface(
                    shape = CircleShape,
                    color = playerColor,
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = player.name.take(1).uppercase(),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = player.name + if (isLocal) " (You)" else "",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                        if (isHost) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = StadiumColors.Gold.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "HOST",
                                    color = StadiumColors.Gold,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = if (!player.isConnected) "Disconnected" else player.color.name,
                        fontSize = 11.sp,
                        color = if (!player.isConnected) StadiumColors.Danger else StadiumColors.TextMuted
                    )
                }
            }

            // Ready Badge
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (player.isReady) StadiumColors.Success.copy(alpha = 0.15f) else StadiumColors.CardElevated,
                border = BorderStroke(1.dp, if (player.isReady) StadiumColors.Success else StadiumColors.BorderBright)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = if (player.isReady) Icons.Default.CheckCircle else Icons.Default.HourglassEmpty,
                        contentDescription = null,
                        tint = if (player.isReady) StadiumColors.Success else StadiumColors.TextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (player.isReady) "READY" else "WAITING",
                        color = if (player.isReady) StadiumColors.Success else StadiumColors.TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun LobbyEmptySlotCard(slotIndex: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = StadiumColors.Card.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, StadiumColors.Border)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = StadiumColors.CardElevated,
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = StadiumColors.TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = "Slot $slotIndex: Waiting for player...",
                color = StadiumColors.TextMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
