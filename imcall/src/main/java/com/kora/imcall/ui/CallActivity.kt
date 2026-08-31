package com.kora.imcall.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.kora.imcall.*
import com.kora.imcall.engine.WebRtcEngine
import com.kora.imcore.call.CallSignal
import kotlinx.coroutines.launch
import org.webrtc.SurfaceViewRenderer

class CallActivity : AppCompatActivity() {
    private var engine: WebRtcEngine? = null
    private var engineCallId: String? = null
    private var muted = false
    private var speaker = true
    private var connectedAt = 0L
    private var offerStarted = false
    private var permissionGrantedAction: (() -> Unit)? = null
    private var loadedProfileCallId: String? = null
    private lateinit var status: TextView
    private lateinit var local: SurfaceViewRenderer
    private lateinit var remote: SurfaceViewRenderer
    // Activity's base Context is not attached while field initializers run.
    private val timer = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            if (connectedAt > 0) {
                val seconds = (SystemClock.elapsedRealtime() - connectedAt) / 1000
                status.text = "%02d:%02d".format(seconds / 60, seconds % 60)
                timer.postDelayed(this, 1000)
            }
        }
    }
    private val permission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val action = permissionGrantedAction
        permissionGrantedAction = null
        if (result.values.all { it }) action?.invoke() else { IMCall.reject(); finish() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(com.kora.imcall.R.layout.imcall_activity_call)
        status = findViewById(com.kora.imcall.R.id.imcall_status)
        local = findViewById(com.kora.imcall.R.id.imcall_local)
        remote = findViewById(com.kora.imcall.R.id.imcall_remote)
        findViewById<ImageButton>(com.kora.imcall.R.id.imcall_accept).setOnClickListener { requestMediaPermission { IMCall.accept() } }
        findViewById<ImageButton>(com.kora.imcall.R.id.imcall_end).setOnClickListener {
            if (IMCall.session.value?.phase == CallPhase.INCOMING) IMCall.reject() else IMCall.hangup()
        }
        findViewById<ImageButton>(com.kora.imcall.R.id.imcall_mute).setOnClickListener {
            muted = !muted; engine?.setMuted(muted); updateToggle(it as ImageButton, muted)
        }
        findViewById<ImageButton>(com.kora.imcall.R.id.imcall_speaker).setOnClickListener {
            speaker = !speaker; engine?.setSpeaker(speaker); updateToggle(it as ImageButton, speaker)
        }
        findViewById<ImageButton>(com.kora.imcall.R.id.imcall_camera).setOnClickListener { engine?.switchCamera() }
        lifecycleScope.launch { IMCall.session.collect(::render) }
        lifecycleScope.launch {
            IMCall.mediaSignals.collect { signal ->
                when (signal.action) {
                    CallSignal.OFFER -> engine?.receiveOffer(signal.payload)
                    CallSignal.ANSWER -> engine?.receiveAnswer(signal.payload)
                    CallSignal.ICE -> engine?.receiveIce(signal.payload)
                }
            }
        }
        requestMediaPermission { prepareIfNeeded() }
    }

    private fun render(session: CallSession?) {
        if (session == null) { finish(); return }
        if (loadedProfileCallId != session.callId) {
            findViewById<TextView>(com.kora.imcall.R.id.imcall_peer).text = session.peerId
            findViewById<TextView>(com.kora.imcall.R.id.imcall_avatar).text = session.peerId.take(1).uppercase()
            findViewById<ImageView>(com.kora.imcall.R.id.imcall_avatar_image).apply {
                Glide.with(this@CallActivity).clear(this)
                visibility = View.GONE
            }
            loadPeerProfile(session)
        }
        val incoming = session.phase == CallPhase.INCOMING
        findViewById<View>(com.kora.imcall.R.id.imcall_accept_group).visibility = if (incoming) View.VISIBLE else View.GONE
        findViewById<TextView>(com.kora.imcall.R.id.imcall_end_label).text = if (incoming) "拒绝" else "挂断"
        status.text = when (session.phase) {
            CallPhase.OUTGOING -> "正在等待对方接受邀请…"
            CallPhase.INCOMING -> if (session.callType == CallSignal.TYPE_VIDEO) "邀请你进行视频通话" else "邀请你进行语音通话"
            CallPhase.CONNECTING -> "正在连接…"
            CallPhase.CONNECTED -> status.text
            CallPhase.ENDED -> session.reason.ifBlank { "通话结束" }
            else -> ""
        }
        val video = session.callType == CallSignal.TYPE_VIDEO
        local.visibility = if (video) View.VISIBLE else View.GONE
        remote.visibility = if (video) View.VISIBLE else View.GONE
        findViewById<View>(com.kora.imcall.R.id.imcall_camera_group).visibility = if (video) View.VISIBLE else View.GONE
        findViewById<View>(com.kora.imcall.R.id.imcall_avatar_container).visibility = if (video) View.GONE else View.VISIBLE
        findViewById<View>(com.kora.imcall.R.id.imcall_secondary_controls).visibility = if (incoming) View.INVISIBLE else View.VISIBLE
        if (session.phase == CallPhase.CONNECTING) {
            prepareIfNeeded()
            if (session.outgoing && !offerStarted && engine != null) {
                offerStarted = true
                engine?.createOffer()
            }
        }
        if (session.phase == CallPhase.ENDED) window.decorView.postDelayed({ finish() }, 800)
    }

    private fun prepareIfNeeded() {
        val session = IMCall.session.value ?: return
        if (session.phase == CallPhase.INCOMING || engineCallId == session.callId) return
        if (!hasPermissions()) return
        engineCallId = session.callId
        engine = WebRtcEngine(this, { action, payload -> IMCall.sendMedia(action, payload) }, {
            runOnUiThread { connectedAt = SystemClock.elapsedRealtime(); IMCall.markConnected(); timer.post(tick) }
        }, { runOnUiThread { IMCall.hangup() } }).also {
            it.prepare(local, remote, session.callType == CallSignal.TYPE_VIDEO)
            speaker = session.callType == CallSignal.TYPE_VIDEO
            it.setSpeaker(speaker)
            updateToggle(findViewById(com.kora.imcall.R.id.imcall_speaker), speaker)
        }
    }

    private fun requestMediaPermission(granted: () -> Unit) {
        if (hasPermissions()) granted() else {
            permissionGrantedAction = granted
            permission.launch(requiredPermissions())
        }
    }
    private fun loadPeerProfile(session: CallSession) {
        if (loadedProfileCallId == session.callId) return
        loadedProfileCallId = session.callId
        lifecycleScope.launch {
            val info = com.kora.imcore.IMClient.getUserInfo(session.peerId) ?: return@launch
            if (IMCall.session.value?.callId != session.callId) return@launch
            findViewById<TextView>(com.kora.imcall.R.id.imcall_peer).text = info.nickname.ifBlank { session.peerId }
            val image = findViewById<ImageView>(com.kora.imcall.R.id.imcall_avatar_image)
            if (info.avatar.isBlank()) {
                image.visibility = View.GONE
            } else {
                image.visibility = View.VISIBLE
                Glide.with(this@CallActivity)
                    .load(info.avatar)
                    .circleCrop()
                    .error(android.R.color.transparent)
                    .into(image)
            }
        }
    }
    private fun requiredPermissions(): Array<String> =
        if (IMCall.session.value?.callType == CallSignal.TYPE_VIDEO) {
            arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
        } else {
            arrayOf(Manifest.permission.RECORD_AUDIO)
        }

    private fun hasPermissions() = requiredPermissions().all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }
    private fun updateToggle(button: ImageButton, selected: Boolean) {
        button.isSelected = selected
        button.imageTintList = android.content.res.ColorStateList.valueOf(
            android.graphics.Color.parseColor(if (selected) "#222222" else "#FFFFFF")
        )
    }
    @Deprecated("Handled to terminate the active call")
    override fun onBackPressed() {
        IMCall.hangup()
        super.onBackPressed()
    }
    override fun onDestroy() { timer.removeCallbacksAndMessages(null); engine?.release(); engine = null; super.onDestroy() }
}
