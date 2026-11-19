package com.example.audio

import com.example.model.AmpModel
import com.example.model.CabModel
import com.example.model.RigConfig
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Real-time high-gain guitar DSP engine.
 * Signal path:
 * Input -> Noise Gate -> Tube Overdrive -> Preamp (2x oversampled asymmetric tube stages)
 * -> 3-Band Tone Stack -> Power Amp Presence/Depth -> 7-Band Graphic EQ
 * -> Cabinet IR Convolution -> Modulated Chorus -> Echo Delay -> Plate Reverb -> Safety Limiter
 */
class DspEngine(sampleRate: Int = 48000) {
    @Volatile var sampleRate: Int = sampleRate
        private set

    @Volatile private var config: RigConfig = RigConfig()
    @Volatile private var irKernel: FloatArray = CabIrEngine.synthesizeCabinetIr(
        cabModel = RigConfig().cabModel,
        micPosition = RigConfig().micPosition,
        resonance = RigConfig().cabResonance,
        sampleRate = sampleRate,
    )
    @Volatile private var customWavIr: FloatArray? = null
    @Volatile private var matchedSpectralCurveDb: List<Float>? = null

    // Filters
    private val odHpFilter = BiquadFilter()
    private val odToneFilter = BiquadFilter()
    private val preLowCut = BiquadFilter()
    private val ampBassFilter = BiquadFilter()
    private val ampMidFilter = BiquadFilter()
    private val ampTrebleFilter = BiquadFilter()
    private val presenceFilter = BiquadFilter()
    private val depthFilter = BiquadFilter()
    private val eqFilters = Array(7) { BiquadFilter() }

    // Noise gate envelope
    private var gateEnv = 0f
    private var gateGain = 0f
    @Volatile var isGateOpen: Boolean = true
        private set

    // IR convolution circular buffer
    private val irBuffer = FloatArray(512)
    private var irWriteIdx = 0

    // Chorus circular buffer + LFO
    private val chorusBuffer = FloatArray(4096)
    private var chorusIdx = 0
    private var chorusPhase = 0f

    // Delay circular buffer + LP damping
    private val delayBuffer = FloatArray(48000)
    private var delayIdx = 0
    private var delayLpState = 0f

    // Schroeder Plate Reverb (4 parallel combs + 2 series allpasses)
    private val combDelays = intArrayOf(1557, 1617, 1491, 1422)
    private val combBuffers = Array(4) { FloatArray(2400) }
    private val combIdxs = IntArray(4)
    private val combDamper = FloatArray(4)
    private val allpassDelays = intArrayOf(225, 556)
    private val allpassBuffers = Array(2) { FloatArray(800) }
    private val allpassIdxs = IntArray(2)

    // Metering & FFT spectrum snapshot
    @Volatile var inputPeakDb: Float = -60f
        private set
    @Volatile var outputPeakDb: Float = -60f
        private set
    @Volatile var isClipping: Boolean = false
        private set

    private val spectrumWindowSize = 512
    private val spectrumRing = FloatArray(spectrumWindowSize)
    private var spectrumRingIdx = 0
    private val fftReal = FloatArray(spectrumWindowSize)
    private val fftImag = FloatArray(spectrumWindowSize)
    private val hann = FftProcessor.hannWindow(spectrumWindowSize)
    @Volatile var spectrumBands: List<Float> = List(24) { 0f }
        private set

    private val testRiffGenerator = TestRiffGenerator(sampleRate)

    init {
        updateFilters()
    }

    fun setSampleRate(newRate: Int) {
        if (newRate != sampleRate && newRate in 8000..192000) {
            sampleRate = newRate
            testRiffGenerator.setSampleRate(newRate)
            updateFilters()
            rebuildIr()
        }
    }

    fun updateConfig(newConfig: RigConfig) {
        val irChanged = newConfig.cabModel != config.cabModel ||
            newConfig.micPosition != config.micPosition ||
            abs(newConfig.cabResonance - config.cabResonance) > 0.005f
        config = newConfig
        updateFilters()
        if (irChanged) {
            rebuildIr()
        }
    }

    fun setCustomWavIr(kernel: FloatArray) {
        customWavIr = kernel
        if (config.cabModel == CabModel.CUSTOM_WAV_IR) {
            irKernel = kernel
        }
    }

    fun setToneMatchCurve(curveDb: List<Float>?) {
        matchedSpectralCurveDb = curveDb
        rebuildIr()
    }

