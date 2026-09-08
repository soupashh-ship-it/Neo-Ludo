package com.neoludo.game.multiplayer.backend

import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Result of a multi-relay retained lookup (see `fetchRetainedFromAny`). */
sealed interface RelayLookup {
    data class Found(val payload: String, val server: String) : RelayLookup
    data class NotFound(val relayReached: Boolean) : RelayLookup
}

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

    /**
     * Process-wide sticky server. HiveMQ and EMQX are separate broker
     * networks — a room published on one is invisible on the other — so every
     * peer in a room MUST use the same server. The first successful connect
     * (or probe hit) pins it; [orderedServers] then tries it first.
     */
    @Volatile
    var pinnedServer: String? = null
        private set

    fun pinServer(uri: String) {
        if (SERVERS.contains(uri)) pinnedServer = uri
    }

    fun clearPin() {
        pinnedServer = null
    }

    /** Short broker label for the lobby diagnostics line. */
    fun shortName(uri: String?): String = when {
        uri == null -> "?"
        uri.contains("hivemq", ignoreCase = true) -> "HiveMQ"
        uri.contains("emqx", ignoreCase = true) -> "EMQX"
        else -> "?"
    }

    /** Last-known-good (or explicitly preferred) server first, rest in order. */
    fun orderedServers(prefer: String? = null): List<String> {
        val first = prefer?.takeIf { SERVERS.contains(it) } ?: pinnedServer
        return if (first != null) listOf(first) + SERVERS.filter { it != first }
        else SERVERS
    }

    const val CONNECT_TIMEOUT_SEC = 10
    const val KEEP_ALIVE_SEC = 30

    private val CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private val CODE_REGEX = Regex("^NL-[A-Z0-9]{6}$")

    fun generateRoomCode(): String =
        "NL-" + (1..6).map { CODE_CHARS.random() }.joinToString("")

    /**
     * Aggressively recovers the code from chat-app paste junk: formatting
     * (`*NL-ABC123*`), quotes, BiDi marks, or the whole shared message
     * ("Join my game! Room code: NL-ABC123"). A valid code must never
     * become "room does not exist" because of surrounding text.
     */
    fun normalizeRoomCode(raw: String): String {
        val upper = raw.trim().uppercase()
        val stripped = upper.filter { it.isLetterOrDigit() }
        val direct = if (stripped.startsWith("NL")) "NL-" + stripped.removePrefix("NL")
        else "NL-$stripped"
        if (CODE_REGEX.matches(direct)) return direct
        // Fall back to an embedded token with word boundaries, so a 7+
        // character typo can't silently resolve to a different room.
        val embedded = Regex("(?<![A-Z0-9])NL-?([A-Z0-9]{6})(?![A-Z0-9])")
            .find(upper)?.groupValues?.get(1)
        if (embedded != null) return "NL-$embedded"
        return direct
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
     * Connects, trying each public broker in order (pinned/last-known-good
     * first). [willTopic]/[willPayload] are published by the broker itself
     * if we disconnect uncleanly (retained offline-presence marker).
     * The winning server is pinned process-wide so every peer in a room
     * converges on the same broker network.
     */
    suspend fun connect(
        clientId: String,
        willTopic: String? = null,
        willPayload: String? = null,
        preferServer: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (isConnected()) return@withContext Result.success(Unit)
        var lastError: Throwable? = null
        for (uri in MqttRelay.orderedServers(preferServer)) {
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
                MqttRelay.pinServer(uri)
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
     * Reads a retained message from EVERY broker network in parallel and
     * returns the first hit. Rooms live on exactly one network (whichever the
     * host happened to reach first), so a single-server lookup can falsely
     * report "room does not exist" when the guest's default server differs
     * from the host's. The winning server is pinned for subsequent connects.
     *
     * [RelayLookup.NotFound.relayReached] tells "your internet/relays are
     * down" apart from "relays are fine but this room isn't on any of them"
     * so the UI can say something actionable.
     *
     * Fast path first: when already connected, one cheap lookup on the live
     * link (~1.2s) before fanning out to short-lived probe connections.
     */
    suspend fun fetchRetainedFromAny(topic: String, timeoutMs: Long = 12000L): RelayLookup {
        awaitRetained(topic, 1200L)?.let { payload ->
            connectedServer()?.let { MqttRelay.pinServer(it) }
            return RelayLookup.Found(payload, connectedServer() ?: "")
        }
        return withContext(Dispatchers.IO) {
            val anyConnected = AtomicBoolean(isConnected())
            val winner = CompletableDeferred<Pair<String, String>?>(parent = null)
            coroutineScope {
                val perProbeMs = (timeoutMs - 1000L).coerceAtLeast(2500L)
                val jobs = MqttRelay.SERVERS.map { uri ->
                    launch {
                        val payload = probeRetained(uri, topic, perProbeMs) { anyConnected.set(true) }
                        if (payload != null) winner.complete(uri to payload)
                    }
                }
                launch {
                    jobs.joinAll()
                    if (!winner.isCompleted) winner.complete(null)
                }
                try {
                    val hit = withTimeoutOrNull(timeoutMs + 2000L) { winner.await() }
                    if (hit != null) {
                        MqttRelay.pinServer(hit.first)
                        Log.d(tag, "Room topic found on ${hit.first}")
                        RelayLookup.Found(hit.second, hit.first)
                    } else {
                        RelayLookup.NotFound(relayReached = anyConnected.get())
                    }
                } finally {
                    jobs.forEach { it.cancel() }
                }
            }
        }
    }

    /**
     * One-shot blocking probe: connects a throwaway client, waits briefly
     * for the retained message, then disconnects. Never touches the live
     * connection or its subscriptions.
     */
    private fun probeRetained(
        uri: String,
        topic: String,
        timeoutMs: Long,
        onConnected: () -> Unit = {}
    ): String? {
        var probe: MqttClient? = null
        return try {
            probe = MqttClient(uri, "neoludo-probe-" + UUID.randomUUID().toString().take(8), MemoryPersistence())
            probe.connect(
                MqttConnectOptions().apply {
                    isCleanSession = true
                    connectionTimeout = 6
                    keepAliveInterval = 15
                    isAutomaticReconnect = false
                }
            )
            onConnected()
            val latch = CountDownLatch(1)
            val hit = AtomicReference<String?>(null)
            probe.subscribe(topic, 1) { _, message ->
                val payload = message.toString()
                if (payload.isNotBlank()) {
                    hit.set(payload)
                    latch.countDown()
                }
            }
            latch.await(timeoutMs, TimeUnit.MILLISECONDS)
            hit.get()
        } catch (e: Throwable) {
            Log.d(tag, "Probe $uri: ${e.message}")
            null
        } finally {
            try {
                probe?.disconnect()
            } catch (_: Throwable) {
            }
            try {
                probe?.close()
            } catch (_: Throwable) {
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
