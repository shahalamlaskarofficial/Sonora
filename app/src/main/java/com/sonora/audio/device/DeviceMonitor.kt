package com.sonora.audio.device

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.sonora.audio.model.AudioDeviceUi
import com.sonora.audio.model.BatteryState
import com.sonora.audio.model.DeviceBrand
import com.sonora.audio.model.DeviceConnectionType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DeviceMonitor(private val context: Context) {

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val bluetoothManager = appContext.getSystemService(BluetoothManager::class.java)
    private val bluetoothAdapter: BluetoothAdapter? get() = bluetoothManager?.adapter

    private val _connectedDevices = MutableStateFlow<List<AudioDeviceUi>>(emptyList())
    val connectedDevices: StateFlow<List<AudioDeviceUi>> = _connectedDevices.asStateFlow()

    private val _bondedDevices = MutableStateFlow<List<AudioDeviceUi>>(emptyList())
    val bondedDevices: StateFlow<List<AudioDeviceUi>> = _bondedDevices.asStateFlow()

    private val _airPodsBleBattery = MutableStateFlow<BatteryState?>(null)
    val airPodsBleBattery: StateFlow<BatteryState?> = _airPodsBleBattery.asStateFlow()

    private val hfpBatteryCache = mutableMapOf<String, Int>()
    private var a2dpProxy: BluetoothA2dp? = null
    private var headsetProxy: BluetoothHeadset? = null
    private var isStarted = false
    private var isBleScanning = false

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            refreshAll()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            refreshAll()
        }
    }

    private val btReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED" -> {
                    val dev = getParcelableDevice(intent)
                    val level = intent.getIntExtra("android.bluetooth.device.extra.BATTERY_LEVEL", -1)
                    if (dev != null && level in 0..100) {
                        hfpBatteryCache[dev.address] = level
                    }
                    refreshAll()
                }
                BluetoothDevice.ACTION_ACL_CONNECTED,
                BluetoothDevice.ACTION_ACL_DISCONNECTED,
                BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
                BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED,
                AudioManager.ACTION_HEADSET_PLUG -> {
                    refreshAll()
                }
            }
        }
    }

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
            when (profile) {
                BluetoothProfile.A2DP -> a2dpProxy = proxy as? BluetoothA2dp
                BluetoothProfile.HEADSET -> headsetProxy = proxy as? BluetoothHeadset
            }
            refreshAll()
        }

        override fun onServiceDisconnected(profile: Int) {
            when (profile) {
                BluetoothProfile.A2DP -> a2dpProxy = null
                BluetoothProfile.HEADSET -> headsetProxy = null
            }
            refreshAll()
        }
    }

    /**
     * Decodes Apple Continuity BLE Proximity Pairing frames (Manufacturer ID 0x004C, Type 0x07)
     * to read Left, Right, and Case battery levels for AirPods / AirPods Pro / Beats.
     */
    private val bleScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            val record = result?.scanRecord ?: return
            val data = record.getManufacturerSpecificData(0x004C) ?: return
            if (data.size < 18) return
            // Type 0x07 = Proximity Pairing Message
            if ((data[0].toInt() and 0xFF) != 0x07) return

            val statusByte = data[5].toInt() and 0xFF
            val isFlipped = (statusByte and 0x02) == 0
            val b6 = data[6].toInt() and 0xFF
            val b7 = data[7].toInt() and 0xFF

            val rawPrimary = (b6 shr 4) and 0x0F
            val rawSecondary = b6 and 0x0F
            val rawCase = b7 and 0x0F
            val chargeFlags = (b7 shr 4) and 0x0F

            val leftRaw = if (isFlipped) rawPrimary else rawSecondary
            val rightRaw = if (isFlipped) rawSecondary else rawPrimary

            val leftPct = if (leftRaw in 0..10) (leftRaw * 10).coerceAtMost(100) else null
            val rightPct = if (rightRaw in 0..10) (rightRaw * 10).coerceAtMost(100) else null
            val casePct = if (rawCase in 0..10) (rawCase * 10).coerceAtMost(100) else null

            val leftChg = (chargeFlags and (if (isFlipped) 0x01 else 0x02)) != 0
            val rightChg = (chargeFlags and (if (isFlipped) 0x02 else 0x01)) != 0
            val caseChg = (chargeFlags and 0x04) != 0

            if (leftPct != null || rightPct != null || casePct != null) {
                _airPodsBleBattery.value = BatteryState(
                    leftLevel = leftPct,
                    rightLevel = rightPct,
                    caseLevel = casePct,
                    leftCharging = leftChg,
                    rightCharging = rightChg,
                    caseCharging = caseChg,
                    sourceLabel = "Apple BLE Continuity"
                )
                refreshAll()
            }
        }
    }

    fun start() {
        if (isStarted) {
            refreshAll()
            return
        }
        isStarted = true
        audioManager?.registerAudioDeviceCallback(audioDeviceCallback, Handler(Looper.getMainLooper()))

        val filter = IntentFilter().apply {
            addAction("android.bluetooth.device.action.BATTERY_LEVEL_CHANGED")
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
            addAction(AudioManager.ACTION_HEADSET_PLUG)
        }
        ContextCompat.registerReceiver(appContext, btReceiver, filter, ContextCompat.RECEIVER_EXPORTED)

        if (hasConnectPermission()) {
            runCatching {
                bluetoothAdapter?.getProfileProxy(appContext, profileListener, BluetoothProfile.A2DP)
                bluetoothAdapter?.getProfileProxy(appContext, profileListener, BluetoothProfile.HEADSET)
            }
        }
        startAirPodsBleScan()
        refreshAll()
    }

    @SuppressLint("MissingPermission")
    fun startAirPodsBleScan() {
        if (isBleScanning || !hasScanPermission()) return
        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: return
        val filter = ScanFilter.Builder()
            .setManufacturerData(0x004C, byteArrayOf(0x07), byteArrayOf(0xFF.toByte()))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
            .build()
        runCatching {
            scanner.startScan(listOf(filter), settings, bleScanCallback)
            isBleScanning = true
        }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        if (!isStarted) return
        isStarted = false
        runCatching { audioManager?.unregisterAudioDeviceCallback(audioDeviceCallback) }
        runCatching { appContext.unregisterReceiver(btReceiver) }
        if (isBleScanning && hasScanPermission()) {
            runCatching { bluetoothAdapter?.bluetoothLeScanner?.stopScan(bleScanCallback) }
            isBleScanning = false
        }
        a2dpProxy?.let { bluetoothAdapter?.closeProfileProxy(BluetoothProfile.A2DP, it) }
        headsetProxy?.let { bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HEADSET, it) }
    }

    @SuppressLint("MissingPermission")
    fun refreshAll() {
        val activeList = mutableListOf<AudioDeviceUi>()
        val seenIds = mutableSetOf<String>()

        // 1. Inspect live Android AudioManager output devices (Wired 3.5mm, USB-C, BLE Audio, BT A2DP)
        val outputs = audioManager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS).orEmpty()
        for (info in outputs) {
            when (info.type) {
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_WIRED_HEADSET -> {
                    val id = "wired_35mm"
                    if (seenIds.add(id)) {
                        activeList.add(
                            AudioDeviceUi(
                                id = id,
                                hardwareName = info.productName?.toString()?.takeIf { it.isNotBlank() } ?: "Wired Headphones (3.5mm)",
                                iconKey = "wired",
                                connectionType = DeviceConnectionType.WIRED_35MM,
                                brand = DeviceBrand.WIRED,
                                isConnected = true,
                                battery = BatteryState(sourceLabel = "Wired Power (No Battery)")
                            )
                        )
                    }
                }
                AudioDeviceInfo.TYPE_USB_HEADSET,
                AudioDeviceInfo.TYPE_USB_DEVICE -> {
                    val id = "wired_usb_${info.productName}"
                    if (seenIds.add(id)) {
                        activeList.add(
                            AudioDeviceUi(
                                id = id,
                                hardwareName = info.productName?.toString()?.takeIf { it.isNotBlank() } ?: "USB-C Audio DAC",
                                iconKey = "wired",
                                connectionType = DeviceConnectionType.WIRED_USB,
                                brand = DeviceBrand.WIRED,
                                isConnected = true,
                                battery = BatteryState(sourceLabel = "USB-C Bus Powered")
                            )
                        )
                    }
                }
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> {
                    val addr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.address else ""
                    val name = info.productName?.toString()?.takeIf { it.isNotBlank() } ?: "Bluetooth Audio"
                    val id = addr.ifBlank { "bt_$name" }
                    if (seenIds.add(id)) {
                        val brand = classifyBrand(name)
                        val bat = resolveBattery(addr, brand)
                        activeList.add(
                            AudioDeviceUi(
                                id = id,
                                hardwareName = name,
                                iconKey = defaultIconFor(name, brand),
                                connectionType = DeviceConnectionType.BLUETOOTH_CLASSIC,
                                brand = brand,
                                isConnected = true,
                                battery = bat
                            )
                        )
                    }
                }
                else -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        (info.type == AudioDeviceInfo.TYPE_BLE_HEADSET || info.type == AudioDeviceInfo.TYPE_BLE_SPEAKER)
                    ) {
                        val addr = info.address
                        val name = info.productName?.toString()?.takeIf { it.isNotBlank() } ?: "BLE Audio Device"
                        val id = addr.ifBlank { "ble_$name" }
                        if (seenIds.add(id)) {
                            val brand = classifyBrand(name)
                            activeList.add(
                                AudioDeviceUi(
                                    id = id,
                                    hardwareName = name,
                                    iconKey = defaultIconFor(name, brand),
                                    connectionType = DeviceConnectionType.BLUETOOTH_LE,
                                    brand = brand,
                                    isConnected = true,
                                    battery = resolveBattery(addr, brand)
                                )
                            )
                        }
                    }
                }
            }
        }

        // 2. Supplement with Bluetooth A2DP / HFP Profile Proxies & Bonded Devices
        val bondedList = mutableListOf<AudioDeviceUi>()
        if (hasConnectPermission()) {
            val proxyConnected = buildSet {
                runCatching { a2dpProxy?.connectedDevices?.forEach { add(it.address) } }
                runCatching { headsetProxy?.connectedDevices?.forEach { add(it.address) } }
            }

            runCatching {
                bluetoothAdapter?.bondedDevices.orEmpty().forEach { device ->
                    val name = device.name ?: "Bluetooth Device"
                    val brand = classifyBrand(name)
                    val isConn = proxyConnected.contains(device.address) || seenIds.contains(device.address)
                    val bat = resolveBattery(device.address, brand, device)
                    val ui = AudioDeviceUi(
                        id = device.address,
                        hardwareName = name,
                        iconKey = defaultIconFor(name, brand),
                        connectionType = DeviceConnectionType.BLUETOOTH_CLASSIC,
                        brand = brand,
                        isConnected = isConn,
                        battery = bat
                    )
                    bondedList.add(ui)
                    if (isConn && seenIds.add(device.address)) {
                        activeList.add(ui)
                    } else if (isConn) {
                        // Enrich existing AudioManager entry with better battery info from BluetoothDevice
                        val idx = activeList.indexOfFirst { it.id == device.address }
                        if (idx >= 0 && !activeList[idx].battery.hasAnyBattery && bat.hasAnyBattery) {
                            activeList[idx] = activeList[idx].copy(battery = bat)
                        }
                    }
                }
            }
        }

        _connectedDevices.value = activeList
        _bondedDevices.value = bondedList.sortedByDescending { it.isConnected }
    }

    private fun resolveBattery(address: String, brand: DeviceBrand, device: BluetoothDevice? = null): BatteryState {
        if (brand == DeviceBrand.APPLE) {
            _airPodsBleBattery.value?.let { return it }
        }
        val cached = hfpBatteryCache[address]
        val reflected = device?.let { readHiddenBatteryLevel(it) }
        val level = reflected ?: cached
        return if (level != null && level in 0..100) {
            BatteryState(singleLevel = level, sourceLabel = "Bluetooth HFP Telemetry")
        } else {
            BatteryState(sourceLabel = "Not Reported by Device")
        }
    }

    private fun readHiddenBatteryLevel(device: BluetoothDevice): Int? {
        return runCatching {
            val method = device.javaClass.getMethod("getBatteryLevel")
            val result = method.invoke(device) as? Int ?: -1
            if (result in 0..100) result else null
        }.getOrNull()
    }

    fun classifyBrand(name: String): DeviceBrand {
        val n = name.lowercase()
        return when {
            "airpod" in n || "beats" in n -> DeviceBrand.APPLE
            "galaxy buds" in n || "buds2" in n || "buds3" in n || "buds fe" in n || "samsung" in n -> DeviceBrand.SAMSUNG
            "oneplus" in n || "nord buds" in n -> DeviceBrand.ONEPLUS
            "realme" in n -> DeviceBrand.REALME
            "jbl" in n -> DeviceBrand.JBL
            "sony" in n || "wh-1000" in n || "wf-1000" in n || "linkbuds" in n -> DeviceBrand.SONY
            "boat" in n || "airdopes" in n || "rockerz" in n || "nirvana" in n -> DeviceBrand.BOAT
            "noise" in n || "buds vs" in n || "air buds" in n -> DeviceBrand.NOISE
            "wired" in n || "usb" in n -> DeviceBrand.WIRED
            else -> DeviceBrand.GENERIC
        }
    }

    private fun defaultIconFor(name: String, brand: DeviceBrand): String {
        val n = name.lowercase()
        return when {
            brand == DeviceBrand.WIRED -> "wired"
            "rockerz" in n || "neckband" in n || "bullets" in n -> "neckband"
            "wh-" in n || "headphone" in n || "max" in n || "tune 7" in n || "live 6" in n -> "headphones"
            "speaker" in n || "flip" in n || "charge" in n || "stone" in n -> "speaker"
            else -> "earbuds"
        }
    }

    fun hasConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    fun hasScanPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    @Suppress("DEPRECATION")
    private fun getParcelableDevice(intent: Intent): BluetoothDevice? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
    }
}
