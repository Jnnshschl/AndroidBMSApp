package de.jnns.bmsmonitor.bms

import io.realm.RealmList
import java.nio.ByteBuffer
import java.nio.ByteOrder

class BmsCellInfoResponse(bytes: ByteArray) {
    var command: Int = 0
    var status: Int = 0
    var cellCount: Int = 0
    var cellVoltages: RealmList<Float>

    init {
        command = bytes[1].toInt()
        status = bytes[2].toInt()
        cellCount = bytes[3].toInt() / 2
        cellVoltages = RealmList<Float>()

        for (i in 0 until cellCount) {
            cellVoltages.add(bytesToShort(bytes[4 + (i * 2)], bytes[4 + (i * 2) + 1]) / 1000.0f)
        }
    }

    private fun bytesToShort(h: Byte, l: Byte, order: ByteOrder = ByteOrder.BIG_ENDIAN): Short {
        val byteBuffer: ByteBuffer = ByteBuffer.allocateDirect(2)

        byteBuffer.order(order)
        byteBuffer.put(h)
        byteBuffer.put(l)
        byteBuffer.flip()

        return byteBuffer.short
    }
}