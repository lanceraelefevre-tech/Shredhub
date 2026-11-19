package com.example.audio

import android.content.Context
import android.net.Uri
import com.example.model.CabModel
import com.example.model.MicPosition
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * High-fidelity Impulse Response (IR) cabinet simulator.
 * Synthesizes realistic multi-speaker cabinet + microphone acoustic FIR kernels
 * and supports loading user .wav impulse responses or tone-matched spectral curves.
 */
object CabIrEngine {
    const val IR_TAPS = 192

    /**
     * Generates a 192-tap causal cabinet + mic impulse response kernel via IFFT of
     * a physically modeled guitar speaker magnitude response + early enclosure reflections.
     */
    fun synthesizeCabinetIr(
        cabModel: CabModel,
        micPosition: MicPosition,
        resonance: Float,
        sampleRate: Int,
        matchedSpectralCurveDb: List<Float>? = null,
    ): FloatArray {
        val fftSize = 512
        val half = fftSize / 2
        val real = FloatArray(fftSize)
        val imag = FloatArray(fftSize)
        val binHz = sampleRate.toFloat() / fftSize

        val resBoostDb = (resonance - 0.5f) * 8f + micPosition.proximityBassDb
        val highRollOff = (cabModel.highRollOffHz * (1f + micPosition.brightnessOffsetDb * 0.06f))
            .coerceIn(2600f, 8200f)

        for (k in 0..half) {
            val freq = k * binHz
            val magDb = if (freq < 28f) {
                -42f
            } else {
                // Subsonic cabinet HPF rolloff below enclosure resonance
                val subHpDb = if (freq < cabModel.lowResHz) {
                    -24f * FftProcessor.log2(cabModel.lowResHz / freq).coerceIn(0f, 3f)
                } else 0f

                // Cabinet resonance bump
                val resDist = FftProcessor.log2(freq / cabModel.lowResHz)
                val resPeakDb = resBoostDb * exp(-(resDist * resDist) / 0.35f)

                // Speaker midrange scoop / cone breakup hump
                val midDist = FftProcessor.log2(freq / cabModel.midFocusHz)
                val midPeakDb = cabModel.midFocusDb * exp(-(midDist * midDist) / 0.55f)

                // Classic 4kHz cone dip (comb notch) + steep speaker low-pass rolloff
                val notchDist = FftProcessor.log2(freq / 3850f)
                val coneNotchDb = -4.5f * exp(-(notchDist * notchDist) / 0.08f)

                val lpDb = if (freq > highRollOff) {
                    -26f * FftProcessor.log2(freq / highRollOff).coerceIn(0f, 3.5f)
                } else 0f

                val micTiltDb = micPosition.brightnessOffsetDb * ((freq / 3500f).coerceIn(0f, 1.3f))

                // Optional spectral shaping from Tone Matcher
                val matchOffsetDb = if (cabModel == CabModel.CUSTOM_MATCH_IR && !matchedSpectralCurveDb.isNullOrEmpty()) {
                    interpolateCurveDb(freq, matchedSpectralCurveDb) * 0.45f
                } else 0f

                subHpDb + resPeakDb + midPeakDb + coneNotchDb + lpDb + micTiltDb + matchOffsetDb
            }

            val linearMag = 10.0.pow((magDb / 20.0)).toFloat().coerceIn(0.0005f, 4.0f)
            // Linear-ish phase shifted toward minimum phase via slight causal delay (14 samples)
            val phase = -2.0 * PI * k * 14.0 / fftSize
            real[k] = (linearMag * cos(phase)).toFloat()
            imag[k] = (linearMag * sin(phase)).toFloat()
            if (k in 1 until half) {
                real[fftSize - k] = real[k]
                imag[fftSize - k] = -imag[k]
            }
        }

        FftProcessor.ifft(real, imag)

        // Window into causal IR_TAPS kernel + subtle early box reflection taps
        val kernel = FloatArray(IR_TAPS)
        for (i in 0 until IR_TAPS) {
            val fade = if (i > IR_TAPS - 32) {
                0.5f * (1f + cos(PI.toFloat() * (i - (IR_TAPS - 32)) / 32f))
            } else 1f
            kernel[i] = real[i] * fade
        }
        // Subtle 4x12 wood back-wall reflection
        if (IR_TAPS > 64) {
            kernel[48] += kernel[14] * 0.14f
            kernel[79] -= kernel[14] * 0.08f
        }
        normalizeIr(kernel)
        return kernel
    }

    private fun interpolateCurveDb(freq: Float, curveDb: List<Float>): Float {
        val minF = 60f
        val maxF = 11000f
        val pos = ((ln(freq.coerceIn(minF, maxF) / minF)) / ln(maxF / minF)) * (curveDb.size - 1)
        val idx0 = pos.toInt().coerceIn(0, curveDb.size - 1)
        val idx1 = (idx0 + 1).coerceIn(0, curveDb.size - 1)
        val frac = pos - idx0
        return curveDb[idx0] * (1f - frac) + curveDb[idx1] * frac
    }

