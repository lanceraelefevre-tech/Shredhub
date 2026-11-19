package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.audio.AudioTelemetry
import com.example.model.RigConfig
import com.example.ui.components.AmpKnob
import com.example.ui.components.LedIndicator
import com.example.ui.theme.BezelBorder
import com.example.ui.theme.ChassisSurface
import com.example.ui.theme.CrimsonLed
import com.example.ui.theme.ModuleElevated
import com.example.ui.theme.ModuleSurface
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SteelBlue
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TubeAmber
import com.example.ui.theme.TubeAmberGlow
import com.example.ui.theme.VoltCyan
import kotlin.math.roundToInt

@Composable
fun PedalboardScreen(
    config: RigConfig,
    telemetry: AudioTelemetry,
    onUpdateRig: ((RigConfig) -> RigConfig) -> Unit,
    onUpdateEqBand: (Int, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Signal Flow Bar
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = ChassisSurface,
            border = BorderStroke(1.dp, BezelBorder),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ChainPill("GATE", config.gateEnabled, VoltCyan)
                ChainArrow()
                ChainPill("OD BOOST", config.odEnabled, TubeAmber)
                ChainArrow()
                ChainPill("AMP", true, CrimsonLed)
                ChainArrow()
                ChainPill("7-EQ", config.eqEnabled, TubeAmberGlow)
                ChainArrow()
                ChainPill("CAB IR", true, VoltCyan)
                ChainArrow()
                ChainPill("CHORUS", config.chorusEnabled, SteelBlue)
                ChainArrow()
                ChainPill("DELAY", config.delayEnabled, SteelBlue)
                ChainArrow()
                ChainPill("REVERB", config.reverbEnabled, SteelBlue)
            }
        }

        // Row 1: Noise Gate + Tube Overdrive
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StompBoxCard(
                title = "NOISE GATE",
                subtitle = if (telemetry.isGateOpen) "OPEN" else "GATED",
                enabled = config.gateEnabled,
                accentColor = VoltCyan,
                onToggle = { onUpdateRig { it.copy(gateEnabled = !it.gateEnabled) } },
                testTag = "stomp_gate",
                modifier = Modifier.weight(1f),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    AmpKnob(
                        label = "Thresh",
                        value = ((config.gateThresholdDb + 80f) / 70f).coerceIn(0f, 1f),
                        onValueChange = { v ->
                            onUpdateRig { it.copy(gateThresholdDb = (-80f + v * 70f).coerceIn(-80f, -10f)) }
                        },
                        testTag = "knob_gate_thresh",
                        defaultValue = 0.54f,
                        accentColor = VoltCyan,
                        valueFormatter = { v -> "${(-80f + v * 70f).roundToInt()}dB" },
                    )
                }
            }

            StompBoxCard(
                title = "TS OVERDRIVE",
                subtitle = "800Hz Tight Push",
                enabled = config.odEnabled,
                accentColor = TubeAmber,
                onToggle = { onUpdateRig { it.copy(odEnabled = !it.odEnabled) } },
                testTag = "stomp_od",
                modifier = Modifier.weight(1.35f),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    AmpKnob(
                        label = "Drive",
                        value = config.odDrive,
                        onValueChange = { v -> onUpdateRig { it.copy(odDrive = v) } },
                        testTag = "knob_od_drive",
                        defaultValue = 0.14f,
                        accentColor = TubeAmber,
                        knobSize = 48.dp,
                    )
                    AmpKnob(
                        label = "Tone",
                        value = config.odTone,
                        onValueChange = { v -> onUpdateRig { it.copy(odTone = v) } },
                        testTag = "knob_od_tone",
                        defaultValue = 0.72f,
                        accentColor = TubeAmber,
                        knobSize = 48.dp,
                    )
                    AmpKnob(
                        label = "Level",
                        value = config.odLevel,
                        onValueChange = { v -> onUpdateRig { it.copy(odLevel = v) } },
                        testTag = "knob_od_level",
                        defaultValue = 0.85f,
                        accentColor = TubeAmber,
                        knobSize = 48.dp,
                    )
                }
            }
        }

        // 7-Band Graphic EQ Rack
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = ChassisSurface,
            border = BorderStroke(1.dp, if (config.eqEnabled) TubeAmberGlow else BezelBorder),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        LedIndicator(isActive = config.eqEnabled, activeColor = TubeAmberGlow)
                        Text(
                            text = "7-BAND STUDIO GRAPHIC EQ",
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(
                            onClick = {
                                onUpdateRig { it.copy(eqBands = List(7) { 0f }) }
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = TextSecondary),
                            modifier = Modifier.testTag("eq_flat_button"),
                        ) {
                            Text("FLAT", style = MaterialTheme.typography.labelMedium)
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (config.eqEnabled) TubeAmber else ModuleElevated,
                            modifier = Modifier
                                .height(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onUpdateRig { it.copy(eqEnabled = !it.eqEnabled) } }
                                .testTag("stomp_eq"),
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 14.dp),
                            ) {
                                Text(
                                    text = if (config.eqEnabled) "ON" else "BYPASS",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (config.eqEnabled) ObsidianBg else TextSecondary,
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    RigConfig.EQ_FREQUENCIES.forEachIndexed { idx, freqLabel ->
                        val bandDb = config.eqBands.getOrElse(idx) { 0f }
                        EqVerticalSlider(
                            label = freqLabel,
                            dbValue = bandDb,
                            enabled = config.eqEnabled,
                            onValueChange = { onUpdateEqBand(idx, it) },
                            testTag = "eq_slider_$idx",
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        // Row 2: Mod Chorus, Echo Delay, Plate Reverb
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StompBoxCard(
                title = "CHORUS",
                subtitle = "Analog Dual",
                enabled = config.chorusEnabled,
                accentColor = SteelBlue,
                onToggle = { onUpdateRig { it.copy(chorusEnabled = !it.chorusEnabled) } },
                testTag = "stomp_chorus",
                modifier = Modifier.weight(0.9f),
            ) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    AmpKnob(
                        label = "Mix",
                        value = config.chorusMix,
                        onValueChange = { v -> onUpdateRig { it.copy(chorusMix = v) } },
                        testTag = "knob_chorus_mix",
                        defaultValue = 0.25f,
                        accentColor = SteelBlue,
                        knobSize = 50.dp,
                    )
                }
            }

            StompBoxCard(
                title = "PLATE REVERB",
                subtitle = "Studio Tail",
                enabled = config.reverbEnabled,
                accentColor = SteelBlue,
                onToggle = { onUpdateRig { it.copy(reverbEnabled = !it.reverbEnabled) } },
                testTag = "stomp_reverb",
                modifier = Modifier.weight(1.1f),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    AmpKnob(
                        label = "Decay",
                        value = config.reverbDecay,
                        onValueChange = { v -> onUpdateRig { it.copy(reverbDecay = v) } },
                        testTag = "knob_reverb_decay",
                        defaultValue = 0.26f,
                        accentColor = SteelBlue,
                        knobSize = 48.dp,
                    )
                    AmpKnob(
                        label = "Mix",
                        value = config.reverbMix,
                        onValueChange = { v -> onUpdateRig { it.copy(reverbMix = v) } },
                        testTag = "knob_reverb_mix",
                        defaultValue = 0.14f,
                        accentColor = SteelBlue,
                        knobSize = 48.dp,
                    )
                }
            }
        }

        // Echo Delay Pedal
        StompBoxCard(
            title = "ECHO DELAY",
            subtitle = "Warm Tape Feedback",
            enabled = config.delayEnabled,
            accentColor = SteelBlue,
            onToggle = { onUpdateRig { it.copy(delayEnabled = !it.delayEnabled) } },
            testTag = "stomp_delay",
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                AmpKnob(
                    label = "Time",
                    value = ((config.delayTimeMs - 40f) / 760f).coerceIn(0f, 1f),
                    onValueChange = { v ->
                        onUpdateRig { it.copy(delayTimeMs = (40f + v * 760f).coerceIn(40f, 800f)) }
                    },
                    testTag = "knob_delay_time",
                    defaultValue = 0.42f,
                    accentColor = SteelBlue,
                    valueFormatter = { v -> "${(40f + v * 760f).roundToInt()}ms" },
                )
                AmpKnob(
                    label = "Feedbk",
                    value = (config.delayFeedback / 0.85f).coerceIn(0f, 1f),
                    onValueChange = { v ->
                        onUpdateRig { it.copy(delayFeedback = (v * 0.85f).coerceIn(0f, 0.85f)) }
                    },
                    testTag = "knob_delay_feedback",
                    defaultValue = 0.33f,
                    accentColor = SteelBlue,
                )
                AmpKnob(
                    label = "Mix",
                    value = config.delayMix,
                    onValueChange = { v -> onUpdateRig { it.copy(delayMix = v) } },
                    testTag = "knob_delay_mix",
                    defaultValue = 0.18f,
                    accentColor = SteelBlue,
                )
            }
        }
    }
}

