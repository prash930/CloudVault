package com.cloudbox.app.ui.screens.home

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloudbox.app.data.api.models.CloudFile
import com.cloudbox.app.data.api.models.ShareOut
import com.cloudbox.app.data.local.TokenManager
import com.cloudbox.app.data.repository.AuthRepository
import com.cloudbox.app.data.repository.FileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class TransferItem(
    val name: String,
    val direction: String,
    val progress: Float,
    val status: String
)

data class PendingUpload(
    val uri: Uri,
    val name: String,
    val size: Long,
    val mime: String
)

data class HomeUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    val userName: String = "",
    val userEmail: String = "",
    val storageUsed: Long = 0,
    val storageQuota: Long = 0,
    val usedFormatted: String = "0 B",
    val quotaFormatted: String = "50.0 GB",
    val usagePercentage: Float = 0f,
    val files: List<CloudFile> = emptyList(),
    val recentFiles: List<CloudFile> = emptyList(),
    val trash: List<CloudFile> = emptyList(),
    val shared: List<ShareOut> = emptyList(),
    val folders: List<CloudFile> = emptyList(),
    val currentFolderId: Int? = null,
    val folderStack: List<CloudFile> = emptyList(),
    val search: String = "",
    val homeSearch: String = "",
    val sort: String = "name",
    val direction: String = "asc",
    val transfers: List<TransferItem> = emptyList(),
    val selectedCategory: String? = null,
    val pendingUploads: List<PendingUpload> = emptyList(),
    val isUploading: Boolean = false,
    val shareLink: String? = null,
    val hasAvatar1: Boolean = false,
    val hasAvatar2: Boolean = false,
    val profileSaving: Boolean = false
)

class HomeViewModel : ViewModel() {
    private val authRepository = AuthRepository()
    private val fileRepository = FileRepository()
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun loadInitial() {
        loadUserInfo()
        loadStorageUsage()
        loadFiles()
        loadRecent()
        loadTrash()
        loadShared()
        refreshMe()
    }

    fun loadUserInfo() {
        val name = TokenManager.getUserName() ?: "User"
        val email = TokenManager.getUserEmail() ?: ""
        _uiState.update {
            it.copy(
                userName = name,
                userEmail = email,
                hasAvatar1 = TokenManager.hasAvatar1(),
                hasAvatar2 = TokenManager.hasAvatar2()
            )
        }
    }

    fun refreshMe() {
        viewModelScope.launch {
            val result = authRepository.getMe()
            result.getOrNull()?.let { user ->
                TokenManager.saveUserInfo(user.email, user.display_name, user.role)
                TokenManager.saveAvatarFlags(user.has_avatar_1, user.has_avatar_2)
                _uiState.update {
                    it.copy(
                        userName = user.display_name,
                        userEmail = user.email,
                        hasAvatar1 = user.has_avatar_1,
                        hasAvatar2 = user.has_avatar_2
                    )
                }
            }
        }
    }

    fun loadStorageUsage() {
        viewModelScope.launch {
            val result = authRepository.getStorageUsage()
            result.getOrNull()?.let { response ->
                _uiState.update {
                    it.copy(
                        storageUsed = response.used_bytes,
                        storageQuota = response.quota_bytes,
                        usedFormatted = response.used_formatted,
                        quotaFormatted = response.quota_formatted,
                        usagePercentage = response.usage_percentage
                    )
                }
            }
        }
    }

