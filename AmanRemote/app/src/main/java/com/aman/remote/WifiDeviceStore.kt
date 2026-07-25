package com.aman.remote

import android.content.Context

data class WifiDevice(val name: String, val ip: String)

/** Simple persistence for the list of WiFi devices the user has added, using SharedPreferences. */
object WifiDeviceStore {
    private const val PREFS = "aman_remote_prefs"
    private const val KEY_DEVICES = "wifi_devices"

    fun getAll(context: Context): List<WifiDevice> {
        val raw = prefs(context).getString(KEY_DEVICES, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split(";;").mapNotNull { entry ->
            val parts = entry.split("::")
            if (parts.size == 2) WifiDevice(parts[0], parts[1]) else null
        }
    }

    fun add(context: Context, device: WifiDevice) {
        val current = getAll(context).toMutableList()
        // avoid duplicate IPs
        current.removeAll { it.ip == device.ip }
        current.add(device)
        save(context, current)
    }

    fun remove(context: Context, ip: String) {
        val current = getAll(context).toMutableList()
        current.removeAll { it.ip == ip }
        save(context, current)
    }

    private fun save(context: Context, list: List<WifiDevice>) {
        val raw = list.joinToString(";;") { "${it.name}::${it.ip}" }
        prefs(context).edit().putString(KEY_DEVICES, raw).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
