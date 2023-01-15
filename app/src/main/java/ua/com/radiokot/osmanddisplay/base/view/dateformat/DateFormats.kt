package ua.com.radiokot.osmanddisplay.base.view.dateformat

import android.content.Context
import java.text.DateFormat
import java.util.*

object DateFormats {
    private val locale: Locale
        get() = Locale.getDefault()

    /**
     * Formats given date to the long string with date only
     */
    val longDateOnly: DateFormat
        get() = java.text.SimpleDateFormat("dd MMMM yyyy", locale)

    /**
     * Formats given date to the long string with time only:
     * 12-/24-hour time based on device preference
     */
    fun longTimeOnly(context: Context): DateFormat {
        return android.text.format.DateFormat.getTimeFormat(context)
    }

    /**
     * Formats given date with [longTimeOnly] if it is today.
     * Otherwise [longDateOnly] is used.
     */
    fun longTimeOrDate(context: Context): DateFormat {
        return SimpleDateFormat { date ->
            val currentCalendar = Calendar.getInstance()
            val currentDay = currentCalendar.get(Calendar.DAY_OF_YEAR)
            val currentYear = currentCalendar.get(Calendar.YEAR)

            val actionCalendar = Calendar.getInstance().apply { timeInMillis = date.time }
            val actionDay = actionCalendar.get(Calendar.DAY_OF_YEAR)
            val actionYear = actionCalendar.get(Calendar.YEAR)

            when {
                currentDay == actionDay && currentYear == actionYear ->
                    longTimeOnly(context)
                else ->
                    longDateOnly
            }.format(date)
        }
    }
}