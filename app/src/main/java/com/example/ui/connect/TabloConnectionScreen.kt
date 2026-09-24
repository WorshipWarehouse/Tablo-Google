package com.example.ui.connect

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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalFocusManager
import com.example.ui.util.safeRequest
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.TabloDevice
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

enum class ConnectMode(val label: String) {
    AUTO_DISCOVER("Network Auto-Detect"),
    ACCOUNT_LOGIN("Tablo Account Login"),
    DIRECT_IP("Direct IP")
}

@Composable
fun TabloConnectionScreen(
    currentDevice: TabloDevice?,
    discoveredDevices: List<TabloDevice>,
    isScanning: Boolean,
    isConnecting: Boolean,
    isLoggingIn: Boolean = false,
    connectionError: String? = null,
    loginError: String? = null,
    onStartScan: () -> Unit,
    onSelectDevice: (TabloDevice) -> Unit,
    onManualConnect: (String, Int) -> Unit,
    onLoginAccount: (String, String) -> Unit,
    onDisconnect: () -> Unit,
    onBack: () -> Unit,
    onRequestTopNav: () -> Unit = {},
    onNavigateLeftPage: () -> Unit = {},
    onNavigateRightPage: () -> Unit = {},
    focusRequester: androidx.compose.ui.focus.FocusRequester? = null,
    modifier: Modifier = Modifier
) {
    var selectedMode by remember { mutableStateOf(ConnectMode.AUTO_DISCOVER) }
    val tabFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }

    LaunchedEffect(focusRequester) {
        if (focusRequester != null) {
            tabFocusRequester.safeRequest()
        }
    }

    // Account login inputs
    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var activeInputField by remember { mutableStateOf(0) } // 0 = email, 1 = password

    // Direct IP inputs
    var ipInput by remember { mutableStateOf("192.168.1.") }
    var portInput by remember { mutableIntStateOf(8885) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TvBackground)
            .padding(horizontal = 28.dp, vertical = 16.dp)
            .onKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_BACK -> {
                            onBack()
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            onRequestTopNav()
                            true
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header Row with Title and Mode Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "TABLO CONNECT & LOGIN",
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Connect via local network discovery, Tablo Account login, or direct IP address.",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }

                // Mode Selector Tabs
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ConnectMode.values().forEach { mode ->
                        val isSelected = selectedMode == mode
                        val interactionSource = remember { MutableInteractionSource() }
                        val isFocused by interactionSource.collectIsFocusedAsState()

                        val bg = when {
                            isFocused -> TabloTeal
                            isSelected -> TvSurfaceElevated
                            else -> TvSurface
                        }
                        val textCol = when {
                            isFocused -> Color.Black
                            isSelected -> TabloTeal
                            else -> TextSecondary
                        }
                        val border = when {
                            isFocused -> BorderStroke(2.dp, TvFocusHighlight)
                            isSelected -> BorderStroke(1.dp, TabloTeal.copy(alpha = 0.6f))
                            else -> BorderStroke(1.dp, TvBorder)
                        }

                        var tabMod = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(bg)
                            .border(border, RoundedCornerShape(8.dp))
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = { selectedMode = mode }
                            )
                            .focusable(interactionSource = interactionSource)
                            .onKeyEvent { keyEvent ->
                                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                    if (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                                        onRequestTopNav()
                                        true
                                    } else false
                                } else false
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp)

                        if (mode == ConnectMode.AUTO_DISCOVER) {
                            tabMod = tabMod.focusRequester(tabFocusRequester)
                        }

                        Box(
                            modifier = tabMod,
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = mode.label,
                                color = textCol,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Main Two-Column Layout
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Left Column: Interactive Mode Panel
                Column(
                    modifier = Modifier
                        .weight(1.15f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when (selectedMode) {
                        ConnectMode.AUTO_DISCOVER -> {
                            AutoDiscoverPanel(
                                isScanning = isScanning,
                                isConnecting = isConnecting,
                                onStartScan = onStartScan
                            )
                        }
                        ConnectMode.ACCOUNT_LOGIN -> {
                            AccountLoginPanel(
                                email = emailInput,
                                password = passwordInput,
                                activeField = activeInputField,
                                isLoggingIn = isLoggingIn,
                                onEmailChange = { emailInput = it },
                                onPasswordChange = { passwordInput = it },
                                onFieldSelect = { activeInputField = it },
                                onSubmit = { onLoginAccount(emailInput, passwordInput) }
                            )
                        }
                        ConnectMode.DIRECT_IP -> {
                            DirectIpPanel(
                                ip = ipInput,
                                port = portInput,
                                isConnecting = isConnecting,
                                onIpChange = { ipInput = it },
                                onPortChange = { portInput = it },
                                onConnect = { onManualConnect(ipInput, portInput) }
                            )
                        }
                    }

                    // Error Message Banner if present
                    val activeError = loginError ?: connectionError
                    if (activeError != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x28EF4444))
                                .border(BorderStroke(1.dp, Color(0x66EF4444)), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = activeError,
                                color = TextPrimary,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    // Connected Device Status Summary (if currently connected)
                    if (currentDevice != null) {
                        ConnectedDeviceCard(
                            device = currentDevice,
                            onDisconnect = onDisconnect,
                            onBackToLive = onBack
                        )
                    }
                }

                // Right Column: Discovered Devices List
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "AVAILABLE TABLO DEVICES",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        if (discoveredDevices.isNotEmpty()) {
                            Text(
                                text = "${discoveredDevices.size} found",
                                color = TabloTeal,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (discoveredDevices.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(TvSurface)
                                .border(BorderStroke(1.dp, TvBorder), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Router,
                                    contentDescription = null,
                                    tint = TextMuted,
                                    modifier = Modifier.size(36.dp)
                                )
                                Text(
                                    text = if (isScanning) "Searching for Tablos..." else "No Tablo Devices Found",
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (isScanning) {
                                        "Querying Tablo Association server and UDP broadcast on port 8881..."
                                    } else {
                                        "Select 'Network Auto-Detect' and press Find Tablo, sign in with your Tablo account, or connect via Direct IP."
                                    },
                                    color = TextMuted,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(discoveredDevices) { device ->
                                DiscoveredDeviceCard(
                                    device = device,
                                    isCurrentlyConnected = currentDevice?.serverId == device.serverId ||
                                            (currentDevice?.host == device.host && currentDevice.port == device.port),
                                    isConnecting = isConnecting,
                                    onSelect = { onSelectDevice(device) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AutoDiscoverPanel(
    isScanning: Boolean,
    isConnecting: Boolean,
    onStartScan: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(TvSurface)
            .border(BorderStroke(1.dp, TvBorder), RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.Router, contentDescription = null, tint = TabloTeal, modifier = Modifier.size(24.dp))
            Column {
                Text("Local Network Auto-Discovery", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Standard Tablo discovery per API docs (UDP 8881/8882 & getipinfo). No password required.",
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }
        }

        Text(
            text = "Your Fire TV discovers Tablo DVRs on the local Wi-Fi/Ethernet network. Tablo 2nd/3rd Gen, DUAL, and QUAD units do not require a password for direct local streaming.",
            color = TextSecondary,
            fontSize = 12.sp,
            lineHeight = 16.sp
        )

        Button(
            onClick = onStartScan,
            enabled = !isScanning && !isConnecting,
            colors = ButtonDefaults.buttonColors(containerColor = TabloTeal),
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
        ) {
            if (isScanning) {
                CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Scanning Network...", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            } else {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.Black)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Scan for Tablo Devices", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun AccountLoginPanel(
    email: String,
    password: String,
    activeField: Int,
    isLoggingIn: Boolean,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onFieldSelect: (Int) -> Unit,
    onSubmit: () -> Unit
) {
    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(TvSurface)
            .border(BorderStroke(1.dp, TvBorder), RoundedCornerShape(12.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.AccountCircle, contentDescription = null, tint = TabloTeal, modifier = Modifier.size(22.dp))
            Text("Tablo Account Cloud Sign-In", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }

        // Native Fire TV Email Field
        OutlinedTextField(
            value = email,
            onValueChange = onEmailChange,
            label = { Text("Tablo Account Email", fontSize = 12.sp) },
            placeholder = { Text("you@example.com", color = TextMuted.copy(alpha = 0.6f)) },
            leadingIcon = {
                Icon(Icons.Default.Email, contentDescription = null, tint = TabloTeal)
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Down) }
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedBorderColor = TvFocusHighlight,
                unfocusedBorderColor = TvBorder,
                focusedContainerColor = TvSurfaceElevated,
                unfocusedContainerColor = TvBackground,
                focusedLabelColor = TabloTeal,
                unfocusedLabelColor = TextMuted,
                cursorColor = TabloTeal
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        )

        // Native Fire TV Password Field
        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = { Text("Password", fontSize = 12.sp) },
            placeholder = { Text("Enter password", color = TextMuted.copy(alpha = 0.6f)) },
            leadingIcon = {
                Icon(Icons.Default.Lock, contentDescription = null, tint = TabloTeal)
            },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                    if (email.isNotBlank() && password.isNotBlank()) onSubmit()
                }
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedBorderColor = TvFocusHighlight,
                unfocusedBorderColor = TvBorder,
                focusedContainerColor = TvSurfaceElevated,
                unfocusedContainerColor = TvBackground,
                focusedLabelColor = TabloTeal,
                unfocusedLabelColor = TextMuted,
                cursorColor = TabloTeal
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        )

        // Sign In Button
        Button(
            onClick = {
                focusManager.clearFocus()
                onSubmit()
            },
            enabled = !isLoggingIn && email.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = TabloTeal),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            if (isLoggingIn) {
                CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Signing In...", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            } else {
                Text("Sign In & Find Tablo", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun DirectIpPanel(
    ip: String,
    port: Int,
    isConnecting: Boolean,
    onIpChange: (String) -> Unit,
    onPortChange: (Int) -> Unit,
    onConnect: () -> Unit
) {
    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(TvSurface)
            .border(BorderStroke(1.dp, TvBorder), RoundedCornerShape(12.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.SettingsEthernet, contentDescription = null, tint = TabloTeal, modifier = Modifier.size(22.dp))
            Text("Direct IP & Port Connection", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }

        // Native Fire TV IP Input Field
        OutlinedTextField(
            value = ip,
            onValueChange = onIpChange,
            label = { Text("Tablo IP Address", fontSize = 12.sp) },
            placeholder = { Text("e.g. 192.168.1.100", color = TextMuted.copy(alpha = 0.6f)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                    if (ip.isNotBlank()) onConnect()
                }
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedBorderColor = TvFocusHighlight,
                unfocusedBorderColor = TvBorder,
                focusedContainerColor = TvSurfaceElevated,
                unfocusedContainerColor = TvBackground,
                focusedLabelColor = TabloTeal,
                unfocusedLabelColor = TextMuted,
                cursorColor = TabloTeal
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        )

        // Port Selection Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Port:", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            listOf(8885, 8881).forEach { p ->
                val isSelected = port == p
                Button(
                    onClick = { onPortChange(p) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSelected) TabloTeal else TvSurfaceElevated
                    ),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.height(38.dp)
                ) {
                    Text(
                        text = ":$p",
                        color = if (isSelected) Color.Black else TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }

        Button(
            onClick = {
                focusManager.clearFocus()
                onConnect()
            },
            enabled = !isConnecting && ip.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = TabloTeal),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            if (isConnecting) {
                CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Testing Connection...", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            } else {
                Text("Connect to $ip:$port", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun ConnectedDeviceCard(
    device: TabloDevice,
    onDisconnect: () -> Unit,
    onBackToLive: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x1A00C896))
            .border(BorderStroke(1.5.dp, TabloTeal), RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = TabloTeal, modifier = Modifier.size(20.dp))
                Text(
                    text = device.name,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "${device.host}:${device.port}",
                color = TabloTeal,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Model: ${device.model}", color = TextSecondary, fontSize = 12.sp)
            Text("Tuners: ${device.tunerCount}", color = TextSecondary, fontSize = 12.sp)
            if (device.firmware.isNotEmpty()) {
                Text("Firmware: ${device.firmware}", color = TextMuted, fontSize = 11.sp)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onBackToLive,
                colors = ButtonDefaults.buttonColors(containerColor = TabloTeal),
                modifier = Modifier
                    .weight(1.5f)
                    .height(38.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Live Multiview", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            Button(
                onClick = onDisconnect,
                colors = ButtonDefaults.buttonColors(containerColor = LiveRed.copy(alpha = 0.25f)),
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = null, tint = LiveRed)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Disconnect", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun DiscoveredDeviceCard(
    device: TabloDevice,
    isCurrentlyConnected: Boolean,
    isConnecting: Boolean,
    onSelect: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val bg = when {
        isCurrentlyConnected -> Color(0x2200C896)
        isFocused -> TvSurfaceElevated
        else -> TvSurface
    }
    val border = when {
        isFocused -> BorderStroke(2.dp, TvFocusHighlight)
        isCurrentlyConnected -> BorderStroke(1.5.dp, TabloTeal)
        else -> BorderStroke(1.dp, TvBorder)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(border, RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onSelect
            )
            .focusable(interactionSource = interactionSource)
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isCurrentlyConnected) TabloTeal else TvSurfaceElevated),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Tv,
                        contentDescription = null,
                        tint = if (isCurrentlyConnected) Color.Black else TabloTeal,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Text(
                        text = device.name,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${device.model} • ${device.host}:${device.port}",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            }

            if (isCurrentlyConnected) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(TabloTeal.copy(alpha = 0.2f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("ACTIVE", color = TabloTeal, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = onSelect,
                    enabled = !isConnecting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isFocused) TabloTeal else TvSurfaceElevated
                    ),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text(
                        text = "Connect",
                        color = if (isFocused) Color.Black else TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
