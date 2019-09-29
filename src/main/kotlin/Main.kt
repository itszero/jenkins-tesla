import com.beust.klaxon.JsonObject
import com.beust.klaxon.Parser
import com.beust.klaxon.json
import com.github.kittinunf.fuel.core.FuelManager
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.fuel.httpPost
import db.Config
import db.Logs
import db.Metrics
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.joda.time.DateTime
import org.joda.time.Duration

data class RefreshTokenResponse(
    val accessToken: String,
    val refreshToken: String
)

data class CarOverview(
    val id: String,
    val name: String,
    val state: String
)

data class CarStatus(
    val mileage: Int,
    val battery: Int,
    val lat: Double,
    val lon: Double,
    val charging: Boolean,
    val chargingPower: Int,
    val version: String,
    val state: String,
    val fullData: String
)

class UnauthorizedException : Exception("API Unauthorized")

fun refreshToken(refreshToken: String): RefreshTokenResponse {
    val req = "/oauth/token".httpPost()
        .body(
            json {
                obj(
                    "grant_type" to "refresh_token",
                    "client_id" to "81527cff06843c8634fdc09e8ac0abefb46ac849f38fe1e431c2ef2106796384",
                    "client_secret" to "c7257eb71a564034f9419ee651c7d0e5f7aa6bfbd18bafb5c5c033b093bb2fa3",
                    "refresh_token" to refreshToken
                )
            }.toJsonString()
        )
    req.headers["Content-Type"] = "application/json"
    val (_, _, refreshResult) = req.responseString()

    val refreshResponseData = Parser().parse(StringBuilder(refreshResult.get())) as JsonObject
    return RefreshTokenResponse(
        refreshResponseData.string("access_token")!!,
        refreshResponseData.string("refresh_token")!!
    )
}

fun getVehicle(token: String, vin: String): CarOverview {
    val (_, resp, result) = "/api/1/vehicles".httpGet()
        .header("Authorization" to "Bearer $token")
        .responseString()

    when (resp.statusCode) {
        200 -> {
            val data = Parser().parse(StringBuilder(result.get())) as JsonObject
            val vehicleObj = data.array<JsonObject>("response")!!.filter { vehicle: JsonObject ->
                vehicle.string("vin")!! == vin
            }.first()

            return CarOverview(
                id = vehicleObj.long("id")!!.toString(),
                name = vehicleObj.string("display_name")!!,
                state = vehicleObj.string("state")!!
            )
        }
        401 -> {
            throw UnauthorizedException()
        }
        else -> {
            throw result.component2()!!
        }
    }
}

fun getVehicleStatus(token: String, vehicle: CarOverview): CarStatus {
    val (_, _, chargingStateResult) = "/api/1/vehicles/${vehicle.id}/data_request/charge_state".httpGet()
        .header("Authorization" to "Bearer $token")
        .responseString()
    val (_, _, vehicleStateResult) = "/api/1/vehicles/${vehicle.id}/data_request/vehicle_state".httpGet()
        .header("Authorization" to "Bearer $token")
        .responseString()
    val (_, _, driveStateResult) = "/api/1/vehicles/${vehicle.id}/data_request/drive_state".httpGet()
        .header("Authorization" to "Bearer $token")
        .responseString()

    val chargingStateData = Parser().parse(StringBuilder(chargingStateResult.get())) as JsonObject
    val vehicleStateData = Parser().parse(StringBuilder(vehicleStateResult.get())) as JsonObject
    val driveStateData = Parser().parse(StringBuilder(driveStateResult.get())) as JsonObject

    val currentMileage = vehicleStateData.obj("response")!!.int("odometer")!!
    val chargingState = chargingStateData.obj("response")!!.string("charging_state")!!
    val charging = chargingState == "Charging"

    return CarStatus(
        mileage = currentMileage,
        battery = chargingStateData.obj("response")!!.int("battery_level")!!,
        lat = driveStateData.obj("response")!!.double("latitude")!!,
        lon = driveStateData.obj("response")!!.double("longitude")!!,
        charging = charging,
        chargingPower = chargingStateData.obj("response")!!.int("charger_power")!!,
        version = vehicleStateData.obj("response")!!.string("car_version")!!,
        state = vehicle.state,
        fullData = json {
            obj(
                "charging_state" to chargingStateData.obj("response")!!,
                "vehicle_state" to vehicleStateData.obj("response")!!,
                "drive_state" to driveStateData.obj("response")!!
            )
        }.toJsonString()
    )
}

