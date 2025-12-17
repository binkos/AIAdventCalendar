package com.aiadventcalendar.mcp

import com.aiadventcalendar.mcp.models.StoredForecast
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Thread-safe storage for weather forecasts.
 * Stores forecasts in a JSON file with timestamps.
 */
class WeatherStorage(private val storagePath: String = "weather_forecasts.json") {
    
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    
    private val mutex = Mutex()
    
    @Serializable
    private data class StorageFile(
        val forecasts: MutableList<StoredForecast> = mutableListOf()
    )
    
    /**
     * Stores a forecast with the current timestamp.
     */
    suspend fun storeForecast(location: String, forecast: String, timestamp: Long? = null): Unit = mutex.withLock {
        val forecasts = loadForecasts()
        val now = System.currentTimeMillis() / 1000
        val storedForecast = StoredForecast(
            timestamp = timestamp ?: now,
            location = location,
            forecast = forecast,
            receivedAt = now
        )
        forecasts.add(storedForecast)
        saveForecasts(forecasts)
    }
    
    /**
     * Retrieves all stored forecasts.
     */
    suspend fun getAllStoredForecasts(): List<StoredForecast> = mutex.withLock {
        loadForecasts()
    }
    
    /**
     * Retrieves forecasts within a specific time range.
     */
    suspend fun getForecastsByTimeRange(startTime: Long, endTime: Long): List<StoredForecast> = mutex.withLock {
        loadForecasts().filter { it.timestamp >= startTime && it.timestamp <= endTime }
    }
    
    /**
     * Clears all stored forecasts.
     */
    suspend fun clearAllForecasts(): Unit = mutex.withLock {
        saveForecasts(mutableListOf())
    }
    
    private fun loadForecasts(): MutableList<StoredForecast> {
        val file = File(storagePath)
        return if (file.exists() && file.length() > 0) {
            try {
                val content = file.readText()
                val storageFile = json.decodeFromString<StorageFile>(content)
                storageFile.forecasts
            } catch (e: Exception) {
                mutableListOf()
            }
        } else {
            mutableListOf()
        }
    }
    
    private fun saveForecasts(forecasts: MutableList<StoredForecast>) {
        val file = File(storagePath)
        val storageFile = StorageFile(forecasts)
        val content = json.encodeToString(storageFile)
        file.writeText(content)
    }
}

