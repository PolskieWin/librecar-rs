package com.librecar.rust.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CalendarEventItem(
    val id: Long,
    val title: String,
    val location: String,
    val timeLabel: String,
    val isLocalReminder: Boolean,
)

object CalendarProviderHelper {
    fun hasCalendarPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    suspend fun loadUpcomingEvents(
        context: Context,
        localReminders: List<LocalCalendarReminder>,
    ): List<CalendarEventItem> = withContext(Dispatchers.IO) {
        val combined = mutableListOf<CalendarEventItem>()

        // Always include local CarPlay trip reminders
        for (r in localReminders) {
            combined.add(
                CalendarEventItem(
                    id = r.id,
                    title = r.title,
                    location = r.location,
                    timeLabel = r.timeLabel,
                    isLocalReminder = true,
                )
            )
        }

        if (hasCalendarPermission(context)) {
            val now = System.currentTimeMillis()
            val horizon = now + 4L * 24L * 60L * 60L * 1000L // Next 4 days
            val timeFmt = SimpleDateFormat("EEE HH:mm", Locale.getDefault())
            val projection = arrayOf(
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.EVENT_LOCATION,
                CalendarContract.Instances.BEGIN,
            )
            val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
                .appendPath(now.toString())
                .appendPath(horizon.toString())
                .build()

            runCatching {
                context.contentResolver.query(
                    uri,
                    projection,
                    null,
                    null,
                    "${CalendarContract.Instances.BEGIN} ASC",
                )?.use { c ->
                    val idCol = c.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID)
                    val titleCol = c.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
                    val locCol = c.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_LOCATION)
                    val beginCol = c.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
                    var count = 0
                    while (c.moveToNext() && count < 12) {
                        val title = c.getString(titleCol)?.takeIf { it.isNotBlank() } ?: continue
                        val loc = c.getString(locCol).orEmpty()
                        val begin = c.getLong(beginCol)
                        combined.add(
                            CalendarEventItem(
                                id = c.getLong(idCol),
                                title = title,
                                location = loc,
                                timeLabel = timeFmt.format(Date(begin)),
                                isLocalReminder = false,
                            )
                        )
                        count++
                    }
                }
            }
        }
        combined
    }
}
