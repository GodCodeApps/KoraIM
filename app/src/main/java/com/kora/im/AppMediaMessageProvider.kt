package com.kora.im

import com.kora.imui.provider.IMMediaMessageProvider
import com.kora.imui.provider.ImageUploadRequest
import com.kora.imui.provider.ImageUploadResult
import com.kora.imui.provider.VideoUploadRequest
import com.kora.imui.provider.VideoUploadResult
import com.kora.imui.provider.VoiceUploadRequest
import com.kora.imui.provider.VoiceUploadResult

/**
 * Demo implementation. Replace the returned local paths with URLs/file IDs from
 * the app's own file service in a production integration.
 */
class AppMediaMessageProvider : IMMediaMessageProvider {
    override suspend fun uploadImage(request: ImageUploadRequest) =
        ImageUploadResult(remoteUrl = request.localPath)

    override suspend fun uploadVideo(request: VideoUploadRequest) =
        VideoUploadResult(
            remoteUrl = request.localPath,
            remoteCoverUrl = request.localCoverPath
        )

    override suspend fun uploadVoice(request: VoiceUploadRequest) =
        VoiceUploadResult(remoteUrl = request.localPath)
}

