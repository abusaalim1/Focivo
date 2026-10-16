package com.example.ui.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.TrendingDown
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.FocusSessionEntity
import com.example.data.model.ReflectionEntity
import com.example.data.model.UserPreferencesEntity
import com.example.ui.theme.BottomNavClearance
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.SerifItalicFontFamily
import com.example.ui.theme.focivoTokens
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/* ───────────────────────── Design tokens (supplied design) ───────────────────────── */

/**
 * Design tokens for Insights — resolves to the dark/light system set.
 * (The supplied design's dark values are exactly the system dark tokens.)
 */
private object C {
    val Bg @Composable get() = focivoTokens().bg
    val Surface @Composable get() = focivoTokens().surface
    val Line @Composable get() = focivoTokens().line
    val Ink @Composable get() = focivoTokens().text
    val Muted @Composable get() = focivoTokens().muted
    val Accent @Composable get() = focivoTokens().accent
    val AccentSoft @Composable get() = focivoTokens().softAccent
    val AccentDim @Composable get() = focivoTokens().accentText
    val GoalLine @Composable get() = if (focivoTokens().isDark) Color(0xFF34343A) else Color(0xFFD8D8D2)
    val SegGray @Composable get() = focivoTokens().surface2
}

/**
 * App font pair (owner rule): Poppins for ALL body/labels; Instrument Serif
 * italic ONLY for highlight/display text (headlines, hero numbers, stat values).
 */
private val SerifItalic = SerifItalicFontFamily

/* ───────────────────────── UI state (built from real data) ───────────────────────── */

private data class InsightsUiState(
    val dateLabel: String,
    val goalHoursPerDay: Float,
    val weekMinutes: List<Int>,      // Mon..Sun, size 7
    val todayIndex: Int,             // 0 = Mon ... 6 = Sun
    val sessionsToday: Int,
    val topMode: String,
    val weekTotalHours: Float,
    val vsLastWeekPercent: Int,
    val totalSessions: Int,
    val completionPercent: Int,
    val activeDays: Int,
    val peakWindow: String,
    val peakBoostPercent: Int,
    val subjectRows: List<Triple<String, Float, String>>, // (name, hours, "x.x hrs" / "N mins")
    val morningPercent: Int,
    val afternoonPercent: Int,
    val eveningPercent: Int,
) {
    val todayMinutes: Int get() = weekMinutes.getOrElse(todayIndex) { 0 }
}