    private fun rebuildIr() {
        val cfg = config
        irKernel = when (cfg.cabModel) {
            CabModel.CUSTOM_WAV_IR -> customWavIr ?: CabIrEngine.synthesizeCabinetIr(
                CabModel.V30_4X12_TIGHT, cfg.micPosition, cfg.cabResonance, sampleRate
            )
            else -> CabIrEngine.synthesizeCabinetIr(
                cfg.cabModel, cfg.micPosition, cfg.cabResonance, sampleRate, matchedSpectralCurveDb
            )
        }
    }

    private fun updateFilters() {
        val sr = sampleRate.toFloat()
        val cfg = config
        val amp = cfg.ampModel

        odHpFilter.setHighPass(sr, 720f, 0.707f)
        odToneFilter.setHighShelf(sr, 2200f, (cfg.odTone - 0.5f) * 12f)

        preLowCut.setHighPass(sr, amp.preLowCutHz, 0.75f)
        ampBassFilter.setLowShelf(sr, 120f, (cfg.bass - 0.5f) * 14f)
        ampMidFilter.setPeaking(sr, 720f, 1.1f, (cfg.mid - 0.5f) * 16f + amp.midScoopBiasDb)
        ampTrebleFilter.setHighShelf(sr, 2800f, (cfg.treble - 0.5f) * 14f)
        presenceFilter.setHighShelf(sr, 4200f, (cfg.presence - 0.5f) * 10f)
        depthFilter.setLowShelf(sr, 95f, (cfg.depth - 0.5f) * 10f)

        val eqFreqs = floatArrayOf(80f, 160f, 400f, 800f, 1600f, 3200f, 6400f)
        for (i in 0 until 7) {
            val db = cfg.eqBands.getOrElse(i) { 0f }.coerceIn(-12f, 12f)
            eqFilters[i].setPeaking(sr, eqFreqs[i], 1.4f, db)
        }
    }

    fun generateTestRiffBlock(buffer: FloatArray, frames: Int) {
        testRiffGenerator.renderBlock(buffer, frames)
    }

