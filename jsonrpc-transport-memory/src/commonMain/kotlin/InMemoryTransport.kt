package io.github.genkt.jsonrpc.transport.memory

import io.genkt.jsonrpc.SendAction
import io.genkt.jsonrpc.Transport
import io.genkt.jsonrpc.completeCatchingSuspend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch

@Suppress("FunctionName")
public fun <T> CoroutineScope.InMemoryTransport(
    bufferSize: Int = Channel.UNLIMITED,
    onBufferOverflow: BufferOverflow = BufferOverflow.SUSPEND,
    onSendUndeliveredElement: ((SendAction<T>) -> Unit)? = null,
    onReceiveUndeliveredElement: ((Result<T>) -> Unit)? = null,
): Pair<Transport<T, T>, Transport<T, T>> {
    val channelIn1 = Channel(bufferSize, onBufferOverflow, onSendUndeliveredElement)
    val channelOut1 = Channel(bufferSize, onBufferOverflow, onReceiveUndeliveredElement)
    val channelIn2 = Channel(bufferSize, onBufferOverflow, onSendUndeliveredElement)
    val channelOut2 = Channel(bufferSize, onBufferOverflow, onReceiveUndeliveredElement)
    val coroutineScope = this
    val onClose: () -> Unit = {
        channelIn1.close()
        channelOut1.close()
        channelIn2.close()
        channelOut2.close()
    }

    val listenJob1 = launch(start = CoroutineStart.LAZY) {
        channelIn1.consumeAsFlow().collect { sendAction ->
            sendAction.completeCatchingSuspend { channelOut2.send(Result.success(it)) }
        }
    }
    val listenJob2 = launch(start = CoroutineStart.LAZY) {
        channelIn2.consumeAsFlow().collect { sendAction ->
            sendAction.completeCatchingSuspend { channelOut1.send(Result.success(it)) }
        }
    }
    val onStart: () -> Unit = {
        listenJob1.start()
        listenJob2.start()
    }

    return Transport(
        channelIn1,
        channelOut1.consumeAsFlow(),
        coroutineScope,
        onClose,
        onStart,
    ) to Transport(
        channelIn2,
        channelOut2.consumeAsFlow(),
        coroutineScope,
        onClose,
        onStart
    )
}