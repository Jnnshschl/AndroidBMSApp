package de.jnns.bmsmonitor.bluetooth

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.util.Log
import de.jnns.bmsmonitor.bike.LcdToMcuResponse
import de.jnns.bmsmonitor.bike.McuToLcdResponse
import java.util.*

@ExperimentalUnsignedTypes
class BikeGattClientCallback(
    val onLcdToMcuDataAvailable: (data: LcdToMcuResponse) -> Unit,
    val onMcuToLcdDataAvailable: (data: McuToLcdResponse) -> Unit,
    val onConnectionSucceeded: () -> Unit,
    val onConnectionFailed: () -> Unit
) :
    BluetoothGattCallback() {
    lateinit var lcdToMcuCharacteristic: BluetoothGattCharacteristic
    lateinit var mcuToLcdCharacteristic: BluetoothGattCharacteristic

    var isConnected = false

    private val serviceUuid = UUID.fromString("c5bcf4cf-48fc-4cbd-a103-22d839e13d33")
    private val lcdToMcuUuid = UUID.fromString("e756a872-cf61-4e8a-bfe7-68868ce56092")
    private val mcuToLcdUuid = UUID.fromString("82538842-376d-457b-9789-bd0f93c347ed")

    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        super.onConnectionStateChange(gatt, status, newState)

        if (status != BluetoothGatt.GATT_SUCCESS) {
            Log.d("BluetoothGatt", "connection failed")
            onConnectionFailed()
            isConnected = false
            return
        }

        if (newState == BluetoothProfile.STATE_CONNECTED) {
            gatt.discoverServices()
        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            isConnected = false
        }
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        super.onServicesDiscovered(gatt, status)

        if (status != BluetoothGatt.GATT_SUCCESS) {
            return
        }

        val service = gatt.getService(serviceUuid)

        if (service != null) {
            lcdToMcuCharacteristic = service.getCharacteristic(lcdToMcuUuid)
            mcuToLcdCharacteristic = service.getCharacteristic(mcuToLcdUuid)

            gatt.setCharacteristicNotification(lcdToMcuCharacteristic, true)
            gatt.setCharacteristicNotification(mcuToLcdCharacteristic, true)

            onConnectionSucceeded()
            isConnected = true
        }
    }

    override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        super.onCharacteristicChanged(gatt, characteristic)

        if (characteristic.uuid == lcdToMcuUuid) {
            // Log.d("BluetoothGatt", "FrameData (LCD): " + characteristic.value.toHexString())

            val data = LcdToMcuResponse(characteristic.value)
            onLcdToMcuDataAvailable(data)
        } else if (characteristic.uuid == mcuToLcdUuid) {
            // Log.d("BluetoothGatt", "FrameData (MCU): " + characteristic.value.toHexString())

            val data = McuToLcdResponse(characteristic.value)
            onMcuToLcdDataAvailable(data)
        }
    }

    private fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }
}