/** Builds [InsightsUiState] entirely from the app's real session + prefs stores. */
private fun buildInsightsState(
    sessions: List<FocusSessionEntity>,
    userPreferences: UserPreferencesEntity?
): InsightsUiState {
    val cal = Calendar.getInstance()
    val dow = cal.get(Calendar.DAY_OF_WEEK)
    // Monday 00:00 of the current week (same convention as WeeklyProductivityChart)
    val daysFromMonday = if (dow == Calendar.SUNDAY) 6 else (dow - Calendar.MONDAY)
    val dayMs = 24L * 3600 * 1000
    val todayStart = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val weekStartMs = todayStart - daysFromMonday * dayMs
    val prevWeekStartMs = weekStartMs - 7 * dayMs

    val weekMinutes = (0..6).map { i ->
        val start = weekStartMs + i * dayMs
        sessions.filter { it.completedAt in start until (start + dayMs) && it.durationSeconds > 0 }
            .sumOf { it.durationSeconds } / 60
    }
    val weekTotalMin = weekMinutes.sum()
    val prevWeekMin = sessions
        .filter { it.completedAt in prevWeekStartMs until weekStartMs && it.durationSeconds > 0 }
        .sumOf { it.durationSeconds } / 60
    val vsLastWeek = when {
        prevWeekMin > 0 -> Math.round((weekTotalMin - prevWeekMin) * 100f / prevWeekMin)
        weekTotalMin > 0 -> 100
        else -> 0
    }

    val sessionsToday = sessions.count { it.completedAt in todayStart until (todayStart + dayMs) }
    val weekSessions = sessions.filter { it.completedAt in weekStartMs until (weekStartMs + 7 * dayMs) }
    val topMode = weekSessions.groupBy { it.mode.ifBlank { "Deep Work" } }
        .maxByOrNull { it.value.size }?.key ?: ""

    val totalSessions = sessions.size
    val completionPercent = if (sessions.isNotEmpty()) {
        (sessions.count { it.durationSeconds >= it.targetDurationSeconds * 0.8 } * 100) / sessions.size
    } else 0
    val activeDays = sessions.filter { it.completedAt > 0 }
        .map { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it.completedAt)) }
        .toSet().size

    // Peak flow window: hour with the most sessions -> 2h window (matches prior screen)
    val peakHour = sessions.groupBy { it.hourOfDay }.maxByOrNull { it.value.size }?.key
    val peakWindow: String
    val peakBoost: Int
    if (peakHour == null) {
        peakWindow = "First session unlocks peak window"
        peakBoost = 0
    } else {
        val startAmpm = if (peakHour < 12) "AM" else "PM"
        val ds = if (peakHour % 12 == 0) 12 else peakHour % 12
        val endHour = (peakHour + 2) % 24
        val endAmpm = if (endHour < 12) "AM" else "PM"
        val de = if (endHour % 12 == 0) 12 else endHour % 12
        peakWindow = String.format(Locale.US, "%02d:00 %s – %02d:00 %s", ds, startAmpm, de, endAmpm)
        val peakSecs = sessions.filter { it.hourOfDay in peakHour..(peakHour + 2) }.sumOf { it.durationSeconds }
        val totalSecs = sessions.sumOf { it.durationSeconds }
        peakBoost = if (totalSecs > 0) ((peakSecs.toFloat() / totalSecs) * 100).toInt().coerceIn(1, 100) else 0
    }

    // Subject breakdown (by task title, this week) — same as the previous screen
    val subjectRows = weekSessions
        .groupBy { it.taskTitle.ifBlank { "General" } }
        .map { (subject, list) ->
            val secs = list.sumOf { it.durationSeconds }
            val hrs = secs / 3600f
            val formatted = if (hrs >= 0.1f) String.format(Locale.US, "%.1f hrs", hrs)
            else "${(secs / 60).coerceAtLeast(1)} mins"
            Triple(subject, hrs, formatted)
        }.sortedByDescending { it.second }.take(6)

    // Time-of-day buckets: app convention (morning 5–11, afternoon 12–16, evening 17–23 + 0–4)
    val morningSecs = sessions.filter { it.hourOfDay in 5..11 }.sumOf { it.durationSeconds }
    val afternoonSecs = sessions.filter { it.hourOfDay in 12..16 }.sumOf { it.durationSeconds }
    val eveningSecs = sessions.filter { it.hourOfDay in 17..23 || it.hourOfDay in 0..4 }.sumOf { it.durationSeconds }
    val totalCadence = morningSecs + afternoonSecs + eveningSecs
    val (mPct, aPct, ePct) = if (totalCadence > 0) {
        val m = ((morningSecs.toFloat() / totalCadence) * 100).toInt()
        val a = ((afternoonSecs.toFloat() / totalCadence) * 100).toInt()
        Triple(m, a, (100 - m - a).coerceAtLeast(0))
    } else Triple(0, 0, 0)

    val goalMinutes = userPreferences?.dailyGoalMinutes?.takeIf { it > 0 } ?: 120

    return InsightsUiState(
        dateLabel = SimpleDateFormat("EEEE, MMM d", Locale.US).format(Date()),
        goalHoursPerDay = goalMinutes / 60f,
        weekMinutes = weekMinutes,
        todayIndex = daysFromMonday,
        sessionsToday = sessionsToday,
        topMode = topMode,
        weekTotalHours = weekTotalMin / 60f,
        vsLastWeekPercent = vsLastWeek,
        totalSessions = totalSessions,
        completionPercent = completionPercent,
        activeDays = activeDays,
        peakWindow = peakWindow,
        peakBoostPercent = peakBoost,
        subjectRows = subjectRows,
        morningPercent = mPct,
        afternoonPercent = aPct,
        eveningPercent = ePct,
    )
}

/* ───────────────────────── Screen ───────────────────────── */

