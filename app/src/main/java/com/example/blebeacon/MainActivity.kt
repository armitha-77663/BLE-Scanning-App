package com.example.blebeacon

import android.util.Log
import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val viewModel: BleViewModel by viewModels()
    private lateinit var beaconAdapter: BeaconAdapter
    private var bleScanner: BluetoothLeScanner? = null
    private val handler = Handler(Looper.getMainLooper())
    private var autoStopRunnable: Runnable? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            beginScan()
        } else {
            viewModel.addLog("Permissions denied — cannot scan")
            Toast.makeText(this, "Bluetooth permissions are required to scan", Toast.LENGTH_LONG).show()
        }
    }

    private val scanCallback = object : ScanCallback() {

        override fun onScanResult(callbackType: Int, result: ScanResult) {

            val record = result.scanRecord ?: return
            val manufacturerData = record.manufacturerSpecificData

            // Keep only BLE advertising devices
            if (manufacturerData.size() == 0) return

            Log.d(
                "BLE",
                "Name: ${result.device.name} | MAC: ${result.device.address} | RSSI: ${result.rssi}"
            )

            // SEND DEVICE TO VIEWMODEL
            viewModel.processScanResult(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {

            results.forEach { result ->

                val record = result.scanRecord ?: return@forEach
                val manufacturerData = record.manufacturerSpecificData

                // Keep only BLE beacon-style advertisers
                if (manufacturerData.size() == 0) return@forEach

                Log.d(
                    "BLE",
                    "Batch Device: ${result.device.name} | " +
                            "MAC: ${result.device.address} | " +
                            "RSSI: ${result.rssi}"
                )

                // Send valid BLE devices to UI
                viewModel.processScanResult(result)
            }
        }

        override fun onScanFailed(errorCode: Int) {

            val reason = when (errorCode) {
                SCAN_FAILED_ALREADY_STARTED -> "already started"
                SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "registration failed"
                SCAN_FAILED_FEATURE_UNSUPPORTED -> "feature unsupported"
                SCAN_FAILED_INTERNAL_ERROR -> "internal error"
                else -> "error $errorCode"
            }

            viewModel.addLog("Scan failed: $reason")
            viewModel.setScanning(false)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Set up RecyclerView
        beaconAdapter = BeaconAdapter()
        findViewById<RecyclerView>(R.id.recyclerView).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = beaconAdapter
        }

        // Button listeners
        findViewById<Button>(R.id.btnScan).setOnClickListener { checkPermissionsAndScan() }
        findViewById<Button>(R.id.btnStop).setOnClickListener { stopScan() }
        findViewById<Button>(R.id.btnClear).setOnClickListener { viewModel.clearBeacons() }

        val mapView = findViewById<PositionMapView>(R.id.mapView)

        // Observe beacons
        lifecycleScope.launch {
            viewModel.beacons.collect { beaconMap ->
                val list = beaconMap.values.sortedByDescending { it.rssi }
                beaconAdapter.submitList(list)
                mapView.beacons = list

                val count = list.size
                findViewById<TextView>(R.id.tvBeaconCount).text =
                    "$count beacon${if (count != 1) "s" else ""}"

                val strongestRssi = list.maxOfOrNull { it.rssi }
                val closestDist = list.minOfOrNull { it.distance }
                findViewById<TextView>(R.id.tvStrongest).text =
                    strongestRssi?.let { "$it dBm" } ?: "—"
                findViewById<TextView>(R.id.tvClosest).text =
                    closestDist?.let { "${"%.1f".format(it)} m" } ?: "—"
                findViewById<TextView>(R.id.tvPositionStatus).text =
                    if (count >= 3) "Active" else "Need ${3 - count} more"
            }
        }

        // Observe scanning state
        lifecycleScope.launch {
            viewModel.isScanning.collect { scanning ->
                findViewById<Button>(R.id.btnScan).visibility =
                    if (scanning) View.GONE else View.VISIBLE
                findViewById<Button>(R.id.btnStop).visibility =
                    if (scanning) View.VISIBLE else View.GONE
                findViewById<TextView>(R.id.tvStatus).text =
                    if (scanning) "● Scanning..." else "Idle"
                findViewById<TextView>(R.id.tvStatus).setTextColor(
                    if (scanning) getColor(R.color.green_strong) else getColor(android.R.color.darker_gray)
                )
            }
        }

        // Observe logs
        lifecycleScope.launch {
            viewModel.logs.collect { logs ->
                findViewById<TextView>(R.id.tvLog).text = logs.joinToString("\n")
            }
        }

        viewModel.addLog("App started. Tap 'Start Scan' to detect beacons.")
    }

    private fun checkPermissionsAndScan() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN))
                needed.add(Manifest.permission.BLUETOOTH_SCAN)
            if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT))
                needed.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION))
                needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (needed.isEmpty()) beginScan()
        else permissionLauncher.launch(needed.toTypedArray())
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun beginScan() {
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter

        if (bluetoothAdapter == null) {
            viewModel.addLog("Bluetooth not available on this device")
            return
        }
        if (!bluetoothAdapter.isEnabled) {
            viewModel.addLog("Bluetooth is disabled — please enable it")
            Toast.makeText(this, "Please enable Bluetooth and try again", Toast.LENGTH_SHORT).show()
            return
        }

        bleScanner = bluetoothAdapter.bluetoothLeScanner
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()

        try {
            bleScanner?.startScan(null, settings, scanCallback)
            viewModel.setScanning(true)
            viewModel.addLog("BLE scan started")

            // Auto-stop after 60 seconds to save battery
            autoStopRunnable = Runnable {
                stopScan()
                viewModel.addLog("Auto-stopped after 60 seconds")
            }.also { handler.postDelayed(it, 60_000) }

        } catch (e: SecurityException) {
            viewModel.addLog("Security exception: ${e.message}")
        }
    }

    private fun stopScan() {
        autoStopRunnable?.let { handler.removeCallbacks(it) }
        try {
            bleScanner?.stopScan(scanCallback)
        } catch (e: SecurityException) {
            viewModel.addLog("Could not stop scan: ${e.message}")
        }
        bleScanner = null
        viewModel.setScanning(false)
        viewModel.addLog("Scan stopped")
    }

    override fun onDestroy() {
        super.onDestroy()
        stopScan()
    }
}
