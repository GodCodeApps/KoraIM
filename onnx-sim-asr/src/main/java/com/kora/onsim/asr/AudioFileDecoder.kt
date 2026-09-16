package com.kora.onsim.asr

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/** Decodes local AAC/M4A media into 16 kHz mono PCM samples. */
internal object AudioFileDecoder {
    private const val TARGET_SAMPLE_RATE = 16_000

    fun decode(path: String): FloatArray {
        require(File(path).isFile) { "Audio file does not exist: $path" }

        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(path)
            val trackIndex = findAudioTrack(extractor)
            require(trackIndex >= 0) { "No audio track found in: $path" }
            extractor.selectTrack(trackIndex)

            val inputFormat = extractor.getTrackFormat(trackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME)
                ?: error("Audio MIME type is missing")
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(inputFormat, null, null, 0)
            codec.start()

            val pcm = ArrayList<Float>()
            val bufferInfo = MediaCodec.BufferInfo()
            var outputFormat = inputFormat
            var inputDone = false
            var outputDone = false

            while (!outputDone) {
                if (!inputDone) {
                    val inputIndex = codec.dequeueInputBuffer(10_000L)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)
                            ?: error("Decoder input buffer is unavailable")
                        inputBuffer.clear()
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(
                                inputIndex,
                                0,
                                0,
                                0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputDone = true
                        } else {
                            val presentationTimeUs = extractor.sampleTime.coerceAtLeast(0L)
                            codec.queueInputBuffer(
                                inputIndex,
                                0,
                                sampleSize,
                                presentationTimeUs,
                                0,
                            )
                            extractor.advance()
                        }
                    }
                }

                when (val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000L)) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        // Continue feeding/draining until the decoder emits EOS.
                    }

                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        outputFormat = codec.outputFormat
                    }

                    else -> if (outputIndex >= 0) {
                        val outputBuffer = codec.getOutputBuffer(outputIndex)
                        val isCodecConfig = bufferInfo.flags and
                            MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                        if (outputBuffer != null && bufferInfo.size > 0 && !isCodecConfig) {
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            appendPcm(
                                outputBuffer,
                                outputFormat,
                                pcm,
                            )
                        }
                        outputDone = bufferInfo.flags and
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        codec.releaseOutputBuffer(outputIndex, false)
                    }
                }
            }

            require(pcm.isNotEmpty()) { "Decoded audio is empty: $path" }
            return resampleToTarget(pcm, outputFormat.sampleRate())
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun findAudioTrack(extractor: MediaExtractor): Int {
        for (index in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(index)
            if (format.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                return index
            }
        }
        return -1
    }

    private fun appendPcm(
        buffer: ByteBuffer,
        format: MediaFormat,
        output: MutableList<Float>,
    ) {
        val channels = format.channelCount().coerceAtLeast(1)
        val encoding = if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
            format.getInteger(MediaFormat.KEY_PCM_ENCODING)
        } else {
            AudioFormat.ENCODING_PCM_16BIT
        }
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        if (encoding == AudioFormat.ENCODING_PCM_FLOAT) {
            val frameBytes = channels * 4
            val frameCount = buffer.remaining() / frameBytes
            repeat(frameCount) {
                var sum = 0f
                repeat(channels) { sum += buffer.float }
                output.add((sum / channels).coerceIn(-1f, 1f))
            }
        } else {
            val frameBytes = channels * 2
            val frameCount = buffer.remaining() / frameBytes
            repeat(frameCount) {
                var sum = 0f
                repeat(channels) { sum += buffer.short / 32768f }
                output.add((sum / channels).coerceIn(-1f, 1f))
            }
        }
    }

    private fun resampleToTarget(samples: List<Float>, sourceRate: Int): FloatArray {
        if (sourceRate <= 0 || sourceRate == TARGET_SAMPLE_RATE) return samples.toFloatArray()
        val outputSize = (samples.size.toDouble() * TARGET_SAMPLE_RATE / sourceRate)
            .roundToInt()
            .coerceAtLeast(1)
        return FloatArray(outputSize) { index ->
            val sourcePosition = index.toDouble() * sourceRate / TARGET_SAMPLE_RATE
            val low = sourcePosition.toInt().coerceIn(0, samples.lastIndex)
            val high = (low + 1).coerceAtMost(samples.lastIndex)
            val fraction = (sourcePosition - low).toFloat()
            samples[low] + (samples[high] - samples[low]) * fraction
        }
    }

    private fun MediaFormat.channelCount(): Int =
        if (containsKey(MediaFormat.KEY_CHANNEL_COUNT)) getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 1

    private fun MediaFormat.sampleRate(): Int =
        if (containsKey(MediaFormat.KEY_SAMPLE_RATE)) getInteger(MediaFormat.KEY_SAMPLE_RATE) else TARGET_SAMPLE_RATE
}
