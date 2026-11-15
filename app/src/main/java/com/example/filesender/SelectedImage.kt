package com.example.filesender

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream

class SelectedImage private constructor(
    val uri: Uri,
    private val bitmap: Bitmap,
    val resolver: ContentResolver,
    override val fileName: String
) : ISelectedItem {
    companion object {
        suspend fun LoadImage(
            uri: Uri,
            resolver: ContentResolver
        ): SelectedImage {
            return withContext(Dispatchers.IO) {
                val fileName = resolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        cursor.getString(
                            cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)
                        )
                    } else {
                        null
                    }
                }?.takeIf { it.isNotBlank() }
                    ?: throw IOException("Název souboru se nepodařilo načíst")

                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                options.inJustDecodeBounds = false
                options.inSampleSize = 1
                while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 512) {
                    options.inSampleSize *= 2
                }
                val bitmap = resolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, options)
                } ?: throw IOException("Obrázek se nepodařilo načíst")

                SelectedImage(uri, bitmap, resolver, fileName)
            }
        }
    }

    @Composable
    fun ShowImage(modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = contentScale,
            modifier = modifier
        )
    }


    override fun GetContent(): InputStream {
        return resolver.openInputStream(uri)
            ?: throw IOException("Soubor se nepodařilo otevřít")
    }
}
