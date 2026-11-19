package com.example.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioMixerAttributes
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import android.os.Process
import androidx.core.content.ContextCompat
import com.example.model.RigConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.Executors
import kotlin.concurrent.thread

data class AudioEndpoint(
    val id: Int,
    val name: String,
    val typeLabel: String,
    val isUsb: Boolean,
    val deviceInfo: AudioDeviceInfo?,
)

data class AudioTelemetry(
    val engineRunning: Boolean = false,
    val testRiffMode: Boolean = true,
    val hasRecordPermission: Boolean = false,
    val sampleRate: Int = 48000,
    val bufferFrames: Int = 192,
    val estimatedLatencyMs: Float = 8.0f,
    val bitPerfectUsbRequested: Boolean = true,
    val bitPerfectUsbActive: Boolean = false,
    val inputDevices: List<AudioEndpoint> = emptyList(),
    val outputDevices: List<AudioEndpoint> = emptyList(),
    val selectedInputId: Int? = null,
    val selectedOutputId: Int? = null,
    val usbConnected: Boolean = false,
    val activeUsbName: String? = null,
    val inputPeakDb: Float = -60f,
    val outputPeakDb: Float = -60f,
    val isClipping: Boolean = false,
    val isGateOpen: Boolean = true,
    val spectrumBands: List<Float> = List(24) { 0.05f },
    val statusError: String? = null,
)

