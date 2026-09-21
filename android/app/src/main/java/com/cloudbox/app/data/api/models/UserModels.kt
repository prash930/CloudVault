package com.cloudbox.app.data.api.models

data class UserResponse(
    val id: Int,
    val email: String,
    val display_name: String,
    val role: String,
    val status: String,
    val storage_quota_bytes: Long,
    val storage_used_bytes: Long,
    val created_at: String,
    val has_avatar_1: Boolean = false,
    val has_avatar_2: Boolean = false
)

data class UpdateProfileRequest(
    val display_name: String? = null,
    val email: String? = null,
    val password: String? = null
)

data class StorageUsageResponse(
    val used_bytes: Long,
    val quota_bytes: Long,
    val used_formatted: String,
    val quota_formatted: String,
    val usage_percentage: Float
)
