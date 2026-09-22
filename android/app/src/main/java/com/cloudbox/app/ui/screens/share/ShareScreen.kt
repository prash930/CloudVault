package com.cloudbox.app.ui.screens.share

import android.content.Intent
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cloudbox.app.ui.components.BackRow
import com.cloudbox.app.ui.components.CloudboxScreen
import com.cloudbox.app.ui.components.FileGlyph
import com.cloudbox.app.ui.components.FileGlyphFrom
import com.cloudbox.app.ui.components.PillButton
import com.cloudbox.app.ui.screens.home.HomeViewModel
import com.cloudbox.app.ui.theme.CloudBlue
import com.cloudbox.app.ui.theme.CloudBorder
import com.cloudbox.app.ui.theme.CloudCard
import com.cloudbox.app.ui.theme.CloudMuted
import com.cloudbox.app.ui.theme.CloudNavy
import java.io.File

@Composable
fun ShareScreen(
    fileId: Int,
    onBack: () -> Unit,
    viewModel: HomeViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val file = viewModel.fileById(fileId)
    var email by remember { mutableStateOf("") }
    var sentTo by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadShared()
    }

    fun openInApp(share: com.cloudbox.app.data.api.models.ShareOut) {
        val dir = File(context.cacheDir, "shared_cache").apply { mkdirs() }
        viewModel.downloadSharedFile(share, dir, onReady = { saved ->
            try {
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    saved
                )
                val mimeType = android.webkit.MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(saved.extension.lowercase())
                    ?: share.mime_type
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
        }, onError = { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        })
    }

    CloudboxScreen {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            BackRow("Share File", onBack)
            Spacer(Modifier.height(12.dp))

            file?.let {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(CloudCard)
                        .padding(12.dp)
                ) {
                    FileGlyph(it)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(it.filename, color = CloudNavy, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(if (it.is_folder) "Folder" else it.mime_type ?: "file", color = CloudMuted, fontSize = 12.sp)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Text("Share with email", color = CloudNavy, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                placeholder = { Text("friend@example.com", color = CloudMuted) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = CloudCard,
                    unfocusedContainerColor = CloudCard,
                    focusedBorderColor = CloudBlue,
                    unfocusedBorderColor = CloudBorder
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            PillButton(
                text = "Send Share Invite",
                enabled = email.isNotBlank() && email.contains("@"),
                onClick = {
                    viewModel.shareWithEmail(fileId, email.trim()) {
                        sentTo = email.trim()
                        Toast.makeText(context, "Shared with ${email.trim()}", Toast.LENGTH_SHORT).show()
                    }
                    email = ""
                }
            )
            sentTo?.let {
                Spacer(Modifier.height(8.dp))
                Text("Shared with $it", color = CloudBlue, fontSize = 13.sp)
            }

            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Shared with you", color = CloudNavy, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = { viewModel.loadShared() }) { Text("Refresh") }
            }
            Spacer(Modifier.height(4.dp))

            if (uiState.shared.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                    Text("Nothing shared with you yet", color = CloudMuted, fontSize = 14.sp)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(uiState.shared, key = { it.id }) { share ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(CloudCard)
                                .clickable { openInApp(share) }
                                .padding(12.dp)
                        ) {
                            if (share.filename != null) {
                                FileGlyphFrom(share.filename, share.mime_type)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(share.filename ?: "Shared file", color = CloudNavy, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                Text(
                                    share.shared_with_email ?: "Shared with you",
                                    color = CloudMuted,
                                    fontSize = 12.sp
                                )
                            }
                            IconButton(onClick = { openInApp(share) }) {
                                Icon(Icons.Default.OpenInNew, contentDescription = "Open", tint = CloudBlue)
                            }
                        }
                    }
                }
            }
        }
    }
}