package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.RegainLimePrimary
import com.example.ui.theme.SerifItalicFontFamily
import com.example.ui.theme.focivoEyebrow
import com.example.ui.theme.focivoTokens

/**
 * Focivo 1.3.2, item 8 — premium chooser for profile pictures and group
 * pictures.
 *
 * AVATAR mode (Edit Profile): preset avatars ONLY, no upload. Two sections
 * (Boys / Girls), each with 10 FREE + 10 LOCKED avatars on the points ladder
 * (Scholar 1000 pts, Grandmaster 5000 pts). Locked tiles show lock + price.
 * GROUP mode (Create Group): "Upload photo" + flat preset grid, no locks.
 *
 * Design language: focivoTokens, Poppins body labels, Instrument Serif
 * italic headline. English only, zero emojis.
 */
enum class PresetChooserMode { AVATAR, GROUP }

/** Avatar gallery section. */
enum class AvatarSection { BOYS, GIRLS }

/** Points tiers for locked avatars (points are never spent — threshold unlock). */
const val AVATAR_UNLOCK_TIER_1 = 500
const val AVATAR_UNLOCK_TIER_2 = 1000

/**
 * A single avatar preset with its section and lock threshold.
 * unlockPoints = 0 → FREE.
 */
data class AvatarPreset(
    val id: String,
    val section: AvatarSection,
    val unlockPoints: Int = 0,
) {
    val ref: String get() = presetRefForAvatar(id)
    val isFree: Boolean get() = unlockPoints <= 0
    fun isUnlocked(userPoints: Int, isAdmin: Boolean): Boolean =
        isAdmin || userPoints >= unlockPoints
}

/**
 * Structured avatar gallery. Id ranges (owner spec 2026-10-17, rev 20:21 —
 * HUMANS ONLY; each section 10 FREE + 15 LOCKED = 25):
 * - Boys FREE: a41–a50 | Boys LOCKED: a51–a60, a66–a70 (8 at 500 pts, 7 at 1000 pts)
 * - Girls FREE: a11–a15, a26–a30 | Girls LOCKED: a31–a40, a61–a65 (8 at 500 pts, 7 at 1000 pts)
 * Missing asset files are omitted (no crash, no placeholder art).
 */
fun avatarPresets(context: android.content.Context): List<AvatarPreset> {
    val available = try {
        (context.assets.list("presets/avatars") ?: emptyArray())
            .filter { it.endsWith(".webp", ignoreCase = true) }
            .map { it.substringBeforeLast(".") }
            .toSet()
    } catch (_: Exception) { emptySet() }

    fun idRange(prefix: String, from: Int, to: Int): List<String> =
        (from..to).map { prefix + it.toString().padStart(2, '0') }

    val defs = mutableListOf<AvatarPreset>()
    // Boys (humans only): 10 free + 15 locked (8 at 500 pts, 7 at 1000 pts)
    idRange("a", 41, 50).forEach { defs += AvatarPreset(it, AvatarSection.BOYS, 0) }
    idRange("a", 51, 58).forEach { defs += AvatarPreset(it, AvatarSection.BOYS, AVATAR_UNLOCK_TIER_1) }
    idRange("a", 59, 60).forEach { defs += AvatarPreset(it, AvatarSection.BOYS, AVATAR_UNLOCK_TIER_2) }
    idRange("a", 66, 70).forEach { defs += AvatarPreset(it, AvatarSection.BOYS, AVATAR_UNLOCK_TIER_2) }
    // Girls: 10 free + 15 locked (8 at 500 pts, 7 at 1000 pts)
    idRange("a", 11, 15).forEach { defs += AvatarPreset(it, AvatarSection.GIRLS, 0) }
    idRange("a", 26, 30).forEach { defs += AvatarPreset(it, AvatarSection.GIRLS, 0) }
    idRange("a", 31, 38).forEach { defs += AvatarPreset(it, AvatarSection.GIRLS, AVATAR_UNLOCK_TIER_1) }
    idRange("a", 39, 40).forEach { defs += AvatarPreset(it, AvatarSection.GIRLS, AVATAR_UNLOCK_TIER_2) }
    idRange("a", 61, 65).forEach { defs += AvatarPreset(it, AvatarSection.GIRLS, AVATAR_UNLOCK_TIER_2) }
    return defs.filter { it.id in available }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetPhotoChooserSheet(
    mode: PresetChooserMode,
    onUploadPhoto: () -> Unit,
    onPresetSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    initialSelection: String? = null,
    modifier: Modifier = Modifier,
    /** Focus points for avatar lock checks (AVATAR mode only). */
    focusPoints: Int = 0,
    /** Admin bypass for avatar locks (AVATAR mode only). */
    isAdmin: Boolean = false,
) {
    val tokens = focivoTokens()
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // v1.3.4 (item 8): keyed on initialSelection — a late-arriving current
    // value (e.g. prefs loading after the dialog opened) can no longer leave
    // the sheet's selection desynced from the confirm button's enabled state.
    var selection by remember(initialSelection) { mutableStateOf(initialSelection) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = tokens.surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(tokens.line)
            )
        },
        modifier = modifier.testTag("preset_chooser_sheet")
    ) {
        PresetChooserContent(
            mode = mode,
            selectedRef = selection,
            onSelectionChange = { selection = it },
            onUploadPhoto = onUploadPhoto,
            onPresetConfirmed = { selection?.let(onPresetSelected) },
            focusPoints = focusPoints,
            isAdmin = isAdmin,
            // v1.3.4 (item 8): locked tiles used to swallow taps silently —
            // no selection formed, so the confirm button stayed disabled and
            // it looked like no Continue/Select action existed. The tap now
            // explains the lock instead of doing nothing.
            onLockedTileTap = { preset ->
                android.widget.Toast.makeText(
                    context,
                    "Locked \u2014 earn ${preset.unlockPoints} Focus Points to unlock this avatar.",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            },
        )
    }
}