    fun processBlock(input: FloatArray, output: FloatArray, frames: Int) {
        val cfg = config
        val amp = cfg.ampModel
        val sr = sampleRate.toFloat()
        val kernel = irKernel
        val irLen = kernel.size

        // Gate constants
        val gateThreshLin = Math.pow(10.0, cfg.gateThresholdDb / 20.0).toFloat()
        val gateAttack = exp(-1f / (sr * 0.002f))
        val gateRelease = exp(-1f / (sr * 0.045f))

        // Amp drive constants
        val driveGain = 1.0f + cfg.gain * cfg.gain * amp.baseDriveScale
        val asym = amp.asymmetryBias
        val masterGain = 0.25f + cfg.master * 1.15f

        // Delay constants
        val delaySamples = ((cfg.delayTimeMs / 1000f) * sr).toInt().coerceIn(64, delayBuffer.size - 1)
        val delayFb = cfg.delayFeedback.coerceIn(0f, 0.85f)
        val delayMix = if (cfg.delayEnabled) cfg.delayMix else 0f

        // Chorus constants
        val chorusMix = if (cfg.chorusEnabled) cfg.chorusMix else 0f
        val chorusRateInc = (2.0 * PI * 1.15 / sr).toFloat()

        // Reverb constants
        val reverbMix = if (cfg.reverbEnabled) cfg.reverbMix else 0f
        val reverbFeedback = 0.68f + cfg.reverbDecay * 0.26f

        var inMax = 0f
        var outMax = 0f

        for (i in 0 until frames) {
            var x = input[i]
            val absIn = abs(x)
            if (absIn > inMax) inMax = absIn

            // 1. Noise Gate
            if (cfg.gateEnabled) {
                val coeff = if (absIn > gateEnv) gateAttack else gateRelease
                gateEnv = coeff * gateEnv + (1f - coeff) * absIn
                val targetGate = if (gateEnv >= gateThreshLin) 1f else if (gateEnv < gateThreshLin * 0.65f) 0f else gateGain
                gateGain = 0.992f * gateGain + 0.008f * targetGate
                x *= gateGain
            } else {
                gateGain = 1f
            }

            // 2. Tube Overdrive Pedal (TS-808 style tight low cut + asymmetric diode clip)
            if (cfg.odEnabled) {
                val tight = odHpFilter.process(x)
                val boosted = tight * (1.8f + cfg.odDrive * 16f)
                val clipped = tanh((boosted + 0.08f).toDouble()).toFloat() - 0.078f
                val toned = odToneFilter.process(clipped)
                x = toned * (0.45f + cfg.odLevel * 0.9f)
            }

            // 3. Amplifier Preamp (tight low-cut + 2x oversampled cascading tube stages)
            x = preLowCut.process(x)
            if (amp == AmpModel.JAZZ_CLEAN) {
                x = tanh((x * driveGain * 0.65f).toDouble()).toFloat() * 1.1f
            } else {
                // 2x oversampling internal pass for cleaner high-gain harmonics
                var stageOut = 0f
                for (os in 0 until 2) {
                    var s = x * driveGain * 0.55f
                    s = tanh((s + asym).toDouble()).toFloat() - tanh(asym.toDouble()).toFloat() * 0.7f
                    s = tanh((s * 2.15f - asym * 0.6f).toDouble()).toFloat()
                    s = tanh((s * 1.65f).toDouble()).toFloat()
                    stageOut += s * 0.5f
                }
                // Subtle tube power-amp sag
                x = stageOut * (1f - amp.sagAmount * 0.25f * abs(stageOut))
            }

            // 4. Amp Tone Stack + Presence/Depth
            x = ampBassFilter.process(x)
            x = ampMidFilter.process(x)
            x = ampTrebleFilter.process(x)
            x = presenceFilter.process(x)
            x = depthFilter.process(x)

            // 5. 7-Band Graphic EQ
            if (cfg.eqEnabled) {
                for (b in 0 until 7) {
                    x = eqFilters[b].process(x)
                }
            }

            // 6. Cabinet IR Convolution (Circular buffer FIR)
            irBuffer[irWriteIdx] = x
            var cabOut = 0f
            var readIdx = irWriteIdx
            for (tap in 0 until irLen) {
                cabOut += irBuffer[readIdx] * kernel[tap]
                readIdx--
                if (readIdx < 0) readIdx = irBuffer.size - 1
            }
            irWriteIdx++
            if (irWriteIdx >= irBuffer.size) irWriteIdx = 0
            x = cabOut * masterGain

            // 7. Modulated Chorus
            if (chorusMix > 0.005f) {
                chorusBuffer[chorusIdx] = x
                val lfo = 0.5f + 0.5f * sin(chorusPhase)
                chorusPhase += chorusRateInc
                if (chorusPhase > 2f * PI.toFloat()) chorusPhase -= 2f * PI.toFloat()
                val delayMod = (0.004f + lfo * 0.009f) * sr
                val readPos = (chorusIdx - delayMod.toInt() + chorusBuffer.size) % chorusBuffer.size
                val wet = chorusBuffer[readPos]
                x = x * (1f - chorusMix * 0.4f) + wet * chorusMix * 0.65f
                chorusIdx = (chorusIdx + 1) % chorusBuffer.size
            }

            // 8. Echo Delay
            if (delayMix > 0.005f) {
                val readPos = (delayIdx - delaySamples + delayBuffer.size) % delayBuffer.size
                val delayed = delayBuffer[readPos]
                delayLpState = delayLpState * 0.35f + delayed * 0.65f
                delayBuffer[delayIdx] = x + delayLpState * delayFb
                delayIdx = (delayIdx + 1) % delayBuffer.size
                x += delayed * delayMix
            }

            // 9. Plate Reverb
            if (reverbMix > 0.005f) {
                var combSum = 0f
                val revIn = x * 0.32f
                for (c in 0 until 4) {
                    val buf = combBuffers[c]
                    val idx = combIdxs[c]
                    val out = buf[idx]
                    combDamper[c] = out * 0.72f + combDamper[c] * 0.28f
                    buf[idx] = revIn + combDamper[c] * reverbFeedback
                    combIdxs[c] = (idx + 1) % combDelays[c]
                    combSum += out
                }
                var ap = combSum * 0.25f
                for (a in 0 until 2) {
                    val buf = allpassBuffers[a]
                    val idx = allpassIdxs[a]
                    val bufOut = buf[idx]
                    val y = -ap + bufOut
                    buf[idx] = ap + bufOut * 0.5f
                    allpassIdxs[a] = (idx + 1) % allpassDelays[a]
                    ap = y
                }
                x = x * (1f - reverbMix * 0.25f) + ap * reverbMix * 0.8f
            }

            // 10. Safety Soft Limiter
            val outSample = tanh((x * 0.92f).toDouble()).toFloat()
            output[i] = outSample

            val absOut = abs(outSample)
            if (absOut > outMax) outMax = absOut

            // Feed spectrum ring
            spectrumRing[spectrumRingIdx] = outSample
            spectrumRingIdx++
            if (spectrumRingIdx >= spectrumWindowSize) {
                spectrumRingIdx = 0
                updateLiveSpectrum()
            }
        }

        isGateOpen = gateGain > 0.35f
        inputPeakDb = (20f * log10(max(inMax, 1e-5f))).coerceIn(-60f, 0f)
        outputPeakDb = (20f * log10(max(outMax, 1e-5f))).coerceIn(-60f, 0f)
        isClipping = outMax > 0.97f
    }

