package com.neoludo.game.multiplayer.repository

import com.neoludo.game.engine.model.GameState
import com.neoludo.game.multiplayer.backend.FirebaseAuthDataSource
import com.neoludo.game.multiplayer.backend.FirebaseRoomDataSource
import com.neoludo.game.multiplayer.model.ChatEvent
import com.neoludo.game.multiplayer.model.NetworkAction
import com.neoludo.game.multiplayer.model.NetworkEvent
import com.neoludo.game.multiplayer.model.PlayerPresence
import com.neoludo.game.multiplayer.model.RoomMetadata
import com.neoludo.game.multiplayer.model.RoomSnapshot
import com.neoludo.game.multiplayer.model.RoomStatus
import kotlinx.coroutines.flow.Flow

class RoomRepository(
    private val dataSource: FirebaseRoomDataSource
) {
    suspend fun createRoom(meta: RoomMetadata, host: PlayerPresence): Result<Unit> =
        dataSource.createRoom(meta, host)

    suspend fun joinRoom(roomId: String, player: PlayerPresence): Result<PlayerPresence> =
        dataSource.joinRoom(roomId, player)

    suspend fun updateRoomStatus(roomId: String, status: RoomStatus): Result<Unit> =
        dataSource.updateRoomStatus(roomId, status)

    suspend fun setFillBots(roomId: String, fillBots: Boolean): Result<Unit> =
        dataSource.setFillBots(roomId, fillBots)

    suspend fun leaveRoom(roomId: String, uid: String): Result<Unit> =
        dataSource.leaveRoom(roomId, uid)

    fun observeRoom(roomId: String): Flow<RoomSnapshot?> =
        dataSource.observeRoom(roomId)
}

class ActionRepository(
    private val dataSource: FirebaseRoomDataSource
) {
    suspend fun postAction(roomId: String, action: NetworkAction): Result<Unit> =
        dataSource.postAction(roomId, action)

    fun observeActions(roomId: String): Flow<NetworkAction> =
        dataSource.observeActions(roomId)
}

class PresenceRepository(
    private val dataSource: FirebaseRoomDataSource,
    private val authDataSource: FirebaseAuthDataSource
) {
    suspend fun setReady(roomId: String, uid: String, isReady: Boolean): Result<Unit> =
        dataSource.setPlayerReady(roomId, uid, isReady)

    suspend fun setConnected(roomId: String, uid: String, isConnected: Boolean): Result<Unit> =
        dataSource.setPlayerConnected(roomId, uid, isConnected)

    fun observeConnected(): Flow<Boolean> =
        dataSource.observeConnected()

    suspend fun ensureAuthenticated(fallbackId: String): Result<String> =
        authDataSource.ensureAuthenticated(fallbackId)
}

class ChatRepository(
    private val dataSource: FirebaseRoomDataSource
) {
    suspend fun sendChat(roomId: String, chat: ChatEvent): Result<Unit> =
        dataSource.sendChat(roomId, chat)

    fun observeChat(roomId: String): Flow<ChatEvent> =
        dataSource.observeChat(roomId)
}

class GameRepository(
    private val dataSource: FirebaseRoomDataSource
) {
    suspend fun publishGameState(
        roomId: String,
        state: GameState,
        metaUpdate: RoomMetadata? = null
    ): Result<Unit> = dataSource.publishGameState(roomId, state, metaUpdate)

    suspend fun publishEvent(roomId: String, event: NetworkEvent): Result<Unit> =
        dataSource.publishEvent(roomId, event)

    fun observeEvents(roomId: String): Flow<NetworkEvent> =
        dataSource.observeEvents(roomId)
}