@Composable
private fun StompBoxCard(
    title: String,
    subtitle: String,
    enabled: Boolean,
    accentColor: Color,
    onToggle: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
    controls: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = ModuleSurface,
        border = BorderStroke(1.5.dp, if (enabled) accentColor else BezelBorder),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (enabled) accentColor else TextMuted,
                    )
                }
                LedIndicator(isActive = enabled, activeColor = accentColor, size = 11.dp)
            }

            controls()

            // Tactile Stomp Footswitch Pad
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (enabled) accentColor.copy(alpha = 0.22f) else ModuleElevated,
                border = BorderStroke(1.dp, if (enabled) accentColor else BezelBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onToggle)
                    .testTag(testTag),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = if (enabled) "ACTIVE" else "BYPASS",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (enabled) accentColor else TextSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun EqVerticalSlider(
    label: String,
    dbValue: Float,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    val currentDb by rememberUpdatedState(dbValue.coerceIn(-12f, 12f))
    val callback by rememberUpdatedState(onValueChange)
    val norm = ((currentDb + 12f) / 24f).coerceIn(0f, 1f)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(horizontal = 2.dp),
    ) {
        Text(
            text = "${if (currentDb > 0.4f) "+" else ""}${currentDb.roundToInt()}",
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) TubeAmberGlow else TextMuted,
        )
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .width(42.dp)
                .height(126.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(ObsidianBg)
                .pointerInput(Unit) {
                    detectVerticalDragGestures { change, dragAmount ->
                        change.consume()
                        val deltaDb = -dragAmount / 5.0f
                        callback((currentDb + deltaDb).coerceIn(-12f, 12f))
                    }
                }
                .testTag(testTag),
            contentAlignment = Alignment.BottomCenter,
        ) {
            // Center 0 dB hairline
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(BezelBorder)
            )
            // Active level bar
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.55f)
                    .fillMaxHeight(norm.coerceAtLeast(0.04f))
                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                    .background(if (enabled) TubeAmber else TextMuted)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
        )
    }
}

@Composable
private fun ChainPill(label: String, active: Boolean, color: Color) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (active) color.copy(alpha = 0.2f) else ModuleSurface,
        border = BorderStroke(1.dp, if (active) color else BezelBorder),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) color else TextMuted,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun ChainArrow() {
    Text("->", style = MaterialTheme.typography.labelSmall, color = TextMuted)
}
