package de.jnns.bmsmonitor

import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import com.google.gson.Gson
import de.jnns.bmsmonitor.bike.LcdToMcuResponse
import de.jnns.bmsmonitor.bike.McuToLcdResponse
import de.jnns.bmsmonitor.bluetooth.BikeGattClientCallback
import de.jnns.bmsmonitor.data.BikeData

@ExperimentalUnsignedTypes
class BikeService : Service() {
    // bluetooth stuff
    private lateinit var bluetoothGatt: BluetoothGatt
    private lateinit var gattClientCallback: BikeGattClientCallback
    private lateinit var currentBleDevice: BluetoothDevice
    private lateinit var bluetoothLeScanner: BluetoothLeScanner
    private lateinit var leDeviceList: ArrayList<BluetoothDevice>
    private var isScanning = false

    // bluetooth device mac to use
    private lateinit var bleMac: String
    private lateinit var blePin: String
    private lateinit var bleName: String

    // wait for both datasets
    private var receivedLcdToMcu = false
    private var receivedMcuToLcd = false

    // main dataset
    private var bikeData = BikeData()

    override fun onCreate() {
        bleMac = PreferenceManager.getDefaultSharedPreferences(this).getString("macAddressBike", "")!!
        blePin = PreferenceManager.getDefaultSharedPreferences(this).getString("blePinBike", "")!!

        // bluetooth uart callbacks
        gattClientCallback = BikeGattClientCallback(
            ::onLcdToMcuDataAvailable,  // process lcd to mcu info
            ::onMcuToLcdDataAvailable,  // process mcu to lcd info
            ::connectToDevice           // on connection fails
        )

        if (bleMac.isNotEmpty()) {
            bluetoothLeScanner = BluetoothAdapter.getDefaultAdapter().bluetoothLeScanner
            leDeviceList = ArrayList()

            startBleScanning()
        }
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    private fun onLcdToMcuDataAvailable(data: LcdToMcuResponse) {
        bikeData.assistLevel = data.assistLevel

        receivedLcdToMcu = true
        sendData()
    }

    private fun onMcuToLcdDataAvailable(data: McuToLcdResponse) {
        bikeData.speed = data.speed

        receivedMcuToLcd = true
        sendData()
    }

    private fun sendData() {
        if (receivedLcdToMcu && receivedMcuToLcd) {
            val intent = Intent("bikeDataIntent")
            intent.putExtra("deviceName", bleName)
            intent.putExtra("bikeData", Gson().toJson(bikeData))
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        }
    }

    private fun startBleScanning() {
        if (!isScanning) {
            isScanning = true
            bluetoothLeScanner.startScan(leScanCallback)
        }
    }

    private fun stopBleScanning(dev: BluetoothDevice) {
        isScanning = false
        currentBleDevice = dev
        bluetoothLeScanner.stopScan(leScanCallback)

        dev.setPin(blePin.toByteArray())
        dev.createBond()

        connectToDevice()
    }

    private fun connectToDevice() {
        bluetoothGatt = currentBleDevice.connectGatt(this, false, gattClientCallback)
    }

    private val leScanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)

            if (!leDeviceList.contains(result.device)) {
                Log.d("BikeService", "${result.device.name}: ${result.device.address}")

                leDeviceList.add(result.device)

                if (result.device.address.equals(bleMac, ignoreCase = true)) {
                    stopBleScanning(result.device)
                    bleName = result.device.name
                }
            }
        }
    }
}