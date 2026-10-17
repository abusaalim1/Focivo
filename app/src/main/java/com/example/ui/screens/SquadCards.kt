package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.squads.Squad
import com.example.data.squads.SquadStanding
import com.example.data.squads.squadJoinLink
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.SerifItalicFontFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/* ═══════════════════════════ Tokens (owner's Group Cards design) ═══════════════════════════ */

internal val SquadLime = Color(0xFF8CE000)
internal val SquadInk = Color(0xFF0D110E)
private val SquadCream = Color(0xFFF3F1E8)
internal val SquadRowLine = Color(0xFF27381F)
internal val CrownGold = Color(0xFFFFD66B)

private fun squadHm(minutes: Int): String =
    if (minutes < 60) "${minutes}m"
    else "${minutes / 60}h ${(minutes % 60).toString().padStart(2, '0')}m"

/** Rank message under a member's name — English only, no emojis (vector crown drawn instead). */
private fun squadRankMessage(rank: Int, me: Boolean): String = when {
    rank == 1 && me -> "You are on top. Hold the crown."
    rank == 1 -> "Leads the group this week."
    rank == 2 && me -> "One spot from the top. Push."
    rank == 2 -> "Chasing the leader."
    rank == 3 && me -> "Podium finish. Keep climbing."
    me -> "Ranked #$rank this week."
    else -> "Ranked #$rank this week."
}

private fun squadStandingsQuote(meRank: Int, leaderName: String, gapMin: Int): String = when {
    meRank == 1 -> "You are on top. Defend it."
    gapMin <= 0 -> "$leaderName leads. Catch up."
    else -> "$leaderName leads by ${squadHm(gapMin)}. Catch up."
}

/* ═══════════════════════════ Shared card chrome ═══════════════════════════ */

/** .c — 360x450, radius 30, shadow (matches the owner's Group Cards). */
@Composable
private fun SquadCardFrame(
    drawBg: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit,
    borderColor: Color? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(30.dp)
    Box(
        Modifier
            .fillMaxSize()
            .shadow(24.dp, shape, ambientColor = Color.Black.copy(alpha = .45f), spotColor = Color.Black.copy(alpha = .45f))
            .clip(shape)
            .drawBehind(drawBg)
            .then(if (borderColor != null) Modifier.border(1.dp, borderColor, shape) else Modifier)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
    }
}

/** .top — logo + FOCIVO + right label. */
@Composable
private fun SquadTopBar(label: String, brand: Color, textColor: Color) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(
                painterResource(R.drawable.focivo_logo), null,
                Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)),
            )
            Text(
                "FOCIVO",
                style = TextStyle(
                    fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Bold,
                    fontSize = 11.sp, letterSpacing = 2.4.sp, color = brand,
                )
            )
        }
        Text(
            label,
            style = TextStyle(
                fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Medium,
                fontSize = 12.sp, letterSpacing = .5.sp, color = textColor.copy(alpha = .6f),
            ),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

internal fun squadSerif(size: Float, color: Color, lh: Float = 1.05f) = TextStyle(
    fontFamily = SerifItalicFontFamily, fontStyle = FontStyle.Italic,
    fontWeight = FontWeight.Normal, fontSize = size.sp, lineHeight = (size * lh).sp,
    color = color, textAlign = TextAlign.Center,
)

internal fun squadSans(size: Float, weight: FontWeight, color: Color, ls: Float = 0f, lh: Float = 1f, align: TextAlign = TextAlign.Center) = TextStyle(
    fontFamily = PoppinsFontFamily, fontWeight = weight, fontSize = size.sp,
    letterSpacing = ls.sp, lineHeight = (size * lh).sp, color = color, textAlign = align,
)

/** Vector crown (gold) — the HTML's crown path, drawn; never an emoji. Internal: reused by the detail-screen podium. */
@Composable
internal fun SquadCrown(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val path = PathParser().parsePathString("M3 18l-1-11 5 4 5-8 5 8 5-4-1 11z").toPath()
        withTransform(
            { scale(size.width / 24f, size.height / 24f, Offset.Zero) }
        ) {
            drawPath(path, CrownGold)
        }
    }
}

