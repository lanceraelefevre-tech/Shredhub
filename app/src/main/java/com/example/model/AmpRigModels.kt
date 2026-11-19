package com.example.model

enum class AmpModel(
    val id: String,
    val displayName: String,
    val subtitle: String,
    val genreTag: String,
    val baseDriveScale: Float,
    val asymmetryBias: Float,
    val preLowCutHz: Float,
    val midScoopBiasDb: Float,
    val sagAmount: Float,
) {
    PEV_6505(
        id = "PEV_6505",
        displayName = "PV-6505 Lead",
        subtitle = "Cascading 5-stage American high-gain aggression",
        genreTag = "Melodeath / Metalcore",
        baseDriveScale = 22f,
        asymmetryBias = 0.22f,
        preLowCutHz = 115f,
        midScoopBiasDb = 1.5f,
        sagAmount = 0.18f,
    ),
    RECTO_ULTRA(
        id = "RECTO_ULTRA",
        displayName = "Recto Dual Red",
        subtitle = "Huge 6L6 low-end thump & modern scooped sizzle",
        genreTag = "Nu-Metal / Thrash",
        baseDriveScale = 20f,
        asymmetryBias = 0.28f,
        preLowCutHz = 88f,
        midScoopBiasDb = -3.2f,
        sagAmount = 0.25f,
    ),
    DJENT_PRECISION(
        id = "DJENT_PRECISION",
        displayName = "Djent Precision",
        subtitle = "Surgical low-end gate & razor-tight pick attack",
        genreTag = "Modern Prog / Djent",
        baseDriveScale = 24f,
        asymmetryBias = 0.16f,
        preLowCutHz = 165f,
        midScoopBiasDb = 2.8f,
        sagAmount = 0.08f,
    ),
    BRIT_HOTROD(
        id = "BRIT_HOTROD",
        displayName = "Brit JCM Hot-Rod",
        subtitle = "Modded EL34 upper-mid bark & classic harmonic grind",
        genreTag = "Heavy Metal / Shred",
        baseDriveScale = 14f,
        asymmetryBias = 0.14f,
        preLowCutHz = 105f,
        midScoopBiasDb = 2.2f,
        sagAmount = 0.22f,
    ),
    DOOM_FUZZ(
        id = "DOOM_FUZZ",
        displayName = "Doom Fuzz Wall",
        subtitle = "Massive octave-saturated sub-bass sludge distortion",
        genreTag = "Doom / Sludge / Stoner",
        baseDriveScale = 28f,
        asymmetryBias = 0.42f,
        preLowCutHz = 62f,
        midScoopBiasDb = -1.5f,
        sagAmount = 0.35f,
    ),
    JAZZ_CLEAN(
        id = "JAZZ_CLEAN",
        displayName = "Studio Clean JC",
        subtitle = "Ultra-high headroom sparkling clean channel",
        genreTag = "Ambient / Clean",
        baseDriveScale = 2.2f,
        asymmetryBias = 0.02f,
        preLowCutHz = 75f,
        midScoopBiasDb = 0.0f,
        sagAmount = 0.04f,
    );

    companion object {
        fun fromId(id: String): AmpModel = entries.firstOrNull { it.id == id } ?: PEV_6505
    }
}

