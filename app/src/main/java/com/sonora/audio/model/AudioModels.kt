package com.sonora.audio.model

enum class DeviceConnectionType(val label: String) {
    BLUETOOTH_CLASSIC("Bluetooth Audio"),
    BLUETOOTH_LE("BLE Audio"),
    WIRED_35MM("Wired 3.5mm"),
    WIRED_USB("USB-C Audio"),
    PHONE_SPEAKER("Device Speaker")
}

enum class DeviceBrand(
    val displayName: String,
    val companionPackage: String? = null,
    val companionAppName: String? = null
) {
    APPLE("Apple AirPods"),
    SAMSUNG("Samsung Galaxy Buds", "com.samsung.android.app.watchmanager", "Galaxy Wearable"),
    ONEPLUS("OnePlus Buds", "com.heytap.headset", "HeyMelody"),
    REALME("Realme Buds", "com.realme.link", "realme Link"),
    JBL("JBL Audio", "jbl.stc.com", "JBL Headphones"),
    SONY("Sony Audio", "com.sony.songpal.mdr", "Sony Sound Connect"),
    BOAT("boAt Audio", "com.boat.hearables", "boAt Hearables"),
    NOISE("Noise Audio", "com.noisefit", "NoiseFit / Buds"),
    WIRED("Wired Studio"),
    GENERIC("Universal Audio")
}

data class BatteryState(
    val singleLevel: Int? = null,
    val leftLevel: Int? = null,
    val rightLevel: Int? = null,
    val caseLevel: Int? = null,
    val leftCharging: Boolean = false,
    val rightCharging: Boolean = false,
    val caseCharging: Boolean = false,
    val sourceLabel: String = "Unavailable"
) {
    val hasAnyBattery: Boolean
        get() = singleLevel != null || leftLevel != null || rightLevel != null || caseLevel != null

    val lowestBattery: Int?
        get() = listOfNotNull(singleLevel, leftLevel, rightLevel).minOrNull()
}

data class AudioDeviceUi(
    val id: String,
    val hardwareName: String,
    val customName: String? = null,
    val iconKey: String = "earbuds",
    val connectionType: DeviceConnectionType,
    val brand: DeviceBrand,
    val isConnected: Boolean,
    val battery: BatteryState = BatteryState(),
    val lastConnectedMs: Long = System.currentTimeMillis()
) {
    val displayName: String
        get() = customName?.takeIf { it.isNotBlank() } ?: hardwareName

    val isWired: Boolean
        get() = connectionType == DeviceConnectionType.WIRED_35MM ||
            connectionType == DeviceConnectionType.WIRED_USB
}

data class EqPreset(
    val id: String,
    val name: String,
    val bandLevelsDb: List<Float>, // 5 normalized bands: 60Hz, 230Hz, 910Hz, 3.6kHz, 14kHz (-12f..12f)
    val bassBoost: Float = 0f,     // 0f..1f
    val trebleBoost: Float = 0f,   // 0f..1f
    val vocalBoost: Float = 0f,    // 0f..1f
    val isBuiltIn: Boolean = true
)

data class DeviceAudioProfile(
    val deviceId: String,
    val presetName: String = "Music",
    val eqEnabled: Boolean = true,
    val bandLevelsDb: List<Float> = listOf(1.5f, 0.5f, 0f, 1.5f, 2f),
    val bassBoost: Float = 0.15f,
    val trebleBoost: Float = 0.10f,
    val vocalBoost: Float = 0f,
    val virtualizer: Float = 0f,
    val balance: Float = 0f,       // -1f (Left) .. 0f (Center) .. 1f (Right)
    val gamingMode: Boolean = false
)

data class ConnectionHistoryEntry(
    val deviceId: String,
    val deviceName: String,
    val connectionType: String,
    val timestampMs: Long,
    val batteryPercent: Int?
)

data class CapabilityStatus(
    val title: String,
    val tier: String, // "Universal", "Protocol", "Vendor-Locked"
    val detail: String,
    val available: Boolean
)

object BuiltInPresets {
    val all: List<EqPreset> = listOf(
        EqPreset("music", "Music", listOf(1.5f, 0.5f, 0f, 1.5f, 2.5f), bassBoost = 0.2f, trebleBoost = 0.15f),
        EqPreset("gaming", "Gaming", listOf(-1f, 0.5f, 2.5f, 4f, 3f), bassBoost = 0.1f, trebleBoost = 0.35f, vocalBoost = 0.2f),
        EqPreset("movies", "Movies", listOf(3.5f, 1.5f, 1f, 2f, 2.5f), bassBoost = 0.4f, trebleBoost = 0.2f, vocalBoost = 0.25f),
        EqPreset("podcasts", "Podcasts", listOf(-3f, -1f, 4.5f, 3.5f, 0f), bassBoost = 0f, trebleBoost = 0.1f, vocalBoost = 0.55f),
        EqPreset("classical", "Classical", listOf(2f, 0.5f, -0.5f, 2f, 3.5f), bassBoost = 0.05f, trebleBoost = 0.25f),
        EqPreset("bass", "Bass", listOf(6.5f, 4f, 0f, 1f, 1.5f), bassBoost = 0.75f, trebleBoost = 0.1f),
        EqPreset("flat", "Flat", listOf(0f, 0f, 0f, 0f, 0f), bassBoost = 0f, trebleBoost = 0f)
    )
}
