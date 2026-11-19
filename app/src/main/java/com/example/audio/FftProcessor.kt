package com.example.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

object FftProcessor {

    fun hannWindow(size: Int): FloatArray {
        return FloatArray(size) { i ->
            (0.5 * (1.0 - cos(2.0 * PI * i / (size - 1)))).toFloat()
        }
    }

    /**
     * In-place Cooley-Tukey Radix-2 FFT. [n] must be a power of 2.
     */
    fun fft(real: FloatArray, imag: FloatArray) {
        val n = real.size
        require(n > 0 && (n and (n - 1)) == 0) { "FFT size must be power of 2, got $n" }

        // Bit-reversal permutation
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while ((j and bit) != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = real[i]
                real[i] = real[j]
                real[j] = tr
                val ti = imag[i]
                imag[i] = imag[j]
                imag[j] = ti
            }
        }

        // Butterfly stages
        var len = 2
        while (len <= n) {
            val half = len shr 1
            val angleStep = (-2.0 * PI / len)
            val wLenR = cos(angleStep).toFloat()
            val wLenI = sin(angleStep).toFloat()
            var start = 0
            while (start < n) {
                var wr = 1f
                var wi = 0f
                for (k in 0 until half) {
                    val evenIdx = start + k
                    val oddIdx = evenIdx + half
                    val tr = wr * real[oddIdx] - wi * imag[oddIdx]
                    val ti = wr * imag[oddIdx] + wi * real[oddIdx]
                    real[oddIdx] = real[evenIdx] - tr
                    imag[oddIdx] = imag[evenIdx] - ti
                    real[evenIdx] = real[evenIdx] + tr
                    imag[evenIdx] = imag[evenIdx] + ti
                    val nextWr = wr * wLenR - wi * wLenI
                    wi = wr * wLenI + wi * wLenR
                    wr = nextWr
                }
                start += len
            }
            len = len shl 1
        }
    }

    /**
     * Inverse FFT in-place. Scales by 1/N.
     */
    fun ifft(real: FloatArray, imag: FloatArray) {
        val n = real.size
        for (i in 0 until n) imag[i] = -imag[i]
        fft(real, imag)
        val invN = 1f / n
        for (i in 0 until n) {
            real[i] *= invN
            imag[i] = -imag[i] * invN
        }
    }

    /**
     * Extracts [bandCount] logarithmically spaced energy levels in dB (60 Hz .. 12 kHz)
     * from a power magnitude spectrum [mag].
     */
    fun extractLogBandsDb(
        mag: FloatArray,
        sampleRate: Int,
        fftSize: Int,
        bandCount: Int = 24,
        minFreq: Float = 60f,
        maxFreq: Float = 11000f,
    ): List<Float> {
        val binHz = sampleRate.toFloat() / fftSize
        val halfBins = fftSize / 2
        val ratio = maxFreq / minFreq
        val rawDb = FloatArray(bandCount)
        var maxVal = -120f

        for (b in 0 until bandCount) {
            val fLow = minFreq * Math.pow(ratio.toDouble(), b.toDouble() / bandCount).toFloat()
            val fHigh = minFreq * Math.pow(ratio.toDouble(), (b + 1).toDouble() / bandCount).toFloat()
            val binLow = (fLow / binHz).toInt().coerceIn(1, halfBins - 1)
            val binHigh = (fHigh / binHz).toInt().coerceIn(binLow, halfBins - 1)
            var sumSq = 0f
            var count = 0
            for (k in binLow..binHigh) {
                sumSq += mag[k] * mag[k]
                count++
            }
            val rms = sqrt(sumSq / max(1, count))
            val db = 20f * log10(max(rms, 1e-7f))
            rawDb[b] = db
            if (db > maxVal) maxVal = db
        }

        // Normalize peak to 0 dB for clean comparison (-48 dB .. 0 dB)
        return rawDb.map { (it - maxVal).coerceIn(-48f, 0f) }
    }

    fun computeBandCenterFrequencies(
        bandCount: Int = 24,
        minFreq: Float = 60f,
        maxFreq: Float = 11000f,
    ): List<Float> {
        val ratio = (maxFreq / minFreq).toDouble()
        return List(bandCount) { b ->
            (minFreq * Math.pow(ratio, (b + 0.5) / bandCount)).toFloat()
        }
    }

    /**
     * Quick log2 helper.
     */
    fun log2(x: Float): Float = (ln(x.toDouble()) / ln(2.0)).toFloat()
}
