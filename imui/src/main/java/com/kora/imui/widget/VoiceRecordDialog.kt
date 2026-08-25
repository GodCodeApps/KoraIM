package com.kora.imui.widget

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.Typeface
import com.kora.imui.R

/**
 * 录音状态弹窗（类似微信录音 HUD）：
 * - 正常录音：展示白色麦克风图标与 6 级音量波形动画，底部提示“手指上滑，取消发送”
 * - 上滑取消：展示红色取消图标，底部高亮提示“松开手指，取消发送”
 * - 录音太短：展示感叹号警告，提示“说话时间太短”
 */
class VoiceRecordDialog(context: Context) : Dialog(context) {

    private lateinit var llRecording: LinearLayout
    private lateinit var ivCancel: ImageView
    private lateinit var ivWarning: ImageView
    private lateinit var tvTip: TextView
    private val volumeBars = mutableListOf<View>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_voice_record)
        setCancelable(false)
        setCanceledOnTouchOutside(false)

        window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
            attributes?.apply {
                gravity = Gravity.CENTER
                width = WindowManager.LayoutParams.WRAP_CONTENT
                height = WindowManager.LayoutParams.WRAP_CONTENT
            }
        }

        llRecording = findViewById(R.id.ll_recording_indicator)
        ivCancel = findViewById(R.id.iv_record_cancel)
        ivWarning = findViewById(R.id.iv_record_warning)
        tvTip = findViewById(R.id.tv_record_tip)

        volumeBars.add(findViewById(R.id.v_bar_1))
        volumeBars.add(findViewById(R.id.v_bar_2))
        volumeBars.add(findViewById(R.id.v_bar_3))
        volumeBars.add(findViewById(R.id.v_bar_4))
        volumeBars.add(findViewById(R.id.v_bar_5))
        volumeBars.add(findViewById(R.id.v_bar_6))
    }

    /** 设置为正常录音状态 */
    fun showRecording() {
        if (!isShowing) show()
        llRecording.visibility = View.VISIBLE
        ivCancel.visibility = View.GONE
        ivWarning.visibility = View.GONE
        tvTip.text = "手指上滑，取消发送"
        tvTip.setBackgroundResource(0)
        tvTip.setTypeface(null, Typeface.NORMAL)
    }

    /** 设置为上滑取消状态 */
    fun showWantToCancel() {
        if (!isShowing) show()
        llRecording.visibility = View.GONE
        ivCancel.visibility = View.VISIBLE
        ivWarning.visibility = View.GONE
        tvTip.text = "松开手指，取消发送"
        tvTip.setBackgroundResource(R.drawable.bg_voice_cancel_badge)
        tvTip.setTypeface(null, Typeface.BOLD)
    }

    /** 设置为录音时间太短警告状态 */
    fun showTooShort(onDismiss: () -> Unit) {
        if (!isShowing) show()
        llRecording.visibility = View.GONE
        ivCancel.visibility = View.GONE
        ivWarning.visibility = View.VISIBLE
        tvTip.text = "说话时间太短"
        tvTip.setBackgroundResource(0)
        tvTip.setTypeface(null, Typeface.NORMAL)
        tvTip.postDelayed({
            dismiss()
            onDismiss()
        }, 800)
    }

    /** 根据最大振幅更新音量波形动画（1..6 级） */
    fun updateAmplitude(amplitude: Int) {
        if (!::llRecording.isInitialized || llRecording.visibility != View.VISIBLE) return
        val level = (amplitude / 3000).coerceIn(0, 6)
        volumeBars.forEachIndexed { index, view ->
            view.alpha = if (index < level) 1.0f else 0.2f
        }
    }
}
