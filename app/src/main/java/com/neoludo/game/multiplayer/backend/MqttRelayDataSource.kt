package com.neoludo.game.multiplayer.backend

import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.util.UUID

/**
 * Zero-config online transport over free public MQTT brokers.
 *
 * No account, no API keys, no `google-services.json` — the APK works straight
 * after install. Trade-offs vs Firebase (documented, accepted for casual play):
 * - No SLA: public brokers are best-effort and rate-limited.
 * - Obscurity-level privacy: topics are unlisted but guessable from the room
 *   code. Never put passwords or personal data in display names / chat.
 * - No server-side transactions: seat races resolve last-writer-wins; the
 *   host-authoritative game processor + version fencing keep gameplay correct.
 *
 * Protocol (all payloads are JSON via kotlinx.serialization):
 * - `neoludo/v1/rooms/<CODE>/meta`        retained  RoomMetadata
 * - `neoludo/v1/rooms/<CODE>/players/<uid>` retained PlayerPresence
 *     (Last-Will publishes the offline marker, so crashes are detected)
 * - `neoludo/v1/rooms/<CODE>/state`       retained  GameState
 * - `neoludo/v1/rooms/<CODE>/actions`     transient NetworkAction (QoS 1)
 * - `neoludo/v1/rooms/<CODE>/chat`        transient ChatEvent (QoS 1)
 * - `neoludo/v1/rooms/<CODE>/events`      transient NetworkEvent (QoS 1)
 */
object MqttRelay {
    const val ROOT = "neoludo/v1/rooms"

    /** Tried in order: encrypted first, plain-text fallback. */
    val SERVERS = listOf(
        "ssl://broker.hivemq.com:8883",
        "tcp://broker.hivemq.com:1883",
        "ssl://broker.emqx.io:8883",
        "tcp://broker.emqx.io:1883"
    )

    const val CONNECT_TIMEOUT_SEC = 10
    const val KEEP_ALIVE_SEC = 30

    private val CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private val CODE_REGEX = Regex("^NL-[A-Z0-9]{6}$")

    fun generateRoomCode(): String =
        "NL-" + (1..6).map { CODE_CHARS.random() }.joinToString("")

    fun normalizeRoomCode(raw: String): String {
        val stripped = raw.trim().uppercase().replace(" ", "").replace("-", "")
        return if (stripped.startsWith("NL")) "NL-" + stripped.removePrefix("NL")
        else "NL-$stripped"
    }

    /** Null when the code is malformed (also rejects MQTT wildcards). */
    fun sanitizeCode(raw: String): String? {
        val code = normalizeRoomCode(raw)
        if (!CODE_REGEX.matches(code)) return null
        if (code.contains('+') || code.contains('#')) return null
        return code
    }

    fun metaTopic(code: String) = "$ROOT/$code/meta"
    fun presenceTopic(code: String, uid: String) = "$ROOT/$code/players/$uid"
    fun playersWildcard(code: String) = "$ROOT/$code/players/+"
    fun playersPrefix(code: String) = "$ROOT/$code/players/"
    fun stateTopic(code: String) = "$ROOT/$code/state"
    fun actionsTopic(code: String) = "$ROOT/$code/actions"
    fun chatTopic(code: String) = "$ROOT/$code/chat"
    fun eventsTopic(code: String) = "$ROOT/$code/events"

    fun uidFromPresenceTopic(topic: String, code: String): String? {
        val prefix = playersPrefix(code)
        if (!topic.startsWith(prefix)) return null
        val uid = topic.removePrefix(prefix)
        if (uid.isBlank() || uid.contains('/') || uid.contains('+') || uid.contains('#')) return null
        return uid
    }
}

class MqttRelayDataSource {
    private val tag = "MqttRelayDataSource"
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private var client: MqttClient? = null
    private var connectedServer: String? = null

