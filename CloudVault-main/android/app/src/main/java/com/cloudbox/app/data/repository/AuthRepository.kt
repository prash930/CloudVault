package com.cloudbox.app.data.repository

import com.cloudbox.app.data.api.ApiClient
import com.cloudbox.app.data.api.models.ErrorResponse
import com.cloudbox.app.data.api.models.ForgotPasswordRequest
import com.cloudbox.app.data.api.models.LoginRequest
import com.cloudbox.app.data.api.models.RegisterRequest
import com.cloudbox.app.data.api.models.StorageUsageResponse
import com.cloudbox.app.data.api.models.TokenResponse
import com.cloudbox.app.data.api.models.UpdateProfileRequest
import com.cloudbox.app.data.api.models.UserResponse
import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okio.BufferedSink
import com.google.gson.Gson
import retrofit2.Response

class AuthRepository {
    private val api = ApiClient.authApi
    private val gson = Gson()
    
    private fun <T> handleResponse(response: Response<T>): Result<T> {
        if (response.isSuccessful) {
            response.body()?.let {
                return Result.success(it)
            }
        }
        val errorMsg = try {
            val error = gson.fromJson(response.errorBody()?.string(), ErrorResponse::class.java)
            error.detail
        } catch (e: Exception) {
            "An unknown error occurred"
        }
        return Result.failure(Exception(errorMsg))
    }
    
    suspend fun register(email: String, displayName: String, password: String): Result<String> {
        return try {
            val res = api.register(RegisterRequest(email, displayName, password))
            val result = handleResponse(res)
            if (result.isSuccess) {
                Result.success(result.getOrNull()?.message ?: "Success")
            } else {
                Result.failure(result.exceptionOrNull() ?: Exception("Unknown Error"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun login(email: String, password: String): Result<TokenResponse> {
        return try {
            val res = api.login(username = email, password = password)
            handleResponse(res)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun logout(): Result<String> {
        return try {
            val res = api.logout()
            val result = handleResponse(res)
            if (result.isSuccess) {
                Result.success(result.getOrNull()?.message ?: "Success")
            } else {
                Result.failure(result.exceptionOrNull() ?: Exception("Unknown Error"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun forgotPassword(email: String): Result<String> {
        return try {
            val res = api.forgotPassword(ForgotPasswordRequest(email))
            val result = handleResponse(res)
            if (result.isSuccess) {
                Result.success(result.getOrNull()?.message ?: "Success")
            } else {
                Result.failure(result.exceptionOrNull() ?: Exception("Unknown Error"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getMe(): Result<UserResponse> {
        return try {
            val res = api.getMe()
            handleResponse(res)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getStorageUsage(): Result<StorageUsageResponse> {
        return try {
            val res = api.getStorageUsage()
            handleResponse(res)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateProfile(displayName: String?, email: String?, password: String?): Result<UserResponse> {
        return try {
            handleResponse(api.updateProfile(UpdateProfileRequest(displayName, email, password?.ifBlank { null })))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadAvatar(contentResolver: ContentResolver, uri: Uri, slot: Int): Result<UserResponse> {
        return try {
            var name = "avatar.jpg"
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) name = cursor.getString(index)
            }
            val mime = contentResolver.getType(uri) ?: "image/jpeg"
            val body = object : RequestBody() {
                override fun contentType() = mime.toMediaTypeOrNull()
                override fun writeTo(sink: BufferedSink) {
                    contentResolver.openInputStream(uri)?.use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            sink.write(buffer, 0, read)
                        }
                    }
                }
            }
            handleResponse(api.uploadAvatar(slot, MultipartBody.Part.createFormData("file", name, body)))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
