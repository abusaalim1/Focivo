package com.example.ui.components

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
const val AVATAR_UNLOCK_SCHOLAR = 1000
const val AVATAR_UNLOCK_GRANDMASTER = 5000

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
 * Structured avatar gallery. Id ranges (owner spec 2026-10-17):
 * - Boys FREE: a01–a10 | Boys LOCKED: a16–a20 (Scholar), a21–a25 (Grandmaster)
 * - Girls FREE: a11–a15, a26–a30 | Girls LOCKED: a31–a35 (Scholar), a36–a40 (Grandmaster)
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
    // Boys
    idRange("a", 1, 10).forEach { defs += AvatarPreset(it, AvatarSection.BOYS, 0) }
    idRange("a", 16, 20).forEach { defs += AvatarPreset(it, AvatarSection.BOYS, AVATAR_UNLOCK_SCHOLAR) }
    idRange("a", 21, 25).forEach { defs += AvatarPreset(it, AvatarSection.BOYS, AVATAR_UNLOCK_GRANDMASTER) }
    // Girls
    idRange("a", 11, 15).forEach { defs += AvatarPreset(it, AvatarSection.GIRLS, 0) }
    idRange("a", 26, 30).forEach { defs += AvatarPreset(it, AvatarSection.GIRLS, 0) }
    idRange("a", 31, 35).forEach { defs += AvatarPreset(it, AvatarSection.GIRLS, AVATAR_UNLOCK_SCHOLAR) }
    idRange("a", 36, 40).forEach { defs += AvatarPreset(it, AvatarSection.GIRLS, AVATAR_UNLOCK_GRANDMASTER) }
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selection by remember { mutableStateOf(initialSelection) }

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
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(2.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.heightIn(max = 336.dp),
            ) {
                items(ids, key = { it }) { id ->
                    val ref = refFor(id)
                    val selected = ref == selectedRef
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(tokens.surface2)
                            .border(
                                width = if (selected) 2.5.dp else 1.dp,
                                color = if (selected) RegainLimePrimary else tokens.line,
                                shape = RoundedCornerShape(20.dp),
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onSelectionChange(ref) },
                            )
                            .testTag("preset_tile_$id"),
                        contentAlignment = Alignment.Center,
                    ) {
                        PresetImage(
                            ref = ref,
                            contentDescription = "Preset $id",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(96.dp)
                                .clip(RoundedCornerShape(20.dp)),
                            contentScale = ContentScale.Crop,
                        )
                        if (selected) {
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
                    }
                }
            }
        }

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
                text = "Use this design",
                style = TextStyle(
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                ),
            )
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
 * selectable until unlocked (or admin).
 */
@Composable
private fun AvatarSectionsGrid(
    presets: List<AvatarPreset>,
    selectedRef: String?,
    onSelectionChange: (String?) -> Unit,
    focusPoints: Int,
    isAdmin: Boolean,
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
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(2.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.heightIn(max = 336.dp),
                ) {
                    items(sectionPresets, key = { it.id }) { preset ->
                        val unlocked = preset.isUnlocked(focusPoints, isAdmin)
                        val selected = preset.ref == selectedRef
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(tokens.surface2)
                                .border(
                                    width = if (selected) 2.5.dp else 1.dp,
                                    color = if (selected) RegainLimePrimary else tokens.line,
                                    shape = RoundedCornerShape(20.dp),
                                )
                                .then(
                                    if (unlocked) Modifier.clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = { onSelectionChange(preset.ref) },
                                    ) else Modifier
                                )
                                .testTag("preset_tile_${preset.id}"),
                            contentAlignment = Alignment.Center,
                        ) {
                            PresetImage(
                                ref = preset.ref,
                                contentDescription = "Avatar ${preset.id}",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(96.dp)
                                    .clip(RoundedCornerShape(20.dp)),
                                contentScale = ContentScale.Crop,
                            )
                            if (!unlocked) {
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
                            } else if (selected) {
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
                        }
                    }
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
