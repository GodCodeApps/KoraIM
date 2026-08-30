package com.kora.imcall

import android.content.Context
import android.content.Intent
import com.kora.imcall.ui.CallActivity
import com.kora.imcore.IMClient
import com.kora.imcore.call.CallSignal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import java.util.UUID

object IMCall {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var appContext: Context? = null
    private var started = false
    private var timeoutJob: Job? = null
    private val _session = MutableStateFlow<CallSession?>(null)
    val session = _session.asStateFlow()
    private val _mediaSignals = MutableSharedFlow<CallSignal>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    internal val mediaSignals = _mediaSignals

    fun init(context: Context) {
        appContext = context.applicationContext
        if (started) return
        started = true
        scope.launch { IMClient.callSignals.collect(::onSignal) }
    }

    fun startAudioCall(context: Context, peerId: String) = start(context, peerId, CallSignal.TYPE_AUDIO)
    fun startVideoCall(context: Context, peerId: String) = start(context, peerId, CallSignal.TYPE_VIDEO)

    private fun start(context: Context, peerId: String, type: String) {
        val active = _session.value
        if (active != null && active.phase !in listOf(CallPhase.IDLE, CallPhase.ENDED)) return
        val callId = UUID.randomUUID().toString()
        _session.value = CallSession(callId, peerId, type, true, CallPhase.OUTGOING)
        scheduleTimeout(callId)
        openActivity(context)
        send(CallSignal(callId, CallSignal.INVITE, receiverId = peerId, callType = type))
    }

    fun accept() {
        val current = _session.value ?: return
        timeoutJob?.cancel()
        _session.value = current.copy(phase = CallPhase.CONNECTING)
        send(CallSignal(current.callId, CallSignal.ACCEPT, receiverId = current.peerId, callType = current.callType))
    }

    fun reject() = end(CallSignal.REJECT, "已拒绝")
    fun hangup() = end(if (_session.value?.phase == CallPhase.OUTGOING) CallSignal.CANCEL else CallSignal.HANGUP, "通话结束")

    internal fun sendMedia(action: String, payload: String) {
        val current = _session.value ?: return
        send(CallSignal(current.callId, action, receiverId = current.peerId, callType = current.callType, payload = payload))
    }

    internal fun markConnected() { _session.value = _session.value?.copy(phase = CallPhase.CONNECTED) }

    private fun end(action: String, reason: String) {
        val current = _session.value ?: return
        timeoutJob?.cancel()
        send(CallSignal(current.callId, action, receiverId = current.peerId, callType = current.callType))
        _session.value = current.copy(phase = CallPhase.ENDED, reason = reason)
    }

    private fun send(signal: CallSignal) { IMClient.sendCallSignal(signal) }

    private fun onSignal(signal: CallSignal) {
        val current = _session.value
        if (signal.action == CallSignal.INVITE) {
            if (current != null && current.phase !in listOf(CallPhase.IDLE, CallPhase.ENDED)) {
                send(CallSignal(signal.callId, CallSignal.BUSY, receiverId = signal.senderId, callType = signal.callType))
                return
            }
            _session.value = CallSession(signal.callId, signal.senderId, signal.callType, false, CallPhase.INCOMING)
            send(CallSignal(signal.callId, CallSignal.RINGING, receiverId = signal.senderId, callType = signal.callType))
            scheduleTimeout(signal.callId)
            appContext?.let(::openActivity)
            return
        }
        if (current == null || current.callId != signal.callId || current.peerId != signal.senderId) return
        when (signal.action) {
            CallSignal.ACCEPT -> {
                timeoutJob?.cancel()
                _session.value = current.copy(phase = CallPhase.CONNECTING)
            }
            CallSignal.REJECT, CallSignal.CANCEL, CallSignal.HANGUP, CallSignal.BUSY, CallSignal.TIMEOUT ->
                _session.value = current.copy(phase = CallPhase.ENDED, reason = when (signal.action) {
                    CallSignal.REJECT -> "对方已拒绝"
                    CallSignal.CANCEL -> "对方已取消"
                    CallSignal.BUSY -> "对方忙线"
                    CallSignal.TIMEOUT -> "无人接听"
                    else -> "通话结束"
                })
            CallSignal.OFFER, CallSignal.ANSWER, CallSignal.ICE, CallSignal.READY -> _mediaSignals.tryEmit(signal)
        }
    }

    private fun openActivity(context: Context) {
        context.startActivity(Intent(context, CallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
    }

    private fun scheduleTimeout(callId: String) {
        timeoutJob?.cancel()
        timeoutJob = scope.launch {
            delay(CALL_TIMEOUT_MS)
            val current = _session.value ?: return@launch
            if (current.callId != callId || current.phase !in listOf(CallPhase.OUTGOING, CallPhase.INCOMING)) return@launch
            send(CallSignal(callId, CallSignal.TIMEOUT, receiverId = current.peerId, callType = current.callType))
            _session.value = current.copy(phase = CallPhase.ENDED, reason = "无人接听")
        }
    }

    private const val CALL_TIMEOUT_MS = 45_000L
}
