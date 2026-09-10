package com.example.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class AlarmItem(
    val id: String = UUID.randomUUID().toString(),
    val label: String = "Focus Alarm",
    val hour: Int = 8,
    val minute: Int = 0,
    val isEnabled: Boolean = true,
    val ringtone: String = "Zen Bell",
    val vibrate: Boolean = true,
    val isSnoozed: Boolean = false,
    val daysActive: String = "Mon,Tue,Wed,Thu,Fri"
) {
    val formattedTime: String
        get() {
            val h12 = if (hour % 12 == 0) 12 else hour % 12
            val amPm = if (hour < 12) "AM" else "PM"
            return String.format("%02d:%02d", h12, minute)
        }

    val amPm: String
        get() = if (hour < 12) "AM" else "PM"
}
