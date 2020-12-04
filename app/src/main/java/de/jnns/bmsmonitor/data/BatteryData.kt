package de.jnns.bmsmonitor.data

class BatteryData {
    var current = 0.0f

    var totalCapacity = 0.0f
    var currentCapacity = 0.0f

    var cycles: Int = 0

    var temperatureCount: Int = 0
    var cellCount = 0

    lateinit var temperatures: FloatArray
    lateinit var cellVoltages: FloatArray

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
            return temperatures.max()!!
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