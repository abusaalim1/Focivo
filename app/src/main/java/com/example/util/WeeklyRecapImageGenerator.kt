package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.data.model.WeeklyDailyStat
import com.example.data.model.WeeklyRecapSummary
import com.example.data.model.WeeklySubjectStat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

object WeeklyRecapImageGenerator {

    private const val TAG = "WeeklyRecapImageGen"
    private const val CARD_WIDTH = 1080
    private const val CARD_HEIGHT = 1920

    /**
     * Generates a high-resolution, social-ready summary card Bitmap (1080x1920)
     * utilizing Poppins typography, the official Focivo celebration mascot,
     * obsidian glassmorphism cards, and zero plant emojis.
     */
    fun generateRecapCardBitmap(context: Context, recap: WeeklyRecapSummary): Bitmap {
        val bitmap = Bitmap.createBitmap(CARD_WIDTH, CARD_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Load custom Poppins Fonts
        val fontBold = try {
            ResourcesCompat.getFont(context, R.font.poppins_bold) ?: Typeface.DEFAULT_BOLD
        } catch (e: Exception) {
            Typeface.DEFAULT_BOLD
        }
        val fontSemiBold = try {
            ResourcesCompat.getFont(context, R.font.poppins_semibold) ?: Typeface.DEFAULT_BOLD
        } catch (e: Exception) {
            Typeface.DEFAULT_BOLD
        }
        val fontMedium = try {
            ResourcesCompat.getFont(context, R.font.poppins_medium) ?: Typeface.DEFAULT
        } catch (e: Exception) {
            Typeface.DEFAULT
        }

        // 1. Deep Obsidian / Forest Luxury Background
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, CARD_HEIGHT.toFloat(),
                intArrayOf(
                    0xFF070B06.toInt(),
                    0xFF0E170C.toInt(),
                    0xFF091008.toInt(),
                    0xFF050804.toInt()
                ),
                floatArrayOf(0f, 0.35f, 0.75f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, CARD_WIDTH.toFloat(), CARD_HEIGHT.toFloat(), bgPaint)

        // 2. Ambient Cyber-Lime / Emerald Glow Lights
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        glowPaint.shader = RadialGradient(
            CARD_WIDTH * 0.82f, CARD_HEIGHT * 0.16f, 440f,
            intArrayOf(0x388CE000.toInt(), 0x158CE000.toInt(), 0x00000000),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(CARD_WIDTH * 0.82f, CARD_HEIGHT * 0.16f, 440f, glowPaint)

        glowPaint.shader = RadialGradient(
            CARD_WIDTH * 0.18f, CARD_HEIGHT * 0.82f, 480f,
            intArrayOf(0x2810B981.toInt(), 0x0E10B981.toInt(), 0x00000000),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(CARD_WIDTH * 0.18f, CARD_HEIGHT * 0.82f, 480f, glowPaint)

        // 3. Outer Frosted Glass Container
        val cardMarginH = 46f
        val cardMarginTop = 50f
        val cardMarginBottom = 46f
        val cardRect = RectF(
            cardMarginH,
            cardMarginTop,
            CARD_WIDTH - cardMarginH,
            CARD_HEIGHT - cardMarginBottom
        )

        // Outer Card Fill (Frosted Glass Dark)
        val cardBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xF2121D12.toInt()
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(cardRect, 48f, 48f, cardBgPaint)

        // Specular Neon Gradient Border
        val cardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3.5f
            shader = LinearGradient(
                cardRect.left, cardRect.top, cardRect.right, cardRect.bottom,
                intArrayOf(
                    0xEE8CE000.toInt(),
                    0x66A6EB38.toInt(),
                    0x228CE000.toInt(),
                    0x9910B981.toInt()
                ),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(cardRect, 48f, 48f, cardBorderPaint)

        // Positioning references
        var curY = cardMarginTop + 48f
        val contentLeft = cardMarginH + 40f
        val contentRight = CARD_WIDTH - cardMarginH - 40f
        val contentWidth = contentRight - contentLeft

        // 4. Header Bar: Pill Badge & Week Date Range
        val logoBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF1D2E1B.toInt()
            style = Paint.Style.FILL
        }
        val badgeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x668CE000.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        val badgeRect = RectF(contentLeft, curY, contentLeft + 270f, curY + 54f)
        canvas.drawRoundRect(badgeRect, 27f, 27f, logoBadgePaint)
        canvas.drawRoundRect(badgeRect, 27f, 27f, badgeBorderPaint)

        val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFA6EB38.toInt()
            textSize = 23f
            typeface = fontBold
            letterSpacing = 0.08f
        }
        canvas.drawText("⚡ FOCIVO RECAP", contentLeft + 24f, curY + 36f, badgeTextPaint)

        // Date Range on Right
        val dateTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFA5B2A1.toInt()
            textSize = 24f
            typeface = fontMedium
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(recap.weekIdentifier, contentRight, curY + 36f, dateTextPaint)

        curY += 76f

        // 5. Title Heading: "Weekly Focus Recap"
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = 46f
            typeface = fontBold
            letterSpacing = -0.01f
        }
        canvas.drawText("Weekly Focus Recap", contentLeft, curY + 40f, titlePaint)

        curY += 66f

        // 6. Giant Hero Focus Card with Integrated Mascot
        val heroBoxHeight = 250f
        val heroRect = RectF(contentLeft, curY, contentRight, curY + heroBoxHeight)

        val heroBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                heroRect.left, heroRect.top, heroRect.right, heroRect.bottom,
                intArrayOf(0xFF1B2D19.toInt(), 0xFF132013.toInt()),
                null,
                Shader.TileMode.CLAMP
            )
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(heroRect, 32f, 32f, heroBgPaint)

        val heroBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
            shader = LinearGradient(
                heroRect.left, heroRect.top, heroRect.right, heroRect.bottom,
                intArrayOf(0xCC8CE000.toInt(), 0x338CE000.toInt()),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(heroRect, 32f, 32f, heroBorderPaint)

        // Mascot Rendering in Hero Box (Right side with glow halo)
        val mascotBitmap: Bitmap? = try {
            val drawable = ContextCompat.getDrawable(context, R.drawable.mascot_celebration)
                ?: ContextCompat.getDrawable(context, R.drawable.mascot_achievement)
            drawable?.let { d ->
                val bmp = Bitmap.createBitmap(
                    d.intrinsicWidth.coerceAtLeast(1),
                    d.intrinsicHeight.coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888
                )
                val dCanvas = Canvas(bmp)
                d.setBounds(0, 0, dCanvas.width, dCanvas.height)
                d.draw(dCanvas)
                bmp
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error loading mascot drawable: ${e.message}")
            null
        }

        if (mascotBitmap != null) {
            val mascotTargetHeight = 220f
            val mascotTargetWidth = (mascotBitmap.width.toFloat() / mascotBitmap.height.toFloat()) * mascotTargetHeight
            val mascotRight = contentRight - 24f
            val mascotLeft = mascotRight - mascotTargetWidth
            val mascotTop = curY + (heroBoxHeight - mascotTargetHeight) / 2f
            val mascotCenter = mascotLeft + mascotTargetWidth / 2f
            val mascotMiddleY = mascotTop + mascotTargetHeight / 2f

            // Radial Glow Halo behind Mascot
            val mascotGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    mascotCenter, mascotMiddleY, 130f,
                    intArrayOf(0x448CE000.toInt(), 0x188CE000.toInt(), 0x00000000),
                    floatArrayOf(0f, 0.6f, 1f),
                    Shader.TileMode.CLAMP
                )
                style = Paint.Style.FILL
            }
            canvas.drawCircle(mascotCenter, mascotMiddleY, 130f, mascotGlowPaint)

            // Draw Mascot Bitmap
            val srcRect = Rect(0, 0, mascotBitmap.width, mascotBitmap.height)
            val destRect = RectF(mascotLeft, mascotTop, mascotRight, mascotTop + mascotTargetHeight)
            canvas.drawBitmap(mascotBitmap, srcRect, destRect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        }

        // Left Side of Hero Box: Huge Hours Number & Sub-label
        val heroKickerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFA6EB38.toInt()
            textSize = 21f
            typeface = fontBold
            letterSpacing = 0.06f
        }
        canvas.drawText("TOTAL DEEP WORK", contentLeft + 32f, curY + 52f, heroKickerPaint)

        val heroNumberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = 92f
            typeface = fontBold
            letterSpacing = -0.02f
        }
        val heroHoursText = "${recap.totalHoursFormatted} hrs"
        canvas.drawText(heroHoursText, contentLeft + 30f, curY + 148f, heroNumberPaint)

