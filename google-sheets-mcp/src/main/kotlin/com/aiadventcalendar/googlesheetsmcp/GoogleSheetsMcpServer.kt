package com.aiadventcalendar.googlesheetsmcp

import io.ktor.utils.io.streams.asInput
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.buffered
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.time.Instant
import java.util.logging.Logger

private val logger = Logger.getLogger("GoogleSheetsMcpServer")

/**
 * Starts the Google Sheets MCP server that provides tools for storing data in Google Sheets.
 */
fun runGoogleSheetsMcpServer() {
    val spreadsheetId = System.getenv("GOOGLE_SHEETS_ID")
        ?: throw IllegalStateException("GOOGLE_SHEETS_ID environment variable is required")
    
    val credentialsPath = System.getenv("GOOGLE_SHEETS_CREDENTIALS_PATH") ?: "credentials.json"
    val sheetName = System.getenv("GOOGLE_SHEETS_NAME") ?: "Sheet1"
    
    logger.info("Starting Google Sheets MCP Server for spreadsheet: $spreadsheetId")
    
    val sheetsService = GoogleSheetsService(
        credentialsPath = credentialsPath,
        spreadsheetId = spreadsheetId,
        sheetName = sheetName
    )

    val server = Server(
        Implementation(
            name = "google-sheets",
            version = "1.0.0",
        ),
        ServerOptions(
            capabilities = ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = true)),
        ),
    )

    server.addTool(
        name = "append_to_sheets",
        description = """
            Append weather forecast data to a Google Sheet.
            Adds a new row with timestamp, location, and forecast details.
            Columns: Timestamp | Location | Forecast
        """.trimIndent(),
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("location") {
                    put("type", "string")
                    put("description", "Location name (e.g., New York, California)")
                }
                putJsonObject("forecast") {
                    put("type", "string")
                    put("description", "Forecast data as JSON string or formatted text")
                }
                putJsonObject("timestamp") {
                    put("type", "string")
                    put("description", "Timestamp of the forecast (ISO format, optional)")
                }
            },
            required = listOf("location", "forecast"),
        ),
    ) { request ->
        val location = request.arguments?.get("location")?.jsonPrimitive?.content
            ?: return@addTool CallToolResult(
                content = listOf(TextContent("The 'location' parameter is required.")),
            )
        val forecast = request.arguments?.get("forecast")?.jsonPrimitive?.content
            ?: return@addTool CallToolResult(
                content = listOf(TextContent("The 'forecast' parameter is required.")),
            )
        val timestamp = request.arguments?.get("timestamp")?.jsonPrimitive?.content
            ?: Instant.now().toString()

        try {
            runBlocking {
                sheetsService.appendRow(location, forecast, timestamp)
            }
            CallToolResult(content = listOf(TextContent("Forecast successfully appended to Google Sheets.")))
        } catch (e: Exception) {
            logger.severe("Error appending to sheets: ${e.message}")
            CallToolResult(content = listOf(TextContent("Error appending to sheets: ${e.message}")))
        }
    }

    val transport = StdioServerTransport(
        System.`in`.asInput(),
        System.out.asSink().buffered(),
    )

    runBlocking {
        val session = server.createSession(transport)
        val done = Job()
        session.onClose {
            done.complete()
        }
        done.join()
    }
}

