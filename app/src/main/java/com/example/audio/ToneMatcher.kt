package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.example.model.AmpModel
import com.example.model.CabModel
import com.example.model.MicPosition
import com.example.model.RigConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class ToneMatchResult(
    val sampleName: String,
    val sampleDurationSec: Float,
    val confidencePercent: Int,
    val targetSpectrumDb: List<Float>,
    val matchedRigSpectrumDb: List<Float>,
    val customIrOffsetCurveDb: List<Float>,
    val matchedConfig: RigConfig,
    val detectedTraits: List<String>,
    val summary: String,
)

object ToneMatcher {
    private const val FFT_SIZE = 4096
    private const val HOP_SIZE = 2048

    /**
     * Analyzes an uploaded guitar audio sample and maps its spectral tilt, mid contour,
     * dynamic crest factor, harmonic saturation density, and brightness onto the
     * ShredForge amplifier, pedalboard, and impulse response cabinet rig.
     */
    fun analyzeAndMatch(sample: DecodedAudioSample): ToneMatchResult {
        val pcm = sample.samples
        val sr = sample.sampleRate
        val hann = FftProcessor.hannWindow(FFT_SIZE)
        val real = FloatArray(FFT_SIZE)
        val imag = FloatArray(FFT_SIZE)
        val avgMag = FloatArray(FFT_SIZE / 2)

        var frameCount = 0
        var totalPeak = 0f
        var totalRmsSq = 0.0
        var totalZcr = 0.0
        var totalSamplesChecked = 0

        // Multi-frame FFT power averaging over active (non-silent) guitar frames
        var offset = 0
        while (offset + FFT_SIZE <= pcm.size) {
            var frameSq = 0f
            var framePeak = 0f
            var zc = 0
            for (i in 0 until FFT_SIZE) {
                val s = pcm[offset + i]
                frameSq += s * s
                val a = abs(s)
                if (a > framePeak) framePeak = a
                if (i > 0 && ((s >= 0f && pcm[offset + i - 1] < 0f) || (s < 0f && pcm[offset + i - 1] >= 0f))) {
                    zc++
                }
            }
            val frameRms = sqrt(frameSq / FFT_SIZE)
            if (frameRms > 0.015f) {
                for (i in 0 until FFT_SIZE) {
                    real[i] = pcm[offset + i] * hann[i]
                    imag[i] = 0f
                }
                FftProcessor.fft(real, imag)
                for (k in 0 until FFT_SIZE / 2) {
                    val m = sqrt(real[k] * real[k] + imag[k] * imag[k])
                    avgMag[k] += m
                }
                totalRmsSq += frameSq
                if (framePeak > totalPeak) totalPeak = framePeak
                totalZcr += zc.toDouble() / FFT_SIZE
                totalSamplesChecked += FFT_SIZE
                frameCount++
            }
            offset += HOP_SIZE
        }

        // Fallback if sample is very short or quiet
        if (frameCount == 0) {
            val len = min(FFT_SIZE, pcm.size)
            for (i in 0 until FFT_SIZE) {
                real[i] = if (i < len) pcm[i] * hann[i] else 0f
                imag[i] = 0f
            }
            FftProcessor.fft(real, imag)
            for (k in 0 until FFT_SIZE / 2) {
                avgMag[k] = sqrt(real[k] * real[k] + imag[k] * imag[k])
            }
            frameCount = 1
            totalPeak = pcm.maxOfOrNull { abs(it) } ?: 0.5f
            totalRmsSq = pcm.sumOf { (it * it).toDouble() }.coerceAtLeast(1e-4)
            totalSamplesChecked = pcm.size.coerceAtLeast(1)
        }

        for (k in avgMag.indices) avgMag[k] /= frameCount.toFloat()

        // 24-band spectral curve (-48..0 dB)
        val targetCurve24 = FftProcessor.extractLogBandsDb(avgMag, sr, FFT_SIZE, bandCount = 24)

        // Compute musical spectral band energy averages
        val subBass = bandAvgDb(avgMag, sr, 45f, 95f)
        val lowChunk = bandAvgDb(avgMag, sr, 95f, 220f)
        val lowMid = bandAvgDb(avgMag, sr, 220f, 480f)
        val midCore = bandAvgDb(avgMag, sr, 480f, 1100f)
        val upperMid = bandAvgDb(avgMag, sr, 1100f, 2800f)
        val presenceFizz = bandAvgDb(avgMag, sr, 2800f, 5800f)
        val highAir = bandAvgDb(avgMag, sr, 5800f, 10000f)

        // Compute spectral centroid (Hz)
        val binHz = sr.toFloat() / FFT_SIZE
        var weightedSum = 0.0
        var magSum = 0.0
        for (k in 2 until FFT_SIZE / 2) {
            val freq = k * binHz
            if (freq in 50f..11000f) {
                val m = avgMag[k].toDouble()
                weightedSum += freq * m
                magSum += m
            }
        }
        val centroidHz = if (magSum > 1e-9) (weightedSum / magSum).toFloat() else 1400f

        // Crest factor (dB): compressed wall-of-sound metal is 4..9 dB; crunch 9..13 dB; clean 13+ dB
        val globalRms = sqrt(totalRmsSq / max(1, totalSamplesChecked)).toFloat()
        val crestDb = (20f * log10(max(totalPeak, 1e-5f) / max(globalRms, 1e-5f))).coerceIn(3f, 22f)
        val zcr = (totalZcr / max(1, frameCount)).toFloat()

        // Sonic indices
        val midScoopDb = ((lowChunk + upperMid) * 0.5f) - midCore // positive = scooped mids
        val lowTightness = lowChunk - subBass // higher = tighter low-end cut
        val brightnessIndex = ((upperMid + presenceFizz) * 0.5f) - lowMid
        val highRollOffDelta = presenceFizz - highAir

        // Estimate saturation / gain (0..1)
        val gainFromCrest = ((14.5f - crestDb) / 9.5f).coerceIn(0f, 1f)
        val gainFromHarmonics = ((presenceFizz - subBass + 18f) / 30f).coerceIn(0f, 1f)
        val estimatedGain = (gainFromCrest * 0.65f + gainFromHarmonics * 0.35f).coerceIn(0.22f, 0.96f)

        // Select optimal AmpModel
        val ampModel = when {
            estimatedGain < 0.38f && crestDb > 12.5f -> AmpModel.JAZZ_CLEAN
            subBass > lowChunk - 1.5f && centroidHz < 1250f && estimatedGain > 0.68f -> AmpModel.DOOM_FUZZ
            lowTightness > 4.5f && midScoopDb < 1.5f && centroidHz > 1550f && estimatedGain > 0.65f -> AmpModel.DJENT_PRECISION
            midScoopDb > 3.2f && estimatedGain > 0.62f -> AmpModel.RECTO_ULTRA
            estimatedGain in 0.42f..0.68f && centroidHz in 1100f..1900f -> AmpModel.BRIT_HOTROD
            else -> AmpModel.PEV_6505
        }

        // Select Cabinet IR & Mic Position
        val cabModel = CabModel.CUSTOM_MATCH_IR
        val micPos = when {
            presenceFizz > upperMid - 1.5f || centroidHz > 1950f -> MicPosition.CAP_CENTER
            highAir < presenceFizz - 9f || centroidHz < 1150f -> MicPosition.OFF_AXIS
            else -> MicPosition.CAP_EDGE
        }

        // Compute Tone Stack & Overdrive settings
        val odNeeded = estimatedGain > 0.58f && ampModel != AmpModel.DOOM_FUZZ
        val bassKnob = ((0.52f + (subBass - midCore) * 0.018f)).coerceIn(0.28f, 0.85f)
        val midKnob = ((0.54f - midScoopDb * 0.032f)).coerceIn(0.20f, 0.82f)
        val trebleKnob = ((0.58f + brightnessIndex * 0.022f)).coerceIn(0.35f, 0.88f)
        val presenceKnob = ((0.55f + (presenceFizz - midCore) * 0.020f)).coerceIn(0.32f, 0.90f)
        val depthKnob = ((0.50f + (subBass - lowChunk) * 0.022f)).coerceIn(0.30f, 0.85f)

        // Compute 7-band EQ delta to match sample envelope at [80, 160, 400, 800, 1600, 3200, 6400 Hz]
        val bandMeans = listOf(
            bandAvgDb(avgMag, sr, 60f, 110f),
            bandAvgDb(avgMag, sr, 110f, 240f),
            bandAvgDb(avgMag, sr, 240f, 560f),
            bandAvgDb(avgMag, sr, 560f, 1150f),
            bandAvgDb(avgMag, sr, 1150f, 2300f),
            bandAvgDb(avgMag, sr, 2300f, 4500f),
            bandAvgDb(avgMag, sr, 4500f, 8500f),
        )
        val refPivot = bandMeans.average().toFloat()
        // Gentle metal reference bias so EQ steers smoothly without extreme boosts
        val eqBands = bandMeans.mapIndexed { idx, bDb ->
            val targetShape = (bDb - refPivot) * 0.55f
            val highCutCorrection = if (idx == 6 && highRollOffDelta > 10f) -2.5f else 0f
            (targetShape + highCutCorrection).coerceIn(-9.5f, 9.5f)
        }

        val gateThresh = if (estimatedGain > 0.65f) -38f else -50f
        val matchedConfig = RigConfig(
            ampModel = ampModel,
            gain = if (ampModel == AmpModel.JAZZ_CLEAN) 0.28f else estimatedGain.coerceIn(0.50f, 0.94f),
            bass = bassKnob,
            mid = midKnob,
            treble = trebleKnob,
            presence = presenceKnob,
            depth = depthKnob,
            master = 0.72f,
            cabModel = cabModel,
            micPosition = micPos,
            cabResonance = ((0.48f + (subBass - lowMid) * 0.015f)).coerceIn(0.30f, 0.80f),
            gateEnabled = estimatedGain > 0.50f,
            gateThresholdDb = gateThresh,
            odEnabled = odNeeded,
            odDrive = if (lowTightness > 4f) 0.10f else 0.24f,
            odTone = ((0.64f + brightnessIndex * 0.015f)).coerceIn(0.45f, 0.88f),
            odLevel = 0.86f,
            eqEnabled = true,
            eqBands = eqBands,
            chorusEnabled = ampModel == AmpModel.JAZZ_CLEAN,
            chorusMix = if (ampModel == AmpModel.JAZZ_CLEAN) 0.35f else 0.18f,
            delayEnabled = false,
            reverbEnabled = true,
            reverbDecay = 0.22f,
            reverbMix = if (ampModel == AmpModel.JAZZ_CLEAN) 0.30f else 0.10f,
            toneMatched = true,
            toneMatchSummary = null, // populated below
        )

        // Compute spectral offset curve for CabIrEngine custom FIR shaping & simulate matched rig response
        val customIrOffsets = targetCurve24.mapIndexed { idx, targetDb ->
            val centerF = FftProcessor.computeBandCenterFrequencies(24)[idx]
            val baselineDb = -10f - (abs(centerF - 1200f) / 1000f) * 2.2f
            (targetDb - baselineDb).coerceIn(-12f, 12f)
        }

        // Simulate our matched DSP rig on an impulse + pink-like harmonic excitation to verify spectral fit
        val simRigCurve24 = simulateRigSpectrum(matchedConfig, customIrOffsets, sr)
        var diffSq = 0f
        for (i in 0 until 24) {
            val d = targetCurve24[i] - simRigCurve24[i]
            diffSq += d * d
        }
        val rmseDb = sqrt(diffSq / 24f)
        val confidence = (98f - rmseDb * 1.4f).roundToInt().coerceIn(76, 97)

        val traits = buildList {
            when {
                estimatedGain > 0.75f -> add("Saturated High-Gain")
                estimatedGain > 0.52f -> add("Driven Crunch")
                else -> add("Clean Headroom")
            }
            if (midScoopDb > 2.5f) add("Scooped 750Hz Mids")
            else if (midScoopDb < -1.0f) add("Aggressive Mid Bark")
            if (lowTightness > 4f) add("Tight 80Hz Low-Cut")
            else if (subBass > lowChunk - 2f) add("Sub-Heavy Doom Low")
            if (centroidHz > 1800f) add("Crisp V30 Bite")
            else add("Warm Cone Voicing")
        }

        val summary = "${ampModel.displayName} • ${traits.take(3).joinToString(" • ")}"
        return ToneMatchResult(
            sampleName = sample.displayName,
            sampleDurationSec = sample.durationSec,
            confidencePercent = confidence,
            targetSpectrumDb = targetCurve24,
            matchedRigSpectrumDb = simRigCurve24,
            customIrOffsetCurveDb = customIrOffsets,
            matchedConfig = matchedConfig.copy(toneMatchSummary = summary),
            detectedTraits = traits,
            summary = summary,
        )
    }