@Composable
fun InsightsScreen(
    sessions: List<FocusSessionEntity>,
    reflections: List<ReflectionEntity>, // kept for call-site compatibility
    userPreferences: UserPreferencesEntity? = null,
    onSaveReflection: (String) -> Unit,  // kept for call-site compatibility
    onOpenSessionHistory: () -> Unit = {},
    modifier: Modifier = Modifier,
    testInitialScroll: Int = 0, // screenshot seam: initial scroll offset in dp
) {
    val state = remember(sessions, userPreferences) { buildInsightsState(sessions, userPreferences) }

    Box(
        modifier
            .fillMaxSize()
            .background(C.Bg)
            .testTag("insights_screen")
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState(testInitialScroll))
                .padding(start = 22.dp, end = 22.dp, top = 24.dp, bottom = BottomNavClearance)
        ) {
            Header(state.goalHoursPerDay)
            Spacer(Modifier.height(28.dp))
            Hero(state)
            Spacer(Modifier.height(30.dp))
            WeekCard(state)
            Spacer(Modifier.height(26.dp))
            StatsRow(state)
            Spacer(Modifier.height(26.dp))
            Divider()
            InfoRow(
                icon = Icons.Rounded.Bolt,
                title = "Peak flow window",
                subtitle = state.peakWindow,
            ) {
                if (state.peakBoostPercent > 0) {
                    T("+${state.peakBoostPercent}%", 13, FontWeight.SemiBold, C.Accent)
                }
            }
            InfoRow(
                icon = Icons.Rounded.Schedule,
                title = "Session history",
                subtitle = "${state.totalSessions} sessions logged",
                onClick = onOpenSessionHistory,
            ) {
                Icon(Icons.Rounded.ChevronRight, null, tint = C.Muted, modifier = Modifier.size(20.dp))
            }

            if (state.subjectRows.isNotEmpty()) {
                Spacer(Modifier.height(30.dp))
                Eyebrow("By subject")
                Spacer(Modifier.height(14.dp))
                state.subjectRows.forEachIndexed { i, (subject, _, formatted) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(C.Accent))
                        Spacer(Modifier.width(12.dp))
                        T(subject, 14, FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        T(formatted, 13, FontWeight.SemiBold, if (i == 0) C.Accent else C.Muted)
                    }
                    if (i < state.subjectRows.lastIndex) Spacer(Modifier.height(10.dp))
                }
            }

            Spacer(Modifier.height(30.dp))
            TimeOfDay(state)
        }
    }
}

/* ───────────────────────── Sections ───────────────────────── */

@Composable
private fun Header(goal: Float) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Eyebrow("This week")
            Spacer(Modifier.height(6.dp))
            T("Insights", 34, FontWeight.Bold, spacing = -0.7f, serif = true)
        }
        Row(
            Modifier
                .border(1.dp, C.Line, CircleShape)
                .padding(horizontal = 12.dp, vertical = 7.dp)
        ) {
            val goalText = if (goal % 1f == 0f) "${goal.toInt()}h"
            else String.format(Locale.US, "%.1fh", goal)
            T("Goal ", 12, color = C.Muted)
            T(goalText, 12, FontWeight.SemiBold, C.Accent)
            T(" / day", 12, color = C.Muted)
        }
    }
}

@Composable
private fun Hero(s: InsightsUiState) {
    val goalMin = s.goalHoursPerDay * 60f
    val target = (s.todayMinutes / goalMin).coerceIn(0f, 1f)
    val progress by animateFloatAsState(target, tween(1200), label = "ring")

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
        Box(Modifier.size(128.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 8.dp.toPx()
                val topLeft = Offset(stroke / 2, stroke / 2)
                val arc = Size(size.width - stroke, size.height - stroke)
                drawArc(C.Line, 0f, 360f, false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Round))
                if (progress > 0f) {
                    drawArc(C.Accent, -90f, 360f * progress, false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Round))
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                T(formatMinutes(s.todayMinutes), 28, FontWeight.Bold, spacing = -0.5f, serif = true)
                T("today", 11, color = C.Muted)
            }
        }
        Column {
            T(s.dateLabel, 15, FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            val pct = (s.todayMinutes / goalMin * 100).toInt()
            val modeText = if (s.topMode.isNotBlank()) " of ${s.topMode}" else ""
            T("${s.sessionsToday} sessions$modeText.\n$pct% of your daily goal.", 13, color = C.Muted)
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(C.AccentSoft)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val up = s.vsLastWeekPercent >= 0
                    Icon(
                        if (up) Icons.Rounded.TrendingUp else Icons.Rounded.TrendingDown,
                        null, tint = C.Accent, modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    val sign = if (up) "+" else "-"
                    T("$sign${kotlin.math.abs(s.vsLastWeekPercent)}% vs last week", 12, FontWeight.SemiBold, C.Accent)
                }
            }
        }
    }
}

