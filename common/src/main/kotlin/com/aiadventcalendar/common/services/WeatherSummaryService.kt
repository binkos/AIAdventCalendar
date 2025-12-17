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
import java.util.logging.Logger

private val logger = Logger.getLogger("WeatherSummaryService")

/**
 * Service that periodically summarizes stored weather forecasts and sends to Oliver agent.
 * Runs every configured interval (default 3 hours).
 */
class WeatherSummaryService(
    private val agentService: AgentService
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var summaryJob: Job? = null

    /**
     * Starts the scheduled summary service.
     */
    fun start() {
        if (!SchedulerConfig.scheduledTaskEnabled) {
            logger.info("Scheduled tasks are disabled. WeatherSummaryService not started.")
            return
        }

        logger.info("Starting WeatherSummaryService - summarizing forecasts every ${SchedulerConfig.summaryIntervalHours} hours")

        summaryJob = scope.launch {
            while (isActive) {
                try {
                    summarizeAndSend()
                } catch (e: Exception) {
                    logger.severe("Error in summary service: ${e.message}")
                    e.printStackTrace()
                }

                delay(SchedulerConfig.summaryIntervalHours * 60 * 60 * 1000)
            }
        }
    }

    /**
     * Stops the scheduled summary service.
     */
    fun stop() {
        summaryJob?.cancel()
        logger.info("WeatherSummaryService stopped")
    }

    private suspend fun summarizeAndSend() = withContext(Dispatchers.IO) {
        logger.info("Starting summary process")

        try {
            // Ensure summary agent exists
            agentService.createOrGetAgent(SchedulerConfig.summaryAgentId)

            // Get or create chat for summaries
            val summaryChats = agentService.getChatHistory(SchedulerConfig.summaryAgentId)
            val summaryChat = summaryChats.firstOrNull() ?: agentService.createChat(SchedulerConfig.summaryAgentId)

            // Step 1: Request all stored forecasts via MCP tool
            // The agent will use get_all_forecasts MCP tool to retrieve data
            logger.info("Requesting all stored forecasts from MCP")
            val dataRequestMessage = "Use the get_all_forecasts tool to retrieve all stored weather forecasts and return the data."
            val storedDataResponse = agentService.processMessage(
                agentId = SchedulerConfig.summaryAgentId,
                chatId = summaryChat.id,
                userMessage = dataRequestMessage,
                temperature = 0.7
            )

            // Step 2: Summarize the data
            logger.info("Summarizing weather forecast data")
            val summaryMessage = """
                Analyze and summarize the following weather forecast data. 
                Provide a concise summary highlighting key trends, alerts, and important information.
                
                Forecast Data:
                $storedDataResponse
            """.trimIndent()

            val summaryResponse = agentService.processMessage(
                agentId = SchedulerConfig.summaryAgentId,
                chatId = summaryChat.id,
                userMessage = summaryMessage,
                temperature = 0.7
            )

            // Step 3: Ensure Oliver agent and chat "-1" exist
            logger.info("Sending summary to Oliver agent")
            val oliverChat = agentService.ensureChatExists(
                agentId = SchedulerConfig.oliverAgentId,
                chatId = SchedulerConfig.oliverChatId
            )

            // Step 4: Send summary to Oliver agent
            val oliverMessage = "Weather Summary Report:\n\n$summaryResponse"
            agentService.processMessage(
                agentId = SchedulerConfig.oliverAgentId,
                chatId = oliverChat.id,
                userMessage = oliverMessage,
                temperature = 0.7
            )

            logger.info("Summary sent successfully to Oliver agent")
        } catch (e: Exception) {
            logger.severe("Failed to process summary: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }
}

