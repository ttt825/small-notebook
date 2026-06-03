package com.xiaojilu.app.utils

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object DateUtils {
    private val beijingTimeZone = TimeZone.getTimeZone("Asia/Shanghai")
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
        timeZone = beijingTimeZone
    }
    private val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault()).apply {
        timeZone = beijingTimeZone
    }
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun formatBeijingDate(date: Date): String {
        return dateFormat.format(date)
    }

    fun parseBeijingDate(dateStr: String): Date {
        val calendar = Calendar.getInstance(beijingTimeZone)
        val parts = dateStr.split("-")
        calendar.set(Calendar.YEAR, parts[0].toInt())
        calendar.set(Calendar.MONTH, parts[1].toInt() - 1)
        calendar.set(Calendar.DAY_OF_MONTH, parts[2].toInt())
        return calendar.time
    }

    fun getCurrentBeijingDate(): String {
        return formatBeijingDate(Date())
    }

    fun getCurrentBeijingDateTime(): String {
        return dateTimeFormat.format(Date())
    }

    fun getCurrentIsoDateTime(): String {
        return isoFormat.format(Date())
    }

    fun formatBeijingDisplayTime(isoStr: String): String {
        try {
            val date = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
                .apply { timeZone = TimeZone.getTimeZone("UTC") }
                .parse(isoStr)
            if (date != null) {
                val calendar = Calendar.getInstance(beijingTimeZone)
                calendar.time = date
                val hours = String.format("%02d", calendar.get(Calendar.HOUR_OF_DAY))
                val minutes = String.format("%02d", calendar.get(Calendar.MINUTE))
                return "$hours:$minutes"
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "00:00"
    }

    fun beijingDatetimeToIso(localStr: String): String {
        try {
            // localStr 格式: "yyyy-MM-ddTHH:mm" (北京时间)
            // 使用北京时间时区解析
            val beijingFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault())
            beijingFormat.timeZone = beijingTimeZone
            val beijingTime = beijingFormat.parse(localStr)
            
            if (beijingTime != null) {
                // 使用UTC时区的SimpleDateFormat格式化，它会自动处理时区转换
                val utcFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
                utcFormat.timeZone = TimeZone.getTimeZone("UTC")
                return utcFormat.format(beijingTime)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return getCurrentIsoDateTime()
    }

    fun beijingDatetimeToDateStr(localStr: String): String {
        return localStr.split("T")[0]
    }

    fun formatBeijingTimeForExport(isoStr: String): String {
        val date = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .parse(isoStr)
        if (date != null) {
            val calendar = Calendar.getInstance(beijingTimeZone)
            calendar.time = date
            val exportFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            exportFormat.timeZone = beijingTimeZone
            return exportFormat.format(calendar.time)
        }
        return getCurrentBeijingDateTime().replace("T", " ") + ":00"
    }

    // 将导出格式转换为ISO格式
    fun exportDatetimeToIso(exportStr: String): String {
        try {
            // 使用北京时间时区解析导出格式的时间
            val exportFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            exportFormat.timeZone = beijingTimeZone
            val beijingTime = exportFormat.parse(exportStr)
            
            if (beijingTime != null) {
                // 直接使用UTC时区的SimpleDateFormat格式化，它会自动处理时区转换
                val utcFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
                utcFormat.timeZone = TimeZone.getTimeZone("UTC")
                return utcFormat.format(beijingTime)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return getCurrentIsoDateTime()
    }
}