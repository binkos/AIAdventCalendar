package com.aiadventcalendar.mcp

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.headers
import io.ktor.serialization.kotlinx.json.json
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import com.aiadventcalendar.mcp.models.StoredForecast
import kotlinx.serialization.encodeToString
import kotlinx.datetime.Clock
import kotlinx.coroutines.runBlocking

/**
 * Starts an MCP server that provides weather-related tools for fetching active
 * weather alerts by state and weather forecasts by latitude/longitude.
 */
fun runMcpServer() {
    // Initialize weather storage
    val weatherStorage = WeatherStorage()
    
    // JSON encoder for forecast storage
    val jsonEncoder = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    
    // Base URL for the Weather API
    val baseUrl = "https://api.weather.gov"

    // Create an HTTP client with a default request configuration and JSON content negotiation
    val httpClient = HttpClient(CIO) {
        defaultRequest {
            url(baseUrl)
            headers {
                append("Accept", "application/geo+json")
                append("User-Agent", "WeatherApiClient/1.0")
            }
            contentType(ContentType.Application.Json)
        }
        // Install content negotiation plugin for JSON serialization/deserialization
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    prettyPrint = true
                },
            )
        }
    }

    // Create the MCP Server instance with a basic implementation
    val server = Server(
        Implementation(
            name = "weather", // Tool name is "weather"
            version = "1.0.0", // Version of the implementation
        ),
        ServerOptions(
            capabilities = ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = true)),
        ),
    )

    // Register a tool to fetch weather alerts by state
    server.addTool(
        name = "get_alerts",
        description = """
            Get weather alerts for a US state. Input is Two-letter US state code (e.g. CA, NY)
        """.trimIndent(),
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("state") {
                    put("type", "string")
                    put("description", "Two-letter US state code (e.g. CA, NY)")
                }
            },
            required = listOf("state"),
        ),
    ) { request ->
        val state = request.arguments?.get("state")?.jsonPrimitive?.content
            ?: return@addTool CallToolResult(
                content = listOf(TextContent("The 'state' parameter is required.")),
            )

        val alerts = httpClient.getAlerts(state)
        
        // Automatically store the alerts with current timestamp
        val alertsJson = jsonEncoder.encodeToString(alerts)
        val location = "State: $state"
        
        runBlocking {
            weatherStorage.storeForecast(location, alertsJson)
        }

        CallToolResult(content = alerts.map { TextContent(it) })
    }

    // Register a tool to fetch weather forecast by latitude and longitude
    server.addTool(
        name = "get_forecast",
        description = """
            Get weather forecast for a specific latitude/longitude
        """.trimIndent(),
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("latitude") {
                    put("type", "number")
                }
                putJsonObject("longitude") {
                    put("type", "number")
                }
            },
            required = listOf("latitude", "longitude"),
        ),
    ) { request ->
        val latitude = request.arguments?.get("latitude")?.jsonPrimitive?.doubleOrNull
        val longitude = request.arguments?.get("longitude")?.jsonPrimitive?.doubleOrNull
        if (latitude == null || longitude == null) {
            return@addTool CallToolResult(
                content = listOf(TextContent("The 'latitude' and 'longitude' parameters are required.")),
            )
        }

        val forecast = httpClient.getForecast(latitude, longitude)
        
        // Automatically store the forecast with current timestamp
        // Location is derived from lat/long (could be enhanced to reverse geocode)
        val location = "$latitude,$longitude"
        val forecastJson = jsonEncoder.encodeToString(forecast)
        
        runBlocking {
            weatherStorage.storeForecast(location, forecastJson)
        }

        CallToolResult(content = forecast.map { TextContent(it) })
    }

    // Register a tool to store forecast with timestamp
    server.addTool(
        name = "store_forecast",
        description = """
            Store a weather forecast with timestamp and location for later retrieval
        """.trimIndent(),
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("location") {
                    put("type", "string")
                    put("description", "Location name (e.g., California)")
                }
                putJsonObject("forecast") {
                    put("type", "string")
                    put("description", "Forecast data as JSON string")
                }
                putJsonObject("timestamp") {
                    put("type", "number")
                    put("description", "Unix timestamp (optional, uses current time if not provided)")
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
        val timestamp = request.arguments?.get("timestamp")?.jsonPrimitive?.doubleOrNull?.toLong()

        runBlocking {
            weatherStorage.storeForecast(location, forecast, timestamp)
        }

        CallToolResult(content = listOf(TextContent("Forecast stored successfully.")))
    }

    // Register a tool to get all stored forecasts
    server.addTool(
        name = "get_all_forecasts",
        description = """
            Retrieve all stored weather forecasts
        """.trimIndent(),
        inputSchema = ToolSchema(
            properties = buildJsonObject {},
            required = listOf(),
        ),
    ) { _ ->
        val forecasts = runBlocking {
            weatherStorage.getAllStoredForecasts()
        }

        val forecastsJson = jsonEncoder.encodeToString(forecasts)

        CallToolResult(content = listOf(TextContent(forecastsJson)))
    }

    // Register a tool to get forecasts by time range
    server.addTool(
        name = "get_forecasts_by_range",
        description = """
            Retrieve stored forecasts within a specific time range
        """.trimIndent(),
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("startTime") {
                    put("type", "number")
                    put("description", "Start timestamp (Unix time)")
                }
                putJsonObject("endTime") {
                    put("type", "number")
                    put("description", "End timestamp (Unix time)")
                }
            },
            required = listOf("startTime", "endTime"),
        ),
    ) { request ->
        val startTime = request.arguments?.get("startTime")?.jsonPrimitive?.doubleOrNull?.toLong()
            ?: return@addTool CallToolResult(
                content = listOf(TextContent("The 'startTime' parameter is required.")),
            )
        val endTime = request.arguments?.get("endTime")?.jsonPrimitive?.doubleOrNull?.toLong()
            ?: return@addTool CallToolResult(
                content = listOf(TextContent("The 'endTime' parameter is required.")),
            )

        val forecasts = runBlocking {
            weatherStorage.getForecastsByTimeRange(startTime, endTime)
        }

        val forecastsJson = jsonEncoder.encodeToString(forecasts)

        CallToolResult(content = listOf(TextContent(forecastsJson)))
    }

    // Create a transport using standard IO for server communication
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