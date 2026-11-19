package com.example.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Real-time zero-allocation Direct Form II Transposed Biquad Filter.
 */
class BiquadFilter {
    @Volatile private var b0 = 1.0f
    @Volatile private var b1 = 0.0f
    @Volatile private var b2 = 0.0f
    @Volatile private var a1 = 0.0f
    @Volatile private var a2 = 0.0f

    private var z1 = 0.0f
    private var z2 = 0.0f

    fun reset() {
        z1 = 0f
        z2 = 0f
    }

    fun process(input: Float): Float {
        val out = b0 * input + z1
        z1 = b1 * input - a1 * out + z2
        z2 = b2 * input - a2 * out
        // Denormal flush
        if (z1 in -1e-20f..1e-20f) z1 = 0f
        if (z2 in -1e-20f..1e-20f) z2 = 0f
        return out
    }

    fun setHighPass(sampleRate: Float, freqHz: Float, q: Float = 0.707f) {
        val f = freqHz.coerceIn(20f, sampleRate * 0.45f)
        val w0 = (2.0 * PI * f / sampleRate)
        val cosW0 = cos(w0)
        val alpha = sin(w0) / (2.0 * q)
        val a0 = 1.0 + alpha
        b0 = ((1.0 + cosW0) / 2.0 / a0).toFloat()
        b1 = (-(1.0 + cosW0) / a0).toFloat()
        b2 = ((1.0 + cosW0) / 2.0 / a0).toFloat()
        a1 = (-2.0 * cosW0 / a0).toFloat()
        a2 = ((1.0 - alpha) / a0).toFloat()
    }

    fun setLowPass(sampleRate: Float, freqHz: Float, q: Float = 0.707f) {
        val f = freqHz.coerceIn(40f, sampleRate * 0.46f)
        val w0 = (2.0 * PI * f / sampleRate)
        val cosW0 = cos(w0)
        val alpha = sin(w0) / (2.0 * q)
        val a0 = 1.0 + alpha
        b0 = ((1.0 - cosW0) / 2.0 / a0).toFloat()
        b1 = ((1.0 - cosW0) / a0).toFloat()
        b2 = ((1.0 - cosW0) / 2.0 / a0).toFloat()
        a1 = (-2.0 * cosW0 / a0).toFloat()
        a2 = ((1.0 - alpha) / a0).toFloat()
    }

    fun setPeaking(sampleRate: Float, freqHz: Float, q: Float, gainDb: Float) {
        val f = freqHz.coerceIn(25f, sampleRate * 0.45f)
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * f / sampleRate
        val cosW0 = cos(w0)
        val alpha = sin(w0) / (2.0 * q)
        val a0 = 1.0 + alpha / a
        b0 = ((1.0 + alpha * a) / a0).toFloat()
        b1 = (-2.0 * cosW0 / a0).toFloat()
        b2 = ((1.0 - alpha * a) / a0).toFloat()
        a1 = (-2.0 * cosW0 / a0).toFloat()
        a2 = ((1.0 - alpha / a) / a0).toFloat()
    }

    fun setLowShelf(sampleRate: Float, freqHz: Float, gainDb: Float, slope: Float = 0.75f) {
        val f = freqHz.coerceIn(25f, sampleRate * 0.45f)
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * f / sampleRate
        val cosW0 = cos(w0)
        val sinW0 = sin(w0)
        val alpha = sinW0 / 2.0 * sqrt((a + 1.0 / a) * (1.0 / slope - 1.0) + 2.0)
        val twoSqrtAAlpha = 2.0 * sqrt(a) * alpha
        val a0 = (a + 1.0) + (a - 1.0) * cosW0 + twoSqrtAAlpha
        b0 = (a * ((a + 1.0) - (a - 1.0) * cosW0 + twoSqrtAAlpha) / a0).toFloat()
        b1 = (2.0 * a * ((a - 1.0) - (a + 1.0) * cosW0) / a0).toFloat()
        b2 = (a * ((a + 1.0) - (a - 1.0) * cosW0 - twoSqrtAAlpha) / a0).toFloat()
        a1 = (-2.0 * ((a - 1.0) + (a + 1.0) * cosW0) / a0).toFloat()
        a2 = ((a + 1.0) + (a - 1.0) * cosW0 - twoSqrtAAlpha) .let { (it / a0).toFloat() }
    }

    fun setHighShelf(sampleRate: Float, freqHz: Float, gainDb: Float, slope: Float = 0.75f) {
        val f = freqHz.coerceIn(100f, sampleRate * 0.45f)
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * f / sampleRate
        val cosW0 = cos(w0)
        val sinW0 = sin(w0)
        val alpha = sinW0 / 2.0 * sqrt((a + 1.0 / a) * (1.0 / slope - 1.0) + 2.0)
        val twoSqrtAAlpha = 2.0 * sqrt(a) * alpha
        val a0 = (a + 1.0) - (a - 1.0) * cosW0 + twoSqrtAAlpha
        b0 = (a * ((a + 1.0) + (a - 1.0) * cosW0 + twoSqrtAAlpha) / a0).toFloat()
        b1 = (-2.0 * a * ((a - 1.0) + (a + 1.0) * cosW0) / a0).toFloat()
        b2 = (a * ((a + 1.0) + (a - 1.0) * cosW0 - twoSqrtAAlpha) / a0).toFloat()
        a1 = (2.0 * ((a - 1.0) - (a + 1.0) * cosW0) / a0).toFloat()
        a2 = (((a + 1.0) - (a - 1.0) * cosW0 - twoSqrtAAlpha) / a0).toFloat()
    }
}
