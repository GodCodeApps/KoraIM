package com.kora.imui.utils

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object TimeFormatUtils {

    /**
     * 仿微信的消息时间显示逻辑：
     * 1. 今天：HH:mm (如 14:30)
     * 2. 昨天：昨天 HH:mm (如 昨天 14:30)
     * 3. 7天内：星期X HH:mm (如 星期二 14:30)
     * 4. 同一年：MM月dd日 HH:mm (如 08月12日 14:30)
     * 5. 跨年：yyyy年MM月dd日 HH:mm (如 2023年08月12日 14:30)
     */
    fun formatWeChatTime(timestamp: Long): String {
        if (timestamp <= 0) return ""

        val msgTime = Calendar.getInstance()
        msgTime.timeInMillis = timestamp

        val now = Calendar.getInstance()

        val msgYear = msgTime.get(Calendar.YEAR)
        val nowYear = now.get(Calendar.YEAR)

        val msgDayOfYear = msgTime.get(Calendar.DAY_OF_YEAR)
        val nowDayOfYear = now.get(Calendar.DAY_OF_YEAR)

        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val timeString = timeFormat.format(Date(timestamp))

        return if (msgYear == nowYear) {
            when {
                msgDayOfYear == nowDayOfYear -> {
                    // 今天
                    timeString
                }
                nowDayOfYear - msgDayOfYear == 1 -> {
                    // 昨天
                    "昨天 $timeString"
                }
                nowDayOfYear - msgDayOfYear in 2..6 -> {
                    // 一周内
                    val weekFormat = SimpleDateFormat("EEEE", Locale.CHINESE)
                    val weekString = weekFormat.format(Date(timestamp))
                    "$weekString $timeString"
                }
                else -> {
                    // 今年内
                    val dateFormat = SimpleDateFormat("MM月dd日 HH:mm", Locale.CHINESE)
                    dateFormat.format(Date(timestamp))
                }
            }
        } else {
            // 跨年
            val fullFormat = SimpleDateFormat("yyyy年MM月dd日 HH:mm", Locale.CHINESE)
            fullFormat.format(Date(timestamp))
        }
    }
}
