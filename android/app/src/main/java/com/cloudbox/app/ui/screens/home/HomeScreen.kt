package com.cloudbox.app.ui.screens.home

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PeopleAlt
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.cloudbox.app.BuildConfig
import com.cloudbox.app.data.api.models.CloudFile
import com.cloudbox.app.data.api.models.ShareOut
import com.cloudbox.app.data.local.TokenManager
import com.cloudbox.app.data.util.DownloadHelper
import com.cloudbox.app.ui.components.CloudBoxTopBar
import com.cloudbox.app.ui.components.FileGlyph
import com.cloudbox.app.ui.components.FileGlyphFrom
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
    Files("My Files", Icons.Default.Folder),
    Shared("Shared", Icons.Default.PeopleAlt),
    Profile("Profile", Icons.Default.Person)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onLogout: () -> Unit,
    onOpenUploadQueue: () -> Unit,
    onOpenShare: (Int) -> Unit,
    onOpenPreview: (Int) -> Unit,
    onOpenProfilePhotos: () -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var tab by remember { mutableStateOf(HomeTab.Home) }
    var showTrash by remember { mutableStateOf(false) }

    val multiPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.queueUploads(context.contentResolver, uris)
            onOpenUploadQueue()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadInitial()
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
                    onShowTrash = { showTrash = true },
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
                    onShare = onOpenShare,
                    onRename = viewModel::rename,
                    onTrash = viewModel::moveToTrash,
                    onTrashMultiple = viewModel::moveMultipleToTrash,
                    onShowTrash = { showTrash = true }
                )
                HomeTab.Shared -> SharedView(
                    shared = uiState.shared,
                    onRefresh = viewModel::loadShared,
                    onOpenShareUrl = { url ->
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } catch (e: Exception) {
                            Toast.makeText(context, "Could not open link", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                HomeTab.Profile -> ProfileView(
                    uiState = uiState,
                    onEditPhotos = onOpenProfilePhotos,
                    onLogout = onLogout,
                    viewModel = viewModel
                )
            }
        }
    }

    if (showTrash) {
        AlertDialog(
            onDismissRequest = { showTrash = false },
            title = { Text("Trash") },
            text = {
                if (uiState.trash.isEmpty()) {
                    Text("Trash is empty", color = CloudMuted)
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(uiState.trash, key = { it.id }) { file ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(CloudSoft)
                                    .padding(10.dp)
                            ) {
                                FileGlyph(file)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(file.filename, color = CloudNavy, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                    Text("${formatBytes(file.size_bytes)}", color = CloudMuted, fontSize = 12.sp)
                                }
                                IconButton(onClick = { viewModel.restore(file) }) {
                                    Icon(Icons.Default.Restore, contentDescription = "Restore", tint = CloudBlue)
                                }
                                IconButton(onClick = { viewModel.permanentlyDelete(file) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete forever", tint = CloudBlue)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTrash = false }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun HomeDashboard(
    uiState: HomeUiState,
    onFiles: () -> Unit,
    onUpload: () -> Unit,
    onOpenPreview: (Int) -> Unit,
    onShowTrash: () -> Unit,
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
            QuickAction(Icons.Default.Delete, "Trash", CloudBlue, onShowTrash, Modifier.weight(1f))
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
    onRename: (CloudFile, String) -> Unit,
    onTrash: (CloudFile) -> Unit,
    onTrashMultiple: (List<CloudFile>) -> Unit,
    onShowTrash: () -> Unit
) {
    var newFolderDialog by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<CloudFile>() }

    fun toggleSelect(file: CloudFile) {
        if (selected.contains(file)) selected.remove(file) else selected.add(file)
    }

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
            if (selectionMode) {
                IconButton(onClick = { selectionMode = false; selected.clear() }) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel", tint = CloudNavy)
                }
                Text("${selected.size} selected", color = CloudNavy, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                IconButton(
                    enabled = selected.isNotEmpty(),
                    onClick = { onTrashMultiple(selected.toList()); selectionMode = false; selected.clear() }
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Move selected to trash", tint = if (selected.isNotEmpty()) CloudBlue else CloudMuted)
                }
                IconButton(onClick = {
                    if (selected.size == uiState.files.size) selected.clear()
                    else {
                        selected.clear()
                        selected.addAll(uiState.files)
                    }
                }) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "Select all", tint = CloudBlue)
                }
            } else {
                if (uiState.selectedCategory == null && uiState.folderStack.isNotEmpty()) {
                    TextButton(onClick = onUp) { Text("Up: ${uiState.folderStack.last().filename}") }
                }
                Spacer(Modifier.weight(1f))
                SortMenu(uiState.sort, onSort)
                IconButton(onClick = onShowTrash) {
                    Icon(Icons.Default.Delete, contentDescription = "Trash", tint = CloudBlue)
                }
                IconButton(onClick = { selectionMode = true }) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "Select files", tint = CloudBlue)
                }
                IconButton(onClick = {
                    if (uiState.selectedCategory == null) newFolderDialog = true
                }) {
                    Icon(Icons.Default.Add, contentDescription = "New folder", tint = CloudBlue)
                }
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
                        onRename = onRename,
                        onTrash = onTrash,
                        selectionMode = selectionMode,
                        isSelected = file in selected,
                        onToggleSelect = { toggleSelect(file) }
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
    onShare: (Int) -> Unit,
    onRename: (CloudFile, String) -> Unit,
    onTrash: (CloudFile) -> Unit,
    selectionMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelect: () -> Unit = {}
) {
    var menu by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        FileRowCard(
            file = file,
            subtitle = if (file.is_folder) "Folder" else "${formatBytes(file.size_bytes)}  \u2022  ${formatDateTime(file.updated_at)}",
            onClick = { if (selectionMode) onToggleSelect() else onOpenPreview(file) },
            modifier = Modifier.fillMaxWidth(),
            selected = isSelected,
            leading = if (selectionMode) {
                {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggleSelect() }
                    )
                }
            } else {
                null
            }
        )
        Box(modifier = Modifier.align(Alignment.TopEnd)) {
            if (!selectionMode) {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Actions", tint = CloudMuted)
                }
            }
            DropdownMenu(expanded = menu && !selectionMode, onDismissRequest = { menu = false }) {
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
                    DropdownMenuItem(
                        text = { Text("Share") },
                        onClick = { menu = false; onShare(file.id) },
                        leadingIcon = { Icon(Icons.Default.PeopleAlt, null) }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Rename") },
                    onClick = { menu = false; rename = true },
                    leadingIcon = { Icon(Icons.Default.Description, null) }
                )
                DropdownMenuItem(
                    text = { Text("Move to Trash") },
                    onClick = { menu = false; onTrash(file) },
                    leadingIcon = { Icon(Icons.Default.Delete, null) }
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

@Composable
private fun SharedView(
    shared: List<ShareOut>,
    onRefresh: () -> Unit,
    onOpenShareUrl: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Shared with you", color = CloudNavy, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = onRefresh) { Text("Refresh") }
        }
        Spacer(Modifier.height(12.dp))
        if (shared.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                Text("Nothing shared with you yet", color = CloudMuted, fontSize = 14.sp)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(shared, key = { it.id }) { share ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(CloudCard)
                            .clickable { share.share_url?.let(onOpenShareUrl) }
                            .padding(12.dp)
                    ) {
                        if (share.filename != null) {
                            FileGlyphFrom(share.filename, share.mime_type)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(share.filename ?: "Shared file", color = CloudNavy, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text(
                                if (share.is_link) "Share link" else (share.shared_with_email ?: "Shared with you"),
                                color = CloudMuted,
                                fontSize = 12.sp
                            )
                        }
                        IconButton(onClick = { share.share_url?.let(onOpenShareUrl) }) {
                            Icon(Icons.Default.OpenInNew, contentDescription = "Open", tint = CloudBlue)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileView(
    uiState: HomeUiState,
    onEditPhotos: () -> Unit,
    onLogout: () -> Unit,
    viewModel: HomeViewModel
) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    var name by remember { mutableStateOf(uiState.userName) }
    var email by remember { mutableStateOf(uiState.userEmail) }
    var password by remember { mutableStateOf("") }
    var autoBackup by remember { mutableStateOf(TokenManager.isAutoBackupEnabled()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            AvatarCircle(hasAvatar = uiState.hasAvatar1, url = BuildConfig.BASE_URL + "auth/avatar/1", onClick = onEditPhotos)
        }
        Spacer(Modifier.height(12.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(uiState.userName, color = CloudNavy, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            IconButton(onClick = { focusRequester.requestFocus() }) {
                Icon(Icons.Default.Edit, contentDescription = "Edit your name", tint = CloudBlue, modifier = Modifier.size(18.dp))
            }
        }
        Text(uiState.userEmail, color = CloudMuted, fontSize = 13.sp, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)

        Spacer(Modifier.height(20.dp))
        StorageIndicator(uiState.usagePercentage, uiState.usedFormatted, uiState.quotaFormatted)

        Spacer(Modifier.height(20.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CloudCard)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Edit profile", color = CloudNavy, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Display name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    shape = RoundedCornerShape(14.dp)
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("New password (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )
                Spacer(Modifier.height(14.dp))
                TextButton(
                    onClick = {
                        viewModel.updateProfile(name.trim(), email.trim(), password) {
                            Toast.makeText(context, "Profile updated", Toast.LENGTH_SHORT).show()
                            password = ""
                        }
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Save changes", color = CloudBlue, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CloudCard)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f).padding(end = 16.dp)) {
                    Text("Auto Backup", color = CloudNavy, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = if (autoBackup) "Active: Backing up to Telegram Drive" else "Auto backup paused",
                        color = if (autoBackup) CloudBlue else CloudMuted,
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = autoBackup,
                    onCheckedChange = { isChecked ->
                        autoBackup = isChecked
                        TokenManager.setAutoBackupEnabled(isChecked)
                        Toast.makeText(context, if (isChecked) "Auto backup ON" else "Auto backup OFF", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Restore, contentDescription = null, tint = CloudBlue)
            Spacer(Modifier.width(6.dp))
            Text("Sign out", color = CloudBlue, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun AvatarCircle(hasAvatar: Boolean, url: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(88.dp)
            .clip(CircleShape)
            .background(CloudSoft)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (hasAvatar) {
            AsyncImage(
                model = url,
                contentDescription = "Avatar",
                modifier = Modifier.fillMaxSize().clip(CircleShape)
            )
        } else {
            Icon(Icons.Default.Person, contentDescription = null, tint = CloudMuted, modifier = Modifier.size(40.dp))
        }
        Icon(
            Icons.Default.Add,
            contentDescription = "Edit photos",
            tint = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(24.dp)
                .clip(CircleShape)
                .background(CloudBlue)
                .padding(4.dp)
        )
    }
}