package com.aiadventcalendar.googlesheetsmcp

import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.SheetsScopes
import com.google.api.services.sheets.v4.model.ValueRange
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStreamReader
import java.time.Instant
import java.util.Collections

/**
 * Service for interacting with Google Sheets API.
 */
class GoogleSheetsService(
    private val credentialsPath: String,
    private val spreadsheetId: String,
    private val sheetName: String = "Sheet1"
) {
    private val applicationName = "Weather Forecast Sheets"
    private val jsonFactory: JsonFactory = GsonFactory.getDefaultInstance()
    private val scopes = Collections.singletonList(SheetsScopes.SPREADSHEETS)
    private val tokensDirectoryPath = "tokens"

    /**
     * Appends a row with forecast data to the Google Sheet.
     */
    suspend fun appendRow(location: String, forecast: String, timestamp: String = Instant.now().toString()) {
        val httpTransport = GoogleNetHttpTransport.newTrustedTransport()
        val service = Sheets.Builder(httpTransport, jsonFactory, getCredentials(httpTransport))
            .setApplicationName(applicationName)
            .build()

        val range = "$sheetName!A:C"
        // Create ValueRange with values using the constructor pattern
        val rowValues = listOf(
            listOf<Any>(timestamp, location, forecast)
        )
        val valueRange = ValueRange()
            .setValues(rowValues)

        service.spreadsheets().values()
            .append(spreadsheetId, range, valueRange)
            .setValueInputOption("RAW")
            .setInsertDataOption("INSERT_ROWS")
            .execute()
    }

    private fun getCredentials(httpTransport: NetHttpTransport): Credential {
        val clientSecrets = GoogleClientSecrets.load(
            jsonFactory,
            InputStreamReader(File(credentialsPath).inputStream())
        )

        val flow = GoogleAuthorizationCodeFlow.Builder(
            httpTransport, jsonFactory, clientSecrets, scopes
        )
            .setDataStoreFactory(FileDataStoreFactory(File(tokensDirectoryPath)))
            .setAccessType("offline")
            .build()

        val receiver = LocalServerReceiver.Builder().setPort(8888).build()
        return AuthorizationCodeInstalledApp(flow, receiver).authorize("user")
    }
}

