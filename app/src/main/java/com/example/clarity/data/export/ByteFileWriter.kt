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
class ByteFileWriter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun write(uri: Uri, bytes: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val stream = context.contentResolver.openOutputStream(uri, "wt")
                ?: throw IOException("Unable to open output stream for $uri")
            stream.use { output ->
                output.write(bytes)
                output.flush()
            }
        }
    }
}

