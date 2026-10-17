package com.example.data.squads

import android.util.Log
import com.example.data.SupabaseManager
import com.example.data.SupabaseService
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.random.Random

/**
 * v1.3.0 — Squads backend access (PostgREST on the main Supabase project).
 *
 * Schema: supabase/focivo_schema.sql. As of 2026-10-17 the Management-API DDL
 * is 403-blocked (token lacks database_read), so the tables may not exist
 * yet. Every call maps a missing-table error to [SquadsUnavailable] so the UI
 * can show the graceful "setting up" state instead of crashing.
 */
object SquadRepository {
    private const val TAG = "SquadRepository"
    private val client get() = SupabaseManager.client

    /** Thrown when the squads tables are absent (PostgREST 42P01 / 404). */
    class SquadsUnavailable(message: String) : Exception(message)

    private fun missingTable(e: Exception): Boolean {
        val msg = (e.message.orEmpty() + " " + e.cause?.message.orEmpty())
        return msg.contains("42P01", ignoreCase = true) ||
            msg.contains("does not exist", ignoreCase = true) ||
            msg.contains("Could not find the table", ignoreCase = true) ||
            msg.contains("schema cache", ignoreCase = true)
    }

    private fun wrap(e: Exception, op: String): Exception {
        Log.w(TAG, "squads $op failed: ${e.message}")
        return if (missingTable(e)) SquadsUnavailable("squads backend not provisioned yet") else e
    }

    /** 6-char unambiguous invite code, e.g. "ABU7K2". */
    fun newInviteCode(): String {
        val alphabet = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
        return (1..6).map { alphabet[Random.nextInt(alphabet.length)] }.joinToString("")
    }

    /** Squads the given user belongs to. */
    suspend fun fetchMySquads(userId: String): Result<List<Squad>> = withContext(Dispatchers.IO) {
        try {
            val memberships = client.from("squad_members").select {
                filter { eq("user_id", userId) }
            }.decodeList<SquadMemberDto>()
            if (memberships.isEmpty()) return@withContext Result.success(emptyList())
            val squadIds = memberships.map { it.squad_id }.distinct()
            val squads = client.from("squads").select {
                filter { isIn("id", squadIds) }
            }.decodeList<SquadDto>()
            val counts = memberships.groupingBy { it.squad_id }.eachCount()
            Result.success(squads.map { it.toSquad(memberCount = counts[it.id] ?: 0) })
        } catch (e: Exception) {
            Result.failure(wrap(e, "fetchMySquads"))
        }
    }

    /** Look up a squad by its invite code (used by typed-code + deep-link join). */
    suspend fun fetchSquadByCode(code: String): Result<Squad?> = withContext(Dispatchers.IO) {
        try {
            val normalized = code.trim().uppercase()
            val list = client.from("squads").select {
                filter { eq("invite_code", normalized) }
            }.decodeList<SquadDto>()
            val dto = list.firstOrNull() ?: return@withContext Result.success(null)
            val members = fetchMembersInternal(dto.id)
            Result.success(dto.toSquad(memberCount = members.size))
        } catch (e: Exception) {
            Result.failure(wrap(e, "fetchSquadByCode"))
        }
    }

    /** Create a squad; the creator becomes member #1. */
    suspend fun createSquad(
        name: String,
        bio: String?,
        pictureUrl: String?,
        ownerUserId: String,
        ownerDisplayName: String,
        ownerAvatarUrl: String?,
    ): Result<Squad> = withContext(Dispatchers.IO) {
        try {
            val cleanName = name.trim().take(48)
            if (cleanName.isBlank()) return@withContext Result.failure(IllegalArgumentException("name required"))
            val cleanBio = bio?.trim()?.take(160)?.ifBlank { null }
            val squadId = java.util.UUID.randomUUID().toString()
            // Collision-safe code generation (6 chars; retry on the rare clash).
            // Insert payload is built explicitly so server-default columns
            // (created_at/updated_at) are never sent as explicit nulls.
            var inviteCode: String? = null
            for (attempt in 1..5) {
                val code = newInviteCode()
                try {
                    client.from("squads").insert(buildJsonObject {
                        put("id", squadId)
                        put("name", cleanName)
                        if (cleanBio != null) put("bio", cleanBio)
                        if (!pictureUrl.isNullOrBlank()) put("picture_url", pictureUrl)
                        put("invite_code", code)
                        put("owner_user_id", ownerUserId)
                    })
                    inviteCode = code
                    break
                } catch (e: Exception) {
                    if (missingTable(e)) throw SquadsUnavailable("squads backend not provisioned yet")
                    Log.w(TAG, "createSquad code retry ($code): ${e.message}")
                }
            }
            val code = inviteCode
                ?: return@withContext Result.failure(Exception("Could not create the group. Please try again."))
            // Owner auto-joins as member #1 (best-effort; squad still exists if this fails).
            runCatching {
                client.from("squad_members").insert(buildJsonObject {
                    put("id", java.util.UUID.randomUUID().toString())
                    put("squad_id", squadId)
                    put("user_id", ownerUserId)
                    put("display_name", ownerDisplayName)
                    if (!ownerAvatarUrl.isNullOrBlank()) put("avatar_url", ownerAvatarUrl)
                })
            }.onFailure { Log.w(TAG, "createSquad owner auto-join failed: ${it.message}") }
            Result.success(
                Squad(
                    id = squadId,
                    name = cleanName,
                    bio = cleanBio,
                    pictureUrl = pictureUrl?.ifBlank { null },
                    inviteCode = code,
                    ownerUserId = ownerUserId,
                    memberCount = 1,
                )
            )
        } catch (e: Exception) {
            if (e is SquadsUnavailable) Result.failure(e) else Result.failure(wrap(e, "createSquad"))
        }
    }

