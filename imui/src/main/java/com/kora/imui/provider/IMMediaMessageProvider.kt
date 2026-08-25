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

data class FileUploadRequest(val localPath: String, val name: String, val size: Long, val mimeType: String)
data class FileUploadResult(val remoteUrl: String)

/**
 * 多媒体文件上传 SPI 接口：
 * 由业务宿主 App 实现，负责将本地文件上传到业务 OSS/文件服务器并返回远程访问 URL。
 */
interface IMMediaMessageProvider {
    /** 上传图片并返回远程图片 URL */
    suspend fun uploadImage(request: ImageUploadRequest): ImageUploadResult

    /** 上传视频并返回远程视频与封面 URL */
    suspend fun uploadVideo(request: VideoUploadRequest): VideoUploadResult

    /** 上传语音并返回远程音频 URL */
    suspend fun uploadVoice(request: VoiceUploadRequest): VoiceUploadResult

    /** Upload a generic file. The default keeps existing providers source-compatible. */
    suspend fun uploadFile(request: FileUploadRequest): FileUploadResult =
        error("File upload is not supported by this provider")
}

