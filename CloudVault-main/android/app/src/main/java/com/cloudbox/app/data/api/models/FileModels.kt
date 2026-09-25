package com.cloudbox.app.data.api.models

data class CloudFile(
    val id: Int,
    val filename: String,
    val original_filename: String,
    val mime_type: String?,
    val size_bytes: Long,
    val is_folder: Boolean,
    val parent_folder_id: Int?,
    val is_trashed: Boolean,
    val moderation_status: String,
    val created_at: String,
    val updated_at: String
)

data class FileListResponse(
    val items: List<CloudFile>,
    val total: Int
)

data class CreateFolderRequest(
    val name: String,
    val parent_folder_id: Int? = null
)

data class RenameRequest(val filename: String)

data class MoveRequest(val parent_folder_id: Int?)
