package de.jnns.bmsmonitor.bluetooth

import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Intent
import android.os.Handler
import android.os.IBinder

@ExperimentalUnsignedTypes
class BleService : Service() {
    // bluetooth stuff
    private var isScanning = false
    private lateinit var bluetoothLeScanner: BluetoothLeScanner

    override fun onCreate() {
        super.onCreate()

        bluetoothLeScanner = BluetoothAdapter.getDefaultAdapter().bluetoothLeScanner

        startBleScanning()
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    private fun startBleScanning() {
        if (!isScanning) {
            isScanning = true
            bluetoothLeScanner.startScan(leScanCallback)
        }
    }

    private fun stopBleScanning(delay: Long = 0) {
        if (isScanning) {
            Handler().postDelayed({ bluetoothLeScanner.stopScan(leScanCallback) }, delay)
        }
    }

    private val leScanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)

            if (result.device != null) {
                BleManager.i.addDevice(result.device)
            }
        }
    }
}