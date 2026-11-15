package com.example.filesender

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch


class FileSelector {
    var files by mutableStateOf<List<SelectedFile>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    private var preview by mutableStateOf<SelectedImage?>(null)
    var errorMessage by mutableStateOf<String?>(null)
        private set

    @Composable
    fun AddSelector(modifier: Modifier, enabled: Boolean = true) {
        val resolver = LocalContext.current.contentResolver
        val scope = rememberCoroutineScope()
        var openPreview by remember { mutableStateOf<SelectedImage?>(null) }

        fun loadSelection(uris: List<Uri>, photosOnly: Boolean) {
            if (uris.isEmpty() || isLoading) return
            isLoading = true
            errorMessage = null
            scope.launch {
                try {
                    val selection = files.toMutableList()
                    val errors = mutableListOf<String>()
                    for (uri in uris.distinct()) {
                        if (selection.any { it.uri == uri }) continue
                        try {
                            selection += SelectedFile.LoadFile(uri, resolver)
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            errors += "Soubor se nepodařilo načíst: ${e.message}"
                        }
                    }
                    files = selection.toList()
                    errorMessage = errors.takeIf { it.isNotEmpty() }?.joinToString("\n")

                    // Náhled je jen pro první fotku; obsah ostatních se čte až při odesílání.
                    if (photosOnly && preview == null) {
                        val firstPhoto = files.firstOrNull { it.uri in uris }
                        if (firstPhoto != null) {
                            try {
                                preview = SelectedImage.LoadImage(firstPhoto.uri, resolver)
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                // Nedostupný náhled nebrání odeslání původního souboru.
                            }
                        }
                    }
                } finally {
                    isLoading = false
                }
            }
        }

        val photoPicker = rememberLauncherForActivityResult(
            ActivityResultContracts.PickMultipleVisualMedia()
        ) { loadSelection(it, photosOnly = true) }
        val filePicker = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenMultipleDocuments()
        ) { loadSelection(it, photosOnly = false) }

        val canPick = enabled && !isLoading
        Column(modifier) {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PanelTitle(if (files.isEmpty()) "Přílohy" else "Přílohy (${files.size})", Modifier.weight(1f))
                    if (files.isNotEmpty()) {
                        TextButton(enabled = canPick, onClick = {
                            files = emptyList()
                            preview = null
                            errorMessage = null
                        }) { Text("Vymazat vše") }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PickerTile("Vybrat fotky", R.drawable.fs_photo, canPick, Modifier.weight(1f)) {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                    PickerTile("Vybrat soubory", R.drawable.fs_folder, canPick, Modifier.weight(1f)) {
                        filePicker.launch(arrayOf("*/*"))
                    }
                }
                if (isLoading) DetailText("Načítám výběr…")
                files.forEach { file ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val photo = preview?.takeIf { it.uri == file.uri }
                        if (photo != null) {
                            photo.ShowImage(Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
                                .clickable(role = Role.Button, onClickLabel = "Zvětšit náhled") { openPreview = photo }
                                .semantics { contentDescription = "Náhled fotky ${file.fileName}" })
                        } else {
                            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                    val extension = file.fileName.substringAfterLast('.', "").uppercase()
                                    if (extension.length in 1..5) Text(extension, style = MaterialTheme.typography.labelSmall)
                                    else Glyph(R.drawable.fs_folder)
                                }
                            }
                        }
                        Text(file.fileName, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        IconButton(enabled = canPick, modifier = Modifier.semantics {
                            contentDescription = "Odebrat ${file.fileName}"
                        }, onClick = {
                            files = files.filterNot { it.uri == file.uri }
                            if (file.uri == preview?.uri) preview = null
                        }) { Text("×", style = MaterialTheme.typography.titleLarge) }
                    }
                }
                errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
        openPreview?.let { photo ->
            AlertDialog(onDismissRequest = { openPreview = null },
                title = { Text(photo.fileName, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                text = { photo.ShowImage(Modifier.fillMaxWidth().heightIn(max = 360.dp), ContentScale.Fit) },
                confirmButton = { TextButton(onClick = { openPreview = null }) { Text("Zavřít") } })
        }
    }
}

@Composable
private fun PickerTile(label: String, icon: Int, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(modifier = modifier, shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant) {
        Column(Modifier.fillMaxWidth().clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Glyph(icon)
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}
