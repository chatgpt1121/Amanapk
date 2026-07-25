package com.aman.remote

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.text.MessageFormat

/**
 * WiFi (local network) mode. Both phones must be on the SAME WiFi network
 * (e.g. same home/office router or same mobile hotspot). No pairing needed,
 * which is why this works when Bluetooth pairing is being unreliable.
 *
 * One phone starts a server (listens on PORT), the other phone types in
 * that server's IP address and connects to it directly.
 *
 * Reuses the exact same broadcast action names as BluetoothConnectionService
 * so MainActivity's single receiver + CommandExecutor work for both modes
 * without any changes.
 */
class WifiConnectionService : Service() {

    companion object {
        const val PORT = 8988
        const val CHANNEL_ID = "aman_remote_wifi_channel"
        const val NOTIF_ID = 102
    }

    inner class LocalBinder : Binder() {
        fun getService(): WifiConnectionService = this@WifiConnectionService
    }

    private val binder = LocalBinder()

    private var serverSocket: ServerSocket? = null
    private var socket: Socket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    @Volatile var isConnected = false
        private set

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Aman Remote (WiFi)", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun startForegroundNotification() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Aman Remote (WiFi)")
            .setContentText("Powered by Aman - WiFi connection active")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .build()
        startForeground(NOTIF_ID, notification)
    }

    /** Returns this device's local WiFi IP (e.g. "192.168.1.24") so the user can share it. */
    fun getLocalIpAddress(): String {
        try {
            val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            val ipInt = wifiManager.connectionInfo.ipAddress
            if (ipInt != 0) {
                return MessageFormat.format(
                    "{0}.{1}.{2}.{3}",
                    ipInt and 0xff,
                    ipInt shr 8 and 0xff,
                    ipInt shr 16 and 0xff,
                    ipInt shr 24 and 0xff
                )
            }
        } catch (_: Exception) { }
        return "Unknown - check WiFi settings"
    }

    /** This phone = receiver. Starts listening for the controller phone to connect. */
    fun startServer() {
        startForegroundNotification()
        Thread {
            try {
                serverSocket = ServerSocket(PORT)
                broadcastState("Waiting on WiFi (IP: ${getLocalIpAddress()})")
                val clientSocket = serverSocket?.accept() // blocks until a client connects
                if (clientSocket != null) {
                    setupStreams(clientSocket)
                    broadcastState("Connected (WiFi)")
                    listenForMessages()
                }
            } catch (e: IOException) {
                broadcastState("WiFi server error: ${e.message}")
            }
        }.start()
    }

    /** This phone = controller. Connects to the receiver phone's IP address. */
    fun connectToIp(ip: String) {
        startForegroundNotification()
        Thread {
            try {
                broadcastState("Connecting via WiFi to $ip...")
                val clientSocket = Socket()
                clientSocket.connect(InetSocketAddress(ip.trim(), PORT), 8000)
                setupStreams(clientSocket)
                broadcastState("Connected (WiFi)")
                listenForMessages()
            } catch (e: IOException) {
                broadcastState("WiFi connection failed: ${e.message}")
            }
        }.start()
    }

    private fun setupStreams(sock: Socket) {
        socket = sock
        inputStream = sock.getInputStream()
        outputStream = sock.getOutputStream()
        isConnected = true
    }

    private fun listenForMessages() {
        val buffer = ByteArray(1024)
        while (isConnected) {
            try {
                val bytes = inputStream?.read(buffer) ?: -1
                if (bytes > 0) {
                    val msg = String(buffer, 0, bytes).trim()
                    if (msg.isNotEmpty()) {
                        val intent = Intent(BluetoothConnectionService.ACTION_MESSAGE_RECEIVED)
                            .putExtra(BluetoothConnectionService.EXTRA_MESSAGE, msg)
                        sendBroadcast(intent)
                    }
                } else if (bytes == -1) {
                    isConnected = false
                    broadcastState("Disconnected")
                }
            } catch (e: IOException) {
                isConnected = false
                broadcastState("Disconnected")
                break
            }
        }
    }

    fun sendCommand(command: String) {
        try {
            outputStream?.write((command + "\n").toByteArray())
            outputStream?.flush()
        } catch (e: IOException) {
            broadcastState("Send failed: ${e.message}")
        }
    }

    private fun broadcastState(state: String) {
        val intent = Intent(BluetoothConnectionService.ACTION_CONNECTION_STATE)
            .putExtra(BluetoothConnectionService.EXTRA_STATE, state)
        sendBroadcast(intent)
    }

    fun closeConnection() {
        isConnected = false
        try { inputStream?.close() } catch (_: IOException) {}
        try { outputStream?.close() } catch (_: IOException) {}
        try { socket?.close() } catch (_: IOException) {}
        try { serverSocket?.close() } catch (_: IOException) {}
    }

    override fun onDestroy() {
        closeConnection()
        super.onDestroy()
    }
}
