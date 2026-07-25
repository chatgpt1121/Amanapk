package com.aman.remote

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.*
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

private typealias SysPackageManager = android.content.pm.PackageManager

/** One row in the "devices you can control" list. */
private sealed class DeviceEntry(val displayName: String, val subtitle: String) {
    class Bt(val device: BluetoothDevice, name: String, address: String) :
        DeviceEntry(name, "Bluetooth • $address")
    class Wifi(val ip: String, name: String) :
        DeviceEntry(name, "WiFi • $ip")
}

class MainActivity : AppCompatActivity() {

    // Only used here to LISTEN (this phone acting as receiver). Actual sending of
    // commands happens in RemoteControlActivity once a device is tapped.
    private var btService: BluetoothConnectionService? = null
    private var btBound = false
    private var wifiService: WifiConnectionService? = null
    private var wifiBound = false

    private lateinit var tvMyStatus: TextView
    private lateinit var rvDevices: RecyclerView

    private val btServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            btService = (service as BluetoothConnectionService.LocalBinder).getService()
            btBound = true
        }
        override fun onServiceDisconnected(name: ComponentName?) { btBound = false }
    }
    private val wifiServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            wifiService = (service as WifiConnectionService.LocalBinder).getService()
            wifiBound = true
        }
        override fun onServiceDisconnected(name: ComponentName?) { wifiBound = false }
    }

    // Receives status + incoming commands while this phone is being controlled by someone else.
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothConnectionService.ACTION_MESSAGE_RECEIVED -> {
                    val msg = intent.getStringExtra(BluetoothConnectionService.EXTRA_MESSAGE) ?: return
                    CommandExecutor.execute(this@MainActivity, msg)
                }
                BluetoothConnectionService.ACTION_CONNECTION_STATE -> {
                    val state = intent.getStringExtra(BluetoothConnectionService.EXTRA_STATE) ?: return
                    tvMyStatus.text = state
                }
            }
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refreshDeviceList() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvMyStatus = findViewById(R.id.tvMyStatus)
        rvDevices = findViewById(R.id.rvDevices)
        rvDevices.layoutManager = LinearLayoutManager(this)

        requestNeededPermissions()

        findViewById<Button>(R.id.btnListenBt).setOnClickListener {
            startAndBindBtService { btService?.startServer() }
        }

        findViewById<Button>(R.id.btnListenWifi).setOnClickListener {
            startAndBindWifiService {
                wifiService?.startServer()
                window.decorView.postDelayed({
                    tvMyStatus.text = "Your IP: ${wifiService?.getLocalIpAddress()}"
                }, 500)
            }
        }

        findViewById<Button>(R.id.btnAddWifiDevice).setOnClickListener { showAddWifiDeviceDialog() }

        findViewById<Button>(R.id.btnEnableSleepPermission).setOnClickListener { ensureDeviceAdmin() }

        refreshDeviceList()
    }

    /**
     * Prompts THIS phone's user to allow the app as Device Admin.
     * Needed on the phone that will be LOCKED/PUT TO SLEEP remotely -
     * not on the controller phone. Only needs to be done once, ever.
     */
    private fun ensureDeviceAdmin() {
        val dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(this, DeviceAdminReceiver::class.java)
        if (dpm.isAdminActive(admin)) {
            Toast.makeText(this, "Already enabled", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
        intent.putExtra(
            DevicePolicyManager.EXTRA_ADD_EXPLANATION,
            "Needed so this phone can be locked/put to sleep remotely by another phone."
        )
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        refreshDeviceList()
    }

    @SuppressLint("MissingPermission")
    private fun refreshDeviceList() {
        val entries = mutableListOf<DeviceEntry>()

        if (hasBluetoothPermission()) {
            val adapter = BluetoothAdapter.getDefaultAdapter()
            adapter?.bondedDevices?.forEach { device ->
                entries.add(DeviceEntry.Bt(device, device.name ?: "Unknown device", device.address))
            }
        }

        WifiDeviceStore.getAll(this).forEach { wd ->
            entries.add(DeviceEntry.Wifi(wd.ip, wd.name))
        }

        rvDevices.adapter = DeviceEntryAdapter(entries) { entry -> openRemoteControl(entry) }
    }

    private fun openRemoteControl(entry: DeviceEntry) {
        val intent = Intent(this, RemoteControlActivity::class.java)
        when (entry) {
            is DeviceEntry.Bt -> {
                intent.putExtra(RemoteControlActivity.EXTRA_MODE, RemoteControlActivity.MODE_BT)
                intent.putExtra(RemoteControlActivity.EXTRA_ADDRESS_OR_IP, entry.device.address)
                intent.putExtra(RemoteControlActivity.EXTRA_NAME, entry.displayName)
            }
            is DeviceEntry.Wifi -> {
                intent.putExtra(RemoteControlActivity.EXTRA_MODE, RemoteControlActivity.MODE_WIFI)
                intent.putExtra(RemoteControlActivity.EXTRA_ADDRESS_OR_IP, entry.ip)
                intent.putExtra(RemoteControlActivity.EXTRA_NAME, entry.displayName)
            }
        }
        startActivity(intent)
    }

    private fun showAddWifiDeviceDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 0)
        }
        val nameInput = EditText(this).apply { hint = "Name e.g. Papa's Phone" }
        val ipInput = EditText(this).apply { hint = "IP e.g. 192.168.1.24" }
        container.addView(nameInput)
        container.addView(ipInput)

        AlertDialog.Builder(this)
            .setTitle("Add WiFi device")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val name = nameInput.text.toString().trim().ifEmpty { "Unnamed phone" }
                val ip = ipInput.text.toString().trim()
                if (ip.isNotEmpty()) {
                    WifiDeviceStore.add(this, WifiDevice(name, ip))
                    refreshDeviceList()
                } else {
                    Toast.makeText(this, "IP address daalo", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---------------- Helpers ----------------

    private fun startAndBindBtService(onReady: () -> Unit) {
        val intent = Intent(this, BluetoothConnectionService::class.java)
        if (!btBound) {
            startService(intent)
            bindService(intent, btServiceConnection, Context.BIND_AUTO_CREATE)
            window.decorView.postDelayed({ onReady() }, 400)
        } else onReady()
    }

    private fun startAndBindWifiService(onReady: () -> Unit) {
        val intent = Intent(this, WifiConnectionService::class.java)
        if (!wifiBound) {
            startService(intent)
            bindService(intent, wifiServiceConnection, Context.BIND_AUTO_CREATE)
            window.decorView.postDelayed({ onReady() }, 400)
        } else onReady()
    }

    private fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) ==
                SysPackageManager.PERMISSION_GRANTED
        } else true
    }

    private fun requestNeededPermissions() {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms.add(Manifest.permission.BLUETOOTH_CONNECT)
            perms.add(Manifest.permission.BLUETOOTH_SCAN)
        }
        perms.add(Manifest.permission.ACCESS_FINE_LOCATION)
        perms.add(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(perms.toTypedArray())
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

/** Adapter for the "devices you can control" list - shared by Bluetooth and WiFi entries. */
private class DeviceEntryAdapter(
    private val items: List<DeviceEntry>,
    private val onClick: (DeviceEntry) -> Unit
) : RecyclerView.Adapter<DeviceEntryAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tvDeviceName)
        val subtitle: TextView = view.findViewById(R.id.tvDeviceAddress)
    }

    override fun onCreateViewHolder(parent: ViewGroup, position: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_device, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val entry = items[position]
        holder.name.text = entry.displayName
        holder.subtitle.text = entry.subtitle
        holder.itemView.setOnClickListener { onClick(entry) }
    }

    override fun getItemCount() = items.size
}
