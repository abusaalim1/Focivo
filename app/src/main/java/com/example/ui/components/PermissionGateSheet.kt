package com.example.ui.components

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.ui.theme.NearBlack
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.RegainLimePrimary
import com.example.ui.theme.isAppInDarkTheme
import com.example.util.PermissionUtils

/**
 * v1.2.1 — Start-Study permission gate sheet.
 *
 * Shown when the user taps Start Study while any of the 7 focus permissions
 * is missing: the session does NOT start. Each missing permission gets a
 * Grant button that fires the same system dialog / settings intent onboarding
 * uses (trust explainer first for Accessibility and Device Admin). Later
 * dismisses the sheet and stays in-app — nothing starts.
 *
 * Auto-dismisses when the user returns with everything granted.
 */
@Composable
fun PermissionGateSheet(
    missingIds: List<String>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isDark = isAppInDarkTheme()
    val textPrimary = if (isDark) Color(0xFFF0F4ED) else NearBlack
    val textSecondary = if (isDark) Color(0xFFA0A89E) else Color(0xFF5A635A)
    val cardBg = if (isDark) Color(0xFF1A2019) else Color.White
    val cardBorder = if (isDark) Color(0x22FFFFFF) else Color(0xFFE2E8E0)
    val rowBg = if (isDark) Color(0xFF232A22) else Color(0xFFF2F5EE)

    var explainerFor by remember { mutableStateOf<String?>(null) }

    // Notifications runtime-permission launcher (Android 13+), mirroring onboarding.
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* re-check happens on resume */ }

    fun fireGrant(id: String) {
        val perm = PermissionUtils.focusPermissionById(id)
        if (perm?.sensitive == true) {
            explainerFor = id
            return
        }
        val ok = if (id == "notifications" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                true
            } catch (_: Exception) {
                PermissionUtils.fireFocusPermissionIntent(context, id)
            }
        } else {
            PermissionUtils.fireFocusPermissionIntent(context, id)
        }
        if (!ok) {
            Toast.makeText(
                context,
                "Couldn't open Settings — please grant \"${perm?.title ?: id}\" manually.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // Auto-dismiss when the user comes back with everything granted.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                try {
                    if (PermissionUtils.hasAllFocusPermissions(context)) onDismiss()
                } catch (_: Exception) { }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(cardBg)
                .border(1.dp, cardBorder, RoundedCornerShape(28.dp))
                .padding(horizontal = 22.dp, vertical = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(RegainLimePrimary.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = RegainLimePrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Permissions needed",
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = textPrimary
                    )
                    Text(
                        text = "Focivo can't run a proper focus session without these.",
                        fontFamily = PoppinsFontFamily,
                        fontSize = 12.5.sp,
                        color = textSecondary,
                        lineHeight = 17.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            missingIds.forEach { id ->
                val perm = PermissionUtils.focusPermissionById(id) ?: return@forEach
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(rowBg)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = perm.title,
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = textPrimary
                        )
                        Text(
                            text = perm.why,
                            fontFamily = PoppinsFontFamily,
                            fontSize = 11.5.sp,
                            color = textSecondary,
                            lineHeight = 15.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(RegainLimePrimary)
                            .clickable { fireGrant(id) }
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                            .testTag("permission_gate_grant_$id"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Grant",
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = NearBlack
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Grant the missing permissions, then tap Start Study again.",
                fontFamily = PoppinsFontFamily,
                fontSize = 12.sp,
                color = textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onDismiss() }
                        .padding(horizontal = 18.dp, vertical = 12.dp)
                        .testTag("permission_gate_later"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Later",
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = textSecondary
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = RegainLimePrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Nothing starts until you grant them",
                        fontFamily = PoppinsFontFamily,
                        fontSize = 11.5.sp,
                        color = textSecondary
                    )
                }
            }
        }
    }

    explainerFor?.let { id ->
        TrustExplainerDialog(
            permission = if (id == "device_admin") SensitivePermission.DEVICE_ADMIN
            else SensitivePermission.ACCESSIBILITY,
            onContinue = {
                explainerFor = null
                val ok = PermissionUtils.fireFocusPermissionIntent(context, id)
                if (!ok) {
                    Toast.makeText(
                        context,
                        "Couldn't open Settings — please grant it manually.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            },
            onNotNow = { explainerFor = null }
        )
    }
}
