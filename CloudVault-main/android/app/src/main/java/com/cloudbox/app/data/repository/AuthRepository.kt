package com.cloudbox.app.data.repository

import com.cloudbox.app.data.api.ApiClient
import com.cloudbox.app.data.api.models.ErrorResponse
import com.cloudbox.app.data.api.models.RegisterRequest
import com.cloudbox.app.data.api.models.StorageUsageResponse
import com.cloudbox.app.data.api.models.TokenResponse
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
    
    suspend fun getStorageUsage(): Result<StorageUsageResponse> {
        return try {
            val res = api.getStorageUsage()
            handleResponse(res)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
