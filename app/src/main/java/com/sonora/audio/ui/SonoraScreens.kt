package com.sonora.audio.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.Cable
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Earbuds
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.LocationSearching
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.SurroundSound
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonora.audio.audio.AudioEngineStatus
import com.sonora.audio.data.SonoraSettings
import com.sonora.audio.model.AudioDeviceUi
import com.sonora.audio.model.BuiltInPresets
import com.sonora.audio.model.CapabilityStatus
import com.sonora.audio.model.ConnectionHistoryEntry
import com.sonora.audio.model.DeviceAudioProfile
import com.sonora.audio.model.DeviceBrand
import com.sonora.audio.model.EqPreset
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

fun iconForKey(key: String): ImageVector = when (key) {
    "wired" -> Icons.Rounded.Cable
    "headphones" -> Icons.Rounded.Headphones
    "neckband" -> Icons.Rounded.Headphones
    "speaker" -> Icons.Rounded.Speaker
    else -> Icons.Rounded.Earbuds
}

@Composable
fun DashboardScreen(
    darkTheme: Boolean,
    connectedDevices: List<AudioDeviceUi>,
    activeDevice: AudioDeviceUi?,
    activeProfile: DeviceAudioProfile,
    engineStatus: AudioEngineStatus,
    settings: SonoraSettings,
    wiredSlots: List<Pair<String, String>>,
    hasPermissions: Boolean,
    onRequestPermissions: () -> Unit,
    onRefresh: () -> Unit,
    onToggleTheme: () -> Unit,
    onSelectWiredSlot: (String) -> Unit,
    onAddWiredSlot: (String) -> Unit,
    onVolumeChange: (Int) -> Unit,
    onQuickPreset: (EqPreset) -> Unit,
    onOpenEqTab: () -> Unit
) {
    var showAddWiredDialog by remember { mutableStateOf(false) }
    var newWiredName by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 46.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "SONORA",
                        fontSize = 12.sp,
                        letterSpacing = 3.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = SonoraPalette.Cyan
                    )
                    Text(
                        text = "Audio Companion",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh", tint = MaterialTheme.colorScheme.onBackground)
                    }
                    IconButton(onClick = onToggleTheme) {
                        Icon(
                            if (darkTheme) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                            contentDescription = "Theme",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }
        }

        if (!hasPermissions) {
            item {
                GlassCard(darkTheme = darkTheme, highlightAccent = true) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Permissions Needed for Bluetooth Telemetry",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "Grant Nearby Devices & Notifications so Sonora can read live earbud battery levels, auto-apply your EQ, and detect AirPods BLE packets.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = onRequestPermissions,
                            colors = ButtonDefaults.buttonColors(containerColor = SonoraPalette.Cyan, contentColor = Color.Black)
                        ) {
                            Text("Grant Permissions", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Hero Connected Device Card
        item {
            GlassCard(darkTheme = darkTheme, highlightAccent = activeDevice != null) {
                if (activeDevice == null) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(62.dp)
                                .clip(CircleShape)
                                .background(SonoraPalette.Cyan.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Bluetooth, contentDescription = null, tint = SonoraPalette.Cyan, modifier = Modifier.size(30.dp))
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "No External Audio Connected",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Connect AirPods, Galaxy Buds, Sony, JBL, OnePlus, boAt, Noise, or plug in wired headphones. Sonora detects them automatically.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .size(50.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(SonoraPalette.Cyan.copy(alpha = 0.18f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        iconForKey(activeDevice.iconKey),
                                        contentDescription = null,
                                        tint = SonoraPalette.Cyan,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = activeDevice.displayName,
                                        fontSize = 19.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${activeDevice.brand.displayName} · ${activeDevice.connectionType.label}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            StatusPill(text = "LIVE", color = SonoraPalette.Mint)
                        }

                        // Battery Visual Rings (L / R / Case or Single)
                        if (activeDevice.battery.leftLevel != null || activeDevice.battery.rightLevel != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                AnimatedBatteryRing(activeDevice.battery.leftLevel, "Left", activeDevice.battery.leftCharging, darkTheme = darkTheme)
                                AnimatedBatteryRing(activeDevice.battery.rightLevel, "Right", activeDevice.battery.rightCharging, darkTheme = darkTheme)
                                AnimatedBatteryRing(activeDevice.battery.caseLevel, "Case", activeDevice.battery.caseCharging, darkTheme = darkTheme)
                            }
                        } else if (activeDevice.battery.singleLevel != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                AnimatedBatteryRing(activeDevice.battery.singleLevel, "Device", darkTheme = darkTheme)
                                Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
                                    Text("Telemetry Source", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(activeDevice.battery.sourceLabel, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        "Active Profile: ${activeProfile.presetName}",
                                        fontSize = 12.sp,
                                        color = SonoraPalette.Cyan
                                    )
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (activeDevice.isWired) "Direct Wired Signal · Lossless Path" else "Battery not reported via standard HFP",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = activeProfile.presetName,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SonoraPalette.Cyan
                                )
                            }
                        }
                    }
                }
            }
        }

        // Dedicated Wired Studio Card (visible whenever wired headset is plugged in)
        if (activeDevice?.isWired == true) {
            item {
                GlassCard(darkTheme = darkTheme, highlightAccent = true) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Cable, contentDescription = null, tint = SonoraPalette.Mint)
                                Spacer(Modifier.width(8.dp))
                                Text("Wired Studio Dashboard", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                            IconButton(onClick = { showAddWiredDialog = true }) {
                                Icon(Icons.Rounded.Add, contentDescription = "Add wired profile", tint = SonoraPalette.Cyan)
                            }
                        }
                        Text(
                            "Switch separate saved EQ, bass, treble, and balance profiles for each of your wired earphones or IEMs:",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            wiredSlots.forEach { (slotId, slotName) ->
                                FilterChip(
                                    selected = settings.activeWiredProfileId == slotId,
                                    onClick = { onSelectWiredSlot(slotId) },
                                    label = { Text(slotName) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Live Master Volume & Quick EQ Curve Preview
        item {
            GlassCard(darkTheme = darkTheme) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.VolumeUp, contentDescription = null, tint = SonoraPalette.Cyan)
                            Spacer(Modifier.width(8.dp))
                            Text("Master Output Volume", fontWeight = FontWeight.SemiBold)
                        }
                        Text("${engineStatus.volumePercent}%", fontWeight = FontWeight.Bold, color = SonoraPalette.Cyan)
                    }
                    Slider(
                        value = engineStatus.volumePercent.toFloat(),
                        onValueChange = { onVolumeChange(it.roundToInt()) },
                        valueRange = 0f..100f,
                        colors = SliderDefaults.colors(thumbColor = SonoraPalette.Cyan, activeTrackColor = SonoraPalette.Cyan)
                    )
                }
            }
        }

        // Quick Preset Strip & Curve Preview
        item {
            GlassCard(darkTheme = darkTheme) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Active Sound Signature", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        TextButton(onClick = onOpenEqTab) {
                            Text("Open Studio EQ", color = SonoraPalette.Cyan)
                        }
                    }
                    EqualizerCurveVisualizer(
                        bandsDb = activeProfile.bandLevelsDb,
                        enabled = activeProfile.eqEnabled,
                        darkTheme = darkTheme
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BuiltInPresets.all.forEach { preset ->
                            FilterChip(
                                selected = activeProfile.presetName == preset.name,
                                onClick = { onQuickPreset(preset) },
                                label = { Text(preset.name) }
                            )
                        }
                    }
                }
            }
        }

        // Multi-device list if 2+ outputs active
        if (connectedDevices.size > 1) {
            item {
                GlassCard(darkTheme = darkTheme) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("All Connected Outputs (${connectedDevices.size})", fontWeight = FontWeight.Bold)
                        connectedDevices.forEach { dev ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(dev.displayName, fontSize = 13.sp)
                                Text(dev.connectionType.label, fontSize = 12.sp, color = SonoraPalette.Cyan)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddWiredDialog) {
        AlertDialog(
            onDismissRequest = { showAddWiredDialog = false },
            title = { Text("New Wired Headphone Profile") },
            text = {
                OutlinedTextField(
                    value = newWiredName,
                    onValueChange = { newWiredName = it },
                    label = { Text("e.g., Moondrop Chu II / Sony MDR") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newWiredName.isNotBlank()) {
                            onAddWiredSlot(newWiredName)
                            newWiredName = ""
                            showAddWiredDialog = false
                        }
                    }
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showAddWiredDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun EqualizerScreen(
    darkTheme: Boolean,
    activeDeviceName: String,
    profile: DeviceAudioProfile,
    customPresets: List<EqPreset>,
    engineStatus: AudioEngineStatus,
    onProfileChange: (DeviceAudioProfile) -> Unit,
    onSaveCustomPreset: (String) -> Unit,
    onDeleteCustomPreset: (String) -> Unit
) {
    val context = LocalContext.current
    var showSaveDialog by remember { mutableStateOf(false) }
    var presetNameInput by remember { mutableStateOf("") }
    val freqLabels = engineStatus.bandFrequenciesHz.map { hz ->
        if (hz >= 1000) "${(hz / 1000f).let { if (it % 1f == 0f) it.toInt().toString() else String.format(Locale.US, "%.1f", it) }}k" else "${hz}Hz"
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 46.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("AUDIO CONTROL CENTRE", fontSize = 11.sp, letterSpacing = 2.5.sp, color = SonoraPalette.Cyan, fontWeight = FontWeight.Bold)
                    Text("Studio Equalizer", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("Saved automatically for $activeDeviceName", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = profile.eqEnabled,
                    onCheckedChange = { onProfileChange(profile.copy(eqEnabled = it)) }
                )
            }
        }

        // Visualizer + Presets
        item {
            GlassCard(darkTheme = darkTheme, highlightAccent = profile.eqEnabled) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(engineStatus.statusNote, fontSize = 11.sp, color = SonoraPalette.Mint)
                        TextButton(onClick = { showSaveDialog = true }) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Save Preset")
                        }
                    }

                    EqualizerCurveVisualizer(
                        bandsDb = profile.bandLevelsDb,
                        enabled = profile.eqEnabled,
                        darkTheme = darkTheme
                    )

                    // Presets row (Built-in + Custom)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        (BuiltInPresets.all + customPresets).forEach { preset ->
                            FilterChip(
                                selected = profile.presetName == preset.name,
                                onClick = {
                                    onProfileChange(
                                        profile.copy(
                                            presetName = preset.name,
                                            bandLevelsDb = preset.bandLevelsDb,
                                            bassBoost = preset.bassBoost,
                                            trebleBoost = preset.trebleBoost,
                                            vocalBoost = preset.vocalBoost
                                        )
                                    )
                                },
                                label = { Text(preset.name) },
                                trailingIcon = if (!preset.isBuiltIn) {
                                    {
                                        Icon(
                                            Icons.Rounded.Delete,
                                            contentDescription = "Delete",
                                            modifier = Modifier
                                                .size(15.dp)
                                                .clickable { onDeleteCustomPreset(preset.id) }
                                        )
                                    }
                                } else null
                            )
                        }
                    }
                }
            }
        }

        // 5-Band Sliders
        item {
            GlassCard(darkTheme = darkTheme) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("5-Band Frequency Tuning (-12 dB to +12 dB)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    profile.bandLevelsDb.forEachIndexed { idx, valueDb ->
                        val label = freqLabels.getOrElse(idx) { "Band ${idx + 1}" }
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = "${if (valueDb >= 0) "+" else ""}${String.format(Locale.US, "%.1f", valueDb)} dB",
                                    fontSize = 12.sp,
                                    color = SonoraPalette.Cyan
                                )
                            }
                            Slider(
                                value = valueDb,
                                enabled = profile.eqEnabled,
                                onValueChange = { updated ->
                                    val newBands = profile.bandLevelsDb.toMutableList()
                                    newBands[idx] = updated
                                    onProfileChange(profile.copy(presetName = "Custom", bandLevelsDb = newBands))
                                },
                                valueRange = -12f..12f,
                                colors = SliderDefaults.colors(thumbColor = SonoraPalette.Cyan, activeTrackColor = SonoraPalette.Cyan)
                            )
                        }
                    }
                }
            }
        }

        // Bass, Treble, Vocal, Soundstage & L/R Balance
        item {
            GlassCard(darkTheme = darkTheme) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Acoustic Enhancements", fontWeight = FontWeight.Bold, fontSize = 15.sp)

                    LabeledSlider("Bass Boost", profile.bassBoost, profile.eqEnabled, SonoraPalette.Violet) {
                        onProfileChange(profile.copy(presetName = "Custom", bassBoost = it))
                    }
                    LabeledSlider("Treble Sparkle", profile.trebleBoost, profile.eqEnabled, SonoraPalette.Cyan) {
                        onProfileChange(profile.copy(presetName = "Custom", trebleBoost = it))
                    }
                    LabeledSlider("Vocal Clarity", profile.vocalBoost, profile.eqEnabled, SonoraPalette.Mint) {
                        onProfileChange(profile.copy(presetName = "Custom", vocalBoost = it))
                    }
                    LabeledSlider("Soundstage Width (DSP)", profile.virtualizer, profile.eqEnabled && !profile.gamingMode, SonoraPalette.Amber) {
                        onProfileChange(profile.copy(virtualizer = it))
                    }

                    // Stereo L/R Balance
                    Spacer(Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("L / R Stereo Balance", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        val balText = when {
                            profile.balance < -0.05f -> "L ${(-profile.balance * 100).roundToInt()}%"
                            profile.balance > 0.05f -> "R ${(profile.balance * 100).roundToInt()}%"
                            else -> "Centered"
                        }
                        Text(
                            text = if (engineStatus.balanceSupported) balText else "Requires Android 9+",
                            fontSize = 12.sp,
                            color = SonoraPalette.Cyan
                        )
                    }
                    Slider(
                        value = profile.balance,
                        enabled = engineStatus.balanceSupported,
                        onValueChange = { onProfileChange(profile.copy(balance = it)) },
                        valueRange = -1f..1f
                    )

                    // Honest Mono/Stereo system indicator + shortcut
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Mono / Stereo Output", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text(
                                text = "System is currently in ${if (engineStatus.systemMonoEnabled) "Mono" else "Stereo"} mode",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(onClick = { openSystemSettings(context, Settings.ACTION_ACCESSIBILITY_SETTINGS) }) {
                            Text("System Audio", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save Custom EQ Preset") },
            text = {
                OutlinedTextField(
                    value = presetNameInput,
                    onValueChange = { presetNameInput = it },
                    label = { Text("Preset Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (presetNameInput.isNotBlank()) {
                            onSaveCustomPreset(presetNameInput.trim())
                            presetNameInput = ""
                            showSaveDialog = false
                        }
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun SmartFeaturesScreen(
    darkTheme: Boolean,
    activeDevice: AudioDeviceUi?,
    profile: DeviceAudioProfile,
    engineStatus: AudioEngineStatus,
    onToggleGamingMode: (Boolean) -> Unit,
    onToggleFindChannel: (String) -> Unit
) {
    val context = LocalContext.current
    val capabilities = remember(activeDevice, engineStatus) {
        buildCapabilityList(activeDevice, engineStatus)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 46.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text("SMART HEADPHONE HUB", fontSize = 11.sp, letterSpacing = 2.5.sp, color = SonoraPalette.Cyan, fontWeight = FontWeight.Bold)
                Text("Smart Controls", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Gaming Audio Mode
        item {
            GlassCard(darkTheme = darkTheme, highlightAccent = profile.gamingMode) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.SportsEsports, contentDescription = null, tint = SonoraPalette.Cyan, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Gaming Audio Mode", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(
                                "Disables heavy spatial DSP to lower latency and boosts footstep frequencies.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(checked = profile.gamingMode, onCheckedChange = onToggleGamingMode)
                }
            }
        }

        // Find My Earbuds Acoustic Beacon
        item {
            GlassCard(darkTheme = darkTheme) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.LocationSearching, contentDescription = null, tint = SonoraPalette.Amber)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Find My Earbuds (Acoustic Ping)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(
                                "Plays a pulsing high-frequency chirp on Left, Right, or Both channels while connected. Remove earbuds from your ears first.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("LEFT" to "Ping Left", "BOTH" to "Both", "RIGHT" to "Ping Right").forEach { (ch, label) ->
                            val isPlaying = engineStatus.findingChannel == ch
                            Button(
                                onClick = { onToggleFindChannel(ch) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isPlaying) SonoraPalette.Coral else SonoraPalette.Cyan.copy(alpha = 0.2f),
                                    contentColor = if (isPlaying) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                Icon(
                                    if (isPlaying) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(if (isPlaying) "Stop" else label, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // Spatial Audio Status
        item {
            GlassCard(darkTheme = darkTheme) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.SurroundSound, contentDescription = null, tint = SonoraPalette.Violet, modifier = Modifier.size(26.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Android Spatial Audio", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            val desc = when {
                                engineStatus.spatialAudioEnabled -> "Active${if (engineStatus.headTrackingAvailable) " · Head Tracking Ready" else ""}"
                                engineStatus.spatialAudioAvailable -> "Supported by device — enable in System Sound settings"
                                else -> "Hardware Spatializer not exposed on this device; use DSP Soundstage in Studio EQ"
                            }
                            Text(desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    IconButton(onClick = { openSystemSettings(context, Settings.ACTION_SOUND_SETTINGS) }) {
                        Icon(Icons.Rounded.OpenInNew, contentDescription = "Sound Settings", tint = SonoraPalette.Cyan)
                    }
                }
            }
        }

        // Honest ANC / Touch / Vendor Companion Bridge
        item {
            GlassCard(darkTheme = darkTheme) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Lock, contentDescription = null, tint = SonoraPalette.Cyan)
                        Spacer(Modifier.width(8.dp))
                        Text("ANC, Transparency & Touch Controls", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    Text(
                        "Sonora never simulates fake ANC or touch toggles. Active Noise Cancellation and touch-gesture remapping use encrypted manufacturer protocols. When available, open your headset's official bridge below:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val pkg = activeDevice?.brand?.companionPackage
                    val appName = activeDevice?.brand?.companionAppName
                    if (pkg != null && appName != null) {
                        val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
                        Button(
                            onClick = {
                                if (launchIntent != null) {
                                    context.startActivity(launchIntent)
                                } else {
                                    openSystemSettings(context, Settings.ACTION_BLUETOOTH_SETTINGS)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SonoraPalette.Violet, contentColor = Color.White)
                        ) {
                            Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (launchIntent != null) "Open $appName" else "Open Bluetooth Device Settings")
                        }
                    } else {
                        OutlinedButton(onClick = { openSystemSettings(context, Settings.ACTION_BLUETOOTH_SETTINGS) }) {
                            Text("Open Android Bluetooth Settings")
                        }
                    }
                }
            }
        }

        // Hardware Capability Matrix
        item {
            GlassCard(darkTheme = darkTheme) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Live Hardware Capability Report", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    capabilities.forEach { cap ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(cap.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text(cap.detail, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            StatusPill(
                                text = cap.tier,
                                color = when (cap.tier) {
                                    "Universal" -> SonoraPalette.Mint
                                    "Protocol" -> SonoraPalette.Cyan
                                    else -> SonoraPalette.Amber
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DevicesAndSettingsScreen(
    darkTheme: Boolean,
    bondedAndSavedDevices: List<AudioDeviceUi>,
    history: List<ConnectionHistoryEntry>,
    settings: SonoraSettings,
    onRenameDevice: (String, String?, String) -> Unit,
    onUpdateSettings: ((SonoraSettings) -> SonoraSettings) -> Unit
) {
    var editingDevice by remember { mutableStateOf<AudioDeviceUi?>(null) }
    var nameInput by remember { mutableStateOf("") }
    var selectedIcon by remember { mutableStateOf("earbuds") }
    val dateFmt = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 46.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text("DEVICES & AUTOMATION", fontSize = 11.sp, letterSpacing = 2.5.sp, color = SonoraPalette.Cyan, fontWeight = FontWeight.Bold)
                Text("Audio Vault", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Automation & Notification Settings
        item {
            GlassCard(darkTheme = darkTheme) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.NotificationsActive, contentDescription = null, tint = SonoraPalette.Cyan)
                        Spacer(Modifier.width(8.dp))
                        Text("Smart Automation", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    SettingSwitchRow(
                        title = "Auto-Apply Saved Profiles",
                        subtitle = "Instantly load each device's custom EQ & balance on connect",
                        checked = settings.autoApplyProfile
                    ) { checked -> onUpdateSettings { it.copy(autoApplyProfile = checked) } }

                    SettingSwitchRow(
                        title = "Low Battery Alerts (Below ${settings.batteryAlertThreshold}%)",
                        subtitle = "Send alert notification when headset battery runs low",
                        checked = settings.batteryAlertsEnabled
                    ) { checked -> onUpdateSettings { it.copy(batteryAlertsEnabled = checked) } }

                    SettingSwitchRow(
                        title = "Dark Obsidian Theme",
                        subtitle = "Switch between glossy dark glass and frosted pearl light mode",
                        checked = settings.darkTheme
                    ) { checked -> onUpdateSettings { it.copy(darkTheme = checked) } }
                }
            }
        }

        // Known / Paired Devices with Custom Rename & Icon
        item {
            Text("Paired & Remembered Devices", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        if (bondedAndSavedDevices.isEmpty()) {
            item {
                GlassCard(darkTheme = darkTheme) {
                    Text(
                        "No paired or remembered audio devices yet. Pair headphones in Android Settings or plug in wired earphones.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(bondedAndSavedDevices, key = { it.id }) { dev ->
                GlassCard(darkTheme = darkTheme, highlightAccent = dev.isConnected) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (dev.isConnected) SonoraPalette.Mint.copy(alpha = 0.2f) else Color.Gray.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    iconForKey(dev.iconKey),
                                    contentDescription = null,
                                    tint = if (dev.isConnected) SonoraPalette.Mint else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(dev.displayName, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${dev.brand.displayName} · ${if (dev.isConnected) "Connected" else "Saved"}",
                                    fontSize = 12.sp,
                                    color = if (dev.isConnected) SonoraPalette.Mint else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(onClick = {
                            editingDevice = dev
                            nameInput = dev.displayName
                            selectedIcon = dev.iconKey
                        }) {
                            Icon(Icons.Rounded.Edit, contentDescription = "Rename", tint = SonoraPalette.Cyan)
                        }
                    }
                }
            }
        }

        // Connection History
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.History, contentDescription = null, tint = SonoraPalette.Violet)
                Spacer(Modifier.width(8.dp))
                Text("Recent Connection History", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }

        if (history.isEmpty()) {
            item {
                GlassCard(darkTheme = darkTheme) {
                    Text("Connection timestamps and battery snapshots will appear here.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            items(history.take(12)) { entry ->
                GlassCard(darkTheme = darkTheme, cornerRadius = 18.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(entry.deviceName, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(
                                "${entry.connectionType} · ${dateFmt.format(Date(entry.timestampMs))}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        entry.batteryPercent?.let {
                            StatusPill(text = "$it%", color = SonoraPalette.Cyan)
                        }
                    }
                }
            }
        }
    }

    editingDevice?.let { dev ->
        AlertDialog(
            onDismissRequest = { editingDevice = null },
            title = { Text("Customize Device") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Custom Name") },
                        singleLine = true
                    )
                    Text("Icon Style", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("earbuds" to "Buds", "headphones" to "Over-Ear", "wired" to "Wired", "speaker" to "Speaker").forEach { (key, lbl) ->
                            FilterChip(
                                selected = selectedIcon == key,
                                onClick = { selectedIcon = key },
                                label = { Text(lbl, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onRenameDevice(dev.id, nameInput, selectedIcon)
                    editingDevice = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editingDevice = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun LabeledSlider(
    title: String,
    value: Float,
    enabled: Boolean,
    accent: Color,
    onChange: (Float) -> Unit
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text("${(value * 100).roundToInt()}%", fontSize = 12.sp, color = accent, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value,
            enabled = enabled,
            onValueChange = onChange,
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
        )
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun StatusPill(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text = text, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

private fun buildCapabilityList(device: AudioDeviceUi?, status: AudioEngineStatus): List<CapabilityStatus> {
    return listOf(
        CapabilityStatus("5-Band Equalizer & Bass Boost", "Universal", status.statusNote, status.dspAttached),
        CapabilityStatus("Stereo L/R Balance", "Universal", if (status.balanceSupported) "DynamicsProcessing active" else "Android 9+ required", status.balanceSupported),
        CapabilityStatus("Find My Earbuds (Acoustic Ping)", "Universal", "Stereo L/R audio beacon when connected", device != null),
        CapabilityStatus(
            "AirPods L/R/Case Battery",
            "Protocol",
            if (device?.brand == DeviceBrand.APPLE) "Listening to Apple 0x004C BLE frames" else "Active automatically when AirPods detected",
            device?.brand == DeviceBrand.APPLE
        ),
        CapabilityStatus("ANC / Transparency / Touch Remap", "Vendor-Locked", "Requires vendor encrypted protocol (use companion bridge)", false)
    )
}

private fun openSystemSettings(context: Context, action: String) {
    runCatching {
        context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
