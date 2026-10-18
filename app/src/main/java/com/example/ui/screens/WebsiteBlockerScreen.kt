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
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
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
import com.example.ui.theme.focivoTokens
import com.example.util.WebsiteBlockManager

// v1.1.1 — "Website blocker" management screen. Mirrors StudyChannelsScreen's
// premium design language: the WebsiteBlockManager engine existed with a full
// default domain list + custom add/remove, but had ZERO call sites and no UI.
// This screen wires it up: blocked domains are enforced in browsers during
// study sessions (see the deterministic domain check in the accessibility
// service's browser guard). Study websites still win — the engine consults
// the educational allowlist before any block.
private val BlockerSerifItalic = FontFamily(
    Font(R.font.instrument_serif_italic, FontWeight.Normal, FontStyle.Italic)
)

@Composable
fun WebsiteBlockerScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        WebsiteBlockManager.init(context)
    }
    val domains by WebsiteBlockManager.blockedDomainsState.collectAsState()

    var newDomain by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    fun submit() {
        val ok = WebsiteBlockManager.addCustomBlockedDomain(context, newDomain)
        if (ok) {
            newDomain = ""
            showError = false
            focusManager.clearFocus()
        } else {
            showError = true
        }
    }

    val bg = focivoTokens().bg
    val cardBg = focivoTokens().surface
    val cardBorder = focivoTokens().line
    val titleColor = focivoTokens().text
    val bodyColor = focivoTokens().muted
    val accentText = focivoTokens().accentText
    val lime = focivoTokens().accent

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
                    text = "Website blocker",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = BlockerSerifItalic,
                        fontWeight = FontWeight.Bold,
                        color = titleColor,
                        fontSize = 22.sp
                    )
                )
                Text(
                    text = "${domains.size} sites blocked during study",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = bodyColor,
                        fontSize = 12.sp
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Pages on these domains are blocked in browsers during a study session. " +
                "Study websites you have allowlisted always win — this list never blocks learning.",
            style = MaterialTheme.typography.bodySmall.copy(
                color = bodyColor,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Add custom domain
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = newDomain,
                onValueChange = { newDomain = it; showError = false },
                placeholder = { Text("Add a site, e.g. netflix.com", fontSize = 14.sp) },
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
                    .background(lime)
                    .clickable(onClick = { submit() }),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add website",
                    tint = NearBlack,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        if (showError) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Please enter a valid, non-duplicate domain.",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = focivoTokens().danger,
                    fontSize = 12.sp
                )
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Domain list
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(domains, key = { it.domain }) { entry ->
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
                            imageVector = Icons.Default.Block,
                            contentDescription = null,
                            tint = focivoTokens().danger,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = entry.domain,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = titleColor,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        if (entry.isCustom) {
                            Surface(
                                color = lime.copy(alpha = 0.18f),
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
                                .clickable {
                                    WebsiteBlockManager.removeBlockedDomain(context, entry)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove website",
                                tint = bodyColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { WebsiteBlockManager.resetToDefaults(context) }
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