fun write(car: CarOverview, info: CarStatus) {
    transaction {
        Metrics.insert {
            it[vehicleId] = car.id
            it[mileage] = info.mileage
            it[battery] = info.battery.toDouble()
            it[lat] = info.lat
            it[lon] = info.lon
            it[charging] = info.charging
            it[chargingPower] = info.chargingPower
            it[version] = info.version
            it[state] = info.state
            it[fullData] = info.fullData
            it[timestamp] = DateTime.now()
        }
    }
}

fun annotateCharging(charging: Boolean) {
    fun annotate(msg: String) {
        val req = "${System.getenv("GRAFANA_HOST")}/api/annotations".httpPost()
            .header(
                "Content-Type" to "application/json",
                "Authorization" to "Bearer ${System.getenv("GRAFANA_TOKEN")}"
            )
            .body(json {
                obj(
                    "dashboardId" to 1,
                    "panelId" to 10,
                    "time" to System.currentTimeMillis(),
                    "isRegion" to false,
                    "text" to msg
                )
            }.toJsonString())
        req.headers["Content-Type"] = "application/json"
        val (_, _, refreshResult) = req.responseString()
        println("annotating: $msg")
        println(refreshResult)
    }

    val wasCharging = transaction {
        val lastStatus = Metrics.selectAll().lastOrNull()
        if (lastStatus != null) {
            lastStatus[Metrics.charging]
        } else {
            charging // return the same charging state to no-op this function
        }
    }

    if (wasCharging && !charging) {
        annotate("stopped charging")
    } else if (!wasCharging && charging) {
        annotate("started charging")
    }
}

