package com.kora.imui.widget

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.Window
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.TextView
import com.kora.imui.R
import com.kora.imui.attachment.RedPacketAttachment

class RedPacketOpenDialog(
    context: Context,
    private val packet: RedPacketAttachment,
    private val senderName: String,
    private val onOpenSuccess: () -> Unit
) : Dialog(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_red_packet_open)

        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window?.attributes?.apply {
            gravity = Gravity.CENTER
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
        }

        findViewById<ImageView>(R.id.iv_close).setOnClickListener {
            dismiss()
        }

        findViewById<TextView>(R.id.tv_sender_name).text = "${senderName}的红包"
        findViewById<TextView>(R.id.tv_greeting).text = packet.greeting

        val btnOpen = findViewById<TextView>(R.id.btn_open)
        btnOpen.setOnClickListener {
            btnOpen.isEnabled = false
            
            // 3D Flip Animation
            val animator = ObjectAnimator.ofFloat(btnOpen, "rotationY", 0f, 720f) // 2 spins
            animator.duration = 1000 // 1 second
            animator.interpolator = LinearInterpolator()
            animator.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    onOpenSuccess()
                    dismiss()
                }
            })
            animator.start()
        }
    }
}
