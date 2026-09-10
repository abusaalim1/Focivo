package com.example.data.model

data class TaskEntity(
    val id: Long = System.currentTimeMillis(),
    val title: String = "",
    val category: String = "Deep Work",
    val priority: Int = 1, // 1: High, 2: Medium, 3: Low
    val durationMinutes: Int = 25,
    val scheduledTime: String = "09:00",
    val isCompleted: Boolean = false,
    val isTopPriority: Boolean = false,
    val notes: String = "",
    val date: String = "", // YYYY-MM-DD
    val createdAt: Long = System.currentTimeMillis()
)
