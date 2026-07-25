package com.aman.remote

import android.bluetooth.BluetoothAdapter
import android.content.*
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Opens when the user taps a device in MainActivity's list.
 * Auto-connects to that device (Bluetooth or WiFi, based on EXTRA_MODE)
 * and shows the full set of remote-control buttons for it.
 */
class RemoteControlActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MODE = "mode"
        const val EXTRA_ADDRESS_OR_IP = "address_or_ip"
        const val EXTRA_NAME = "name"
        const val MODE_BT = "bt"
        const val MODE_WIFI = "wifi"
    }

    private var mode = MODE_BT
    private var addressOrIp = ""

    private var btService: BluetoothConnectionService? = null
    private var btBound = false
    private var wifiService: WifiConnectionService? = null
    private var wifiBound = false

    private lateinit var tvStatus: TextView
    private lateinit var tvLog: TextView

    private val btServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            btService = (service as BluetoothConnectionService.LocalBinder).getService()
            btBound = true
            val device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(addressOrIp)
            btService?.connectToDevice(device)
        }
        override fun onServiceDisconnected(name: ComponentName?) { btBound = false }
    }

    private val wifiServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            wifiService = (service as WifiConnectionService.LocalBinder).getService()
            wifiBound = true
            wifiService?.connectToIp(addressOrIp)
        }
        override fun onServiceDisconnected(name: ComponentName?) { wifiBound = false }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothConnectionService.ACTION_MESSAGE_RECEIVED -> {
                    val msg = intent.getStringExtra(BluetoothConnectionService.EXTRA_MESSAGE) ?: return
                    CommandExecutor.execute(this@RemoteControlActivity, msg)
                }
                BluetoothConnectionService.ACTION_CONNECTION_STATE -> {
                    val state = intent.getStringExtra(BluetoothConnectionService.EXTRA_STATE) ?: return
                    tvStatus.text = "Status: $state"
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_remote_control)

        mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_BT
        addressOrIp = intent.getStringExtra(EXTRA_ADDRESS_OR_IP) ?: ""
        val name = intent.getStringExtra(EXTRA_NAME) ?: "Device"

        findViewById<TextView>(R.id.tvDeviceTitle).text = name
        tvStatus = findViewById(R.id.tvStatus)
        tvLog = findViewById(R.id.tvLog)

        findViewById<Button>(R.id.btnVolUp).setOnClickListener { sendCommand("VOL_UP") }
        findViewById<Button>(R.id.btnVolDown).setOnClickListener { sendCommand("VOL_DOWN") }
        findViewById<Button>(R.id.btnFlashOn).setOnClickListener { sendCommand("FLASH_ON") }
        findViewById<Button>(R.id.btnFlashOff).setOnClickListener { sendCommand("FLASH_OFF") }
        findViewById<Button>(R.id.btnWake).setOnClickListener { sendCommand("WAKE") }
        findViewById<Button>(R.id.btnWifiPanel).setOnClickListener { sendCommand("WIFI_PANEL") }
        findViewById<Button>(R.id.btnBtPanel).setOnClickListener { sendCommand("BT_PANEL") }
        findViewById<Button>(R.id.btnSleep).setOnClickListener { sendCommand("SLEEP") }

        connect()
    }

    private fun connect() {
        tvStatus.text = "Status: Connecting..."
        if (mode == MODE_BT) {
            val intent = Intent(this, BluetoothConnectionService::class.java)
            startService(intent)
            bindService(intent, btServiceConnection, Context.BIND_AUTO_CREATE)
        } else {
            val intent = Intent(this, WifiConnectionService::class.java)
            startService(intent)
            bindService(intent, wifiServiceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun sendCommand(cmd: String) {
        val connected = if (mode == MODE_BT) btService?.isConnected == true else wifiService?.isConnected == true
        if (!connected) {
            Toast.makeText(this, "Abhi connected nahi hai, thoda ruko", Toast.LENGTH_SHORT).show()
            return
        }
        if (mode == MODE_BT) btService?.sendCommand(cmd) else wifiService?.sendCommand(cmd)
        log("Sent: $cmd")
    }

    private fun log(msg: String) {
        tvLog.text = "$msg\n${tvLog.text}"
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(BluetoothConnectionService.ACTION_MESSAGE_RECEIVED)
            addAction(BluetoothConnectionService.ACTION_CONNECTION_STATE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(receiver)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (btBound) { unbindService(btServiceConnection); btBound = false }
        if (wifiBound) { unbindService(wifiServiceConnection); wifiBound = false }
    }
}
