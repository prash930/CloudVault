package com.cloudbox.app.ui.screens.home

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloudbox.app.data.api.models.CloudFile
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

data class HomeUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val userName: String = "",
    val userEmail: String = "",
    val storageUsed: Long = 0,
    val storageQuota: Long = 0,
    val usedFormatted: String = "0 B",
    val quotaFormatted: String = "50.0 GB",
    val usagePercentage: Float = 0f,
    val files: List<CloudFile> = emptyList(),
    val trash: List<CloudFile> = emptyList(),
    val currentFolderId: Int? = null,
    val folderStack: List<CloudFile> = emptyList(),
    val search: String = "",
    val sort: String = "name",
    val direction: String = "asc",
    val transfers: List<TransferItem> = emptyList()
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
        loadTrash()
    }

    fun loadUserInfo() {
        val name = TokenManager.getUserName() ?: "User"
        val email = TokenManager.getUserEmail() ?: ""
        _uiState.update { it.copy(userName = name, userEmail = email) }
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
            val result = fileRepository.listFiles(state.currentFolderId, state.search.ifBlank { null }, state.sort, state.direction)
            if (result.isSuccess) {
                _uiState.update { it.copy(isLoading = false, files = result.getOrNull()?.items.orEmpty()) }
            } else {
                _uiState.update { it.copy(isLoading = false, error = result.exceptionOrNull()?.message) }
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

    fun setSearch(value: String) {
        _uiState.update { it.copy(search = value) }
        loadFiles()
    }

    fun setSort(value: String) {
        _uiState.update { it.copy(sort = value) }
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
            } else {
                _uiState.update { it.copy(error = result.exceptionOrNull()?.message) }
            }
        }
    }

    fun upload(contentResolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            val transferName = uri.lastPathSegment ?: "Upload"
            upsertTransfer(TransferItem(transferName, "Upload", 0f, "Running"))
            val result = fileRepository.upload(contentResolver, uri, _uiState.value.currentFolderId) { progress ->
                upsertTransfer(TransferItem(transferName, "Upload", progress, "Running"))
            }
            if (result.isSuccess) {
                upsertTransfer(TransferItem(result.getOrNull()?.filename ?: transferName, "Upload", 1f, "Complete"))
                loadFiles()
                loadStorageUsage()
            } else {
                upsertTransfer(TransferItem(transferName, "Upload", 0f, "Failed"))
                _uiState.update { it.copy(error = result.exceptionOrNull()?.message) }
            }
        }
    }

    fun download(file: CloudFile, targetDir: File) {
        viewModelScope.launch {
            upsertTransfer(TransferItem(file.filename, "Download", 0f, "Running"))
            val result = fileRepository.download(file, targetDir) { progress ->
                upsertTransfer(TransferItem(file.filename, "Download", progress, "Running"))
            }
            if (result.isSuccess) {
                upsertTransfer(TransferItem(file.filename, "Download", 1f, "Complete"))
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

    fun moveToTrash(file: CloudFile) {
        viewModelScope.launch {
            val result = fileRepository.moveToTrash(file.id)
            if (result.isSuccess) {
                loadFiles()
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

    private fun upsertTransfer(item: TransferItem) {
        _uiState.update { state ->
            val rest = state.transfers.filterNot { it.name == item.name && it.direction == item.direction }
            state.copy(transfers = listOf(item) + rest)
        }
    }
}