/**
 * The chooser body on its own — used by [PresetPhotoChooserSheet] and by
 * the Roborazzi screenshot test (test sources only).
 */
@Composable
fun PresetChooserContent(
    mode: PresetChooserMode,
    selectedRef: String?,
    onSelectionChange: (String?) -> Unit,
    onUploadPhoto: () -> Unit,
    onPresetConfirmed: () -> Unit,
    modifier: Modifier = Modifier,
    focusPoints: Int = 0,
    isAdmin: Boolean = false,
    /**
     * v1.3.4 (item 8): fired when a LOCKED avatar tile is tapped. Locked
     * tiles are not selectable, but the tap must never be silent — the
     * caller explains the lock (default: no-op; the sheet shows a Toast).
     */
    onLockedTileTap: (AvatarPreset) -> Unit = {},
) {
    val tokens = focivoTokens()
    val context = LocalContext.current
    val ids = remember(mode) {
        if (mode == PresetChooserMode.AVATAR) avatarPresetIds(context) else groupPresetIds(context)
    }
    val refFor: (String) -> String = remember(mode) {
        if (mode == PresetChooserMode.AVATAR) ::presetRefForAvatar else ::presetRefForGroup
    }
    val eyebrow = if (mode == PresetChooserMode.AVATAR) "PROFILE PICTURE" else "GROUP PICTURE"

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(tokens.surface)
            .padding(horizontal = 22.dp)
            .padding(bottom = 28.dp)
    ) {
        Text(
            text = eyebrow,
            style = focivoEyebrow(),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Pick a look",
            style = TextStyle(
                fontFamily = SerifItalicFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
                color = tokens.text,
            ),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (mode == PresetChooserMode.AVATAR)
                "Choose one of the built-in avatars."
            else
                "Upload your own photo, or choose one of the built-in designs.",
            style = TextStyle(
                fontFamily = PoppinsFontFamily,
                fontSize = 13.sp,
                color = tokens.muted,
            ),
        )
        Spacer(Modifier.height(16.dp))

        // v1.1.1: the grids below can exceed the screen height (Boys + Girls
        // sections), which used to push the "Use this design" button below
        // the visible sheet — the owner reported no save option was visible.
        // The middle scrolls; the header and the confirm button stay pinned.
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
        ) {
        // ——— Upload photo (GROUP mode only; AVATAR is presets-only) ————
        if (mode == PresetChooserMode.GROUP) {
            PresetChooserOptionRow(
                icon = {
                    Icon(
                        Icons.Default.CloudUpload,
                        contentDescription = null,
                        tint = tokens.accentText,
                        modifier = Modifier.size(22.dp),
                    )
                },
                title = "Upload photo",
                subtitle = "From your gallery",
                onClick = onUploadPhoto,
            )
            Spacer(Modifier.height(10.dp))
        }

        // ——— Choose a preset header ——————————————————————
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.GridOn,
                contentDescription = null,
                tint = tokens.accentText,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Choose a preset",
                style = TextStyle(
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = tokens.text,
                ),
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${ids.size} designs",
                style = TextStyle(
                    fontFamily = PoppinsFontFamily,
                    fontSize = 12.sp,
                    color = tokens.muted,
                ),
            )
        }
        Spacer(Modifier.height(12.dp))

        if (mode == PresetChooserMode.AVATAR) {
            AvatarSectionsGrid(
                presets = remember { avatarPresets(context) },
                selectedRef = selectedRef,
                onSelectionChange = onSelectionChange,
                focusPoints = focusPoints,
                isAdmin = isAdmin,
                onLockedTileTap = onLockedTileTap,
            )
        } else if (ids.isEmpty()) {
            Text(
                text = "No presets available right now.",
                style = TextStyle(
                    fontFamily = PoppinsFontFamily,
                    fontSize = 13.sp,
                    color = tokens.muted,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                textAlign = TextAlign.Center,
            )
        } else {
            // v1.1.1: plain chunked rows (NOT a LazyVerticalGrid) — lazy
            // grids refuse infinity max-height inside the sheet's vertical
            // scroll even with userScrollEnabled=false.
            ChunkedPresetGrid(
                items = ids,
                key = { it },
            ) { id, tileModifier ->
                val ref = refFor(id)
                PresetTile(
                    modifier = tileModifier,
                    ref = ref,
                    contentDescription = "Preset $id",
                    selected = ref == selectedRef,
                    enabled = true,
                    onClick = { onSelectionChange(ref) },
                    testTag = "preset_tile_$id",
                )
            }
        }

        } // end scrollable middle

        Spacer(Modifier.height(18.dp))
        Button(
            onClick = onPresetConfirmed,
            enabled = selectedRef != null,
            colors = ButtonDefaults.buttonColors(
                containerColor = RegainLimePrimary,
                contentColor = Color(0xFF0D1B05),
                disabledContainerColor = tokens.line,
                disabledContentColor = tokens.muted,
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("preset_confirm_button"),
        ) {
            Text(
                // v1.3.4 (item 8): mode-aware label — the avatar flow gets a
                // clear "Use" action ("Use This Avatar").
                text = if (mode == PresetChooserMode.AVATAR) "Use This Avatar" else "Use this design",
                style = TextStyle(
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                ),
            )
        }
    }
}

