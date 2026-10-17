package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.squads.SQUADS_MAX_MEMBERS
import com.example.data.squads.Squad
import com.example.data.squads.SquadRepository
import com.example.data.squads.SquadRepository.SquadsUnavailable
import com.example.data.squads.SquadStanding
import com.example.data.squads.squadJoinLink
import com.example.ui.components.FeatureIcon
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.SerifItalicFontFamily
import com.example.ui.theme.focivoEyebrow
import com.example.ui.theme.focivoTokens
import com.example.util.captureComposedBitmapGuarded
import com.example.util.saveBitmapToGallery
import com.example.util.shareCardViaSystemSheet
import com.example.util.shareGeneric
import com.example.util.shareToWhatsApp
import com.example.util.writeShareCardToCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Locale

/**
 * v1.3.0 — Squads (friends competition). Entered from the HOME screen only
 * (owner explicit). Create → name / picture / bio → invite code + referral
 * card; join via typed code or the https://focivoapp.vercel.app/join/<code>
 * deep link; standings ranked by REAL study time from study_leaderboard.
 *
 * While the squads tables are absent server-side (DDL 403-blocked as of
 * 2026-10-17), every screen shows the graceful "setting up" state.
 */

private sealed interface SquadRoute {
    data object Hub : SquadRoute
    data object Create : SquadRoute
    data class Invite(val squad: Squad) : SquadRoute
    data class Detail(val squadId: String) : SquadRoute
    data class Join(val prefillCode: String) : SquadRoute
}

@Composable
fun SquadsFlowScreen(
    onBack: () -> Unit,
    prefillJoinCode: String? = null,
    myUserId: String?,
    myDisplayName: String,
    myAvatarUrl: String?,
    myWeeklyMinutes: Int = 0,
    modifier: Modifier = Modifier,
) {
    val T = focivoTokens()
    var route by remember(prefillJoinCode) {
        mutableStateOf<SquadRoute>(
            if (!prefillJoinCode.isNullOrBlank()) SquadRoute.Join(prefillJoinCode) else SquadRoute.Hub
        )
    }
    BackHandler {
        route = when (route) {
            is SquadRoute.Hub -> return@BackHandler onBack()
            is SquadRoute.Invite -> SquadRoute.Hub
            is SquadRoute.Detail -> SquadRoute.Hub
            is SquadRoute.Create -> SquadRoute.Hub
            is SquadRoute.Join -> SquadRoute.Hub
        }
    }
    Box(
        modifier
            .fillMaxSize()
            .background(T.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        when (val r = route) {
            is SquadRoute.Hub -> SquadHubScreen(
                myUserId = myUserId,
                onBack = onBack,
                onCreate = { route = SquadRoute.Create },
                onJoinCode = { route = SquadRoute.Join("") },
                onOpenSquad = { route = SquadRoute.Detail(it) },
            )
            is SquadRoute.Create -> SquadCreateScreen(
                myUserId = myUserId,
                myDisplayName = myDisplayName,
                myAvatarUrl = myAvatarUrl,
                onBack = { route = SquadRoute.Hub },
                onCreated = { route = SquadRoute.Invite(it) },
            )
            is SquadRoute.Invite -> SquadInviteScreen(
                squad = r.squad,
                inviterName = myDisplayName,
                inviterWeeklyMin = myWeeklyMinutes,
                memberNames = listOf(myDisplayName),
                onBack = { route = SquadRoute.Hub },
                onOpenSquad = { route = SquadRoute.Detail(r.squad.id) },
            )
            is SquadRoute.Detail -> SquadDetailScreen(
                squadId = r.squadId,
                myUserId = myUserId,
                myDisplayName = myDisplayName,
                onBack = { route = SquadRoute.Hub },
            )
            is SquadRoute.Join -> SquadJoinScreen(
                prefillCode = r.prefillCode,
                myUserId = myUserId,
                myDisplayName = myDisplayName,
                myAvatarUrl = myAvatarUrl,
                onBack = { route = SquadRoute.Hub },
                onJoined = { route = SquadRoute.Detail(it.id) },
            )
        }
    }
}

/* ═══════════════════════════ Small shared pieces ═══════════════════════════ */

@Composable
private fun SquadTopAppBar(title: String, onBack: () -> Unit) {
    val T = focivoTokens()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = T.text)
        }
        Text(
            title,
            style = TextStyle(
                fontFamily = SerifItalicFontFamily, fontWeight = FontWeight.Bold,
                fontSize = 24.sp, color = T.text,
            ),
        )
    }
}

