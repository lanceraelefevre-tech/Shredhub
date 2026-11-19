package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.audio.AudioTelemetry
import com.example.model.AmpModel
import com.example.model.CabModel
import com.example.model.MicPosition
import com.example.model.RigConfig
import com.example.ui.components.AmpKnob
import com.example.ui.components.LedIndicator
import com.example.ui.theme.BezelBorder
import com.example.ui.theme.ChassisSurface
import com.example.ui.theme.CrimsonLed
import com.example.ui.theme.ModuleElevated
import com.example.ui.theme.ModuleSurface
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TubeAmber
import com.example.ui.theme.TubeAmberGlow
import com.example.ui.theme.VoltCyan

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AmpCabScreen(
    config: RigConfig,
    telemetry: AudioTelemetry,
    onSelectAmp: (AmpModel) -> Unit,
    onUpdateRig: ((RigConfig) -> RigConfig) -> Unit,
    onSelectCab: (CabModel) -> Unit,
    onSelectMic: (MicPosition) -> Unit,
    onLoadWavIr: (Uri) -> Unit,
    modifier: Modifier = Modifier,
) {
    val wavPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onLoadWavIr(uri)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // 1. Amplifier Head Chassis
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = ChassisSurface,
            border = BorderStroke(1.dp, BezelBorder),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Image(
                    painter = painterResource(id = R.drawable.img_amp_chassis_1795045008116),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .matchParentSize()
                        .alpha(0.24f),
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    ChassisSurface.copy(alpha = 0.72f),
                                    ChassisSurface.copy(alpha = 0.94f),
                                )
                            )
                        )
                )

                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                LedIndicator(
                                    isActive = telemetry.engineRunning,
                                    activeColor = if (config.ampModel == AmpModel.JAZZ_CLEAN) VoltCyan else CrimsonLed,
                                    size = 11.dp,
                                )
                                Text(
                                    text = config.ampModel.displayName.uppercase(),
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = TubeAmberGlow,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Text(
                                text = "${config.ampModel.genreTag} • ${config.ampModel.subtitle}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                            )
                        }

                        if (config.toneMatched) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                border = BorderStroke(1.dp, VoltCyan),
                            ) {
                                Text(
                                    text = "MATCHED",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = VoltCyan,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }

                    // Live Output Spectrum Strip
                    LiveSpectrumStrip(
                        bands = telemetry.spectrumBands,
                        engineRunning = telemetry.engineRunning,
                    )

                    // Amp Topology Selector
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AmpModel.entries.forEach { amp ->
                            val selected = config.ampModel == amp
                            FilterChip(
                                selected = selected,
                                onClick = { onSelectAmp(amp) },
                                label = {
                                    Text(
                                        text = amp.displayName,
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = TubeAmber,
                                    selectedLabelColor = ObsidianBg,
                                    containerColor = ModuleSurface,
                                    labelColor = TextSecondary,
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = selected,
                                    borderColor = BezelBorder,
                                    selectedBorderColor = TubeAmberGlow,
                                ),
                                modifier = Modifier.testTag("amp_chip_${amp.id}"),
                            )
                        }
                    }

                    // Preamp & Power Amp Rotary Knobs
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        maxItemsInEachRow = 4,
                    ) {
                        AmpKnob(
                            label = "Gain",
                            value = config.gain,
                            onValueChange = { v -> onUpdateRig { it.copy(gain = v) } },
                            testTag = "knob_gain",
                            defaultValue = 0.78f,
                            accentColor = CrimsonLed,
                        )
                        AmpKnob(
                            label = "Bass",
                            value = config.bass,
                            onValueChange = { v -> onUpdateRig { it.copy(bass = v) } },
                            testTag = "knob_bass",
                            defaultValue = 0.50f,
                            accentColor = TubeAmber,
                        )
                        AmpKnob(
                            label = "Mid",
                            value = config.mid,
                            onValueChange = { v -> onUpdateRig { it.copy(mid = v) } },
                            testTag = "knob_mid",
                            defaultValue = 0.50f,
                            accentColor = TubeAmber,
                        )
                        AmpKnob(
                            label = "Treble",
                            value = config.treble,
                            onValueChange = { v -> onUpdateRig { it.copy(treble = v) } },
                            testTag = "knob_treble",
                            defaultValue = 0.65f,
                            accentColor = TubeAmber,
                        )
                        AmpKnob(
                            label = "Pres",
                            value = config.presence,
                            onValueChange = { v -> onUpdateRig { it.copy(presence = v) } },
                            testTag = "knob_presence",
                            defaultValue = 0.62f,
                            accentColor = TubeAmberGlow,
                        )
                        AmpKnob(
                            label = "Depth",
                            value = config.depth,
                            onValueChange = { v -> onUpdateRig { it.copy(depth = v) } },
                            testTag = "knob_depth",
                            defaultValue = 0.52f,
                            accentColor = TubeAmberGlow,
                        )
                        AmpKnob(
                            label = "Master",
                            value = config.master,
                            onValueChange = { v -> onUpdateRig { it.copy(master = v) } },
                            testTag = "knob_master",
                            defaultValue = 0.72f,
                            accentColor = VoltCyan,
                        )
                    }
                }
            }
        }

        // 2. Impulse Response Cabinet Simulator
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = ChassisSurface,
            border = BorderStroke(1.dp, BezelBorder),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Image(
                    painter = painterResource(id = R.drawable.img_cab_grill_1795045008922),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .matchParentSize()
                        .alpha(0.28f),
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    ChassisSurface.copy(alpha = 0.70f),
                                    ChassisSurface.copy(alpha = 0.92f),
                                )
                            )
                        )
                )

                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Speaker,
                                contentDescription = null,
                                tint = VoltCyan,
                            )
                            Column {
                                Text(
                                    text = "IR CABINET: ${config.cabModel.displayName.uppercase()}",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = if (config.cabModel == CabModel.CUSTOM_WAV_IR && config.customIrName != null) {
                                        "Loaded IR: ${config.customIrName}"
                                    } else {
                                        config.cabModel.speakerSpec
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                )
                            }
                        }
                    }

                    // Cabinet IR selector
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CabModel.entries.forEach { cab ->
                            val selected = config.cabModel == cab
                            FilterChip(
                                selected = selected,
                                onClick = { onSelectCab(cab) },
                                label = {
                                    Text(
                                        text = cab.displayName,
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = VoltCyan,
                                    selectedLabelColor = ObsidianBg,
                                    containerColor = ModuleSurface,
                                    labelColor = TextSecondary,
                                ),
                                modifier = Modifier.testTag("cab_chip_${cab.id}"),
                            )
                        }
                    }

                    // Mic Placement + Resonance + Custom WAV IR Load
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "MIC PLACEMENT",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted,
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                            ) {
                                MicPosition.entries.forEach { mic ->
                                    val selected = config.micPosition == mic
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (selected) ModuleElevated else ModuleSurface,
                                        border = BorderStroke(1.dp, if (selected) VoltCyan else BezelBorder),
                                        modifier = Modifier
                                            .height(48.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { onSelectMic(mic) }
                                            .testTag("mic_pos_${mic.id}"),
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.padding(horizontal = 10.dp),
                                        ) {
                                            Text(
                                                text = mic.displayName,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = if (selected) VoltCyan else TextSecondary,
                                            )
                                        }
                                    }
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    wavPicker.launch(arrayOf("audio/wav", "audio/x-wav", "audio/*"))
                                },
                                border = BorderStroke(1.dp, VoltCyan),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = VoltCyan),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("import_wav_ir_button"),
                            ) {
                                Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("LOAD .WAV IR", style = MaterialTheme.typography.labelLarge)
                            }
                        }

                        Spacer(Modifier.width(10.dp))

                        AmpKnob(
                            label = "Resonance",
                            value = config.cabResonance,
                            onValueChange = { v -> onUpdateRig { it.copy(cabResonance = v) } },
                            testTag = "knob_cab_resonance",
                            defaultValue = 0.50f,
                            accentColor = VoltCyan,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveSpectrumStrip(
    bands: List<Float>,
    engineRunning: Boolean,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = ObsidianBg.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, BezelBorder),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.GraphicEq,
                contentDescription = "Live Output Spectrum",
                tint = if (engineRunning) VoltCyan else TextMuted,
                modifier = Modifier.size(16.dp),
            )
            Canvas(modifier = Modifier.fillMaxSize()) {
                val count = bands.size.coerceAtLeast(1)
                val gap = 3.dp.toPx()
                val barW = (size.width - gap * (count - 1)) / count
                for (i in 0 until count) {
                    val v = if (engineRunning) bands[i].coerceIn(0.05f, 1f) else 0.06f
                    val h = size.height * v
                    val color = when {
                        v > 0.82f -> CrimsonLed
                        v > 0.55f -> TubeAmber
                        else -> VoltCyan
                    }
                    drawRect(
                        color = color.copy(alpha = if (engineRunning) 0.9f else 0.25f),
                        topLeft = Offset(i * (barW + gap), size.height - h),
                        size = Size(barW, h),
                    )
                }
            }
        }
    }
}
