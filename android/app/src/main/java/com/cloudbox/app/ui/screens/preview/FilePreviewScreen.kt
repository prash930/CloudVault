package com.cloudbox.app.ui.screens.preview

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import com.cloudbox.app.data.api.models.CloudFile
import com.cloudbox.app.data.util.DownloadHelper
import com.cloudbox.app.ui.components.ActionTile
import com.cloudbox.app.ui.components.BackRow
import com.cloudbox.app.ui.components.CloudboxScreen
import com.cloudbox.app.ui.components.FullscreenImageViewer
import com.cloudbox.app.ui.components.VideoPlayerModal
import com.cloudbox.app.ui.components.fileVisual
import com.cloudbox.app.ui.components.formatBytes
import com.cloudbox.app.ui.components.formatDate
import com.cloudbox.app.ui.components.formatDateTime
import com.cloudbox.app.ui.screens.home.HomeViewModel
import com.cloudbox.app.ui.theme.CloudBlue
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
    var file by remember { mutableStateOf(viewModel.fileById(fileId)) }
    var fetchFailed by remember { mutableStateOf(false) }
    val cacheDir = remember(fileId) { File(context.cacheDir, "preview_cache").apply { mkdirs() } }
    var previewFile by remember { mutableStateOf<File?>(null) }
    var previewError by remember { mutableStateOf<String?>(null) }
    var retryCount by remember { mutableStateOf(0) }
    var isOpening by remember { mutableStateOf(false) }
    var showImageFull by remember { mutableStateOf(false) }
    var showVideoPlayer by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }

    LaunchedEffect(fileId) {
        if (file == null) {
            viewModel.fetchFile(fileId) { fetched ->
                if (fetched != null) file = fetched else fetchFailed = true
            }
        }
    }

    LaunchedEffect(file?.id, retryCount) {
        val f = file ?: return@LaunchedEffect
        previewFile = null
        previewError = null
        viewModel.preparePreview(
            f, cacheDir,
            onReady = { previewFile = it },
            onError = { previewError = it }
        )
    }

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
        val f = file ?: return
        val cacheDir = File(context.cacheDir, "opened").apply { mkdirs() }
        val cachedFile = File(cacheDir, f.filename)
        if (cachedFile.exists() && cachedFile.length() > 0 && (f.size_bytes <= 0 || cachedFile.length() == f.size_bytes)) {
            launchIntent(cachedFile)
            return
        }
        isOpening = true
        Toast.makeText(context, "Opening ${f.filename}...", Toast.LENGTH_SHORT).show()
        viewModel.openFile(f, cacheDir) { savedFile ->
            isOpening = false
            launchIntent(savedFile)
        }
    }

    CloudboxScreen {
        val f = file ?: run {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(if (fetchFailed) "File not found" else "Loading file...", color = CloudMuted, fontSize = 16.sp)
                if (fetchFailed) {
                    Spacer(Modifier.height(12.dp))
                    androidx.compose.material3.TextButton(onClick = {
                        fetchFailed = false
                        viewModel.fetchFile(fileId) { fetched ->
                            if (fetched != null) file = fetched else fetchFailed = true
                        }
                    }) {
                        Text("Retry", color = CloudBlue)
                    }
                } else {
                    Spacer(Modifier.height(12.dp))
                    androidx.compose.material3.CircularProgressIndicator(color = CloudBlue, strokeWidth = 3.dp)
                }
            }
            return@CloudboxScreen
        }

        val isImage = f.mime_type?.startsWith("image/") == true
        val isVideo = f.mime_type?.startsWith("video/") == true
        val (icon, tint, bg) = fileVisual(f)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            BackRow(f.filename, onBack)
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
                    val img = previewFile
                    val err = previewError
                    if (img != null) {
                        SubcomposeAsyncImage(
                            model = img,
                            contentDescription = f.filename,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { showImageFull = true },
                            loading = {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    androidx.compose.material3.CircularProgressIndicator(color = CloudBlue, strokeWidth = 3.dp)
                                }
                            },
                            error = {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(48.dp))
                                }
                            },
                            success = {
                                SubcomposeAsyncImageContent()
                            }
                        )
                    } else if (err != null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(48.dp))
                                Spacer(Modifier.height(8.dp))
                                Text(err, color = CloudMuted, fontSize = 13.sp, maxLines = 2)
                                Spacer(Modifier.height(8.dp))
                                androidx.compose.material3.TextButton(onClick = { retryCount++ }) {
                                    Text("Retry", color = CloudBlue)
                                }
                            }
                        }
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            androidx.compose.material3.CircularProgressIndicator(color = CloudBlue, strokeWidth = 3.dp)
                        }
                    }
                } else if (isVideo) {
                    Box(
                        modifier = Modifier.size(96.dp).clip(RoundedCornerShape(24.dp)).background(bg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(48.dp))
                    }
                    val videoReady = previewFile != null
                    val err = previewError
                    if (err != null) {
                        Column(
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(err, color = CloudMuted, fontSize = 13.sp, maxLines = 2)
                            androidx.compose.material3.TextButton(onClick = { retryCount++ }) {
                                Text("Retry", color = CloudBlue)
                            }
                        }
                    } else if (!videoReady) {
                        Box(
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.material3.CircularProgressIndicator(
                                color = CloudBlue,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color(0x88000000))
                            .clickable(enabled = videoReady) {
                                if (previewFile != null) showVideoPlayer = true
                            }
                            .padding(10.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier.size(96.dp).clip(RoundedCornerShape(24.dp)).background(bg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(48.dp))
                    }
                    Text(
                        "Preview not available",
                        color = CloudMuted,
                        fontSize = 14.sp,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(f.filename, color = CloudNavy, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2)
            Text(
                "${if (f.is_folder) "Folder" else formatBytes(f.size_bytes)}  \u2022  ${f.mime_type ?: "file"}",
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
                    DetailRow("Type", if (f.is_folder) "Folder" else f.mime_type ?: "Unknown")
                    DetailRow("Size", if (f.is_folder) "—" else formatBytes(f.size_bytes))
                    DetailRow("Created", formatDate(f.created_at))
                    DetailRow("Modified", formatDateTime(f.updated_at))
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                ActionTile(Icons.Default.OpenInNew, "Open", onClick = { openExternally() })
                if (isVideo) {
                    ActionTile(Icons.Default.PlayArrow, "Play", onClick = {
                        if (previewFile != null) showVideoPlayer = true
                    })
                }
                if (isImage) {
                    ActionTile(Icons.Default.Fullscreen, "View", onClick = { showImageFull = true })
                }
                ActionTile(Icons.Default.Download, "Download", onClick = {
                    val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
                    viewModel.download(f, dir) { saved ->
                        val uri = DownloadHelper.publishToDownloads(context, saved, f.mime_type)
                        Toast.makeText(
                            context,
                            if (uri != null) "Saved to Downloads/Cloudbox/${saved.name}" else "Saved to app storage (${saved.absolutePath})",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                })
                if (!f.is_folder) {
                    ActionTile(Icons.Default.Share, "Share", onClick = { onShare(f.id) })
                }
                ActionTile(Icons.Default.Description, "Rename", onClick = { showRenameDialog = true })
            }

            if (showRenameDialog) {
                var name by remember { mutableStateOf(f.filename) }
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showRenameDialog = false },
                    title = { Text("Rename") },
                    text = {
                        androidx.compose.material3.OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            singleLine = true
                        )
                    },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = {
                            val newName = name.trim()
                            if (newName.isNotBlank() && newName != f.filename) {
                                viewModel.rename(f, newName)
                                file = f.copy(filename = newName)
                            }
                            showRenameDialog = false
                        }) { Text("Save") }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
                    }
                )
            }
        }
    }

    val previewLocal = previewFile
    val previewF = file
    if (previewF != null && showImageFull && previewLocal != null) {
        FullscreenImageViewer(
            imageFile = previewLocal,
            title = previewF.filename,
            onDismiss = { showImageFull = false }
        )
    }

    if (previewF != null && showVideoPlayer && previewLocal != null) {
        VideoPlayerModal(
            videoUri = Uri.fromFile(previewLocal),
            title = previewF.filename,
            onDismiss = { showVideoPlayer = false }
        )
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