    fun loadFiles() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val state = _uiState.value
            val parent = if (state.selectedCategory != null) null else state.currentFolderId
            val recursive = state.selectedCategory != null
            val result = fileRepository.listFiles(parent, state.search.ifBlank { null }, state.sort, state.direction, state.selectedCategory, recursive)
            if (result.isSuccess) {
                val items = result.getOrNull()?.items.orEmpty()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        files = items,
                        folders = items.filter { file -> file.is_folder }
                    )
                }
            } else {
                _uiState.update { it.copy(isLoading = false, error = result.exceptionOrNull()?.message) }
            }
        }
    }

    fun loadRecent() {
        viewModelScope.launch {
            val result = fileRepository.listFiles(null, null, "date", "desc", null, true)
            result.getOrNull()?.let { response ->
                _uiState.update { it.copy(recentFiles = response.items.filter { file -> !file.is_folder }.take(20)) }
            }
        }
    }

    fun loadTrash() {
        viewModelScope.launch {
            val result = fileRepository.trash()
            result.getOrNull()?.let { response ->
                _uiState.update { it.copy(trash = response.items) }
            }
        }
    }

    fun loadShared() {
        viewModelScope.launch {
            val result = fileRepository.sharedWithMe()
            result.getOrNull()?.let { response ->
                _uiState.update { it.copy(shared = response.items) }
            }
        }
    }

    fun setSearch(value: String) {
        _uiState.update { it.copy(search = value) }
        loadFiles()
    }

    fun setHomeSearch(value: String) {
        _uiState.update { it.copy(homeSearch = value) }
        if (value.isBlank()) {
            loadRecent()
        } else {
            viewModelScope.launch {
                val result = fileRepository.listFiles(null, value, "date", "desc", null, true)
                result.getOrNull()?.let { response ->
                    _uiState.update { it.copy(recentFiles = response.items.take(25)) }
                }
            }
        }
    }

    fun setSort(value: String) {
        val direction = if (value == "date" || value == "size") "desc" else "asc"
        _uiState.update { it.copy(sort = value, direction = direction) }
        loadFiles()
    }

    fun setCategory(category: String?) {
        _uiState.update { it.copy(selectedCategory = category, currentFolderId = null, folderStack = emptyList(), search = "") }
        loadFiles()
    }

    fun openFolder(folder: CloudFile) {
        if (!folder.is_folder) return
        _uiState.update {
            it.copy(currentFolderId = folder.id, folderStack = it.folderStack + folder, search = "")
        }
        loadFiles()
    }

    fun goUp() {
        val stack = _uiState.value.folderStack.dropLast(1)
        _uiState.update { it.copy(folderStack = stack, currentFolderId = stack.lastOrNull()?.id) }
        loadFiles()
    }

    fun createFolder(name: String) {
        viewModelScope.launch {
            val result = fileRepository.createFolder(name, _uiState.value.currentFolderId)
            if (result.isSuccess) {
                loadFiles()
                _uiState.update { it.copy(successMessage = "Folder created") }
            } else {
                _uiState.update { it.copy(error = result.exceptionOrNull()?.message) }
            }
        }
    }

    fun queueUploads(contentResolver: ContentResolver, uris: List<Uri>) {
        val pending = uris.map { uri ->
            val meta = fileRepository.fileMeta(contentResolver, uri)
            PendingUpload(uri, meta.first, meta.third, meta.second)
        }
        _uiState.update { it.copy(pendingUploads = it.pendingUploads + pending) }
    }

    fun removePending(uri: Uri) {
        _uiState.update { it.copy(pendingUploads = it.pendingUploads.filterNot { item -> item.uri == uri }) }
    }

    fun clearPending() {
        _uiState.update { it.copy(pendingUploads = emptyList()) }
    }

    fun uploadPending(contentResolver: ContentResolver, onDone: () -> Unit) {
        val pending = _uiState.value.pendingUploads
        if (pending.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isUploading = true, error = null) }
            var failed = 0
            pending.forEach { item ->
                upsertTransfer(TransferItem(item.name, "Upload", 0f, "Running"))
                val result = fileRepository.upload(contentResolver, item.uri, _uiState.value.currentFolderId) { progress ->
                    upsertTransfer(TransferItem(item.name, "Upload", progress, "Running"))
                }
                if (result.isSuccess) {
                    upsertTransfer(TransferItem(item.name, "Upload", 1f, "Complete"))
                } else {
                    failed += 1
                    upsertTransfer(TransferItem(item.name, "Upload", 0f, "Failed"))
                }
            }
            _uiState.update {
                it.copy(
                    isUploading = false,
                    pendingUploads = emptyList(),
                    error = if (failed > 0) "$failed file(s) failed to upload" else null,
                    successMessage = if (failed == 0) "Upload complete" else null
                )
            }
            loadFiles()
            loadRecent()
            loadStorageUsage()
            onDone()
        }
    }

    fun upload(contentResolver: ContentResolver, uri: Uri) {
        queueUploads(contentResolver, listOf(uri))
        uploadPending(contentResolver) {}
    }

    fun download(file: CloudFile, targetDir: File) {
        viewModelScope.launch {
            upsertTransfer(TransferItem(file.filename, "Download", 0f, "Running"))
            val result = fileRepository.download(file, targetDir) { progress ->
                upsertTransfer(TransferItem(file.filename, "Download", progress, "Running"))
            }
            if (result.isSuccess) {
                upsertTransfer(TransferItem(file.filename, "Download", 1f, "Complete"))
                _uiState.update { it.copy(successMessage = "Downloaded ${file.filename}") }
            } else {
                upsertTransfer(TransferItem(file.filename, "Download", 0f, "Failed"))
                _uiState.update { it.copy(error = result.exceptionOrNull()?.message) }
            }
        }
    }

    fun openFile(file: CloudFile, cacheDir: File, onReady: (File) -> Unit) {
        viewModelScope.launch {
            val result = fileRepository.download(file, cacheDir) { }
            if (result.isSuccess) {
                val savedFile = File(cacheDir, file.filename)
                onReady(savedFile)
            } else {
                _uiState.update { it.copy(error = result.exceptionOrNull()?.message) }
            }
        }
    }

    fun rename(file: CloudFile, filename: String) {
        viewModelScope.launch {
            val result = fileRepository.rename(file.id, filename)
            if (result.isSuccess) loadFiles() else _uiState.update { it.copy(error = result.exceptionOrNull()?.message) }
        }
    }

    fun moveFile(file: CloudFile, parentFolderId: Int?) {
        viewModelScope.launch {
            val result = fileRepository.move(file.id, parentFolderId)
            if (result.isSuccess) {
                loadFiles()
                _uiState.update { it.copy(successMessage = "Moved") }
            } else {
                _uiState.update { it.copy(error = result.exceptionOrNull()?.message) }
            }
        }
    }

    fun moveToTrash(file: CloudFile) {
        viewModelScope.launch {
            val result = fileRepository.moveToTrash(file.id)
            if (result.isSuccess) {
                loadFiles()
                loadRecent()
                loadTrash()
            } else {
                _uiState.update { it.copy(error = result.exceptionOrNull()?.message) }
            }
        }
    }

    fun restore(file: CloudFile) {
        viewModelScope.launch {
            val result = fileRepository.restore(file.id)
            if (result.isSuccess) {
                loadTrash()
                loadFiles()
            } else {
                _uiState.update { it.copy(error = result.exceptionOrNull()?.message) }
            }
        }
    }

    fun permanentlyDelete(file: CloudFile) {
        viewModelScope.launch {
            val result = fileRepository.permanentlyDelete(file.id)
            if (result.isSuccess) {
                loadTrash()
                loadStorageUsage()
            } else {
                _uiState.update { it.copy(error = result.exceptionOrNull()?.message) }
            }
        }
    }

    fun shareWithEmail(fileId: Int, email: String, onDone: () -> Unit) {
        viewModelScope.launch {
            val result = fileRepository.shareWithEmail(fileId, email)
            if (result.isSuccess) {
                loadShared()
                _uiState.update { it.copy(successMessage = "Shared with $email") }
                onDone()
            } else {
                _uiState.update { it.copy(error = result.exceptionOrNull()?.message) }
            }
        }
    }

    fun createShareLink(fileId: Int) {
        viewModelScope.launch {
            val result = fileRepository.createShareLink(fileId)
            if (result.isSuccess) {
                _uiState.update { it.copy(shareLink = result.getOrNull()?.share_url, successMessage = "Link created") }
            } else {
                _uiState.update { it.copy(error = result.exceptionOrNull()?.message) }
            }
        }
    }

    fun clearShareLink() {
        _uiState.update { it.copy(shareLink = null) }
    }

    fun updateProfile(name: String, email: String, password: String, onDone: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(profileSaving = true, error = null) }
            val result = authRepository.updateProfile(name, email, password.ifBlank { null })
            if (result.isSuccess) {
                val user = result.getOrNull()!!
                TokenManager.saveUserInfo(user.email, user.display_name, user.role)
                _uiState.update {
                    it.copy(
                        profileSaving = false,
                        userName = user.display_name,
                        userEmail = user.email,
                        successMessage = "Profile updated"
                    )
                }
                onDone()
            } else {
                _uiState.update { it.copy(profileSaving = false, error = result.exceptionOrNull()?.message) }
            }
        }
    }

    fun uploadAvatar(contentResolver: ContentResolver, uri: Uri, slot: Int) {
        viewModelScope.launch {
            val result = authRepository.uploadAvatar(contentResolver, uri, slot)
            if (result.isSuccess) {
                val user = result.getOrNull()!!
                TokenManager.saveAvatarFlags(user.has_avatar_1, user.has_avatar_2)
                _uiState.update { it.copy(hasAvatar1 = user.has_avatar_1, hasAvatar2 = user.has_avatar_2) }
            } else {
                _uiState.update { it.copy(error = result.exceptionOrNull()?.message) }
            }
        }
    }

    fun consumeMessages() {
        _uiState.update { it.copy(error = null, successMessage = null) }
    }

    fun fileById(id: Int): CloudFile? {
        return _uiState.value.files.firstOrNull { it.id == id }
            ?: _uiState.value.recentFiles.firstOrNull { it.id == id }
            ?: _uiState.value.trash.firstOrNull { it.id == id }
    }

    private fun upsertTransfer(item: TransferItem) {
        _uiState.update { state ->
            val rest = state.transfers.filterNot { it.name == item.name && it.direction == item.direction }
            state.copy(transfers = listOf(item) + rest)
        }
    }
}
