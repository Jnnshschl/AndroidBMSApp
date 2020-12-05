package de.jnns.bmsmonitor

import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import com.google.gson.Gson
import de.jnns.bmsmonitor.bluetooth.BmsGattClientCallback
import de.jnns.bmsmonitor.bms.BmsCellInfoResponse
import de.jnns.bmsmonitor.bms.BmsGeneralInfoResponse
import de.jnns.bmsmonitor.data.BatteryData

@ExperimentalUnsignedTypes
class BmsService : Service() {
    // BMS commands, they won't change
    private val cmdGeneralInfo: ByteArray = ubyteArrayOf(0xDDU, 0xA5U, 0x03U, 0x00U, 0xFFU, 0xFDU, 0x77U).toByteArray()
    private val cmdCellInfo: ByteArray = ubyteArrayOf(0xDDU, 0xA5U, 0x04U, 0x00U, 0xFFU, 0xFCU, 0x77U).toByteArray()
    private val cmdBmsVersion: ByteArray = ubyteArrayOf(0xDDU, 0xA5U, 0x05U, 0x00U, 0xFFU, 0xFBU, 0x77U).toByteArray()

    // bluetooth stuff
    private lateinit var bluetoothGatt: BluetoothGatt
    private lateinit var gattClientCallback: BmsGattClientCallback
    private lateinit var currentBleDevice: BluetoothDevice
    private lateinit var bluetoothLeScanner: BluetoothLeScanner
    private lateinit var leDeviceList: ArrayList<BluetoothDevice>
    private var isScanning = false

    // Handler that is going to poll data from the bms
    // it is going to toggle "dataModeSwitch" and
    // request General or Cell data
    private val dataHandler: Handler = Handler()
    private var dataModeSwitch = false
    private var dataPollDelay: Long = 0

    // we need both datasets to update the view
    private var cellInfoReceived = false
    private var generalInfoReceived = false

    // bluetooth device mac to use
    private lateinit var bleMac: String
    private lateinit var blePin: String
    private lateinit var bleName: String

    // no need to refresh data in the background
    private var isInForeground = false

    // main dataset
    private var batteryData = BatteryData()

    override fun onCreate() {
        bleMac = PreferenceManager.getDefaultSharedPreferences(this).getString("macAddress", "")!!
        blePin = PreferenceManager.getDefaultSharedPreferences(this).getString("blePin", "")!!
        dataPollDelay = PreferenceManager.getDefaultSharedPreferences(this).getString("refreshInterval", "1000")!!.toLong() / 2

        // bluetooth uart callbacks
        gattClientCallback = BmsGattClientCallback(
            ::onGeneralInfoAvailable,   // process general info
            ::onCellInfoAvailable,      // process cell info
            ::onConnectionSucceeded,    // on connection succeeded
            ::connectToDevice           // on connection fails
        )

        if (bleMac.isNotEmpty()) {
            //val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            //val bluetoothAdapter = bluetoothManager.adapter

            // enable bluetooth if needed
            // if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            //     startActivityForResult(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), 1)
            // }

            bluetoothLeScanner = BluetoothAdapter.getDefaultAdapter().bluetoothLeScanner
            leDeviceList = ArrayList()

            startBleScanning()
        }

        isInForeground = true
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    private fun onGeneralInfoAvailable(generalInfo: BmsGeneralInfoResponse) {
        batteryData.current = generalInfo.totalCurrent
        batteryData.currentCapacity = generalInfo.residualCapacity
        batteryData.totalCapacity = generalInfo.nominalCapacity
        batteryData.temperatureCount = generalInfo.temperatureProbeCount
        batteryData.temperatures = generalInfo.temperatureProbeValues

        generalInfoReceived = true
        sendData()
    }

    private fun onCellInfoAvailable(cellInfo: BmsCellInfoResponse) {
        batteryData.cellCount = cellInfo.cellCount
        batteryData.cellVoltages = cellInfo.cellVoltages

        cellInfoReceived = true
        sendData()
    }

    private fun sendData() {
        if (cellInfoReceived && generalInfoReceived) {
            cellInfoReceived = false
            generalInfoReceived = false

            val intent = Intent("bmsDataIntent")
            intent.putExtra("deviceName", bleName)
            intent.putExtra("batteryData", Gson().toJson(batteryData))
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        }
    }

    private fun onConnectionSucceeded() {
        dataHandler.postDelayed(object : Runnable {
            override fun run() {
                if (gattClientCallback.isConnected) {
                    if (isInForeground) {
                        if (dataModeSwitch) {
                            writeBytes(cmdGeneralInfo)
                        } else {
                            writeBytes(cmdCellInfo)
                        }

                        dataModeSwitch = !dataModeSwitch

                        dataHandler.postDelayed(this, dataPollDelay)
                    }
                } else {
                    connectToDevice()
                }
            }
        }, dataPollDelay)
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

    private fun writeBytes(bytes: ByteArray) {
        gattClientCallback.writeCharacteristic.value = bytes
        bluetoothGatt.writeCharacteristic(gattClientCallback.writeCharacteristic)
    }

    private val leScanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)

            if (!isInForeground) {
                return
            }

            if (!leDeviceList.contains(result.device)) {
                Log.d("BLE", "${result.device.name}: ${result.device.address}")

                leDeviceList.add(result.device)

                if (result.device.address.equals(bleMac, ignoreCase = true)) {
                    stopBleScanning(result.device)
                    bleName = result.device.name
                }
            }
        }
    }
}