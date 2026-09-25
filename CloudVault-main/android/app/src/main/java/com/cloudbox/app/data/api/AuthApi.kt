package com.cloudbox.app.data.api

import com.cloudbox.app.data.api.models.ForgotPasswordRequest
import com.cloudbox.app.data.api.models.MessageResponse
import com.cloudbox.app.data.api.models.RegisterRequest
import com.cloudbox.app.data.api.models.StorageUsageResponse
import com.cloudbox.app.data.api.models.TokenResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT

interface AuthApi {
    @POST("/auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<MessageResponse>
    
    @FormUrlEncoded
    @POST("/auth/login")
    suspend fun login(
        @Field("username") username: String,
        @Field("password") password: String
    ): Response<TokenResponse>
    
    @POST("/auth/logout")
    suspend fun logout(): Response<MessageResponse>
    
    @POST("/auth/forgot-password")
    suspend fun forgotPassword(@Body request: ForgotPasswordRequest): Response<MessageResponse>

    @GET("/files/storage-usage")
    suspend fun getStorageUsage(): Response<StorageUsageResponse>
}
