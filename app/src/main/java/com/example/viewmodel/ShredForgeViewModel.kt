package com.example.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.audio.AudioDeviceManager
import com.example.audio.AudioSampleDecoder
import com.example.audio.AudioTelemetry
import com.example.audio.CabIrEngine
import com.example.audio.DecodedAudioSample
import com.example.audio.ToneMatchResult
import com.example.audio.ToneMatcher
import com.example.audio.ToneSamplePlayer
import com.example.data.PresetRepository
import com.example.model.AmpModel
import com.example.model.CabModel
import com.example.model.FactoryPreset
import com.example.model.FactoryPresets
import com.example.model.MicPosition
import com.example.model.PresetCategory
import com.example.model.RigConfig
import com.example.model.RigPresetDoc
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class StudioTab(val label: String) {
    SIGNAL_CHAIN("Signal"),
    AMP_CAB("Amp"),
    PEDALBOARD("Pedals"),
    TONE_MATCH("Match"),
    PRESETS("Presets"),
}

data class ToneMatchUiState(
    val analyzing: Boolean = false,
    val playingSample: Boolean = false,
    val decodedSample: DecodedAudioSample? = null,
    val result: ToneMatchResult? = null,
    val appliedToRig: Boolean = false,
    val errorMessage: String? = null,
)

