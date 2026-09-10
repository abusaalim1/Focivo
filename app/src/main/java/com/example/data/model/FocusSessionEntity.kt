package com.example.data.model

data class FocusSessionEntity(
    val id: Long = System.currentTimeMillis(),
    val taskTitle: String = "",
    val durationSeconds: Int = 0,
    val targetDurationSeconds: Int = 0,
    val mode: String = "Deep Work", // "Deep Work", "Classic", "Short Sprint", "Custom"
    val distractionsCount: Int = 0,
    val distractionTypes: String = "", // e.g., "Phone:1, Thought:2"
    val focusPointsEarned: Int = 12,
    val completedAt: Long = System.currentTimeMillis(),
    val dayOfWeek: Int = 1, // 1 (Mon) to 7 (Sun)
    val hourOfDay: Int = 10, // 0 to 23
    val notes: String = ""
)
