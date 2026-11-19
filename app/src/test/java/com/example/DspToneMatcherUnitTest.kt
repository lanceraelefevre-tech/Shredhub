package com.example

import com.example.audio.CabIrEngine
import com.example.audio.DecodedAudioSample
import com.example.audio.DspEngine
import com.example.audio.FftProcessor
import com.example.audio.ToneMatcher
import com.example.model.AmpModel
import com.example.model.CabModel
import com.example.model.FactoryPresets
import com.example.model.MicPosition
import com.example.model.PresetCategory
import com.example.model.RigPresetDoc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.tanh

class DspToneMatcherUnitTest {

    @Test
    fun `fft preserves dominant sine frequency bin`() {
        val sr = 48000
        val n = 1024
        val freq = 1000f
        val real = FloatArray(n) { i -> sin(2.0 * PI * freq * i / sr).toFloat() }
        val imag = FloatArray(n)
        FftProcessor.fft(real, imag)

        var maxIdx = 0
        var maxMag = -1f
        for (k in 1 until n / 2) {
            val mag = real[k] * real[k] + imag[k] * imag[k]
            if (mag > maxMag) {
                maxMag = mag
                maxIdx = k
            }
        }
        val detectedHz = maxIdx * (sr.toFloat() / n)
        assertTrue("Expected ~1000Hz, got $detectedHz", abs(detectedHz - freq) < 60f)
    }

    @Test
    fun `dsp engine processes high gain test riff without NaN or clipping past unity`() {
        val dsp = DspEngine(48000)
        dsp.updateConfig(FactoryPresets.all.first().config)
        val input = FloatArray(512)
        val output = FloatArray(512)
        dsp.generateTestRiffBlock(input, 512)
        dsp.processBlock(input, output, 512)

        assertTrue("Output should contain audible signal", output.any { abs(it) > 0.005f })
        assertTrue("All samples must be finite and within [-1, 1]", output.all { it.isFinite() && abs(it) <= 1.0f })
    }

    @Test
    fun `cab ir kernel is synthesized and normalized`() {
        val kernel = CabIrEngine.synthesizeCabinetIr(
            cabModel = CabModel.V30_4X12_TIGHT,
            micPosition = MicPosition.CAP_EDGE,
            resonance = 0.55f,
            sampleRate = 48000,
        )
        assertEquals(CabIrEngine.IR_TAPS, kernel.size)
        assertTrue(kernel.all { it.isFinite() })
        assertTrue(kernel.any { abs(it) > 0.01f })
    }

    @Test
    fun `tone matcher detects heavily saturated metal sample and builds matched rig`() {
        val sr = 48000
        val len = sr * 2
        // Synthesize a compressed high-gain distorted metal chord (dense harmonics + low crest factor)
        val samples = FloatArray(len) { i ->
            val t = i.toDouble() / sr
            val raw = sin(2.0 * PI * 82.4 * t) +
                0.7 * sin(2.0 * PI * 164.8 * t) +
                0.6 * sin(2.0 * PI * 246.9 * t) +
                0.4 * sin(2.0 * PI * 1850.0 * t)
            tanh(raw * 6.0).toFloat() * 0.85f
        }
        val decoded = DecodedAudioSample(
            displayName = "heavy_riff.wav",
            samples = samples,
            sampleRate = sr,
            durationSec = 2.0f,
        )
        val result = ToneMatcher.analyzeAndMatch(decoded)
        assertTrue(result.matchedConfig.toneMatched)
        assertTrue(result.matchedConfig.gain >= 0.55f)
        assertEquals(24, result.targetSpectrumDb.size)
        assertEquals(24, result.matchedRigSpectrumDb.size)
        assertTrue(result.matchedConfig.ampModel != AmpModel.JAZZ_CLEAN)
    }

    @Test
    fun `rig preset doc toCreateMap excludes id and preserves bounds`() {
        val doc = RigPresetDoc.fromRigConfig(
            userId = "user_123",
            name = "Test Rig",
            category = PresetCategory.MODERN_METAL,
            favorite = true,
            config = FactoryPresets.all.first().config,
        )
        val createMap = doc.toCreateMap()
        assertFalse("Must never include @DocumentId 'id' key", createMap.containsKey("id"))
        assertEquals("user_123", createMap["userId"])
        assertEquals(7, (createMap["eqBands"] as List<*>).size)
    }
}