@Composable
private fun SquadsUnavailableCard(onRetry: () -> Unit) {
    val T = focivoTokens()
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(T.surface)
            .border(1.dp, T.line, RoundedCornerShape(24.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FeatureIcon(drawableRes = R.drawable.ic3d_people, contentDescription = null, size = 56.dp)
        Spacer(Modifier.height(12.dp))
        Text(
            "Squads are being set up",
            style = TextStyle(fontFamily = PoppinsFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = T.text),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "The squad arena is almost ready. Try again soon.",
            style = TextStyle(fontFamily = PoppinsFontFamily, fontSize = 13.sp, color = T.muted),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier
                .clip(CircleShape)
                .background(T.accent)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null, onClick = onRetry
                )
                .padding(horizontal = 22.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Retry",
                style = TextStyle(fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = T.onAccent),
            )
        }
    }
}

@Composable
private fun SquadPrimaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    val T = focivoTokens()
    Box(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(CircleShape)
            .background(if (enabled) T.accent else T.line)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null, onClick = { if (enabled) onClick() }
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = TextStyle(
                fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Bold,
                fontSize = 15.sp, color = if (enabled) T.onAccent else T.muted,
            ),
        )
    }
}

@Composable
private fun SquadTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    singleLine: Boolean = true,
    maxChars: Int = 48,
) {
    val T = focivoTokens()
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= maxChars) onValueChange(it) },
        label = { Text(label, style = TextStyle(fontFamily = PoppinsFontFamily, fontSize = 13.sp)) },
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = T.accent,
            unfocusedBorderColor = T.line,
            focusedLabelColor = T.accent,
            unfocusedLabelColor = T.muted,
            cursorColor = T.accent,
            focusedTextColor = T.text,
            unfocusedTextColor = T.text,
        ),
        textStyle = TextStyle(fontFamily = PoppinsFontFamily, fontSize = 15.sp),
    )
}

/** Share action row: WhatsApp (real logo) / More / Download — same icons as the session share screen. */
private const val SQUAD_SHARE_LINES = "M8.600 13.500l6.800 4M15.400 6.500l-6.800 4"
private const val SQUAD_DOWNLOAD_PATH = "M12 4v11M7 11l5 5 5-5M5 20h14"

