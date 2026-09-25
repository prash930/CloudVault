package com.cloudbox.app.ui.screens.home

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.cloudbox.app.BuildConfig
import com.cloudbox.app.data.api.models.CloudFile
import com.cloudbox.app.data.local.TokenManager
import com.cloudbox.app.data.util.DownloadHelper
import com.cloudbox.app.ui.components.CloudBoxTopBar
import com.cloudbox.app.ui.components.FileGlyph
import com.cloudbox.app.ui.components.FileRowCard
import com.cloudbox.app.ui.components.SearchField
import com.cloudbox.app.ui.components.StorageIndicator
import com.cloudbox.app.ui.components.formatBytes
import com.cloudbox.app.ui.components.formatDateTime
import com.cloudbox.app.ui.theme.CloudBackground
import com.cloudbox.app.ui.theme.CloudBlue
import com.cloudbox.app.ui.theme.CloudCard
import com.cloudbox.app.ui.theme.CloudMuted
import com.cloudbox.app.ui.theme.CloudNavy
import com.cloudbox.app.ui.theme.CloudSoft

private enum class HomeTab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Default.Home),
    Files("My Files", Icons.Default.Folder)
}

private enum class FileCategory(val key: String, val label: String, val icon: ImageVector) {
    Images("images", "Images", Icons.Default.PhotoLibrary),
    Videos("videos", "Videos", Icons.Default.VideoLibrary),
    Music("music", "Music", Icons.Default.MusicNote),
    Documents("documents", "Documents", Icons.Default.Description),
    All("all", "Everything", Icons.Default.Folder)
}

private fun categoryFromKey(key: String?): FileCategory? =
    FileCategory.entries.firstOrNull { it.key == key }

