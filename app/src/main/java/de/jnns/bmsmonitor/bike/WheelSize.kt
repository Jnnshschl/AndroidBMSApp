package de.jnns.bmsmonitor.bike

import java.sql.Types

@ExperimentalUnsignedTypes
enum class WheelSize(val value: Byte) {
    UNKNOWN(0xFFU.toByte()),
    SIZE_10(0xE),
    SIZE_12(0x2),
    SIZE_14(0x6),
    SIZE_16(0x0),
    SIZE_18(0x4),
    SIZE_20(0x8),
    SIZE_22(0xC),
    SIZE_24(0x10),
    SIZE_26(0x14),
    SIZE_700C(0x18);

    companion object {
        fun fromByte(value: Byte) = values().first { it.value == value }
    }
}