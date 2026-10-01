package com.kora.onsim.asr

import java.io.File
import java.io.RandomAccessFile

/** Writes 16-bit PCM samples to a WAV file and fills in its header on close. */
internal class PcmWavRecorder(
    private val file: File,
    private val sampleRate: Int,
    private val channels: Int,
) {
    private val output: RandomAccessFile
    private var dataSize = 0L
    private var closed = false

    init {
        file.parentFile?.mkdirs()
        output = RandomAccessFile(file, "rw")
        output.setLength(0)
        output.write(ByteArray(WAV_HEADER_SIZE))
    }

    fun append(samples: ShortArray, count: Int) {
        check(!closed) { "WAV recorder is already closed" }
        require(count in 0..samples.size) { "Invalid PCM sample count: $count" }
        for (index in 0 until count) {
            val sample = samples[index].toInt()
            output.write(sample and 0xff)
            output.write((sample ushr 8) and 0xff)
        }
        dataSize += count * BYTES_PER_SAMPLE
    }

    fun close() {
        if (closed) return
        closed = true
        try {
            output.seek(0)
            writeAscii("RIFF")
            writeIntLE((WAV_HEADER_SIZE - 8L + dataSize).toInt())
            writeAscii("WAVE")
            writeAscii("fmt ")
            writeIntLE(16)
            writeShortLE(1)
            writeShortLE(channels)
            writeIntLE(sampleRate)
            writeIntLE(sampleRate * channels * BYTES_PER_SAMPLE)
            writeShortLE(channels * BYTES_PER_SAMPLE)
            writeShortLE(16)
            writeAscii("data")
            writeIntLE(dataSize.toInt())
        } finally {
            output.close()
        }
    }

    private fun writeAscii(value: String) {
        output.write(value.toByteArray(Charsets.US_ASCII))
    }

    private fun writeShortLE(value: Int) {
        output.write(value and 0xff)
        output.write((value ushr 8) and 0xff)
    }

    private fun writeIntLE(value: Int) {
        output.write(value and 0xff)
        output.write((value ushr 8) and 0xff)
        output.write((value ushr 16) and 0xff)
        output.write((value ushr 24) and 0xff)
    }

    companion object {
        private const val WAV_HEADER_SIZE = 44
        private const val BYTES_PER_SAMPLE = 2
    }
}
