package com.example.mobilenetworkmapper

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class NetworkDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, "network_mapper.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {

        db.execSQL(
            """
            CREATE TABLE measurements (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                timestamp TEXT,
                operator TEXT,
                network_type TEXT,
                signal_dbm INTEGER,
                signal_quality TEXT,
                latitude REAL,
                longitude REAL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int
    ) {
        db.execSQL("DROP TABLE IF EXISTS measurements")
        onCreate(db)
    }

    fun insertMeasurement(
        timestamp: String,
        operator: String,
        networkType: String,
        signalDbm: Int,
        signalQuality: String,
        latitude: Double,
        longitude: Double
    ) {

        val db = writableDatabase

        val values = ContentValues().apply {
            put("timestamp", timestamp)
            put("operator", operator)
            put("network_type", networkType)
            put("signal_dbm", signalDbm)
            put("signal_quality", signalQuality)
            put("latitude", latitude)
            put("longitude", longitude)
        }

        db.insert("measurements", null, values)

        db.close()
    }
    fun getAllMeasurements(): List<NetworkMeasurement> {
        val measurements = mutableListOf<NetworkMeasurement>()
        val db = readableDatabase

        val cursor = db.rawQuery(
            "SELECT * FROM measurements ORDER BY id DESC",
            null
        )

        cursor.use {
            while (it.moveToNext()) {
                measurements.add(
                    NetworkMeasurement(
                        id = it.getInt(it.getColumnIndexOrThrow("id")),
                        timestamp = it.getString(
                            it.getColumnIndexOrThrow("timestamp")
                        ),
                        operator = it.getString(
                            it.getColumnIndexOrThrow("operator")
                        ),
                        networkType = it.getString(
                            it.getColumnIndexOrThrow("network_type")
                        ),
                        signalDbm = it.getInt(
                            it.getColumnIndexOrThrow("signal_dbm")
                        ),
                        signalQuality = it.getString(
                            it.getColumnIndexOrThrow("signal_quality")
                        ),
                        latitude = it.getDouble(
                            it.getColumnIndexOrThrow("latitude")
                        ),
                        longitude = it.getDouble(
                            it.getColumnIndexOrThrow("longitude")
                        )
                    )
                )
            }
        }

        db.close()
        return measurements
    }

    fun deleteMeasurement(id: Int): Boolean {
        val db = writableDatabase
        val deletedRows = db.delete("measurements", "id = ?", arrayOf(id.toString()))
        db.close()
        return deletedRows > 0
    }

    fun deleteMeasurements(ids: List<Int>): Int {
        if (ids.isEmpty()) return 0
        val db = writableDatabase
        val args = ids.map { it.toString() }.toTypedArray()
        val placeholders = ids.joinToString(",") { "?" }
        val deletedRows = db.delete("measurements", "id IN ($placeholders)", args)
        db.close()
        return deletedRows
    }
}
data class NetworkMeasurement(
    val id: Int,
    val timestamp: String,
    val operator: String,
    val networkType: String,
    val signalDbm: Int,
    val signalQuality: String,
    val latitude: Double,
    val longitude: Double
)
