package de.jnns.bmsmonitor.services

import android.app.Service
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.content.Intent
import android.content.SharedPreferences
import android.os.IBinder
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import com.google.gson.Gson
import de.jnns.bmsmonitor.bike.LcdToMcuResponse
import de.jnns.bmsmonitor.bike.McuToLcdResponse
import de.jnns.bmsmonitor.bluetooth.BikeGattClientCallback
import de.jnns.bmsmonitor.bluetooth.BleManager
import de.jnns.bmsmonitor.data.BikeData

@ExperimentalUnsignedTypes
class BikeService : Service() {
    // bluetooth stuff
    private lateinit var bluetoothGatt: BluetoothGatt
    private lateinit var gattClientCallback: BikeGattClientCallback
    private lateinit var currentBleDevice: BluetoothDevice

    // bluetooth device mac to use
    private lateinit var bleMac: String
    private lateinit var blePin: String
    private lateinit var bleName: String

    // wait for both datasets
    private var receivedLcdToMcu = false
    private var receivedMcuToLcd = false

    // is connected
    private var isConnected = false
    private var isConnecting = false

    // main dataset
    private var bikeData = BikeData()

    private lateinit var listener: SharedPreferences.OnSharedPreferenceChangeListener

    override fun onCreate() {
        super.onCreate()

        bleMac = PreferenceManager.getDefaultSharedPreferences(this).getString("macAddressBike", "")!!
        blePin = PreferenceManager.getDefaultSharedPreferences(this).getString("blePinBike", "")!!

        BleManager.i.onUpdateFunctions.add {
            searchForDeviceAndConnect()
        }

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)

        listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "macAddressBike") {
                bleMac = PreferenceManager.getDefaultSharedPreferences(this).getString("macAddressBike", "")!!

                disconnectFromDevice()
                searchForDeviceAndConnect()
            }
        }

        prefs.registerOnSharedPreferenceChangeListener(listener)

        // bluetooth uart callbacks
        gattClientCallback = BikeGattClientCallback(
            ::onLcdToMcuDataAvailable,  // process lcd to mcu info
            ::onMcuToLcdDataAvailable,  // process mcu to lcd info
            ::onConnectionSucceeded,    // on connection fails
            ::connectToDevice           // on connection fails
        )
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

        Log.d("BikeService", "onLcdToMcuDataAvailable")
    }

    private fun onMcuToLcdDataAvailable(data: McuToLcdResponse) {
        bikeData.speed = data.speed

        receivedMcuToLcd = true
        sendData()

        Log.d("BikeService", "onMcuToLcdDataAvailable")
    }

    private fun sendData() {
        if (receivedLcdToMcu && receivedMcuToLcd) {
            val intent = Intent("bikeDataIntent")
            intent.putExtra("deviceName", bleName)
            intent.putExtra("bikeData", Gson().toJson(bikeData))
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        }
    }

    private fun searchForDeviceAndConnect() {
        val bleDevice = BleManager.i.bleDevices.firstOrNull { x -> x.address.equals(bleMac, ignoreCase = true) }

        if (bleDevice != null) {
            currentBleDevice = bleDevice
            connectToDevice()
        }
    }

    private fun onConnectionFailed() {
        isConnected = false
        isConnecting = false

        connectToDevice()
    }

    private fun onConnectionSucceeded() {
        isConnected = true
        isConnecting = false
    }


    private fun connectToDevice() {
        if (!isConnected && !isConnecting) {
            isConnecting = true

            // bluetooth uart callbacks
            gattClientCallback = BikeGattClientCallback(
                ::onLcdToMcuDataAvailable,  // process lcd to mcu info
                ::onMcuToLcdDataAvailable,  // process mcu to lcd info
                ::onConnectionSucceeded,    // on connection succeeded
                ::onConnectionFailed        // on connection fails
            )

            currentBleDevice.setPin(blePin.toByteArray())
            currentBleDevice.createBond()

            bluetoothGatt = currentBleDevice.connectGatt(this, false, gattClientCallback)
        }
    }

    private fun disconnectFromDevice() {
        if (isConnected) {
            bluetoothGatt.close()

            isConnected = false
        }
    }
}