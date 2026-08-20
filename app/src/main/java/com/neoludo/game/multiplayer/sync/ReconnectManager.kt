package com.neoludo.game.multiplayer.sync

import com.neoludo.game.multiplayer.model.ConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ReconnectManager(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    private val onGracePeriodExpired: () -> Unit = {}
) {
    private val _connectionState = MutableStateFlow(ConnectionState.CONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private var graceJob: Job? = null
    val gracePeriodSeconds: Int = 30

    fun onConnected() {
        graceJob?.cancel()
        _connectionState.value = ConnectionState.CONNECTED
    }

    fun onDisconnected() {
        _connectionState.value = ConnectionState.RECONNECTING
        graceJob?.cancel()
        graceJob = scope.launch {
            delay(gracePeriodSeconds * 1000L)
            _connectionState.value = ConnectionState.DISCONNECTED
            onGracePeriodExpired()
        }
    }

    fun reset() {
        graceJob?.cancel()
        _connectionState.value = ConnectionState.CONNECTED
    }
}