/**
 * v1.1.1: one preset tile. Shared by both chooser modes so the tile look
 * (selected lime ring, optional lock overlay) stays identical.
 *
 * v1.3.4 (item 8): [onDisabledClick] fires when a disabled (locked) tile is
 * tapped — the tap is acknowledged (e.g. "locked" explainer) instead of
 * silently swallowed.
 */
@Composable
private fun PresetTile(
    ref: String,
    contentDescription: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
    overlay: (@Composable BoxScope.() -> Unit)? = null,
    onDisabledClick: (() -> Unit)? = null,
) {
    val tokens = focivoTokens()
    val clickableModifier =
        if (enabled || onDisabledClick != null) Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = { if (enabled) onClick() else onDisabledClick?.invoke() },
        ) else Modifier
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(tokens.surface2)
            .border(
                width = if (selected) 2.5.dp else 1.dp,
                color = if (selected) RegainLimePrimary else tokens.line,
                shape = RoundedCornerShape(20.dp),
            )
            .then(clickableModifier)
            .testTag(testTag),
        contentAlignment = Alignment.Center,
    ) {
        PresetImage(
            ref = ref,
            contentDescription = contentDescription,
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .clip(RoundedCornerShape(20.dp)),
            contentScale = ContentScale.Crop,
        )
        if (selected && enabled) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(RegainLimePrimary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = Color(0xFF0D1B05),
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        overlay?.let { this.it() }
    }
}

