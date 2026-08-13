package com.kora.imui

import android.text.TextUtils
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.kora.imcore.ImSdkImpl
import com.kora.imcore.constant.MsgDirection
import com.kora.imcore.constant.SessionType
import com.kora.imcore.db.Message
import com.kora.imui.MessageBuilder.createTextMessage
import com.kora.imui.MessageBuilder.createImageMessage
import com.kora.imui.MessageListPanelEx
import com.kora.imui.R
import com.kora.imui.attachment.VoiceAttachment
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


/**
 * Copyright 2026 GodCodeApps
 * @Date: 2026/07/15:10:02
 * @Description:聊天底部输入区域控制
 */
class InputPanel(
    var fragment: Fragment,
    var proxy: ModuleProxy?,
    var sessionId: String,
    var sessionType: Int,
    var actions: List<BaseInputAction>?,
    var rootView: View,
    var messageListPanelEx: MessageListPanelEx
) {
    private val chatInputView = rootView.findViewById<ChatInputView>(R.id.chat_input_view)

    private var onMoreActionClickListener: ((Int) -> Unit)? = null
    fun setActionClickListener(cb: (res: Int) -> Unit) {
        this.onMoreActionClickListener = cb
    }

    class Builder {
        private var mProxy: ModuleProxy? = null
        private var mActions: List<BaseInputAction>? = null
        private var mSessionId: String = ""
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

        fun setActions(actions: List<BaseInputAction>): Builder {
            this.mActions = actions
            return this
        }

        fun build(fragment: Fragment, rootView: View, messageListPanelEx: MessageListPanelEx): InputPanel {
            return InputPanel(
                fragment,mProxy, mSessionId, mSessionType, mActions, rootView, messageListPanelEx
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
                val createTextMessage = createTextMessage(sessionId, sessionType, msg = message)
                // 添加到适配器并滚动到底部
//                messageListPanelEx.addItemMsg(createTextMessage)
                proxy?.sendMessage(createTextMessage)
            }

            override fun onVoiceClick() {
                // 处理点击语音按钮的逻辑
                Toast.makeText(proxy?.getAppActivity(), "语音按钮点击", Toast.LENGTH_SHORT).show()
            }

            override fun onMoreOptionClick(optionName: String?) {
                if (optionName.equals("相册")) {
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
                                        localPath = path,
                                        mWidth = media.width,
                                        mHeight = media.height
                                    )
                                    proxy?.sendMessage(msg)
                                }
                            }

                            override fun onCancel() {
                            }
                        })
                    return
                }


                Toast.makeText(proxy?.getAppActivity(), optionName + "按钮点击", Toast.LENGTH_SHORT)
                    .show()
            }

            override fun onEmojiClick(emojiTag: String?) {
                chatInputView.insertText(emojiTag)
            }

            private var audioRecordHelper: com.kora.imui.utils.AudioRecordHelper? = null
            private var toast: Toast? = null

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
                
                if (audioRecordHelper == null) {
                    val cacheDir = fragment.context?.cacheDir
                    if (cacheDir != null) {
                        audioRecordHelper = com.kora.imui.utils.AudioRecordHelper(cacheDir)
                    }
                }
                
                audioRecordHelper?.startRecording()
                toast = Toast.makeText(fragment.context, "正在录音... 上滑取消", Toast.LENGTH_SHORT)
                toast?.show()
            }

            override fun onVoiceRecordEnd() {
                toast?.cancel()
                val result = audioRecordHelper?.stopRecording()
                if (result != null) {
                    val path = result.first
                    val duration = result.second
                    
                    if (duration < 1000) {
                        Toast.makeText(fragment.context, "录音时间太短", Toast.LENGTH_SHORT).show()
                        return
                    }

                    // Create VoiceAttachment
                    val voiceAttach = VoiceAttachment().apply {
                        this.path = path
                        this.duration = duration
                    }
                    val msg = Message(
                        sessionType = sessionType,
                        sessionId = sessionId,
                        account = ImSdkImpl.getAccount() ?: "",
                        type = voiceAttach.getMsgType(),
                        direct = MsgDirection.OUT,
                        status = MsgStatus.SENDING,
                        time = System.currentTimeMillis(),
                        attachment = voiceAttach.toJson(false)
                    )
                    proxy?.sendMessage(msg)
                }
            }

            override fun onVoiceRecordCancel() {
                toast?.cancel()
                Toast.makeText(fragment.context, "已取消录音", Toast.LENGTH_SHORT).show()
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