@Composable
private fun SquadShareActions(
    onWhatsApp: () -> Unit,
    onMore: () -> Unit,
    onDownload: () -> Unit,
) {
    val T = focivoTokens()
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(34.dp, Alignment.CenterHorizontally),
    ) {
        SquadActionItem("WhatsApp", Color(0xFF25D366), null, onWhatsApp) {
            Image(
                painterResource(R.drawable.ic_whatsapp), null,
                Modifier.size(28.dp), contentScale = ContentScale.Fit,
            )
        }
        SquadActionItem("More", Color(0xFF1B211C), Color(0xFF27381F), onMore) {
            SquadSvg24(24.dp) {
                val st = Stroke(1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                drawCircle(Color.White, 3f, androidx.compose.ui.geometry.Offset(18f, 5f), style = st)
                drawCircle(Color.White, 3f, androidx.compose.ui.geometry.Offset(6f, 12f), style = st)
                drawCircle(Color.White, 3f, androidx.compose.ui.geometry.Offset(18f, 19f), style = st)
                drawPath(PathParser().parsePathString(SQUAD_SHARE_LINES).toPath(), Color.White, style = st)
            }
        }
        SquadActionItem("Download", Color(0xFF1B211C), Color(0xFF27381F), onDownload) {
            SquadSvg24(24.dp) {
                drawPath(
                    PathParser().parsePathString(SQUAD_DOWNLOAD_PATH).toPath(), Color.White,
                    style = Stroke(1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
    }
}

@Composable
private fun SquadActionItem(
    label: String,
    bg: Color,
    border: Color?,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(bg)
                .then(if (border != null) Modifier.border(1.dp, border, CircleShape) else Modifier)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { icon() }
        Spacer(Modifier.height(8.dp))
        Text(
            label,
            style = TextStyle(fontFamily = PoppinsFontFamily, fontSize = 12.sp, color = Color.White.copy(alpha = .65f)),
        )
    }
}

@Composable
private fun SquadSvg24(dp: Dp, draw: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit) {
    Canvas(Modifier.size(dp)) {
        scale(size.width / 24f, androidx.compose.ui.geometry.Offset.Zero) { draw() }
    }
}

/* ═══════════════════════════ Hub ═══════════════════════════ */

@Composable
/** Public for v1.3.0 screenshot tests (route shells render these directly). */
fun SquadHubScreen(
    myUserId: String?,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onJoinCode: () -> Unit,
    onOpenSquad: (String) -> Unit,
) {
    val T = focivoTokens()
    val scope = rememberCoroutineScope()
    var squads by remember { mutableStateOf<List<Squad>?>(null) }
    var unavailable by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        if (myUserId.isNullOrBlank()) return
        scope.launch {
            squads = null; unavailable = false; error = null
            val res = SquadRepository.fetchMySquads(myUserId)
            res.onSuccess { squads = it }
                .onFailure {
                    if (it is SquadsUnavailable) unavailable = true
                    else error = it.message ?: "Could not load squads."
                }
        }
    }
    LaunchedEffect(myUserId) { refresh() }

    Column(Modifier.fillMaxSize()) {
        SquadTopAppBar("Squads", onBack)
        when {
            myUserId.isNullOrBlank() -> {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    FeatureIcon(drawableRes = R.drawable.ic3d_people, contentDescription = null, size = 56.dp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Sign in to use squads",
                        style = TextStyle(fontFamily = PoppinsFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = T.text),
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Squads need an account so your friends can find you on the leaderboard.",
                        style = TextStyle(fontFamily = PoppinsFontFamily, fontSize = 13.sp, color = T.muted),
                        textAlign = TextAlign.Center,
                    )
                }
            }
            unavailable -> {
                Column(Modifier.padding(horizontal = 22.dp)) { SquadsUnavailableCard(onRetry = ::refresh) }
            }
            squads == null && error == null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = T.accent)
                }
            }
            else -> {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Text(
                            "COMPETE WITH FRIENDS".uppercase(Locale.getDefault()),
                            style = focivoEyebrow(),
                            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                        )
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(T.accent)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null, onClick = onCreate
                                    )
                                    .padding(18.dp),
                            ) {
                                Column {
                                    Text(
                                        "Create squad",
                                        style = TextStyle(fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = T.onAccent),
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "Up to $SQUADS_MAX_MEMBERS friends",
                                        style = TextStyle(fontFamily = PoppinsFontFamily, fontSize = 12.sp, color = T.onAccent.copy(alpha = .75f)),
                                    )
                                }
                            }
                            Box(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(T.surface)
                                    .border(1.dp, T.line, RoundedCornerShape(20.dp))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null, onClick = onJoinCode
                                    )
                                    .padding(18.dp),
                            ) {
                                Column {
                                    Text(
                                        "Join with code",
                                        style = TextStyle(fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = T.text),
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "Enter an invite code",
                                        style = TextStyle(fontFamily = PoppinsFontFamily, fontSize = 12.sp, color = T.muted),
                                    )
                                }
                            }
                        }
                    }
                    if (error != null) {
                        item {
                            Text(
                                error!!,
                                style = TextStyle(fontFamily = PoppinsFontFamily, fontSize = 13.sp, color = T.muted),
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }
                    }
                    val list = squads.orEmpty()
                    if (list.isNotEmpty()) {
                        item {
                            Text(
                                "MY SQUADS",
                                style = focivoEyebrow(),
                                modifier = Modifier.padding(top = 10.dp),
                            )
                        }
                        items(list, key = { it.id }) { squad ->
                            SquadListRow(squad = squad, onClick = { onOpenSquad(squad.id) })
                        }
                    } else if (error == null) {
                        item {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(T.surface)
                                    .border(1.dp, T.line, RoundedCornerShape(24.dp))
                                    .padding(26.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                FeatureIcon(drawableRes = R.drawable.ic3d_trophy, contentDescription = null, size = 52.dp)
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    "No squads yet",
                                    style = TextStyle(fontFamily = PoppinsFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = T.text),
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Create one and invite your friends to compete.",
                                    style = TextStyle(fontFamily = PoppinsFontFamily, fontSize = 12.5.sp, color = T.muted),
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SquadListRow(squad: Squad, onClick: () -> Unit) {
    val T = focivoTokens()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(T.surface)
            .border(1.dp, T.line, RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null, onClick = onClick
            )
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SquadPictureThumb(pictureUrl = squad.pictureUrl, name = squad.name, size = 52.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                squad.name,
                style = TextStyle(fontFamily = PoppinsFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = T.text),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${squad.memberCount} member${if (squad.memberCount == 1) "" else "s"} · Code ${squad.inviteCode}",
                style = TextStyle(fontFamily = PoppinsFontFamily, fontSize = 12.sp, color = T.muted),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = T.muted)
    }
}

@Composable
private fun SquadPictureThumb(pictureUrl: String?, name: String, size: Dp) {
    val T = focivoTokens()
    val bitmap = remember(pictureUrl) { decodeDataUriBitmap(pictureUrl) }
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(T.softAccent)
            .border(1.dp, T.line, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                name.trim().take(1).uppercase(Locale.getDefault()).ifBlank { "S" },
                style = TextStyle(fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Bold, fontSize = (size.value / 2.4f).sp, color = T.accentText),
            )
        }
    }
}

private fun decodeDataUriBitmap(dataUri: String?): androidx.compose.ui.graphics.ImageBitmap? {
    if (dataUri.isNullOrBlank()) return null
    return try {
        val clean = if (dataUri.contains(",")) dataUri.substringAfter(",") else dataUri
        val bytes = Base64.decode(clean, Base64.DEFAULT)
        val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        bmp.asImageBitmap()
    } catch (_: Exception) { null }
}

/* ═══════════════════════════ Create ═══════════════════════════ */

@Composable
/** Public for v1.3.0 screenshot tests (route shells render these directly). */
fun SquadCreateScreen(
    myUserId: String?,
    myDisplayName: String,
    myAvatarUrl: String?,
    onBack: () -> Unit,
    onCreated: (Squad) -> Unit,
) {
    val T = focivoTokens()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var pictureDataUri by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val encoded = encodePickedSquadPicture(context, uri)
                withContext(Dispatchers.Main) {
                    if (encoded != null) pictureDataUri = encoded
                    else Toast.makeText(context, "Could not read that image.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        SquadTopAppBar("New squad", onBack)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                // Picture picker
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SquadPictureThumb(pictureUrl = pictureDataUri, name = name.ifBlank { "S" }, size = 72.dp)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            "Squad picture",
                            style = TextStyle(fontFamily = PoppinsFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = T.text),
                        )
                        Spacer(Modifier.height(4.dp))
                        Box(
                            Modifier
                                .clip(CircleShape)
                                .background(T.surface)
                                .border(1.dp, T.line, CircleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null, onClick = { picker.launch("image/*") }
                                )
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Text(
                                if (pictureDataUri == null) "Choose image" else "Change image",
                                style = TextStyle(fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Medium, fontSize = 12.5.sp, color = T.accentText),
                            )
                        }
                    }
                }
            }
            item { SquadTextField(value = name, onValueChange = { name = it; error = null }, label = "Squad name", maxChars = 48) }
            item {
                SquadTextField(
                    value = bio, onValueChange = { bio = it }, label = "Bio (optional)",
                    singleLine = false, maxChars = 160,
                )
            }
            item {
                Text(
                    "Up to $SQUADS_MAX_MEMBERS members. You will get an invite code and a shareable card after creating.",
                    style = TextStyle(fontFamily = PoppinsFontFamily, fontSize = 12.5.sp, color = T.muted),
                )
            }
            if (error != null) {
                item {
                    Text(
                        error!!,
                        style = TextStyle(fontFamily = PoppinsFontFamily, fontSize = 13.sp, color = Color(0xFFE5484D)),
                    )
                }
            }
            item {
                SquadPrimaryButton(
                    text = if (creating) "Creating..." else "Create squad",
                    enabled = !creating && name.isNotBlank() && !myUserId.isNullOrBlank(),
                ) {
                    creating = true
                    error = null
                    scope.launch {
                        val res = SquadRepository.createSquad(
                            name = name,
                            bio = bio.ifBlank { null },
                            pictureUrl = pictureDataUri,
                            ownerUserId = myUserId!!,
                            ownerDisplayName = myDisplayName.ifBlank { "Student" },
                            ownerAvatarUrl = myAvatarUrl,
                        )
                        creating = false
                        res.onSuccess { onCreated(it) }
                            .onFailure {
                                error = if (it is SquadsUnavailable)
                                    "Squads are being set up — try again soon."
                                else it.message ?: "Could not create the squad."
                            }
                    }
                }
            }
        }
    }
}

