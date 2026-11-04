package com.example.clarity.domain

import com.example.clarity.data.drive.DriveMultipart
import com.google.gson.JsonParser
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveMultipartTest {

    @Test
    fun `DriveMultipart builds valid multipart related body escaping quotes unicode and preserving binary bytes`() {
        val boundary = "test_boundary_123"
        val fileName = "clarity \"budgets\" — 2026-10 (රු).xlsx"
        val mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        val binaryPayload = byteArrayOf(
            0x50,
            0x4B,
            0x03,
            0x04,
            0x00,
            0x7F,
            0x80.toByte(),
            0xFF.toByte(),
            0x0D,
            0x0A
        )

        assertEquals(
            "multipart/related; boundary=test_boundary_123",
            DriveMultipart.contentTypeHeader(boundary)
        )

        val body = DriveMultipart.build(
            fileName = fileName,
            mimeType = mimeType,
            bytes = binaryPayload,
            boundary = boundary
        )

        val part1Prefix = (
            "--$boundary\r\n" +
                "Content-Type: application/json; charset=UTF-8\r\n\r\n"
            ).toByteArray(Charsets.UTF_8)
        assertTrue(
            "Body must start with first boundary and JSON Content-Type header",
            body.take(part1Prefix.size).toByteArray().contentEquals(part1Prefix)
        )

        val part2Marker = (
            "\r\n--$boundary\r\n" +
                "Content-Type: $mimeType\r\n\r\n"
            ).toByteArray(Charsets.UTF_8)
        val closingBoundary = "\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8)

        assertTrue(
            "Body must end with closing boundary",
            body.takeLast(closingBoundary.size).toByteArray().contentEquals(closingBoundary)
        )

        // Locate part2Marker to split metadata JSON and binary payload
        val part2Start = indexOfSubArray(body, part2Marker, part1Prefix.size)
        assertTrue("Part 2 marker must be present", part2Start > part1Prefix.size)

        val metadataBytes = body.copyOfRange(part1Prefix.size, part2Start)
        val metadataJsonString = metadataBytes.toString(Charsets.UTF_8)
        val jsonObj = JsonParser.parseString(metadataJsonString).asJsonObject
        assertEquals(fileName, jsonObj.get("name").asString)
        assertEquals(mimeType, jsonObj.get("mimeType").asString)

        val binaryStart = part2Start + part2Marker.size
        val binaryEnd = body.size - closingBoundary.size
        val extractedBinary = body.copyOfRange(binaryStart, binaryEnd)
        assertArrayEquals(binaryPayload, extractedBinary)
    }

    private fun indexOfSubArray(haystack: ByteArray, needle: ByteArray, fromIndex: Int): Int {
        val max = haystack.size - needle.size
        outer@ for (i in fromIndex..max) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return -1
    }
}

