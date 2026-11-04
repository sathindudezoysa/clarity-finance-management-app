package com.example.clarity.data.drive

import com.google.gson.Gson
import java.io.ByteArrayOutputStream

object DriveMultipart {
    const val DEFAULT_BOUNDARY = "clarity_drive_boundary_7f3a9c1e"
    private const val CRLF = "\r\n"

    private data class DriveMetadata(
        val name: String,
        val mimeType: String
    )

    private val gson = Gson()

    fun contentTypeHeader(boundary: String = DEFAULT_BOUNDARY): String =
        "multipart/related; boundary=$boundary"

    fun build(
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        boundary: String = DEFAULT_BOUNDARY
    ): ByteArray {
        val metadataJson = gson.toJson(DriveMetadata(name = fileName, mimeType = mimeType))
        val out = ByteArrayOutputStream()

        fun writeAscii(text: String) {
            out.write(text.toByteArray(Charsets.UTF_8))
        }

        // Part 1: JSON metadata
        writeAscii("--$boundary$CRLF")
        writeAscii("Content-Type: application/json; charset=UTF-8$CRLF$CRLF")
        out.write(metadataJson.toByteArray(Charsets.UTF_8))
        writeAscii(CRLF)

        // Part 2: Binary file content
        writeAscii("--$boundary$CRLF")
        writeAscii("Content-Type: $mimeType$CRLF$CRLF")
        out.write(bytes)
        writeAscii(CRLF)

        // Closing boundary
        writeAscii("--$boundary--$CRLF")

        return out.toByteArray()
    }
}

