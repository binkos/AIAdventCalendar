package com.aiadventcalendar.common.config

/**
 * Configuration for scheduled weather forecast tasks.
 * Values can be overridden via environment variables.
 */
object SchedulerConfig {
    val forecastIntervalMinutes: Long = System.getenv("FORECAST_INTERVAL_MINUTES")?.toLongOrNull() ?: 60L
    val summaryIntervalHours: Long = System.getenv("SUMMARY_INTERVAL_HOURS")?.toLongOrNull() ?: 3L
    val forecastLocation: String = System.getenv("FORECAST_LOCATION") ?: "California"
    val forecastAgentId: String = System.getenv("FORECAST_AGENT_ID") ?: "weather-collector"
    val summaryAgentId: String = System.getenv("SUMMARY_AGENT_ID") ?: "weather-summarizer"
    val oliverAgentId: String = System.getenv("OLIVER_AGENT_ID") ?: "Oliver"
    val oliverChatId: String = System.getenv("OLIVER_CHAT_ID") ?: "-1"
    val scheduledTaskEnabled: Boolean = System.getenv("SCHEDULED_TASK_ENABLED")?.toBoolean() ?: true
}