class AudioDeviceManager(private val context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val dspEngine = DspEngine(queryOptimalSampleRate())

    private val _telemetry = MutableStateFlow(
        AudioTelemetry(
            sampleRate = dspEngine.sampleRate,
            bufferFrames = queryOptimalFramesPerBuffer(),
            hasRecordPermission = hasMicPermission(),
        )
    )
    val telemetry: StateFlow<AudioTelemetry> = _telemetry.asStateFlow()

    @Volatile private var running = false
    private var audioThread: Thread? = null
    private var activeTrack: AudioTrack? = null
    private var activeRecord: AudioRecord? = null
    private var activeMixerAttrUsbDevice: AudioDeviceInfo? = null

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            refreshDevices()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            refreshDevices()
        }
    }

    init {
        audioManager.registerAudioDeviceCallback(deviceCallback, null)
        refreshDevices()
    }

    fun hasMicPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun refreshPermissionState() {
        _telemetry.update { it.copy(hasRecordPermission = hasMicPermission()) }
    }

    fun queryOptimalSampleRate(): Int {
        val srStr = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
        return srStr?.toIntOrNull()?.takeIf { it in 22050..192000 } ?: 48000
    }

    private fun queryOptimalFramesPerBuffer(): Int {
        val fpb = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)
        val raw = fpb?.toIntOrNull()?.takeIf { it in 64..1024 } ?: 192
        return raw.coerceIn(64, 512)
    }

    fun refreshDevices() {
        val inputs = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            .filter { !it.isSink }
            .map { info ->
                AudioEndpoint(
                    id = info.id,
                    name = info.productName?.toString()?.takeIf { it.isNotBlank() } ?: deviceTypeName(info.type),
                    typeLabel = deviceTypeName(info.type),
                    isUsb = isUsbType(info.type),
                    deviceInfo = info,
                )
            }
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter { it.isSink }
            .map { info ->
                AudioEndpoint(
                    id = info.id,
                    name = info.productName?.toString()?.takeIf { it.isNotBlank() } ?: deviceTypeName(info.type),
                    typeLabel = deviceTypeName(info.type),
                    isUsb = isUsbType(info.type),
                    deviceInfo = info,
                )
            }

        val usbIn = inputs.firstOrNull { it.isUsb }
        val usbOut = outputs.firstOrNull { it.isUsb }
        val usbConnected = usbIn != null || usbOut != null

        _telemetry.update { state ->
            val chosenIn = state.selectedInputId?.takeIf { id -> inputs.any { it.id == id } }
                ?: usbIn?.id ?: inputs.firstOrNull()?.id
            val chosenOut = state.selectedOutputId?.takeIf { id -> outputs.any { it.id == id } }
                ?: usbOut?.id ?: outputs.firstOrNull()?.id
            state.copy(
                inputDevices = inputs,
                outputDevices = outputs,
                selectedInputId = chosenIn,
                selectedOutputId = chosenOut,
                usbConnected = usbConnected,
                activeUsbName = usbIn?.name ?: usbOut?.name,
                hasRecordPermission = hasMicPermission(),
            )
        }
    }

    fun updateRigConfig(config: RigConfig) {
        dspEngine.updateConfig(config)
    }

    fun setCustomWavIr(kernel: FloatArray) {
        dspEngine.setCustomWavIr(kernel)
    }

    fun setToneMatchCurve(curveDb: List<Float>?) {
        dspEngine.setToneMatchCurve(curveDb)
    }

    fun setTestRiffMode(enabled: Boolean) {
        if (!enabled && !hasMicPermission()) {
            _telemetry.update {
                it.copy(
                    testRiffMode = true,
                    hasRecordPermission = false,
                    statusError = "Grant Microphone / USB Audio Input permission to monitor live guitar.",
                )
            }
            return
        }
        val changed = _telemetry.value.testRiffMode != enabled
        _telemetry.update { it.copy(testRiffMode = enabled, statusError = null) }
        if (changed && running) {
            restartEngine()
        }
    }

    fun setSelectedInput(id: Int) {
        _telemetry.update { it.copy(selectedInputId = id) }
        if (running && !_telemetry.value.testRiffMode) restartEngine()
    }

    fun setSelectedOutput(id: Int) {
        _telemetry.update { it.copy(selectedOutputId = id) }
        if (running) restartEngine()
    }

    fun setBufferFrames(frames: Int) {
        val clamped = frames.coerceIn(64, 512)
        val latencyMs = (clamped * 2f / _telemetry.value.sampleRate) * 1000f
        _telemetry.update { it.copy(bufferFrames = clamped, estimatedLatencyMs = latencyMs) }
        if (running) restartEngine()
    }

    fun setBitPerfectUsb(enabled: Boolean) {
        _telemetry.update { it.copy(bitPerfectUsbRequested = enabled) }
        if (running) restartEngine()
    }

    fun toggleEngine() {
        if (running) stopEngine() else startEngine()
    }

    fun startEngine() {
        if (running) return
        val state = _telemetry.value
        val useTestRiff = state.testRiffMode || !hasMicPermission()
        if (state.testRiffMode != useTestRiff) {
            _telemetry.update { it.copy(testRiffMode = useTestRiff) }
        }

        running = true
        _telemetry.update { it.copy(engineRunning = true, statusError = null) }

        audioThread = thread(start = true, name = "ShredForgeDSP") {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            runAudioLoop(useTestRiff)
        }
    }

    fun stopEngine() {
        running = false
        runCatching { audioThread?.join(500) }
        audioThread = null
        releaseStreams()
        _telemetry.update {
            it.copy(
                engineRunning = false,
                inputPeakDb = -60f,
                outputPeakDb = -60f,
                isClipping = false,
                bitPerfectUsbActive = false,
            )
        }
    }

    private fun restartEngine() {
        stopEngine()
        startEngine()
    }

    private fun runAudioLoop(useTestRiff: Boolean) {
        val sr = _telemetry.value.sampleRate
        dspEngine.setSampleRate(sr)
        val blockFrames = _telemetry.value.bufferFrames
        val inBuf = FloatArray(blockFrames)
        val outBuf = FloatArray(blockFrames)
        val stereoOutBuf = FloatArray(blockFrames * 2)

        val audioAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        var bitPerfectActive = false
        val selectedOutDevice = _telemetry.value.outputDevices
            .firstOrNull { it.id == _telemetry.value.selectedOutputId }?.deviceInfo

        var chosenOutFormat = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setSampleRate(sr)
            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
            .build()

        // Try Android 14+ BIT_PERFECT USB Mixer attributes when a USB output is connected
        if (Build.VERSION.SDK_INT >= 34 && selectedOutDevice != null && isUsbType(selectedOutDevice.type) &&
            _telemetry.value.bitPerfectUsbRequested
        ) {
            runCatching {
                val supported = audioManager.getSupportedMixerAttributes(selectedOutDevice)
                val preferred = supported.firstOrNull {
                    it.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT &&
                        it.format.encoding == AudioFormat.ENCODING_PCM_FLOAT
                } ?: supported.firstOrNull {
                    it.format.sampleRate == sr && it.format.encoding == AudioFormat.ENCODING_PCM_FLOAT
                }
                if (preferred != null) {
                    val ok = audioManager.setPreferredMixerAttributes(audioAttrs, selectedOutDevice, preferred)
                    if (ok) {
                        chosenOutFormat = preferred.format
                        bitPerfectActive = preferred.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT
                        activeMixerAttrUsbDevice = selectedOutDevice
                    }
                }
            }
        }

        val minTrackBytes = AudioTrack.getMinBufferSize(
            chosenOutFormat.sampleRate,
            chosenOutFormat.channelMask,
            chosenOutFormat.encoding,
        ).coerceAtLeast(blockFrames * 8)

        val trackBuilder = AudioTrack.Builder()
            .setAudioAttributes(audioAttrs)
            .setAudioFormat(chosenOutFormat)
            .setBufferSizeInBytes(maxOf(minTrackBytes, blockFrames * 8))
            .setTransferMode(AudioTrack.MODE_STREAM)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            trackBuilder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
        }
        val track = try {
            trackBuilder.build()
        } catch (e: Exception) {
            _telemetry.update { it.copy(engineRunning = false, statusError = "AudioTrack init failed: ${e.message}") }
            running = false
            return
        }
        activeTrack = track
        if (selectedOutDevice != null) {
            track.setPreferredDevice(selectedOutDevice)
        }

        var recorder: AudioRecord? = null
        if (!useTestRiff && hasMicPermission()) {
            val source = resolveBestAudioSource()
            val minRecBytes = AudioRecord.getMinBufferSize(
                sr,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_FLOAT,
            ).coerceAtLeast(blockFrames * 4)
            recorder = try {
                AudioRecord.Builder()
                    .setAudioSource(source)
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                            .setSampleRate(sr)
                            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(maxOf(minRecBytes, blockFrames * 4))
                    .build()
            } catch (e: Exception) {
                null
            }
            if (recorder != null && recorder.state == AudioRecord.STATE_INITIALIZED) {
                activeRecord = recorder
                val selectedInDevice = _telemetry.value.inputDevices
                    .firstOrNull { it.id == _telemetry.value.selectedInputId }?.deviceInfo
                if (selectedInDevice != null) {
                    recorder.setPreferredDevice(selectedInDevice)
                }
                recorder.startRecording()
            } else {
                recorder?.release()
                recorder = null
            }
        }

        val actualOutChannels = chosenOutFormat.channelCount.coerceIn(1, 2)
        val latencyMs = ((blockFrames * 2f) / sr) * 1000f
        _telemetry.update {
            it.copy(
                bitPerfectUsbActive = bitPerfectActive,
                estimatedLatencyMs = latencyMs,
            )
        }

        track.play()
        var uiPublishCounter = 0

        while (running) {
            if (recorder != null && !useTestRiff) {
                val read = recorder.read(inBuf, 0, blockFrames, AudioRecord.READ_BLOCKING)
                if (read <= 0) continue
                dspEngine.processBlock(inBuf, outBuf, read)
                writeOutput(track, outBuf, stereoOutBuf, read, actualOutChannels)
            } else {
                dspEngine.generateTestRiffBlock(inBuf, blockFrames)
                dspEngine.processBlock(inBuf, outBuf, blockFrames)
                writeOutput(track, outBuf, stereoOutBuf, blockFrames, actualOutChannels)
            }

            uiPublishCounter++
            if (uiPublishCounter >= 4) {
                uiPublishCounter = 0
                _telemetry.update {
                    it.copy(
                        inputPeakDb = dspEngine.inputPeakDb,
                        outputPeakDb = dspEngine.outputPeakDb,
                        isClipping = dspEngine.isClipping,
                        isGateOpen = dspEngine.isGateOpen,
                        spectrumBands = dspEngine.spectrumBands,
                    )
                }
            }
        }

        releaseStreams()
    }

    private fun writeOutput(
        track: AudioTrack,
        mono: FloatArray,
        stereoScratch: FloatArray,
        frames: Int,
        channels: Int,
    ) {
        if (channels == 1) {
            track.write(mono, 0, frames, AudioTrack.WRITE_BLOCKING)
        } else {
            for (i in 0 until frames) {
                val s = mono[i]
                stereoScratch[i * 2] = s
                stereoScratch[i * 2 + 1] = s
            }
            track.write(stereoScratch, 0, frames * 2, AudioTrack.WRITE_BLOCKING)
        }
    }

    private fun resolveBestAudioSource(): Int {
        val supportsUnprocessed = audioManager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED)
        return if (supportsUnprocessed == "true") {
            MediaRecorder.AudioSource.UNPROCESSED
        } else {
            MediaRecorder.AudioSource.VOICE_RECOGNITION
        }
    }

    private fun releaseStreams() {
        runCatching {
            activeRecord?.let { rec ->
                if (rec.recordingState == AudioRecord.RECORDSTATE_RECORDING) rec.stop()
                rec.release()
            }
        }
        activeRecord = null

        runCatching {
            activeTrack?.let { tr ->
                if (tr.playState == AudioTrack.PLAYSTATE_PLAYING) tr.stop()
                tr.release()
            }
        }
        activeTrack = null

        if (Build.VERSION.SDK_INT >= 34) {
            activeMixerAttrUsbDevice?.let { usbDev ->
                runCatching {
                    val attr = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build()
                    audioManager.clearPreferredMixerAttributes(attr, usbDev)
                }
            }
        }
        activeMixerAttrUsbDevice = null
    }

    fun release() {
        stopEngine()
        runCatching { audioManager.unregisterAudioDeviceCallback(deviceCallback) }
    }

    private fun isUsbType(type: Int): Boolean =
        type == AudioDeviceInfo.TYPE_USB_DEVICE ||
            type == AudioDeviceInfo.TYPE_USB_ACCESSORY ||
            type == AudioDeviceInfo.TYPE_USB_HEADSET

    private fun deviceTypeName(type: Int): String = when (type) {
        AudioDeviceInfo.TYPE_USB_DEVICE -> "USB Audio Interface"
        AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB Accessory"
        AudioDeviceInfo.TYPE_USB_HEADSET -> "USB Headset / DAC"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired Headset"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired Headphones"
        AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Built-in Mic"
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Device Speaker"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET -> "Bluetooth Audio"
        else -> "Audio Device"
    }
}
