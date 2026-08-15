package com.kora.imui.provider

data class ImageUploadRequest(
    val localPath: String,
    val width: Int,
    val height: Int,
    val size: Long,
    val mimeType: String
)

data class ImageUploadResult(val remoteUrl: String)

data class VideoUploadRequest(
    val localPath: String,
    val localCoverPath: String,
    val duration: Long,
    val width: Int,
    val height: Int,
    val size: Long,
    val mimeType: String
)

data class VideoUploadResult(
    val remoteUrl: String,
    val remoteCoverUrl: String = ""
)

data class VoiceUploadRequest(
    val localPath: String,
    val duration: Long,
    val size: Long,
    val mimeType: String
)

data class VoiceUploadResult(val remoteUrl: String)

/** Implemented by the host app. IMUI never talks to a business file server directly. */
interface IMMediaMessageProvider {
    suspend fun uploadImage(request: ImageUploadRequest): ImageUploadResult
    suspend fun uploadVideo(request: VideoUploadRequest): VideoUploadResult
    suspend fun uploadVoice(request: VoiceUploadRequest): VoiceUploadResult
}

