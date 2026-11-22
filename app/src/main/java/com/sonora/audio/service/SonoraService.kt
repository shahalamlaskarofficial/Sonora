package com.sonora.audio.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.sonora.audio.MainActivity
import com.sonora.audio.R
import com.sonora.audio.audio.SonoraAudioEngine
import com.sonora.audio.data.SonoraStorage
import com.sonora.audio.device.DeviceMonitor
import com.sonora.audio.model.AudioDeviceUi
import com.sonora.audio.model.ConnectionHistoryEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SonoraService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var storage: SonoraStorage
    private lateinit var monitor: DeviceMonitor
    private var lastAppliedDeviceId: String? = null
    private val alertedLowBatteryIds = mutableSetOf<String>()

    companion object {
        const val CHANNEL_STATUS = "sonora_audio_status"
        const val CHANNEL_ALERTS = "sonora_battery_alerts"
        const val NOTIF_STATUS_ID = 1001
        const val NOTIF_ALERT_ID = 1002
    }

    override fun onCreate() {
        super.onCreate()
        storage = SonoraStorage(this)
        monitor = DeviceMonitor(this)
        SonoraAudioEngine.init(this)
        createNotificationChannels()

        val initialNotif = buildStatusNotification("Sonora DSP Active", "Listening for connected audio devices")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_STATUS_ID, initialNotif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIF_STATUS_ID, initialNotif)
        }

        monitor.start()
        observeConnectedDevices()
    }

    private fun observeConnectedDevices() {
        scope.launch {
            monitor.connectedDevices.collect { devices ->
                val active = devices.firstOrNull()
                val settings = storage.settingsFlow.first()

                if (active != null) {
                    val profileKey = if (active.isWired) settings.activeWiredProfileId else active.id
                    if (settings.autoApplyProfile && lastAppliedDeviceId != profileKey) {
                        val savedProfile = storage.getProfileForDevice(profileKey)
                        SonoraAudioEngine.applyProfile(savedProfile)
                        lastAppliedDeviceId = profileKey
                        storage.upsertSavedDevice(active)
                        storage.recordConnection(
                            ConnectionHistoryEntry(
                                deviceId = active.id,
                                deviceName = active.displayName,
                                connectionType = active.connectionType.label,
                                timestampMs = System.currentTimeMillis(),
                                batteryPercent = active.battery.lowestBattery
                            )
                        )
                    }
                    updateNotificationForDevice(active)
                    checkBatteryAlert(active, settings.batteryAlertsEnabled, settings.batteryAlertThreshold)
                } else {
                    lastAppliedDeviceId = null
                    val nm = getSystemService(NotificationManager::class.java)
                    nm.notify(
                        NOTIF_STATUS_ID,
                        buildStatusNotification("Sonora Standby", "Connect headphones or earbuds to auto-apply EQ")
                    )
                }
            }
        }
    }

    private fun updateNotificationForDevice(device: AudioDeviceUi) {
        val batteryText = when {
            device.battery.leftLevel != null || device.battery.rightLevel != null -> {
                val l = device.battery.leftLevel?.let { "L $it%" } ?: ""
                val r = device.battery.rightLevel?.let { "R $it%" } ?: ""
                val c = device.battery.caseLevel?.let { "Case $it%" } ?: ""
                listOf(l, r, c).filter { it.isNotEmpty() }.joinToString(" · ")
            }
            device.battery.singleLevel != null -> "Battery ${device.battery.singleLevel}%"
            device.isWired -> "Wired Hi-Fi DSP Active"
            else -> device.connectionType.label
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_STATUS_ID, buildStatusNotification(device.displayName, batteryText))
    }

    private fun checkBatteryAlert(device: AudioDeviceUi, enabled: Boolean, threshold: Int) {
        if (!enabled) return
        val low = device.battery.lowestBattery ?: return
        if (low <= threshold && alertedLowBatteryIds.add(device.id)) {
            val nm = getSystemService(NotificationManager::class.java)
            val alert = NotificationCompat.Builder(this, CHANNEL_ALERTS)
                .setSmallIcon(R.drawable.ic_sonora_logo)
                .setContentTitle("${device.displayName} battery low ($low%)")
                .setContentText("Connect to charger soon to avoid audio interruption.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
            nm.notify(NOTIF_ALERT_ID, alert)
        } else if (low > threshold + 10) {
            alertedLowBatteryIds.remove(device.id)
        }
    }

    private fun buildStatusNotification(title: String, text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_sonora_logo)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(pending)
            .build()
    }

    private fun createNotificationChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_STATUS, "Sonora Active Audio", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS, "Battery Alerts", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        monitor.stop()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