/* ═══════════════════════════ Card A — Standings (adapts HTML k1) ═══════════════════════════ */

/**
 * 360x450 standings card (dark ink + lime glow): podium top-3 with gold crown,
 * rows 4-8 with lime bars, rank quote. Rendered to 1080x1350 PNG for sharing.
 */
@Composable
fun SquadStandingsCardPng(
    squad: Squad,
    standings: List<SquadStanding>,
    dateLabel: String = remember { squadDateLabel() },
) {
    // v1.3.2: the share card leaves the group — locked (private) members are
    // excluded entirely, never rendered with a zeroed time.
    val standings = standings.filterNot { it.isLocked }
    val top = standings.take(3)
    val rest = standings.drop(3)
    // The 450dp card fits 3 rows: ranks 4-6. When the viewer is ranked lower,
    // swap the last row for their own so the shared card still shows them.
    val shownRows = when {
        rest.size <= 3 -> rest
        rest.any { it.isMe } -> rest.take(2) + rest.first { it.isMe }
        else -> rest.take(3)
    }
    val maxMin = (standings.maxOfOrNull { it.weeklySeconds } ?: 1L).coerceAtLeast(1L)
    val me = standings.firstOrNull { it.isMe }
    val leader = standings.firstOrNull()

    SquadCardFrame(
        drawBg = {
            drawRect(SquadInk)
            drawRect(
                Brush.radialGradient(
                    0f to SquadLime.copy(alpha = .16f),
                    .62f to Color.Transparent,
                    center = Offset(size.width / 2f, size.height * .3f),
                    radius = hypot(size.width / 2f, size.height * .62f),
                )
            )
        }
    ) {
        SquadTopBar("${squad.name} · This week", SquadLime, Color.White)
        Text("Standings", style = squadSerif(26f, Color.White), modifier = Modifier.padding(top = 4.dp))

        // Podium (2nd, 1st, 3rd) — .pod
        if (top.isNotEmpty()) {
            val order = listOfNotNull(top.getOrNull(1), top.getOrNull(0), top.getOrNull(2))
            Row(
                Modifier.padding(top = 10.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.Bottom,
            ) {
                order.forEachIndexed { i, s ->
                    val isFirst = s.rank == 1
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(98.dp),
                    ) {
                        Box(
                            modifier = Modifier.padding(top = if (isFirst) 12.dp else 0.dp),
                            contentAlignment = Alignment.TopCenter,
                        ) {
                            Box(
                                Modifier
                                    .size(if (isFirst) 46.dp else 38.dp)
                                    .then(
                                        if (s.isMe) Modifier.border(3.dp, SquadLime, CircleShape)
                                        else Modifier
                                    )
                                    .clip(CircleShape),
                            ) {
                                LeaderboardAvatar(
                                    avatarUrl = s.member.avatarUrl,
                                    displayName = s.member.displayName,
                                    modifier = Modifier.fillMaxSize(),
                                    isCurrentUser = s.isMe,
                                    textSizeSp = 15,
                                )
                            }
                            if (isFirst) {
                                // Gold crown floating above the winner's avatar (vector, never an emoji).
                                SquadCrown(
                                    Modifier
                                        .align(Alignment.TopCenter)
                                        .offset(y = (-13).dp)
                                        .size(22.dp)
                                )
                            }
                        }
                        Text(
                            s.member.displayName,
                            style = squadSans(12.5f, FontWeight.SemiBold, Color.White),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 6.dp).widthIn(max = 96.dp),
                        )
                        Text(
                            squadHm((s.weeklySeconds / 60).toInt()),
                            style = squadSans(12f, FontWeight.Normal, Color.White.copy(alpha = .65f)),
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(when (s.rank) { 1 -> 60.dp; 2 -> 46.dp; else -> 34.dp })
                                .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                                .background(
                                    Brush.verticalGradient(
                                        listOf(SquadLime.copy(alpha = .38f), SquadLime.copy(alpha = .06f))
                                    )
                                )
                                .border(1.dp, SquadLime.copy(alpha = .25f), RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)),
                            contentAlignment = Alignment.TopCenter,
                        ) {
                            Text(
                                "${s.rank}",
                                style = squadSerif(24f, Color.White),
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                    if (i < order.size - 1) Spacer(Modifier.width(10.dp))
                }
            }
        }

        // Rows 4..8 — .row with lime bars
        if (rest.isNotEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                shownRows.forEach { s ->
                    val min = (s.weeklySeconds / 60).toInt()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .then(
                                if (s.isMe) Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(SquadLime.copy(alpha = .13f))
                                    .border(1.dp, SquadLime.copy(alpha = .4f), RoundedCornerShape(14.dp))
                                else Modifier
                            )
                            .padding(horizontal = 10.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("${s.rank}", style = squadSans(12.5f, FontWeight.SemiBold, Color.White.copy(alpha = .55f)), modifier = Modifier.width(16.dp))
                        Box(Modifier.size(24.dp).clip(CircleShape)) {
                            LeaderboardAvatar(
                                avatarUrl = s.member.avatarUrl,
                                displayName = s.member.displayName,
                                modifier = Modifier.fillMaxSize(),
                                isCurrentUser = s.isMe,
                                textSizeSp = 10,
                            )
                        }
                        Text(
                            s.member.displayName,
                            style = squadSans(12.5f, FontWeight.SemiBold, Color.White),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(46.dp),
                        )
                        Box(
                            Modifier.weight(1f).height(5.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(Color.White.copy(alpha = .1f))
                        ) {
                            Box(
                                Modifier.fillMaxWidth((s.weeklySeconds.toFloat() / maxMin).coerceIn(0.02f, 1f))
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(SquadLime)
                            )
                        }
                        Text(
                            squadHm(min),
                            style = squadSans(12.5f, FontWeight.Normal, Color.White.copy(alpha = .75f)),
                            modifier = Modifier.width(54.dp),
                            textAlign = TextAlign.End,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))
        // .q — rank quote
        val quote = if (me != null && leader != null) {
            squadStandingsQuote(me.rank, leader.member.displayName, ((leader.weeklySeconds - me.weeklySeconds) / 60).toInt())
        } else {
            "Study together. Win together."
        }
        Text(
            quote,
            style = squadSerif(16f, Color.White.copy(alpha = .92f), 1.25f),
            modifier = Modifier.widthIn(max = 290.dp).padding(top = 6.dp),
        )
        Text(
            dateLabel,
            style = squadSans(11f, FontWeight.Medium, Color.White.copy(alpha = .4f), .5f),
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/* ═══════════════════════════ Card B — Invite / referral (adapts HTML k5) ═══════════════════════════ */

/**
 * 360x450 invite card (lime gradient, "Study with me." challenge poster):
 * squad name, top-3 mini rows, join-code pill, link. Rendered to 1080x1350
 * PNG for WhatsApp / Instagram / system-sheet sharing.
 */
@Composable
fun SquadInviteCardPng(
    squad: Squad,
    inviterName: String,
    inviterWeeklyMin: Int,
    memberNames: List<String> = emptyList(),
) {
    val link = remember(squad.inviteCode) { squadJoinLink(squad.inviteCode) }
    SquadCardFrame(
        drawBg = {
            val a = Math.toRadians(160.0)
            val dx = sin(a).toFloat()
            val dy = (-cos(a)).toFloat()
            val len = abs(size.width * dx) + abs(size.height * dy)
            val c = Offset(size.width / 2f, size.height / 2f)
            val dir = Offset(dx, dy) * (len / 2f)
            drawRect(
                Brush.linearGradient(
                    listOf(Color(0xFF9BEA10), Color(0xFFD4F56A)),
                    start = c - dir, end = c + dir,
                )
            )
        }
    ) {
        SquadTopBar("Invite", SquadInk, SquadInk)
        Text(
            "Study with me.",
            style = squadSerif(46f, SquadInk, 1f),
            modifier = Modifier.padding(top = 10.dp),
        )
        Text(
            "Join \u201C${squad.name}\u201D on Focivo. Block distractions, climb the group leaderboard.",
            style = squadSans(13f, FontWeight.Medium, SquadInk.copy(alpha = .8f), lh = 1.4f),
            modifier = Modifier.padding(top = 8.dp).widthIn(max = 280.dp),
        )

        // inviter's week line
        Text(
            "$inviterName is at ${squadHm(inviterWeeklyMin)} this week. Think you can top it?",
            style = squadSans(12.5f, FontWeight.SemiBold, SquadInk),
            modifier = Modifier.padding(top = 12.dp).widthIn(max = 290.dp),
        )

        Spacer(Modifier.weight(1f))

        // code pill
        Box(
            Modifier
                .clip(RoundedCornerShape(99.dp))
                .background(SquadInk)
                .padding(horizontal = 22.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "JOIN WITH CODE  ${squad.inviteCode}",
                style = squadSans(13f, FontWeight.Bold, SquadLime, 3f),
            )
        }
        Text(
            link,
            style = squadSans(11f, FontWeight.Medium, SquadInk.copy(alpha = .7f), .5f),
            modifier = Modifier.padding(top = 8.dp),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        if (memberNames.isNotEmpty()) {
            Text(
                "${memberNames.size} ${if (memberNames.size == 1) "friend" else "friends"} already focusing",
                style = squadSans(11.5f, FontWeight.Medium, SquadInk.copy(alpha = .6f)),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

private fun squadDateLabel(): String = try {
    SimpleDateFormat("MMM d", Locale.US).format(Date())
} catch (_: Exception) { "" }

/** Small member card used in the squad detail screen — adapts HTML k2's rank rows. */
@Composable
fun SquadMemberRow(
    standing: SquadStanding,
    maxWeeklySeconds: Long,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val s = standing
    val minutes = (s.weeklySeconds / 60).toInt()
    // v1.3.2: private members (not self) render LOCKED — name + avatar +
    // lock, NO study time anywhere on the row.
    val locked = s.isLocked
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .then(
                if (s.isMe) Modifier
                    .background(SquadLime.copy(alpha = .1f))
                    .border(1.dp, SquadLime.copy(alpha = .35f), RoundedCornerShape(16.dp))
                else Modifier.background(Color(0xFF161B17))
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "${s.rank}",
            style = squadSans(13f, FontWeight.Bold, if (s.rank == 1) CrownGold else Color.White.copy(alpha = .5f)),
            modifier = Modifier.width(20.dp),
            textAlign = TextAlign.Center,
        )
        Box(Modifier.size(38.dp).clip(CircleShape)) {
            LeaderboardAvatar(
                avatarUrl = s.member.avatarUrl,
                displayName = s.member.displayName,
                modifier = Modifier.fillMaxSize(),
                isCurrentUser = s.isMe,
                textSizeSp = 13,
            )
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    s.member.displayName,
                    style = squadSans(13.5f, FontWeight.SemiBold, Color.White, align = TextAlign.Start),
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
                if (s.rank == 1) SquadCrown(Modifier.size(16.dp))
                if (locked) Icon(
                    Icons.Default.Lock,
                    contentDescription = "Private profile",
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(
                if (locked) "Private profile" else squadRankMessage(s.rank, s.isMe),
                style = squadSans(11.5f, FontWeight.Normal, Color.White.copy(alpha = .55f), align = TextAlign.Start),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (!locked) {
                Box(
                    Modifier.fillMaxWidth().height(5.dp).padding(top = 0.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(Color.White.copy(alpha = .08f))
                ) {
                    Box(
                        Modifier.fillMaxWidth((s.weeklySeconds.toFloat() / maxWeeklySeconds.coerceAtLeast(1L)).coerceIn(0.02f, 1f))
                            .height(5.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(SquadLime)
                    )
                }
            }
        }
        if (locked) {
            Icon(
                Icons.Default.Lock,
                contentDescription = "Private",
                tint = Color.White.copy(alpha = 0.45f),
                modifier = Modifier.size(18.dp),
            )
        } else {
            Text(
                squadHm(minutes),
                style = squadSans(13f, FontWeight.SemiBold, Color.White),
                textAlign = TextAlign.End,
            )
        }
    }
}
