package com.cloudbox.app.ui.screens.home

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cloudbox.app.ui.components.BackRow
import com.cloudbox.app.ui.components.CloudboxScreen
import com.cloudbox.app.ui.components.FileGlyphFrom
import com.cloudbox.app.ui.components.PillButton
import com.cloudbox.app.ui.components.formatBytes
import com.cloudbox.app.ui.theme.CloudBlue
import com.cloudbox.app.ui.theme.CloudCard
import com.cloudbox.app.ui.theme.CloudMuted
import com.cloudbox.app.ui.theme.CloudNavy

@Composable
fun UploadQueueScreen(
    onBack: () -> Unit,
    viewModel: HomeViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activeUploads = uiState.transfers.filter { it.direction == "Upload" }

    CloudboxScreen {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            BackRow("Upload Files", onBack)
            Spacer(Modifier.height(12.dp))

            if (uiState.isUploading) {
                Text("Uploading...", color = CloudNavy, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(activeUploads) { transfer ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CloudCard, RoundedCornerShape(16.dp))
                                .padding(12.dp)
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(transfer.name, color = CloudNavy, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                Spacer(Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { transfer.progress },
                                    modifier = Modifier.fillMaxWidth().height(6.dp),
                                    color = CloudBlue
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(if (transfer.status == "Complete") "Done" else "${(transfer.progress * 100).toInt()}%", color = CloudMuted, fontSize = 12.sp)
                        }
                    }
                }
            } else if (uiState.pendingUploads.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("No files selected", color = CloudMuted, fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Tap the upload button to pick files.", color = CloudMuted, fontSize = 14.sp)
                }
            } else {
                Text("Ready to upload (${uiState.pendingUploads.size})", color = CloudNavy, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    items(uiState.pendingUploads, key = { it.uri.toString() }) { pending ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CloudCard, RoundedCornerShape(16.dp))
                                .padding(12.dp)
                        ) {
                            FileGlyphFrom(pending.name, pending.mime)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(pending.name, color = CloudNavy, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                Text("${formatBytes(pending.size)}", color = CloudMuted, fontSize = 12.sp)
                            }
                            IconButton(onClick = { viewModel.removePending(pending.uri) }) {
                                Icon(Icons.Default.Close, contentDescription = "Remove", tint = CloudMuted)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                PillButton(
                    text = "Upload ${uiState.pendingUploads.size} file(s)",
                    onClick = {
                        viewModel.uploadPending(context.contentResolver) {
                            viewModel.consumeMessages()
                        }
                    }
                )
            }
        }
    }
}