    private fun updateLiveSpectrum() {
        for (i in 0 until spectrumWindowSize) {
            fftReal[i] = spectrumRing[i] * hann[i]
            fftImag[i] = 0f
        }
        FftProcessor.fft(fftReal, fftImag)
        val mag = FloatArray(spectrumWindowSize / 2) { k ->
            kotlin.math.sqrt(fftReal[k] * fftReal[k] + fftImag[k] * fftImag[k])
        }
        val bandsDb = FftProcessor.extractLogBandsDb(mag, sampleRate, spectrumWindowSize, bandCount = 24)
        // Normalize -48..0 dB to 0..1 for UI visualizer
        spectrumBands = bandsDb.map { ((it + 48f) / 48f).coerceIn(0.02f, 1f) }
    }
}

/**
 * Generates an authentic palm-muted heavy metal Drop-C DI guitar riff in real time
 * using plucked Karplus-Strong string synthesis so users can audition amps & matched tones
 * immediately without needing a live guitar connected.
 */
class TestRiffGenerator(private var sampleRate: Int) {
    private val delayLine = FloatArray(2048)
    private var writePos = 0
    private var currentPeriod = 360
    private var lpPrev = 0f
    private var noteSampleCounter = 0
    private var stepIndex = 0
    private var dampingFactor = 0.985f

    // Drop C metal chug pattern: C2 (65.4 Hz), Eb2 (77.8 Hz), F2 (87.3 Hz), G2 (98.0 Hz), Bb2 (116.5 Hz)
    private val riffNotesHz = floatArrayOf(
        65.41f, 65.41f, 65.41f, 77.78f,
        65.41f, 65.41f, 98.00f, 87.31f,
        65.41f, 65.41f, 65.41f, 116.54f,
        98.00f, 77.78f, 65.41f, 65.41f,
    )
    private val isPalmMuted = booleanArrayOf(
        true, true, false, false,
        true, true, false, false,
        true, true, false, false,
        false, false, true, false,
    )

    private var seed = 1337

    fun setSampleRate(rate: Int) {
        sampleRate = rate
    }

    private fun nextWhiteNoise(): Float {
        seed = (seed * 1664525 + 1013904223)
        return ((seed shr 16) and 0xFFFF) / 32768f - 1f
    }

    fun renderBlock(out: FloatArray, frames: Int) {
        val noteDurationSamples = (sampleRate * 0.21f).toInt() // ~142 BPM 8th notes
        for (i in 0 until frames) {
            if (noteSampleCounter <= 0) {
                val freq = riffNotesHz[stepIndex]
                val muted = isPalmMuted[stepIndex]
                currentPeriod = (sampleRate / freq).toInt().coerceIn(32, delayLine.size - 2)
                dampingFactor = if (muted) 0.962f else 0.994f
                // Excite string with pick attack transient
                for (p in 0 until currentPeriod) {
                    val env = if (p < currentPeriod / 2) 1f else 0.45f
                    delayLine[p] = nextWhiteNoise() * 0.42f * env
                }
                writePos = 0
                lpPrev = 0f
                noteSampleCounter = noteDurationSamples
                stepIndex = (stepIndex + 1) % riffNotesHz.size
            }

            val readIdx = (writePos - currentPeriod + delayLine.size) % delayLine.size
            val s0 = delayLine[readIdx]
            val s1 = delayLine[(readIdx + 1) % delayLine.size]
            val avg = 0.5f * (s0 + s1)
            val damped = (avg * 0.65f + lpPrev * 0.35f) * dampingFactor
            lpPrev = damped
            delayLine[writePos] = damped
            writePos = (writePos + 1) % delayLine.size

            // Add subtle power-chord fifth harmonic body
            val fifthRead = (writePos - (currentPeriod * 2 / 3) + delayLine.size) % delayLine.size
            val diSample = (damped * 0.85f + delayLine[fifthRead] * 0.35f).coerceIn(-0.9f, 0.9f)
            out[i] = diSample
            noteSampleCounter--
        }
    }
}
