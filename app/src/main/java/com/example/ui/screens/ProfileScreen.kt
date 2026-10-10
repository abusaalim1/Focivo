package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
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
import com.example.ui.components.AuroraBackground
import com.example.ui.components.GlassCard
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.pressFeedback
import com.example.ui.theme.MutedBorderLight
import com.example.ui.theme.NearBlack
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.RegainLimeContainer
import com.example.ui.theme.RegainLimeDeepText
import com.example.ui.theme.RegainLimePrimary
import com.example.ui.theme.SecondaryTextLight
import com.example.ui.theme.isAppInDarkTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
                color = NearBlack,
                fontWeight = FontWeight.Bold,
                fontSize = textSizeSp.sp
            )
        )
    }
}

data class CleanBadge(
    val title: String,
    val icon: ImageVector,
    val isUnlocked: Boolean
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
    onCheckForUpdates: () -> Unit = {},
    onLogout: () -> Unit = {},
    onUpdateProfile: (newName: String, avatarBytes: ByteArray?) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isDark = isAppInDarkTheme()
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showPrivacyPolicySheet by remember { mutableStateOf(false) }
    var showWidgetsSheet by remember { mutableStateOf(false) }
    val widgetSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Semantic colors for Dark / Light mode compatibility
    val cardBg = if (isDark) MaterialTheme.colorScheme.surface else Color.White
    val cardBorder = if (isDark) MaterialTheme.colorScheme.outlineVariant else MutedBorderLight
    val textPrimary = if (isDark) MaterialTheme.colorScheme.onSurface else NearBlack
    val textSecondary = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else SecondaryTextLight

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

    val badges = listOf(
        CleanBadge("First Flow", Icons.Default.Star, sessionCount >= 1),
        CleanBadge("60m Deep", Icons.Default.HourglassTop, sessions.any { it.durationSeconds >= 3600 }),
        CleanBadge("7 Days Flow", Icons.Default.DateRange, distinctDaysCount >= 7),
        CleanBadge("25h Master", Icons.Default.MilitaryTech, totalSeconds >= 25 * 3600),
        CleanBadge("Focus Shield", Icons.Default.Security, (userPreferences?.isAppBlockerEnabled == true) || ((userPreferences?.shieldBlockedAttempts ?: 0) > 0)),
        CleanBadge("100 Blocks", Icons.Default.WorkspacePremium, sessionCount >= 100)
    )

    Box(modifier = modifier.fillMaxSize()) {
        AuroraBackground(modifier = Modifier.fillMaxSize())

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .testTag("profile_screen"),
            contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp),
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
                            text = "IDENTITY",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = PoppinsFontFamily,
                                color = textSecondary,
                                letterSpacing = 2.sp,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Profile",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontFamily = PoppinsFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = textPrimary,
                                fontSize = 24.sp
                            )
                        )
                    }

                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(cardBg)
                            .border(1.dp, cardBorder, CircleShape)
                            .testTag("settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
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
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(RegainLimeContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = "Support Focivo",
                                    tint = RegainLimeDeepText,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

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
                                            .background(RegainLimeContainer)
                                            .padding(horizontal = 7.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "UPI DONATE",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = PoppinsFontFamily,
                                                color = RegainLimeDeepText,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 9.sp
                                            )
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Voluntary contribution · Keep Focivo free & growing",
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
                                    color = RegainLimePrimary,
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
                                        color = Color(0xFFEF4444)
                                    )
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(RegainLimeContainer)
                                        .clickable { onRetryFetchProfile() }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Retry",
                                        tint = RegainLimeDeepText,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Retry Sync",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontFamily = PoppinsFontFamily,
                                            color = RegainLimeDeepText,
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
                                            .background(RegainLimePrimary)
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
                                                    tint = RegainLimeDeepText,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                        if (userEmail.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(1.dp))
                                            Text(
                                                text = userEmail,
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontFamily = PoppinsFontFamily,
                                                    fontSize = 12.sp,
                                                    color = textSecondary
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
                                        .background(if (isDark) Color(0x30FFFFFF) else MutedBorderLight)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(dailyProgress.coerceAtLeast(0.08f))
                                            .fillMaxHeight()
                                            .clip(CircleShape)
                                            .background(RegainLimePrimary)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Quick Stats Strip
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Sessions
                    GlassCard(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "$sessionCount",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontFamily = PoppinsFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = textPrimary,
                                    fontSize = 20.sp
                                )
                            )
                            Text(
                                text = "Sessions",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = PoppinsFontFamily,
                                    color = textSecondary,
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }

                    // Hours
                    GlassCard(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = hoursFocused,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontFamily = PoppinsFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = textPrimary,
                                    fontSize = 20.sp
                                )
                            )
                            Text(
                                text = "Focused",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = PoppinsFontFamily,
                                    color = textSecondary,
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }

                    // Streak
                    val currentStreakDays = userPreferences?.currentStreak ?: 0
                    GlassCard(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "${currentStreakDays}d",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontFamily = PoppinsFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    color = RegainLimeDeepText
                                )
                            )
                            Text(
                                text = "Streak",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = PoppinsFontFamily,
                                    color = textSecondary,
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }
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
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(RegainLimeContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = "Focus Shield",
                                    tint = RegainLimeDeepText,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

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
                                            .background(if (isShieldActive) RegainLimeContainer else (if (isDark) Color(0x30FFFFFF) else Color(0xFFF0F4EC)))
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = if (isShieldActive) "ARMED" else if (!hasShieldPermissions) "SETUP" else "IDLE",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = PoppinsFontFamily,
                                                color = if (isShieldActive) RegainLimeDeepText else textSecondary,
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
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(RegainLimeContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Widgets,
                                    contentDescription = "Home Widgets",
                                    tint = RegainLimeDeepText,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

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
                                            .background(RegainLimeContainer)
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "5 WIDGETS",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = PoppinsFontFamily,
                                                color = RegainLimeDeepText,
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
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(if (isTodaySunday) RegainLimeContainer else Color(0xFF232A25)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DateRange,
                                    contentDescription = "Sunday Recap",
                                    tint = if (isTodaySunday) RegainLimeDeepText else Color(0xFFA0A89E),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

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
                                            .background(if (isTodaySunday) RegainLimeContainer else Color(0xFF2A342B))
                                            .padding(horizontal = 7.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = if (isTodaySunday) "READY" else "SUNDAY ONLY",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = PoppinsFontFamily,
                                                color = if (isTodaySunday) RegainLimeDeepText else Color(0xFFB0B8AE),
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
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(RegainLimeContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = "Session History",
                                    tint = RegainLimeDeepText,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

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
                                            .background(RegainLimeContainer)
                                            .padding(horizontal = 7.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "${sessions.size} LOGS",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = PoppinsFontFamily,
                                                color = RegainLimeDeepText,
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
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(RegainLimeContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Alarm,
                                    contentDescription = null,
                                    tint = RegainLimeDeepText,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

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
                            BadgeGridItem(badge = badge, isDark = isDark, cardBg = cardBg, cardBorder = cardBorder, textPrimary = textPrimary, textSecondary = textSecondary, modifier = Modifier.weight(1f))
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        badges.drop(3).take(3).forEach { badge ->
                            BadgeGridItem(badge = badge, isDark = isDark, cardBg = cardBg, cardBorder = cardBorder, textPrimary = textPrimary, textSecondary = textSecondary, modifier = Modifier.weight(1f))
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
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(RegainLimeContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SystemUpdate,
                                    contentDescription = "Check for Updates",
                                    tint = RegainLimeDeepText,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

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
                                            .background(RegainLimeContainer)
                                            .padding(horizontal = 7.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "v$currentVersionName",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = PoppinsFontFamily,
                                                color = RegainLimeDeepText,
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
                                    .background(RegainLimeContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PrivacyTip,
                                    contentDescription = "Privacy Policy",
                                    tint = RegainLimeDeepText,
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
                        .background(if (isDark) Color(0x30EF4444) else Color(0xFFFFEBEE))
                        .border(1.dp, if (isDark) Color(0x50EF4444) else Color(0xFFFFCDD2), RoundedCornerShape(18.dp))
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
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Sign Out",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontFamily = PoppinsFontFamily,
                                color = Color(0xFFEF4444),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        )
                    }
                }
            }
        }
    }

    if (showEditProfileDialog) {
        var editedName by remember { mutableStateOf(userName) }
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
                            .background(RegainLimePrimary)
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
                        onUpdateProfile(editedName.trim(), avatarBytes)
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
    modifier: Modifier = Modifier
) {
    GlassCard(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(
                        if (badge.isUnlocked) RegainLimeContainer else (if (isDark) Color(0x20FFFFFF) else Color(0xFFF0F4EC))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (badge.isUnlocked) badge.icon else Icons.Default.Lock,
                    contentDescription = badge.title,
                    tint = if (badge.isUnlocked) RegainLimeDeepText else textSecondary,
                    modifier = Modifier.size(18.dp)
                )
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
        }
    }
}
