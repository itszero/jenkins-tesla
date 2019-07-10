package db

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction

object Logs : Table() {
    val id = integer("id").autoIncrement().primaryKey()
    val timestamp = datetime("timestamp")
    val message = text("message")

    fun write(msg: String) {
        transaction {
            Logs.insert {
                it[message] = msg
            }
        }
    }
}