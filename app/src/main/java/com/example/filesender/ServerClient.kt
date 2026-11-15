package com.example.filesender

import android.net.Uri
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject

suspend fun pingServer(baseUrl: String, timeoutMillis: Int = 5_000): String {
    return withContext(Dispatchers.IO) {
        val url = URL("${baseUrl.trimEnd('/')}/ping")
        val connection = url.openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "GET"
            connection.useCaches = false
            connection.instanceFollowRedirects = false
            connection.connectTimeout = timeoutMillis
            connection.readTimeout = timeoutMillis

            val statusCode = connection.responseCode

            if (statusCode !in 200..299) {
                throw connection.serverError(statusCode)
            }

            connection.inputStream.bufferedReader().use { reader ->
                val body = reader.readText()
                responseMessage(body) ?: "Server je dostupný"
            }
        } finally {
            connection.disconnect()
        }
    }
}

internal suspend fun findOfflineReceivers(addresses: Set<String>): Set<String> = coroutineScope {
    addresses.map { address ->
        async {
            val reachable = try {
                pingServer(address, timeoutMillis = 2_000) == "FileSender běží"
            } catch (_: IOException) {
                false
            } catch (_: SecurityException) {
                false
            }
            if (reachable) null else address
        }
    }.awaitAll().filterNotNull().toSet()
}

suspend fun sendFile(
    baseUrl: String,
    file: ISelectedItem
) {
    withContext(Dispatchers.IO) {
        val url = URL("${baseUrl.trimEnd('/')}/files?fileName=${Uri.encode(file.fileName)}")
        val connection = url.openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = 5_000
            connection.readTimeout = 30_000
            connection.setRequestProperty(
                "Content-Type",
                "application/octet-stream"
            )
            connection.setChunkedStreamingMode(0)

            file.GetContent().use { input ->
                connection.outputStream.use { output ->
                    input.copyTo(output)
                }
            }

            val status = connection.responseCode
            if (status !in 200..299) {
                throw connection.serverError(status)
            }
            connection.inputStream.close()
        } finally {
            connection.disconnect()
        }
    }
}

private fun responseMessage(body: String): String? = runCatching {
    JSONObject(body).optString("message").takeIf { it.isNotBlank() }
}.getOrNull()

private fun HttpURLConnection.serverError(statusCode: Int): IOException {
    val message = runCatching {
        errorStream?.bufferedReader()?.use { responseMessage(it.readText()) }
    }.getOrNull()
    return IOException("Server vrátil HTTP $statusCode" + if (message != null) ": $message" else "")
}
