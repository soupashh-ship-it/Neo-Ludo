package com.neoludo.game.multiplayer

import com.neoludo.game.engine.model.GameState
import com.neoludo.game.multiplayer.model.ChatEvent
import com.neoludo.game.multiplayer.model.ConnectionState
import com.neoludo.game.multiplayer.model.RoomSnapshot
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface MultiplayerClient {
    val roomState: StateFlow<RoomSnapshot?>
    val gameState: StateFlow<GameState?>
    val connectionState: StateFlow<ConnectionState>
    val chatEvents: SharedFlow<ChatEvent>

    suspend fun rollDice(): Result<Unit>
    suspend fun movePiece(pieceId: Int): Result<Unit>
    suspend fun sendChat(message: String): Result<Unit>
    suspend fun sendEmote(emoteId: String): Result<Unit>
    suspend fun setReady(isReady: Boolean): Result<Unit>
    suspend fun startMatch(): Result<Unit>
    suspend fun leaveRoom(): Result<Unit>
    fun release() {}
}
