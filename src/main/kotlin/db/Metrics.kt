package db

import org.jetbrains.exposed.sql.Table

object Metrics : Table() {
    val id = integer("id").autoIncrement().primaryKey()
    val vehicleId = varchar("vehicle_id", 64)
    val state = varchar("state", 64)
    val mileage = integer("mileage")
    val battery = double("battery")
    val lat = double("lat")
    val lon = double("lon")
    val charging = bool("charging")
    val chargingPower = integer("charging_power")
    val version = text("version")
    val timestamp = datetime("timestamp")
    val fullData = jsonb("full_data")
}