private fun encodePickedSquadPicture(context: Context, uri: Uri): String? {
    return try {
        val bmp: Bitmap = if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
        val scale = (maxOf(bmp.width, bmp.height).toFloat() / 512f).coerceAtLeast(1f)
        val small = Bitmap.createScaledBitmap(
            bmp, (bmp.width / scale).toInt().coerceAtLeast(1), (bmp.height / scale).toInt().coerceAtLeast(1), true
        )
        val out = ByteArrayOutputStream()
        small.compress(Bitmap.CompressFormat.JPEG, 80, out)
        if (!bmp.isRecycled) bmp.recycle()
        if (!small.isRecycled) small.recycle()
        "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    } catch (_: Exception) { null }
}

/* ═══════════════════════════ Invite (referral card) ═══════════════════════════ */

@Composable
/** Public for v1.3.0 screenshot tests (route shells render these directly). */
fun SquadInviteScreen(
    squad: Squad,
    inviterName: String,
    inviterWeeklyMin: Int,
    memberNames: List<String>,
    onBack: () -> Unit,
    onOpenSquad: () -> Unit,
) {
    val T = focivoTokens()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val link = remember(squad.inviteCode) { squadJoinLink(squad.inviteCode) }
    var toastMsg by remember { mutableStateOf<String?>(null) }

    fun copyLink() {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Squad invite", link))
        toastMsg = "Invite link copied."
        scope.launch {
            kotlinx.coroutines.delay(2200)
            toastMsg = null
        }
    }

    suspend fun renderInvitePng(): Bitmap? =
        captureComposedBitmapGuarded(context, 1080, 1350) {
            Box(Modifier.size(360.dp, 450.dp)) {
                SquadInviteCardPng(
                    squad = squad,
                    inviterName = inviterName,
                    inviterWeeklyMin = inviterWeeklyMin,
                    memberNames = memberNames,
                )
            }
        }

    fun shareInvite(toWhatsApp: Boolean) {
        scope.launch {
            val bitmap = renderInvitePng()
            val text = "Join my study squad \"${squad.name}\" on Focivo! Code: ${squad.inviteCode} — $link"
            if (bitmap == null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Couldn't render the card — sharing text instead.", Toast.LENGTH_SHORT).show()
                }
                if (toWhatsApp) shareToWhatsApp(context, text) else shareGeneric(context, text, "Share squad invite")
                return@launch
            }
            val uri = writeShareCardToCache(context, bitmap)
            if (!bitmap.isRecycled) bitmap.recycle()
            if (uri == null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Couldn't prepare the image — sharing text instead.", Toast.LENGTH_SHORT).show()
                }
                if (toWhatsApp) shareToWhatsApp(context, text) else shareGeneric(context, text, "Share squad invite")
                return@launch
            }
            shareCardViaSystemSheet(context, text, toWhatsApp, uri, "Share squad invite")
        }
    }

    fun downloadInvite() {
        scope.launch {
            val bitmap = renderInvitePng() ?: run {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Could not save the image. Please try again.", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }
            val uri = saveBitmapToGallery(context, bitmap)
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    if (uri != null) "Image saved to gallery." else "Could not save the image. Please try again.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0C0B))
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text(
                "Invite friends",
                style = TextStyle(fontFamily = SerifItalicFontFamily, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = Color.White),
            )
        }
        // Live preview of the referral card (the owner's Group Cards design),
        // scaled 0.8x into the slot like the session share carousel.
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 36.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(288.dp, 360.dp)) {
                Box(
                    Modifier
                        .size(360.dp, 450.dp)
                        .graphicsLayer {
                            scaleX = .8f
                            scaleY = .8f
                            transformOrigin = TransformOrigin(0f, 0f)
                        }
                ) {
                    SquadInviteCardPng(
                        squad = squad,
                        inviterName = inviterName,
                        inviterWeeklyMin = inviterWeeklyMin,
                        memberNames = memberNames,
                    )
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        // Code + link row
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 36.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF161B17))
                    .border(1.dp, Color(0xFF27381F), RoundedCornerShape(16.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null, onClick = ::copyLink
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Column {
                    Text(
                        "INVITE CODE",
                        style = TextStyle(fontFamily = PoppinsFontFamily, fontSize = 10.sp, letterSpacing = 3.sp, color = Color.White.copy(alpha = .55f)),
                    )
                    Text(
                        squad.inviteCode,
                        style = TextStyle(fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = 5.sp, color = Color(0xFF8CE000)),
                    )
                    Text(
                        link,
                        style = TextStyle(fontFamily = PoppinsFontFamily, fontSize = 11.sp, color = Color.White.copy(alpha = .55f)),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = ::copyLink) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy link", tint = Color.White)
            }
        }
        if (toastMsg != null) {
            Text(
                toastMsg!!,
                style = TextStyle(fontFamily = PoppinsFontFamily, fontSize = 12.5.sp, color = Color.White),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 10.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF242A25))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
        Spacer(Modifier.height(18.dp))
        SquadShareActions(
            onWhatsApp = { shareInvite(toWhatsApp = true) },
            onMore = { shareInvite(toWhatsApp = false) },
            onDownload = ::downloadInvite,
        )
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .padding(bottom = 28.dp)
                .height(52.dp)
                .clip(CircleShape)
                .background(Color(0xFF8CE000))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null, onClick = onOpenSquad
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Open squad",
                style = TextStyle(fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF0D110E)),
            )
        }
    }
}

