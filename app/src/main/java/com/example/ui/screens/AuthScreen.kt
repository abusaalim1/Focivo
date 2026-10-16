package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.example.ui.components.PrivacyPolicySheet
import com.example.ui.components.PremiumButton
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.SerifItalicFontFamily
import com.example.ui.theme.focivoTokens
import com.example.ui.theme.isAppInDarkTheme

/** v1.2: which tab the auth screen opens on — set by the splash flow. */
enum class AuthMode { SignIn, CreateAccount }

/** v1.2: live privacy page, opened in the browser from the auth screen. */
private const val PRIVACY_POLICY_URL = "https://focivoapp.vercel.app/privacy.html"

private fun openUrlInBrowser(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        // best-effort: no browser available
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    initialMode: AuthMode = AuthMode.SignIn,
    isLoading: Boolean,
    errorMessage: String?,
    verificationMessage: String? = null,
    onLogin: (email: String, pin: String) -> Unit,
    onRegister: (email: String, pin: String, fullName: String) -> Unit,
    onGoogleSignIn: (context: Context) -> Unit = {},
    onGoogleDirectSignIn: (name: String, email: String) -> Unit = { _, _ -> },
    onGuestSignIn: () -> Unit = {},
    onClearError: () -> Unit,
    // v1.4.0-round2: forgot-password wiring
    passwordResetState: com.example.ui.viewmodel.FocuslyViewModel.PasswordResetState =
        com.example.ui.viewmodel.FocuslyViewModel.PasswordResetState.Idle,
    pendingPasswordRecovery: Boolean = false,
    onForgotPassword: (email: String) -> Unit = {},
    onClearPasswordReset: () -> Unit = {},
    onSetNewPassword: (password: String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    var isRegisterMode by remember(initialMode) { mutableStateOf(initialMode == AuthMode.CreateAccount) }
    var nameInput by remember { mutableStateOf("") }
    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var showGoogleAccountDialog by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }
    var hasAcceptedPrivacyPolicy by remember { mutableStateOf(false) }
    var showPrivacyPolicySheet by remember { mutableStateOf(false) }
    // v1.4.0-round2
    var showForgotPasswordDialog by remember { mutableStateOf(false) }

    val activeError = localError ?: errorMessage

    // Detect state hints for smart auto-switching
    val isAccountNotFoundError = activeError != null && (
            activeError.contains("No account found", ignoreCase = true) ||
            activeError.contains("create a new account", ignoreCase = true)
    )
    val isAccountAlreadyExistsError = activeError != null && (
            activeError.contains("already exists", ignoreCase = true) ||
            activeError.contains("already registered", ignoreCase = true)
    )

    fun handleSubmit() {
        focusManager.clearFocus()
        localError = null
        onClearError()

        val cleanEmail = emailInput.trim()
        val cleanPassword = passwordInput.trim()
        val cleanName = nameInput.trim()

        if (cleanEmail.isEmpty()) {
            localError = "Please enter your email address."
            return
        }
        if (!cleanEmail.contains("@") || !cleanEmail.contains(".")) {
            localError = "Please enter a valid email address (e.g. user@example.com)."
            return
        }
        if (cleanPassword.isEmpty()) {
            localError = "Please enter your password."
            return
        }
        if (cleanPassword.length < 4) {
            localError = "Password must be at least 4 characters."
            return
        }

        if (isRegisterMode && !hasAcceptedPrivacyPolicy) {
            localError = "Please read and agree to the Privacy Policy to create an account."
            return
        }

        if (isRegisterMode) {
            onRegister(cleanEmail, cleanPassword, cleanName)
        } else {
            onLogin(cleanEmail, cleanPassword)
        }
    }

    // Owner's Full App Redesign token system (dark + light aware).
    val T = focivoTokens()
    val isDark = T.isDark
    val authBg = T.bg
    val authLime = T.accent
    val textColorPrimary = T.text
    val textColorSecondary = T.muted
    val fieldBgColor = T.surface
    val fieldBorderColor = T.line

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(authBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .testTag("auth_screen")
    ) {
        // v1.2: soft lime glow at the top — continuity with the splash animation.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val glowCenter = Offset(size.width / 2f, size.height * 0.28f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(authLime.copy(alpha = 0.12f), Color.Transparent),
                    center = glowCenter,
                    radius = size.width * 0.85f
                ),
                radius = size.width * 0.85f,
                center = glowCenter
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // v1.2: Focivo logo — the same mark shown in the splash animation.
            Image(
                painter = painterResource(id = R.drawable.focivo_logo),
                contentDescription = "Focivo logo",
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(84.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .testTag("auth_logo")
            )

            // Headline block
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Focivo",
                    style = TextStyle(
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = if (isDark) T.accent else T.accentText,
                        letterSpacing = 0.5.sp
                    ),
                    modifier = Modifier.testTag("focusly_wordmark")
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = if (isRegisterMode) "Start your focus" else "Welcome back",
                    style = TextStyle(
                        fontFamily = SerifItalicFontFamily,
                        fontStyle = FontStyle.Italic,
                        fontWeight = FontWeight.Normal,
                        fontSize = 32.sp,
                        lineHeight = 38.sp,
                        color = T.text,
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier.testTag("auth_headline")
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = if (isRegisterMode)
                        "Enter your details to create a new account and begin deep focus."
                    else
                        "Enter your registered email and password to log in.",
                    style = TextStyle(
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Normal,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = textColorSecondary,
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .testTag("auth_subtitle")
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Mode Selector Tabs: [ Sign In ] | [ Create Account ]
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 2.dp,
                        shape = CircleShape,
                        ambientColor = Color(0x0A000000),
                        spotColor = Color(0x0E000000)
                    )
                    .testTag("auth_mode_tabs"),
                shape = CircleShape,
                color = fieldBgColor,
                border = androidx.compose.foundation.BorderStroke(1.dp, fieldBorderColor)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Sign In Tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(CircleShape)
                            .background(
                                if (!isRegisterMode) T.accent else Color.Transparent
                            )
                            .clickable {
                                if (isRegisterMode) {
                                    isRegisterMode = false
                                    localError = null
                                    onClearError()
                                }
                            }
                            .padding(vertical = 12.dp)
                            .testTag("tab_sign_in"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Sign In",
                            style = TextStyle(
                                fontFamily = PoppinsFontFamily,
                                fontSize = 14.sp,
                                fontWeight = if (!isRegisterMode) FontWeight.Bold else FontWeight.Medium,
                                color = if (!isRegisterMode) T.onAccent else textColorSecondary
                            )
                        )
                    }

                    // Create Account Tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(CircleShape)
                            .background(
                                if (isRegisterMode) T.accent else Color.Transparent
                            )
                            .clickable {
                                if (!isRegisterMode) {
                                    isRegisterMode = true
                                    localError = null
                                    onClearError()
                                }
                            }
                            .padding(vertical = 12.dp)
                            .testTag("tab_create_account"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Create Account",
                            style = TextStyle(
                                fontFamily = PoppinsFontFamily,
                                fontSize = 14.sp,
                                fontWeight = if (isRegisterMode) FontWeight.Bold else FontWeight.Medium,
                                color = if (isRegisterMode) T.onAccent else textColorSecondary
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Divider
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(fieldBorderColor)
                )
                Text(
                    text = if (isRegisterMode) "or sign up with email" else "or sign in with email",
                    style = TextStyle(
                        fontFamily = PoppinsFontFamily,
                        fontSize = 12.5.sp,
                        color = textColorSecondary,
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier.padding(horizontal = 14.dp)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(fieldBorderColor)
                )
            }

            // Verification / Status Banner
            if (!verificationMessage.isNullOrBlank()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp)
                        .testTag("auth_verification_card"),
                    shape = RoundedCornerShape(16.dp),
                    color = T.softAccent,
                    border = androidx.compose.foundation.BorderStroke(1.dp, T.accent)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = verificationMessage,
                            style = TextStyle(
                                fontFamily = PoppinsFontFamily,
                                color = T.accentText,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Error Banner with One-Tap Action Suggestion
            if (activeError != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp)
                        .testTag("auth_error_card"),
                    shape = RoundedCornerShape(16.dp),
                    color = T.danger.copy(alpha = 0.10f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, T.danger.copy(alpha = 0.25f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = activeError,
                                style = TextStyle(
                                    fontFamily = PoppinsFontFamily,
                                    color = T.danger,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    localError = null
                                    onClearError()
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear error",
                                    tint = T.danger,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        // Smart direct action when account doesn't exist yet
                        if (isAccountNotFoundError && !isRegisterMode) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    isRegisterMode = true
                                    localError = null
                                    onClearError()
                                },
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = T.accent,
                                    contentColor = T.onAccent
                                ),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("action_create_account_suggested")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Create New Account with this Email",
                                    fontSize = 13.sp,
                                    fontFamily = PoppinsFontFamily,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Smart direct action when account already exists
                        if (isAccountAlreadyExistsError && isRegisterMode) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    isRegisterMode = false
                                    localError = null
                                    onClearError()
                                },
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = T.accent,
                                    contentColor = T.onAccent
                                ),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("action_sign_in_suggested")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Sign In with this Account",
                                    fontSize = 13.sp,
                                    fontFamily = PoppinsFontFamily,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Input Form Fields
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Name field (if Create Account mode)
                AnimatedVisibility(
                    visible = isRegisterMode,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Full name",
                            style = TextStyle(
                                fontFamily = PoppinsFontFamily,
                                fontSize = 13.sp,
                                color = textColorPrimary,
                                fontWeight = FontWeight.SemiBold
                            ),
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = {
                                nameInput = it
                                localError = null
                                onClearError()
                            },
                            textStyle = TextStyle(
                                fontFamily = PoppinsFontFamily,
                                color = textColorPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            placeholder = {
                                Text("Enter your full name", color = T.muted, fontFamily = PoppinsFontFamily, fontSize = 14.sp)
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "Name",
                                    tint = T.accent,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            singleLine = true,
                            shape = CircleShape,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = fieldBgColor,
                                unfocusedContainerColor = fieldBgColor,
                                focusedTextColor = textColorPrimary,
                                unfocusedTextColor = textColorPrimary,
                                cursorColor = T.accent,
                                focusedBorderColor = T.accent,
                                unfocusedBorderColor = fieldBorderColor
                            ),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { focusManager.moveFocus(FocusDirection.Down) }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("name_input")
                        )
                    }
                }

                // Email address field
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Email address",
                        style = TextStyle(
                            fontFamily = PoppinsFontFamily,
                            fontSize = 13.sp,
                            color = textColorPrimary,
                            fontWeight = FontWeight.SemiBold
                        ),
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    OutlinedTextField(
                        value = emailInput,
                        onValueChange = {
                            emailInput = it
                            localError = null
                            onClearError()
                        },
                        textStyle = TextStyle(
                            fontFamily = PoppinsFontFamily,
                            color = textColorPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        placeholder = {
                            Text("name@example.com", color = T.muted, fontFamily = PoppinsFontFamily, fontSize = 14.sp)
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = "Email",
                                tint = T.accent,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = {
                            if (emailInput.isNotEmpty()) {
                                IconButton(
                                    onClick = { emailInput = "" },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear email",
                                        tint = textColorSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = CircleShape,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = fieldBgColor,
                            unfocusedContainerColor = fieldBgColor,
                            focusedTextColor = textColorPrimary,
                            unfocusedTextColor = textColorPrimary,
                            cursorColor = T.accent,
                            focusedBorderColor = T.accent,
                            unfocusedBorderColor = fieldBorderColor
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("email_input")
                    )
                }

                // Password field
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Password",
                        style = TextStyle(
                            fontFamily = PoppinsFontFamily,
                            fontSize = 13.sp,
                            color = textColorPrimary,
                            fontWeight = FontWeight.SemiBold
                        ),
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = {
                            passwordInput = it
                            localError = null
                            onClearError()
                        },
                        textStyle = TextStyle(
                            fontFamily = PoppinsFontFamily,
                            color = textColorPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        placeholder = {
                            Text(
                                if (isRegisterMode) "Choose a password (min. 4 chars)" else "Enter your password",
                                color = T.muted,
                                fontFamily = PoppinsFontFamily,
                                fontSize = 14.sp
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Password",
                                tint = T.accent,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(
                                    imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle password visibility",
                                    tint = textColorSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        shape = CircleShape,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = fieldBgColor,
                            unfocusedContainerColor = fieldBgColor,
                            focusedTextColor = textColorPrimary,
                            unfocusedTextColor = textColorPrimary,
                            cursorColor = T.accent,
                            focusedBorderColor = T.accent,
                            unfocusedBorderColor = fieldBorderColor
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { handleSubmit() }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("password_input")
                    )
                }

                // v1.4.0-round2: Forgot password (sign-in mode only)
                AnimatedVisibility(
                    visible = !isRegisterMode,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = { showForgotPasswordDialog = true },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                            modifier = Modifier.testTag("forgot_password_link")
                        ) {
                            Text(
                                text = "Forgot password?",
                                style = TextStyle(
                                    fontFamily = PoppinsFontFamily,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isDark) T.accent else T.accentText
                                )
                            )
                        }
                    }
                }

                // Privacy Policy Consent Checkbox (Registration Mode Only)
                AnimatedVisibility(
                    visible = isRegisterMode,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = hasAcceptedPrivacyPolicy,
                            onCheckedChange = {
                                hasAcceptedPrivacyPolicy = it
                                if (it) {
                                    localError = null
                                    onClearError()
                                }
                            },
                            colors = CheckboxDefaults.colors(
                                checkedColor = T.accent,
                                uncheckedColor = textColorSecondary,
                                checkmarkColor = T.onAccent
                            ),
                            modifier = Modifier.testTag("privacy_policy_checkbox")
                        )

                        val annotatedString = buildAnnotatedString {
                            withStyle(style = SpanStyle(color = textColorPrimary, fontFamily = PoppinsFontFamily, fontSize = 13.sp)) {
                                append("I have read and agree to the ")
                            }
                            pushStringAnnotation(tag = "PRIVACY_POLICY", annotation = "open")
                            withStyle(
                                style = SpanStyle(
                                    color = if (isDark) T.accent else T.accentText,
                                    fontFamily = PoppinsFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            ) {
                                append("Privacy Policy")
                            }
                            pop()
                        }

                        ClickableText(
                            text = annotatedString,
                            onClick = { offset ->
                                annotatedString.getStringAnnotations(tag = "PRIVACY_POLICY", start = offset, end = offset)
                                    .firstOrNull()?.let {
                                        showPrivacyPolicySheet = true
                                    }
                            },
                            modifier = Modifier.testTag("privacy_policy_link")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Primary CTA Button (v1.4.0-round2 premium button system)
            PremiumButton(
                text = if (isLoading) "Please wait..." else if (isRegisterMode) "Create Account" else "Sign In",
                onClick = { handleSubmit() },
                enabled = !isLoading && (!isRegisterMode || hasAcceptedPrivacyPolicy),
                modifier = Modifier.fillMaxWidth(),
                testTag = "btn_primary"
            )

            Spacer(modifier = Modifier.height(16.dp))

            // v1.2: privacy consent line (Create Account mode) — opens the live page.
            AnimatedVisibility(
                visible = isRegisterMode,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                val consentLink = buildAnnotatedString {
                    withStyle(
                        style = SpanStyle(
                            color = textColorSecondary,
                            fontFamily = PoppinsFontFamily,
                            fontSize = 12.5.sp
                        )
                    ) {
                        append("By creating an account you agree to our ")
                    }
                    pushStringAnnotation(tag = "PRIVACY_PAGE", annotation = PRIVACY_POLICY_URL)
                    withStyle(
                        style = SpanStyle(
                            color = authLime,
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp
                        )
                    ) {
                        append("Privacy Policy")
                    }
                    pop()
                }
                ClickableText(
                    text = consentLink,
                    style = TextStyle(textAlign = TextAlign.Center),
                    onClick = { offset ->
                        consentLink.getStringAnnotations(tag = "PRIVACY_PAGE", start = offset, end = offset)
                            .firstOrNull()?.let { openUrlInBrowser(context, it.item) }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .testTag("privacy_policy_page_link")
                )
            }

            // Footer Switcher Links
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (isRegisterMode) "Already have an account? " else "Don't have an account? ",
                    style = TextStyle(
                        fontFamily = PoppinsFontFamily,
                        fontSize = 14.sp,
                        color = textColorSecondary
                    )
                )
                TextButton(
                    onClick = {
                        isRegisterMode = !isRegisterMode
                        localError = null
                        onClearError()
                    },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                    modifier = Modifier.testTag("toggle_register_mode")
                ) {
                    Text(
                        text = if (isRegisterMode) "Sign In" else "Create Account",
                        style = TextStyle(
                            fontFamily = PoppinsFontFamily,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) T.accent else T.accentText
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Guest option
            TextButton(
                onClick = { onGuestSignIn() },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("guest_continue_button")
            ) {
                Text(
                    text = "Continue as Guest",
                    style = TextStyle(
                        fontFamily = PoppinsFontFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = textColorSecondary,
                        textAlign = TextAlign.Center
                    )
                )
            }

            // v1.2: privacy page link (both modes) — opens the live page.
            TextButton(
                onClick = { openUrlInBrowser(context, PRIVACY_POLICY_URL) },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.testTag("privacy_policy_page_link_footer")
            ) {
                Text(
                    text = "Privacy Policy",
                    style = TextStyle(
                        fontFamily = PoppinsFontFamily,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = textColorSecondary
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Privacy Policy Modal Sheet
        if (showPrivacyPolicySheet) {
            PrivacyPolicySheet(
                onDismiss = { showPrivacyPolicySheet = false }
            )
        }

        // Google Account Selection Dialog
        if (showGoogleAccountDialog) {
            GoogleAccountSelectDialog(
                onDismiss = { showGoogleAccountDialog = false },
                onSelectAccount = { name, email ->
                    showGoogleAccountDialog = false
                    onGoogleDirectSignIn(name, email)
                },
                onTriggerCredentialManager = {
                    showGoogleAccountDialog = false
                    onGoogleSignIn(context)
                }
            )
        }

        // v1.4.0-round2: Forgot password dialog
        if (showForgotPasswordDialog) {
            ForgotPasswordDialog(
                initialEmail = emailInput,
                resetState = passwordResetState,
                onDismiss = {
                    showForgotPasswordDialog = false
                    onClearPasswordReset()
                },
                onSend = { email -> onForgotPassword(email) }
            )
        }

        // v1.4.0-round2: Set-new-password sheet after a recovery deep link
        if (pendingPasswordRecovery) {
            SetNewPasswordSheet(
                resetState = passwordResetState,
                onDismiss = { onClearPasswordReset() },
                onSubmit = { newPassword -> onSetNewPassword(newPassword) }
            )
        }
    }
}

/**
 * Geometric Aperture Badge matching reference design:
 * Soft green glowing halo background with vivid lime green aperture blades.
 */
@Composable
fun FocuslyApertureBadge(modifier: Modifier = Modifier) {
    com.example.ui.components.LockZenLogoView(
        modifier = modifier,
        size = 80.dp,
        showBackgroundSquircle = true
    )
}

/**
 * Official Google 'G' icon in 4-color branding.
 */
@Composable
fun GoogleGIcon(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(id = R.drawable.ic_google_logo),
        contentDescription = "Google Logo",
        modifier = modifier
    )
}

/**
 * Google Account Select Dialog styled matching reference design.
 */
@Composable
fun GoogleAccountSelectDialog(
    onDismiss: () -> Unit,
    onSelectAccount: (name: String, email: String) -> Unit,
    onTriggerCredentialManager: () -> Unit
) {
    var googleName by remember { mutableStateOf("") }
    var googleEmail by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf<String?>(null) }

    val isDark = isAppInDarkTheme()
    val dialogBg = focivoTokens().surface
    val dialogFieldBg = focivoTokens().surface2
    val dialogTextColor = focivoTokens().text
    val dialogSecondaryText = focivoTokens().muted
    val dialogBorder = focivoTokens().accent.copy(alpha = 0.2f)

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {},
        containerColor = dialogBg,
        tonalElevation = 8.dp,
        shape = RoundedCornerShape(24.dp),
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GoogleGIcon(modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Google Sign In",
                            style = TextStyle(
                                fontFamily = PoppinsFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = dialogTextColor
                            )
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = dialogSecondaryText,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Sign in using your Google account identity:",
                    style = TextStyle(
                        fontFamily = PoppinsFontFamily,
                        fontSize = 13.sp,
                        color = dialogSecondaryText
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Input for Google Name
                Text(
                    text = "Your Name",
                    style = TextStyle(fontFamily = PoppinsFontFamily, fontSize = 12.sp, color = dialogSecondaryText, fontWeight = FontWeight.Medium)
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = googleName,
                    onValueChange = {
                        googleName = it
                        inputError = null
                    },
                    placeholder = { Text("e.g. Tommy Smith", fontFamily = PoppinsFontFamily, fontSize = 13.5.sp, color = focivoTokens().muted) },
                    singleLine = true,
                    shape = CircleShape,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = focivoTokens().accent,
                        unfocusedBorderColor = dialogBorder,
                        focusedContainerColor = dialogFieldBg,
                        unfocusedContainerColor = dialogFieldBg,
                        focusedTextColor = dialogTextColor,
                        unfocusedTextColor = dialogTextColor
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Input for Google Email
                Text(
                    text = "Google Email",
                    style = TextStyle(fontFamily = PoppinsFontFamily, fontSize = 12.sp, color = dialogSecondaryText, fontWeight = FontWeight.Medium)
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = googleEmail,
                    onValueChange = {
                        googleEmail = it
                        inputError = null
                    },
                    placeholder = { Text("tommy@gmail.com", fontFamily = PoppinsFontFamily, fontSize = 13.5.sp, color = focivoTokens().muted) },
                    singleLine = true,
                    shape = CircleShape,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = focivoTokens().accent,
                        unfocusedBorderColor = dialogBorder,
                        focusedContainerColor = dialogFieldBg,
                        unfocusedContainerColor = dialogFieldBg,
                        focusedTextColor = dialogTextColor,
                        unfocusedTextColor = dialogTextColor
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                if (inputError != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = inputError!!,
                        style = TextStyle(color = focivoTokens().danger, fontSize = 12.sp, fontFamily = PoppinsFontFamily)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Submit Button
                Button(
                    onClick = {
                        if (googleEmail.trim().isEmpty() || !googleEmail.contains("@")) {
                            inputError = "Please enter a valid Google email address."
                            return@Button
                        }
                        val name = if (googleName.trim().isEmpty()) googleEmail.substringBefore("@") else googleName.trim()
                        onSelectAccount(name, googleEmail.trim())
                    },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = focivoTokens().accent, contentColor = MaterialTheme.colorScheme.onPrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text(
                        text = "Sign In with Google Identity",
                        style = TextStyle(fontFamily = PoppinsFontFamily, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Or trigger System Credential Manager
                TextButton(
                    onClick = { onTriggerCredentialManager() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Use Android Credential Manager Prompt",
                        style = TextStyle(fontFamily = PoppinsFontFamily, fontSize = 12.5.sp, color = focivoTokens().accentText, fontWeight = FontWeight.SemiBold)
                    )
                }
            }
        }
    )
}

/**
 * v1.4.0-round2: Forgot-password dialog. Email entry -> Supabase recovery
 * email -> idle/sending/sent/error states, all English.
 */
@Composable
private fun ForgotPasswordDialog(
    initialEmail: String,
    resetState: com.example.ui.viewmodel.FocuslyViewModel.PasswordResetState,
    onDismiss: () -> Unit,
    onSend: (email: String) -> Unit
) {
    var email by remember { mutableStateOf(initialEmail) }
    val isDark = isAppInDarkTheme()
    val textPrimary = focivoTokens().text
    val textSecondary = focivoTokens().muted

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Reset password",
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = textPrimary
            )
        },
        text = {
            Column {
                when (resetState) {
                    is com.example.ui.viewmodel.FocuslyViewModel.PasswordResetState.Sent -> {
                        Text(
                            text = "Recovery email sent. Check your inbox and tap the link, then set a new password here in the app.",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 14.sp,
                            color = textPrimary
                        )
                    }
                    is com.example.ui.viewmodel.FocuslyViewModel.PasswordResetState.Error -> {
                        Text(
                            text = resetState.message,
                            fontFamily = PoppinsFontFamily,
                            fontSize = 14.sp,
                            color = focivoTokens().danger
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    else -> {
                        Text(
                            text = "Enter your account email. We will send you a link to reset your password.",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 14.sp,
                            color = textSecondary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
                if (resetState !is com.example.ui.viewmodel.FocuslyViewModel.PasswordResetState.Sent) {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email", fontFamily = PoppinsFontFamily) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = focivoTokens().accent,
                            cursorColor = focivoTokens().accent
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("forgot_password_email")
                    )
                }
            }
        },
        confirmButton = {
            val sending = resetState is com.example.ui.viewmodel.FocuslyViewModel.PasswordResetState.Sending
            val sent = resetState is com.example.ui.viewmodel.FocuslyViewModel.PasswordResetState.Sent
            if (!sent) {
                PremiumButton(
                    text = if (sending) "Sending..." else "Send link",
                    onClick = { onSend(email.trim()) },
                    enabled = !sending && email.contains("@"),
                    compact = true,
                    modifier = Modifier.testTag("forgot_password_send")
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", fontFamily = PoppinsFontFamily, color = textSecondary)
            }
        }
    )
}

/**
 * v1.4.0-round2: shown after the user taps the recovery link in their email.
 * Sets the new password against the recovery session.
 */
@Composable
private fun SetNewPasswordSheet(
    resetState: com.example.ui.viewmodel.FocuslyViewModel.PasswordResetState,
    onDismiss: () -> Unit,
    onSubmit: (password: String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    val isDark = isAppInDarkTheme()
    val textPrimary = focivoTokens().text
    val textSecondary = focivoTokens().muted
    val updating = resetState is com.example.ui.viewmodel.FocuslyViewModel.PasswordResetState.Updating

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Set a new password",
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = textPrimary
            )
        },
        text = {
            Column {
                Text(
                    text = "Choose a new password for your account (min. 6 characters).",
                    fontFamily = PoppinsFontFamily,
                    fontSize = 14.sp,
                    color = textSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; localError = null },
                    label = { Text("New password", fontFamily = PoppinsFontFamily) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = focivoTokens().accent,
                        cursorColor = focivoTokens().accent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("new_password_input")
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it; localError = null },
                    label = { Text("Confirm password", fontFamily = PoppinsFontFamily) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = focivoTokens().accent,
                        cursorColor = focivoTokens().accent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("new_password_confirm")
                )
                val stateError = (resetState as? com.example.ui.viewmodel.FocuslyViewModel.PasswordResetState.Error)?.message
                val showError = localError ?: stateError
                if (showError != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = showError,
                        fontFamily = PoppinsFontFamily,
                        fontSize = 13.sp,
                        color = focivoTokens().danger
                    )
                }
            }
        },
        confirmButton = {
            PremiumButton(
                text = if (updating) "Saving..." else "Save password",
                onClick = {
                    localError = when {
                        password.length < 6 -> "Password must be at least 6 characters."
                        password != confirm -> "Passwords do not match."
                        else -> null
                    }
                    if (localError == null) onSubmit(password)
                },
                enabled = !updating,
                compact = true,
                modifier = Modifier.testTag("new_password_submit")
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", fontFamily = PoppinsFontFamily, color = textSecondary)
            }
        }
    )
}