@Composable
private fun WeekCard(s: InsightsUiState) {
    val chartHeight = 110.dp
    val labels = listOf("M", "T", "W", "T", "F", "S", "S")
    val goalMin = s.goalHoursPerDay * 60f

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(C.Surface)
            .padding(20.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            T("Focus per day", 15, FontWeight.SemiBold, modifier = Modifier.weight(1f))
            val totalText = if (s.weekTotalHours >= 0.1f)
                String.format(Locale.US, "%.1f hrs total", s.weekTotalHours)
            else "${s.weekMinutes.sum()}m total"
            T(totalText, 12, color = C.Muted)
        }
        Spacer(Modifier.height(26.dp))

        Box(Modifier.fillMaxWidth().height(chartHeight)) {
            Canvas(Modifier.fillMaxWidth().height(1.dp)) {
                drawLine(
                    C.GoalLine, Offset(0f, 0f), Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))
                )
            }
            val goalLbl = if (s.goalHoursPerDay % 1f == 0f) "${s.goalHoursPerDay.toInt()}h"
            else String.format(Locale.US, "%.1fh", s.goalHoursPerDay)
            T(goalLbl, 10, color = C.Muted,
                modifier = Modifier.align(Alignment.TopEnd).offset(y = (-18).dp))

            Row(Modifier.fillMaxSize()) {
                s.weekMinutes.forEachIndexed { i, min ->
                    val on = i == s.todayIndex
                    val minHeight: Dp = if (on) 14.dp else 8.dp
                    val target = (chartHeight * (min / goalMin).coerceIn(0f, 1f)).coerceAtLeast(minHeight)
                    val h by animateDpAsState(target, tween(800), label = "bar$i")

                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
                        Box(
                            Modifier
                                .width(8.dp)
                                .height(h)
                                .then(
                                    if (on) Modifier.shadow(
                                        14.dp, CircleShape, ambientColor = C.Accent, spotColor = C.Accent
                                    ) else Modifier
                                )
                                .clip(CircleShape)
                                .background(if (on) C.Accent else C.Line)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { i, l ->
                val on = i == s.todayIndex
                T(l, 11, if (on) FontWeight.Bold else FontWeight.Medium,
                    if (on) C.Ink else C.Muted, align = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatsRow(s: InsightsUiState) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        StatItem("${s.totalSessions}", "Sessions", Modifier.weight(1f))
        VLine()
        StatItem("${s.completionPercent}%", "Completion", Modifier.weight(1f))
        VLine()
        StatItem("${s.activeDays}", if (s.activeDays == 1) "Active day" else "Active days", Modifier.weight(1f))
    }
}

@Composable
private fun StatItem(value: String, label: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        T(value, 24, FontWeight.Bold, spacing = -0.4f, serif = true)
        Spacer(Modifier.height(2.dp))
        T(label.uppercase(), 10, FontWeight.Medium, C.Muted, spacing = 0.6f)
    }
}

@Composable
private fun VLine() = Box(Modifier.fillMaxHeight(0.7f).width(1.dp).background(C.Line))

@Composable
private fun TimeOfDay(s: InsightsUiState) {
    Eyebrow("Time of day")
    Spacer(Modifier.height(16.dp))
    Row(
        Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Segment(s.morningPercent, C.SegGray)
        Segment(s.afternoonPercent, C.AccentDim)
        Segment(s.eveningPercent, C.Accent)
    }
    Spacer(Modifier.height(14.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Legend("Morning", s.morningPercent, C.SegGray)
        Legend("Afternoon", s.afternoonPercent, C.AccentDim)
        Legend("Evening", s.eveningPercent, C.Accent)
    }
}

@Composable
private fun RowScope.Segment(percent: Int, color: Color) {
    Box(
        Modifier
            .weight(percent.coerceAtLeast(1).toFloat())
            .fillMaxHeight()
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
private fun Legend(label: String, percent: Int, dot: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(6.dp))
        T("$label ", 12, color = C.Muted)
        T("$percent%", 12, FontWeight.SemiBold)
    }
}

/* ───────────────────────── Rows ───────────────────────── */

@Composable
private fun Divider() = Box(Modifier.fillMaxWidth().height(1.dp).background(C.Line))

@Composable
private fun InfoRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(vertical = 18.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(C.Surface),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = C.Accent, modifier = Modifier.size(18.dp)) }
            Column(Modifier.weight(1f)) {
                T(title, 14, FontWeight.SemiBold)
                T(subtitle, 12, color = C.Muted)
            }
            trailing()
        }
        Divider()
    }
}

/* ───────────────────────── Text helpers ───────────────────────── */

@Composable
private fun T(
    text: String,
    size: Int,
    weight: FontWeight = FontWeight.Medium,
    color: Color = C.Ink,
    spacing: Float = 0f,
    align: TextAlign? = null,
    modifier: Modifier = Modifier,
    serif: Boolean = false,
) {
    Text(
        text = text,
        modifier = modifier,
        textAlign = align,
        style = TextStyle(
            fontFamily = if (serif) SerifItalic else PoppinsFontFamily,
            fontSize = size.sp,
            fontWeight = weight,
            color = color,
            letterSpacing = spacing.sp,
            lineHeight = (size * 1.45f).sp,
        )
    )
}

@Composable
private fun Eyebrow(text: String) =
    T(text.uppercase(), 12, FontWeight.SemiBold, C.Muted, spacing = 1.7f)

private fun formatMinutes(m: Int): String =
    if (m < 60) "${m}m" else "${m / 60}h ${m % 60}m".replace(" 0m", "")
