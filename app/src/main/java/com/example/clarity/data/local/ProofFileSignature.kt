package com.example.clarity.data.local

object ProofFileSignature {
    fun matches(mime: String, bytes: ByteArray): Boolean {
        fun starts(vararg signature: Int) = bytes.size >= signature.size &&
            signature.indices.all { bytes[it].toInt() and 255 == signature[it] }
        fun ascii(from: Int, length: Int) = if (bytes.size >= from + length)
            String(bytes, from, length, Charsets.US_ASCII) else ""
        return when (mime) {
            "application/pdf" -> ascii(0, 5) == "%PDF-"
            "image/jpeg" -> starts(0xff, 0xd8, 0xff)
            "image/png" -> starts(0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
            "image/gif" -> ascii(0, 6) in setOf("GIF87a", "GIF89a")
            "image/webp" -> ascii(0, 4) == "RIFF" && ascii(8, 4) == "WEBP"
            "image/heic", "image/heif" -> ascii(4, 4) == "ftyp" && ascii(8, 4) in setOf("heic", "heix", "hevc", "hevx", "mif1", "msf1")
            else -> false
        }
    }
}
