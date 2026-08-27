package com.hermes.companion.broker

import com.hermes.companion.domain.NodeCommand
import com.hermes.companion.domain.NodeEventFrame
import com.hermes.companion.domain.Receipt
import com.hermes.companion.domain.SendOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** In-memory broker for tests and for wiring the dispatcher without a network. */
class FakeNodeBroker(
    initial: BrokerConnectionState = BrokerConnectionState.Connected,
) : NodeBroker {
    private val _connection = MutableStateFlow(initial)
    override val connection: StateFlow<BrokerConnectionState> = _connection.asStateFlow()

    private val _commands = MutableSharedFlow<NodeCommand>(extraBufferCapacity = 64)
    override fun commands(): Flow<NodeCommand> = _commands.asSharedFlow()

    val receipts = mutableListOf<Receipt>()
    val events = mutableListOf<NodeEventFrame>()

    override suspend fun sendReceipt(receipt: Receipt) { receipts += receipt }

    override suspend fun sendEvent(frame: NodeEventFrame): SendOutcome {
        if (_connection.value != BrokerConnectionState.Connected) return SendOutcome.Unacknowledged
        events += frame
        return SendOutcome.Acked
    }

    override fun start(scope: CoroutineScope) {}
    override fun stop() { _connection.value = BrokerConnectionState.Disconnected }

    suspend fun inject(command: NodeCommand) { _commands.emit(command) }
    fun setConnected(connected: Boolean) {
        _connection.value = if (connected) BrokerConnectionState.Connected else BrokerConnectionState.Disconnected
    }
}
