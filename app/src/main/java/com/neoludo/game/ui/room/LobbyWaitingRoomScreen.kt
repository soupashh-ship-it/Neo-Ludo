package com.neoludo.game.ui.room

import android.content.Intent
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
import com.neoludo.game.core.designsystem.NeoLudoButton
import com.neoludo.game.core.designsystem.NeoLudoCard
import com.neoludo.game.core.designsystem.NeoLudoColors
import com.neoludo.game.engine.model.PlayerColor
import com.neoludo.game.multiplayer.OnlineRoomClient
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
    val presences = roomState?.players ?: emptyList()
    val meta = roomState?.meta
    val maxPlayers = meta?.maxPlayers ?: 4

    val self = presences.find { it.id == client.currentUid || it.id == localPlayerId }
    val isHost = meta?.hostId == client.currentUid || self?.isHost == true
    val isSelfReady = self?.isReady ?: true

    var isStarting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var copiedCodeToast by remember { mutableStateOf(false) }

    // Auto-navigate to game when match starts on ANY connected device
    LaunchedEffect(roomState?.meta?.status, roomState?.gameState) {
        if (roomState?.meta?.status == RoomStatus.IN_GAME || roomState?.gameState != null) {
            onStartGame()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NeoLudoColors.ObsidianBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(36.dp))

            // Top Bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(
                    onClick = {
                        scope.launch { client.leaveRoom() }
                        onBack()
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(NeoLudoColors.ObsidianSurfaceCard)
                        .border(1.dp, NeoLudoColors.ObsidianBorder, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Leave",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "Lobby Waiting Room",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = "${presences.size} / $maxPlayers Players Joined",
                        color = NeoLudoColors.ObsidianTextMuted,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Room Code Card
            NeoLudoCard(modifier = Modifier.fillMaxWidth()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "ROOM CODE",
                        color = NeoLudoColors.ObsidianTextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
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
                            color = if (copiedCodeToast) NeoLudoColors.EmeraldGreen.copy(alpha = 0.2f) else Color(0xFF1E293B),
                            border = BorderStroke(1.dp, if (copiedCodeToast) NeoLudoColors.EmeraldGreen else Color(0xFF334155))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = if (copiedCodeToast) Icons.Default.Check else Icons.Default.ContentCopy,
                                    contentDescription = "Copy",
                                    tint = if (copiedCodeToast) NeoLudoColors.EmeraldGreen else Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (copiedCodeToast) "Copied!" else "Copy Code",
                                    color = if (copiedCodeToast) NeoLudoColors.EmeraldGreen else Color.White,
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
                            color = NeoLudoColors.CobaltBlue,
                            border = BorderStroke(1.dp, Color(0xFF60A5FA))
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

            Spacer(modifier = Modifier.height(20.dp))

            if (errorMessage != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFEF4444).copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, Color(0xFFEF4444)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errorMessage ?: "",
                        color = Color(0xFFFCA5A5),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Player Slots
            Text(
                text = "PLAYERS ($maxPlayers SLOTS)",
                color = NeoLudoColors.ObsidianTextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(10.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Occupied Slots
                presences.forEach { player ->
                    LobbyPlayerCard(
                        player = player,
                        isLocal = player.id == client.currentUid || player.id == localPlayerId
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
                        .background(NeoLudoColors.ObsidianSurfaceCard)
                        .border(1.dp, NeoLudoColors.ObsidianBorder, RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.SmartToy,
                            contentDescription = null,
                            tint = NeoLudoColors.EmeraldGreen,
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
                        colors = SwitchDefaults.colors(checkedThumbColor = NeoLudoColors.EmeraldGreen)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Bottom Action
            if (isHost) {
                val canStart = presences.size >= 2 || (meta?.fillBots == true && presences.isNotEmpty())
                if (isStarting) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = NeoLudoColors.EmeraldGreen)
                    }
                } else {
                    NeoLudoButton(
                        text = if (canStart) "Start Game" else "Waiting for Players...",
                        accentColor = NeoLudoColors.EmeraldGreen,
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
                    accentColor = if (isSelfReady) NeoLudoColors.EmeraldGreen else NeoLudoColors.CobaltBlue,
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
        color = NeoLudoColors.ObsidianSurfaceCard,
        border = BorderStroke(1.dp, if (isLocal) playerColor else NeoLudoColors.ObsidianBorder)
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
                        if (player.isHost) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = NeoLudoColors.AmberYellow.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "HOST",
                                    color = NeoLudoColors.AmberYellow,
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
                        color = if (!player.isConnected) Color(0xFFEF4444) else NeoLudoColors.ObsidianTextMuted
                    )
                }
            }

            // Ready Badge
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (player.isReady) NeoLudoColors.EmeraldGreen.copy(alpha = 0.15f) else Color(0xFF334155).copy(alpha = 0.3f),
                border = BorderStroke(1.dp, if (player.isReady) NeoLudoColors.EmeraldGreen else Color(0xFF475569))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = if (player.isReady) Icons.Default.CheckCircle else Icons.Default.HourglassEmpty,
                        contentDescription = null,
                        tint = if (player.isReady) NeoLudoColors.EmeraldGreen else Color.Gray,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (player.isReady) "READY" else "WAITING",
                        color = if (player.isReady) NeoLudoColors.EmeraldGreen else Color.Gray,
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
        color = Color(0xFF0F172A).copy(alpha = 0.5f),
        border = BorderStroke(1.dp, Color(0xFF1E293B))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = Color(0xFF1E293B),
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = Color(0xFF475569),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = "Slot $slotIndex: Waiting for player...",
                color = Color(0xFF64748B),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
