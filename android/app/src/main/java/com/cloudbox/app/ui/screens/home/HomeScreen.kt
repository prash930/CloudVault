package com.cloudbox.app.ui.screens.home

import android.content.Intent
import android.os.Environment
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.cloudbox.app.data.local.TokenManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cloudbox.app.data.api.models.CloudFile
import com.cloudbox.app.ui.components.CloudBoxTopBar
import com.cloudbox.app.ui.components.StorageIndicator
import java.io.File

private enum class HomeTab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Default.Home),
    Files("My Files", Icons.Default.Folder),
    Recent("Recent", Icons.Default.Schedule),
    Transfers("Transfers", Icons.Default.Upload),
    Trash("Trash", Icons.Default.Delete),
    Profile("Profile", Icons.Default.Person)
}

private enum class FileCategory(val key: String, val label: String, val icon: ImageVector) {
    Images("images", "Images", Icons.Default.PhotoLibrary),
    Videos("videos", "Videos", Icons.Default.VideoLibrary),
    Music("music", "Music", Icons.Default.MusicNote),
    Documents("documents", "Documents", Icons.Default.Description),
    Emails("emails", "Emails", Icons.Default.AlternateEmail),
    Passwords("passwords", "Passwords", Icons.Default.Key)
}

private fun categoryFromKey(key: String?): FileCategory? =
    FileCategory.entries.firstOrNull { it.key == key }

