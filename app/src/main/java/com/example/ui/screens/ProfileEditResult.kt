package com.example.ui.screens

/**
 * v1.3.0 — result of the Profile edit dialog.
 *
 * The dialog edits display name, avatar, study goal, class, target exam
 * and the public/private visibility toggle. All fields are written to
 * local preferences on save; name/goal/avatar/visibility also sync to
 * the Supabase leaderboard on a best-effort basis.
 */
data class ProfileEditResult(
    val displayName: String,
    val avatarBytes: ByteArray?,
    val studyGoal: String = "",
    val studyClass: String = "",
    val targetExam: String = "",
    val isPublic: Boolean = true,
    /**
     * v1.3.2 item 8 — bundled preset avatar reference, e.g. `preset:avatar:a03`.
     * When set, it is stored as the avatar value directly (through the
     * existing avatar_url sync path) instead of uploading [avatarBytes].
     */
    val avatarPresetRef: String? = null
)
