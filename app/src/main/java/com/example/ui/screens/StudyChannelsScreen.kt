package com.example.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.NearBlack
import com.example.ui.theme.RegainLimeDeepText
import com.example.ui.theme.SecondaryTextLight
import com.example.ui.theme.isAppInDarkTheme
import com.example.util.StudyChannelsManager

// v1.2.3 design-unification — owner's clean-redesign language:
// #0A0A0B bg, #131315 cards, #1F1F22 lines, #B6F13A lime, #76767C muted,
// Poppins body + Instrument Serif italic for the page headline.
private val ChannelsLime = Color(0xFFB6F13A)

private val ChannelsSerifItalic = FontFamily(
    Font(R.font.instrument_serif_italic, FontWeight.Normal, FontStyle.Italic)
)

/**
 * v1.3.0 — "Study channels" screen (Regain's "Watch only study channels",
 * made real). Shows the built-in education channels plus the user's custom
 * additions; any channel can be removed. Videos from allowlisted channels
 * are always treated as study content by the YouTube guard.
 */
@Composable
fun StudyChannelsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val isDark = isAppInDarkTheme()

    LaunchedEffect(Unit) {
        StudyChannelsManager.init(context)
    }
    val channels by StudyChannelsManager.channelsState.collectAsState()
    var newChannel by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    fun submit() {
        val ok = StudyChannelsManager.addCustomChannel(context, newChannel)
        if (ok) {
            newChannel = ""
            showError = false
            focusManager.clearFocus()
        } else {
            showError = true
        }
    }

    val bg = if (isDark) Color(0xFF0A0A0B) else Color(0xFFF5FAFB)
    val cardBg = if (isDark) Color(0xFF131315) else Color.White
    val cardBorder = if (isDark) Color(0xFF1F1F22) else Color(0xFFDCE6D2)
    val titleColor = if (isDark) Color(0xFFF4F4F5) else NearBlack
    val bodyColor = if (isDark) Color(0xFF76767C) else SecondaryTextLight
    val accentText = if (isDark) ChannelsLime else RegainLimeDeepText

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // Top bar
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(cardBg)
                    .border(1.dp, cardBorder, CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = titleColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Study channels",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = ChannelsSerifItalic,
                        fontWeight = FontWeight.Bold,
                        color = titleColor,
                        fontSize = 22.sp
                    )
                )
                Text(
                    text = "${channels.size} channels allowlisted",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = bodyColor,
                        fontSize = 12.sp
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Videos from these channels are always treated as study content by the AI guard.",
            style = MaterialTheme.typography.bodySmall.copy(
                color = bodyColor,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Add custom channel
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = newChannel,
                onValueChange = { newChannel = it; showError = false },
                placeholder = { Text("Add a channel name…", fontSize = 14.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                isError = showError,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp)
            )
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(ChannelsLime)
                    .clickable(onClick = { submit() }),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add channel",
                    tint = NearBlack,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        if (showError) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Please enter a valid, non-duplicate channel name.",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color(0xFFFF5252),
                    fontSize = 12.sp
                )
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Channel list
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(channels, key = { it.name }) { entry ->
                Surface(
                    color = cardBg,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, cardBorder, RoundedCornerShape(14.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.School,
                            contentDescription = null,
                            tint = accentText,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = entry.name,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = titleColor,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        if (entry.isCustom) {
                            Surface(
                                color = ChannelsLime.copy(alpha = 0.18f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "CUSTOM",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = accentText,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .clickable { StudyChannelsManager.removeChannel(context, entry) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove channel",
                                tint = bodyColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Reset
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { StudyChannelsManager.resetToDefaults(context) }
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Reset to defaults",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = accentText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            )
        }
    }
}