class ShredForgeViewModel(
    private val repository: PresetRepository,
    private val audioManager: AudioDeviceManager,
    val currentUserId: String,
    private val appContext: Context,
) : ViewModel() {

    private val _activeTab = MutableStateFlow(StudioTab.SIGNAL_CHAIN)
    val activeTab: StateFlow<StudioTab> = _activeTab.asStateFlow()

    private val _rigConfig = MutableStateFlow(FactoryPresets.all.first().config)
    val rigConfig: StateFlow<RigConfig> = _rigConfig.asStateFlow()

    private val _activePresetName = MutableStateFlow(FactoryPresets.all.first().name)
    val activePresetName: StateFlow<String> = _activePresetName.asStateFlow()

    private val _activeCloudPresetId = MutableStateFlow<String?>(null)
    val activeCloudPresetId: StateFlow<String?> = _activeCloudPresetId.asStateFlow()

    private val _activeCloudPresetCategory = MutableStateFlow(PresetCategory.MODERN_METAL)
    val activeCloudPresetCategory: StateFlow<PresetCategory> = _activeCloudPresetCategory.asStateFlow()

    private val _activeCloudPresetFavorite = MutableStateFlow(false)
    val activeCloudPresetFavorite: StateFlow<Boolean> = _activeCloudPresetFavorite.asStateFlow()

    private val _toneMatchState = MutableStateFlow(ToneMatchUiState())
    val toneMatchState: StateFlow<ToneMatchUiState> = _toneMatchState.asStateFlow()

    private val _selectedCategory = MutableStateFlow<PresetCategory?>(null)
    val selectedCategory: StateFlow<PresetCategory?> = _selectedCategory.asStateFlow()

    private val _favoritesOnly = MutableStateFlow(false)
    val favoritesOnly: StateFlow<Boolean> = _favoritesOnly.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _savingPreset = MutableStateFlow(false)
    val savingPreset: StateFlow<Boolean> = _savingPreset.asStateFlow()

    val audioTelemetry: StateFlow<AudioTelemetry> = audioManager.telemetry

    val presetsState: StateFlow<UiState<List<RigPresetDoc>>> = repository.observePresets(currentUserId)
        .map<List<RigPresetDoc>, UiState<List<RigPresetDoc>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing presets", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load cloud presets."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading,
        )

    init {
        audioManager.updateRigConfig(_rigConfig.value)
    }

    fun selectTab(tab: StudioTab) {
        _activeTab.value = tab
    }

    fun clearStatusBanner() {
        _statusBanner.value = null
    }

    fun updateRig(transform: (RigConfig) -> RigConfig) {
        _rigConfig.update { current ->
            val next = transform(current)
            audioManager.updateRigConfig(next)
            next
        }
    }

    fun selectAmpModel(amp: AmpModel) {
        updateRig { it.copy(ampModel = amp, toneMatched = false) }
    }

    fun selectCabModel(cab: CabModel) {
        updateRig { it.copy(cabModel = cab) }
    }

    fun selectMicPosition(mic: MicPosition) {
        updateRig { it.copy(micPosition = mic) }
    }

    fun updateEqBand(index: Int, db: Float) {
        updateRig { current ->
            val updated = current.eqBands.toMutableList()
            if (index in updated.indices) {
                updated[index] = db.coerceIn(-12f, 12f)
            }
            current.copy(eqBands = updated)
        }
    }

    fun loadCustomWavIr(uri: Uri) {
        viewModelScope.launch {
            val sr = audioTelemetry.value.sampleRate
            val name = withContext(Dispatchers.IO) {
                AudioSampleDecoder.queryDisplayName(appContext, uri)
            }
            val result = withContext(Dispatchers.IO) {
                CabIrEngine.loadWavIr(appContext, uri, sr)
            }
            result.onSuccess { kernel ->
                audioManager.setCustomWavIr(kernel)
                updateRig {
                    it.copy(
                        cabModel = CabModel.CUSTOM_WAV_IR,
                        customIrName = name,
                    )
                }
                _statusBanner.value = "Loaded IR: $name"
            }.onFailure { err ->
                _statusBanner.value = "IR load failed: ${err.message ?: "Invalid WAV file"}"
            }
        }
    }

    // Audio Engine & USB I/O controls
    fun toggleEngine() = audioManager.toggleEngine()
    fun startEngine() = audioManager.startEngine()
    fun setTestRiffMode(enabled: Boolean) = audioManager.setTestRiffMode(enabled)
    fun refreshAudioPermissions() = audioManager.refreshPermissionState()
    fun refreshAudioDevices() = audioManager.refreshDevices()
    fun setSelectedInput(id: Int) = audioManager.setSelectedInput(id)
    fun setSelectedOutput(id: Int) = audioManager.setSelectedOutput(id)
    fun setBufferFrames(frames: Int) = audioManager.setBufferFrames(frames)
    fun setBitPerfectUsb(enabled: Boolean) = audioManager.setBitPerfectUsb(enabled)

    // Tone Matching
    fun analyzeToneSample(uri: Uri) {
        viewModelScope.launch {
            _toneMatchState.update {
                it.copy(analyzing = true, errorMessage = null, appliedToRig = false)
            }
            val decodedRes = withContext(Dispatchers.IO) {
                AudioSampleDecoder.decode(appContext, uri)
            }
            decodedRes.onFailure { err ->
                _toneMatchState.update {
                    it.copy(
                        analyzing = false,
                        errorMessage = err.message ?: "Could not decode audio sample.",
                    )
                }
                return@launch
            }
            val sample = decodedRes.getOrThrow()
            val matchResult = withContext(Dispatchers.Default) {
                ToneMatcher.analyzeAndMatch(sample)
            }
            _toneMatchState.update {
                it.copy(
                    analyzing = false,
                    decodedSample = sample,
                    result = matchResult,
                    appliedToRig = false,
                    errorMessage = null,
                )
            }
        }
    }

    fun applyMatchedToneToRig() {
        val result = _toneMatchState.value.result ?: return
        audioManager.setToneMatchCurve(result.customIrOffsetCurveDb)
        _rigConfig.value = result.matchedConfig
        audioManager.updateRigConfig(result.matchedConfig)
        _activePresetName.value = "Matched: ${result.sampleName.substringBeforeLast('.')}"
        _activeCloudPresetId.value = null
        _toneMatchState.update { it.copy(appliedToRig = true) }
        _statusBanner.value = "Tone Match applied (${result.confidencePercent}% spectral fit)"
        if (!audioTelemetry.value.engineRunning) {
            audioManager.startEngine()
        }
    }

    fun playUploadedToneSample() {
        val sample = _toneMatchState.value.decodedSample ?: return
        if (_toneMatchState.value.playingSample) return
        val wasRunning = audioTelemetry.value.engineRunning
        if (wasRunning) audioManager.stopEngine()
        _toneMatchState.update { it.copy(playingSample = true) }
        viewModelScope.launch {
            ToneSamplePlayer.playSample(sample) {
                _toneMatchState.update { it.copy(playingSample = false) }
                if (wasRunning) audioManager.startEngine()
            }
        }
    }

    // Preset Management
    fun setCategoryFilter(category: PresetCategory?) {
        _selectedCategory.value = category
    }

    fun toggleFavoritesOnly() {
        _favoritesOnly.update { !it }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun loadFactoryPreset(preset: FactoryPreset) {
        audioManager.setToneMatchCurve(null)
        _rigConfig.value = preset.config
        audioManager.updateRigConfig(preset.config)
        _activePresetName.value = preset.name
        _activeCloudPresetId.value = null
        _statusBanner.value = "Loaded factory preset: ${preset.name}"
    }

    fun loadCloudPreset(doc: RigPresetDoc) {
        val cfg = doc.toRigConfig()
        if (!cfg.toneMatched) {
            audioManager.setToneMatchCurve(null)
        }
        _rigConfig.value = cfg
        audioManager.updateRigConfig(cfg)
        _activePresetName.value = doc.name
        _activeCloudPresetId.value = doc.id
        _activeCloudPresetCategory.value = PresetCategory.fromId(doc.category)
        _activeCloudPresetFavorite.value = doc.favorite
        _statusBanner.value = "Loaded rig: ${doc.name}"
    }

    fun saveNewCloudPreset(
        name: String,
        category: PresetCategory,
        favorite: Boolean,
    ) {
        viewModelScope.launch {
            _savingPreset.value = true
            val res = repository.createPreset(
                userId = currentUserId,
                name = name,
                category = category,
                favorite = favorite,
                config = _rigConfig.value,
            )
            _savingPreset.value = false
            res.onSuccess { newId ->
                _activePresetName.value = name.trim()
                _activeCloudPresetId.value = newId
                _activeCloudPresetCategory.value = category
                _activeCloudPresetFavorite.value = favorite
                _statusBanner.value = "Saved preset \"$name\" to cloud"
            }.onFailure { err ->
                _statusBanner.value = err.message ?: "Failed to save preset"
            }
        }
    }

    fun overwriteActiveCloudPreset() {
        val id = _activeCloudPresetId.value ?: return
        val name = _activePresetName.value
        val category = _activeCloudPresetCategory.value
        val fav = _activeCloudPresetFavorite.value
        viewModelScope.launch {
            _savingPreset.value = true
            val res = repository.updatePreset(
                userId = currentUserId,
                presetId = id,
                name = name,
                category = category,
                favorite = fav,
                config = _rigConfig.value,
            )
            _savingPreset.value = false
            res.onSuccess {
                _statusBanner.value = "Updated \"$name\""
            }.onFailure { err ->
                _statusBanner.value = err.message ?: "Failed to update preset"
            }
        }
    }

    fun togglePresetFavorite(doc: RigPresetDoc) {
        viewModelScope.launch {
            val nextFav = !doc.favorite
            val res = repository.toggleFavorite(currentUserId, doc.id, nextFav)
            if (res.isSuccess && _activeCloudPresetId.value == doc.id) {
                _activeCloudPresetFavorite.value = nextFav
            }
            res.onFailure { err ->
                _statusBanner.value = err.message ?: "Could not update favorite"
            }
        }
    }

    fun deleteCloudPreset(doc: RigPresetDoc) {
        viewModelScope.launch {
            val res = repository.deletePreset(currentUserId, doc.id)
            res.onSuccess {
                if (_activeCloudPresetId.value == doc.id) {
                    _activeCloudPresetId.value = null
                }
                _statusBanner.value = "Deleted \"${doc.name}\""
            }.onFailure { err ->
                _statusBanner.value = err.message ?: "Failed to delete preset"
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioManager.release()
    }

    private companion object {
        const val TAG = "ShredForgeVM"
    }
}
