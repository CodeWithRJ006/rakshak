package com.rakshak.core.log

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SystemEventLogger {
    private val _logs = MutableSharedFlow<String>(replay = 50, extraBufferCapacity = 50)
    val logs: SharedFlow<String> = _logs.asSharedFlow()

    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun log(tag: String, message: String) {
        val time = dateFormat.format(Date())
        val formattedLog = "[$time] [$tag] $message"
        _logs.tryEmit(formattedLog)
    }
}
