package de.jnns.bmsmonitor.bike

class McuToLcdResponse(bytes: ByteArray) {
    var batteryLevel: Int = 0
    var speed: Int = 0
    var rotationPeriod: Int = 0
    var errorCode: Int = 0
    var isThrottleActive: Boolean = false
    var isCruiseActive: Boolean = false
    var isWalkAssistActive: Boolean = false
    var power: Int = 0
    var motorTemperature: Int = 0

    init {
        // 0 unknown

        batteryLevel = bytes[1].toInt()

        // 2 unknown

        speed = bytes[3].toInt()

        rotationPeriod = bytes[4].toInt()

        errorCode = bytes[5].toInt()

        // 6 CRC

        isThrottleActive = (bytes[5].toInt() and 0x1) != 0
        isCruiseActive = (bytes[5].toInt() and 0x8) != 0
        isWalkAssistActive = (bytes[5].toInt() and 0x10) != 0

        power = bytes[8].toInt() * 13

        motorTemperature = bytes[9].toInt() + 15

        // 10 unknown

        // 11 unknown
    }
}