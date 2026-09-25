package com.cloudbox.app.data.api

import com.cloudbox.app.data.api.models.CloudFile
import com.cloudbox.app.data.api.models.CreateFolderRequest
import com.cloudbox.app.data.api.models.FileListResponse
import com.cloudbox.app.data.api.models.MessageResponse
import com.cloudbox.app.data.api.models.MoveRequest
import com.cloudbox.app.data.api.models.RenameRequest

import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

interface FilesApi {
    @GET("/files")
    suspend fun listFiles(
        @Query("parent_folder_id") parentFolderId: Int? = null,
        @Query("search") search: String? = null,
        @Query("sort") sort: String = "name",
        @Query("direction") direction: String = "asc",
        @Query("category") category: String? = null,
        @Query("recursive") recursive: Boolean = false
    ): Response<FileListResponse>

    @GET("/files/search")
    suspend fun searchFiles(
        @Query("q") query: String,
        @Query("sort") sort: String = "name",
        @Query("direction") direction: String = "asc"
    ): Response<FileListResponse>

    @GET("/files/trash")
    suspend fun trash(
        @Query("sort") sort: String = "date",
        @Query("direction") direction: String = "desc"
    ): Response<FileListResponse>

    @POST("/files/folders")
    suspend fun createFolder(@Body request: CreateFolderRequest): Response<CloudFile>

    @Multipart
    @POST("/files/upload")
    suspend fun uploadFile(
        @Part file: MultipartBody.Part,
        @Query("parent_folder_id") parentFolderId: Int? = null
    ): Response<CloudFile>

    @GET("/files/{file_id}")
    suspend fun getFile(@Path("file_id") fileId: Int): Response<CloudFile>

    @Streaming
    @GET("/files/{file_id}/download")
    suspend fun download(@Path("file_id") fileId: Int): Response<ResponseBody>

    @POST("/files/{file_id}/rename")
    suspend fun rename(@Path("file_id") fileId: Int, @Body request: RenameRequest): Response<CloudFile>

    @POST("/files/{file_id}/move")
    suspend fun move(@Path("file_id") fileId: Int, @Body request: MoveRequest): Response<CloudFile>

    @POST("/files/{file_id}/trash")
    suspend fun trashFile(@Path("file_id") fileId: Int): Response<CloudFile>

    @POST("/files/{file_id}/restore")
    suspend fun restore(@Path("file_id") fileId: Int): Response<CloudFile>

    @DELETE("/files/{file_id}")
    suspend fun permanentlyDelete(@Path("file_id") fileId: Int): Response<MessageResponse>
}
