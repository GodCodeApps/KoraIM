package com.kora.imui

import android.text.TextUtils
import android.view.View
import android.widget.Toast
import android.provider.OpenableColumns
import androidx.fragment.app.Fragment
import com.kora.imcore.ImSdkImpl
import com.kora.imcore.constant.MsgDirection
import com.kora.imcore.constant.SessionType
import com.kora.imcore.db.Message
import com.kora.imui.MessageBuilder.createTextMessage
import com.kora.imui.MessageBuilder.createImageMessage
import com.kora.imui.MessageBuilder.createVideoMessage
import com.kora.imui.MessageListPanelEx
import com.kora.imui.R
import com.kora.imui.attachment.VoiceAttachment
import com.kora.imui.attachment.VideoAttachment
import com.kora.imui.attachment.RedPacketAttachment
import com.kora.imui.attachment.FileAttachment
import com.kora.imui.bean.BaseInputAction
import com.kora.imui.inputbox.ChatInputView
import com.kora.imui.inputbox.ChatInputView.OnInputListener
import com.kora.imui.module.ModuleProxy
import com.kora.imui.utils.GlideEngine
import com.luck.picture.lib.basic.PictureSelector
import com.luck.picture.lib.config.SelectMimeType
import com.luck.picture.lib.entity.LocalMedia
import com.luck.picture.lib.interfaces.OnResultCallbackListener
import com.kora.imcore.constant.MsgStatus
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.kora.imui.IMMediaMessageSender
import androidx.activity.result.contract.ActivityResultContracts


/**
 * 聊天底部输入面板业务调度器：
 * 负责聚合文本输入、语音录制（带 HUD 振幅波形弹窗）、相册图片/视频选择、发红包弹窗等动作。
 */
