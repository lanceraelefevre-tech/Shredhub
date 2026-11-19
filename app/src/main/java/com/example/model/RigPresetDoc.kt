package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class RigPresetDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val category: String = PresetCategory.MODERN_METAL.id,
    val favorite: Boolean = false,
    val ampModelId: String = AmpModel.PEV_6505.id,
    val gain: Double = 0.78,
    val bass: Double = 0.48,
    val mid: Double = 0.54,
    val treble: Double = 0.68,
    val presence: Double = 0.66,
    val depth: Double = 0.52,
    val master: Double = 0.72,
    val cabModelId: String = CabModel.V30_4X12_TIGHT.id,
    val micPosition: String = MicPosition.CAP_EDGE.id,
    val cabResonance: Double = 0.50,
    val gateOn: Boolean = true,
    val gateThresholdDb: Double = -42.0,
    val odOn: Boolean = true,
    val odDrive: Double = 0.14,
    val odTone: Double = 0.72,
    val odLevel: Double = 0.85,
    val eqOn: Boolean = true,
    val eqBands: List<Double> = listOf(-1.5, -1.0, 0.5, 2.5, 1.5, 2.0, -0.5),
    val chorusOn: Boolean = false,
    val chorusMix: Double = 0.25,
    val delayOn: Boolean = false,
    val delayTimeMs: Double = 360.0,
    val delayFeedback: Double = 0.28,
    val delayMix: Double = 0.18,
    val reverbOn: Boolean = true,
    val reverbDecay: Double = 0.26,
    val reverbMix: Double = 0.14,
    val toneMatched: Boolean = false,
    val toneMatchSummary: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun toRigConfig(): RigConfig {
        val safeBands = if (eqBands.size == 7) {
            eqBands.map { it.toFloat().coerceIn(-12f, 12f) }
        } else {
            listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f)
        }
        return RigConfig(
            ampModel = AmpModel.fromId(ampModelId),
            gain = gain.toFloat().coerceIn(0f, 1f),
            bass = bass.toFloat().coerceIn(0f, 1f),
            mid = mid.toFloat().coerceIn(0f, 1f),
            treble = treble.toFloat().coerceIn(0f, 1f),
            presence = presence.toFloat().coerceIn(0f, 1f),
            depth = depth.toFloat().coerceIn(0f, 1f),
            master = master.toFloat().coerceIn(0f, 1f),
            cabModel = CabModel.fromId(cabModelId),
            micPosition = MicPosition.fromId(micPosition),
            cabResonance = cabResonance.toFloat().coerceIn(0f, 1f),
            gateEnabled = gateOn,
            gateThresholdDb = gateThresholdDb.toFloat().coerceIn(-80f, -10f),
            odEnabled = odOn,
            odDrive = odDrive.toFloat().coerceIn(0f, 1f),
            odTone = odTone.toFloat().coerceIn(0f, 1f),
            odLevel = odLevel.toFloat().coerceIn(0f, 1f),
            eqEnabled = eqOn,
            eqBands = safeBands,
            chorusEnabled = chorusOn,
            chorusMix = chorusMix.toFloat().coerceIn(0f, 1f),
            delayEnabled = delayOn,
            delayTimeMs = delayTimeMs.toFloat().coerceIn(40f, 800f),
            delayFeedback = delayFeedback.toFloat().coerceIn(0f, 0.85f),
            delayMix = delayMix.toFloat().coerceIn(0f, 1f),
            reverbEnabled = reverbOn,
            reverbDecay = reverbDecay.toFloat().coerceIn(0f, 1f),
            reverbMix = reverbMix.toFloat().coerceIn(0f, 1f),
            toneMatched = toneMatched,
            toneMatchSummary = toneMatchSummary,
        )
    }

    fun toCreateMap(): Map<String, Any> {
        val raw = mutableMapOf<String, Any?>(
            "userId" to userId,
            "name" to name.trim().take(50),
            "category" to PresetCategory.fromId(category).id,
            "favorite" to favorite,
            "ampModelId" to AmpModel.fromId(ampModelId).id,
            "gain" to gain.coerceIn(0.0, 1.0),
            "bass" to bass.coerceIn(0.0, 1.0),
            "mid" to mid.coerceIn(0.0, 1.0),
            "treble" to treble.coerceIn(0.0, 1.0),
            "presence" to presence.coerceIn(0.0, 1.0),
            "depth" to depth.coerceIn(0.0, 1.0),
            "master" to master.coerceIn(0.0, 1.0),
            "cabModelId" to CabModel.fromId(cabModelId).id,
            "micPosition" to MicPosition.fromId(micPosition).id,
            "cabResonance" to cabResonance.coerceIn(0.0, 1.0),
            "gateOn" to gateOn,
            "gateThresholdDb" to gateThresholdDb.coerceIn(-80.0, -10.0),
            "odOn" to odOn,
            "odDrive" to odDrive.coerceIn(0.0, 1.0),
            "odTone" to odTone.coerceIn(0.0, 1.0),
            "odLevel" to odLevel.coerceIn(0.0, 1.0),
            "eqOn" to eqOn,
            "eqBands" to normalizedEqBands(eqBands),
            "chorusOn" to chorusOn,
            "chorusMix" to chorusMix.coerceIn(0.0, 1.0),
            "delayOn" to delayOn,
            "delayTimeMs" to delayTimeMs.coerceIn(40.0, 800.0),
            "delayFeedback" to delayFeedback.coerceIn(0.0, 0.85),
            "delayMix" to delayMix.coerceIn(0.0, 1.0),
            "reverbOn" to reverbOn,
            "reverbDecay" to reverbDecay.coerceIn(0.0, 1.0),
            "reverbMix" to reverbMix.coerceIn(0.0, 1.0),
            "toneMatched" to toneMatched,
            "toneMatchSummary" to toneMatchSummary?.take(200),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        @Suppress("UNCHECKED_CAST")
        return raw.filterValues { it != null } as Map<String, Any>
    }

    fun toUpdateMap(): Map<String, Any> {
        val raw = mutableMapOf<String, Any?>(
            "name" to name.trim().take(50),
            "category" to PresetCategory.fromId(category).id,
            "favorite" to favorite,
            "ampModelId" to AmpModel.fromId(ampModelId).id,
            "gain" to gain.coerceIn(0.0, 1.0),
            "bass" to bass.coerceIn(0.0, 1.0),
            "mid" to mid.coerceIn(0.0, 1.0),
            "treble" to treble.coerceIn(0.0, 1.0),
            "presence" to presence.coerceIn(0.0, 1.0),
            "depth" to depth.coerceIn(0.0, 1.0),
            "master" to master.coerceIn(0.0, 1.0),
            "cabModelId" to CabModel.fromId(cabModelId).id,
            "micPosition" to MicPosition.fromId(micPosition).id,
            "cabResonance" to cabResonance.coerceIn(0.0, 1.0),
            "gateOn" to gateOn,
            "gateThresholdDb" to gateThresholdDb.coerceIn(-80.0, -10.0),
            "odOn" to odOn,
            "odDrive" to odDrive.coerceIn(0.0, 1.0),
            "odTone" to odTone.coerceIn(0.0, 1.0),
            "odLevel" to odLevel.coerceIn(0.0, 1.0),
            "eqOn" to eqOn,
            "eqBands" to normalizedEqBands(eqBands),
            "chorusOn" to chorusOn,
            "chorusMix" to chorusMix.coerceIn(0.0, 1.0),
            "delayOn" to delayOn,
            "delayTimeMs" to delayTimeMs.coerceIn(40.0, 800.0),
            "delayFeedback" to delayFeedback.coerceIn(0.0, 0.85),
            "delayMix" to delayMix.coerceIn(0.0, 1.0),
            "reverbOn" to reverbOn,
            "reverbDecay" to reverbDecay.coerceIn(0.0, 1.0),
            "reverbMix" to reverbMix.coerceIn(0.0, 1.0),
            "toneMatched" to toneMatched,
            "toneMatchSummary" to toneMatchSummary?.take(200),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        @Suppress("UNCHECKED_CAST")
        return raw.filterValues { it != null } as Map<String, Any>
    }

    companion object {
        private fun normalizedEqBands(bands: List<Double>): List<Double> {
            val padded = List(7) { idx -> bands.getOrElse(idx) { 0.0 }.coerceIn(-12.0, 12.0) }
            return padded
        }

        fun fromRigConfig(
            userId: String,
            name: String,
            category: PresetCategory,
            favorite: Boolean,
            config: RigConfig,
            existingId: String = "",
        ): RigPresetDoc = RigPresetDoc(
            id = existingId,
            userId = userId,
            name = name.trim().take(50),
            category = category.id,
            favorite = favorite,
            ampModelId = config.ampModel.id,
            gain = config.gain.toDouble(),
            bass = config.bass.toDouble(),
            mid = config.mid.toDouble(),
            treble = config.treble.toDouble(),
            presence = config.presence.toDouble(),
            depth = config.depth.toDouble(),
            master = config.master.toDouble(),
            cabModelId = config.cabModel.id,
            micPosition = config.micPosition.id,
            cabResonance = config.cabResonance.toDouble(),
            gateOn = config.gateEnabled,
            gateThresholdDb = config.gateThresholdDb.toDouble(),
            odOn = config.odEnabled,
            odDrive = config.odDrive.toDouble(),
            odTone = config.odTone.toDouble(),
            odLevel = config.odLevel.toDouble(),
            eqOn = config.eqEnabled,
            eqBands = List(7) { idx -> config.eqBands.getOrElse(idx) { 0f }.toDouble().coerceIn(-12.0, 12.0) },
            chorusOn = config.chorusEnabled,
            chorusMix = config.chorusMix.toDouble(),
            delayOn = config.delayEnabled,
            delayTimeMs = config.delayTimeMs.toDouble(),
            delayFeedback = config.delayFeedback.toDouble(),
            delayMix = config.delayMix.toDouble(),
            reverbOn = config.reverbEnabled,
            reverbDecay = config.reverbDecay.toDouble(),
            reverbMix = config.reverbMix.toDouble(),
            toneMatched = config.toneMatched,
            toneMatchSummary = config.toneMatchSummary?.take(200),
        )
    }
}
