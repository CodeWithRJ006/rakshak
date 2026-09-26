package com.rakshak.core.sensor

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.ArrayDeque

/**
 * Data class representing a single sensor reading.
 * Uses a cloned FloatArray to prevent mutation bugs from SensorEvent.
 */
data class SensorData(
    val timestamp: Long,
    val values: FloatArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as SensorData
        if (timestamp != other.timestamp) return false
        if (!values.contentEquals(other.values)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = timestamp.hashCode()
        result = 31 * result + values.contentHashCode()
        return result
    }
}

/**
 * Thread-safe fixed-size ring buffer for sensor events.
 * 
 * At SENSOR_DELAY_GAME (~50Hz), a 4-second buffer requires capacity = 200.
 */
class SensorRingBuffer(private val capacity: Int) {
    private val buffer = ArrayDeque<SensorData>(capacity)
    
    // We expose the buffer as a StateFlow so collectors get the latest snapshot immediately.
    // Note: Emitting every single 20ms update to StateFlow might cause some backpressure/dropping
    // if collectors are slow, but StateFlow correctly conflates updates.
    private val _flow = MutableStateFlow<List<SensorData>>(emptyList())
    val flow: StateFlow<List<SensorData>> = _flow.asStateFlow()

    @Synchronized
    fun add(timestamp: Long, values: FloatArray) {
        if (buffer.size >= capacity) {
            buffer.removeFirst()
        }
        buffer.addLast(SensorData(timestamp, values.clone()))
        // Emit the current snapshot
        _flow.value = buffer.toList()
    }

    @Synchronized
    fun clear() {
        buffer.clear()
        _flow.value = emptyList()
    }
    
    @Synchronized
    fun getCurrentSize(): Int = buffer.size
}
