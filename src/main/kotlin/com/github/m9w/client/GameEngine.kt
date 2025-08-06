package com.github.m9w.client

import com.darkorbit.ProtocolPacket
import com.github.m9w.Scheduler
import com.github.m9w.client.auth.AuthenticationProvider
import com.github.m9w.client.network.NetworkLayer
import com.github.m9w.context
import com.github.m9w.feature.annotations.SystemEvents
import com.github.m9w.feature.waitMs
import com.github.m9w.metaplugins.proxy.ProxyModule
import com.github.m9w.optionalContext
import com.github.m9w.protocol.Factory

class GameEngine() {
    private val authentication: AuthenticationProvider by context
    val scheduler: Scheduler by context
    private val proxy: ProxyModule? by optionalContext
    val userIdAndSid get() = authentication.run { "$userID|$sessionID" }
    var network: NetworkLayer = NetworkLayer(); private set
    var state: State = State.NOT_CONNECTED

    enum class State {
        NOT_CONNECTED, NO_LOGIN, DESTROYED, NORMAL, REPAIRING, TRAVELING, ESCAPING, STOPED
    }

    fun connect() {
        try {
            state = State.NOT_CONNECTED
            network.onDisconnect = {}
            network.close()
            network = NetworkLayer(authentication.address, proxy)
            network.onPackageHandler = scheduler::handleEvent
            network.onDisconnect = { handleEvent(SystemEvents.ON_DISCONNECT) }
            handleEvent(SystemEvents.ON_CONNECT)
        } catch (e: Exception) {
            e.printStackTrace()
            handleEvent(SystemEvents.ON_DISCONNECT)
        }
    }

    fun disconnect() {
        proxy?.releaseProxy()
        network.close()
        state = State.STOPED
    }

    suspend fun reconnect(reconnectInMs: Long = 0, keepProxy: Boolean = false) {
        network.close()
        if (reconnectInMs > 0) {
            if (!keepProxy) proxy?.releaseProxy()
            network.close()
            state = State.STOPED
            waitMs(reconnectInMs)
        }
        state = State.NOT_CONNECTED
    }

    fun handleEvent(event: String, body: String = "") = scheduler.handleEvent(event, body)

    fun cancelWaitMs(interruptKey: String, body: (()->Exception)?=null) = scheduler.cancelWaitMs(interruptKey, body)

    inline fun <reified T : ProtocolPacket> send(noinline changes: T.() -> Unit) {
        val data = Factory.build(T::class).also { changes.invoke(it) }
        network.send(data)
    }
}
