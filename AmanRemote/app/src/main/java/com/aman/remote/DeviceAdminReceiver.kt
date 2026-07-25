package com.aman.remote

import android.app.admin.DeviceAdminReceiver as AndroidDeviceAdminReceiver
import android.content.Context
import android.content.Intent

/**
 * Required by Android to allow this app to call DevicePolicyManager.lockNow(),
 * which is what actually implements the "Sleep" (screen off/lock) remote command.
 * The user must enable this under Settings > Security > Device Admin Apps
 * the first time they use the Sleep feature (the app will prompt them).
 */
class DeviceAdminReceiver : AndroidDeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
    }
}