fun main() {
    Database.connect(
        "jdbc:postgresql://${System.getenv("PGHOST")}/tesla",
        "org.postgresql.Driver",
        System.getenv("PGUSER"),
        System.getenv("PGPASSWORD")
    )
    transaction {
        addLogger(StdOutSqlLogger)
    }
    FuelManager.instance.basePath = "https://owner-api.teslamotors.com"
    FuelManager.instance.removeAllResponseInterceptors()

    var triedRefresh = false
    val tryToSleepLength = 15

    while (true) {
        try {
            var config = Config.get()
            val token = config[Config.accessToken]
            val vin = config[Config.vin]

            println("Getting vehicles...")
            val vehicle = getVehicle(token, vin)
            println(vehicle)

            Logs.write("Vehicle Overview: $vehicle")
            if (vehicle.state == "asleep") {
                // If we have been sleeping, no need to try to sleep now
                transaction {
                    Config.update({ Config.vin eq vin }) {
                        it[tryToSleepSince] = null
                    }
                }

                // Continue to wake up the car if it's more than 6 hours since last ping
                val shouldContinue = transaction {
                    val lastStatus =
                        Metrics.select { (Metrics.state eq "online") }.sortedByDescending { Metrics.timestamp }
                            .lastOrNull()
                    if (lastStatus != null) {
                        val lastAwakeTime = lastStatus[Metrics.timestamp]
                        val duration = Duration(lastAwakeTime, null)

                        duration.standardHours > 6
                    } else {
                        false
                    }
                }

                if (!shouldContinue) {
                    Logs.write("The car is sleeping, exit.")
                    println("Shhhh the car is sleeping. Good bye.")
                    System.exit(0)
                } else {
                    Logs.write("The car has slept for 6 hours, wake it up")
                }
            } else if (vehicle.state == "online") {
                // If it just wake up, block sleep mode for 30 mins
                transaction {
                    val lastStatus =
                        Metrics.selectAll().sortedByDescending { Metrics.timestamp }
                            .lastOrNull()
                    if (lastStatus != null && lastStatus[Metrics.state] == "asleep") {
                        Logs.write("The car just woke up. Blocking sleep for 30mins")
                        Config.update({ Config.vin eq vin }) {
                            it[blockSleepUntil] = DateTime.now().plusMinutes(30)
                            it[tryToSleepSince] = null
                        }
                    }
                }
            }

            config = Config.get()
            val blockSleepRemaining = if (config[Config.blockSleepUntil] != null) {
                val minsLeft = Duration(null, config[Config.blockSleepUntil]).standardMinutes
                // if it has expired, clean it up
                if (minsLeft < 0) {
                    transaction {
                        Config.update({ Config.vin eq vin }) {
                            it[blockSleepUntil] = null
                        }
                    }
                }
                Math.max(minsLeft, 0)
            } else {
                0
            }

            val tryToSleepDuration =
                if (config[Config.tryToSleepSince] != null) {
                    Duration(config[Config.tryToSleepSince], null).standardMinutes
                } else {
                    null
                }

            if (tryToSleepDuration != null) {
                if (tryToSleepDuration >= tryToSleepLength) {
                    Logs.write("Cleaning up sleep mode")
                    transaction {
                        Config.update({ Config.vin eq vin }) {
                            it[tryToSleepSince] = null
                        }
                    }
                } else if (tryToSleepDuration >= 0) {
                    Logs.write("Trying to sleep for ${tryToSleepLength - tryToSleepDuration} mins.")
                    println("Trying to sleep... ${tryToSleepLength - tryToSleepDuration} mins. remaining")
                    System.exit(0)
                }
            }

            val vehicleStatus = getVehicleStatus(token, vehicle)
            val debugVehicleStatus = vehicleStatus.copy(fullData = "<REDACTED>")
            println(debugVehicleStatus)
            Logs.write("Status: $debugVehicleStatus")
            annotateCharging(vehicleStatus.charging)

            // If the car is charging, actively extend sleep block. Otherwise we'll try to let it sleep
            // right after unplugging due to no movement.
            if (vehicleStatus.charging) {
                Logs.write("The car is charging. Keep blocking sleep for 30mins")
                transaction {
                    Config.update({ Config.vin eq vin }) {
                        it[blockSleepUntil] = DateTime.now().plusMinutes(30)
                        it[tryToSleepSince] = null
                    }
                }
            }

            // If the current mileage is the same as 10 minutes ago, let's try to allow the car to sleep
            val mileageWhileAgo = transaction {
                val lastStatus = Metrics.select {
                    (Metrics.state eq "online") and (Metrics.timestamp lessEq DateTime.now().minusMinutes(10))
                }.sortedByDescending { Metrics.timestamp }
                    .lastOrNull()
                if (lastStatus != null) {
                    lastStatus[Metrics.mileage]
                } else {
                    0
                }
            }

            // Only start sleep mode if:
            //   1. The car has not moved in the past 10 mins
            //   2. Not currently trying to sleep
            //   3. Sleep mode is not blocked
            //   4. The car is not being charged
            if (vehicleStatus.mileage == mileageWhileAgo && tryToSleepDuration == null && blockSleepRemaining <= 0 && !vehicleStatus.charging) {
                Logs.write("Turn on sleep mode")
                transaction {
                    Config.update({ Config.vin eq vin }) {
                        it[tryToSleepSince] = DateTime.now()
                    }
                }
            } else if (blockSleepRemaining > 0) {
                Logs.write("Sleep mode is being blocked for $blockSleepRemaining mins")
                println("Sleep mode is blocked for $blockSleepRemaining mins...")
            }

            write(vehicle, vehicleStatus)
            break
        } catch (e: UnauthorizedException) {
            if (!triedRefresh) {
                val config = Config.get()
                val vin = config[Config.vin]
                val tokenRefresh = config[Config.refreshToken]

                Logs.write("Refreshing token")
                println("Refresh token...")
                val (newAccessToken, newRefreshToken) = refreshToken(tokenRefresh)
                transaction {
                    Config.update({ Config.vin eq vin }) {
                        it[accessToken] = newAccessToken
                        it[refreshToken] = newRefreshToken
                    }
                }
                triedRefresh = true
            } else {
                Logs.write("ERROR: Token refresh failed")
                println("Unable to refresh token! Exit")
                break
            }
        }
    }
}
