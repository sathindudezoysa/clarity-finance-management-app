package com.example.clarity.data.drive

import com.google.gson.Gson
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DriveFile(
    val id: String,
    val name: String,
    val webViewLink: String? = null
)

enum class DriveUploadError {
    NETWORK,
    UNAUTHORIZED,
    FORBIDDEN,
    OTHER
}

class DriveUploadException(val error: DriveUploadError) : Exception(error.name)

@Singleton
class GoogleDriveUploader @Inject constructor() {
    private val gson = Gson()

    private data class DriveFileDto(
        val id: String? = null,
        val name: String? = null,
        val webViewLink: String? = null
    )

    suspend fun upload(
        accessToken: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray
    ): Result<DriveFile> = withContext(Dispatchers.IO) {
        val boundary = DriveMultipart.DEFAULT_BOUNDARY
        val body = DriveMultipart.build(
            fileName = fileName,
            mimeType = mimeType,
            bytes = bytes,
            boundary = boundary
        )
        var connection: HttpURLConnection? = null
        try {
            val url = URL(UPLOAD_ENDPOINT)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                useCaches = false
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Authorization", "Bearer $accessToken")
                setRequestProperty("Content-Type", DriveMultipart.contentTypeHeader(boundary))
                setFixedLengthStreamingMode(body.size)
            }
            connection.outputStream.use { output ->
                output.write(body)
                output.flush()
            }

            val code = connection.responseCode
            if (code in 200..299) {
                val responseText = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val parsed = runCatching { gson.fromJson(responseText, DriveFileDto::class.java) }.getOrNull()
                val id = parsed?.id?.takeIf { it.isNotBlank() }
                    ?: return@withContext Result.failure(DriveUploadException(DriveUploadError.OTHER))
                val name = parsed.name?.takeIf { it.isNotBlank() } ?: fileName
                Result.success(
                    DriveFile(
                        id = id,
                        name = name,
                        webViewLink = parsed.webViewLink
                    )
                )
            } else {
                runCatching { connection.errorStream?.use { it.readBytes() } }
                val mappedError = when (code) {
                    HttpURLConnection.HTTP_UNAUTHORIZED -> DriveUploadError.UNAUTHORIZED
                    HttpURLConnection.HTTP_FORBIDDEN -> DriveUploadError.FORBIDDEN
                    else -> DriveUploadError.OTHER
                }
                Result.failure(DriveUploadException(mappedError))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: UnknownHostException) {
            Result.failure(DriveUploadException(DriveUploadError.NETWORK))
        } catch (_: IOException) {
            Result.failure(DriveUploadException(DriveUploadError.NETWORK))
        } catch (_: Exception) {
            Result.failure(DriveUploadException(DriveUploadError.OTHER))
        } finally {
            connection?.disconnect()
        }
    }

    private companion object {
        const val UPLOAD_ENDPOINT =
            "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,name,webViewLink"
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 60_000
    }
}