private fun autoBackupPermissions(): Array<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onLogout: () -> Unit,
    onOpenUploadQueue: () -> Unit,
    onOpenPreview: (Int) -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var tab by remember { mutableStateOf(HomeTab.Home) }

    val multiPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.queueUploads(context.contentResolver, uris)
            onOpenUploadQueue()
        }
    }

    val backupPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) {
            viewModel.runAutoBackup(context.contentResolver)
        } else {
            Toast.makeText(context, "Permission needed for auto backup", Toast.LENGTH_SHORT).show()
        }
    }

    fun ensureAndRunAutoBackup() {
        val permissions = autoBackupPermissions()
        val granted = permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        if (granted) {
            viewModel.runAutoBackup(context.contentResolver)
        } else {
            backupPermissionLauncher.launch(permissions)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadInitial()
    }

    LaunchedEffect(Unit) {
        if (TokenManager.isAutoBackupEnabled()) {
            val permissions = autoBackupPermissions()
            val granted = permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
            if (granted) {
                viewModel.runAutoBackup(context.contentResolver)
            }
        }
    }

    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeMessages()
        }
    }
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.consumeMessages()
        }
    }

    Scaffold(
        topBar = { CloudBoxTopBar(title = "Cloudbox", onLogout = onLogout) },
        floatingActionButton = {
            if (tab == HomeTab.Home || tab == HomeTab.Files) {
                FloatingActionButton(
                    onClick = { multiPicker.launch("*/*") },
                    containerColor = CloudBlue,
                    contentColor = Color.White,
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.Upload, contentDescription = "Upload files")
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = CloudCard, tonalElevation = 8.dp) {
                HomeTab.entries.forEach { item ->
                    val selected = tab == item
                    NavigationBarItem(
                        selected = selected,
                        onClick = { tab = item },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = CloudBlue,
                            selectedTextColor = CloudBlue,
                            indicatorColor = CloudSoft,
                            unselectedIconColor = CloudMuted,
                            unselectedTextColor = CloudMuted
                        )
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (tab) {
                HomeTab.Home -> HomeDashboard(
                    uiState = uiState,
                    onFiles = { tab = HomeTab.Files },
                    onUpload = { multiPicker.launch("*/*") },
                    onOpenPreview = onOpenPreview,
                    onBackup = { ensureAndRunAutoBackup() },
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
                    onNewFolder = viewModel::createFolder,
                    onOpenFolder = viewModel::openFolder,
                    onUp = viewModel::goUp,
                    onOpenPreview = { file -> if (file.is_folder) viewModel.openFolder(file) else onOpenPreview(file.id) },
                    onDownload = { file ->
                        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
                        viewModel.download(file, dir) { saved ->
                            val uri = DownloadHelper.publishToDownloads(context, saved, file.mime_type)
                            Toast.makeText(
                                context,
                                if (uri != null) "Saved to Downloads/Cloudbox/${saved.name}" else "Saved to app storage (${saved.absolutePath})",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    onRename = viewModel::rename
                )
            }
        }
    }
}

@Composable
private fun HomeDashboard(
    uiState: HomeUiState,
    onFiles: () -> Unit,
    onUpload: () -> Unit,
    onOpenPreview: (Int) -> Unit,
    onBackup: () -> Unit,
    onCategory: (String?) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("Welcome, ${uiState.userName}", color = CloudNavy, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Manage your files from anywhere", color = CloudMuted, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(16.dp))

        StorageIndicator(uiState.usagePercentage, uiState.usedFormatted, uiState.quotaFormatted)
        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            QuickAction(Icons.Default.Upload, "Upload", CloudBlue, onUpload, Modifier.weight(1f))
            QuickAction(Icons.Default.Folder, "My Files", CloudBlue, onFiles, Modifier.weight(1f))
            QuickAction(Icons.Default.WorkspacePremium, "Auto Backup", CloudBlue, { onBackup() }, Modifier.weight(1f))
        }
        Spacer(Modifier.height(20.dp))

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Categories", color = CloudNavy, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(FileCategory.entries) { cat ->
                CategoryChip(cat, onClick = {
                    onCategory(if (cat.key == "all") null else cat.key)
                })
            }
        }

        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Recent", color = CloudNavy, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))

        if (uiState.recentFiles.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.Center) {
                Text("No recent files yet", color = CloudMuted, fontSize = 14.sp)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(uiState.recentFiles, key = { it.id }) { file ->
                    FileRowCard(
                        file = file,
                        subtitle = "${formatBytes(file.size_bytes)}  \u2022  ${file.mime_type ?: "file"}",
                        onClick = { onOpenPreview(file.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickAction(
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(CloudCard)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp)
    ) {
        Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(6.dp))
        Text(label, color = CloudNavy, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun CategoryChip(category: FileCategory, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(CloudSoft)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Icon(category.icon, contentDescription = null, tint = CloudBlue, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(6.dp))
        Text(category.label, color = CloudNavy, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun FilesView(
    uiState: HomeUiState,
    onSearch: (String) -> Unit,
    onSort: (String) -> Unit,
    onClearCategory: () -> Unit,
    onNewFolder: (String) -> Unit,
    onOpenFolder: (CloudFile) -> Unit,
    onUp: () -> Unit,
    onOpenPreview: (CloudFile) -> Unit,
    onDownload: (CloudFile) -> Unit,
    onShare: (Int) -> Unit,
    onRename: (CloudFile, String) -> Unit
) {
    var newFolderDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
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
            SearchField(
                value = uiState.search,
                onValueChange = onSearch,
                placeholder = "Search files and folders..."
            )
        }
        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (uiState.selectedCategory == null && uiState.folderStack.isNotEmpty()) {
                TextButton(onClick = onUp) { Text("Up: ${uiState.folderStack.last().filename}") }
            }
            Spacer(Modifier.weight(1f))
            SortMenu(uiState.sort, onSort)
            IconButton(onClick = {
                if (uiState.selectedCategory == null) newFolderDialog = true
            }) {
                Icon(Icons.Default.Add, contentDescription = "New folder", tint = CloudBlue)
            }
        }

if (uiState.isLoading) {
            Box(Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                androidx.compose.material3.CircularProgressIndicator(color = CloudBlue)
            }
        }

        if (uiState.files.isEmpty()) {
            if (!uiState.isLoading) {
                Box(Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (uiState.selectedCategory != null) "No ${categoryFromKey(uiState.selectedCategory)?.label?.lowercase()} found" else "This folder is empty",
                        color = CloudMuted
                    )
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                items(uiState.files, key = { it.id }) { file ->
                    FileListItem(
                        file = file,
                        onOpenPreview = onOpenPreview,
                        onDownload = onDownload,
                        onShare = onShare,
                        onRename = onRename
                    )
                }
            }
        }
    }

    if (newFolderDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { newFolderDialog = false },
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
                    if (name.isNotBlank()) onNewFolder(name)
                    newFolderDialog = false
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { newFolderDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SortMenu(current: String, onSort: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(Icons.Default.Sort, contentDescription = "Sort", tint = CloudBlue, modifier = Modifier.size(20.dp))
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        listOf("name", "date", "size", "type").forEach { sort ->
            DropdownMenuItem(
                text = {
                    Text(
                        if (sort == current) "${sort.replaceFirstChar { it.uppercase() }} (selected)"
                        else sort.replaceFirstChar { it.uppercase() }
                    )
                },
                onClick = {
                    expanded = false
                    onSort(sort)
                }
            )
        }
    }
}

@Composable
private fun FileListItem(
    file: CloudFile,
    onOpenPreview: (CloudFile) -> Unit,
    onDownload: (CloudFile) -> Unit,
    onRename: (CloudFile, String) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        FileRowCard(
            file = file,
            subtitle = if (file.is_folder) "Folder" else "${formatBytes(file.size_bytes)}  \u2022  ${formatDateTime(file.updated_at)}",
            onClick = { onOpenPreview(file) },
            modifier = Modifier.fillMaxWidth()
        )
        Box(modifier = Modifier.align(Alignment.TopEnd)) {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Actions", tint = CloudMuted)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(if (file.is_folder) "Open" else "Preview") },
                    onClick = { menu = false; onOpenPreview(file) },
                    leadingIcon = { Icon(Icons.Default.OpenInNew, null) }
                )
                if (!file.is_folder) {
                    DropdownMenuItem(
                        text = { Text("Download") },
                        onClick = { menu = false; onDownload(file) },
                        leadingIcon = { Icon(Icons.Default.Download, null) }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Rename") },
                    onClick = { menu = false; rename = true },
                    leadingIcon = { Icon(Icons.Default.Description, null) }
                )
            }
        }
    }

    if (rename) {
        var name by remember { mutableStateOf(file.filename) }
        AlertDialog(
            onDismissRequest = { rename = false },
            title = { Text("Rename") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = { onRename(file, name); rename = false }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { rename = false }) { Text("Cancel") } }
        )
    }
}