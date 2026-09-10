package com.example.data.model

data class ReflectionEntity(
    val id: Long = System.currentTimeMillis(),
    val weekLabel: String = "",
    val totalMinutesFocused: Int = 0,
    val sessionsCompleted: Int = 0,
    val bestDay: String = "Wednesday",
    val completionRate: Int = 80,
    val reflectionText: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
