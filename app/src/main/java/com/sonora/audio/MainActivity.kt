package com.sonora.audio

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.sonora.audio.audio.SonoraAudioEngine
import com.sonora.audio.data.SonoraSettings
import com.sonora.audio.data.SonoraStorage
import com.sonora.audio.device.DeviceMonitor
import com.sonora.audio.model.DeviceAudioProfile
import com.sonora.audio.model.EqPreset
import com.sonora.audio.service.SonoraService
import com.sonora.audio.ui.AuroraBackground
import com.sonora.audio.ui.DashboardScreen
import com.sonora.audio.ui.DevicesAndSettingsScreen
import com.sonora.audio.ui.EqualizerScreen
import com.sonora.audio.ui.SmartFeaturesScreen
import com.sonora.audio.ui.SonoraPalette
import com.sonora.audio.ui.SonoraTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        SonoraAudioEngine.init(this)
        setContent {
            SonoraAppRoot()
        }
    }
}

@Composable
private fun SonoraAppRoot() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val storage = remember { SonoraStorage(context) }
    val monitor = remember { DeviceMonitor(context) }

    val settings by storage.settingsFlow.collectAsState(initial = SonoraSettings())
    val profilesMap by storage.profilesFlow.collectAsState(initial = emptyMap())
    val customPresets by storage.customPresetsFlow.collectAsState(initial = emptyList())
    val savedDevicesMap by storage.savedDevicesFlow.collectAsState(initial = emptyMap())
    val history by storage.historyFlow.collectAsState(initial = emptyList())
    val wiredSlots by storage.wiredSlotsFlow.collectAsState(initial = listOf("wired_default" to "Primary Wired IEM"))

    val rawConnected by monitor.connectedDevices.collectAsState()
    val rawBonded by monitor.bondedDevices.collectAsState()
    val engineStatus by SonoraAudioEngine.status.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var hasPermissions by remember { mutableStateOf(checkRequiredPermissions(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasPermissions = checkRequiredPermissions(context)
        monitor.startAirPodsBleScan()
        monitor.refreshAll()
    }

    DisposableEffect(Unit) {
        monitor.start()
        runCatching {
            ContextCompat.startForegroundService(context, Intent(context, SonoraService::class.java))
        }
        onDispose {
            SonoraAudioEngine.stopFindEarbuds()
            monitor.stop()
        }
    }

    // Merge live hardware detections with saved custom names & icons
    val connectedDevices = remember(rawConnected, savedDevicesMap) {
        rawConnected.map { live ->
            val saved = savedDevicesMap[live.id]
            live.copy(
                customName = saved?.customName,
                iconKey = saved?.iconKey ?: live.iconKey
            )
        }
    }

    val allKnownDevices = remember(rawBonded, savedDevicesMap, connectedDevices) {
        val map = linkedMapOf<String, com.sonora.audio.model.AudioDeviceUi>()
        connectedDevices.forEach { map[it.id] = it }
        rawBonded.forEach { b ->
            if (!map.containsKey(b.id)) {
                val saved = savedDevicesMap[b.id]
                map[b.id] = b.copy(customName = saved?.customName, iconKey = saved?.iconKey ?: b.iconKey)
            }
        }
        savedDevicesMap.values.forEach { s ->
            if (!map.containsKey(s.id)) map[s.id] = s
        }
        map.values.toList()
    }

    val activeDevice = connectedDevices.firstOrNull()
    val activeProfileId = when {
        activeDevice == null -> "default_global"
        activeDevice.isWired -> settings.activeWiredProfileId
        else -> activeDevice.id
    }
    val activeProfile = profilesMap[activeProfileId] ?: DeviceAudioProfile(deviceId = activeProfileId)

    LaunchedEffect(activeProfileId, activeProfile) {
        SonoraAudioEngine.applyProfile(activeProfile)
    }

    SonoraTheme(darkTheme = settings.darkTheme) {
        AuroraBackground(darkTheme = settings.darkTheme) {
            Box(modifier = Modifier.fillMaxSize()) {
                when (selectedTab) {
                    0 -> DashboardScreen(
                        darkTheme = settings.darkTheme,
                        connectedDevices = connectedDevices,
                        activeDevice = activeDevice,
                        activeProfile = activeProfile,
                        engineStatus = engineStatus,
                        settings = settings,
                        wiredSlots = wiredSlots,
                        hasPermissions = hasPermissions,
                        onRequestPermissions = { permissionLauncher.launch(requiredPermissionsList()) },
                        onRefresh = {
                            monitor.refreshAll()
                            SonoraAudioEngine.refreshSystemAudioStatus(context)
                        },
                        onToggleTheme = { scope.launch { storage.updateSettings { it.copy(darkTheme = !it.darkTheme) } } },
                        onSelectWiredSlot = { slotId ->
                            scope.launch { storage.updateSettings { it.copy(activeWiredProfileId = slotId) } }
                        },
                        onAddWiredSlot = { name -> scope.launch { storage.addWiredSlot(name) } },
                        onVolumeChange = { SonoraAudioEngine.setMediaVolumePercent(it) },
                        onQuickPreset = { preset ->
                            val updated = activeProfile.copy(
                                presetName = preset.name,
                                bandLevelsDb = preset.bandLevelsDb,
                                bassBoost = preset.bassBoost,
                                trebleBoost = preset.trebleBoost,
                                vocalBoost = preset.vocalBoost
                            )
                            SonoraAudioEngine.applyProfile(updated)
                            scope.launch { storage.saveDeviceProfile(updated) }
                        },
                        onOpenEqTab = { selectedTab = 1 }
                    )

                    1 -> EqualizerScreen(
                        darkTheme = settings.darkTheme,
                        activeDeviceName = activeDevice?.displayName ?: "Default Speaker",
                        profile = activeProfile,
                        customPresets = customPresets,
                        engineStatus = engineStatus,
                        onProfileChange = { updated ->
                            SonoraAudioEngine.applyProfile(updated)
                            scope.launch { storage.saveDeviceProfile(updated) }
                        },
                        onSaveCustomPreset = { name ->
                            scope.launch {
                                storage.saveCustomPreset(
                                    EqPreset(
                                        id = "custom_${System.currentTimeMillis()}",
                                        name = name,
                                        bandLevelsDb = activeProfile.bandLevelsDb,
                                        bassBoost = activeProfile.bassBoost,
                                        trebleBoost = activeProfile.trebleBoost,
                                        vocalBoost = activeProfile.vocalBoost,
                                        isBuiltIn = false
                                    )
                                )
                            }
                        },
                        onDeleteCustomPreset = { id -> scope.launch { storage.deleteCustomPreset(id) } }
                    )

                    2 -> SmartFeaturesScreen(
                        darkTheme = settings.darkTheme,
                        activeDevice = activeDevice,
                        profile = activeProfile,
                        engineStatus = engineStatus,
                        onToggleGamingMode = { enabled ->
                            val updated = activeProfile.copy(gamingMode = enabled)
                            SonoraAudioEngine.applyProfile(updated)
                            scope.launch { storage.saveDeviceProfile(updated) }
                        },
                        onToggleFindChannel = { ch -> SonoraAudioEngine.toggleFindEarbuds(ch) }
                    )

                    3 -> DevicesAndSettingsScreen(
                        darkTheme = settings.darkTheme,
                        bondedAndSavedDevices = allKnownDevices,
                        history = history,
                        settings = settings,
                        onRenameDevice = { id, newName, iconKey ->
                            scope.launch {
                                allKnownDevices.find { it.id == id }?.let { storage.upsertSavedDevice(it) }
                                storage.updateDeviceMeta(id, newName, iconKey)
                            }
                        },
                        onUpdateSettings = { transform -> scope.launch { storage.updateSettings(transform) } }
                    )
                }

                // Floating Glass Bottom Navigation Dock
                FloatingGlassDock(
                    darkTheme = settings.darkTheme,
                    selectedTab = selectedTab,
                    onSelectTab = { selectedTab = it },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                )
            }
        }
    }
}

@Composable
private fun FloatingGlassDock(
    darkTheme: Boolean,
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        Triple("Home", Icons.Rounded.Headphones, 0),
        Triple("Studio", Icons.Rounded.Equalizer, 1),
        Triple("Smart", Icons.Rounded.Tune, 2),
        Triple("Devices", Icons.Rounded.Devices, 3)
    )
    val shape = RoundedCornerShape(28.dp)
    val bg = if (darkTheme) Color(0xD9101728) else Color(0xEBFFFFFF)
    val borderBrush = Brush.horizontalGradient(
        listOf(SonoraPalette.Cyan.copy(alpha = 0.45f), SonoraPalette.Violet.copy(alpha = 0.35f))
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bg)
            .border(1.dp, borderBrush, shape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { (label, icon, index) ->
            DockItem(
                label = label,
                icon = icon,
                selected = selectedTab == index,
                onClick = { onSelectTab(index) }
            )
        }
    }
}

@Composable
private fun DockItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    val tint = if (selected) SonoraPalette.Cyan else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) SonoraPalette.Cyan.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
        Text(label, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, color = tint)
    }
}

private fun requiredPermissionsList(): Array<String> = buildList {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        add(Manifest.permission.BLUETOOTH_CONNECT)
        add(Manifest.permission.BLUETOOTH_SCAN)
    } else {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
}.toTypedArray()

private fun checkRequiredPermissions(context: android.content.Context): Boolean {
    return requiredPermissionsList().all { perm ->
        ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
    }
}