    private val exactHandlers = mutableMapOf<String, MutableList<(String) -> Unit>>()
    private val prefixHandlers = mutableListOf<Pair<String, (String, String) -> Unit>>()
    private val subscribedTopics = mutableMapOf<String, Int>()

    /** Invoked on unexpected connection loss (Paho thread). */
    var onConnectionLost: (() -> Unit)? = null

    fun isConnected(): Boolean = try {
        client?.isConnected == true
    } catch (_: Throwable) {
        false
    }

    fun connectedServer(): String? = connectedServer

    /**
     * Connects, trying each public broker in order. [willTopic]/[willPayload]
     * are published by the broker itself if we disconnect uncleanly
     * (retained offline-presence marker).
     */
    suspend fun connect(
        clientId: String,
        willTopic: String? = null,
        willPayload: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (isConnected()) return@withContext Result.success(Unit)
        var lastError: Throwable? = null
        for (uri in MqttRelay.SERVERS) {
            try {
                val c = MqttClient(uri, clientId, MemoryPersistence())
                c.setCallback(relayCallback)
                val opts = MqttConnectOptions().apply {
                    isCleanSession = true
                    connectionTimeout = MqttRelay.CONNECT_TIMEOUT_SEC
                    keepAliveInterval = MqttRelay.KEEP_ALIVE_SEC
                    isAutomaticReconnect = false
                    if (willTopic != null && willPayload != null) {
                        setWill(willTopic, willPayload.toByteArray(Charsets.UTF_8), 1, true)
                    }
                }
                c.connect(opts)
                client = c
                connectedServer = uri
                resubscribeAll()
                return@withContext Result.success(Unit)
            } catch (e: Throwable) {
                lastError = e
                Log.w(tag, "Relay $uri unreachable: ${e.message}")
                try {
                    client?.close()
                } catch (_: Throwable) {
                }
                client = null
            }
        }
        Result.failure(lastError ?: IllegalStateException("No relay reachable"))
    }

    suspend fun disconnect(): Unit = withContext(Dispatchers.IO) {
        try {
            client?.disconnect()
        } catch (_: Throwable) {
        }
        try {
            client?.close()
        } catch (_: Throwable) {
        }
        client = null
        connectedServer = null
    }

