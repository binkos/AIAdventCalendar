package com.aiadventcalendar.common.services

import com.aiadventcalendar.common.AgentService
import com.aiadventcalendar.common.config.SchedulerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
private val logger = System.getLogger("WeatherForecastService")

/**
 * Service that periodically collects weather forecasts.
 * Runs every configured interval (default 60 minutes).
 */
class WeatherForecastService(
    private val agentService: AgentService
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var forecastJob: Job? = null

    /**
     * Starts the scheduled forecast collection service.
     */
    fun start() {
        if (!SchedulerConfig.scheduledTaskEnabled) {
            logger.log(System.Logger.Level.INFO, "Scheduled tasks are disabled. WeatherForecastService not started.")
            return
        }

        logger.log(System.Logger.Level.INFO, 
            "Starting WeatherForecastService - collecting forecasts every ${SchedulerConfig.forecastIntervalMinutes} minutes for location: ${SchedulerConfig.forecastLocation}"
        )

        forecastJob = scope.launch {
            while (isActive) {
                try {
                    collectForecast()
                } catch (e: Exception) {
                    logger.log(System.Logger.Level.ERROR, "Error collecting forecast: ${e.message}", e)
                }

                delay(SchedulerConfig.forecastIntervalMinutes * 60 * 1000)
            }
        }
    }

    /**
     * Stops the scheduled forecast collection service.
     */
    fun stop() {
        forecastJob?.cancel()
        logger.log(System.Logger.Level.INFO, "WeatherForecastService stopped")
    }

    private suspend fun collectForecast() = withContext(Dispatchers.IO) {
        logger.log(System.Logger.Level.INFO, "Collecting forecast for ${SchedulerConfig.forecastLocation}")

        try {
            // Ensure agent exists
            agentService.createOrGetAgent(SchedulerConfig.forecastAgentId)

            // Get or create chat for forecasts
            val chats = agentService.getChatHistory(SchedulerConfig.forecastAgentId)
            val forecastChat = chats.firstOrNull() ?: agentService.createChat(SchedulerConfig.forecastAgentId)

            // Request forecast - the agent will use MCP tools to get and store the forecast
            // For California, use state code "CA" for alerts
            val locationQuery = when {
                SchedulerConfig.forecastLocation.equals("California", ignoreCase = true) -> "CA"
                else -> SchedulerConfig.forecastLocation
            }
            val message = "Get weather forecast alerts for ${SchedulerConfig.forecastLocation} (state code: $locationQuery). Use the get_alerts tool with state code '$locationQuery' to retrieve alerts. The alerts will be automatically stored."
            val response = agentService.processMessage(
                agentId = SchedulerConfig.forecastAgentId,
                chatId = forecastChat.id,
                userMessage = message,
                temperature = 0.7
            )

            logger.log(System.Logger.Level.INFO, "Forecast collected successfully: ${response.take(200)}")
        } catch (e: Exception) {
            logger.log(System.Logger.Level.ERROR, "Failed to collect forecast: ${e.message}", e)
            throw e
        }
    }
}