/* ═══════════════════════════ Join ═══════════════════════════ */

@Composable
/** Public for v1.3.0 screenshot tests (route shells render these directly). */
fun SquadJoinScreen(
    prefillCode: String,
    myUserId: String?,
    myDisplayName: String,
    myAvatarUrl: String?,
    onBack: () -> Unit,
    onJoined: (Squad) -> Unit,
) {
    val T = focivoTokens()
    val scope = rememberCoroutineScope()
    var code by remember(prefillCode) { mutableStateOf(prefillCode.uppercase(Locale.US)) }
    var joining by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var foundName by remember { mutableStateOf<String?>(null) }

    fun lookup() {
        val c = code.trim().uppercase(Locale.US)
        if (c.length < 4 || myUserId.isNullOrBlank()) return
        scope.launch {
            SquadRepository.fetchSquadByCode(c)
                .onSuccess { foundName = it?.name }
                .onFailure {
                    foundName = null
                    if (it is SquadsUnavailable) error = "Squads are being set up — try again soon."
                }
        }
    }

    Column(Modifier.fillMaxSize()) {
        SquadTopAppBar(if (prefillCode.isNotBlank()) "Join squad" else "Join with code", onBack)
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (prefillCode.isNotBlank()) {
                Text(
                    "You were invited to join a study squad.",
                    style = TextStyle(fontFamily = PoppinsFontFamily, fontSize = 14.sp, color = T.muted),
                )
            }
            SquadTextField(
                value = code,
                onValueChange = {
                    code = it.uppercase(Locale.US).filter { ch -> ch.isLetterOrDigit() }
                    error = null
                    foundName = null
                },
                label = "Invite code",
                maxChars = 8,
            )
            if (foundName != null) {
                Text(
                    "Squad: $foundName",
                    style = TextStyle(fontFamily = PoppinsFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = T.accentText),
                )
            }
            if (error != null) {
                Text(
                    error!!,
                    style = TextStyle(fontFamily = PoppinsFontFamily, fontSize = 13.sp, color = Color(0xFFE5484D)),
                )
            }
            if (myUserId.isNullOrBlank()) {
                Text(
                    "Sign in to join squads.",
                    style = TextStyle(fontFamily = PoppinsFontFamily, fontSize = 13.sp, color = T.muted),
                )
            } else {
                SquadPrimaryButton(
                    text = if (joining) "Joining..." else "Join squad",
                    enabled = !joining && code.trim().length >= 4,
                ) {
                    joining = true
                    error = null
                    scope.launch {
                        val res = SquadRepository.joinSquad(
                            code = code,
                            userId = myUserId,
                            displayName = myDisplayName.ifBlank { "Student" },
                            avatarUrl = myAvatarUrl,
                        )
                        joining = false
                        res.onSuccess { onJoined(it) }
                            .onFailure {
                                error = if (it is SquadsUnavailable) "Squads are being set up — try again soon."
                                else it.message ?: "Could not join the squad."
                            }
                    }
                }
            }
            // Live lookup while typing (best-effort).
            LaunchedEffect(code) {
                if (code.trim().length >= 6) lookup()
            }
        }
    }
}