        val heroSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFD2E6C3.toInt()
            textSize = 24f
            typeface = fontMedium
            letterSpacing = 0.02f
        }
        canvas.drawText(
            "🔥 ${recap.totalMinutesFocused} Minutes Focused Across ${recap.sessionCount} Sessions",
            contentLeft + 32f,
            curY + 204f,
            heroSubPaint
        )

        curY += heroBoxHeight + 22f

        // 7. 3-Column Quick Metrics: [Sessions], [Streak], [Best Day]
        val metricGap = 16f
        val colWidth = (contentWidth - metricGap * 2) / 3f
        val metricCardHeight = 126f

        val metrics = listOf(
            Triple("⚡ SESSIONS", "${recap.sessionCount} Done", 0xFF8CE000.toInt()),
            Triple("🔥 STREAK", "${recap.currentStreak} Day${if (recap.currentStreak > 1) "s" else ""}", 0xFFFF9800.toInt()),
            Triple("🏆 BEST DAY", recap.bestDayName, 0xFF38BDF8.toInt())
        )

        for (i in metrics.indices) {
            val mLeft = contentLeft + i * (colWidth + metricGap)
            val mRect = RectF(mLeft, curY, mLeft + colWidth, curY + metricCardHeight)

            val mBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF192518.toInt()
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(mRect, 22f, 22f, mBgPaint)

            val mBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x288CE000.toInt()
                style = Paint.Style.STROKE
                strokeWidth = 1.5f
            }
            canvas.drawRoundRect(mRect, 22f, 22f, mBorderPaint)

            val mLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = metrics[i].third
                textSize = 20f
                typeface = fontBold
                letterSpacing = 0.04f
            }
            canvas.drawText(metrics[i].first, mLeft + 18f, curY + 42f, mLabelPaint)

            val mValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFFFFFFF.toInt()
                textSize = 25f
                typeface = fontBold
            }
            canvas.drawText(metrics[i].second, mLeft + 18f, curY + 88f, mValuePaint)
        }

        curY += metricCardHeight + 26f

        // 8. Daily Consistency Bar Chart Section
        val chartBoxHeight = 236f
        val chartRect = RectF(contentLeft, curY, contentRight, curY + chartBoxHeight)

        val chartBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF152215.toInt()
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(chartRect, 26f, 26f, chartBgPaint)
        canvas.drawRoundRect(chartRect, 26f, 26f, badgeBorderPaint)

        // Section Title & Peak Day Badge
        val chartTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = 27f
            typeface = fontBold
        }
        canvas.drawText("Daily Consistency", contentLeft + 26f, curY + 44f, chartTitlePaint)

        val bestDayBadgeText = "🏆 Best: ${recap.bestDayName}"
        val chartSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFA6EB38.toInt()
            textSize = 23f
            typeface = fontBold
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(bestDayBadgeText, contentRight - 26f, curY + 44f, chartSubPaint)

        // 7 Daily Bars
        val dailyStats = recap.dailyBreakdowns.ifEmpty {
            listOf(
                WeeklyDailyStat("Mon", 0),
                WeeklyDailyStat("Tue", 0),
                WeeklyDailyStat("Wed", 0),
                WeeklyDailyStat("Thu", 0),
                WeeklyDailyStat("Fri", 0),
                WeeklyDailyStat("Sat", 0),
                WeeklyDailyStat("Sun", 0)
            )
        }
        val maxDailyMins = maxOf(1, dailyStats.maxOfOrNull { it.totalMinutes } ?: 1)
        val barAreaTop = curY + 68f
        val barAreaBottom = curY + chartBoxHeight - 44f
        val maxBarHeight = barAreaBottom - barAreaTop
        val numDays = dailyStats.size
        val barSlotWidth = (contentWidth - 52f) / numDays.toFloat()
        val barWidth = 34f

        val dayLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFA0ACA0.toInt()
            textSize = 20f
            typeface = fontMedium
            textAlign = Paint.Align.CENTER
        }
        val barValPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFA6EB38.toInt()
            textSize = 18f
            typeface = fontBold
            textAlign = Paint.Align.CENTER
        }

        for (d in dailyStats.indices) {
            val stat = dailyStats[d]
            val slotCenterX = contentLeft + 26f + (d * barSlotWidth) + (barSlotWidth / 2f)
            val barLeft = slotCenterX - (barWidth / 2f)
            val barRight = slotCenterX + (barWidth / 2f)

            val frac = if (stat.totalMinutes > 0) (stat.totalMinutes.toFloat() / maxDailyMins.toFloat()).coerceIn(0.14f, 1f) else 0.06f
            val barTop = barAreaBottom - (maxBarHeight * frac)
            val isBest = stat.isBestDay || stat.dayName.equals(recap.bestDayName, ignoreCase = true)

            // Slot Background Track
            val barSlotBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x1AFFFFFF.toInt()
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(RectF(barLeft, barAreaTop, barRight, barAreaBottom), 10f, 10f, barSlotBgPaint)

            // Active Bar Fill with Neon Gradient
            val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = if (isBest && stat.totalMinutes > 0) {
                    LinearGradient(
                        barLeft, barTop, barRight, barAreaBottom,
                        intArrayOf(0xFFCCFF00.toInt(), 0xFF76C400.toInt()),
                        null,
                        Shader.TileMode.CLAMP
                    )
                } else if (stat.totalMinutes > 0) {
                    LinearGradient(
                        barLeft, barTop, barRight, barAreaBottom,
                        intArrayOf(0xFFA6EB38.toInt(), 0xFF4D8500.toInt()),
                        null,
                        Shader.TileMode.CLAMP
                    )
                } else {
                    null
                }
                if (stat.totalMinutes == 0) color = 0x118CE000.toInt()
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(RectF(barLeft, barTop, barRight, barAreaBottom), 10f, 10f, barPaint)

            // Top Value Label
            if (stat.totalMinutes > 0) {
                val valStr = if (stat.totalMinutes >= 60) "${stat.totalMinutes / 60}h" else "${stat.totalMinutes}m"
                canvas.drawText(valStr, slotCenterX, barTop - 6f, barValPaint)
            }

            // Bottom Day Name
            canvas.drawText(stat.dayName.take(3), slotCenterX, curY + chartBoxHeight - 14f, dayLabelPaint)
        }

        curY += chartBoxHeight + 26f

        // 9. Subjects Breakdown Section ("What I studied this week")
        val subjectBoxHeight = 250f
        val subRect = RectF(contentLeft, curY, contentRight, curY + subjectBoxHeight)

        canvas.drawRoundRect(subRect, 26f, 26f, chartBgPaint)
        canvas.drawRoundRect(subRect, 26f, 26f, badgeBorderPaint)

        canvas.drawText("Subjects Breakdown", contentLeft + 26f, curY + 44f, chartTitlePaint)

        val subList = recap.subjectBreakdowns.take(3).ifEmpty {
            listOf(
                WeeklySubjectStat("Deep Work", recap.totalMinutesFocused, recap.sessionCount, 1.0f)
            )
        }

        var subItemY = curY + 70f
        for (sub in subList) {
            val subNamePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFFFFFFF.toInt()
                textSize = 23f
                typeface = fontBold
            }
            canvas.drawText(sub.subject, contentLeft + 26f, subItemY + 20f, subNamePaint)

            val hoursStr = if (sub.totalMinutes >= 60) {
                String.format(Locale.US, "%.1fh", sub.totalMinutes / 60.0)
            } else {
                "${sub.totalMinutes}m"
            }
            val subMetaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFA6EB38.toInt()
                textSize = 21f
                typeface = fontBold
                textAlign = Paint.Align.RIGHT
            }
            canvas.drawText("$hoursStr • ${sub.sessionCount} sessions", contentRight - 26f, subItemY + 20f, subMetaPaint)

            // Progress Bar Track & Fill
            val barTrackRect = RectF(contentLeft + 26f, subItemY + 32f, contentRight - 26f, subItemY + 44f)
            val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x22FFFFFF.toInt()
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(barTrackRect, 6f, 6f, trackPaint)

            val fillWidth = ((contentWidth - 52f) * sub.percentage.coerceIn(0.06f, 1f))
            val barFillRect = RectF(contentLeft + 26f, subItemY + 32f, contentLeft + 26f + fillWidth, subItemY + 44f)
            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    barFillRect.left, barFillRect.top, barFillRect.right, barFillRect.bottom,
                    intArrayOf(0xFF8CE000.toInt(), 0xFF10B981.toInt()),
                    null,
                    Shader.TileMode.CLAMP
                )
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(barFillRect, 6f, 6f, fillPaint)

            subItemY += 54f
        }

        curY += subjectBoxHeight + 24f

        // 10. Session Highlights
        val highlightsBoxHeight = 150f
        val highRect = RectF(contentLeft, curY, contentRight, curY + highlightsBoxHeight)
        canvas.drawRoundRect(highRect, 26f, 26f, chartBgPaint)
        canvas.drawRoundRect(highRect, 26f, 26f, badgeBorderPaint)

        canvas.drawText("Session Highlights", contentLeft + 26f, curY + 40f, chartTitlePaint)

        val topSessions = recap.sessionDetails.take(2)
        var sessY = curY + 68f
        if (topSessions.isNotEmpty()) {
            for (s in topSessions) {
                val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFFA6EB38.toInt()
                    textSize = 21f
                    typeface = fontBold
                }
                canvas.drawText("✓", contentLeft + 26f, sessY + 18f, checkPaint)

                val sessNamePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFFF0F6EC.toInt()
                    textSize = 21f
                    typeface = fontMedium
                }
                val textSnippet = "${s.title.ifBlank { s.subject }} (${s.durationMinutes}m)"
                canvas.drawText(textSnippet.take(45), contentLeft + 52f, sessY + 18f, sessNamePaint)

                val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFFA5B2A1.toInt()
                    textSize = 19f
                    typeface = fontMedium
                    textAlign = Paint.Align.RIGHT
                }
                canvas.drawText(s.formattedTime, contentRight - 26f, sessY + 18f, timePaint)

                sessY += 36f
            }
        } else {
            val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFA5B2A1.toInt()
                textSize = 21f
                typeface = fontMedium
            }
            canvas.drawText("Consistent deep focus sessions logged throughout the week.", contentLeft + 26f, sessY + 18f, emptyPaint)
        }

        curY += highlightsBoxHeight + 22f

        // 11. Motivational Quote Banner
        val quoteBoxHeight = 110f
        val quoteRect = RectF(contentLeft, curY, contentRight, curY + quoteBoxHeight)

        val quoteBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                quoteRect.left, quoteRect.top, quoteRect.right, quoteRect.bottom,
                intArrayOf(0xFF1F301B.toInt(), 0xFF162314.toInt()),
                null,
                Shader.TileMode.CLAMP
            )
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(quoteRect, 22f, 22f, quoteBgPaint)

        val quoteBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x448CE000.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        canvas.drawRoundRect(quoteRect, 22f, 22f, quoteBorderPaint)

        val quoteTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = 21f
            typeface = Typeface.create(fontMedium, Typeface.ITALIC)
        }
        val cleanQuote = recap.motivationalQuote.replace("“", "\"").replace("”", "\"")
        if (cleanQuote.length > 55) {
            val firstLine = cleanQuote.take(55).substringBeforeLast(" ")
            val secondLine = cleanQuote.removePrefix(firstLine).trim()
            canvas.drawText("“$firstLine", contentLeft + 24f, curY + 42f, quoteTextPaint)
            canvas.drawText("$secondLine”", contentLeft + 24f, curY + 78f, quoteTextPaint)
        } else {
            canvas.drawText(cleanQuote, contentLeft + 24f, curY + 60f, quoteTextPaint)
        }

        // 12. Viral CTA & App Promotion Footer (Driving Installs!)
        val footerAppLogo: Bitmap? = try {
            BitmapFactory.decodeResource(context.resources, R.drawable.app_logo)
        } catch (e: Exception) {
            null
        }

        val footerY = CARD_HEIGHT - cardMarginBottom - 30f

        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = 22f
            typeface = fontBold
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.04f
        }
        canvas.drawText(
            "Track your focus with Focivo • Deep Work & Study Tracker",
            CARD_WIDTH / 2f,
            footerY,
            footerPaint
        )

        return bitmap
    }

    /**
     * Writes the bitmap to the cache directory and creates a FileProvider content URI.
     */
    suspend fun saveRecapBitmapToCache(context: Context, bitmap: Bitmap, fileName: String = "sunday_recap.png"): Uri? = withContext(Dispatchers.IO) {
        try {
            val cacheDir = File(context.cacheDir, "recap_shares")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            val imageFile = File(cacheDir, fileName)
            // use{} guarantees the stream is closed even if compress() throws.
            FileOutputStream(imageFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                fos.flush()
            }

            val authority = "${context.packageName}.fileprovider"
            FileProvider.getUriForFile(context, authority, imageFile)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving recap card bitmap: ${e.message}", e)
            null
        }
    }

    /**
     * Creates and launches an Intent to share the generated image summary card with caption text.
     */
    suspend fun shareRecapImage(context: Context, recap: WeeklyRecapSummary, onComplete: () -> Unit = {}) {
        withContext(Dispatchers.Default) {
            val bitmap = generateRecapCardBitmap(context, recap)
            val cleanKey = recap.weekKey.replace(Regex("[^a-zA-Z0-9_]"), "_")
            val fileName = "focivo_sunday_recap_$cleanKey.png"
            val uri = saveRecapBitmapToCache(context, bitmap, fileName)

            withContext(Dispatchers.Main) {
                if (uri != null) {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_SUBJECT, "My Weekly Study Recap - Focivo")
                        putExtra(Intent.EXTRA_TEXT, "⚡ Check out my weekly study recap on Focivo! Focused for ${recap.totalHoursFormatted} hrs across ${recap.sessionCount} sessions. Join me on Focivo to level up your focus! #Focivo #StudyGram #DeepWork")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    val chooser = Intent.createChooser(shareIntent, "Share Weekly Study Recap")
                    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(chooser)
                } else {
                    // Fallback to text sharing if image saving failed
                    shareRecapText(context, recap)
                }
                onComplete()
            }
        }
    }

    /**
     * Direct text sharing method.
     */
    fun shareRecapText(context: Context, recap: WeeklyRecapSummary, onComplete: () -> Unit = {}) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "My Weekly Study Recap - Focivo")
            putExtra(Intent.EXTRA_TEXT, recap.toShareableText())
        }
        val chooser = Intent.createChooser(shareIntent, "Share your weekly focus recap")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
        onComplete()
    }
}
