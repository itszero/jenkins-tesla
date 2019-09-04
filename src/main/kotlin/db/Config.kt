package db

import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

object Config : Table() {
    val vin = varchar("vin", 64)
    val accessToken = varchar("access_token", 64)
    val refreshToken = varchar("refresh_token", 64)
    val tryToSleepSince = datetime("try_to_sleep_time").nullable()
    val blockSleepUntil = datetime("block_sleep_until").nullable()

    fun get(): ResultRow {
        return transaction {
            Config.selectAll().limit(1).first()
        }
    }
}