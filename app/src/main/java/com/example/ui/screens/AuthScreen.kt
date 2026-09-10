package com.example.ui.screens

import android.content.Context
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.components.AuroraBackground
import com.example.ui.components.GlassCard
import com.example.ui.theme.MutedBorderLight
import com.example.ui.theme.MutedTextLight
import com.example.ui.theme.NearBlack
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.PureWhite
import com.example.ui.theme.RegainBgTop
import com.example.ui.theme.RegainLimeContainer
import com.example.ui.theme.RegainLimeDeepText
import com.example.ui.theme.RegainLimeLight
import com.example.ui.theme.RegainLimePrimary
import com.example.ui.theme.SecondaryTextLight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    isLoading: Boolean,
    errorMessage: String?,
    verificationMessage: String? = null,
    onLogin: (email: String, pin: String) -> Unit,
    onRegister: (email: String, pin: String, fullName: String) -> Unit,
    onGoogleSignIn: (context: Context) -> Unit = {},
    onGoogleDirectSignIn: (name: String, email: String) -> Unit = { _, _ -> },
    onGuestSignIn: () -> Unit = {},
    onClearError: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    var isRegisterMode by remember { mutableStateOf(false) }
    var nameInput by remember { mutableStateOf("") }
    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var showGoogleAccountDialog by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }
    var hasAcceptedPrivacyPolicy by remember { mutableStateOf(false) }
    var showPrivacyPolicySheet by remember { mutableStateOf(false) }

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

    val isDark = androidx.compose.foundation.isSystemInDarkTheme() || MaterialTheme.colorScheme.background != RegainBgTop
    val textColorPrimary = if (isDark) PureWhite else NearBlack
    val textColorSecondary = if (isDark) Color(0xFFCBD5E1) else SecondaryTextLight
    val fieldBgColor = if (isDark) MaterialTheme.colorScheme.surface else PureWhite
    val fieldBorderColor = if (isDark) MaterialTheme.colorScheme.outline else MutedBorderLight

    AuroraBackground(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .testTag("auth_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Aperture / Logo Badge
            FocuslyApertureBadge(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .testTag("aperture_badge")
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
                        fontSize = 16.sp,
                        color = if (isDark) RegainLimeLight else RegainLimeDeepText,
                        letterSpacing = 0.5.sp
                    ),
                    modifier = Modifier.testTag("focusly_wordmark")
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = if (isRegisterMode) "Create an account" else "Sign in to Focivo",
                    style = TextStyle(
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp,
                        lineHeight = 34.sp,
                        color = textColorPrimary,
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
                                if (!isRegisterMode) RegainLimePrimary else Color.Transparent
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
                                color = if (!isRegisterMode) PureWhite else textColorSecondary
                            )
                        )
                    }

                    // Create Account Tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(CircleShape)
                            .background(
                                if (isRegisterMode) RegainLimePrimary else Color.Transparent
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
                                color = if (isRegisterMode) PureWhite else textColorSecondary
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Google Button
            Button(
                onClick = {
                    localError = null
                    onClearError()
                    showGoogleAccountDialog = true
                },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = fieldBgColor,
                    contentColor = textColorPrimary
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, fieldBorderColor),
                contentPadding = PaddingValues(horizontal = 20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("btn_google")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = RegainLimePrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Authenticating...",
                            style = TextStyle(
                                fontFamily = PoppinsFontFamily,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = textColorPrimary
                            )
                        )
                    } else {
                        GoogleGIcon(modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Continue with Google",
                            style = TextStyle(
                                fontFamily = PoppinsFontFamily,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = textColorPrimary
                            )
                        )
                    }
                }
            }

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
                    color = RegainLimeContainer,
                    border = androidx.compose.foundation.BorderStroke(1.dp, RegainLimePrimary)
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
                                color = RegainLimeDeepText,
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
                    color = Color(0xFFFFF0F0),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFCDCD))
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
                                    color = Color(0xFFD32F2F),
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
                                    tint = Color(0xFFD32F2F),
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
                                    containerColor = RegainLimePrimary,
                                    contentColor = PureWhite
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
                                    containerColor = RegainLimePrimary,
                                    contentColor = PureWhite
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
                                Text("Enter your full name", color = MutedTextLight, fontFamily = PoppinsFontFamily, fontSize = 14.sp)
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "Name",
                                    tint = RegainLimePrimary,
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
                                cursorColor = RegainLimePrimary,
                                focusedBorderColor = RegainLimePrimary,
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
                            Text("name@example.com", color = MutedTextLight, fontFamily = PoppinsFontFamily, fontSize = 14.sp)
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = "Email",
                                tint = RegainLimePrimary,
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
                            cursorColor = RegainLimePrimary,
                            focusedBorderColor = RegainLimePrimary,
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
                                color = MutedTextLight,
                                fontFamily = PoppinsFontFamily,
                                fontSize = 14.sp
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Password",
                                tint = RegainLimePrimary,
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
                            cursorColor = RegainLimePrimary,
                            focusedBorderColor = RegainLimePrimary,
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
                                checkedColor = RegainLimePrimary,
                                uncheckedColor = textColorSecondary,
                                checkmarkColor = PureWhite
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
                                    color = if (isDark) RegainLimeLight else RegainLimeDeepText,
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

            // Primary CTA Button (Solid Lime Green Fill with White Bold Text)
            Button(
                onClick = { handleSubmit() },
                enabled = !isLoading && (!isRegisterMode || hasAcceptedPrivacyPolicy),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = RegainLimePrimary,
                    contentColor = PureWhite,
                    disabledContainerColor = RegainLimeContainer
                ),
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .shadow(
                        elevation = 6.dp,
                        shape = CircleShape,
                        ambientColor = Color(0x3B8CE000),
                        spotColor = Color(0x3B8CE000)
                    )
                    .testTag("btn_primary")
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = PureWhite,
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Text(
                            text = if (isRegisterMode) "Create Account" else "Sign In",
                            style = TextStyle(
                                fontFamily = PoppinsFontFamily,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = PureWhite
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

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
                            color = if (isDark) RegainLimeLight else RegainLimeDeepText
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

            Spacer(modifier = Modifier.height(8.dp))

            // Sync Status Indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .padding(top = 4.dp, bottom = 8.dp)
                    .testTag("sync_status_badge")
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(RegainLimePrimary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(RegainLimePrimary)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Cloud Sync & Local Backup Active",
                    style = TextStyle(
                        fontFamily = PoppinsFontFamily,
                        fontSize = 12.sp,
                        color = textColorSecondary,
                        fontWeight = FontWeight.Normal
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

    val isDark = androidx.compose.foundation.isSystemInDarkTheme() || MaterialTheme.colorScheme.background != RegainBgTop
    val dialogBg = if (isDark) MaterialTheme.colorScheme.surfaceVariant else PureWhite
    val dialogFieldBg = if (isDark) MaterialTheme.colorScheme.surface else PureWhite
    val dialogTextColor = if (isDark) PureWhite else NearBlack
    val dialogSecondaryText = if (isDark) Color(0xFFCBD5E1) else SecondaryTextLight
    val dialogBorder = if (isDark) MaterialTheme.colorScheme.outline else MutedBorderLight

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
                    placeholder = { Text("e.g. Tommy Smith", fontFamily = PoppinsFontFamily, fontSize = 13.5.sp, color = MutedTextLight) },
                    singleLine = true,
                    shape = CircleShape,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RegainLimePrimary,
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
                    placeholder = { Text("tommy@gmail.com", fontFamily = PoppinsFontFamily, fontSize = 13.5.sp, color = MutedTextLight) },
                    singleLine = true,
                    shape = CircleShape,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RegainLimePrimary,
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
                        style = TextStyle(color = Color(0xFFD32F2F), fontSize = 12.sp, fontFamily = PoppinsFontFamily)
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
                    colors = ButtonDefaults.buttonColors(containerColor = RegainLimePrimary, contentColor = PureWhite),
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
                        style = TextStyle(fontFamily = PoppinsFontFamily, fontSize = 12.5.sp, color = RegainLimeDeepText, fontWeight = FontWeight.SemiBold)
                    )
                }
            }
        }
    )
}
