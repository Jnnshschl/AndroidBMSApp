package de.jnns.bmsmonitor.bluetooth

import android.bluetooth.BluetoothDevice
import kotlinx.coroutines.Runnable

class BleManager {
    var bleDevices = ArrayList<BluetoothDevice>()
    var onUpdateFunctions = ArrayList<Runnable>()

    fun addDevice(device: BluetoothDevice) {
        if (!bleDevices.contains(device)) {
            bleDevices.add(device)

            for (func in onUpdateFunctions)
            {
                func.run()
            }
        }
    }

    fun getBleNames(): ArrayList<String> {
        val sbNames = ArrayList<String>()

        for (bleDevice in bleDevices) {
            if (!bleDevice.name.isNullOrEmpty()) {
                sbNames.add(bleDevice.name)
            } else {
                sbNames.add("unknown")
            }
        }

        return sbNames
    }

    fun getBleAddresses(): ArrayList<String> {
        val sbDevices = ArrayList<String>()

        for (bleDevice in bleDevices) {
            sbDevices.add(bleDevice.address)
        }

        return sbDevices
    }

    companion object {
        val i = BleManager()
    }
}