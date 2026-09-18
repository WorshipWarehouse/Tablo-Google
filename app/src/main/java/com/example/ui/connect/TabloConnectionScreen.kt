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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.model.TabloDevice
import com.example.ui.components.TvKeyButton
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

@Composable
fun TabloConnectionScreen(
    currentDevice: TabloDevice?,
    discoveredDevices: List<TabloDevice>,
    isScanning: Boolean,
    onStartScan: () -> Unit,
    onSelectDevice: (TabloDevice) -> Unit,
    onManualConnect: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showManualIpDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TvBackground)
            .padding(horizontal = 32.dp, vertical = 20.dp)
            .onKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                    keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BACK
                ) {
                    onBack()
                    true
                } else false
            }
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Left Column: Device Connection Status & Account Info
            Column(
                modifier = Modifier
                    .weight(1.1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "CONNECT YOUR TABLO",
                    color = TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Your Fire TV communicates directly with your Tablo over your local home network for zero-lag OTA HD playback.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                // Current Connected Device Card
                if (currentDevice != null) {
                    ConnectedDeviceCard(device = currentDevice)
                }

                // Discovery Action Buttons
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onStartScan,
                        enabled = !isScanning,
                        colors = ButtonDefaults.buttonColors(containerColor = TabloTeal),
                        modifier = Modifier.height(44.dp)
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(
                                color = Color.Black,
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Discovering...", color = Color.Black, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.Black)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Find Tablo", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = { showManualIpDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = TvSurfaceElevated),
                        modifier = Modifier.height(44.dp)
                    ) {
                        Icon(Icons.Default.SettingsEthernet, contentDescription = null, tint = TabloTeal)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Enter IP Manually", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    }
                }

                // Tuner Awareness Card
                TunerAwarenessCard(currentDevice = currentDevice)
            }

            Spacer(modifier = Modifier.width(32.dp))

            // Right Column: Discovered Devices List
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                Text(
                    text = "DISCOVERED TABLO DEVICES",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

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
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Router, contentDescription = null, tint = TextMuted, modifier = Modifier.size(36.dp))
                            Text(
                                text = if (isScanning) "Searching local network..." else "No Tablo detected yet",
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Click 'Find Tablo' to scan association server and UDP broadcasts.",
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(discoveredDevices) { device ->
                            val isCurrent = currentDevice?.host == device.host
                            DiscoveredDeviceCard(
                                device = device,
                                isConnected = isCurrent,
                                onConnect = { onSelectDevice(device) }
                            )
                        }
                    }
                }
            }
        }

        // Manual IP Dialog
        if (showManualIpDialog) {
            ManualIpEntryDialog(
                onConnect = { ip ->
                    showManualIpDialog = false
                    onManualConnect(ip)
                },
                onDismiss = { showManualIpDialog = false }
            )
        }
    }
}

@Composable
fun ConnectedDeviceCard(device: TabloDevice) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF101A29))
            .border(BorderStroke(1.5.dp, TabloTeal.copy(alpha = 0.8f)), RoundedCornerShape(12.dp))
            .padding(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = TabloTeal,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "CONNECTED TABLO",
                        color = TabloTeal,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .background(Color(0x3300D2B4), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text("ACTIVE", color = TabloTeal, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Text(
                text = device.name,
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text("Model", color = TextMuted, fontSize = 11.sp)
                    Text(device.model, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                Column {
                    Text("IP Address", color = TextMuted, fontSize = 11.sp)
                    Text(device.host, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                Column {
                    Text("Tuners", color = TextMuted, fontSize = 11.sp)
                    Text("${device.tunerCount} Hardware Tuners", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                Column {
                    Text("Firmware", color = TextMuted, fontSize = 11.sp)
                    Text(device.firmware, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun TunerAwarenessCard(currentDevice: TabloDevice?) {
    val tuners = currentDevice?.tunerCount ?: 4
    val active = currentDevice?.activeTuners ?: 0
    val available = tuners - active

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(TvSurface)
            .border(BorderStroke(1.dp, TvBorder), RoundedCornerShape(10.dp))
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "TUNER RESOURCE ALLOCATION",
                color = TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (t in 0 until tuners) {
                    val inUse = t < active
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(28.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (inUse) LiveRed.copy(alpha = 0.8f) else TabloTeal.copy(alpha = 0.8f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (inUse) "Tuner ${t + 1} (In Use)" else "Tuner ${t + 1} (Free)",
                            color = Color.Black,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Text(
                text = "Multiview automatically manages stream allocation to prevent exceeding hardware tuner capacity.",
                color = TextMuted,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun DiscoveredDeviceCard(
    device: TabloDevice,
    isConnected: Boolean,
    onConnect: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val background = when {
        isFocused -> TabloTeal
        isConnected -> Color(0xFF101D2B)
        else -> TvSurface
    }
    val titleColor = if (isFocused) Color.Black else TextPrimary
    val subColor = if (isFocused) Color(0xCC000000) else TextSecondary
    val border = if (isFocused) BorderStroke(2.dp, TvFocusHighlight) else BorderStroke(1.dp, TvBorder)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .border(border, RoundedCornerShape(10.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onConnect)
            .focusable(interactionSource = interactionSource)
            .padding(16.dp)
    ) {
        Column {
            Text(
                text = device.name,
                color = titleColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${device.model} • ${device.host} • ${device.tunerCount} Tuners",
                color = subColor,
                fontSize = 12.sp
            )
        }

        Box(
            modifier = Modifier
                .background(
                    if (isFocused) Color.Black else if (isConnected) TabloTeal else TvSurfaceElevated,
                    RoundedCornerShape(6.dp)
                )
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = if (isConnected) "Connected" else "Connect",
                color = if (isFocused) TabloTeal else if (isConnected) Color.Black else TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun ManualIpEntryDialog(
    onConnect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var ipText by remember { mutableStateOf("192.168.1.") }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .width(440.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xF20F172A))
                .border(BorderStroke(1.5.dp, TabloTeal), RoundedCornerShape(14.dp))
                .padding(22.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Enter Tablo IP Address",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TvSurfaceElevated, RoundedCornerShape(8.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = ipText,
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Numeric Keypad for Fire TV Remote
                val numRows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf(".", "0", "DEL")
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    numRows.forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            row.forEach { key ->
                                TvKeyButton(
                                    text = key,
                                    onClick = {
                                        if (key == "DEL") {
                                            if (ipText.isNotEmpty()) ipText = ipText.dropLast(1)
                                        } else {
                                            ipText += key
                                        }
                                    },
                                    modifier = Modifier.weight(1f).height(44.dp)
                                )
                            }
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = TvSurfaceElevated),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel", color = TextPrimary)
                    }
                    Button(
                        onClick = { if (ipText.isNotBlank()) onConnect(ipText.trim()) },
                        colors = ButtonDefaults.buttonColors(containerColor = TabloTeal),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Connect", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
