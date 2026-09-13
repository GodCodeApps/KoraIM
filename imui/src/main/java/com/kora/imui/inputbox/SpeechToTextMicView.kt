package com.kora.imui.inputbox

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.animation.ValueAnimator
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import com.kora.imui.R
import kotlin.math.max

/**
 * 语音转文字按钮。录音时绘制绿色麦克风和两侧的细小声波，声波长度
 * 根据 AudioRecord 的实时音量变化，不需要额外的图片资源。
 */
class SpeechToTextMicView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val microphoneDrawable: Drawable =
        requireNotNull(context.getDrawable(R.drawable.ic_chat_mic_inner))
            .mutate()
    private val pulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 720L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            pulsePhase = it.animatedValue as Float
            invalidate()
        }
    }
    private var recording = false
    private var audioLevel = 0f
    private var pulsePhase = 0f

    fun setRecording(value: Boolean) {
        recording = value
        if (!value) {
            audioLevel = 0f
            stopPulseAnimation()
        }
        invalidate()
    }

    fun setAudioLevel(value: Float) {
        audioLevel = value.coerceIn(0f, 1f)
        if (recording) {
            if (audioLevel >= 0.06f) {
                startPulseAnimation()
            } else if (audioLevel < 0.025f) {
                stopPulseAnimation()
            }
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val centerX = width / 2f
        val centerY = height / 2f
        if (recording) {
            drawRecording(canvas, centerX, centerY)
        } else {
            drawIdle(canvas, centerX, centerY)
        }
    }

    private fun drawRecording(canvas: Canvas, centerX: Float, centerY: Float) {
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(218, 247, 231)
        canvas.drawCircle(centerX, centerY, dp(15f), paint)

        drawPulseRipple(canvas, centerX, centerY)
        drawSoundWaves(canvas, centerX, centerY)

        paint.color = Color.rgb(7, 193, 96)
        canvas.drawCircle(centerX, centerY, dp(10.5f), paint)
        drawMicrophone(canvas, centerX, centerY, Color.WHITE)
    }

    private fun drawIdle(canvas: Canvas, centerX: Float, centerY: Float) {
        drawMicrophone(canvas, centerX, centerY, Color.rgb(117, 117, 117))
    }

    private fun drawPulseRipple(canvas: Canvas, centerX: Float, centerY: Float) {
        if (audioLevel < 0.06f || !pulseAnimator.isRunning) return

        val phase = pulsePhase
        val radius = dp(11.5f + phase * (4f + audioLevel * 2f))
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1.2f)
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = Color.rgb(7, 193, 96)
        paint.alpha = ((1f - phase) * (80f + audioLevel * 110f)).toInt().coerceIn(0, 190)
        canvas.drawCircle(centerX, centerY, radius, paint)
        paint.alpha = 255
        paint.style = Paint.Style.FILL
    }

    private fun drawSoundWaves(canvas: Canvas, centerX: Float, centerY: Float) {
        val level = max(audioLevel, 0.08f)
        val extra = dp(1.5f + level * 3f)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1.15f)
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = Color.rgb(7, 193, 96)
        paint.alpha = (105 + level * 150).toInt().coerceIn(105, 255)

        val left = RectF(
            centerX - dp(16f) - extra,
            centerY - dp(8f) - extra,
            centerX - dp(2f),
            centerY + dp(8f) + extra,
        )
        val right = RectF(
            centerX + dp(2f),
            centerY - dp(8f) - extra,
            centerX + dp(16f) + extra,
            centerY + dp(8f) + extra,
        )
        canvas.drawArc(left, -58f, 116f, false, paint)
        canvas.drawArc(right, 122f, 116f, false, paint)
        paint.alpha = 255
        paint.style = Paint.Style.FILL
    }

    private fun drawMicrophone(canvas: Canvas, centerX: Float, centerY: Float, color: Int) {
        microphoneDrawable.setTint(color)
        val iconSize = dp(24f).toInt()
        val left = (centerX - iconSize / 2f).toInt()
        val top = (centerY - iconSize / 2f).toInt()
        microphoneDrawable.setBounds(left, top, left + iconSize, top + iconSize)
        microphoneDrawable.draw(canvas)
    }

    private fun startPulseAnimation() {
        if (!pulseAnimator.isStarted) {
            pulsePhase = 0f
            pulseAnimator.start()
        }
    }

    private fun stopPulseAnimation() {
        if (pulseAnimator.isStarted) pulseAnimator.cancel()
        pulsePhase = 0f
    }

    override fun onDetachedFromWindow() {
        stopPulseAnimation()
        super.onDetachedFromWindow()
    }

    private fun dp(value: Float): Float = value * density
}
