package com.example.clarity.data.local

import java.io.File
import java.util.concurrent.TimeUnit

/** Temporary viewer copies only. Original private evidence is never removed here. */
object ProofPreviewCache {
    const val MAX_BYTES = 50L * 1024 * 1024
    const val MAX_FILES = 50
    val MAX_AGE_MILLIS: Long = TimeUnit.HOURS.toMillis(24)

    fun prune(root: File, keep: File? = null, now: Long = System.currentTimeMillis()) {
        if (!root.isDirectory) return
        val canonicalRoot = root.canonicalFile
        val prefix = canonicalRoot.path + File.separator
        val protected = keep?.canonicalFile
        val candidates = root.walkTopDown().onEnter {
            it.canonicalFile == canonicalRoot || it.canonicalPath.startsWith(prefix)
        }.filter { it.isFile && it.canonicalPath.startsWith(prefix) }.toList()
        candidates.filter { it.canonicalFile != protected && now - it.lastModified() > MAX_AGE_MILLIS }
            .forEach { it.delete() }
        val remaining = candidates.filter { it.isFile }.sortedBy { it.lastModified() }
        var bytes = remaining.sumOf { it.length() }
        var count = remaining.size
        for (file in remaining) {
            if (bytes <= MAX_BYTES && count <= MAX_FILES) break
            if (file.canonicalFile == protected) continue
            val length = file.length()
            if (file.delete()) { bytes -= length; count-- }
        }
        root.listFiles()?.filter { it.isDirectory && it.canonicalPath.startsWith(prefix) && it.list()?.isEmpty() == true }
            ?.forEach { it.delete() }
    }
}
