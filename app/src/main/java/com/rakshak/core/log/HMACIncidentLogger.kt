package com.rakshak.core.log

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory

// Interface to allow pure JVM testing of HMAC logic without AndroidKeyStore
interface SecretKeyProvider {
    fun getOrCreateKey(): SecretKey
    fun getSecurityLevel(): String
}

class AndroidKeystoreProvider : SecretKeyProvider {
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    
    override fun getSecurityLevel(): String {
        return try {
            val key = getOrCreateKey()
            val factory = SecretKeyFactory.getInstance(key.algorithm, "AndroidKeyStore")
            val keyInfo = factory.getKeySpec(key, KeyInfo::class.java) as KeyInfo
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                when (keyInfo.securityLevel) {
                    KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT,
                    KeyProperties.SECURITY_LEVEL_STRONGBOX -> "Hardware-backed"
                    else -> "Keystore protected"
                }
            } else {
                if (keyInfo.isInsideSecureHardware) "Hardware-backed" else "Keystore protected"
            }
        } catch (e: Exception) {
            Log.e("HMACLogger", "Failed to get security level", e)
            "Keystore protected"
        }
    }

    override fun getOrCreateKey(): SecretKey {
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, "AndroidKeyStore")
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            ).build()
            keyGenerator.init(spec)
            keyGenerator.generateKey()
        }
        return keyStore.getKey(KEY_ALIAS, null) as SecretKey
    }
    
    companion object {
        private const val KEY_ALIAS = "rakshak_hmac_key"
    }
}

data class IncidentRecord(
    val id: Long,
    val timestamp: Long,
    val payloadJson: String,
    val prevMac: String,
    val mac: String
)

interface IncidentDatabase {
    fun getLastMac(): String
    fun insert(timestamp: Long, payloadJson: String, prevMac: String, mac: String)
    fun getAllRecords(): List<IncidentRecord>
}

class AndroidSQLiteIncidentDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION), IncidentDatabase {
    override fun onCreate(db: SQLiteDatabase) {
        val createTable = "CREATE TABLE $TABLE_NAME (" +
                "$COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT," +
                "$COLUMN_TIMESTAMP INTEGER," +
                "$COLUMN_PAYLOAD_JSON TEXT," +
                "$COLUMN_PREV_MAC TEXT," +
                "$COLUMN_MAC TEXT)"
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // No upgrades needed
    }

    override fun getLastMac(): String {
        var previousMac = "0".repeat(64)
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT $COLUMN_MAC FROM $TABLE_NAME ORDER BY $COLUMN_ID DESC LIMIT 1", null)
        if (cursor.moveToFirst()) {
            previousMac = cursor.getString(0) ?: "0".repeat(64)
        }
        cursor.close()
        return previousMac
    }

    override fun insert(timestamp: Long, payloadJson: String, prevMac: String, mac: String) {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_TIMESTAMP, timestamp)
            put(COLUMN_PAYLOAD_JSON, payloadJson)
            put(COLUMN_PREV_MAC, prevMac)
            put(COLUMN_MAC, mac)
        }
        db.insert(TABLE_NAME, null, values)
    }

    override fun getAllRecords(): List<IncidentRecord> {
        val records = mutableListOf<IncidentRecord>()
        val db = this.readableDatabase
        val cursor = db.query(TABLE_NAME, null, null, null, null, null, "$COLUMN_ID ASC")
        
        val idIdx = cursor.getColumnIndexOrThrow(COLUMN_ID)
        val tsIdx = cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP)
        val payloadIdx = cursor.getColumnIndexOrThrow(COLUMN_PAYLOAD_JSON)
        val prevMacIdx = cursor.getColumnIndexOrThrow(COLUMN_PREV_MAC)
        val macIdx = cursor.getColumnIndexOrThrow(COLUMN_MAC)

        while (cursor.moveToNext()) {
            records.add(
                IncidentRecord(
                    id = cursor.getLong(idIdx),
                    timestamp = cursor.getLong(tsIdx),
                    payloadJson = cursor.getString(payloadIdx) ?: "",
                    prevMac = cursor.getString(prevMacIdx) ?: "",
                    mac = cursor.getString(macIdx) ?: ""
                )
            )
        }
        cursor.close()
        return records
    }

    companion object {
        const val DATABASE_VERSION = 1
        const val DATABASE_NAME = "IncidentLog.db"
        const val TABLE_NAME = "incidents"
        const val COLUMN_ID = "id"
        const val COLUMN_TIMESTAMP = "timestamp"
        const val COLUMN_PAYLOAD_JSON = "payload_json"
        const val COLUMN_PREV_MAC = "prev_mac"
        const val COLUMN_MAC = "mac"
    }
}

class HMACIncidentLogger(
    private val database: IncidentDatabase,
    private val keyProvider: SecretKeyProvider = AndroidKeystoreProvider()
) {
    constructor(context: Context) : this(AndroidSQLiteIncidentDatabase(context))

    fun getSecurityLevel(): String = keyProvider.getSecurityLevel()

    private fun generateMac(timestamp: Long, payloadJson: String, prevMac: String): String {
        val canonicalInput = timestamp.toString() + payloadJson + prevMac
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(keyProvider.getOrCreateKey())
        val macBytes = mac.doFinal(canonicalInput.toByteArray(Charsets.UTF_8))
        return macBytes.joinToString("") { "%02x".format(it) }
    }

    suspend fun logIncident(payloadJson: String) = withContext(Dispatchers.IO) {
        try {
            val timestamp = System.currentTimeMillis()
            val previousMac = database.getLastMac()
            val hmacHex = generateMac(timestamp, payloadJson, previousMac)
            
            database.insert(timestamp, payloadJson, previousMac, hmacHex)
            Log.d(TAG, "Incident logged securely to SQLite")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log incident: $e")
        }
    }

    data class VerificationResult(
        val isIntact: Boolean,
        val detail: String,
        val failedId: Long = -1
    )

    suspend fun verifyChain(): VerificationResult = withContext(Dispatchers.IO) {
        try {
            val records = database.getAllRecords()
            
            if (records.isEmpty()) {
                return@withContext VerificationResult(true, "Log is empty")
            }

            var previousMac = "0".repeat(64)
            var verifiedCount = 0

            for (record in records) {
                if (record.prevMac != previousMac) {
                    return@withContext VerificationResult(false, "Chain broken at ID ${record.id}: Previous MAC mismatch", record.id)
                }

                val expectedMac = generateMac(record.timestamp, record.payloadJson, record.prevMac)
                if (expectedMac != record.mac) {
                    return@withContext VerificationResult(false, "MAC verification failed at ID ${record.id}", record.id)
                }

                previousMac = record.mac
                verifiedCount++
            }
            
            VerificationResult(true, "Chain intact. $verifiedCount records verified.")
        } catch (e: Exception) {
            VerificationResult(false, "Verification failed with exception: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "HMACLogger"
    }
}

