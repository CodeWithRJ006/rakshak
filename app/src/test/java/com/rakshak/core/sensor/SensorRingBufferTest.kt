package com.rakshak.core.sensor

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

class SensorRingBufferTest {

    @Test
    fun `buffer never exceeds fixed size`() {
        val buffer = SensorRingBuffer(5)
        
        // Add 10 items
        for (i in 1..10) {
            buffer.add(i.toLong(), floatArrayOf(i.toFloat(), 0f, 0f))
        }

        // Buffer size should be capped at 5
        assertEquals(5, buffer.getCurrentSize())
        
        // Flow should also have 5 items
        val snapshot = buffer.flow.value
        assertEquals(5, snapshot.size)
    }

    @Test
    fun `correct most-recent order`() {
        val buffer = SensorRingBuffer(3)
        
        // Add 5 items
        for (i in 1..5) {
            buffer.add(i.toLong(), floatArrayOf(i.toFloat(), 0f, 0f))
        }

        val snapshot = buffer.flow.value
        
        // Should contain items 3, 4, 5 in that order
        assertEquals(3L, snapshot[0].timestamp)
        assertEquals(4L, snapshot[1].timestamp)
        assertEquals(5L, snapshot[2].timestamp)
        
        // Verify values are correct and independent
        assertEquals(3f, snapshot[0].values[0])
        assertEquals(5f, snapshot[2].values[0])
    }
}