    private fun simulateRigSpectrum(
        config: RigConfig,
        customIrOffsets: List<Float>,
        sampleRate: Int,
    ): List<Float> {
        val dsp = DspEngine(sampleRate)
        dsp.setToneMatchCurve(customIrOffsets)
        dsp.updateConfig(config)
        val excitation = FloatArray(4096)
        val testGen = TestRiffGenerator(sampleRate)
        testGen.renderBlock(excitation, 4096)
        val processed = FloatArray(4096)
        dsp.processBlock(excitation, processed, 4096)

        val hann = FftProcessor.hannWindow(4096)
        val real = FloatArray(4096) { processed[it] * hann[it] }
        val imag = FloatArray(4096)
        FftProcessor.fft(real, imag)
        val mag = FloatArray(2048) { k -> sqrt(real[k] * real[k] + imag[k] * imag[k]) }
        return FftProcessor.extractLogBandsDb(mag, sampleRate, 4096, bandCount = 24)
    }

    private fun bandAvgDb(mag: FloatArray, sampleRate: Int, fLow: Float, fHigh: Float): Float {
        val binHz = sampleRate.toFloat() / FFT_SIZE
        val b0 = (fLow / binHz).toInt().coerceIn(1, mag.lastIndex)
        val b1 = (fHigh / binHz).toInt().coerceIn(b0, mag.lastIndex)
        var sumSq = 0f
        var count = 0
        for (k in b0..b1) {
            sumSq += mag[k] * mag[k]
            count++
        }
        val rms = sqrt(sumSq / max(1, count))
        return 20f * log10(max(rms, 1e-7f))
    }
}

/**
 * Plays back a decoded audio sample so the user can A/B compare the uploaded reference tone
 * against their live ShredForge rig.
 */
object ToneSamplePlayer {
    suspend fun playSample(sample: DecodedAudioSample, onFinished: () -> Unit) =
        withContext(Dispatchers.IO) {
            val minBuf = AudioTrack.getMinBufferSize(
                sample.sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_FLOAT,
            ).coerceAtLeast(4096)
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setSampleRate(sample.sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBuf)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            try {
                track.play()
                val chunk = 1024
                var pos = 0
                while (pos < sample.samples.size) {
                    val count = min(chunk, sample.samples.size - pos)
                    track.write(sample.samples, pos, count, AudioTrack.WRITE_BLOCKING)
                    pos += count
                }
            } finally {
                runCatching { track.stop() }
                runCatching { track.release() }
                withContext(Dispatchers.Main) { onFinished() }
            }
        }
}