class InputPanel(
    var fragment: Fragment,
    var proxy: ModuleProxy?,
    var sessionId: String,
    var peerId: String,
    var sessionType: Int,
    var actions: List<BaseInputAction>?,
    var rootView: View,
    var messageListPanelEx: MessageListPanelEx
) {
    private fun queueMediaMessage(message: Message) {
        messageListPanelEx.addItemMsg(message)
        IMMediaMessageSender.enqueue(message)
    }

    fun updateSessionId(value: String) {
        sessionId = value
    }
    private val chatInputView = rootView.findViewById<ChatInputView>(R.id.chat_input_view)
    private val filePicker = fragment.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        val resolver = fragment.requireContext().contentResolver
        var name = "文件"
        var size = 0L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { name = c.getString(it) ?: name }
                c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !c.isNull(it) }?.let { size = c.getLong(it) }
            }
        }
        val mimeType = resolver.getType(uri) ?: "application/octet-stream"
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            val localPath = withContext(Dispatchers.IO) {
                val safeName = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
                val target = java.io.File(fragment.requireContext().cacheDir, "im_file_${System.currentTimeMillis()}_$safeName")
                resolver.openInputStream(uri)?.use { input -> target.outputStream().use { output -> input.copyTo(output) } }
                target.absolutePath.takeIf { target.exists() }
            } ?: return@launch
            val attachment = FileAttachment().apply {
                this.localPath = localPath
                this.name = name
                this.size = size
                this.mimeType = mimeType
            }
            queueMediaMessage(MessageBuilder.createFileMessage(sessionId, sessionType, peerId.ifBlank { sessionId }, attachment).getMessage())
        }
    }

    private var onMoreActionClickListener: ((Int) -> Unit)? = null
    fun setActionClickListener(cb: (res: Int) -> Unit) {
        this.onMoreActionClickListener = cb
    }

    class Builder {
        private var mProxy: ModuleProxy? = null
        private var mActions: List<BaseInputAction>? = null
        private var mSessionId: String = ""
        private var mPeerId: String = ""
        private var mSessionType: Int = SessionType.None
        fun setProxy(proxy: ModuleProxy): Builder {
            this.mProxy = proxy
            return this
        }

        fun setSessionId(sessionId: String): Builder {
            this.mSessionId = sessionId
            return this
        }

        fun setSessionType(@SessionType sessionType: Int = SessionType.None): Builder {
            this.mSessionType = sessionType
            return this
        }

        fun setPeerId(peerId: String): Builder {
            this.mPeerId = peerId
            return this
        }

        fun setActions(actions: List<BaseInputAction>): Builder {
            this.mActions = actions
            return this
        }

        fun build(fragment: Fragment, rootView: View, messageListPanelEx: MessageListPanelEx): InputPanel {
            return InputPanel(
                fragment,mProxy, mSessionId, mPeerId, mSessionType, mActions, rootView, messageListPanelEx
            )
        }
    }


    init {
        initViews()
        initViewPager()
        initListener()
    }


    private fun initViews() {

    }

    private fun initViewPager() {

    }


    private fun initListener() {
        // 设置监听器来接收来自组件的事件
        chatInputView.setOnInputListener(object : OnInputListener {
            override fun onSendMessage(message: String) {
                // 处理发送消息的逻辑，例如添加到 RecyclerView
                Toast.makeText(proxy?.getAppActivity(), "发送: " + message, Toast.LENGTH_SHORT)
                    .show()

                if (TextUtils.isEmpty(message.toString().trim { it <= ' ' })) {
                    Toast.makeText(proxy?.getAppActivity(), "不能发送空消息", Toast.LENGTH_SHORT)
                        .show()
                    return
                }
                // 创建一个新的 MessageItem
                val createTextMessage = createTextMessage(
                    sessionId,
                    sessionType,
                    receiverId = peerId.ifBlank { sessionId },
                    msg = message
                )
                // 添加到适配器并滚动到底部
//                messageListPanelEx.addItemMsg(createTextMessage)
                proxy?.sendMessage(createTextMessage)
            }

            private var lastTypingTime = 0L

            override fun onTyping() {
                val now = System.currentTimeMillis()
                if (now - lastTypingTime > 2500L) {
                    lastTypingTime = now
                    val target = peerId.ifBlank { sessionId }
                    if (target.isNotBlank()) {
                        android.util.Log.d("KoraIM_Typing", "InputPanel onTyping -> sending to: $target")
                        com.kora.imcore.IMClient.sendTyping(target)
                    }
                }
            }

            override fun onVoiceClick() {
                // 处理点击语音按钮的逻辑
                Toast.makeText(proxy?.getAppActivity(), "语音按钮点击", Toast.LENGTH_SHORT).show()
            }

            override fun onMoreOptionClick(optionName: String?) {
                if (optionName.equals("图片")) {
                    PictureSelector.create(proxy?.getAppActivity())
                        .openGallery(SelectMimeType.ofImage())
                        .setImageEngine(GlideEngine.createGlideEngine())
                        .setMaxSelectNum(9)
                        .forResult(object : OnResultCallbackListener<LocalMedia?> {
                            override fun onResult(result: ArrayList<LocalMedia?>) {
                                for (media in result) {
                                    if (media == null) continue
                                    val path = media.realPath ?: media.availablePath ?: media.path
                                    val msg = createImageMessage(
                                        sessionId = sessionId,
                                        sessionType = sessionType,
                                        receiverId = peerId.ifBlank { sessionId },
                                        localPath = path,
                                        mWidth = media.width,
                                        mHeight = media.height,
                                        size = media.size,
                                        mimeType = media.mimeType.orEmpty()
                                    )
                                    queueMediaMessage(msg.getMessage())
                                }
                            }

                            override fun onCancel() {
                            }
                        })
                    return
                } else if (optionName.equals("视频")) {
                    PictureSelector.create(proxy?.getAppActivity())
                        .openGallery(SelectMimeType.ofVideo())
                        .setImageEngine(GlideEngine.createGlideEngine())
                        .setMaxSelectNum(1)
                        .forResult(object : OnResultCallbackListener<LocalMedia?> {
                            override fun onResult(result: ArrayList<LocalMedia?>) {
                                val media = result.firstOrNull() ?: return
                                val path = media.realPath ?: media.availablePath ?: media.path
                                if (path.isNullOrBlank()) return
                                val attachment = VideoAttachment().apply {
                                    localPath = path
                                    duration = media.duration
                                    width = media.width
                                    height = media.height
                                    size = media.size
                                    mimeType = media.mimeType.orEmpty()
                                }
                                queueMediaMessage(
                                    createVideoMessage(
                                        sessionId = sessionId,
                                        sessionType = sessionType,
                                        receiverId = peerId.ifBlank { sessionId },
                                        attachment = attachment
                                    ).getMessage()
                                )
                            }

                            override fun onCancel() {
                            }
                        })
                    return
                } else if (optionName.equals("文件")) {
                    filePicker.launch(arrayOf("*/*"))
                    return
                }

                // If not handled by imui, bubble it up to the fragment
                val currentFragment = fragment
                if (currentFragment is com.kora.imui.fragment.IMessageFragment) {
                    currentFragment.onMoreOptionClick(optionName ?: "")
                }
                
                ImUIKitImpl.getSessionListener()?.getMoreOptionClickListener()?.invoke(optionName ?: "")
            }

            override fun onEmojiClick(emojiTag: String?) {
                chatInputView.insertText(emojiTag)
            }

            private var audioRecordHelper: com.kora.imui.utils.AudioRecordHelper? = null
            private var voiceRecordDialog: com.kora.imui.widget.VoiceRecordDialog? = null
            private val amplitudeHandler = android.os.Handler(android.os.Looper.getMainLooper())
            private val amplitudeRunnable = object : Runnable {
                override fun run() {
                    val amplitude = audioRecordHelper?.getMaxAmplitude() ?: 0
                    voiceRecordDialog?.updateAmplitude(amplitude)
                    amplitudeHandler.postDelayed(this, 100)
                }
            }

            private fun checkPermission(): Boolean {
                val context = fragment.context ?: return false
                if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    fragment.requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), 1001)
                    return false
                }
                return true
            }

            override fun onVoiceRecordStart() {
                if (!checkPermission()) return
                
                val context = fragment.context ?: return
                if (audioRecordHelper == null) {
                    val cacheDir = context.cacheDir
                    if (cacheDir != null) {
                        audioRecordHelper = com.kora.imui.utils.AudioRecordHelper(cacheDir)
                    }
                }
                
                val started = audioRecordHelper?.startRecording() ?: false
                if (started) {
                    if (voiceRecordDialog == null) {
                        voiceRecordDialog = com.kora.imui.widget.VoiceRecordDialog(context)
                    }
                    voiceRecordDialog?.showRecording()
                    amplitudeHandler.removeCallbacks(amplitudeRunnable)
                    amplitudeHandler.post(amplitudeRunnable)
                }
            }

            override fun onVoiceRecordMove(willCancel: Boolean) {
                if (willCancel) {
                    voiceRecordDialog?.showWantToCancel()
                } else {
                    voiceRecordDialog?.showRecording()
                }
            }

            override fun onVoiceRecordEnd() {
                amplitudeHandler.removeCallbacks(amplitudeRunnable)
                val result = audioRecordHelper?.stopRecording()
                if (result != null) {
                    val path = result.first
                    val duration = result.second
                    
                    if (duration < 1000) {
                        voiceRecordDialog?.showTooShort {
                            java.io.File(path).delete()
                        }
                        return
                    }

                    voiceRecordDialog?.dismiss()

                    // Create VoiceAttachment
                    val voiceAttach = VoiceAttachment().apply {
                        this.localPath = path
                        this.duration = duration
                        this.size = java.io.File(path).length()
                        this.mimeType = "audio/mp4"
                    }
                    val msg = Message(
                        sessionType = sessionType,
                        sessionId = sessionId,
                        senderId = ImSdkImpl.getAccount() ?: "",
                        receiverId = peerId.ifBlank { sessionId },
                        type = voiceAttach.getMsgType(),
                        direct = MsgDirection.OUT,
                        status = MsgStatus.SENDING,
                        time = System.currentTimeMillis(),
                        attachment = voiceAttach.toJson(false)
                    )
                    queueMediaMessage(msg)
                } else {
                    voiceRecordDialog?.dismiss()
                }
            }

            override fun onVoiceRecordCancel() {
                amplitudeHandler.removeCallbacks(amplitudeRunnable)
                voiceRecordDialog?.dismiss()
                audioRecordHelper?.cancelRecording()
            }
        })

    }

    // 模拟接收消息，用于演示
    private fun simulateReceiveMessage() {
        val receivedMessage = createTextMessage(
            sessionId,
            sessionType,
            msg = "我收到了你的消息！",
            msgDirect = MsgDirection.IN
        )
        messageListPanelEx.addItemMsg(receivedMessage)
    }
}
