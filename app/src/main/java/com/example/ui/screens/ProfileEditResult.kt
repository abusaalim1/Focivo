package com.example.ui.screens

/**
 * v1.3.0 — result of the Profile edit dialog.
 *
 * The dialog currently edits display name + avatar only; the remaining
 * fields default to "keep current" semantics — callers that do not collect
 * them pass the user's existing preference values through so a save never
 * wipes data the dialog did not show. When the dialog grows goal/class/
 * exam/visibility controls, pass the edited values here directly.
 */
data class ProfileEditResult(
    val displayName: String,
    val avatarBytes: ByteArray?,
    val studyGoal: String = "",
    val studyClass: String = "",
    val targetExam: String = "",
    val isPublic: Boolean = true
)
