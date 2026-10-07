package com.example.data.model

data class FocusSessionEntity(
    val id: Long = System.currentTimeMillis(),
    val userId: String = "",
    val taskTitle: String = "",
    val durationSeconds: Int = 0,
    val targetDurationSeconds: Int = 0,
    val mode: String = "Deep Work", // "Deep Work", "Classic", "Short Sprint", "Custom"
    val startTime: Long = System.currentTimeMillis() - (durationSeconds * 1000L),
    val endTime: Long = System.currentTimeMillis(),
    val distractionsCount: Int = 0,
    val distractionTypes: String = "", // e.g., "Phone:1, Thought:2"
    val focusPointsEarned: Int = 12,
    val completedAt: Long = System.currentTimeMillis(),
    val dayOfWeek: Int = 1, // 1 (Mon) to 7 (Sun)
    val hourOfDay: Int = 10, // 0 to 23
    val notes: String = ""
) {
    fun getFormattedStartTime(): String {
        val date = java.util.Date(if (startTime > 0) startTime else (completedAt - durationSeconds * 1000L))
        val sdf = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
        return sdf.format(date)
    }

    fun getFormattedEndTime(): String {
        val date = java.util.Date(if (endTime > 0) endTime else completedAt)
        val sdf = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
        return sdf.format(date)
    }

    fun getFormattedDuration(): String {
        val hours = durationSeconds / 3600
        val mins = (durationSeconds % 3600) / 60
        val secs = durationSeconds % 60
        return when {
            hours > 0 -> "${hours}h ${mins}m"
            mins > 0 -> "${mins}m ${secs}s"
            else -> "${secs}s"
        }
    }

    fun getFormattedDate(): String {
        val date = java.util.Date(completedAt)
        val sdf = java.text.SimpleDateFormat("EEEE, MMM d, yyyy", java.util.Locale.getDefault())
        return sdf.format(date)
    }
}
