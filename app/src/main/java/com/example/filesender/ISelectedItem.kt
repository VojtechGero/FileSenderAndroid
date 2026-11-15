package com.example.filesender

import android.content.ContentResolver
import java.io.InputStream

interface ISelectedItem{
    val fileName: String
    fun GetContent(): InputStream
}
