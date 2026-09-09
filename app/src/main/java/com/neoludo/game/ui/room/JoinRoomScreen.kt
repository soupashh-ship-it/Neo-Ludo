package com.neoludo.game.ui.room

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
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoludo.game.core.designsystem.AdaptiveContent
import com.neoludo.game.core.designsystem.NeoLudoButton
import com.neoludo.game.core.designsystem.NeoLudoCard
import com.neoludo.game.core.designsystem.NeoLudoColors
import com.neoludo.game.core.designsystem.NeoLudoSectionLabel
import com.neoludo.game.core.designsystem.NeoLudoSpacing
import com.neoludo.game.core.designsystem.ScreenHeader
import com.neoludo.game.core.designsystem.StadiumBackground
import com.neoludo.game.core.designsystem.StadiumColors
import com.neoludo.game.multiplayer.FirebaseMultiplayerClient
import kotlinx.coroutines.launch

@Composable
fun JoinRoomScreen(
    onJoinRoom: suspend (roomId: String) -> Result<Unit>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var roomCodeInput by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    StadiumBackground(modifier = modifier) {
        AdaptiveContent {
            Spacer(modifier = Modifier.height(NeoLudoSpacing.md))

            ScreenHeader(title = "Join private room", onBack = onBack)

            Spacer(modifier = Modifier.height(NeoLudoSpacing.xxxl))

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
                Spacer(modifier = Modifier.height(NeoLudoSpacing.lg))
            }

            NeoLudoCard(modifier = Modifier.fillMaxWidth()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    NeoLudoSectionLabel(text = "6-digit room code")

                    Spacer(modifier = Modifier.height(NeoLudoSpacing.xl))

                    OutlinedTextField(
                        value = roomCodeInput,
                        onValueChange = { input ->
                            roomCodeInput = input.uppercase().take(12)
                            errorMessage = null
                        },
                        placeholder = {
                            Text(
                                text = "NL-XXXXXX",
                                color = StadiumColors.TextMuted.copy(alpha = 0.6f),
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                        },
                        textStyle = TextStyle(
                            color = StadiumColors.TextPrimary,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center,
                            letterSpacing = 2.sp
                        ),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = StadiumColors.Accent,
                            unfocusedBorderColor = StadiumColors.Border,
                            focusedContainerColor = StadiumColors.CardElevated,
                            unfocusedContainerColor = StadiumColors.CardElevated
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(NeoLudoSpacing.md))

                    // Paste Button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                val clip = clipboardManager.getText()?.text
                                if (!clip.isNullOrBlank()) {
                                    roomCodeInput = FirebaseMultiplayerClient.normalizeRoomCode(clip)
                                    errorMessage = null
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentPaste,
                            contentDescription = "Paste",
                            tint = StadiumColors.AccentBright,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Paste from Clipboard",
                            color = StadiumColors.AccentBright,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(NeoLudoSpacing.xxl))

            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = StadiumColors.Accent)
                }
            } else {
                NeoLudoButton(
                    text = "Join Room",
                    onClick = {
                        val normalized = FirebaseMultiplayerClient.normalizeRoomCode(roomCodeInput)
                        if (normalized.length < 5) {
                            errorMessage = "Please enter a valid room code (e.g. NL-XXXXXX)"
                            return@NeoLudoButton
                        }

                        isLoading = true
                        errorMessage = null
                        scope.launch {
                            val result = onJoinRoom(normalized)
                            isLoading = false
                            result.onFailure {
                                errorMessage = it.message ?: "Could not join this room. Check the code."
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
