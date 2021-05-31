package snnafi.podplay.util

import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

object DateUtils {
    fun jsonDateToShortDate(jsonDate: String?): String {
//1
        if (jsonDate == null) {
            return "-"
        }
// 2
        val inFormat = SimpleDateFormat(
            "yyyy-MM-dd'T'HH:mm:ss",
            Locale.getDefault()
        )
// 3
        val date = inFormat.parse(jsonDate) ?: return "-"
        return dateToShortDate(date)
    }

    fun xmlDateToDate(dateString: String?): Date {
        val date = dateString ?: return Date()
        val inFormat = SimpleDateFormat(
            "EEE, dd MMM yyyy HH:mm:ss z",
            Locale.getDefault()
        )
        return inFormat.parse(date) ?: Date()
    }

    fun dateToShortDate(date: Date): String {
        val outputFormat = DateFormat.getDateInstance(
            DateFormat.SHORT, Locale.getDefault()
        )
        return outputFormat.format(date)
    }
}