package com.example.clarity

import com.example.clarity.data.local.ProofFileSignature
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException

class ProofFileHeaderTest {
    @Test fun emptyStreamHasNoPaddedHeader() {
        assertArrayEquals(byteArrayOf(), ProofFileSignature.readHeader(ByteArrayInputStream(byteArrayOf())))
    }
    @Test fun smallFileReturnsExactlyTheBytesRead() {
        val bytes = "%PDF-".toByteArray()
        val header = ProofFileSignature.readHeader(ByteArrayInputStream(bytes))
        assertArrayEquals(bytes, header)
        assertTrue(ProofFileSignature.matches("application/pdf", header))
    }
    @Test fun partialReadsContinueUntilTheHeaderIsAvailable() {
        val bytes = "RIFF1234WEBP".toByteArray()
        val input = object : ByteArrayInputStream(bytes) {
            override fun read(buffer: ByteArray, offset: Int, length: Int) = super.read(buffer, offset, minOf(length, 2))
        }
        val header = ProofFileSignature.readHeader(input)
        assertArrayEquals(bytes, header)
        assertTrue(ProofFileSignature.matches("image/webp", header))
    }
    @Test fun largeFileOnlyReadsTheBoundedHeader() {
        val input = ByteArrayInputStream(ByteArray(4096) { it.toByte() })
        val header = ProofFileSignature.readHeader(input)
        assertEquals(64, header.size)
        assertEquals(4096 - 64, input.available())
        assertArrayEquals(ByteArray(64) { it.toByte() }, header)
    }
    @Test fun truncatedSignaturesAreRejectedUsingTheirActualLengths() {
        val examples = mapOf("application/pdf" to "%PDF".toByteArray(),
            "image/jpeg" to byteArrayOf(0xff.toByte(), 0xd8.toByte()),
            "image/png" to byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a),
            "image/gif" to "GIF89".toByteArray(), "image/webp" to "RIFF1234WEB".toByteArray(),
            "image/heic" to "1234ftyphei".toByteArray())
        examples.forEach { (mime, bytes) ->
            val header = ProofFileSignature.readHeader(ByteArrayInputStream(bytes))
            assertEquals(bytes.size, header.size)
            assertFalse(mime, ProofFileSignature.matches(mime, header))
        }
    }
    @Test fun zeroLengthBulkReadStillMakesProgress() {
        val bytes = "%PDF-1.4".toByteArray()
        val input = object : ByteArrayInputStream(bytes) {
            private var first = true
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (first) { first = false; return 0 }
                return super.read(buffer, offset, length)
            }
        }
        assertArrayEquals(bytes, ProofFileSignature.readHeader(input))
    }
    @Test fun readErrorsPropagateInsteadOfValidatingAnIncompleteHeader() {
        val input = object : ByteArrayInputStream("%PDF-".toByteArray()) {
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int = throw IOException("Read failed")
        }
        assertThrows(IOException::class.java) { ProofFileSignature.readHeader(input) }
    }
}
