package de.jnns.bmsmonitor.bms

class BmsGeneralInfoResponse(bytes: ByteArray) {
    var command: Int = 0
    var status: Int = 0
    var dataLength: Int = 0
    var totalVoltage: Float = 0.0f
    var totalCurrent: Float = 0.0f
    var residualCapacity: Float = 0.0f
    var capacity: Float = 0.0f
    var nominalCapacity: Float = 0.0f
    var temperatureProbeCount: Int = 0
    var temperatureProbeValues: FloatArray
    var cycles: Int = 0

    init {
        command = bytes[1].toInt()
        status = bytes[2].toInt()
        dataLength = bytes[3].toInt()
        totalVoltage = (bytes[4].toInt() and 0xFF shl 8 or (bytes[5].toInt() and 0xFF)).toFloat() / 100.0f
        totalCurrent = (bytes[6].toInt() and 0xFF shl 8 or (bytes[7].toInt() and 0xFF)).toFloat() / 100.0f

        // discharging
        if (bytes[6].toInt() == -1) {
            totalCurrent -= 655.360f
        }

        residualCapacity = (bytes[8].toInt() and 0xFF shl 8 or (bytes[9].toInt() and 0xFF)).toFloat() / 100.0f
        nominalCapacity = (bytes[10].toInt() and 0xFF shl 8 or (bytes[11].toInt() and 0xFF)).toFloat() / 100.0f
        cycles = bytes[12].toInt() and 0xFF shl 8 or (bytes[13].toInt() and 0xFF)

        // 14 & 15 = production date
        // 16 & 17 = balance low
        // 18 & 19 = balance high
        // 20 & 21 = protection status
        // 22 = version

        capacity = bytes[23].toInt() / 100.0f

        // 24 = MOS status
        // 25 = number of cells

        temperatureProbeCount = bytes[26].toInt()
        temperatureProbeValues = FloatArray(temperatureProbeCount)

        for (i in 0 until temperatureProbeCount) {
            temperatureProbeValues[i] = ((bytes[27 + (i * 2)].toInt() and 0xFF shl 8 or (bytes[27 + (i * 2) + 1].toInt() and 0xFF)) - 2731) / 10.0f
        }
    }
}