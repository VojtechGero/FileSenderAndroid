package com.example.filesender

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SelectedFile private constructor(
    val uri: Uri,
    private val resolver: ContentResolver,
    override val fileName: String
) : ISelectedItem {
    companion object {
        suspend fun LoadFile(uri: Uri, resolver: ContentResolver): SelectedFile =
            withContext(Dispatchers.IO) {
                val name = resolver.query(
                    uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
                )?.use { cursor ->
                    val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && column >= 0) cursor.getString(column) else null
                }?.takeIf { it.isNotBlank() }
                    ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
                    ?: "soubor"

                SelectedFile(uri, resolver, name)
            }
    }

    override fun GetContent(): InputStream = resolver.openInputStream(uri)
        ?: throw IOException("Soubor „$fileName“ se nepodařilo otevřít")
}
