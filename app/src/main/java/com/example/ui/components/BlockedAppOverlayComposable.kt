package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.FocuslyRepository
import com.example.service.FocusShieldService
import com.example.service.ScheduledBlockScheduler
import com.example.ui.theme.NearBlack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Calendar
import java.util.Locale

/**
 * v1.5.0 premium redesign — full-screen minimal block screen.
 *
 * Layout (top to bottom, generous whitespace):
 *  1. Blocked app's icon in a soft white circle + small blocked badge
 *  2. "{App} is blocked during focus" headline + subtle remaining-time line
 *  3. The Focivo guard mascot, large, on a soft lime glow
 *  4. One suggestion card: "Want to study instead?" — the user's next
 *     priority item when one exists, else a rotating real focus tip —
 *     with a single lime link row into the app
 *  5. ONE white pill "Stay in Focus" button (existing exit path:
 *     closes the blocked app to home — strict-mode rules unchanged)
 *
 * Blocking behavior is untouched — presentation only.
 */
@Composable
fun BlockedAppOverlay(
    appName: String,
    blockedPackage: String,
    initialDurationSec: Int = 0,
    reason: String? = null,
    isPunishment: Boolean = false,
    mascotRes: Int = R.drawable.mascot_blocked,
    /**
     * Tri-state next priority:
     *  - null  -> resolve automatically from FocuslyRepository top priorities
     *  - ""    -> force the fallback tip (no priority)
     *  - other -> use the given title verbatim (also used by screenshot tests)
     */
    nextPriority: String? = null,
    /** Overrides the rotating focus tip (null -> deterministic pick by day). */
    focusTip: String? = null,
    onExitApp: () -> Unit,
    onOpenFocusly: () -> Unit
) {
    val context = LocalContext.current

    val accentColor = if (isPunishment) Color(0xFFFF5252) else V150Lime
    val screenBg = Color(0xFF0A0A0B)

    // Live remaining timer state (same sources as before)
    var remainingSeconds by remember(initialDurationSec) {
        val calculated = if (initialDurationSec > 0) {
            initialDurationSec
        } else {
            val shieldRem = FocusShieldService.getRemainingSeconds(context)
            if (shieldRem > 0) {
                shieldRem
            } else if (ScheduledBlockScheduler.isScheduleCurrentlyActive(context)) {
                ScheduledBlockScheduler.getSecondsUntilNextStateChange(context)
            } else {
                25 * 60
            }
        }
        mutableIntStateOf(calculated)
    }

    // Countdown ticker
    LaunchedEffect(Unit) {
        while (remainingSeconds > 0) {
            delay(1000L)
            remainingSeconds -= 1
        }
    }

    val timeFormatted = formatBlockRemaining(remainingSeconds)

    // Resolve the next priority item off the main thread when not provided.
    val resolvedPriority by produceState<String?>(initialValue = nextPriority, nextPriority) {
        if (nextPriority != null) {
            value = nextPriority
        } else {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    withTimeoutOrNull(2500L) {
                        FocuslyRepository.getInstance(context).topPriorities
                            .firstOrNull()
                            ?.firstOrNull { it.title.isNotBlank() }
                            ?.title
                    }
                }.getOrNull()
            }
        }
    }

    val resolvedTip = remember(focusTip) {
        focusTip ?: V150FocusTips[Calendar.getInstance().get(Calendar.DAY_OF_YEAR) % V150FocusTips.size]
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(screenBg)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 400.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(28.dp))

            // 1 — Blocked app icon in a soft white circle + blocked badge
            BlockedAppIconBadge(
                appName = appName,
                blockedPackage = blockedPackage,
                accentColor = accentColor
            )

            Spacer(modifier = Modifier.height(22.dp))

            // 2 — Headline
            Text(
                text = "$appName is blocked during focus",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = BlockSerifItalic,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 23.sp,
                    lineHeight = 30.sp
                ),
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (remainingSeconds > 0) "Unlocks automatically in $timeFormatted"
                else "Session finished — apps are unlocking",
                color = Color(0xFF76767C),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(26.dp))

            // 3 — Guard mascot, large, on a soft glow
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(196.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(188.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    accentColor.copy(alpha = 0.28f),
                                    Color.Transparent
                                )
                            )
                        )
                )
                Image(
                    painter = painterResource(id = mascotRes),
                    contentDescription = "Focivo guard mascot",
                    modifier = Modifier.size(176.dp),
                    contentScale = ContentScale.Fit
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // 4 — Suggestion card
            SuggestionCard(
                nextPriorityTitle = resolvedPriority,
                tip = resolvedTip,
                accentColor = accentColor,
                onLinkClick = onOpenFocusly
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 5 — ONE primary white pill
            Button(
                onClick = onExitApp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .shadow(elevation = 8.dp, shape = RoundedCornerShape(50), spotColor = Color.White.copy(alpha = 0.25f)),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = NearBlack
                ),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Stay in Focus",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = NearBlack,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

private val V150Lime = Color(0xFFB6F13A)

// v1.2.3 design-unification — owner's clean-redesign language:
// Instrument Serif italic for the big display headline.
private val BlockSerifItalic = FontFamily(
    Font(R.font.instrument_serif_italic, FontWeight.Normal, FontStyle.Italic)
)

/** English-only, zero-emoji focus tips used when no priority item exists. */
private val V150FocusTips = listOf(
    "Put your phone in another room for the next 25 minutes — distance beats willpower.",
    "One task, one tab, one timer. Multitasking is just switching with extra steps.",
    "Start with the hardest topic first. Your brain is sharpest at the start.",
    "Study in 25-minute sprints with 5-minute breaks. Consistency beats marathons.",
    "Write down what you just learned in your own words — recall is how memory forms.",
    "A short walk resets attention better than scrolling ever will.",
    "Sleep locks in learning. A tired brain studies twice and remembers half.",
    "Keep water on your desk. Dehydration quietly drains concentration."
)

private fun formatBlockRemaining(totalSeconds: Int): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}

/**
 * The blocked app's real icon in a soft white circle with a small blocked
 * badge; falls back to a neutral drawn tile (no trademarked assets).
 */
@Composable
private fun BlockedAppIconBadge(
    appName: String,
    blockedPackage: String,
    accentColor: Color
) {
    val context = LocalContext.current
    val appIconBitmap = remember(blockedPackage) {
        drawableToBitmapSafely(context, blockedPackage)
    }

    Box(
        modifier = Modifier.size(112.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(104.dp)
                .shadow(elevation = 10.dp, shape = CircleShape, spotColor = Color.Black.copy(alpha = 0.35f))
                .clip(CircleShape)
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            if (appIconBitmap != null) {
                Image(
                    bitmap = appIconBitmap.asImageBitmap(),
                    contentDescription = "$appName icon",
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                // Neutral tile: soft lime-tinted rounded square with the app's initial.
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF1F1F22)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = appName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "A",
                        color = V150Lime,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        // Small blocked badge, bottom-right of the circle
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 4.dp, y = 4.dp)
                .size(34.dp)
                .clip(CircleShape)
                .background(Color(0xFFD93636))
                .border(2.5.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Blocked",
                tint = Color.White,
                modifier = Modifier.size(17.dp)
            )
        }
    }
}

private fun drawableToBitmapSafely(context: Context, packageName: String): Bitmap? {
    return try {
        val drawable: Drawable = context.packageManager.getApplicationIcon(packageName)
        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 96
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 96
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        bitmap
    } catch (_: Exception) {
        null
    }
}

/**
 * Suggestion card: "Want to study instead?" — shows the user's next priority
 * item when one exists, else a rotating real focus tip. The single lime link
 * row opens the app.
 */
@Composable
private fun SuggestionCard(
    nextPriorityTitle: String?,
    tip: String,
    accentColor: Color,
    onLinkClick: () -> Unit
) {
    val hasPriority = !nextPriorityTitle.isNullOrBlank()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF131315))
            .border(1.dp, Color(0xFF1F1F22), RoundedCornerShape(24.dp))
            .padding(horizontal = 20.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = "Want to study instead?",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (hasPriority) {
            Text(
                text = "NEXT PRIORITY",
                color = accentColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = nextPriorityTitle!!,
                color = Color(0xFFF4F4F5),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 20.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        } else {
            Text(
                text = "FOCUS TIP",
                color = accentColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = tip,
                color = Color(0xFFF4F4F5),
                fontSize = 14.sp,
                lineHeight = 21.sp
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onLinkClick
                )
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (hasPriority) "Open planner and start it" else "Open Focivo",
                color = accentColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.width(2.dp))
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
