package com.aman.remote

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.PowerManager
import android.provider.Settings

/**
 * Executes a remote command string on THIS device (the receiver side).
 * Command list:
 *   VOL_UP, VOL_DOWN          -> full auto, no restrictions
 *   FLASH_ON, FLASH_OFF       -> full auto (torch API)
 *   SLEEP                     -> full auto IF device admin already enabled
 *   WAKE                      -> full auto (wake lock + turn screen on)
 *   WIFI_PANEL                -> opens WiFi settings (user taps toggle - Android 10+ restriction)
 *   BT_PANEL                  -> opens Bluetooth settings (same restriction)
 */
object CommandExecutor {

    fun execute(context: Context, command: String) {
        when (command.trim().uppercase()) {
            "VOL_UP" -> adjustVolume(context, AudioManager.ADJUST_RAISE)
            "VOL_DOWN" -> adjustVolume(context, AudioManager.ADJUST_LOWER)
            "FLASH_ON" -> setFlashlight(context, true)
            "FLASH_OFF" -> setFlashlight(context, false)
            "SLEEP" -> lockScreen(context)
            "WAKE" -> wakeScreen(context)
            "WIFI_PANEL" -> openWifiPanel(context)
            "BT_PANEL" -> openBluetoothPanel(context)
        }
    }

    private fun adjustVolume(context: Context, direction: Int) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
    }

    private fun setFlashlight(context: Context, on: Boolean) {
        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return
            cameraManager.setTorchMode(cameraId, on)
        } catch (_: Exception) {
            // Device may not have a flash, or torch is in use elsewhere - fail silently.
        }
    }

    /**
     * Locks / turns off the screen. Requires the app to be an active Device Admin
     * (user enables this once via the prompt shown from MainActivity).
     */
    private fun lockScreen(context: Context) {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(context, DeviceAdminReceiver::class.java)
        if (dpm.isAdminActive(admin)) {
            dpm.lockNow()
        }
        // If admin isn't active yet, the Sleep button on MainActivity handles
        // prompting the user to grant it before this command is ever sent.
    }

    /**
     * Wakes the screen up. Works because the phone is already powered ON;
     * we're just turning the display back on and dismissing the keyguard visually.
     */
    private fun wakeScreen(context: Context) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        val wakeLock = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
            "AmanRemote:wakeLock"
        )
        wakeLock.acquire(3000)
    }

    /** Android 10+ blocks direct WiFi toggling by apps - this opens the panel for the user to tap. */
    private fun openWifiPanel(context: Context) {
        val intent = Intent(Settings.Panel.ACTION_WIFI)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** Same restriction applies to Bluetooth on Android 13+. */
    private fun openBluetoothPanel(context: Context) {
        val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
