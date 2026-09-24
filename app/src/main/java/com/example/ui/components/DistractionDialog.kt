package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.NearBlack
import com.example.ui.theme.RegainLimeDeepText
import com.example.ui.theme.RegainLimePrimary
import com.example.ui.theme.SecondaryTextLight
import com.example.ui.theme.isAppInDarkTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DistractionDialog(
    onDismiss: () -> Unit,
    onLogDistraction: (String) -> Unit
) {
    val isDark = isAppInDarkTheme()

    val options = listOf(
        Pair("Phone", Icons.Default.PhoneAndroid),
        Pair("Thought", Icons.Default.Lightbulb),
        Pair("Notification", Icons.Default.Notifications),
        Pair("Other", Icons.Default.MoreHoriz)
    )

    BasicAlertDialog(
        onDismissRequest = onDismiss
    ) {
        Box(
            modifier = Modifier
                .shadow(
                    elevation = 16.dp,
                    shape = RoundedCornerShape(28.dp),
                    ambientColor = Color(0x20000000),
                    spotColor = Color(0x308CE000)
                )
                .clip(RoundedCornerShape(28.dp))
                .background(if (isDark) Color(0xFF1D221C) else Color(0xFFFFFFFF))
                .border(
                    1.dp,
                    if (isDark) Color(0x308CE000) else Color(0x20000000),
                    RoundedCornerShape(28.dp)
                )
                .padding(24.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Log Distraction",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp,
                        color = if (isDark) Color(0xFFF0F4ED) else NearBlack
                    )
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Acknowledge the impulse and return to flow.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = if (isDark) Color(0xFFA0A89E) else SecondaryTextLight,
                        fontSize = 13.sp
                    )
                )

                Spacer(modifier = Modifier.height(20.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    options.forEach { (type, icon) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isDark) Color(0xFF262C24) else Color(0xFFF4F8EE))
                                .border(
                                    1.dp,
                                    if (isDark) Color(0x25FFFFFF) else Color(0xFFE2EBD6),
                                    RoundedCornerShape(16.dp)
                                )
                                .clickable {
                                    onLogDistraction(type)
                                    onDismiss()
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(RegainLimePrimary.copy(alpha = if (isDark) 0.25f else 0.18f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = type,
                                    tint = if (isDark) RegainLimePrimary else RegainLimeDeepText,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Text(
                                text = type,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isDark) Color(0xFFF0F4ED) else NearBlack
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                TextButton(onClick = onDismiss) {
                    Text(
                        text = "Never mind",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = if (isDark) Color(0xFFA0A89E) else SecondaryTextLight,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }
        }
    }
}
