package com.cloudbox.app.data.repository

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.cloudbox.app.data.api.ApiClient
import com.cloudbox.app.data.api.models.CloudFile
import com.cloudbox.app.data.api.models.CreateFolderRequest
import com.cloudbox.app.data.api.models.ErrorResponse
import com.cloudbox.app.data.api.models.FileListResponse
import com.cloudbox.app.data.api.models.MoveRequest
import com.cloudbox.app.data.api.models.RenameRequest
import com.cloudbox.app.data.api.models.ShareEmailRequest
import com.cloudbox.app.data.api.models.ShareListResponse
import com.cloudbox.app.data.api.models.ShareOut
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okio.BufferedSink
import retrofit2.Response
import java.io.File
import java.io.FileOutputStream

class FileRepository {
    private val api = ApiClient.filesApi
    private val gson = Gson()

    private fun <T> handleResponse(response: Response<T>): Result<T> {
        if (response.isSuccessful) {
            response.body()?.let { return Result.success(it) }
        }
        val errorMsg = try {
            gson.fromJson(response.errorBody()?.string(), ErrorResponse::class.java).detail
        } catch (e: Exception) {
            "An unknown error occurred"
        }
        return Result.failure(Exception(errorMsg))
    }

    suspend fun listFiles(parentFolderId: Int?, search: String?, sort: String, direction: String, category: String? = null, recursive: Boolean = false): Result<FileListResponse> {
        return try {
            handleResponse(api.listFiles(parentFolderId, search, sort, direction, category, recursive))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun trash(): Result<FileListResponse> {
        return try {
            handleResponse(api.trash())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createFolder(name: String, parentFolderId: Int?): Result<CloudFile> {
        return try {
            handleResponse(api.createFolder(CreateFolderRequest(name, parentFolderId)))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun rename(fileId: Int, filename: String): Result<CloudFile> {
        return try {
            handleResponse(api.rename(fileId, RenameRequest(filename)))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun moveToTrash(fileId: Int): Result<CloudFile> {
        return try {
            handleResponse(api.trashFile(fileId))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun restore(fileId: Int): Result<CloudFile> {
        return try {
            handleResponse(api.restore(fileId))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun permanentlyDelete(fileId: Int): Result<String> {
        return try {
            val response = api.permanentlyDelete(fileId)
            if (response.isSuccessful) {
                Result.success(response.body()?.message ?: "File permanently deleted")
            } else {
                val errorMsg = try {
                    gson.fromJson(response.errorBody()?.string(), ErrorResponse::class.java).detail
                } catch (e: Exception) {
                    "Failed to delete file"
                }
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun upload(
        contentResolver: ContentResolver,
        uri: Uri,
        parentFolderId: Int?,
        onProgress: (Float) -> Unit
    ): Result<CloudFile> {
        return try {
            val name = getDisplayName(contentResolver, uri)
            val mime = contentResolver.getType(uri) ?: "application/octet-stream"
            val size = getSize(contentResolver, uri)
            val body = ProgressUriRequestBody(contentResolver, uri, mime, size, onProgress)
            val part = MultipartBody.Part.createFormData("file", name, body)
            handleResponse(api.uploadFile(part, parentFolderId))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getFile(fileId: Int): Result<CloudFile> {
        return try {
            handleResponse(api.getFile(fileId))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun download(file: CloudFile, targetDir: File, onProgress: (Float) -> Unit): Result<File> {
        return try {
            val response = api.download(file.id)
            if (!response.isSuccessful || response.body() == null) {
                return Result.failure(Exception("Download failed"))
            }
            val target = File(targetDir, file.filename)
            writeResponseBody(response.body()!!, target, onProgress)
            Result.success(target)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun move(fileId: Int, parentFolderId: Int?): Result<CloudFile> {
        return try {
            handleResponse(api.move(fileId, MoveRequest(parentFolderId)))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun shareWithEmail(fileId: Int, email: String): Result<ShareOut> {
        return try {
            handleResponse(api.shareWithEmail(fileId, ShareEmailRequest(email)))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadShared(share: ShareOut, target: File): Result<File> {
        return try {
            val response = api.downloadShared(share.token)
            if (!response.isSuccessful || response.body() == null) {
                return Result.failure(Exception("Download failed"))
            }
            writeResponseBody(response.body()!!, target) { }
            if (target.length() == 0L) {
                target.delete()
                return Result.failure(Exception("File is empty on server"))
            }
            Result.success(target)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sharedWithMe(): Result<ShareListResponse> {
        return try {
            handleResponse(api.sharedWithMe())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun fileMeta(contentResolver: ContentResolver, uri: Uri): Triple<String, String, Long> {
        return Triple(getDisplayName(contentResolver, uri), contentResolver.getType(uri) ?: "application/octet-stream", getSize(contentResolver, uri))
    }

    private fun getDisplayName(contentResolver: ContentResolver, uri: Uri): String {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) return cursor.getString(index)
        }
        return uri.lastPathSegment ?: "upload"
    }

    private fun getSize(contentResolver: ContentResolver, uri: Uri): Long {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (index >= 0 && cursor.moveToFirst()) return cursor.getLong(index)
        }
        return -1L
    }

    private fun writeResponseBody(body: ResponseBody, target: File, onProgress: (Float) -> Unit) {
        val total = body.contentLength()
        var written = 0L
        body.byteStream().buffered(65536).use { input ->
            FileOutputStream(target).buffered(65536).use { output ->
                val buffer = ByteArray(65536)
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    written += read
                    if (total > 0) onProgress(written.toFloat() / total.toFloat())
                }
                output.flush()
            }
        }
        onProgress(1f)
    }
}

private class ProgressUriRequestBody(
    private val contentResolver: ContentResolver,
    private val uri: Uri,
    private val mimeType: String,
    private val length: Long,
    private val onProgress: (Float) -> Unit
) : RequestBody() {
    override fun contentType() = mimeType.toMediaTypeOrNull()

    override fun contentLength() = length

    override fun writeTo(sink: BufferedSink) {
        var uploaded = 0L
        contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                sink.write(buffer, 0, read)
                uploaded += read
                if (length > 0) onProgress(uploaded.toFloat() / length.toFloat())
            }
        }
        sink.flush()
        onProgress(1f)
    }
}
