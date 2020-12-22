package de.jnns.bmsmonitor.data

import io.realm.RealmList
import io.realm.RealmObject

open class BatteryData(
    var timestamp: Long = 0L,
    var bleName: String = "",
    var bleAddress: String = "",
    var current: Float = 0.0f,
    var totalCapacity: Float = 0.0f,
    var currentCapacity: Float = 0.0f,
    var cycles: Int = 0,
    var temperatureCount: Int = 0,
    var cellCount: Int = 0,
    var temperatures: RealmList<Float> = RealmList<Float>(),
    var cellVoltages: RealmList<Float> = RealmList<Float>()
) : RealmObject() {

    val voltage: Float
        get() {
            return cellVoltages.sum()
        }

    val avgTemperature: Float
        get() {
            return temperatures.average().toFloat()
        }

    val maxTemperature: Float
        get() {
            return temperatures.maxOrNull()!!
        }

    val percentage: Float
        get() {
            return currentCapacity / totalCapacity
        }

    val watthours: Float
        get() {
            return currentCapacity * voltage
        }

    val totalWatthours: Float
        get() {
            return totalCapacity * voltage
        }

    val power: Float
        get() {
            return current * voltage
        }
}