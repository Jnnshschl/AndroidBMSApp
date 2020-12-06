package de.jnns.bmsmonitor.bluetooth

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.util.Log
import de.jnns.bmsmonitor.bike.LcdToMcuResponse
import de.jnns.bmsmonitor.bike.McuToLcdResponse
import de.jnns.bmsmonitor.bms.BmsCellInfoResponse
import de.jnns.bmsmonitor.bms.BmsGeneralInfoResponse
import java.util.*

@ExperimentalUnsignedTypes
class BikeGattClientCallback(
    val onLcdToMcuDataAvailable: (data: LcdToMcuResponse) -> Unit,
    val onMcuToLcdDataAvailable: (data: McuToLcdResponse) -> Unit,
    val onConnectionFailed: () -> Unit
) :
    BluetoothGattCallback() {
    lateinit var lcdToMcuCharacteristic: BluetoothGattCharacteristic
    lateinit var mcuToLcdCharacteristic: BluetoothGattCharacteristic

    var isConnected = false

    private val serviceUuid = UUID.fromString("c5bcf4cf-48fc-4cbd-a103-22d839e13d33")
    private val lcdToMcuUuid = UUID.fromString("e756a872-cf61-4e8a-bfe7-68868ce56092")
    private val mcuToLcdUuid = UUID.fromString("82538842-376d-457b-9789-bd0f93c347ed")

    override fun onConnectionStateChange(
        gatt: BluetoothGatt,
        status: Int,
        newState: Int
    ) {
        super.onConnectionStateChange(gatt, status, newState)

        if (status != BluetoothGatt.GATT_SUCCESS) {
            Log.i("GATT", "Connection failed")
            onConnectionFailed()
            isConnected = false
            return
        }

        if (newState == BluetoothProfile.STATE_CONNECTED) {
            Log.i("GATT", "Connected")
            gatt.discoverServices()
        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            Log.i("GATT", "Disconnected")
            isConnected = false
        }
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        super.onServicesDiscovered(gatt, status)
        Log.i("GATT", "onServicesDiscovered status: $status")

        if (status != BluetoothGatt.GATT_SUCCESS) {
            return
        }

        val service = gatt.getService(serviceUuid)

        lcdToMcuCharacteristic = service.getCharacteristic(lcdToMcuUuid)
        mcuToLcdCharacteristic = service.getCharacteristic(mcuToLcdUuid)

        gatt.setCharacteristicNotification(lcdToMcuCharacteristic, true)
        gatt.setCharacteristicNotification(mcuToLcdCharacteristic, true)

        isConnected = true
    }

    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic
    ) {
        super.onCharacteristicChanged(gatt, characteristic)

        if (characteristic.uuid == lcdToMcuUuid) {
            // Log.i("GATT", "FrameData (LCD): " + characteristic.value.toHexString())

            val data = LcdToMcuResponse(characteristic.value)
            onLcdToMcuDataAvailable(data)
        } else if (characteristic.uuid == mcuToLcdUuid) {
            // Log.i("GATT", "FrameData (MCU): " + characteristic.value.toHexString())

            val data = McuToLcdResponse(characteristic.value)
            onMcuToLcdDataAvailable(data)
        }
    }

    private fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }
}