package de.jnns.bmsmonitor.bms

class BmsCellInfoResponse(bytes: ByteArray) {
    var command: Int = 0
    var status: Int = 0
    var cellCount: Int = 0
    var cellVoltages: FloatArray

    init {
        command = bytes[1].toInt()
        status = bytes[2].toInt()
        cellCount = bytes[3].toInt() / 2
        cellVoltages = FloatArray(cellCount)

        for (i in 0 until cellCount) {
            cellVoltages[i] = (bytes[4 + (i * 2)].toInt() and 0xFF shl 8 or (bytes[4 + (i * 2) + 1].toInt() and 0xFF)).toFloat() / 1000.0f
        }
    }
}