/**
 * v1.1.1: a fixed 3-column grid rendered as plain (non-lazy) chunked rows.
 * LazyVerticalGrid cannot live inside the sheet's vertical scroll — it
 * throws on infinity max-height even with userScrollEnabled=false — and
 * these lists are tiny (<= 25 items), so lazy layout buys nothing.
 */
@Composable
private fun <T> ChunkedPresetGrid(
    items: List<T>,
    key: (T) -> Any,
    columns: Int = 3,
    tile: @Composable (item: T, modifier: Modifier) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items.chunked(columns).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                rowItems.forEach { item ->
                    key(key(item)) {
                        tile(item, Modifier.weight(1f))
                    }
                }
                // Keep tile widths equal on a short last row.
                repeat(columns - rowItems.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PresetChooserOptionRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val tokens = focivoTokens()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(tokens.surface2)
            .border(1.dp, tokens.line, RoundedCornerShape(18.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag("preset_upload_option"),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(RegainLimePrimary.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            icon()
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = TextStyle(
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.5.sp,
                    color = tokens.text,
                ),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = TextStyle(
                    fontFamily = PoppinsFontFamily,
                    fontSize = 12.sp,
                    color = tokens.muted,
                ),
            )
        }
    }
}

/**
 * Avatar gallery with Boys/Girls sections. Each section shows its presets
 * in a 3-column grid; locked tiles show a lock + points price and are not
 * selectable until unlocked (or admin). Tapping a locked tile fires
 * [onLockedTileTap] so the tap is never silent (v1.3.4 item 8).
 */
@Composable
private fun AvatarSectionsGrid(
    presets: List<AvatarPreset>,
    selectedRef: String?,
    onSelectionChange: (String?) -> Unit,
    focusPoints: Int,
    isAdmin: Boolean,
    onLockedTileTap: (AvatarPreset) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val tokens = focivoTokens()
    Column(modifier = modifier.fillMaxWidth()) {
        AvatarSection.entries.forEach { section ->
            val sectionPresets = presets.filter { it.section == section }
            if (sectionPresets.isNotEmpty()) {
                Text(
                    text = if (section == AvatarSection.BOYS) "Boys" else "Girls",
                    style = TextStyle(
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = tokens.text,
                    ),
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                // v1.1.1: plain chunked rows (NOT a LazyVerticalGrid) — lazy
                // grids refuse infinity max-height inside the sheet's
                // vertical scroll even with userScrollEnabled=false.
                ChunkedPresetGrid(
                    items = sectionPresets,
                    key = { it.id },
                ) { preset, tileModifier ->
                    val unlocked = preset.isUnlocked(focusPoints, isAdmin)
                    PresetTile(
                        modifier = tileModifier,
                        ref = preset.ref,
                        contentDescription = "Avatar ${preset.id}",
                        selected = preset.ref == selectedRef,
                        enabled = unlocked,
                        onClick = { onSelectionChange(preset.ref) },
                        // v1.3.4 (item 8): a locked tile must acknowledge the
                        // tap instead of swallowing it — otherwise no
                        // selection forms and the confirm button never
                        // enables, which reads as "no Continue/Select button".
                        onDisabledClick = { onLockedTileTap(preset) },
                        testTag = "preset_tile_${preset.id}",
                        overlay = if (!unlocked) {
                            {
                                // Locked overlay: dim + lock + price
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .background(Color.Black.copy(alpha = 0.55f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            Icons.Default.Lock,
                                            contentDescription = "Locked",
                                            tint = Color.White,
                                            modifier = Modifier.size(22.dp),
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = "${preset.unlockPoints} pts",
                                            style = TextStyle(
                                                fontFamily = PoppinsFontFamily,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 11.sp,
                                                color = Color.White,
                                            ),
                                        )
                                    }
                                }
                            }
                        } else null,
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        if (presets.isEmpty()) {
            Text(
                text = "No avatars available right now.",
                style = TextStyle(
                    fontFamily = PoppinsFontFamily,
                    fontSize = 13.sp,
                    color = tokens.muted,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}
