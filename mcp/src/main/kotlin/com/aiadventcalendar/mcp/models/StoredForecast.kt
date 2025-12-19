package com.aiadventcalendar.mcp.models

import kotlinx.serialization.Serializable

/**
 * Data model for storing weather forecasts with timestamp information.
 */
@Serializable
data class StoredForecast(
    val timestamp: Long,
    val location: String,
    val forecast: String, // Store as JSON string for simplicity
    val receivedAt: Long
)

