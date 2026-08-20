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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
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
import com.neoludo.game.multiplayer.MultiplayerClient
import com.neoludo.game.multiplayer.model.PlayerPresence
import com.neoludo.game.multiplayer.model.RoomStatus
import kotlinx.coroutines.launch

@Composable
fun LobbyWaitingRoomScreen(
    roomId: String,
    client: MultiplayerClient,
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
    val self = presences.find { it.id == localPlayerId }
    val isHost = self?.isHost == true || (presences.isNotEmpty() && presences.first().id == localPlayerId)
    val maxPlayers = roomState?.meta?.maxPlayers ?: 4
    val isSelfReady = self?.isReady ?: true

    // Auto-navigate to game when match starts on ANY connected device across the internet
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
                Text(
                    text = "Lobby Waiting Room",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Room Code Card with Copy & Share
            NeoLudoCard(modifier = Modifier.fillMaxWidth()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "ROOM CODE",
                        color = NeoLudoColors.ObsidianTextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = roomId,
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 3.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    clipboardManager.setText(AnnotatedString(roomId))
                                }
                                .border(1.dp, NeoLudoColors.ObsidianBorder, RoundedCornerShape(12.dp)),
                            color = NeoLudoColors.ObsidianSurface
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy",
                                    tint = NeoLudoColors.CobaltBlue,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Copy Code",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, "Join my NeoLudo match with code: $roomId")
                                        type = "text/plain"
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Share Room Code"))
                                }
                                .border(1.dp, NeoLudoColors.ObsidianBorder, RoundedCornerShape(12.dp)),
                            color = NeoLudoColors.ObsidianSurface
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Share",
                                    tint = NeoLudoColors.EmeraldGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Share Invite",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Player Roster Header
            val humanCount = presences.count { !it.isAi }
            Text(
                text = "PLAYERS ($humanCount / $maxPlayers)",
                color = NeoLudoColors.ObsidianTextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Player Slots
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (i in 0 until maxPlayers) {
                    val presence = presences.getOrNull(i)
                    PlayerPresenceSlotCard(
                        presence = presence,
                        isLocal = presence?.id == localPlayerId,
                        slotIndex = i
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!isHost) {
                    NeoLudoButton(
                        text = if (isSelfReady) "Ready ✓" else "Not Ready",
                        accentColor = if (isSelfReady) NeoLudoColors.EmeraldGreen else Color.Gray,
                        onClick = {
                            scope.launch { client.setReady(!isSelfReady) }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                if (isHost) {
                    NeoLudoButton(
                        text = "Start Match",
                        accentColor = NeoLudoColors.CobaltBlue,
                        onClick = {
                            scope.launch {
                                client.startMatch()
                                onStartGame()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(36.dp))
        }
    }
}

@Composable
private fun PlayerPresenceSlotCard(
    presence: PlayerPresence?,
    isLocal: Boolean,
    slotIndex: Int
) {
    val isFilled = presence != null
    val defaultColor = when (slotIndex) {
        0 -> PlayerColor.RED
        1 -> PlayerColor.GREEN
        2 -> PlayerColor.YELLOW
        else -> PlayerColor.BLUE
    }
    val playerColor = NeoLudoColors.getPlayerColor(presence?.color ?: defaultColor)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(
                1.dp,
                if (isFilled) playerColor.copy(alpha = 0.5f) else NeoLudoColors.ObsidianBorder,
                RoundedCornerShape(16.dp)
            ),
        color = if (isFilled) NeoLudoColors.ObsidianSurfaceCard else NeoLudoColors.ObsidianSurfaceCard.copy(alpha = 0.35f),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(if (isFilled) playerColor else Color.DarkGray),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isFilled) presence!!.name.take(1).uppercase() else "+",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isFilled) (if (isLocal) "${presence!!.name} (You)" else presence!!.name) else "Waiting for Friend...",
                    color = if (isFilled) Color.White else NeoLudoColors.ObsidianTextMuted,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                if (presence?.isHost == true) {
                    Text(
                        text = "Room Host 👑",
                        color = NeoLudoColors.AmberYellow,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                } else if (presence?.isAi == true) {
                    Text(
                        text = "AI Bot Filler 🤖",
                        color = NeoLudoColors.ObsidianTextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            if (isFilled) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (presence!!.isReady) Icons.Default.CheckCircle else Icons.Default.HourglassEmpty,
                        contentDescription = "Status",
                        tint = if (presence.isReady) NeoLudoColors.EmeraldGreen else NeoLudoColors.AmberYellow,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (presence.isReady) "Ready" else "Waiting",
                        color = if (presence.isReady) NeoLudoColors.EmeraldGreen else NeoLudoColors.AmberYellow,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
