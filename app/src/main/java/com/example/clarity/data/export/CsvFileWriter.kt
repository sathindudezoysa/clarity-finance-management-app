package com.example.clarity.data.export

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class CsvFileWriter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun write(uri: Uri, content: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val stream = context.contentResolver.openOutputStream(uri, "wt")
                ?: throw IOException("Unable to open output stream for $uri")
            stream.use { output ->
                output.write(UTF8_BOM)
                output.write(content.toByteArray(Charsets.UTF_8))
                output.flush()
            }
        }
    }

    private companion object {
        val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
    }
}

