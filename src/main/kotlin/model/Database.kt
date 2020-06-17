package model

import java.time.LocalDateTime
import org.litote.kmongo.*

data class Employee(val login: String, val password: String)
data class Message(val date: LocalDateTime, val rec: String, val res: String, val text: String)
data class Position(val position: String)

val client = KMongo.createClient()
val database = client.getDatabase("messenger")
val employees = database.getCollection<Employee>()
val messages = database.getCollection<Message>()
val positions = database.getCollection<Position>()