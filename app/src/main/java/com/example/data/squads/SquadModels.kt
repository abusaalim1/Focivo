package com.example.data.squads

import kotlinx.serialization.Serializable

/**
 * v1.3.0 — Squads (friends competition) models.
 *
 * Backend schema: supabase/squads_schema.sql (tables `squads` and
 * `squad_members` on the main project). Competition ranking is computed
 * from REAL study time on public.study_leaderboard — no per-member stats
 * live in the squads tables.
 */

/** A study squad. inviteCode is the 6-char join code. */
data class Squad(
    val id: String,
    val name: String,
    val bio: String?,
    val pictureUrl: String?,
    val inviteCode: String,
    val ownerUserId: String,
    val memberCount: Int = 0,
    val createdAt: String? = null,
)

/** One squad member. */
data class SquadMember(
    val id: String,
    val squadId: String,
    val userId: String,
    val displayName: String,
    val avatarUrl: String?,
    val joinedAt: String? = null,
)

/** A member with their REAL study numbers for the standings. */
data class SquadStanding(
    val member: SquadMember,
    val weeklySeconds: Long,
    val allTimeSeconds: Long,
    val rank: Int,
    val isMe: Boolean,
)

const val SQUADS_MAX_MEMBERS = 8
const val SQUADS_JOIN_BASE_URL = "https://focivoapp.vercel.app/join"

fun squadJoinLink(code: String): String = "$SQUADS_JOIN_BASE_URL/$code"

/* ── PostgREST DTOs (snake_case matches the documented schema) ── */

@Serializable
data class SquadDto(
    val id: String,
    val name: String,
    val bio: String? = null,
    val picture_url: String? = null,
    val invite_code: String,
    val owner_user_id: String,
    val created_at: String? = null,
    val updated_at: String? = null,
)

@Serializable
data class SquadMemberDto(
    val id: String,
    val squad_id: String,
    val user_id: String,
    val display_name: String = "",
    val avatar_url: String? = null,
    val joined_at: String? = null,
)

fun SquadDto.toSquad(memberCount: Int = 0) = Squad(
    id = id,
    name = name,
    bio = bio,
    pictureUrl = picture_url,
    inviteCode = invite_code,
    ownerUserId = owner_user_id,
    memberCount = memberCount,
    createdAt = created_at,
)

fun SquadMemberDto.toMember() = SquadMember(
    id = id,
    squadId = squad_id,
    userId = user_id,
    displayName = display_name,
    avatarUrl = avatar_url,
    joinedAt = joined_at,
)
