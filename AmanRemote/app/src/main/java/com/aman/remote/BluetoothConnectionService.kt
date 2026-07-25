package com.aman.remote

import android.annotation.SuppressLint
import android.app.*
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * Foreground service that owns the Bluetooth socket connection.
 * One phone acts as SERVER (listens, waits for the other phone to connect),
 * the other acts as CLIENT (connects to a paired device).
 * Once connected, either side can send simple text commands like "VOL_UP".
 */
class BluetoothConnectionService : Service() {

    companion object {
        // Fixed UUID shared by both the server and client side of the app.
        val APP_UUID: UUID = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66")
        const val CHANNEL_ID = "aman_remote_channel"
        const val NOTIF_ID = 101

        const val ACTION_MESSAGE_RECEIVED = "com.aman.remote.MESSAGE_RECEIVED"
        const val ACTION_CONNECTION_STATE = "com.aman.remote.CONNECTION_STATE"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_STATE = "state"
    }

    inner class LocalBinder : Binder() {
        fun getService(): BluetoothConnectionService = this@BluetoothConnectionService
    }

    private val binder = LocalBinder()

    private var serverSocket: BluetoothServerSocket? = null
    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    @Volatile var isConnected = false
        private set

    private var acceptThread: Thread? = null
    private var readThread: Thread? = null

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Aman Remote", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun startForegroundNotification() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Aman Remote")
            .setContentText("Powered by Aman - connection active")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .build()
        startForeground(NOTIF_ID, notification)
    }

    /** Start listening for an incoming connection (this phone = receiver). */
    @SuppressLint("MissingPermission")
    fun startServer() {
        startForegroundNotification()
        acceptThread = Thread {
            try {
                val adapter = BluetoothAdapter.getDefaultAdapter()
                serverSocket = adapter.listenUsingRfcommWithServiceRecord("AmanRemote", APP_UUID)
                broadcastState("Waiting for connection...")
                val clientSocket = serverSocket?.accept() // blocks until a client connects
                if (clientSocket != null) {
                    setupStreams(clientSocket)
                    broadcastState("Connected")
                    listenForMessages()
                }
            } catch (e: IOException) {
                broadcastState("Server error: ${e.message}")
            }
        }
        acceptThread?.start()
    }

    /** Connect out to an already-paired device (this phone = controller/remote). */
    @SuppressLint("MissingPermission")
    fun connectToDevice(device: BluetoothDevice) {
        startForegroundNotification()
        Thread {
            try {
                BluetoothAdapter.getDefaultAdapter().cancelDiscovery()
                val clientSocket = device.createRfcommSocketToServiceRecord(APP_UUID)
                broadcastState("Connecting...")
                clientSocket.connect() // blocks until connected or throws
                setupStreams(clientSocket)
                broadcastState("Connected")
                listenForMessages()
            } catch (e: IOException) {
                broadcastState("Connection failed: ${e.message}")
            }
        }.start()
    }

    private fun setupStreams(sock: BluetoothSocket) {
        socket = sock
        inputStream = sock.inputStream
        outputStream = sock.outputStream
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
                        val intent = Intent(ACTION_MESSAGE_RECEIVED).putExtra(EXTRA_MESSAGE, msg)
                        sendBroadcast(intent)
                    }
                }
            } catch (e: IOException) {
                isConnected = false
                broadcastState("Disconnected")
                break
            }
        }
    }

    /** Send a text command to the other phone, e.g. "VOL_UP", "FLASH_ON". */
    fun sendCommand(command: String) {
        try {
            outputStream?.write((command + "\n").toByteArray())
            outputStream?.flush()
        } catch (e: IOException) {
            broadcastState("Send failed: ${e.message}")
        }
    }

    private fun broadcastState(state: String) {
        val intent = Intent(ACTION_CONNECTION_STATE).putExtra(EXTRA_STATE, state)
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
