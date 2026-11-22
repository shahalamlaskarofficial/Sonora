lpackage com.sonora.audio.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sonora.audio.model.AudioDeviceUi
import com.sonora.audio.model.ConnectionHistoryEntry
import com.sonora.audio.model.DeviceAudioProfile
import com.sonora.audio.model.DeviceBrand
import com.sonora.audio.model.DeviceConnectionType
import com.sonora.audio.model.EqPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.sonoraDataStore by preferencesDataStore(name = "sonora_audio_store")

typealias ProfileMap = Map<String, DeviceAudioProfile>
typealias DeviceMap = Map<String, AudioDeviceUi>
typealias PresetList = List<EqPreset>
typealias HistoryList = List<ConnectionHistoryEntry>
typealias WiredSlotList = List<Pair<String, String>>

data class SonoraSettings(
    val darkTheme: Boolean = true,
    val autoApplyProfile: Boolean = true,
    val batteryAlertsEnabled: Boolean = true,
    val batteryAlertThreshold: Int = 20,
    val backgroundServiceEnabled: Boolean = true,
    val activeWiredProfileId: String = "wired_default"
)

class SonoraStorage(private val context: Context) {

    private object Keys {
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val AUTO_APPLY = booleanPreferencesKey("auto_apply_profile")
        val BATTERY_ALERTS = booleanPreferencesKey("battery_alerts")
        val BATTERY_THRESHOLD = intPreferencesKey("battery_threshold")
        val SERVICE_ENABLED = booleanPreferencesKey("service_enabled")
        val ACTIVE_WIRED_ID = stringPreferencesKey("active_wired_profile_id")
        val PROFILES_JSON = stringPreferencesKey("device_profiles_json")
        val CUSTOM_PRESETS_JSON = stringPreferencesKey("custom_presets_json")
        val SAVED_DEVICES_JSON = stringPreferencesKey("saved_devices_json")
        val HISTORY_JSON = stringPreferencesKey("connection_history_json")
        val WIRED_PROFILES_LIST = stringPreferencesKey("wired_profiles_names_json")
    }

    private val defaultWiredSlots: WiredSlotList = listOf(
        "wired_default" to "Primary Wired IEM",
        "wired_studio" to "Studio Over-Ear",
        "wired_usbc" to "USB-C DAC Headset"
    )

    val settingsFlow: Flow<SonoraSettings> = context.sonoraDataStore.data.map { prefs ->
        SonoraSettings(
            darkTheme = prefs[Keys.DARK_THEME] ?: true,
            autoApplyProfile = prefs[Keys.AUTO_APPLY] ?: true,
            batteryAlertsEnabled = prefs[Keys.BATTERY_ALERTS] ?: true,
            batteryAlertThreshold = prefs[Keys.BATTERY_THRESHOLD] ?: 20,
            backgroundServiceEnabled = prefs[Keys.SERVICE_ENABLED] ?: true,
            activeWiredProfileId = prefs[Keys.ACTIVE_WIRED_ID] ?: "wired_default"
        )
    }

    val profilesFlow: Flow<ProfileMap> = context.sonoraDataStore.data.map { prefs ->
        parseProfiles(prefs[Keys.PROFILES_JSON])
    }

    val customPresetsFlow: Flow<PresetList> = context.sonoraDataStore.data.map { prefs ->
        parseCustomPresets(prefs[Keys.CUSTOM_PRESETS_JSON])
    }

    val savedDevicesFlow: Flow<DeviceMap> = context.sonoraDataStore.data.map { prefs ->
        parseSavedDevices(prefs[Keys.SAVED_DEVICES_JSON])
    }

    val historyFlow: Flow<HistoryList> = context.sonoraDataStore.data.map { prefs ->
        parseHistory(prefs[Keys.HISTORY_JSON])
    }

