package com.kora.imcall.engine

import android.content.Context
import android.media.AudioManager
import org.json.JSONObject
import org.webrtc.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

internal class WebRtcEngine(
    context: Context,
    private val signal: (String, String) -> Unit,
    private val onConnected: () -> Unit,
    private val onFailed: (String) -> Unit
) {
    private val app = context.applicationContext
    private val audioManager = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val oldAudioMode = audioManager.mode
    private val egl = EglBase.create()
    private val factory: PeerConnectionFactory
    private var peer: PeerConnection? = null
    private var capturer: CameraVideoCapturer? = null
    private var textureHelper: SurfaceTextureHelper? = null
    private var videoSource: VideoSource? = null
    private var audioSource: AudioSource? = null
    private var localVideo: VideoTrack? = null
    private var localAudio: AudioTrack? = null
    private var remoteVideo: VideoTrack? = null
    private val pendingIce = CopyOnWriteArrayList<IceCandidate>()
    private var remoteDescriptionReady = false
    private var localRenderer: SurfaceViewRenderer? = null
    private var remoteRenderer: SurfaceViewRenderer? = null
    private val released = AtomicBoolean(false)

    init {
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(app).createInitializationOptions())
        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(egl.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(egl.eglBaseContext))
            .createPeerConnectionFactory()
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
    }

    fun prepare(local: SurfaceViewRenderer, remote: SurfaceViewRenderer, video: Boolean) {
        localRenderer = local; remoteRenderer = remote
        local.init(egl.eglBaseContext, null); local.setMirror(true); local.setZOrderMediaOverlay(true)
        remote.init(egl.eglBaseContext, null)
        audioSource = factory.createAudioSource(MediaConstraints())
        localAudio = factory.createAudioTrack("kora_audio", audioSource)
        if (video) {
            val enumerator = Camera2Enumerator(app)
            val name = enumerator.deviceNames.firstOrNull(enumerator::isFrontFacing) ?: enumerator.deviceNames.firstOrNull()
            capturer = name?.let { enumerator.createCapturer(it, null) as? CameraVideoCapturer }
            videoSource = factory.createVideoSource(false)
            textureHelper = SurfaceTextureHelper.create("KoraCallCapture", egl.eglBaseContext)
            capturer?.initialize(textureHelper, app, videoSource?.capturerObserver)
            capturer?.startCapture(1280, 720, 30)
            localVideo = factory.createVideoTrack("kora_video", videoSource).also { it.addSink(local) }
        }
        createPeer()
    }

    private fun createPeer() {
        val ice = mutableListOf(
            PeerConnection.IceServer.builder("stun:stun.qq.com:3478").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun.miwifi.com:3478").createIceServer()
        )
        peer = factory.createPeerConnection(PeerConnection.RTCConfiguration(ice), observer)?.apply {
            localAudio?.let { addTrack(it, listOf("kora_stream")) }
            localVideo?.let { addTrack(it, listOf("kora_stream")) }
        }
    }

    fun createOffer() {
        peer?.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                val value = sdp ?: return
                peer?.setLocalDescription(object : SimpleSdpObserver() {
                    override fun onSetSuccess() = signal("offer", value.description)
                }, value)
            }
        }, mediaConstraints())
    }

    fun receiveOffer(sdp: String) {
        val value = SessionDescription(SessionDescription.Type.OFFER, sdp)
        peer?.setRemoteDescription(object : SimpleSdpObserver() {
            override fun onSetSuccess() {
                remoteDescriptionReady = true; flushIce()
                peer?.createAnswer(object : SimpleSdpObserver() {
                    override fun onCreateSuccess(answer: SessionDescription?) {
                        val result = answer ?: return
                        peer?.setLocalDescription(object : SimpleSdpObserver() {
                            override fun onSetSuccess() = signal("answer", result.description)
                        }, result)
                    }
                }, mediaConstraints())
            }
        }, value)
    }

    fun receiveAnswer(sdp: String) {
        peer?.setRemoteDescription(object : SimpleSdpObserver() {
            override fun onSetSuccess() { remoteDescriptionReady = true; flushIce() }
        }, SessionDescription(SessionDescription.Type.ANSWER, sdp))
    }

    fun receiveIce(json: String) {
        runCatching {
            val value = JSONObject(json)
            val ice = IceCandidate(value.optString("sdpMid"), value.optInt("sdpMLineIndex"), value.getString("candidate"))
            if (remoteDescriptionReady) peer?.addIceCandidate(ice) else pendingIce += ice
        }
    }

    private fun flushIce() { pendingIce.forEach { peer?.addIceCandidate(it) }; pendingIce.clear() }
    fun setMuted(muted: Boolean) { localAudio?.setEnabled(!muted) }
    fun setSpeaker(enabled: Boolean) { audioManager.isSpeakerphoneOn = enabled }
    fun switchCamera() { capturer?.switchCamera(null) }

    fun release() {
        if (!released.compareAndSet(false, true)) return
        runCatching { capturer?.stopCapture() }
        runCatching { remoteRenderer?.let { remoteVideo?.removeSink(it) } }
        runCatching { localRenderer?.let { localVideo?.removeSink(it) } }
        runCatching { localAudio?.setEnabled(false) }
        runCatching { localVideo?.setEnabled(false) }
        runCatching { peer?.close() }
        runCatching { peer?.dispose() }
        peer = null
        // Tracks attached to PeerConnection are released with the connection in this
        // WebRTC build. Disposing them again throws "MediaStreamTrack has been disposed".
        localVideo = null; remoteVideo = null; localAudio = null
        runCatching { capturer?.dispose() }; capturer = null
        runCatching { videoSource?.dispose() }; videoSource = null
        runCatching { audioSource?.dispose() }; audioSource = null
        runCatching { textureHelper?.dispose() }; textureHelper = null
        runCatching { factory.dispose() }
        runCatching { localRenderer?.release() }; localRenderer = null
        runCatching { remoteRenderer?.release() }; remoteRenderer = null
        runCatching { egl.release() }
        audioManager.mode = oldAudioMode
    }

    private fun mediaConstraints() = MediaConstraints().apply {
        mandatory += MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true")
        mandatory += MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true")
    }

    private val observer = object : PeerConnection.Observer {
        override fun onIceCandidate(candidate: IceCandidate?) {
            candidate ?: return
            signal("ice", JSONObject().put("sdpMid", candidate.sdpMid).put("sdpMLineIndex", candidate.sdpMLineIndex).put("candidate", candidate.sdp).toString())
        }
        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
            when (state) {
                PeerConnection.IceConnectionState.CONNECTED, PeerConnection.IceConnectionState.COMPLETED -> onConnected()
                PeerConnection.IceConnectionState.FAILED -> onFailed("连接失败")
                else -> Unit
            }
        }
        override fun onTrack(transceiver: RtpTransceiver?) {
            (transceiver?.receiver?.track() as? VideoTrack)?.let { remoteVideo = it; remoteRenderer?.let(it::addSink) }
        }
        override fun onSignalingChange(p0: PeerConnection.SignalingState?) = Unit
        override fun onIceConnectionReceivingChange(p0: Boolean) = Unit
        override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) = Unit
        override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) = Unit
        override fun onAddStream(p0: MediaStream?) = Unit
        override fun onRemoveStream(p0: MediaStream?) = Unit
        override fun onDataChannel(p0: DataChannel?) = Unit
        override fun onRenegotiationNeeded() = Unit
    }

    private open class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(p0: SessionDescription?) = Unit
        override fun onSetSuccess() = Unit
        override fun onCreateFailure(p0: String?) = Unit
        override fun onSetFailure(p0: String?) = Unit
    }
}