    /**
     * Join a group by invite code. Enforces the 100-member cap (v1.3.2 owner spec)
     * and rejects duplicates. Returns the joined group on success.
     */
    suspend fun joinSquad(
        code: String,
        userId: String,
        displayName: String,
        avatarUrl: String?,
    ): Result<Squad> = withContext(Dispatchers.IO) {
        try {
            val squad = fetchSquadByCode(code).getOrElse { return@withContext Result.failure(it) }
                ?: return@withContext Result.failure(NoSuchElementException("No group found for that code."))
            val members = fetchMembersInternal(squad.id)
            if (members.any { it.user_id == userId }) {
                return@withContext Result.success(squad.copy(memberCount = members.size))
            }
            if (members.size >= SQUADS_MAX_MEMBERS) {
                return@withContext Result.failure(IllegalStateException("This group is full (100 members)."))
            }
            client.from("squad_members").insert(buildJsonObject {
                put("id", java.util.UUID.randomUUID().toString())
                put("squad_id", squad.id)
                put("user_id", userId)
                put("display_name", displayName)
                if (!avatarUrl.isNullOrBlank()) put("avatar_url", avatarUrl)
            })
            Result.success(squad.copy(memberCount = members.size + 1))
        } catch (e: Exception) {
            if (e is SquadsUnavailable) Result.failure(e) else Result.failure(wrap(e, "joinSquad"))
        }
    }

    /** Leave a squad. Owners leaving transfers nothing — owner can also delete. */
    suspend fun leaveSquad(squadId: String, userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            client.from("squad_members").delete {
                filter {
                    eq("squad_id", squadId)
                    eq("user_id", userId)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(wrap(e, "leaveSquad"))
        }
    }

    /** Delete a squad (owner only — enforced by the caller). */
    suspend fun deleteSquad(squadId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            client.from("squads").delete {
                filter { eq("id", squadId) }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(wrap(e, "deleteSquad"))
        }
    }

    private suspend fun fetchMembersInternal(squadId: String): List<SquadMemberDto> {
        return client.from("squad_members").select {
            filter { eq("squad_id", squadId) }
        }.decodeList<SquadMemberDto>()
    }

    /** Members of a squad. */
    suspend fun fetchMembers(squadId: String): Result<List<SquadMember>> = withContext(Dispatchers.IO) {
        try {
            Result.success(fetchMembersInternal(squadId).map { it.toMember() })
        } catch (e: Exception) {
            Result.failure(wrap(e, "fetchMembers"))
        }
    }

    /**
     * Squad standings ranked by REAL study time from public.study_leaderboard.
     * Primary sort: this week's seconds; tiebreak: all-time seconds.
     */
    suspend fun fetchStandings(squadId: String, myUserId: String): Result<List<SquadStanding>> =
        withContext(Dispatchers.IO) {
            try {
                val members = fetchMembersInternal(squadId).map { it.toMember() }
                if (members.isEmpty()) return@withContext Result.success(emptyList())
                val leaderboard = runCatching {
                    SupabaseService.getInstance().fetchLeaderboard()
                }.getOrElse { emptyList() }
                val byUser = leaderboard.associateBy { it.user_id }
                val ranked = members.mapNotNull { m ->
                    val row = byUser[m.userId]
                    // v1.3.2: private profiles (is_public=false) show neither
                    // stats nor avatar to other users — including fellow group
                    // members. The member still sees their own entry.
                    if (m.userId != myUserId && row?.is_public == false) return@mapNotNull null
                    SquadStanding(
                        member = m,
                        weeklySeconds = row?.weekly_study_seconds ?: 0L,
                        allTimeSeconds = row?.study_seconds ?: 0L,
                        rank = 0,
                        isMe = m.userId == myUserId,
                    )
                }.sortedWith(
                    compareByDescending<SquadStanding> { it.weeklySeconds }
                        .thenByDescending { it.allTimeSeconds }
                        .thenBy { it.member.displayName.lowercase() }
                ).mapIndexed { i, s -> s.copy(rank = i + 1) }
                Result.success(ranked)
            } catch (e: Exception) {
                Result.failure(wrap(e, "fetchStandings"))
            }
        }
}
