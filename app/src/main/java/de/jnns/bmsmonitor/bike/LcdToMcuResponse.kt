package de.jnns.bmsmonitor.bike

@ExperimentalUnsignedTypes
class LcdToMcuResponse(bytes: ByteArray) {
    var p5BatteryCurve: Int = 0
    var p1MagnetCount: Int = 0
    var maxSpeed: Int = 0
    var wheelSize: Int = 0
    var assistLevel: Int = 0
    var lightStatus: Boolean = false
    var p2WheelImpulse: Int = 0
    var p3PowerAssist: Int = 0
    var p4ThumbThrottle: Int = 0
    var c1PasSensor: Int = 0
    var c2PhaseForm: Int = 0
    var c5MaxPower: Int = 0
    var c7CruiseControl: Int = 0
    var c4ThumbThrottle: Int = 0
    var c12Undervolt: Int = 0
    var c13BrakeRegen: Int = 0

    init {
        p5BatteryCurve = bytes[0].toInt()

        assistLevel = bytes[1].toInt() and 0x7
        lightStatus = (bytes[1].toInt() and 0x80) shr 7 != 0

        maxSpeed = ((((bytes[2].toInt() and 0xF8) shr 1) or ((bytes[4].toInt() and 0x20) shl 2) shr 2)) + 10
        wheelSize = (((bytes[2].toInt() and 0x7) shl 5) or ((bytes[4].toInt() and 0xC0) shr 3) shr 3)

        p1MagnetCount = bytes[3].toInt()

        p2WheelImpulse = (bytes[4].toInt() and 0x07)
        p3PowerAssist = (bytes[4].toInt() and 0x08) shr 3
        p4ThumbThrottle = (bytes[4].toInt() and 0x10) shr 4

        // 5 CRC

        c1PasSensor = (bytes[6].toInt() and 0x38) shr 3
        c2PhaseForm = bytes[6].toInt() and 0x07

        c5MaxPower = bytes[7].toInt() and 0x0F
        c7CruiseControl = (bytes[7].toInt() and 0x60) shr 5

        c4ThumbThrottle = (bytes[8].toInt() and 0xE0) shr 5

        c12Undervolt = bytes[9].toInt() and 0x0F

        c13BrakeRegen = (bytes[10].toInt() and 0x1C) shr 2

        // 11 unknown

        // 12 unknown
    }
}