    val wiredSlotsFlow: Flow<WiredSlotList> = context.sonoraDataStore.data.map { prefs ->
        val raw = prefs[Keys.WIRED_PROFILES_LIST]
        if (raw.isNullOrBlank()) {
            defaultWiredSlots
        } else {
            runCatching {
                val arr = JSONArray(raw)
                buildList {
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        add(obj.getString("id") to obj.getString("name"))
                    }
                }
            }.getOrDefault(defaultWiredSlots)
        }
    }

    suspend fun updateSettings(transform: (SonoraSettings) -> SonoraSettings) {
        val current = settingsFlow.first()
        val updated = transform(current)
        context.sonoraDataStore.edit { prefs ->
            prefs[Keys.DARK_THEME] = updated.darkTheme
            prefs[Keys.AUTO_APPLY] = updated.autoApplyProfile
            prefs[Keys.BATTERY_ALERTS] = updated.batteryAlertsEnabled
            prefs[Keys.BATTERY_THRESHOLD] = updated.batteryAlertThreshold
            prefs[Keys.SERVICE_ENABLED] = updated.backgroundServiceEnabled
            prefs[Keys.ACTIVE_WIRED_ID] = updated.activeWiredProfileId
        }
    }

    suspend fun saveDeviceProfile(profile: DeviceAudioProfile) {
        context.sonoraDataStore.edit { prefs ->
            val map = parseProfiles(prefs[Keys.PROFILES_JSON]).toMutableMap()
            map[profile.deviceId] = profile
            prefs[Keys.PROFILES_JSON] = serializeProfiles(map)
        }
    }

    suspend fun getProfileForDevice(deviceId: String): DeviceAudioProfile {
        val map = profilesFlow.first()
        return map[deviceId] ?: DeviceAudioProfile(deviceId = deviceId)
    }

    suspend fun saveCustomPreset(preset: EqPreset) {
        context.sonoraDataStore.edit { prefs ->
            val list = parseCustomPresets(prefs[Keys.CUSTOM_PRESETS_JSON]).toMutableList()
            list.removeAll { it.id == preset.id || it.name.equals(preset.name, ignoreCase = true) }
            list.add(preset.copy(isBuiltIn = false))
            prefs[Keys.CUSTOM_PRESETS_JSON] = serializePresets(list)
        }
    }

    suspend fun deleteCustomPreset(presetId: String) {
        context.sonoraDataStore.edit { prefs ->
            val list = parseCustomPresets(prefs[Keys.CUSTOM_PRESETS_JSON]).filterNot { it.id == presetId }
            prefs[Keys.CUSTOM_PRESETS_JSON] = serializePresets(list)
        }
    }

    suspend fun addWiredSlot(name: String): String {
        val id = "wired_${System.currentTimeMillis()}"
        val current = wiredSlotsFlow.first().toMutableList()
        current.add(id to name.trim())
        context.sonoraDataStore.edit { prefs ->
            val arr = JSONArray()
            current.forEach { (slotId, slotName) ->
                arr.put(JSONObject().put("id", slotId).put("name", slotName))
            }
            prefs[Keys.WIRED_PROFILES_LIST] = arr.toString()
            prefs[Keys.ACTIVE_WIRED_ID] = id
        }
        return id
    }

    suspend fun upsertSavedDevice(device: AudioDeviceUi) {
        context.sonoraDataStore.edit { prefs ->
            val map = parseSavedDevices(prefs[Keys.SAVED_DEVICES_JSON]).toMutableMap()
            val existing = map[device.id]
            val merged = device.copy(
                customName = existing?.customName ?: device.customName,
                iconKey = existing?.iconKey ?: device.iconKey,
                isConnected = false
            )
            map[device.id] = merged
            prefs[Keys.SAVED_DEVICES_JSON] = serializeDevices(map)
        }
    }

    suspend fun updateDeviceMeta(deviceId: String, customName: String?, iconKey: String) {
        context.sonoraDataStore.edit { prefs ->
            val map = parseSavedDevices(prefs[Keys.SAVED_DEVICES_JSON]).toMutableMap()
            val existing = map[deviceId] ?: return@edit
            map[deviceId] = existing.copy(
                customName = customName?.trim()?.ifBlank { null },
                iconKey = iconKey
            )
            prefs[Keys.SAVED_DEVICES_JSON] = serializeDevices(map)
        }
    }

    suspend fun recordConnection(entry: ConnectionHistoryEntry) {
        context.sonoraDataStore.edit { prefs ->
            val current = parseHistory(prefs[Keys.HISTORY_JSON]).toMutableList()
            val last = current.firstOrNull()
            if (last == null || last.deviceId != entry.deviceId || (entry.timestampMs - last.timestampMs) > 60_000L) {
                current.add(0, entry)
                val trimmed = current.take(40)
                prefs[Keys.HISTORY_JSON] = serializeHistory(trimmed)
            }
        }
    }

    private fun parseProfiles(json: String?): ProfileMap {
        if (json.isNullOrBlank()) return emptyMap()
        return runCatching {
            val arr = JSONArray(json)
            buildMap {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val bandsJson = o.getJSONArray("bands")
                    val bands = List(bandsJson.length()) { idx -> bandsJson.getDouble(idx).toFloat() }
                    val profile = DeviceAudioProfile(
                        deviceId = o.getString("id"),
                        presetName = o.optString("preset", "Music"),
                        eqEnabled = o.optBoolean("eqEnabled", true),
                        bandLevelsDb = if (bands.size == 5) bands else listOf(0f, 0f, 0f, 0f, 0f),
                        bassBoost = o.optDouble("bass", 0.0).toFloat(),
                        trebleBoost = o.optDouble("treble", 0.0).toFloat(),
                        vocalBoost = o.optDouble("vocal", 0.0).toFloat(),
                        virtualizer = o.optDouble("virtualizer", 0.0).toFloat(),
                        balance = o.optDouble("balance", 0.0).toFloat(),
                        gamingMode = o.optBoolean("gaming", false)
                    )
                    put(profile.deviceId, profile)
                }
            }
        }.getOrDefault(emptyMap())
    }

    private fun serializeProfiles(map: ProfileMap): String {
        val arr = JSONArray()
        map.values.forEach { p ->
            val bands = JSONArray()
            p.bandLevelsDb.forEach { bands.put(it.toDouble()) }
            arr.put(
                JSONObject()
                    .put("id", p.deviceId)
                    .put("preset", p.presetName)
                    .put("eqEnabled", p.eqEnabled)
                    .put("bands", bands)
                    .put("bass", p.bassBoost.toDouble())
                    .put("treble", p.trebleBoost.toDouble())
                    .put("vocal", p.vocalBoost.toDouble())
                    .put("virtualizer", p.virtualizer.toDouble())
                    .put("balance", p.balance.toDouble())
                    .put("gaming", p.gamingMode)
            )
        }
        return arr.toString()
    }

    private fun parseCustomPresets(json: String?): PresetList {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val bArr = o.getJSONArray("bands")
                    val bands = List(bArr.length()) { bArr.getDouble(it).toFloat() }
                    add(
                        EqPreset(
                            id = o.getString("id"),
                            name = o.getString("name"),
                            bandLevelsDb = bands,
                            bassBoost = o.optDouble("bass", 0.0).toFloat(),
                            trebleBoost = o.optDouble("treble", 0.0).toFloat(),
                            vocalBoost = o.optDouble("vocal", 0.0).toFloat(),
                            isBuiltIn = false
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun serializePresets(list: PresetList): String {
        val arr = JSONArray()
        list.forEach { p ->
            val b = JSONArray()
            p.bandLevelsDb.forEach { b.put(it.toDouble()) }
            arr.put(
                JSONObject()
                    .put("id", p.id)
                    .put("name", p.name)
                    .put("bands", b)
                    .put("bass", p.bassBoost.toDouble())
                    .put("treble", p.trebleBoost.toDouble())
                    .put("vocal", p.vocalBoost.toDouble())
            )
        }
        return arr.toString()
    }

    private fun parseSavedDevices(json: String?): DeviceMap {
        if (json.isNullOrBlank()) return emptyMap()
        return runCatching {
            val arr = JSONArray(json)
            buildMap {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val type = runCatching {
                        DeviceConnectionType.valueOf(o.getString("type"))
                    }.getOrDefault(DeviceConnectionType.BLUETOOTH_CLASSIC)
                    val brand = runCatching {
                        DeviceBrand.valueOf(o.getString("brand"))
                    }.getOrDefault(DeviceBrand.GENERIC)
                    val dev = AudioDeviceUi(
                        id = o.getString("id"),
                        hardwareName = o.getString("hwName"),
                        customName = o.optString("customName", "").ifBlank { null },
                        iconKey = o.optString("iconKey", "earbuds"),
                        connectionType = type,
                        brand = brand,
                        isConnected = false,
                        lastConnectedMs = o.optLong("lastMs", System.currentTimeMillis())
                    )
                    put(dev.id, dev)
                }
            }
        }.getOrDefault(emptyMap())
    }

    private fun serializeDevices(map: DeviceMap): String {
        val arr = JSONArray()
        map.values.forEach { d ->
            arr.put(
                JSONObject()
                    .put("id", d.id)
                    .put("hwName", d.hardwareName)
                    .put("customName", d.customName ?: "")
                    .put("iconKey", d.iconKey)
                    .put("type", d.connectionType.name)
                    .put("brand", d.brand.name)
                    .put("lastMs", d.lastConnectedMs)
            )
        }
        return arr.toString()
    }

    private fun parseHistory(json: String?): HistoryList {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val bat = if (o.has("bat") && !o.isNull("bat")) o.getInt("bat") else null
                    add(
                        ConnectionHistoryEntry(
                            deviceId = o.getString("id"),
                            deviceName = o.getString("name"),
                            connectionType = o.getString("type"),
                            timestampMs = o.getLong("ts"),
                            batteryPercent = bat
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun serializeHistory(list: HistoryList): String {
        val arr = JSONArray()
        list.forEach { h ->
            val obj = JSONObject()
                .put("id", h.deviceId)
                .put("name", h.deviceName)
                .put("type", h.connectionType)
                .put("ts", h.timestampMs)
            if (h.batteryPercent != null) {
                obj.put("bat", h.batteryPercent)
            }
            arr.put(obj)
        }
        return arr.toString()
    }
}
