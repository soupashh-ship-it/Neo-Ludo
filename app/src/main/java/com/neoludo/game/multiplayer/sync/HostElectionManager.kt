package com.neoludo.game.multiplayer.sync

import com.neoludo.game.multiplayer.model.PlayerPresence
import com.neoludo.game.multiplayer.model.RoomMetadata

class HostElectionManager {

    fun determineCurrentHost(
        meta: RoomMetadata,
        players: List<PlayerPresence>
    ): String {
        val configuredHost = players.firstOrNull { it.id == meta.hostId }
        if (configuredHost != null && configuredHost.isConnected) {
            return configuredHost.id
        }

        // If configured host is disconnected, elect connected player with earliest joinedAt / lowest id
        val connectedHumans = players.filter { it.isConnected && !it.isAi }
        if (connectedHumans.isNotEmpty()) {
            // Single deterministic comparator: earliest joinedAt (0/missing sorts last), then lowest id.
            // Previous code chained minByOrNull ?: minByOrNull — the second branch was dead code,
            // and all-joinedAt==0 fell back to Firebase iteration order (undefined).
            val elected = connectedHumans.minWithOrNull(
                compareBy(
                    { it.joinedAt.takeIf { t -> t > 0 } ?: Long.MAX_VALUE },
                    { it.id }
                )
            )
            return elected?.id ?: meta.hostId
        }

        return meta.hostId
    }

    fun isLocalPlayerHost(
        localUid: String,
        meta: RoomMetadata,
        players: List<PlayerPresence>
    ): Boolean {
        val hostId = determineCurrentHost(meta, players)
        return localUid == hostId
    }
}
