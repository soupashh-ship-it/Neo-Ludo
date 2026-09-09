package com.neoludo.game.ui.friends

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neoludo.game.core.designsystem.AdaptiveContent
import com.neoludo.game.core.designsystem.NeoLudoButton
import com.neoludo.game.core.designsystem.NeoLudoSpacing
import com.neoludo.game.core.designsystem.ScreenHeader
import com.neoludo.game.core.designsystem.StadiumBackground
import com.neoludo.game.core.designsystem.StadiumColors
import com.neoludo.game.core.designsystem.TicketCard

/**
 * Production online-play hub.
 *
 * Older builds showed a local-only "friends" list that looked online and had an
 * unwired Invite button.  That was misleading: Neo Ludo's real invitation
 * mechanism is a private room code shared from the lobby.  This screen only
 * exposes functionality backed by the actual online-room transports.
 */
@Composable
fun FriendsScreen(
    onCreateRoom: () -> Unit,
    onJoinRoom: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    StadiumBackground(modifier = modifier) {
        AdaptiveContent {
            Spacer(modifier = Modifier.height(NeoLudoSpacing.md))
            ScreenHeader(title = "Play with friends", onBack = onBack)

            Spacer(modifier = Modifier.height(NeoLudoSpacing.xl))
            TicketCard(accent = StadiumColors.Accent) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(NeoLudoSpacing.md)
                ) {
                    Icon(
                        imageVector = Icons.Default.Groups,
                        contentDescription = null,
                        tint = StadiumColors.AccentBright,
                        modifier = Modifier.size(38.dp)
                    )
                    Text(
                        text = "Private online rooms",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = StadiumColors.TextPrimary
                    )
                    Text(
                        text = "Create a room and share its code with up to three friends, or join a code they sent you. Phones do not need to be on the same Wi-Fi.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = StadiumColors.TextSecondary
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(NeoLudoSpacing.sm)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            tint = StadiumColors.AccentBright,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "The lobby has a Share Code button.",
                            style = MaterialTheme.typography.bodySmall,
                            color = StadiumColors.TextPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(NeoLudoSpacing.xl))
            NeoLudoButton(
                text = "Create Private Room",
                onClick = onCreateRoom,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(NeoLudoSpacing.md))
            NeoLudoButton(
                text = "Join With Room Code",
                onClick = onJoinRoom,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Login,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(NeoLudoSpacing.xxxl))
        }
    }
}
