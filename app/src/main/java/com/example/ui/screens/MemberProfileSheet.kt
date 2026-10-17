package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SupabaseService
import com.example.data.squads.MemberProfileStats
import com.example.data.squads.SquadMember
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.SerifItalicFontFamily
import com.example.ui.theme.focivoEyebrow

/**
 * Group member profile (v1.3.2, item 7) — modelled on the owner's Regain
 * reference: a dark premium card showing REAL synced data only.
 *
 * Privacy (owner-strict): a private member (isPublic=false, not the viewer)
 * sees NOTHING but name + avatar + the locked "This profile is private"
 * state — no weekly focus, no rank, no sessions, no average. The viewer
 * always sees their own full stats.
 */
data class MemberProfileData(
    val userId: String,
    val displayName: String,
    val avatarUrl: String?,
    val isMe: Boolean,
    val isPublic: Boolean,
    val weeklySeconds: Long,
    /** Null = weekly_sessions column absent server-side → hide the tiles. */
    val weeklySessions: Int?,
    val rank: Int,
    /** REAL total user count from the leaderboard — never a placeholder. */
    val totalUsers: Int,
) {
    val isLocked: Boolean get() = !isPublic && !isMe
}

/**
 * Loads the profile data for [member] from the live leaderboard.
 * Returns null when the leaderboard cannot be read.
 */
suspend fun loadMemberProfileData(member: SquadMember, myUserId: String): MemberProfileData? {
    return try {
        val board = SupabaseService.getInstance().fetchLeaderboard()
        if (board.isEmpty()) return null
        val byUser = board.associateBy { it.user_id }
        val row = byUser[member.userId]
        val weeklyByUser = board.associate { it.user_id to it.weekly_study_seconds }
        val isMe = member.userId == myUserId
        val isPublic = row?.is_public ?: true
        MemberProfileData(
            userId = member.userId,
            displayName = member.displayName,
            avatarUrl = member.avatarUrl,
            isMe = isMe,
            isPublic = isPublic,
            weeklySeconds = row?.weekly_study_seconds ?: 0L,
            weeklySessions = row?.weekly_sessions,
            rank = MemberProfileStats.computeRank(weeklyByUser, member.userId),
            totalUsers = board.size,
        )
    } catch (_: Exception) {
        null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberProfileSheet(
    member: SquadMember,
    myUserId: String,
    onDismiss: () -> Unit,
) {
    var data by remember { mutableStateOf<MemberProfileData?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(member.userId) {
        data = null
        failed = false
        data = loadMemberProfileData(member, myUserId) ?: run { failed = true; null }
    }
    MemberProfileSheetContent(data = data, failed = failed, onDismiss = onDismiss)
}

/** Test/screenshot entry point — renders [data] directly, no network. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberProfileSheetContent(
    data: MemberProfileData?,
    failed: Boolean,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF121513),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.25f))
            )
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp),
            contentAlignment = Alignment.Center,
        ) {
            when {
                failed -> Text(
                    "Could not load this profile. Check your connection and try again.",
                    style = TextStyle(fontFamily = PoppinsFontFamily, fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f)),
                    textAlign = TextAlign.Center,
                )
                data == null -> CircularProgressIndicator(color = Color(0xFF8CE000), modifier = Modifier.size(28.dp))
                else -> MemberProfileBody(data = data)
            }
        }
    }
}

@Composable
private fun MemberProfileBody(data: MemberProfileData) {
    val lime = Color(0xFF8CE000)
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Avatar + name.
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .border(2.dp, lime.copy(alpha = 0.5f), CircleShape),
        ) {
            LeaderboardAvatar(
                avatarUrl = data.avatarUrl,
                displayName = data.displayName,
                modifier = Modifier.fillMaxWidth(),
                isCurrentUser = data.isMe,
                textSizeSp = 24,
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            data.displayName + if (data.isMe) " (you)" else "",
            style = TextStyle(
                fontFamily = SerifItalicFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                color = Color.White,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )

        if (data.isLocked) {
            // ── STRICT privacy: name + avatar + lock. NOTHING else. ──
            Spacer(Modifier.height(20.dp))
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.07f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = "Private",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "This profile is private",
                style = TextStyle(
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = Color.White,
                ),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Their study stats are hidden.",
                style = TextStyle(
                    fontFamily = PoppinsFontFamily,
                    fontSize = 12.5.sp,
                    color = Color.White.copy(alpha = 0.55f),
                ),
                textAlign = TextAlign.Center,
            )
        } else {
            Spacer(Modifier.height(20.dp))
            // This week's focus.
            Text("THIS WEEK'S FOCUS", style = focivoEyebrow().copy(color = Color.White.copy(alpha = 0.5f)))
            Spacer(Modifier.height(6.dp))
            Text(
                MemberProfileStats.formatDuration(data.weeklySeconds),
                style = TextStyle(
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 40.sp,
                    color = Color.White,
                ),
            )
            Spacer(Modifier.height(4.dp))
            val topPct = MemberProfileStats.formatTopPercent(data.rank, data.totalUsers)
            Text(
                "Rank ${data.rank}" + (topPct?.let { " • $it" } ?: ""),
                style = TextStyle(
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = lime,
                ),
            )
            // Sessions tiles — hidden entirely when the column is absent.
            val avg = MemberProfileStats.formatAvgFocus(data.weeklySeconds, data.weeklySessions)
            if (data.weeklySessions != null) {
                Spacer(Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MemberProfileTile(
                        label = "No. of sessions",
                        value = "${data.weeklySessions}",
                        modifier = Modifier.weight(1f),
                    )
                    MemberProfileTile(
                        label = "Avg focus / session",
                        value = avg ?: "—",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun MemberProfileTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color.White.copy(alpha = 0.09f), RoundedCornerShape(16.dp))
            .padding(vertical = 14.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            value,
            style = TextStyle(
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = Color.White,
            ),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = TextStyle(
                fontFamily = PoppinsFontFamily,
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.55f),
            ),
            textAlign = TextAlign.Center,
        )
    }
}