/* ═══════════════════════════ Detail ═══════════════════════════ */

@Composable
/** Public for v1.3.0 screenshot tests (route shells render these directly). */
fun SquadDetailScreen(
    squadId: String,
    myUserId: String?,
    myDisplayName: String,
    onBack: () -> Unit,
) {
    val T = focivoTokens()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var squad by remember { mutableStateOf<Squad?>(null) }
    var standings by remember { mutableStateOf<List<SquadStanding>?>(null) }
    var unavailable by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var leaving by remember { mutableStateOf(false) }

    fun refresh() {
        scope.launch {
            standings = null; unavailable = false; error = null
            val code = squad?.inviteCode
            if (code != null) {
                SquadRepository.fetchSquadByCode(code)
                    .onSuccess { squad = it }
                    .onFailure { if (it is SquadsUnavailable) unavailable = true }
            } else {
                // First load: find the squad via my memberships.
                val mine = myUserId?.let { SquadRepository.fetchMySquads(it).getOrNull() }.orEmpty()
                squad = mine.firstOrNull { it.id == squadId }
                if (squad == null) {
                    error = "Could not open this squad."
                    return@launch
                }
            }
            val s = squad ?: return@launch
            SquadRepository.fetchStandings(s.id, myUserId.orEmpty())
                .onSuccess { standings = it }
                .onFailure {
                    if (it is SquadsUnavailable) unavailable = true
                    else error = it.message ?: "Could not load standings."
                }
        }
    }
    LaunchedEffect(squadId) { refresh() }

    fun shareStandings(toWhatsApp: Boolean) {
        val s = squad ?: return
        val st = standings ?: return
        scope.launch {
            val bitmap = captureComposedBitmapGuarded(context, 1080, 1350) {
                Box(Modifier.size(360.dp, 450.dp)) {
                    SquadStandingsCardPng(squad = s, standings = st)
                }
            }
            val text = "\"${s.name}\" squad standings this week on Focivo — join with code ${s.inviteCode}: ${squadJoinLink(s.inviteCode)}"
            if (bitmap == null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Couldn't render the card — sharing text instead.", Toast.LENGTH_SHORT).show()
                }
                if (toWhatsApp) shareToWhatsApp(context, text) else shareGeneric(context, text, "Share squad standings")
                return@launch
            }
            val uri = writeShareCardToCache(context, bitmap)
            if (!bitmap.isRecycled) bitmap.recycle()
            if (uri == null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Couldn't prepare the image — sharing text instead.", Toast.LENGTH_SHORT).show()
                }
                if (toWhatsApp) shareToWhatsApp(context, text) else shareGeneric(context, text, "Share squad standings")
                return@launch
            }
            shareCardViaSystemSheet(context, text, toWhatsApp, uri, "Share squad standings")
        }
    }

    fun downloadStandings() {
        val s = squad ?: return
        val st = standings ?: return
        scope.launch {
            val bitmap = captureComposedBitmapGuarded(context, 1080, 1350) {
                Box(Modifier.size(360.dp, 450.dp)) {
                    SquadStandingsCardPng(squad = s, standings = st)
                }
            } ?: run {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Could not save the image. Please try again.", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }
            val uri = saveBitmapToGallery(context, bitmap)
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    if (uri != null) "Image saved to gallery." else "Could not save the image. Please try again.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        when {
            unavailable -> {
                SquadTopAppBar("Squad", onBack)
                Column(Modifier.padding(horizontal = 22.dp)) { SquadsUnavailableCard(onRetry = ::refresh) }
            }
            squad == null && error == null -> {
                SquadTopAppBar("Squad", onBack)
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = T.accent)
                }
            }
            squad == null -> {
                SquadTopAppBar("Squad", onBack)
                Text(
                    error ?: "Could not open this squad.",
                    style = TextStyle(fontFamily = PoppinsFontFamily, fontSize = 13.sp, color = T.muted),
                    modifier = Modifier.padding(22.dp),
                )
            }
            else -> {
                val s = squad!!
                val st = standings
                val maxWeekly = (st?.maxOfOrNull { it.weeklySeconds } ?: 1L).coerceAtLeast(1L)
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        SquadTopAppBar(s.name, onBack)
                    }
                    item {
                        // Header: picture, name, bio, code chip.
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SquadPictureThumb(pictureUrl = s.pictureUrl, name = s.name, size = 64.dp)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                if (!s.bio.isNullOrBlank()) {
                                    Text(
                                        s.bio,
                                        style = TextStyle(fontFamily = PoppinsFontFamily, fontSize = 13.sp, color = T.muted),
                                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier
                                            .clip(CircleShape)
                                            .background(T.surface)
                                            .border(1.dp, T.line, CircleShape)
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                    ) {
                                        Text(
                                            "CODE ${s.inviteCode}",
                                            style = TextStyle(fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 2.sp, color = T.accentText),
                                        )
                                    }
                                    Text(
                                        "${s.memberCount}/$SQUADS_MAX_MEMBERS",
                                        style = TextStyle(fontFamily = PoppinsFontFamily, fontSize = 12.sp, color = T.muted),
                                    )
                                }
                            }
                        }
                    }
                    item {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("STANDINGS · THIS WEEK", style = focivoEyebrow())
                            IconButton(onClick = ::refresh, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = T.muted, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    if (st == null) {
                        item {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(120.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(color = T.accent, modifier = Modifier.size(28.dp))
                            }
                        }
                    } else if (st.isEmpty()) {
                        item {
                            Text(
                                "No members yet — share the invite code.",
                                style = TextStyle(fontFamily = PoppinsFontFamily, fontSize = 13.sp, color = T.muted),
                            )
                        }
                    } else {
                        items(st, key = { it.member.id }) { standing ->
                            SquadMemberRow(standing = standing, maxWeeklySeconds = maxWeekly)
                        }
                    }
                    item {
                        Spacer(Modifier.height(6.dp))
                        Text("SHARE STANDINGS", style = focivoEyebrow())
                        Spacer(Modifier.height(12.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0A0C0B))
                                .padding(vertical = 8.dp),
                        ) {
                            SquadShareActions(
                                onWhatsApp = { shareStandings(toWhatsApp = true) },
                                onMore = { shareStandings(toWhatsApp = false) },
                                onDownload = ::downloadStandings,
                            )
                        }
                    }
                    item {
                        Spacer(Modifier.height(4.dp))
                        val isOwner = myUserId != null && myUserId == s.ownerUserId
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {
                                        if (leaving) return@clickable
                                        leaving = true
                                        scope.launch {
                                            val res = if (isOwner) SquadRepository.deleteSquad(s.id)
                                            else SquadRepository.leaveSquad(s.id, myUserId.orEmpty())
                                            leaving = false
                                            res.onSuccess { onBack() }
                                                .onFailure {
                                                    Toast.makeText(
                                                        context,
                                                        it.message ?: "Could not leave the squad.",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                        }
                                    }
                                )
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (leaving) "Working..." else if (isOwner) "Delete squad" else "Leave squad",
                                style = TextStyle(fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = Color(0xFFE5484D)),
                            )
                        }
                    }
                }
            }
        }
    }
}
