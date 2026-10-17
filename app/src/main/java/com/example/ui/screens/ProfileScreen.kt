package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import com.example.ui.components.WidgetsShowcaseSheet
import com.example.ui.components.PremiumSecondaryButton
import com.example.ui.components.FeatureIcon
import androidx.compose.material3.CircularProgressIndicator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import coil.compose.AsyncImage
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.util.Calendar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.FocusSessionEntity
import com.example.data.model.UserPreferencesEntity
import com.example.ui.components.GlassCard
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.pressFeedback
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.SerifItalicFontFamily
import com.example.ui.theme.focivoTokens
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import com.example.R
import com.example.ui.theme.BottomNavClearance
import com.example.ui.theme.isAppInDarkTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// v1.2.3 design-unification — owner's clean-redesign language:
// Poppins body, Instrument Serif italic for the page headline,
@Composable
fun ProfileAvatarImage(
    photoUrl: String?,
    monogram: String,
    modifier: Modifier = Modifier,
    textSizeSp: Int = 20
) {
    val bitmap = remember(photoUrl) {
        if (!photoUrl.isNullOrBlank() && !photoUrl.startsWith("http://") && !photoUrl.startsWith("https://")) {
            try {
                val clean = if (photoUrl.contains(",")) photoUrl.substringAfter(",") else photoUrl
                val bytes = android.util.Base64.decode(clean, android.util.Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (_: Exception) {
                null
            }
        } else null
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "User Avatar",
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } else if (!photoUrl.isNullOrBlank()) {
        AsyncImage(
            model = photoUrl,
            contentDescription = "User Avatar",
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } else {
        Text(
            text = monogram,
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = PoppinsFontFamily,
                color = focivoTokens().text,
                fontWeight = FontWeight.Bold,
                fontSize = textSizeSp.sp
            )
        )
    }
}

data class CleanBadge(
    val title: String,
    val icon: ImageVector,
    val isUnlocked: Boolean,
    // v1.4.0-round2: 3D icon + detail-dialog data (all from real stored data)
    val iconRes: Int = 0,
    val description: String = "",
    val progressText: String = "",
    val progressFraction: Float? = null
)

/**
 * v1.4.3: pure badge computation shared by ProfileScreen (display) and
 * FocuslyViewModel (achievement-unlock notifications). Same inputs, same
 * results — no drift.
 */
fun computeAchievementBadges(
    sessionCount: Int,
    distinctDaysCount: Int,
    totalSeconds: Long,
    bestSessionSeconds: Int,
    shieldAttempts: Int,
    isAppBlockerEnabled: Boolean
): List<CleanBadge> = listOf(
    CleanBadge(
        "First Flow", Icons.Default.Star, sessionCount >= 1,
        iconRes = R.drawable.ach_first_flow,
        description = "Complete your very first focus session.",
        progressText = "${minOf(sessionCount, 1)}/1 session",
        progressFraction = minOf(sessionCount, 1) / 1f
    ),
    CleanBadge(
        "60m Deep", Icons.Default.HourglassTop, bestSessionSeconds >= 3600,
        iconRes = R.drawable.ach_60m_deep,
        description = "Finish a single session of 60 minutes or more.",
        progressText = "${bestSessionSeconds / 60}/60 min best",
        progressFraction = (bestSessionSeconds / 3600f).coerceIn(0f, 1f)
    ),
    CleanBadge(
        "7 Days Flow", Icons.Default.DateRange, distinctDaysCount >= 7,
        iconRes = R.drawable.ach_7days_flow,
        description = "Focus on 7 different days.",
        progressText = "${minOf(distinctDaysCount, 7)}/7 days",
        progressFraction = minOf(distinctDaysCount, 7) / 7f
    ),
    CleanBadge(
        "25h Master", Icons.Default.MilitaryTech, totalSeconds >= 25 * 3600,
        iconRes = R.drawable.ach_25h_master,
        description = "Accumulate 25 hours of total focus time.",
        progressText = "${"%.1f".format(totalSeconds / 3600f)}/25 h",
        progressFraction = (totalSeconds / (25 * 3600f)).coerceIn(0f, 1f)
    ),
    CleanBadge(
        "Focus Shield", Icons.Default.Security,
        isAppBlockerEnabled || (shieldAttempts > 0),
        iconRes = R.drawable.ach_focus_shield,
        description = "Block your first distraction with Focus Shield.",
        progressText = if (shieldAttempts > 0) "$shieldAttempts interceptions" else "No interceptions yet",
        progressFraction = if (shieldAttempts > 0) 1f else 0f
    ),
    CleanBadge(
        "100 Blocks", Icons.Default.WorkspacePremium, sessionCount >= 100,
        iconRes = R.drawable.ach_100_blocks,
        description = "Complete 100 focus sessions.",
        progressText = "${minOf(sessionCount, 100)}/100 sessions",
        progressFraction = minOf(sessionCount, 100) / 100f
    )
)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    userPreferences: UserPreferencesEntity?,
    sessions: List<FocusSessionEntity> = emptyList(),
    isProfileLoading: Boolean = false,
    profileError: String? = null,
    onRetryFetchProfile: () -> Unit = {},
    onOpenSettings: () -> Unit,
    onOpenAlarmStudio: () -> Unit = {},
    onOpenShieldHub: () -> Unit = {},
    onOpenSundayRecap: () -> Unit = {},
    onOpenSupportLockZen: () -> Unit = {},
    onOpenSessionHistory: () -> Unit = {},
    // v1.1: Mascot Buddy screen.
    onOpenMascot: () -> Unit = {},
    onCheckForUpdates: () -> Unit = {},
    onLogout: () -> Unit = {},
    onUpdateProfile: (ProfileEditResult) -> Unit = {},
    modifier: Modifier = Modifier,
    // v1.4.0-round2: test hook — screenshot tests open a badge directly
    initialSelectedBadgeTitle: String? = null
) {
    val context = LocalContext.current
    val isDark = isAppInDarkTheme()
    var showEditProfileDialog by remember { mutableStateOf(false) }
    // v1.4.0-round2: achievement tapped -> detail dialog
    var selectedBadge by remember { mutableStateOf<CleanBadge?>(null) }
    var showPrivacyPolicySheet by remember { mutableStateOf(false) }
    var showWidgetsSheet by remember { mutableStateOf(false) }
    val widgetSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Semantic colors — owner's Full App Redesign token system (dark + light).
    val T = focivoTokens()
    val cardBg = T.surface
    val cardBorder = T.line
    val textPrimary = T.text
    val textSecondary = T.muted

    val sessionCount = sessions.count { it.durationSeconds > 0 }
    val totalSeconds = sessions.sumOf { it.durationSeconds }
    val hoursFocused = if (totalSeconds >= 3600) {
        "${totalSeconds / 3600}h"
    } else if (totalSeconds > 0) {
        "${totalSeconds / 60}m"
    } else {
        "0h"
    }
    val persona = userPreferences?.focusIdentity ?: "The Scholar of Deep Study"
    val alarmRingtone = userPreferences?.alarmRingtone ?: "Zen Bell"

    // Real name & email bound from user preferences / Supabase sync
    val rawName = userPreferences?.currentUserName?.trim()
    val rawEmail = userPreferences?.currentUserEmail?.trim()

    val userName = when {
        !rawName.isNullOrBlank() && rawName != "Deep Worker" -> rawName
        !rawEmail.isNullOrBlank() -> rawEmail.substringBefore("@")
        else -> "Student"
    }
    val userEmail = rawEmail.orEmpty()

    // Dynamic Monogram Avatar (Never hardcoded DW)
    val monogram = if (!userName.isNullOrBlank() && userName != "Student") {
        userName.split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .map { it.first().uppercase() }
            .joinToString("")
            .ifEmpty { userName.take(1).uppercase() }
    } else if (userEmail.isNotBlank()) {
        userEmail.take(2).uppercase()
    } else {
        "S"
    }

    val distinctDaysCount = remember(sessions) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        sessions.filter { it.completedAt > 0 && it.durationSeconds > 0 }.map { sdf.format(Date(it.completedAt)) }.toSet().size
    }

    val hasShieldPermissions = remember(context) {
        com.example.util.PermissionUtils.hasUsageStatsPermission(context) &&
        com.example.util.PermissionUtils.hasOverlayPermission(context) &&
        com.example.util.AiStudyGuardManager.isAccessibilityPermissionGranted(context)
    }
    val isShieldActive = hasShieldPermissions && (userPreferences?.isAppBlockerEnabled == true)

    val dailyGoalMinutes = (userPreferences?.dailyGoalMinutes ?: 240).coerceAtLeast(60)
    val todayStart = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val todayMinutes = remember(sessions, todayStart) {
        sessions.filter { it.completedAt >= todayStart && it.durationSeconds > 0 }.sumOf { it.durationSeconds } / 60
    }
    val dailyProgress = if (dailyGoalMinutes > 0 && todayMinutes > 0) {
        (todayMinutes.toFloat() / dailyGoalMinutes).coerceIn(0f, 1f)
    } else {
        0.1f
    }

    // v1.4.0-round2: achievements with real progress (no fake counters).
    val bestSessionSeconds = remember(sessions) { sessions.maxOfOrNull { it.durationSeconds } ?: 0 }
    val shieldAttempts = userPreferences?.shieldBlockedAttempts ?: 0
    // v1.4.3: shared pure computation (also used for unlock notifications).
    val badges = remember(sessionCount, distinctDaysCount, totalSeconds, bestSessionSeconds, shieldAttempts, userPreferences?.isAppBlockerEnabled) {
        computeAchievementBadges(
            sessionCount = sessionCount,
            distinctDaysCount = distinctDaysCount,
            totalSeconds = totalSeconds.toLong(),
            bestSessionSeconds = bestSessionSeconds,
            shieldAttempts = shieldAttempts,
            isAppBlockerEnabled = userPreferences?.isAppBlockerEnabled == true
        )
    }
    // v1.4.0-round2: test hook — open a badge directly for screenshots
    LaunchedEffect(initialSelectedBadgeTitle) {
        if (initialSelectedBadgeTitle != null && selectedBadge == null) {
            selectedBadge = badges.firstOrNull { it.title == initialSelectedBadgeTitle }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(T.bg)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .testTag("profile_screen"),
            contentPadding = PaddingValues(top = 16.dp, bottom = BottomNavClearance),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Profile",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontFamily = SerifItalicFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = T.text,
                                fontSize = 30.sp,
                                lineHeight = 35.sp,
                                letterSpacing = (-0.6).sp
                            )
                        )
                    }

                                        IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.size(46.dp).testTag("settings_button")
                    ) {
                        FeatureIcon(drawableRes = R.drawable.ic3d_gear, contentDescription = "Settings", size = 46.dp)
                    }
                }
            }

            // Support Focivo / Donation Option (Positioned prominently near the very top)
            item {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_support_focivo_card"),
                    shape = RoundedCornerShape(20.dp),
                    onClick = onOpenSupportLockZen
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                        FeatureIcon(
                                drawableRes = R.drawable.ic3d_heart,
                                contentDescription = "Support Focivo",
                                size = 46.dp
                            )

                            Spacer(modifier = Modifier.width(14.dp))

                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Support Focivo",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontFamily = PoppinsFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            color = textPrimary,
                                            fontSize = 15.sp
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(T.softAccent)
                                            .padding(horizontal = 7.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "UPI donate",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = PoppinsFontFamily,
                                                color = T.accentText,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 9.sp,
                                                letterSpacing = 0.8.sp
                                            )
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Voluntary contribution · keep Focivo free",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = PoppinsFontFamily,
                                        color = textSecondary,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Hero Focus Identity Card with Loading / Retry / Real Profile Binding
            item {
                LiquidGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(22.dp)
                    ) {
                        if (isProfileLoading) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = T.accent,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Syncing profile from Supabase...",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = PoppinsFontFamily,
                                        color = textSecondary,
                                        fontSize = 13.sp
                                    )
                                )
                            }
                        } else if (profileError != null && userEmail.isBlank()) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = profileError,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = PoppinsFontFamily,
                                        color = T.danger
                                    )
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(T.softAccent)
                                        .clickable { onRetryFetchProfile() }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Retry",
                                        tint = T.accentText,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Retry Sync",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontFamily = PoppinsFontFamily,
                                            color = T.accentText,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }
                        } else {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Avatar (Image or Monogram)
                                    val photoUrl = userPreferences?.currentUserPhotoUrl
                                    Box(
                                        modifier = Modifier
                                            .size(58.dp)
                                            .clip(CircleShape)
                                            .background(T.accent)
                                            .clickable { showEditProfileDialog = true },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        ProfileAvatarImage(
                                            photoUrl = photoUrl,
                                            monogram = monogram,
                                            modifier = Modifier.fillMaxSize(),
                                            textSizeSp = 20
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(16.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = userName,
                                                style = MaterialTheme.typography.titleMedium.copy(
                                                    fontFamily = PoppinsFontFamily,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 18.sp,
                                                    color = textPrimary
                                                )
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            IconButton(
                                                onClick = { showEditProfileDialog = true },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Edit,
                                                    contentDescription = "Edit Profile",
                                                    tint = T.accentText,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                        val studyGoalText = userPreferences?.primaryStudyGoal?.trim().orEmpty()
                                        val studyClassText = userPreferences?.studentClassLevel?.trim().orEmpty()
                                        val targetExamText = userPreferences?.targetExam?.trim().orEmpty()
                                        val isPublicProfile = userPreferences?.isProfilePublic ?: true
                                        if (studyGoalText.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(1.dp))
                                            Text(
                                                text = studyGoalText,
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontFamily = SerifItalicFontFamily,
                                                    fontSize = 13.sp,
                                                    color = T.accentText
                                                ),
                                                maxLines = 2
                                            )
                                        }
                                        val classExamLine = listOf(studyClassText, targetExamText)
                                            .filter { it.isNotBlank() }
                                            .joinToString(" · ")
                                        if (classExamLine.isNotBlank()) {
                                            Text(
                                                text = classExamLine,
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontFamily = PoppinsFontFamily,
                                                    fontSize = 12.sp,
                                                    color = textSecondary
                                                ),
                                                maxLines = 1
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .testTag("profile_privacy_pill")
                                                .clip(RoundedCornerShape(20.dp))
                                                .background(
                                                    if (isPublicProfile) T.accent.copy(alpha = 0.16f)
                                                    else textSecondary.copy(alpha = 0.14f)
                                                )
                                                .clickable { showEditProfileDialog = true }
                                                .padding(horizontal = 10.dp, vertical = 5.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = if (isPublicProfile) "Public Profile" else "Private Profile",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontFamily = PoppinsFontFamily,
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 11.sp,
                                                    color = if (isPublicProfile) T.accentText else textSecondary
                                                )
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(
                                            text = "$sessionCount Completed Sessions · $hoursFocused Logged",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = PoppinsFontFamily,
                                                color = textSecondary,
                                                fontWeight = FontWeight.Medium
                                            )
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(18.dp))

                                // Daily Study Progress
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(CircleShape)
                                        .background(T.line)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(dailyProgress.coerceAtLeast(0.08f))
                                            .fillMaxHeight()
                                            .clip(CircleShape)
                                            .background(T.accent)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Quick Stats Strip — mockup: flat stats with hairline dividers.
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    @Composable
                    fun StatCell(value: String, label: String, valueColor: Color, showDivider: Boolean) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (showDivider) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterStart)
                                        .width(1.dp)
                                        .height(44.dp)
                                        .background(T.line)
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = value,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontFamily = PoppinsFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        color = valueColor,
                                        fontSize = 24.sp,
                                        letterSpacing = (-0.5).sp
                                    )
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = label.uppercase(Locale.getDefault()),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = PoppinsFontFamily,
                                        color = T.muted,
                                        fontSize = 10.sp,
                                        letterSpacing = 0.8.sp
                                    )
                                )
                            }
                        }
                    }
                    StatCell(value = "$sessionCount", label = "Sessions", valueColor = T.text, showDivider = false)
                    StatCell(value = hoursFocused, label = "Focused", valueColor = T.text, showDivider = true)
                }
            }



            // Focus Shield Shortcut Card
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    onClick = { onOpenShieldHub() }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                        FeatureIcon(
                                drawableRes = R.drawable.ic3d_shield,
                                contentDescription = "Focus Shield",
                                size = 46.dp
                            )

                            Spacer(modifier = Modifier.width(14.dp))

                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Focus Shield",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontFamily = PoppinsFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            color = textPrimary,
                                            fontSize = 15.sp
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(if (isShieldActive) T.softAccent else T.surface2)
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = if (isShieldActive) "ARMED" else if (!hasShieldPermissions) "SETUP" else "IDLE",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = PoppinsFontFamily,
                                                color = if (isShieldActive) T.accentText else textSecondary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 9.sp
                                            )
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Distraction App & Site Blocker",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = PoppinsFontFamily,
                                        color = textSecondary,
                                        fontSize = 12.sp
                                    )
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = null,
                            tint = textSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // v1.1: Mascot Buddy Card — level badge + wardrobe entry.
            item {
                val mascotLevel = com.example.util.levelForPoints(userPreferences?.focusPoints ?: 0)
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    onClick = { onOpenMascot() }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(
                                        T.surface2
                                    )
                                    .border(
                                        1.dp,
                                        T.accent.copy(alpha = 0.4f),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                com.example.ui.components.BuddyMascot(
                                    modifier = Modifier.width(40.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Mascot Buddy",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontFamily = PoppinsFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            color = textPrimary,
                                            fontSize = 15.sp
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(T.accent)
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = mascotLevel.displayName.uppercase(),
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = PoppinsFontFamily,
                                                color = T.text,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 9.sp
                                            )
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Dress your buddy, level up with Focus Points",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = PoppinsFontFamily,
                                        color = textSecondary,
                                        fontSize = 12.sp
                                    )
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = null,
                            tint = textSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // Apple Glassmorphism Home Widgets Card
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    onClick = { showWidgetsSheet = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                        FeatureIcon(
                                drawableRes = R.drawable.ic3d_widgets,
                                contentDescription = "Home Widgets",
                                size = 46.dp
                            )

                            Spacer(modifier = Modifier.width(14.dp))

                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Home Screen Widgets",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontFamily = PoppinsFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            color = textPrimary,
                                            fontSize = 15.sp
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(T.softAccent)
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "5 WIDGETS",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = PoppinsFontFamily,
                                                color = T.accentText,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 9.sp
                                            )
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Apple Glassmorphism & Poppins fonts",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = PoppinsFontFamily,
                                        color = textSecondary,
                                        fontSize = 12.sp
                                    )
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = null,
                            tint = textSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // Sunday Study Recap Card (Glassmorphism & Real-time Supabase Data - Sunday Only)
            item {
                val isTodaySunday = remember {
                    java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK) == java.util.Calendar.SUNDAY
                }

                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    onClick = {
                        if (isTodaySunday) {
                            onOpenSundayRecap()
                        } else {
                            android.widget.Toast.makeText(
                                context,
                                "Sunday Study Recap is only available on Sundays!",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                        FeatureIcon(
                                drawableRes = R.drawable.ic3d_calendar,
                                contentDescription = "Sunday Recap",
                                size = 46.dp
                            )

                            Spacer(modifier = Modifier.width(14.dp))

                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Sunday Focus Recap",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontFamily = PoppinsFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            color = textPrimary,
                                            fontSize = 15.sp
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(if (isTodaySunday) T.softAccent else T.line)
                                            .padding(horizontal = 7.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = if (isTodaySunday) "READY" else "SUNDAY ONLY",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = PoppinsFontFamily,
                                                color = if (isTodaySunday) T.accentText else T.muted,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 9.sp
                                            )
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isTodaySunday) "Weekly analytics & subject breakdown" else "Unlocks every Sunday with your weekly study review",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = PoppinsFontFamily,
                                        color = textSecondary,
                                        fontSize = 12.sp
                                    )
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Past Focus Sessions Log Card (Firestore Cloud History)
            item {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_session_logs_card"),
                    shape = RoundedCornerShape(20.dp),
                    onClick = onOpenSessionHistory
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                        FeatureIcon(
                                drawableRes = R.drawable.ic3d_history,
                                contentDescription = "Session History",
                                size = 46.dp
                            )

                            Spacer(modifier = Modifier.width(14.dp))

                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Past Focus Sessions Log",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontFamily = PoppinsFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            color = textPrimary,
                                            fontSize = 15.sp
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(T.softAccent)
                                            .padding(horizontal = 7.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "${sessions.size} LOGS",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = PoppinsFontFamily,
                                                color = T.accentText,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 9.sp
                                            )
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Timeline logs with start/end time and duration",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = PoppinsFontFamily,
                                        color = textSecondary,
                                        fontSize = 12.sp
                                    )
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Alarm Studio Shortcut Card
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    onClick = { onOpenAlarmStudio() }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                        FeatureIcon(
                                drawableRes = R.drawable.ic3d_alarm,
                                contentDescription = "Alarm Studio",
                                size = 46.dp
                            )

                            Spacer(modifier = Modifier.width(14.dp))

                            Column {
                                Text(
                                    text = "Alarm Studio",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontFamily = PoppinsFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        color = textPrimary,
                                        fontSize = 15.sp
                                    )
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Ringtones · Active: $alarmRingtone",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = PoppinsFontFamily,
                                        color = textSecondary,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Badges Section Header
            item {
                Text(
                    text = "MILESTONES",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = PoppinsFontFamily,
                        color = textSecondary,
                        letterSpacing = 1.4.sp,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            // Badges Grid
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        badges.take(3).forEach { badge ->
                            BadgeGridItem(badge = badge, isDark = isDark, cardBg = cardBg, cardBorder = cardBorder, textPrimary = textPrimary, textSecondary = textSecondary, onClick = { selectedBadge = badge }, modifier = Modifier.weight(1f))
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        badges.drop(3).take(3).forEach { badge ->
                            BadgeGridItem(badge = badge, isDark = isDark, cardBg = cardBg, cardBorder = cardBorder, textPrimary = textPrimary, textSecondary = textSecondary, onClick = { selectedBadge = badge }, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            // In-App Update Checker Card
            item {
                val currentVersionName = remember {
                    try {
                        val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                        pInfo.versionName ?: "1.0"
                    } catch (_: Exception) {
                        "1.0"
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                GlassCard(
                    modifier = Modifier.fillMaxWidth().testTag("check_for_updates_card"),
                    shape = RoundedCornerShape(20.dp),
                    onClick = { onCheckForUpdates() }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                        FeatureIcon(
                                drawableRes = R.drawable.ic3d_download,
                                contentDescription = "Check for Updates",
                                size = 46.dp
                            )

                            Spacer(modifier = Modifier.width(14.dp))

                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Check for Updates",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontFamily = PoppinsFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            color = textPrimary,
                                            fontSize = 15.sp
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(T.softAccent)
                                            .padding(horizontal = 7.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "v$currentVersionName",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = PoppinsFontFamily,
                                                color = T.accentText,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 9.sp
                                            )
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Tap to check for new features & fixes",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = PoppinsFontFamily,
                                        color = textSecondary,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Privacy Policy Option
            item {
                Spacer(modifier = Modifier.height(10.dp))
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    onClick = { showPrivacyPolicySheet = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(T.softAccent),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PrivacyTip,
                                    contentDescription = "Privacy Policy",
                                    tint = T.accentText,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column {
                                Text(
                                    text = "Privacy Policy",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontFamily = PoppinsFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        color = textPrimary,
                                        fontSize = 15.sp
                                    )
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Read our full privacy & data terms",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = PoppinsFontFamily,
                                        color = textSecondary,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Sign Out action
            item {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(T.danger.copy(alpha = 0.14f))
                        .border(1.dp, T.danger.copy(alpha = 0.28f), RoundedCornerShape(18.dp))
                        .clickable { onLogout() }
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ExitToApp,
                            contentDescription = "Sign Out",
                            tint = T.danger,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Sign Out",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontFamily = PoppinsFontFamily,
                                color = T.danger,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        )
                    }
                }
            }
        }
    }

    // v1.4.0-round2: achievement detail dialog
    selectedBadge?.let { badge ->
        AchievementDetailDialog(
            badge = badge,
            isDark = isDark,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            cardBg = cardBg,
            cardBorder = cardBorder,
            onDismiss = { selectedBadge = null }
        )
    }

    if (showEditProfileDialog) {        var editedName by remember { mutableStateOf(userName) }
        var editedGoal by remember { mutableStateOf(userPreferences?.primaryStudyGoal.orEmpty()) }
        var editedClass by remember { mutableStateOf(userPreferences?.studentClassLevel.orEmpty()) }
        var editedExam by remember { mutableStateOf(userPreferences?.targetExam.orEmpty()) }
        var editedIsPublic by remember { mutableStateOf(userPreferences?.isProfilePublic ?: true) }
        var selectedPhotoUri by remember { mutableStateOf<android.net.Uri?>(null) }
        val photoPickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia()
        ) { uri ->
            if (uri != null) {
                selectedPhotoUri = uri
            }
        }

        AlertDialog(
            onDismissRequest = { showEditProfileDialog = false },
            title = {
                Text(
                    text = "Edit Profile",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Bold
                    )
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(T.accent)
                            .clickable {
                                photoPickerLauncher.launch(
                                    androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (selectedPhotoUri != null) {
                            AsyncImage(
                                model = selectedPhotoUri,
                                contentDescription = "Selected Avatar",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            ProfileAvatarImage(
                                photoUrl = userPreferences?.currentUserPhotoUrl,
                                monogram = monogram,
                                modifier = Modifier.fillMaxSize(),
                                textSizeSp = 24
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "Change Photo",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Tap circle to choose photo",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = PoppinsFontFamily,
                            color = textSecondary,
                            fontSize = 11.sp
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = editedName,
                        onValueChange = { editedName = it },
                        label = { Text("Display Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = editedGoal,
                        onValueChange = { editedGoal = it },
                        label = { Text("Study Goal") },
                        placeholder = { Text("e.g. Crack JEE with daily deep work") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = editedClass,
                            onValueChange = { editedClass = it },
                            label = { Text("Class") },
                            placeholder = { Text("Class 11") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        OutlinedTextField(
                            value = editedExam,
                            onValueChange = { editedExam = it },
                            label = { Text("Target Exam") },
                            placeholder = { Text("JEE / NEET") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    if (userEmail.isNotBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(textSecondary.copy(alpha = 0.08f))
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = "Account Email",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = PoppinsFontFamily,
                                    color = textSecondary,
                                    fontSize = 11.sp
                                )
                            )
                            Text(
                                text = userEmail,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = PoppinsFontFamily,
                                    fontWeight = FontWeight.Medium,
                                    color = textPrimary,
                                    fontSize = 14.sp
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("profile_public_switch")
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { editedIsPublic = !editedIsPublic }
                            .padding(horizontal = 4.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Public Profile",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = PoppinsFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    color = textPrimary,
                                    fontSize = 14.sp
                                )
                            )
                            Text(
                                text = if (editedIsPublic) "Others can see your name, avatar, goal and stats"
                                else "Only you can see your profile",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = PoppinsFontFamily,
                                    color = textSecondary,
                                    fontSize = 12.sp
                                )
                            )
                        }
                        Switch(
                            checked = editedIsPublic,
                            onCheckedChange = { editedIsPublic = it }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val avatarBytes = selectedPhotoUri?.let { uri ->
                            try {
                                val inputStream = context.contentResolver.openInputStream(uri)
                                val bitmap = BitmapFactory.decodeStream(inputStream)
                                inputStream?.close()
                                if (bitmap != null) {
                                    val maxDim = 512
                                    val scaledBitmap = if (bitmap.width > maxDim || bitmap.height > maxDim) {
                                        val aspect = bitmap.width.toFloat() / bitmap.height.toFloat()
                                        val (w, h) = if (aspect >= 1f) maxDim to (maxDim / aspect).toInt() else (maxDim * aspect).toInt() to maxDim
                                        Bitmap.createScaledBitmap(bitmap, w, h, true)
                                    } else {
                                        bitmap
                                    }
                                    val baos = ByteArrayOutputStream()
                                    scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos)
                                    baos.toByteArray()
                                } else null
                            } catch (e: Exception) {
                                null
                            }
                        }
                        onUpdateProfile(
                            ProfileEditResult(
                                displayName = editedName.trim(),
                                avatarBytes = avatarBytes,
                                studyGoal = editedGoal.trim(),
                                studyClass = editedClass.trim(),
                                targetExam = editedExam.trim(),
                                isPublic = editedIsPublic
                            )
                        )
                        showEditProfileDialog = false
                    }
                ) {
                    Text("Save Changes")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditProfileDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showPrivacyPolicySheet) {
        com.example.ui.components.PrivacyPolicySheet(
            onDismiss = { showPrivacyPolicySheet = false }
        )
    }

    if (showWidgetsSheet) {
        WidgetsShowcaseSheet(
            sheetState = widgetSheetState,
            onDismiss = { showWidgetsSheet = false }
        )
    }
}

@Composable
private fun BadgeGridItem(
    badge: CleanBadge,
    isDark: Boolean,
    cardBg: Color,
    cardBorder: Color,
    textPrimary: Color,
    textSecondary: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassCard(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(56.dp),
                contentAlignment = Alignment.Center
            ) {
                if (badge.iconRes != 0) {
                    // 3D icon tile (icons have solid corners -> clip rounded).
                    // Locked: dimmed tile + small lock badge.
                    Image(
                        painter = painterResource(id = badge.iconRes),
                        contentDescription = badge.title,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .alpha(if (badge.isUnlocked) 1f else 0.45f),
                        contentScale = ContentScale.Crop
                    )
                    if (!badge.isUnlocked) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .offset(x = 4.dp, y = 4.dp)
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(focivoTokens().surface2)
                                .border(1.dp, cardBorder, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Locked",
                                tint = textSecondary,
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                if (badge.isUnlocked) focivoTokens().softAccent else focivoTokens().surface2
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (badge.isUnlocked) badge.icon else Icons.Default.Lock,
                            contentDescription = badge.title,
                            tint = if (badge.isUnlocked) focivoTokens().accentText else textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = badge.title,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = PoppinsFontFamily,
                    fontWeight = if (badge.isUnlocked) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 11.sp,
                    color = if (badge.isUnlocked) textPrimary else textSecondary
                )
            )
            if (badge.progressText.isNotBlank()) {
                Text(
                    text = badge.progressText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = PoppinsFontFamily,
                        fontSize = 10.sp,
                        color = textSecondary
                    )
                )
            }
        }
    }
}

/**
 * v1.4.0-round2: achievement detail — big 3D icon, description, real
 * progress bar, unlock state. All values come from stored session data.
 */
@Composable
private fun AchievementDetailDialog(
    badge: CleanBadge,
    isDark: Boolean,
    textPrimary: Color,
    textSecondary: Color,
    cardBg: Color,
    cardBorder: Color,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = null,
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (badge.iconRes != 0) {
                    Image(
                        painter = painterResource(id = badge.iconRes),
                        contentDescription = badge.title,
                        modifier = Modifier
                            .size(96.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .alpha(if (badge.isUnlocked) 1f else 0.55f),
                        contentScale = ContentScale.Crop
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = badge.title,
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp,
                    color = textPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = badge.description,
                    fontFamily = PoppinsFontFamily,
                    fontSize = 13.sp,
                    color = textSecondary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(14.dp))
                badge.progressFraction?.let { fraction ->
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = focivoTokens().accent,
                        trackColor = focivoTokens().line
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
                Text(
                    text = badge.progressText,
                    fontFamily = PoppinsFontFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (badge.isUnlocked) focivoTokens().accentText else textSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (badge.isUnlocked) "Unlocked" else "Locked",
                    fontFamily = PoppinsFontFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (badge.isUnlocked) focivoTokens().accentText else textSecondary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (badge.isUnlocked) focivoTokens().accent.copy(alpha = 0.16f)
                            else focivoTokens().surface2
                        )
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }
        },
        confirmButton = {
            PremiumSecondaryButton(
                text = "Close",
                onClick = onDismiss
            )
        }
    )
}
