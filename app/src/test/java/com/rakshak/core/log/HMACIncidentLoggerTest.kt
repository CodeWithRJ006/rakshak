package com.rakshak.core.log

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class HMACIncidentLoggerTest {

    private class DummyKeyProvider(private val securityLevel: String = "Hardware-backed") : SecretKeyProvider {
        private val key = KeyGenerator.getInstance("HmacSHA256").generateKey()
        override fun getOrCreateKey(): SecretKey = key
        override fun getSecurityLevel(): String = securityLevel
    }

    private class InMemoryIncidentDatabase : IncidentDatabase {
        val records = mutableListOf<IncidentRecord>()
        var nextId = 1L

        override fun getLastMac(): String {
            return records.lastOrNull()?.mac ?: "0".repeat(64)
        }

        override fun insert(timestamp: Long, payloadJson: String, prevMac: String, mac: String) {
            records.add(IncidentRecord(nextId++, timestamp, payloadJson, prevMac, mac))
        }

        override fun getAllRecords(): List<IncidentRecord> {
            return records.toList()
        }
    }

    private lateinit var db: InMemoryIncidentDatabase
    private lateinit var logger: HMACIncidentLogger

    @Before
    fun setup() {
        db = InMemoryIncidentDatabase()
        logger = HMACIncidentLogger(db, DummyKeyProvider())
    }

    @Test
    fun testEmptyLogVerifiesCorrectly() = runBlocking {
        val result = logger.verifyChain()
        assertTrue(result.isIntact)
        assertEquals("Log is empty", result.detail)
    }

    @Test
    fun testSingleIncidentLoggingAndVerification() = runBlocking {
        logger.logIncident("""{"type":"crash"}""")
        
        val result = logger.verifyChain()
        assertTrue("Verification should pass for 1 incident", result.isIntact)
    }

    @Test
    fun testMultipleIncidentsChainCorrectly() = runBlocking {
        logger.logIncident("""{"type":"crash1"}""")
        logger.logIncident("""{"type":"crash2"}""")
        logger.logIncident("""{"type":"crash3"}""")
        
        val result = logger.verifyChain()
        assertTrue("Chain verification failed", result.isIntact)
        assertTrue(result.detail.contains("3 records verified"))
    }

    @Test
    fun testTamperingWithDataBreaksChain() = runBlocking {
        logger.logIncident("""{"type":"crash1"}""")
        logger.logIncident("""{"type":"crash2"}""")
        
        // Tamper with payload
        val tamperedRecord = db.records[0].copy(payloadJson = """""{"type":"tampered"}""""")
        db.records[0] = tamperedRecord
        
        val result = logger.verifyChain()
        assertFalse(result.isIntact)
        assertEquals(1L, result.failedId)
        assertTrue(result.detail.contains("MAC verification failed at ID 1"))
    }

    @Test
    fun testDeletingARecordBreaksChainForSubsequentRecords() = runBlocking {
        logger.logIncident("""{"type":"crash1"}""")
        logger.logIncident("""{"type":"crash2"}""")
        logger.logIncident("""{"type":"crash3"}""")
        
        db.records.removeAt(1) // Delete the second record (ID 2)
        
        val result = logger.verifyChain()
        assertFalse("Chain should be broken when record is deleted", result.isIntact)
        assertEquals(3L, result.failedId)
        assertTrue(result.detail.contains("Previous MAC mismatch"))
    }
    
    @Test
    fun testPayloadWithPipeCharacter() = runBlocking {
        // Ensures that if json contains pipe, it doesn't break formatting
        logger.logIncident("""{"type":"crash|with|pipe"}""")
        
        val result = logger.verifyChain()
        assertTrue("Verification should pass for payload with pipes", result.isIntact)
    }

    @Test
    fun testSecurityLevelTrustedEnvironment() {
        val customLogger = HMACIncidentLogger(db, DummyKeyProvider("Hardware-backed (TRUSTED_ENVIRONMENT)"))
        assertEquals("Hardware-backed (TRUSTED_ENVIRONMENT)", customLogger.getSecurityLevel())
    }

    @Test
    fun testSecurityLevelStrongbox() {
        val customLogger = HMACIncidentLogger(db, DummyKeyProvider("Hardware-backed (STRONGBOX)"))
        assertEquals("Hardware-backed (STRONGBOX)", customLogger.getSecurityLevel())
    }

    @Test
    fun testSecurityLevelSoftwareUnknown() {
        val customLogger = HMACIncidentLogger(db, DummyKeyProvider("Keystore protected"))
        assertEquals("Keystore protected", customLogger.getSecurityLevel())
    }
}




