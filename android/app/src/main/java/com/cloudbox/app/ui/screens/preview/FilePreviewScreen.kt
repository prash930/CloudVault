package com.cloudbox.app.ui.screens.preview

import android.content.Intent
import android.os.Environment
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.cloudbox.app.BuildConfig
import com.cloudbox.app.ui.components.BackRow
import com.cloudbox.app.ui.components.ActionTile
import com.cloudbox.app.ui.components.CloudboxScreen
import com.cloudbox.app.ui.components.fileVisual
import com.cloudbox.app.ui.components.formatBytes
import com.cloudbox.app.ui.components.formatDate
import com.cloudbox.app.ui.components.formatDateTime
import com.cloudbox.app.ui.screens.home.HomeViewModel
import com.cloudbox.app.ui.theme.CloudCard
import com.cloudbox.app.ui.theme.CloudMuted
import com.cloudbox.app.ui.theme.CloudNavy
import com.cloudbox.app.ui.theme.CloudSoft
import java.io.File

@Composable
fun FilePreviewScreen(
    fileId: Int,
    onBack: () -> Unit,
    onShare: (Int) -> Unit,
    viewModel: HomeViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val file = viewModel.fileById(fileId)
    var isOpening by remember { mutableStateOf(false) }

    fun launchIntent(savedFile: File) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", savedFile)
            val mimeType = MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(savedFile.extension.lowercase())
                ?: file?.mime_type
                ?: "*/*"
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "No app found to open this file", Toast.LENGTH_SHORT).show()
        }
    }

    fun openExternally() {
        if (file == null) return
        val cacheDir = File(context.cacheDir, "opened").apply { mkdirs() }
        val cachedFile = File(cacheDir, file.filename)
        if (cachedFile.exists() && cachedFile.length() > 0 && (file.size_bytes <= 0 || cachedFile.length() == file.size_bytes)) {
            launchIntent(cachedFile)
            return
        }
        isOpening = true
        Toast.makeText(context, "Opening ${file.filename}...", Toast.LENGTH_SHORT).show()
        viewModel.openFile(file, cacheDir) { savedFile ->
            isOpening = false
            launchIntent(savedFile)
        }
    }

    CloudboxScreen {
        if (file == null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("File not found", color = CloudMuted, fontSize = 16.sp)
            }
            return@CloudboxScreen
        }

        val isImage = file.mime_type?.startsWith("image/") == true
        val (icon, tint, bg) = fileVisual(file)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            BackRow(file.filename, onBack)
            Spacer(Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(CloudCard),
                contentAlignment = Alignment.Center
            ) {
                if (isImage) {
                    AsyncImage(
                        model = BuildConfig.BASE_URL + "files/${file.id}/download",
                        contentDescription = file.filename,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier.size(96.dp).clip(RoundedCornerShape(24.dp)).background(bg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(48.dp))
                    }
                    Text(
                        if (isImage) "" else "Preview not available",
                        color = CloudMuted,
                        fontSize = 14.sp,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(file.filename, color = CloudNavy, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2)
            Text(
                "${if (file.is_folder) "Folder" else formatBytes(file.size_bytes)}  \u2022  ${file.mime_type ?: "file"}",
                color = CloudMuted,
                fontSize = 13.sp
            )

            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CloudCard)
                    .padding(16.dp)
            ) {
                Column {
                    DetailRow("Type", if (file.is_folder) "Folder" else file.mime_type ?: "Unknown")
                    DetailRow("Size", if (file.is_folder) "—" else formatBytes(file.size_bytes))
                    DetailRow("Created", formatDate(file.created_at))
                    DetailRow("Modified", formatDateTime(file.updated_at))
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                ActionTile(Icons.Default.OpenInNew, "Open", onClick = { openExternally() })
                ActionTile(Icons.Default.Download, "Download", onClick = {
                    val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
                    viewModel.download(file, dir)
                })
                if (!file.is_folder) {
                    ActionTile(Icons.Default.Share, "Share", onClick = { onShare(file.id) })
                }
                if (file.is_folder || !file.is_trashed) {
                    ActionTile(Icons.Default.Delete, "Trash", onClick = {
                        viewModel.moveToTrash(file)
                        onBack()
                    })
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(label, color = CloudMuted, fontSize = 13.sp, modifier = Modifier.width(80.dp))
        Text(value.ifBlank { "—" }, color = CloudNavy, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}