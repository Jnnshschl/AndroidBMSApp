package de.jnns.bmsmonitor.data

import io.realm.RealmObject

open class BikeData(
    var timestamp: Long = 0L,
    var speed: Int = 0,
    var assistLevel: Int = 0
) : RealmObject()