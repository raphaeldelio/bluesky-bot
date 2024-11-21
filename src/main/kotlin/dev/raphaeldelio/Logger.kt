package dev.raphaeldelio

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object Logger {
    private val dateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    enum class Level {
        INFO, WARN, ERROR, DEBUG
    }

    fun log(level: Level, message: String) {
        val timestamp = LocalDateTime.now().format(dateTimeFormatter)
        println("[$timestamp] [${level.name}] $message")
    }

    fun info(message: String) = log(Level.INFO, message)
    fun warn(message: String) = log(Level.WARN, message)
    fun error(message: String) = log(Level.ERROR, message)
    fun debug(message: String) = log(Level.DEBUG, message)
}