package com.neoludo.game.multiplayer

import android.content.Context
import com.google.firebase.FirebaseApp
import com.neoludo.game.engine.model.LudoRuleSet
import com.neoludo.game.engine.model.PlayerColor

/**
 * Full online-room session: the [MultiplayerClient] game actions plus room
 * lifecycle (create / join / lobby). Implemented by two transports:
 *
 * - [FirebaseMultiplayerClient] — used automatically when this build ships with
 *   `google-services.json` (your own Firebase project).
 * - [MqttMultiplayerClient] — zero-config fallback over a free public MQTT
 *   relay. No account, no config file, works straight after install.
 */
interface OnlineRoomClient : MultiplayerClient {
    val currentUid: String
    val currentAssignedColor: PlayerColor
    val currentRoomId: String
    val maxPlayers: Int
    val preferredColor: PlayerColor

    suspend fun createRoom(
        playerCount: Int = maxPlayers,
        fillBots: Boolean = false,
        rules: LudoRuleSet = LudoRuleSet()
    ): Result<String>

    suspend fun joinRoom(roomId: String): Result<Unit>
    suspend fun setFillBots(fillBots: Boolean): Result<Unit>
    /** Short human-readable transport label for diagnostics ("Relay HiveMQ", "Firebase"). */
    val transportDebug: String
    /** Manual reconnect (lobby retry button). Best-effort per transport. */
    suspend fun refreshConnection(): Result<Unit>
}

object OnlineClientFactory {
    fun isFirebaseConfigured(context: Context): Boolean = try {
        FirebaseApp.getApps(context).isNotEmpty()
    } catch (_: Throwable) {
        false
    }

    /**
     * Firebase when configured, otherwise the free public relay.
     * Either way the caller gets a working online room — no setup needed.
     */
    fun create(
        context: Context,
        localPlayerId: String,
        localPlayerName: String,
        localAvatarId: Int,
        preferredColor: PlayerColor = PlayerColor.RED,
        initialRoomId: String = FirebaseMultiplayerClient.generateRoomCode(),
        maxPlayers: Int = 4,
        ruleSet: LudoRuleSet = LudoRuleSet()
    ): OnlineRoomClient {
        return if (isFirebaseConfigured(context)) {
            FirebaseMultiplayerClient(
                localPlayerId = localPlayerId,
                localPlayerName = localPlayerName,
                localAvatarId = localAvatarId,
                preferredColor = preferredColor,
                initialRoomId = initialRoomId,
                maxPlayers = maxPlayers,
                ruleSet = ruleSet
            )
        } else {
            MqttMultiplayerClient(
                localPlayerId = localPlayerId,
                localPlayerName = localPlayerName,
                localAvatarId = localAvatarId,
                preferredColor = preferredColor,
                initialRoomId = initialRoomId,
                maxPlayers = maxPlayers,
                ruleSet = ruleSet
            )
        }
    }
}
