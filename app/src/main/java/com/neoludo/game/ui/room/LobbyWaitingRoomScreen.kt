package com.neoludo.game.ui.room

import android.content.Intent
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

data class LobbySlot(
    val id: String,
    val name: String,
    val color: PlayerColor,
    val isHost: Boolean = false,
    val isReady: Boolean = false,
    val isFilled: Boolean = true
)

@Composable
fun LobbyWaitingRoomScreen(
    roomId: String,
    onStartGame: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var isSelfReady by remember { mutableStateOf(true) }

    // Mock lobby state for immediate interactive use
    val slots = remember(roomId, isSelfReady) {
        listOf(
            LobbySlot("p1", "You (Host)", PlayerColor.RED, isHost = true, isReady = isSelfReady, isFilled = true),
            LobbySlot("p2", "CyberDice", PlayerColor.GREEN, isHost = false, isReady = true, isFilled = true),
            LobbySlot("p3", "ShadowPawn", PlayerColor.YELLOW, isHost = false, isReady = false, isFilled = true),
            LobbySlot("p4", "Waiting...", PlayerColor.BLUE, isHost = false, isReady = false, isFilled = false)
        )
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
                    onClick = onBack,
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

            // Player Roster
            Text(
                text = "PLAYERS (3/4)",
                color = NeoLudoColors.ObsidianTextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                slots.forEach { slot ->
                    PlayerSlotCard(slot = slot)
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Ready & Start Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                NeoLudoButton(
                    text = if (isSelfReady) "Ready ✓" else "Not Ready",
                    accentColor = if (isSelfReady) NeoLudoColors.EmeraldGreen else Color.Gray,
                    onClick = { isSelfReady = !isSelfReady },
                    modifier = Modifier.weight(1f)
                )

                NeoLudoButton(
                    text = "Start Match",
                    accentColor = NeoLudoColors.CobaltBlue,
                    onClick = onStartGame,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(36.dp))
        }
    }
}

@Composable
private fun PlayerSlotCard(slot: LobbySlot) {
    val playerColor = NeoLudoColors.getPlayerColor(slot.color)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(
                1.dp,
                if (slot.isFilled) playerColor.copy(alpha = 0.4f) else NeoLudoColors.ObsidianBorder,
                RoundedCornerShape(16.dp)
            ),
        color = if (slot.isFilled) NeoLudoColors.ObsidianSurfaceCard else NeoLudoColors.ObsidianSurfaceCard.copy(alpha = 0.4f),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (slot.isFilled) playerColor else Color.DarkGray),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (slot.isFilled) slot.name.take(1).uppercase() else "?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = slot.name,
                    color = if (slot.isFilled) Color.White else NeoLudoColors.ObsidianTextMuted,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                if (slot.isHost) {
                    Text(
                        text = "Room Host",
                        color = NeoLudoColors.AmberYellow,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (slot.isFilled) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (slot.isReady) Icons.Default.CheckCircle else Icons.Default.HourglassEmpty,
                        contentDescription = "Status",
                        tint = if (slot.isReady) NeoLudoColors.EmeraldGreen else NeoLudoColors.AmberYellow,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (slot.isReady) "Ready" else "Waiting",
                        color = if (slot.isReady) NeoLudoColors.EmeraldGreen else NeoLudoColors.AmberYellow,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