enum class CabModel(
    val id: String,
    val displayName: String,
    val speakerSpec: String,
    val lowResHz: Float,
    val highRollOffHz: Float,
    val midFocusHz: Float,
    val midFocusDb: Float,
) {
    V30_4X12_TIGHT(
        id = "V30_4X12_TIGHT",
        displayName = "4x12 V30 Tight",
        speakerSpec = "Celestion Vintage 30 • Straight Cab",
        lowResHz = 108f,
        highRollOffHz = 4900f,
        midFocusHz = 2150f,
        midFocusDb = 4.2f,
    ),
    RECTO_4X12_OVERSIZED(
        id = "RECTO_4X12_OVERSIZED",
        displayName = "4x12 Oversized",
        speakerSpec = "Deep Birch Enclosure • Slant Top",
        lowResHz = 88f,
        highRollOffHz = 5300f,
        midFocusHz = 2600f,
        midFocusDb = 3.5f,
    ),
    GREENBACK_4X12(
        id = "GREENBACK_4X12",
        displayName = "4x12 Greenback",
        speakerSpec = "G12M-25 • Warm British Cone Breakup",
        lowResHz = 102f,
        highRollOffHz = 4300f,
        midFocusHz = 1450f,
        midFocusDb = 3.0f,
    ),
    DARK_METAL_4X12(
        id = "DARK_METAL_4X12",
        displayName = "4x12 Doom Stack",
        speakerSpec = "K100 High-Wattage • Sub-Heavy",
        lowResHz = 76f,
        highRollOffHz = 4100f,
        midFocusHz = 1750f,
        midFocusDb = 2.0f,
    ),
    OPEN_2X12_CLEAN(
        id = "OPEN_2X12_CLEAN",
        displayName = "2x12 Open Back",
        speakerSpec = "Jensen Alnico • Wide Clean Shimmer",
        lowResHz = 98f,
        highRollOffHz = 6200f,
        midFocusHz = 2900f,
        midFocusDb = 1.8f,
    ),
    CUSTOM_MATCH_IR(
        id = "CUSTOM_MATCH_IR",
        displayName = "Matched Tone IR",
        speakerSpec = "Synthesized from Uploaded Audio Sample",
        lowResHz = 95f,
        highRollOffHz = 5200f,
        midFocusHz = 2200f,
        midFocusDb = 3.2f,
    ),
    CUSTOM_WAV_IR(
        id = "CUSTOM_WAV_IR",
        displayName = "Custom .WAV IR",
        speakerSpec = "User Loaded Impulse Response File",
        lowResHz = 95f,
        highRollOffHz = 5500f,
        midFocusHz = 2000f,
        midFocusDb = 2.0f,
    );

    companion object {
        fun fromId(id: String): CabModel = entries.firstOrNull { it.id == id } ?: V30_4X12_TIGHT
    }
}

enum class MicPosition(
    val id: String,
    val displayName: String,
    val brightnessOffsetDb: Float,
    val proximityBassDb: Float,
) {
    CAP_CENTER("CAP_CENTER", "Cap Center", 3.5f, 1.5f),
    CAP_EDGE("CAP_EDGE", "Cap Edge", 0.5f, 2.5f),
    OFF_AXIS("OFF_AXIS", "Off-Axis 45°", -3.2f, 3.0f);

    companion object {
        fun fromId(id: String): MicPosition = entries.firstOrNull { it.id == id } ?: CAP_EDGE
    }
}

enum class PresetCategory(val id: String, val label: String) {
    MODERN_METAL("MODERN_METAL", "Modern Metal"),
    THRASH("THRASH", "Thrash"),
    DEATH_DOOM("DEATH_DOOM", "Death / Doom"),
    LEAD_SHRED("LEAD_SHRED", "Lead / Shred"),
    CRUNCH_ROCK("CRUNCH_ROCK", "Crunch / Rock"),
    CLEAN_AMBIENT("CLEAN_AMBIENT", "Clean / Ambient"),
    TONE_MATCHED("TONE_MATCHED", "Tone Matched"),
    CUSTOM("CUSTOM", "Custom");

    companion object {
        fun fromId(id: String): PresetCategory = entries.firstOrNull { it.id == id } ?: CUSTOM
    }
}

data class RigConfig(
    val ampModel: AmpModel = AmpModel.PEV_6505,
    val gain: Float = 0.78f,
    val bass: Float = 0.48f,
    val mid: Float = 0.54f,
    val treble: Float = 0.68f,
    val presence: Float = 0.66f,
    val depth: Float = 0.52f,
    val master: Float = 0.72f,
    val cabModel: CabModel = CabModel.V30_4X12_TIGHT,
    val micPosition: MicPosition = MicPosition.CAP_EDGE,
    val cabResonance: Float = 0.50f,
    val gateEnabled: Boolean = true,
    val gateThresholdDb: Float = -42f,
    val odEnabled: Boolean = true,
    val odDrive: Float = 0.14f,
    val odTone: Float = 0.72f,
    val odLevel: Float = 0.85f,
    val eqEnabled: Boolean = true,
    val eqBands: List<Float> = listOf(-1.5f, -1.0f, 0.5f, 2.5f, 1.5f, 2.0f, -0.5f),
    val chorusEnabled: Boolean = false,
    val chorusMix: Float = 0.25f,
    val delayEnabled: Boolean = false,
    val delayTimeMs: Float = 360f,
    val delayFeedback: Float = 0.28f,
    val delayMix: Float = 0.18f,
    val reverbEnabled: Boolean = true,
    val reverbDecay: Float = 0.26f,
    val reverbMix: Float = 0.14f,
    val toneMatched: Boolean = false,
    val toneMatchSummary: String? = null,
    val customIrName: String? = null,
) {
    companion object {
        val EQ_FREQUENCIES = listOf("80", "160", "400", "800", "1.6k", "3.2k", "6.4k")
    }
}