private fun iconForMime(mime: String?): ImageVector {
    val m = mime ?: return Icons.Default.InsertDriveFile
    return when {
        m.startsWith("image/") -> Icons.Default.Image
        m.startsWith("video/") -> Icons.Default.VideoLibrary
        m.startsWith("audio/") -> Icons.Default.AudioFile
        m.startsWith("message/rfc822") ||
            m.startsWith("application/vnd.ms-outlook") ||
            m.startsWith("application/x-emlx") ||
            m.startsWith("application/mbox") -> Icons.Default.AlternateEmail
        m.startsWith("application/x-sqlite3") ||
            m.startsWith("application/x-keepass") ||
            m.startsWith("application/x-keepass2") -> Icons.Default.Key
        m.startsWith("application/pdf") ||
            m.startsWith("application/msword") ||
            m.startsWith("application/vnd.openxmlformats") ||
            m.startsWith("application/vnd.oasis.opendocument") ||
            m.startsWith("application/vnd.ms-excel") ||
            m.startsWith("application/vnd.ms-powerpoint") ||
            m.startsWith("application/rtf") ||
            m.startsWith("text/") -> Icons.Default.Description
        else -> Icons.Default.InsertDriveFile
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onLogout: () -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var tab by remember { mutableStateOf(HomeTab.Home) }
    var folderDialog by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.upload(context.contentResolver, it) }
    }

    fun openFile(file: CloudFile) {
        val cacheDir = File(context.cacheDir, "opened").apply { mkdirs() }
        viewModel.openFile(file, cacheDir) { savedFile ->
            try {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", savedFile)
                val mimeType = MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(savedFile.extension.lowercase())
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
    }

    LaunchedEffect(Unit) {
        viewModel.loadInitial()
    }

    Scaffold(
        topBar = { CloudBoxTopBar(title = "CloudBox", onLogout = onLogout) },
        floatingActionButton = {
            if (tab == HomeTab.Files || tab == HomeTab.Home) {
                FloatingActionButton(onClick = { picker.launch("*/*") }) {
                    Icon(Icons.Default.Upload, contentDescription = "Upload")
                }
            }
        },
        bottomBar = {
            NavigationBar {
                HomeTab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) }
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            when (tab) {
                HomeTab.Home -> HomeDashboard(
                    uiState,
                    onFiles = { tab = HomeTab.Files },
                    onUpload = { picker.launch("*/*") },
                    onCategory = { category ->
                        viewModel.setCategory(category)
                        tab = HomeTab.Files
                    }
                )
                HomeTab.Files -> FilesView(
                    uiState = uiState,
                    onSearch = viewModel::setSearch,
                    onSort = viewModel::setSort,
                    onClearCategory = { viewModel.setCategory(null) },
                    onNewFolder = { folderDialog = true },
                    onOpenFolder = viewModel::openFolder,
                    onUp = viewModel::goUp,
                    onOpenFile = ::openFile,
                    onDownload = { file ->
                        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
                        viewModel.download(file, dir)
                    },
                    onRename = viewModel::rename,
                    onTrash = viewModel::moveToTrash
                )
                HomeTab.Recent -> FileList(
                    files = uiState.files.sortedByDescending { it.updated_at }.take(25),
                    empty = "No recent files",
                    onOpenFolder = viewModel::openFolder,
                    onOpenFile = ::openFile,
                    onDownload = { file ->
                        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
                        viewModel.download(file, dir)
                    },
                    onRename = viewModel::rename,
                    onTrash = viewModel::moveToTrash
                )
                HomeTab.Transfers -> TransfersView(uiState.transfers)
                HomeTab.Trash -> TrashView(
                    files = uiState.trash,
                    onRestore = viewModel::restore,
                    onDelete = viewModel::permanentlyDelete
                )
                HomeTab.Profile -> ProfileView(uiState)
            }
        }
    }

    if (folderDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { folderDialog = false },
            title = { Text("New Folder") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Folder name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) viewModel.createFolder(name)
                    folderDialog = false
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { folderDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun HomeDashboard(
    uiState: HomeUiState,
    onFiles: () -> Unit,
    onUpload: () -> Unit,
    onCategory: (String) -> Unit
) {
    Text("Welcome, ${uiState.userName}", style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(16.dp))
    StorageIndicator(uiState.usagePercentage, uiState.usedFormatted, uiState.quotaFormatted)
    Spacer(Modifier.height(24.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Button(onClick = onUpload, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.Upload, contentDescription = null)
            Text("Upload")
        }
        Button(onClick = onFiles, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.Folder, contentDescription = null)
            Text("My Files")
        }
    }
    Spacer(Modifier.height(24.dp))
    Text("Categories", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(12.dp))
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        val cats = FileCategory.entries
        listOf(0, 2, 4).forEach { rowStart ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                cats.slice(rowStart until rowStart + 2).forEach { cat ->
                    ElevatedCard(
                        onClick = { onCategory(cat.key) },
                        modifier = Modifier.weight(1f).aspectRatio(1f)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(cat.icon, contentDescription = null, modifier = Modifier.size(40.dp))
                            Spacer(Modifier.height(8.dp))
                            Text(cat.label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilesView(
    uiState: HomeUiState,
    onSearch: (String) -> Unit,
    onSort: (String) -> Unit,
    onClearCategory: () -> Unit,
    onNewFolder: () -> Unit,
    onOpenFolder: (CloudFile) -> Unit,
    onUp: () -> Unit,
    onOpenFile: (CloudFile) -> Unit,
    onDownload: (CloudFile) -> Unit,
    onRename: (CloudFile, String) -> Unit,
    onTrash: (CloudFile) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
    categoryFromKey(uiState.selectedCategory)?.let { cat ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        ) {
            AssistChip(
                onClick = {},
                label = { Text("Showing: ${cat.label}") },
                leadingIcon = { Icon(cat.icon, contentDescription = null) }
            )
            TextButton(onClick = onClearCategory) { Text("Clear") }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = uiState.search,
            onValueChange = onSearch,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            label = { Text("Search") },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
        SortMenu(uiState.sort, onSort)
        if (uiState.selectedCategory == null) {
            IconButton(onClick = onNewFolder) {
                Icon(Icons.Default.Add, contentDescription = "New folder")
            }
        }
    }
    if (uiState.selectedCategory == null && uiState.folderStack.isNotEmpty()) {
        TextButton(onClick = onUp) { Text("Up from ${uiState.folderStack.last().filename}") }
    }
    if (uiState.isLoading) {
        CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
    }
    uiState.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    FileList(uiState.files, if (uiState.selectedCategory != null) "No ${categoryFromKey(uiState.selectedCategory)?.label?.lowercase()} found" else "This folder is empty", onOpenFolder, onOpenFile, onDownload, onRename, onTrash)
    }
}

@Composable
private fun SortMenu(current: String, onSort: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(Icons.Default.Sort, contentDescription = "Sort")
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        listOf("name", "date", "size", "type").forEach { sort ->
            DropdownMenuItem(
                text = { Text(if (sort == current) "${sort.replaceFirstChar { it.uppercase() }} (selected)" else sort.replaceFirstChar { it.uppercase() }) },
                onClick = {
                    expanded = false
                    onSort(sort)
                }
            )
        }
    }
}

@Composable
private fun FileList(
    files: List<CloudFile>,
    empty: String,
    onOpenFolder: (CloudFile) -> Unit,
    onOpenFile: (CloudFile) -> Unit,
    onDownload: (CloudFile) -> Unit,
    onRename: (CloudFile, String) -> Unit,
    onTrash: (CloudFile) -> Unit
) {
    if (files.isEmpty()) {
        Text(empty, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 24.dp))
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
        items(files, key = { it.id }) { file ->
            FileRow(file, onOpenFolder, onOpenFile, onDownload, onRename, onTrash)
        }
    }
}

@Composable
private fun FileRow(
    file: CloudFile,
    onOpenFolder: (CloudFile) -> Unit,
    onOpenFile: (CloudFile) -> Unit,
    onDownload: (CloudFile) -> Unit,
    onRename: (CloudFile, String) -> Unit,
    onTrash: (CloudFile) -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    Card(
        onClick = {
            if (file.is_folder) onOpenFolder(file) else onOpenFile(file)
        },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
        ) {
            Icon(
                if (file.is_folder) Icons.Default.Folder else iconForMime(file.mime_type),
                contentDescription = null,
                modifier = Modifier.size(32.dp)
            )
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(file.filename, style = MaterialTheme.typography.bodyLarge)
                Text(fileDetail(file), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Actions") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (!file.is_folder) {
                    DropdownMenuItem(text = { Text("Open") }, onClick = { menu = false; onOpenFile(file) }, leadingIcon = { Icon(Icons.Default.OpenInNew, null) })
                    DropdownMenuItem(text = { Text("Download") }, onClick = { menu = false; onDownload(file) }, leadingIcon = { Icon(Icons.Default.Download, null) })
                }
                DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; rename = true })
                DropdownMenuItem(text = { Text("Move to Trash") }, onClick = { menu = false; onTrash(file) }, leadingIcon = { Icon(Icons.Default.Delete, null) })
            }
        }
    }
    if (rename) {
        var name by remember { mutableStateOf(file.filename) }
        AlertDialog(
            onDismissRequest = { rename = false },
            title = { Text("Rename") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { onRename(file, name); rename = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { rename = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun TransfersView(transfers: List<TransferItem>) {
    if (transfers.isEmpty()) {
        Text("No transfers yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(transfers) { transfer ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("${transfer.direction}: ${transfer.name}")
                    LinearProgressIndicator(progress = { transfer.progress }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    Text(transfer.status, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun TrashView(files: List<CloudFile>, onRestore: (CloudFile) -> Unit, onDelete: (CloudFile) -> Unit) {
    if (files.isEmpty()) {
        Text("Trash is empty", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(files, key = { it.id }) { file ->
            Card(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(file.filename)
                        Text(fileDetail(file), style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { onRestore(file) }) { Icon(Icons.Default.Restore, contentDescription = "Restore") }
                    IconButton(onClick = { onDelete(file) }) { Icon(Icons.Default.Delete, contentDescription = "Delete permanently") }
                }
            }
        }
    }
}

@Composable
private fun ProfileView(uiState: HomeUiState) {
    var autoBackup by remember { mutableStateOf(TokenManager.isAutoBackupEnabled()) }
    val context = LocalContext.current

    Icon(Icons.Default.WorkspacePremium, contentDescription = null, modifier = Modifier.size(40.dp))
    Text(uiState.userName, style = MaterialTheme.typography.titleLarge)
    Text(uiState.userEmail, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(16.dp))
    StorageIndicator(uiState.usagePercentage, uiState.usedFormatted, uiState.quotaFormatted)

    Spacer(Modifier.height(24.dp))
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                Text(
                    text = "Auto Backup",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = if (autoBackup) "Active: Backing up to Telegram Drive" else "Auto backup paused",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (autoBackup) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Automatically uploads photos and files until toggled off.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = autoBackup,
                onCheckedChange = { isChecked ->
                    autoBackup = isChecked
                    TokenManager.setAutoBackupEnabled(isChecked)
                    Toast.makeText(
                        context,
                        if (isChecked) "Auto backup turned ON" else "Auto backup turned OFF",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )
        }
    }
}

private fun fileDetail(file: CloudFile): String {
    return if (file.is_folder) "Folder" else "${file.size_bytes} bytes - ${file.mime_type ?: "file"}"
}
