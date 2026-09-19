package com.example.ui.login

import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.TabloDevice
import com.example.ui.components.TvRemoteKeyboard
import com.example.ui.theme.LiveRed
import com.example.ui.theme.TabloTeal
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TvBackground
import com.example.ui.theme.TvBorder
import com.example.ui.theme.TvFocusHighlight
import com.example.ui.theme.TvSurface
import com.example.ui.theme.TvSurfaceElevated

/**
 * Gatekeeper Tablo Account Login Screen.
 * The user must log in with their Tablo Account or connect to their Tablo device
 * before accessing any content (Multiview, TV Guide, Search, or Saved Multiviews).
 */
@Composable
fun TabloLoginScreen(
    isLoggingIn: Boolean,
    loginError: String?,
    discoveredDevices: List<TabloDevice>,
    onLogin: (email: String, pass: String) -> Unit,
    onSelectDevice: (TabloDevice) -> Unit,
    onLocalFallback: () -> Unit,
    modifier: Modifier = Modifier
) {
    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var activeField by remember { mutableIntStateOf(0) } // 0 = email, 1 = password
    var showLocalDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TvBackground)
            .padding(horizontal = 36.dp, vertical = 24.dp)
            .onKeyEvent { event ->
                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    val unicodeChar = event.nativeKeyEvent.unicodeChar
                    when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DEL -> {
                            if (activeField == 0 && emailInput.isNotEmpty()) {
                                emailInput = emailInput.dropLast(1)
                                true
                            } else if (activeField == 1 && passwordInput.isNotEmpty()) {
                                passwordInput = passwordInput.dropLast(1)
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_TAB -> {
                            activeField = if (activeField == 0) 1 else 0
                            true
                        }
                        else -> {
                            if (unicodeChar > 31 && unicodeChar < 127) {
                                val char = unicodeChar.toChar().toString()
                                if (activeField == 0) {
                                    emailInput += char
                                } else {
                                    passwordInput += char
                                }
                                true
                            } else false
                        }
                    }
                } else false
            }
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Column: Branding, Form Inputs & Primary Actions
            Column(
                modifier = Modifier
                    .weight(1.1f)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.Center
            ) {
                // Brand Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(TabloTeal, CircleShape)
                    )
                    Text(
                        text = "TABLO TV",
                        color = TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.5.sp
                    )
                    Box(
                        modifier = Modifier
                            .background(TabloTeal.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                            .border(BorderStroke(1.dp, TabloTeal.copy(alpha = 0.5f)), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "4TH GEN",
                            color = TabloTeal,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Sign in to Your Tablo Account",
                    color = TextPrimary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold
                )

                Text(
                    text = "Authenticate with your Tablo credentials to access your OTA & streaming channels, program guide, and live multiview streams.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
                )

                // Error Banner
                AnimatedVisibility(visible = !loginError.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .background(LiveRed.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .border(BorderStroke(1.dp, LiveRed), RoundedCornerShape(8.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = loginError ?: "",
                            color = Color(0xFFFF8B8B),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Multiple Devices Picker (if account has > 1 Tablo)
                if (discoveredDevices.size > 1) {
                    Text(
                        text = "SELECT YOUR TABLO DEVICE:",
                        color = TabloTeal,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(discoveredDevices) { device ->
                            DeviceSelectionCard(
                                device = device,
                                onClick = { onSelectDevice(device) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                } else {
                    // Email Field
                    TvAuthInputField(
                        label = "Tablo Account Email",
                        value = emailInput,
                        placeholder = "you@example.com",
                        icon = Icons.Default.Email,
                        isSelected = activeField == 0,
                        onClick = { activeField = 0 },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Password Field
                    TvAuthInputField(
                        label = "Password",
                        value = "•".repeat(passwordInput.length),
                        placeholder = "Enter your password",
                        icon = Icons.Default.Lock,
                        isSelected = activeField == 1,
                        onClick = { activeField = 1 },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Sign In Primary Button
                        Button(
                            onClick = {
                                if (emailInput.isNotBlank() && passwordInput.isNotBlank()) {
                                    onLogin(emailInput, passwordInput)
                                }
                            },
                            enabled = !isLoggingIn,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = TabloTeal,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .height(46.dp)
                                .weight(1.3f)
                        ) {
                            if (isLoggingIn) {
                                CircularProgressIndicator(
                                    color = Color.Black,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Authenticating...", fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Sign In with Tablo", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }

                        // Local Auto-Detect / Direct IP fallback
                        OutlinedButton(
                            onClick = onLocalFallback,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = TextSecondary
                            ),
                            border = BorderStroke(1.dp, TvBorder),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .height(46.dp)
                                .weight(1f)
                        ) {
                            Icon(Icons.Default.Router, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Local / Direct IP", fontSize = 12.sp)
                        }
                    }
                }
            }

            // Right Column: On-screen Keyboard for TV Remote Navigation
            Column(
                modifier = Modifier
                    .weight(0.9f)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (activeField == 0) "Editing Email" else "Editing Password",
                    color = TabloTeal,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                TvRemoteKeyboard(
                    onKeyPress = { key ->
                        if (activeField == 0) {
                            emailInput += key
                        } else {
                            passwordInput += key
                        }
                    },
                    onBackspace = {
                        if (activeField == 0 && emailInput.isNotEmpty()) {
                            emailInput = emailInput.dropLast(1)
                        } else if (activeField == 1 && passwordInput.isNotEmpty()) {
                            passwordInput = passwordInput.dropLast(1)
                        }
                    },
                    onClear = {
                        if (activeField == 0) emailInput = "" else passwordInput = ""
                    },
                    onDone = {
                        if (activeField == 0) {
                            activeField = 1
                        } else if (emailInput.isNotBlank() && passwordInput.isNotBlank()) {
                            onLogin(emailInput, passwordInput)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun TvAuthInputField(
    label: String,
    value: String,
    placeholder: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val active = isSelected || isFocused
    val border = if (active) {
        BorderStroke(2.dp, TvFocusHighlight)
    } else {
        BorderStroke(1.dp, TvBorder)
    }
    val background = if (active) TvSurfaceElevated else TvSurface

    Column(modifier = modifier) {
        Text(
            text = label.uppercase(),
            color = if (active) TabloTeal else TextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(background)
                .border(border, RoundedCornerShape(8.dp))
                .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
                .focusable(interactionSource = interactionSource)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (active) TabloTeal else TextMuted,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    color = TextMuted.copy(alpha = 0.6f),
                    fontSize = 14.sp
                )
            } else {
                Text(
                    text = value,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun DeviceSelectionCard(
    device: TabloDevice,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val border = if (isFocused) BorderStroke(2.dp, TvFocusHighlight) else BorderStroke(1.dp, TvBorder)
    val background = if (isFocused) Color(0x3300D2B4) else TvSurfaceElevated

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .border(border, RoundedCornerShape(8.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Router,
                contentDescription = null,
                tint = if (isFocused) TabloTeal else TextSecondary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = device.name,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${device.model} • ${device.host}",
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }
        }

        Icon(
            imageVector = Icons.Default.ArrowForward,
            contentDescription = null,
            tint = if (isFocused) TabloTeal else TextMuted,
            modifier = Modifier.size(16.dp)
        )
    }
}
