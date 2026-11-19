package com.example.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.min

data class DecodedAudioSample(
    val displayName: String,
    val samples: FloatArray,
    val sampleRate: Int,
    val durationSec: Float,
)

object AudioSampleDecoder {
    private const val TARGET_SAMPLE_RATE = 48000
    private const val MAX_SECONDS = 12

    fun queryDisplayName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        val name = cursor.getString(idx)
                        if (!name.isNullOrBlank()) return name
                    }
                }
            }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "ToneSample"
    }

    fun decode(context: Context, uri: Uri): Result<DecodedAudioSample> {
        val displayName = queryDisplayName(context, uri)
        // Fast path for WAV files
        if (displayName.lowercase().endsWith(".wav")) {
            val wavAttempt = runCatching {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("Cannot open audio file")
                val decoded = CabIrEngine.parseWavBytes(bytes)
                val resampled = if (decoded.sampleRate != TARGET_SAMPLE_RATE) {
                    CabIrEngine.resampleLinear(decoded.samples, decoded.sampleRate, TARGET_SAMPLE_RATE)
                } else decoded.samples
                val trimmed = extractActiveSegment(resampled, TARGET_SAMPLE_RATE)
                DecodedAudioSample(
                    displayName = displayName,
                    samples = trimmed,
                    sampleRate = TARGET_SAMPLE_RATE,
                    durationSec = trimmed.size.toFloat() / TARGET_SAMPLE_RATE,
                )
            }
            if (wavAttempt.isSuccess) return wavAttempt
        }

        // General MediaExtractor + MediaCodec decode (MP3, AAC, M4A, FLAC, OGG, WAV)
        return runCatching {
            val extractor = MediaExtractor()
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                ?: error("Cannot open selected audio file")
            pfd.use {
                extractor.setDataSource(it.fileDescriptor)
            }

            var audioTrackIdx = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val trackFormat = extractor.getTrackFormat(i)
                val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    audioTrackIdx = i
                    format = trackFormat
                    break
                }
            }
            require(audioTrackIdx >= 0 && format != null) { "No audio track found in selected file" }
            extractor.selectTrack(audioTrackIdx)

            val mime = format.getString(MediaFormat.KEY_MIME)!!
            var srcSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else 1
            var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT

            val codec = MediaCodec.createDecoderByType(mime)
            val pcmOut = ArrayList<Float>(TARGET_SAMPLE_RATE * 6)
            val maxSrcSamples = srcSampleRate * MAX_SECONDS

            try {
                codec.configure(format, null, null, 0)
                codec.start()

                val bufferInfo = MediaCodec.BufferInfo()
                var sawInputEos = false
                var sawOutputEos = false

                while (!sawOutputEos && pcmOut.size < maxSrcSamples) {
                    if (!sawInputEos) {
                        val inIdx = codec.dequeueInputBuffer(10_000)
                        if (inIdx >= 0) {
                            val inBuf = codec.getInputBuffer(inIdx)!!
                            val sampleSize = extractor.readSampleData(inBuf, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(
                                    inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                sawInputEos = true
                            } else {
                                codec.queueInputBuffer(inIdx, 0, sampleSize, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }

                    val outIdx = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                    when {
                        outIdx >= 0 -> {
                            val outBuf = codec.getOutputBuffer(outIdx)
                            if (outBuf != null && bufferInfo.size > 0) {
                                outBuf.position(bufferInfo.offset)
                                outBuf.limit(bufferInfo.offset + bufferInfo.size)
                                appendDecodedMonoSamples(outBuf, channels, pcmEncoding, pcmOut, maxSrcSamples)
                            }
                            codec.releaseOutputBuffer(outIdx, false)
                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                sawOutputEos = true
                            }
                        }
                        outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val outFmt = codec.outputFormat
                            if (outFmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                                srcSampleRate = outFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            }
                            if (outFmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                                channels = outFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            }
                            if (outFmt.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                                pcmEncoding = outFmt.getInteger(MediaFormat.KEY_PCM_ENCODING)
                            }
                        }
                    }
                }
            } finally {
                runCatching { codec.stop() }
                runCatching { codec.release() }
                runCatching { extractor.release() }
            }

            require(pcmOut.size >= 1024) { "Audio sample is too short or silent to analyze." }
            val rawArray = FloatArray(pcmOut.size) { pcmOut[it] }
            val resampled = if (srcSampleRate != TARGET_SAMPLE_RATE) {
                CabIrEngine.resampleLinear(rawArray, srcSampleRate, TARGET_SAMPLE_RATE)
            } else rawArray
            val trimmed = extractActiveSegment(resampled, TARGET_SAMPLE_RATE)

            DecodedAudioSample(
                displayName = displayName,
                samples = trimmed,
                sampleRate = TARGET_SAMPLE_RATE,
                durationSec = trimmed.size.toFloat() / TARGET_SAMPLE_RATE,
            )
        }
    }

    private fun appendDecodedMonoSamples(
        buf: ByteBuffer,
        channels: Int,
        pcmEncoding: Int,
        dst: ArrayList<Float>,
        maxSamples: Int,
    ) {
        val ch = channels.coerceAtLeast(1)
        buf.order(ByteOrder.LITTLE_ENDIAN)
        if (pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT) {
            val floatBuf = buf.asFloatBuffer()
            while (floatBuf.remaining() >= ch && dst.size < maxSamples) {
                var sum = 0f
                for (c in 0 until ch) sum += floatBuf.get()
                dst.add((sum / ch).coerceIn(-1f, 1f))
            }
        } else {
            val shortBuf = buf.asShortBuffer()
            while (shortBuf.remaining() >= ch && dst.size < maxSamples) {
                var sum = 0f
                for (c in 0 until ch) sum += shortBuf.get() / 32768f
                dst.add((sum / ch).coerceIn(-1f, 1f))
            }
        }
    }

    private fun extractActiveSegment(samples: FloatArray, sampleRate: Int): FloatArray {
        if (samples.isEmpty()) return samples
        val start = samples.indexOfFirst { abs(it) > 0.015f }.coerceAtLeast(0)
        val end = samples.indexOfLast { abs(it) > 0.01f }.let { if (it <= start) samples.lastIndex else it }
        val maxLen = min(end - start + 1, sampleRate * 8)
        return samples.copyOfRange(start, start + maxLen)
    }
}
