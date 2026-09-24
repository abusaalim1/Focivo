package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.NearBlack
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.RegainLimeDeepText
import com.example.ui.theme.RegainLimePrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicySheet(
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    val darkSurface = Color(0xFF141814)
    val cardBg = Color(0xFF1C221C)
    val textPrimary = Color(0xFFF0F4ED)
    val textSecondary = Color(0xFFA5B0A4)
    val limeAccent = RegainLimePrimary
    val scrollState = rememberScrollState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = darkSurface,
        scrimColor = Color.Black.copy(alpha = 0.65f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(42.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(limeAccent.copy(alpha = 0.5f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .testTag("privacy_policy_sheet")
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(limeAccent.copy(alpha = 0.2f))
                            .border(1.dp, limeAccent.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PrivacyTip,
                            contentDescription = null,
                            tint = limeAccent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Privacy Policy",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = PoppinsFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = textPrimary
                            )
                        )
                        Text(
                            text = "Last updated: September 2026",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = PoppinsFontFamily,
                                color = textSecondary,
                                fontSize = 11.sp
                            )
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0x22FFFFFF))
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Privacy Policy",
                        tint = textPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Body Content (Scrollable)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(20.dp)),
                color = cardBg
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Thank you for using Focivo. Your privacy matters to us, and we want to be completely transparent about what data we collect, why we collect it, and how it's used. Please read this policy carefully before using the app.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = PoppinsFontFamily,
                            color = textSecondary,
                            fontSize = 13.sp,
                            lineHeight = 19.sp
                        )
                    )

                    // Section 1
                    PolicySection(
                        number = "1",
                        title = "Who We Are",
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        accentColor = limeAccent
                    ) {
                        Text(
                            text = "Focivo is a study focus and app-blocking application designed to help students and professionals build better focus habits by blocking distracting apps during study sessions.\n\nFor any questions about this policy, you can reach us at: focivo.app@gmail.com",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = PoppinsFontFamily,
                                color = textSecondary,
                                fontSize = 13.sp,
                                lineHeight = 19.sp
                            )
                        )
                    }

                    // Section 2
                    PolicySection(
                        number = "2",
                        title = "Information We Collect",
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        accentColor = limeAccent
                    ) {
                        SubSectionHeader("2.1 Account Information", textPrimary)
                        BulletPoint("Your email address", textSecondary)
                        BulletPoint("Your full name", textSecondary)
                        BulletPoint("A password (securely hashed and managed by our authentication provider, Supabase — we never see or store your raw password)", textSecondary)

                        Spacer(modifier = Modifier.height(8.dp))
                        SubSectionHeader("2.2 Usage Data", textPrimary)
                        Text(text = "To provide core app features, we collect:", color = textSecondary, fontSize = 13.sp, fontFamily = PoppinsFontFamily)
                        BulletPoint("Study session data (duration, start/end times, session type)", textSecondary)
                        BulletPoint("Focus streaks, points, and achievement progress", textSecondary)
                        BulletPoint("Schedules and alarms you create", textSecondary)
                        BulletPoint("App blocking preferences (which apps you choose to block, and when)", textSecondary)
                        BulletPoint("Leaderboard participation data (if you choose to appear on the leaderboard)", textSecondary)

                        Spacer(modifier = Modifier.height(8.dp))
                        SubSectionHeader("2.3 Profile Picture", textPrimary)
                        Text(
                            text = "If you choose to upload a profile picture, it is stored securely and is only visible to you (and, if you participate in the leaderboard, potentially visible to other users as part of your public profile).",
                            color = textSecondary,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            fontFamily = PoppinsFontFamily
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        SubSectionHeader("2.4 Onboarding Survey Responses", textPrimary)
                        Text(
                            text = "If you complete our onboarding survey, we collect your responses (e.g. class/education level, study goals, distraction preferences) to personalize your experience — for example, adjusting how urgently the app reminds you about screen time based on whether you're in an exam year.",
                            color = textSecondary,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            fontFamily = PoppinsFontFamily
                        )
                    }

                    // Section 3
                    PolicySection(
                        number = "3",
                        title = "Accessibility Service Permission (AI Study Guard)",
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        accentColor = limeAccent
                    ) {
                        Text(
                            text = "If you choose to enable our optional \"AI Study Guard\" feature:",
                            color = textSecondary,
                            fontSize = 13.sp,
                            fontFamily = PoppinsFontFamily
                        )
                        BulletPoint("We request Android's Accessibility Service permission", textSecondary)
                        BulletPoint("This permission allows AI Academic Sentinel to verify study context only during an active study session or scheduled focus window. 100% private and on-device.", textSecondary)
                        BulletPoint("This is used solely to determine whether your activity in those apps appears study-related or not", textSecondary)
                        BulletPoint("We do not read, record, or monitor any other app on your device — not your messages, not your browser, not your photos, nothing else", textSecondary)
                        BulletPoint("All text analysis happens entirely on your device. We do not transmit any captured on-screen text to our servers or any third party", textSecondary)
                        BulletPoint("This feature is entirely optional. Declining it does not affect any other part of the app", textSecondary)
                        BulletPoint("You can revoke this permission at any time from your device's Settings → Accessibility, or from within Focivo's own settings", textSecondary)

                        Spacer(modifier = Modifier.height(8.dp))
                        SubSectionHeader("Battery Usage", textPrimary)
                        Text(
                            text = "Enabling this feature runs a background monitoring service, which may result in a modest increase in battery consumption. You can disable it at any time if this becomes a concern, with no impact on any other app feature.",
                            color = textSecondary,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            fontFamily = PoppinsFontFamily
                        )
                    }

                    // Section 4
                    PolicySection(
                        number = "4",
                        title = "Strict Study Mode",
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        accentColor = limeAccent
                    ) {
                        Text(
                            text = "If you choose to enable \"Strict Mode,\" Focivo helps you avoid distractions by locking distraction-prone apps during your active study timer or scheduled focus hours. It is completely safe, customizable, and can be configured at any time within your app settings.",
                            color = textSecondary,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            fontFamily = PoppinsFontFamily
                        )
                    }

                    // Section 5
                    PolicySection(
                        number = "5",
                        title = "Usage Access Permission",
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        accentColor = limeAccent
                    ) {
                        Text(
                            text = "To detect how long certain apps are used and to enforce app-blocking schedules, we request Usage Access (PACKAGE_USAGE_STATS) permission. This tells us which apps are open and for how long — it does not let us see the content inside those apps.",
                            color = textSecondary,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            fontFamily = PoppinsFontFamily
                        )
                    }

                    // Section 6
                    PolicySection(
                        number = "6",
                        title = "How We Use Your Information",
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        accentColor = limeAccent
                    ) {
                        Text(text = "We use the information described above to:", color = textSecondary, fontSize = 13.sp, fontFamily = PoppinsFontFamily)
                        BulletPoint("Provide and maintain core app functionality (focus timers, app blocking, schedules, alarms)", textSecondary)
                        BulletPoint("Track your progress, streaks, and achievements", textSecondary)
                        BulletPoint("Personalize reminders and notifications based on your stated goals and survey responses", textSecondary)
                        BulletPoint("Display leaderboard rankings, if you choose to participate", textSecondary)
                        BulletPoint("Improve app performance and fix bugs", textSecondary)

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "We do not sell your personal data to third parties, and we do not use your data for targeted advertising.",
                            color = textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            fontFamily = PoppinsFontFamily
                        )
                    }

                    // Section 7
                    PolicySection(
                        number = "7",
                        title = "Data Storage and Security",
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        accentColor = limeAccent
                    ) {
                        Text(
                            text = "Your data is stored using Supabase, a secure backend infrastructure provider. All data is protected using Row Level Security (RLS), meaning your personal data (study sessions, schedules, alarms, preferences) can only be accessed by your own authenticated account — no other user can view or modify your data.",
                            color = textSecondary,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            fontFamily = PoppinsFontFamily
                        )
                    }

                    // Section 8
                    PolicySection(
                        number = "8",
                        title = "Data Sharing",
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        accentColor = limeAccent
                    ) {
                        Text(text = "We do not share your personal information with third parties, except:", color = textSecondary, fontSize = 13.sp, fontFamily = PoppinsFontFamily)
                        BulletPoint("Where required by law", textSecondary)
                        BulletPoint("With service providers who help us operate the app (e.g. Supabase for backend infrastructure), who are bound by their own privacy and security obligations", textSecondary)
                    }

                    // Section 9
                    PolicySection(
                        number = "9",
                        title = "Your Rights",
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        accentColor = limeAccent
                    ) {
                        Text(text = "You have the right to:", color = textSecondary, fontSize = 13.sp, fontFamily = PoppinsFontFamily)
                        BulletPoint("Access the personal data we hold about you", textSecondary)
                        BulletPoint("Request correction of inaccurate data", textSecondary)
                        BulletPoint("Request deletion of your account and associated data", textSecondary)
                        BulletPoint("Withdraw consent for optional features (Accessibility Service, Focus Shield) at any time", textSecondary)

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "To exercise any of these rights, contact us at focivo.app@gmail.com.",
                            color = textSecondary,
                            fontSize = 13.sp,
                            fontFamily = PoppinsFontFamily
                        )
                    }

                    // Section 10
                    PolicySection(
                        number = "10",
                        title = "Donations",
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        accentColor = limeAccent
                    ) {
                        Text(
                            text = "Focivo includes an optional donation feature allowing users to voluntarily support the app's development via UPI or PayPal. This is entirely optional, is not tied to unlocking any feature, and we do not store or process your payment information — payments are handled directly through UPI or PayPal's own secure systems, not by us.",
                            color = textSecondary,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            fontFamily = PoppinsFontFamily
                        )
                    }

                    // Section 11
                    PolicySection(
                        number = "11",
                        title = "Children's Privacy",
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        accentColor = limeAccent
                    ) {
                        Text(
                            text = "Focivo is intended for students and may be used by users under 18. We do not knowingly collect more information than necessary to provide the app's core functionality, and we encourage parents/guardians to be aware of and involved in their child's use of any app that includes screen-time and app-blocking features.",
                            color = textSecondary,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            fontFamily = PoppinsFontFamily
                        )
                    }

                    // Section 12
                    PolicySection(
                        number = "12",
                        title = "Changes to This Policy",
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        accentColor = limeAccent
                    ) {
                        Text(
                            text = "We may update this Privacy Policy from time to time. If we make material changes, we will notify users within the app or via the email associated with their account. Continued use of Focivo after changes take effect constitutes acceptance of the revised policy.",
                            color = textSecondary,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            fontFamily = PoppinsFontFamily
                        )
                    }

                    // Section 13
                    PolicySection(
                        number = "13",
                        title = "Contact Us",
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        accentColor = limeAccent
                    ) {
                        Text(
                            text = "If you have any questions, concerns, or requests regarding this Privacy Policy or your data, please reach out to:\n\nEmail: focivo.app@gmail.com",
                            color = textSecondary,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            fontFamily = PoppinsFontFamily
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "By creating an account and using Focivo, you acknowledge that you have read and understood this Privacy Policy.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = PoppinsFontFamily,
                            color = limeAccent,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Medium
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun PolicySection(
    number: String,
    title: String,
    textPrimary: Color,
    textSecondary: Color,
    accentColor: Color,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = number,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                        fontSize = 11.sp
                    )
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary,
                    fontSize = 14.sp
                )
            )
        }
        content()
    }
}

@Composable
private fun SubSectionHeader(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall.copy(
            fontFamily = PoppinsFontFamily,
            fontWeight = FontWeight.SemiBold,
            color = color,
            fontSize = 13.sp
        ),
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
    )
}

@Composable
private fun BulletPoint(text: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "• ",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Bold,
                color = RegainLimePrimary,
                fontSize = 13.sp
            )
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = PoppinsFontFamily,
                color = color,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        )
    }
}
