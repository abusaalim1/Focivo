package com.example.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext

/**
 * Focivo 1.3.2, item 8 — bundled preset pictures.
 *
 * A preset reference is a plain string stored wherever an avatar/picture
 * value is stored (leaderboard `avatar_url`, squad `picture_url`):
 *   - `preset:avatar:a03`  -> app/src/main/assets/presets/avatars/a03.webp
 *   - `preset:group:g07`   -> app/src/main/assets/presets/groups/g07.webp
 *
 * The id is the asset filename without extension. Renderers check
 * [isPresetRef] first and fall back to their existing URL/base64 path
 * for anything else, so uploaded photos are untouched.
 */
const val PRESET_AVATAR_PREFIX = "preset:avatar:"
const val PRESET_GROUP_PREFIX = "preset:group:"

private const val AVATAR_ASSET_DIR = "presets/avatars"
private const val GROUP_ASSET_DIR = "presets/groups"

/** True when [value] is a preset reference (avatar or group). */
fun isPresetRef(value: String?): Boolean =
    !value.isNullOrBlank() &&
        (value.startsWith(PRESET_AVATAR_PREFIX) || value.startsWith(PRESET_GROUP_PREFIX))

/**
 * Maps a preset reference to its bundled asset path, e.g.
 * `preset:avatar:a03` -> `presets/avatars/a03.webp`.
 */
fun presetAssetPath(value: String): String {
    val id = value.substringAfterLast(":").ifBlank { "a01" }
    val dir = if (value.startsWith(PRESET_AVATAR_PREFIX)) AVATAR_ASSET_DIR else GROUP_ASSET_DIR
    return "$dir/$id.webp"
}

/** Builds the avatar preset reference for [id] (asset filename without extension). */
fun presetRefForAvatar(id: String): String = "$PRESET_AVATAR_PREFIX$id"

/** Builds the group preset reference for [id] (asset filename without extension). */
fun presetRefForGroup(id: String): String = "$PRESET_GROUP_PREFIX$id"

/**
 * All preset ids currently bundled (sorted). Reads the asset folder at
 * runtime so newly added files appear automatically.
 */
fun avatarPresetIds(context: Context): List<String> = presetIds(context, AVATAR_ASSET_DIR)

/** All group-logo preset ids currently bundled (sorted). */
fun groupPresetIds(context: Context): List<String> = presetIds(context, GROUP_ASSET_DIR)

private fun presetIds(context: Context, dir: String): List<String> {
    return try {
        (context.assets.list(dir) ?: emptyArray())
            .filter { it.endsWith(".webp", ignoreCase = true) }
            .map { it.removeSuffix(".webp").removeSuffix(".WEBP") }
            .sorted()
    } catch (_: Exception) {
        emptyList()
    }
}

/**
 * Renders a bundled preset picture. Renders nothing when [ref] is not a
 * preset reference or the asset cannot be decoded — callers guard with
 * [isPresetRef] and fall back to their existing path.
 */
@Composable
fun PresetImage(
    ref: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val context = LocalContext.current
    val bitmap = remember(ref) {
        if (!isPresetRef(ref)) {
            null
        } else {
            try {
                context.assets.open(presetAssetPath(ref)).use { input ->
                    BitmapFactory.decodeStream(input)?.asImageBitmap()
                }
            } catch (_: Exception) {
                null
            }
        }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
        )
    }
}