    suspend fun publish(
        topic: String,
        payload: String,
        retained: Boolean = false,
        qos: Int = 1
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val c = client
        if (c == null || !c.isConnected) {
            return@withContext Result.failure(IllegalStateException("Relay not connected"))
        }
        try {
            c.publish(topic, MqttMessage(payload.toByteArray(Charsets.UTF_8)).apply {
                this.qos = qos
                isRetained = retained
            })
            Result.success(Unit)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    /** Clears a retained topic (empty retained message deletes it server-side). */
    suspend fun clearRetained(topic: String): Result<Unit> =
        publish(topic, "", retained = true, qos = 1)

    fun subscribeExact(topic: String, qos: Int = 1, handler: (String) -> Unit) {
        synchronized(this) {
            exactHandlers.getOrPut(topic) { mutableListOf() }.add(handler)
            subscribedTopics[topic] = qos
        }
        try {
            client?.takeIf { it.isConnected }?.subscribe(topic, qos)
        } catch (e: Throwable) {
            Log.w(tag, "subscribe $topic failed: ${e.message}")
        }
    }

    fun unsubscribeExact(topic: String, handler: (String) -> Unit) {
        synchronized(this) {
            exactHandlers[topic]?.remove(handler)
            if (exactHandlers[topic].isNullOrEmpty()) {
                exactHandlers.remove(topic)
                subscribedTopics.remove(topic)
                try {
                    client?.takeIf { it.isConnected }?.unsubscribe(topic)
                } catch (_: Throwable) {
                }
            }
        }
    }

    fun subscribePrefix(prefix: String, qos: Int = 1, handler: (String, String) -> Unit) {
        // Paho needs a wildcard subscription; dispatch to prefix handlers locally.
        val wildcard = "$prefix+"
        synchronized(this) {
            prefixHandlers.add(prefix to handler)
            subscribedTopics[wildcard] = qos
        }
        try {
            client?.takeIf { it.isConnected }?.subscribe(wildcard, qos)
        } catch (e: Throwable) {
            Log.w(tag, "subscribe $wildcard failed: ${e.message}")
        }
    }

    fun unsubscribePrefix(prefix: String, handler: (String, String) -> Unit) {
        synchronized(this) {
            prefixHandlers.remove(prefix to handler)
            if (prefixHandlers.none { it.first == prefix }) {
                subscribedTopics.remove("$prefix+")
                try {
                    client?.takeIf { it.isConnected }?.unsubscribe("$prefix+")
                } catch (_: Throwable) {
                }
            }
        }
    }

    /**
     * Reads a retained message (room existence / latest snapshot). Returns null
     * on timeout — the topic simply has no retained message.
     */
    suspend fun awaitRetained(topic: String, timeoutMs: Long = 3000L): String? {
        if (!isConnected()) return null
        val deferred = CompletableDeferred<String?>()
        val handler: (String) -> Unit = { payload ->
            if (!deferred.isCompleted) deferred.complete(payload.ifBlank { null })
        }
        subscribeExact(topic, qos = 1, handler = handler)
        return try {
            withTimeoutOrNull(timeoutMs) { deferred.await() }
        } finally {
            unsubscribeExact(topic, handler)
        }
    }

    fun observeExact(topic: String): Flow<String> = callbackFlow {
        val handler: (String) -> Unit = { payload -> trySend(payload) }
        subscribeExact(topic, handler = handler)
        awaitClose { unsubscribeExact(topic, handler) }
    }

    fun observePrefix(prefix: String): Flow<Pair<String, String>> = callbackFlow {
        val handler: (String, String) -> Unit = { t, p -> trySend(t to p) }
        subscribePrefix(prefix, handler = handler)
        awaitClose { unsubscribePrefix(prefix, handler) }
    }

    private fun resubscribeAll() {
        val topics: Map<String, Int>
        synchronized(this) {
            topics = subscribedTopics.toMap()
        }
        val c = client ?: return
        for ((topic, qos) in topics) {
            try {
                if (c.isConnected) c.subscribe(topic, qos)
            } catch (e: Throwable) {
                Log.w(tag, "resubscribe $topic failed: ${e.message}")
            }
        }
    }

    private val relayCallback = object : MqttCallbackExtended {
        override fun connectComplete(reconnect: Boolean, serverURI: String?) {
            if (reconnect) resubscribeAll()
        }

        override fun connectionLost(cause: Throwable?) {
            Log.w(tag, "Relay connection lost: ${cause?.message}")
            try {
                onConnectionLost?.invoke()
            } catch (_: Throwable) {
            }
        }

        override fun messageArrived(topic: String?, message: MqttMessage?) {
            if (topic == null || message == null) return
            val payload = message.toString()
            val exact: List<(String) -> Unit>
            val prefixed: List<Pair<String, (String, String) -> Unit>>
            synchronized(this@MqttRelayDataSource) {
                exact = exactHandlers[topic]?.toList() ?: emptyList()
                prefixed = prefixHandlers.toList()
            }
            for (h in exact) {
                try {
                    h(payload)
                } catch (_: Throwable) {
                }
            }
            for ((prefix, h) in prefixed) {
                if (topic.startsWith(prefix)) {
                    try {
                        h(topic, payload)
                    } catch (_: Throwable) {
                    }
                }
            }
        }

        override fun deliveryComplete(token: IMqttDeliveryToken?) = Unit
    }

    companion object {
        fun newClientId(uid: String): String =
            "neoludo-" + uid.filter { it.isLetterOrDigit() }.take(16) +
                "-" + UUID.randomUUID().toString().take(8)
    }
}