    /**
     * Loads and parses a standard PCM/Float .wav Impulse Response file via ContentResolver.
     */
    fun loadWavIr(context: Context, uri: Uri, targetSampleRate: Int): Result<FloatArray> {
        return try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return Result.failure(IllegalStateException("Unable to open WAV IR stream."))
            val parsed = parseWavBytes(bytes)
            val resampled = if (parsed.sampleRate != targetSampleRate) {
                resampleLinear(parsed.samples, parsed.sampleRate, targetSampleRate)
            } else {
                parsed.samples
            }
            // Trim leading near-silence, cap to 256 taps for low-latency convolution
            val startIdx = resampled.indexOfFirst { kotlin.math.abs(it) > 0.002f }.coerceAtLeast(0)
            val count = min(256, resampled.size - startIdx).coerceAtLeast(32)
            val kernel = FloatArray(count) { i ->
                val srcIdx = startIdx + i
                val sample = if (srcIdx < resampled.size) resampled[srcIdx] else 0f
                val fade = if (i > count - 24) {
                    0.5f * (1f + cos(PI.toFloat() * (i - (count - 24)) / 24f))
                } else 1f
                sample * fade
            }
            normalizeIr(kernel)
            Result.success(kernel)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    data class DecodedWav(val samples: FloatArray, val sampleRate: Int)

    fun parseWavBytes(bytes: ByteArray): DecodedWav {
        require(bytes.size >= 44) { "File too small to be a valid WAV" }
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val riff = ByteArray(4).also { bb.get(it) }
        require(String(riff) == "RIFF") { "Missing RIFF header" }
        bb.int // chunk size
        val wave = ByteArray(4).also { bb.get(it) }
        require(String(wave) == "WAVE") { "Missing WAVE header" }

        var audioFormat = 1
        var numChannels = 1
        var sampleRate = 48000
        var bitsPerSample = 16
        var dataOffset = -1
        var dataSize = 0

        while (bb.remaining() >= 8) {
            val chunkIdBytes = ByteArray(4).also { bb.get(it) }
            val chunkId = String(chunkIdBytes)
            val chunkSize = bb.int
            if (chunkSize < 0 || chunkSize > bb.remaining()) break
            val nextPos = bb.position() + chunkSize + (chunkSize and 1)
            when (chunkId) {
                "fmt " -> {
                    audioFormat = bb.short.toInt() and 0xFFFF
                    numChannels = bb.short.toInt() and 0xFFFF
                    sampleRate = bb.int
                    bb.int // byteRate
                    bb.short // blockAlign
                    bitsPerSample = bb.short.toInt() and 0xFFFF
                }
                "data" -> {
                    dataOffset = bb.position()
                    dataSize = chunkSize
                    break
                }
            }
            if (nextPos <= bb.capacity()) bb.position(nextPos) else break
        }

        require(dataOffset >= 0 && dataSize > 0) { "WAV file has no audio data chunk" }
        val bytesPerSample = (bitsPerSample / 8).coerceAtLeast(1)
        val frameSize = bytesPerSample * numChannels
        val frameCount = min(dataSize / frameSize, sampleRate * 15)
        val out = FloatArray(frameCount)

        bb.position(dataOffset)
        for (i in 0 until frameCount) {
            var mono = 0f
            for (ch in 0 until numChannels) {
                val s = when {
                    audioFormat == 3 && bitsPerSample == 32 -> bb.float
                    bitsPerSample == 16 -> bb.short / 32768f
                    bitsPerSample == 24 -> {
                        val b0 = bb.get().toInt() and 0xFF
                        val b1 = bb.get().toInt() and 0xFF
                        val b2 = bb.get().toInt()
                        ((b2 shl 16) or (b1 shl 8) or b0) / 8388608f
                    }
                    bitsPerSample == 32 -> bb.int / 2147483648f
                    bitsPerSample == 8 -> ((bb.get().toInt() and 0xFF) - 128) / 128f
                    else -> {
                        bb.position(bb.position() + bytesPerSample)
                        0f
                    }
                }
                mono += s
            }
            out[i] = mono / numChannels
        }
        return DecodedWav(out, sampleRate)
    }

    fun resampleLinear(input: FloatArray, srcRate: Int, dstRate: Int): FloatArray {
        if (srcRate == dstRate || input.isEmpty()) return input
        val ratio = srcRate.toDouble() / dstRate.toDouble()
        val outLen = max(1, (input.size / ratio).toInt())
        return FloatArray(outLen) { i ->
            val srcPos = i * ratio
            val idx0 = srcPos.toInt().coerceIn(0, input.lastIndex)
            val idx1 = (idx0 + 1).coerceIn(0, input.lastIndex)
            val frac = (srcPos - idx0).toFloat()
            input[idx0] * (1f - frac) + input[idx1] * frac
        }
    }

    private fun normalizeIr(kernel: FloatArray) {
        var energy = 0f
        for (v in kernel) energy += v * v
        val rms = sqrt(max(energy, 1e-8f))
        val targetGain = 0.85f / rms
        for (i in kernel.indices) {
            kernel[i] *= targetGain
        }
    }
}
