package com.example.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.example.viewmodel.StudioTab
import kotlin.math.roundToInt

enum class SignalNodeId(
    val title: String,
    val stageRole: String,
) {
    INPUT("Input", "Audio Interface / DI"),
    GATE("Gate", "Pre-Gain Noise Gate"),
    OVERDRIVE("TS OD", "800Hz Tube Boost"),
    AMP("Amp Head", "Tube Preamp & Tone Stack"),
    EQ("7-Band EQ", "Post-Amp Graphic EQ"),
    CABINET("Cab IR", "Impulse Response Cabinet"),
    CHORUS("Chorus", "Modulation Rack"),
    DELAY("Delay", "Echo Time Feedback"),
    REVERB("Reverb", "Studio Plate Tail"),
    OUTPUT("Output", "Master & Limiter"),
}

private data class SignalNodeSpec(
    val id: SignalNodeId,
    val shortLabel: String,
    val subReadout: String,
    val icon: ImageVector,
    val active: Boolean,
    val accentColor: Color,
)

@Composable
fun SignalChainScreen(
    config: RigConfig,
    telemetry: AudioTelemetry,
    onUpdateRig: ((RigConfig) -> RigConfig) -> Unit,
    onNavigateTab: (StudioTab) -> Unit,
    onOpenAudioIo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedNode by remember { mutableStateOf(SignalNodeId.AMP) }

    val inputIcon = when {
        telemetry.testRiffMode -> Icons.Default.GraphicEq
        telemetry.usbConnected -> Icons.Default.Usb
        else -> Icons.Default.Mic
    }
    val inputLabel = when {
        telemetry.testRiffMode -> "DI Riff"
        telemetry.usbConnected -> "USB In"
        else -> "Live Mic"
    }

    val row1Nodes = listOf(
        SignalNodeSpec(
            id = SignalNodeId.INPUT,
            shortLabel = inputLabel,
            subReadout = "${telemetry.inputPeakDb.roundToInt()}dB",
            icon = inputIcon,
            active = telemetry.engineRunning,
            accentColor = VoltCyan,
        ),
        SignalNodeSpec(
            id = SignalNodeId.GATE,
            shortLabel = "Gate",
            subReadout = if (config.gateEnabled) "${config.gateThresholdDb.roundToInt()}dB" else "BYP",
            icon = Icons.Default.Shield,
            active = config.gateEnabled,
            accentColor = VoltCyan,
        ),
        SignalNodeSpec(
            id = SignalNodeId.OVERDRIVE,
            shortLabel = "Overdrive",
            subReadout = if (config.odEnabled) "D:${(config.odDrive * 10).roundToInt()}" else "BYP",
            icon = Icons.Default.ElectricBolt,
            active = config.odEnabled,
            accentColor = TubeAmber,
        ),
        SignalNodeSpec(
            id = SignalNodeId.AMP,
            shortLabel = config.ampModel.displayName.substringBefore(" "),
            subReadout = "G:${(config.gain * 10).roundToInt()}",
            icon = Icons.Default.Whatshot,
            active = true,
            accentColor = CrimsonLed,
        ),
        SignalNodeSpec(
            id = SignalNodeId.EQ,
            shortLabel = "7-EQ",
            subReadout = if (config.eqEnabled) "ON" else "BYP",
            icon = Icons.Default.Equalizer,
            active = config.eqEnabled,
            accentColor = TubeAmberGlow,
        ),
    )

    val row2Nodes = listOf(
        SignalNodeSpec(
            id = SignalNodeId.CABINET,
            shortLabel = config.cabModel.displayName.substringBefore(" "),
            subReadout = config.micPosition.displayName.substringBefore(" "),
            icon = Icons.Default.Speaker,
            active = true,
            accentColor = VoltCyan,
        ),
        SignalNodeSpec(
            id = SignalNodeId.CHORUS,
            shortLabel = "Chorus",
            subReadout = if (config.chorusEnabled) "${(config.chorusMix * 100).roundToInt()}%" else "BYP",
            icon = Icons.Default.Waves,
            active = config.chorusEnabled,
            accentColor = SteelBlue,
        ),
        SignalNodeSpec(
            id = SignalNodeId.DELAY,
            shortLabel = "Delay",
            subReadout = if (config.delayEnabled) "${config.delayTimeMs.roundToInt()}ms" else "BYP",
            icon = Icons.Default.Replay,
            active = config.delayEnabled,
            accentColor = SteelBlue,
        ),
        SignalNodeSpec(
            id = SignalNodeId.REVERB,
            shortLabel = "Reverb",
            subReadout = if (config.reverbEnabled) "${(config.reverbMix * 100).roundToInt()}%" else "BYP",
            icon = Icons.Default.BlurOn,
            active = config.reverbEnabled,
            accentColor = SteelBlue,
        ),
        SignalNodeSpec(
            id = SignalNodeId.OUTPUT,
            shortLabel = "Output",
            subReadout = if (telemetry.isClipping) "CLIP" else "${telemetry.outputPeakDb.roundToInt()}dB",
            icon = Icons.AutoMirrored.Filled.VolumeUp,
            active = telemetry.engineRunning,
            accentColor = if (telemetry.isClipping) CrimsonLed else VoltCyan,
        ),
    )

    val activeStageCount = (row1Nodes + row2Nodes).count { it.active }

    val pulseTransition = rememberInfiniteTransition(label = "signal_pulse")
    val pulsePhase by pulseTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulse_phase",
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // 1. Visual Signal Flow Patchbay Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = ChassisSurface,
            border = BorderStroke(1.dp, BezelBorder),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("signal_chain_dashboard_card"),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "SIGNAL CHAIN FLOW",
                            style = MaterialTheme.typography.titleLarge,
                            color = TubeAmberGlow,
                        )
                        Text(
                            text = "$activeStageCount/10 stages active • Tap any block to inspect or toggle",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (telemetry.engineRunning) MaterialTheme.colorScheme.secondaryContainer else ModuleSurface,
                        border = BorderStroke(1.dp, if (telemetry.engineRunning) VoltCyan else BezelBorder),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        ) {
                            LedIndicator(
                                isActive = telemetry.engineRunning,
                                activeColor = VoltCyan,
                                size = 8.dp,
                            )
                            Text(
                                text = if (telemetry.engineRunning) "SIGNAL LIVE" else "STANDBY",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (telemetry.engineRunning) VoltCyan else TextMuted,
                            )
                        }
                    }
                }

                var row1HeightPx by remember { mutableIntStateOf(0) }

                // Patchbay Matrix with Canvas Connection Lines + Two Serpentine Node Rows
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(ObsidianBg)
                        .border(1.dp, BezelBorder, RoundedCornerShape(12.dp))
                        .padding(horizontal = 6.dp, vertical = 14.dp),
                ) {
                    // Connection Lines Layer
                    Canvas(
                        modifier = Modifier
                            .matchParentSize()
                    ) {
                        val cols = 5
                        val colStep = size.width / cols
                        // Node icon boxes are 48.dp tall starting at 2.dp top padding -> center is 26.dp
                        val boxCenterOffsetPx = 26.dp.toPx()
                        val rowSpacingPx = 34.dp.toPx()
                        val row1Y = boxCenterOffsetPx
                        val row2Y = if (row1HeightPx > 0) {
                            row1HeightPx + rowSpacingPx + boxCenterOffsetPx
                        } else {
                            size.height - 62.dp.toPx()
                        }

                        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)

                        fun drawCableSegment(
                            start: Offset,
                            end: Offset,
                            color: Color,
                            isActive: Boolean,
                            pulseOffset: Float,
                        ) {
                            drawLine(
                                color = if (isActive) color.copy(alpha = 0.85f) else BezelBorder,
                                start = start,
                                end = end,
                                strokeWidth = if (isActive) 2.6.dp.toPx() else 1.6.dp.toPx(),
                                cap = StrokeCap.Round,
                                pathEffect = if (isActive) null else dashEffect,
                            )
                            // Arrowhead pointing right at midpoint
                            val midX = (start.x + end.x) * 0.5f
                            val midY = (start.y + end.y) * 0.5f
                            val arrowSz = 4.dp.toPx()
                            val arrowColor = if (isActive) color else TextMuted
                            val arrowPath = Path().apply {
                                moveTo(midX + arrowSz, midY)
                                lineTo(midX - arrowSz * 0.6f, midY - arrowSz * 0.7f)
                                lineTo(midX - arrowSz * 0.6f, midY + arrowSz * 0.7f)
                                close()
                            }
                            drawPath(arrowPath, color = arrowColor)

                            // Animated signal pulse dot along active segments
                            if (telemetry.engineRunning && isActive) {
                                val t = (pulsePhase + pulseOffset) % 1f
                                val px = start.x + (end.x - start.x) * t
                                val py = start.y + (end.y - start.y) * t
                                drawCircle(
                                    color = color,
                                    radius = 3.2.dp.toPx(),
                                    center = Offset(px, py),
                                )
                            }
                        }

                        // Row 1 horizontal connections (0->1, 1->2, 2->3, 3->4)
                        val row1Colors = listOf(VoltCyan, TubeAmber, CrimsonLed, TubeAmberGlow)
                        for (c in 0 until cols - 1) {
                            val x1 = (c + 0.5f) * colStep
                            val x2 = (c + 1.5f) * colStep
                            val segActive = row1Nodes[c].active || row1Nodes[c + 1].active
                            drawCableSegment(
                                start = Offset(x1, row1Y),
                                end = Offset(x2, row1Y),
                                color = row1Colors[c],
                                isActive = segActive,
                                pulseOffset = c * 0.12f,
                            )
                        }

                        // Serpentine Bus Connection: End of Row 1 (col 4) -> Start of Row 2 (col 0)
                        val r1EndX = 4.5f * colStep
                        val r2StartX = 0.5f * colStep
                        val midDropY = (row1Y + row2Y) * 0.5f
                        val busPath = Path().apply {
                            moveTo(r1EndX, row1Y)
                            lineTo(r1EndX, midDropY)
                            lineTo(r2StartX, midDropY)
                            lineTo(r2StartX, row2Y)
                        }
                        drawPath(
                            path = busPath,
                            color = VoltCyan.copy(alpha = 0.75f),
                            style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round),
                        )
                        // Downward/leftward bus indicator arrow at center of bridge
                        val bridgeMidX = (r1EndX + r2StartX) * 0.5f
                        val bridgeArrow = Path().apply {
                            moveTo(bridgeMidX - 5.dp.toPx(), midDropY)
                            lineTo(bridgeMidX + 4.dp.toPx(), midDropY - 3.8.dp.toPx())
                            lineTo(bridgeMidX + 4.dp.toPx(), midDropY + 3.8.dp.toPx())
                            close()
                        }
                        drawPath(bridgeArrow, color = VoltCyan)

                        if (telemetry.engineRunning) {
                            val px = r1EndX + (r2StartX - r1EndX) * pulsePhase
                            drawCircle(
                                color = VoltCyan,
                                radius = 3.5.dp.toPx(),
                                center = Offset(px, midDropY),
                            )
                        }

                        // Row 2 horizontal connections (0->1, 1->2, 2->3, 3->4)
                        val row2Colors = listOf(VoltCyan, SteelBlue, SteelBlue, VoltCyan)
                        for (c in 0 until cols - 1) {
                            val x1 = (c + 0.5f) * colStep
                            val x2 = (c + 1.5f) * colStep
                            val segActive = row2Nodes[c].active || row2Nodes[c + 1].active
                            drawCableSegment(
                                start = Offset(x1, row2Y),
                                end = Offset(x2, row2Y),
                                color = row2Colors[c],
                                isActive = segActive,
                                pulseOffset = 0.5f + c * 0.12f,
                            )
                        }
                    }

                    // Nodes Layer
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(34.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onSizeChanged { row1HeightPx = it.height },
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            row1Nodes.forEach { node ->
                                SignalChainNodeTile(
                                    spec = node,
                                    selected = selectedNode == node.id,
                                    onClick = { selectedNode = node.id },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            row2Nodes.forEach { node ->
                                SignalChainNodeTile(
                                    spec = node,
                                    selected = selectedNode == node.id,
                                    onClick = { selectedNode = node.id },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }

                // Signal Legend
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    SignalLegendDot(TubeAmber, "Drive / EQ")
                    SignalLegendDot(CrimsonLed, "Tube Amp")
                    SignalLegendDot(VoltCyan, "Cab / I/O")
                    SignalLegendDot(SteelBlue, "Post-FX")
                }
            }
        }

        // 2. Selected Stage Inspector & Quick Control Card
        SignalNodeInspectorCard(
            selectedNode = selectedNode,
            config = config,
            telemetry = telemetry,
            onUpdateRig = onUpdateRig,
            onNavigateTab = onNavigateTab,
            onOpenAudioIo = onOpenAudioIo,
        )
    }
}

@Composable
private fun SignalChainNodeTile(
    spec: SignalNodeSpec,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = when {
        selected -> MaterialTheme.colorScheme.onSurface
        spec.active -> spec.accentColor
        else -> BezelBorder
    }
    val boxColor = when {
        selected -> ModuleElevated
        spec.active -> ModuleSurface
        else -> ChassisSurface
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp)
            .testTag("signal_node_${spec.id.name.lowercase()}"),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(boxColor)
                .border(
                    width = if (selected) 2.dp else 1.2.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(12.dp),
                ),
        ) {
            Icon(
                imageVector = spec.icon,
                contentDescription = spec.shortLabel,
                tint = if (spec.active) spec.accentColor else TextMuted,
                modifier = Modifier.size(22.dp),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
            ) {
                LedIndicator(
                    isActive = spec.active,
                    activeColor = spec.accentColor,
                    size = 6.dp,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = spec.shortLabel,
            style = MaterialTheme.typography.labelSmall,
            color = if (spec.active) MaterialTheme.colorScheme.onSurface else TextMuted,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            text = spec.subReadout,
            style = MaterialTheme.typography.labelSmall,
            color = if (spec.active) spec.accentColor else TextMuted,
            maxLines = 1,
        )
    }
}

@Composable
private fun SignalNodeInspectorCard(
    selectedNode: SignalNodeId,
    config: RigConfig,
    telemetry: AudioTelemetry,
    onUpdateRig: ((RigConfig) -> RigConfig) -> Unit,
    onNavigateTab: (StudioTab) -> Unit,
    onOpenAudioIo: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = ModuleSurface,
        border = BorderStroke(1.dp, BezelBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("signal_node_inspector"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = selectedNode.title.uppercase(),
                        style = MaterialTheme.typography.titleLarge,
                        color = TubeAmberGlow,
                    )
                    Text(
                        text = selectedNode.stageRole,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }

                when (selectedNode) {
                    SignalNodeId.GATE -> Switch(
                        checked = config.gateEnabled,
                        onCheckedChange = { on -> onUpdateRig { it.copy(gateEnabled = on) } },
                        colors = SwitchDefaults.colors(checkedTrackColor = VoltCyan),
                        modifier = Modifier.testTag("inspector_toggle_gate"),
                    )
                    SignalNodeId.OVERDRIVE -> Switch(
                        checked = config.odEnabled,
                        onCheckedChange = { on -> onUpdateRig { it.copy(odEnabled = on) } },
                        colors = SwitchDefaults.colors(checkedTrackColor = TubeAmber),
                        modifier = Modifier.testTag("inspector_toggle_od"),
                    )
                    SignalNodeId.EQ -> Switch(
                        checked = config.eqEnabled,
                        onCheckedChange = { on -> onUpdateRig { it.copy(eqEnabled = on) } },
                        colors = SwitchDefaults.colors(checkedTrackColor = TubeAmberGlow),
                        modifier = Modifier.testTag("inspector_toggle_eq"),
                    )
                    SignalNodeId.CHORUS -> Switch(
                        checked = config.chorusEnabled,
                        onCheckedChange = { on -> onUpdateRig { it.copy(chorusEnabled = on) } },
                        colors = SwitchDefaults.colors(checkedTrackColor = SteelBlue),
                        modifier = Modifier.testTag("inspector_toggle_chorus"),
                    )
                    SignalNodeId.DELAY -> Switch(
                        checked = config.delayEnabled,
                        onCheckedChange = { on -> onUpdateRig { it.copy(delayEnabled = on) } },
                        colors = SwitchDefaults.colors(checkedTrackColor = SteelBlue),
                        modifier = Modifier.testTag("inspector_toggle_delay"),
                    )
                    SignalNodeId.REVERB -> Switch(
                        checked = config.reverbEnabled,
                        onCheckedChange = { on -> onUpdateRig { it.copy(reverbEnabled = on) } },
                        colors = SwitchDefaults.colors(checkedTrackColor = SteelBlue),
                        modifier = Modifier.testTag("inspector_toggle_reverb"),
                    )
                    else -> Unit
                }
            }

            // Stage-specific quick controls
            when (selectedNode) {
                SignalNodeId.INPUT -> {
                    Text(
                        text = if (telemetry.testRiffMode) {
                            "Source: Built-in Drop-C DI Metal Riff Loop • Input Peak ${telemetry.inputPeakDb.roundToInt()} dBFS"
                        } else if (telemetry.usbConnected) {
                            "Source: ${telemetry.activeUsbName ?: "USB Audio Interface"} • ${telemetry.sampleRate} Hz"
                        } else {
                            "Source: Live Device Microphone • Input Peak ${telemetry.inputPeakDb.roundToInt()} dBFS"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(
                        onClick = onOpenAudioIo,
                        border = BorderStroke(1.dp, VoltCyan),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = VoltCyan),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("inspector_open_audio_io"),
                    ) {
                        Text("CONFIGURE USB INTERFACE & LATENCY")
                    }
                }

                SignalNodeId.GATE -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AmpKnob(
                            label = "Threshold",
                            value = ((config.gateThresholdDb + 80f) / 70f).coerceIn(0f, 1f),
                            onValueChange = { v ->
                                onUpdateRig { it.copy(gateThresholdDb = (-80f + v * 70f).coerceIn(-80f, -10f)) }
                            },
                            testTag = "inspector_knob_gate",
                            accentColor = VoltCyan,
                            valueFormatter = { v -> "${(-80f + v * 70f).roundToInt()}dB" },
                        )
                        OpenEditorButton("OPEN PEDALBOARD") { onNavigateTab(StudioTab.PEDALBOARD) }
                    }
                }

                SignalNodeId.OVERDRIVE -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AmpKnob(
                            label = "Drive",
                            value = config.odDrive,
                            onValueChange = { v -> onUpdateRig { it.copy(odDrive = v) } },
                            testTag = "inspector_knob_od_drive",
                            accentColor = TubeAmber,
                        )
                        AmpKnob(
                            label = "Tone",
                            value = config.odTone,
                            onValueChange = { v -> onUpdateRig { it.copy(odTone = v) } },
                            testTag = "inspector_knob_od_tone",
                            accentColor = TubeAmber,
                        )
                        OpenEditorButton("PEDALS") { onNavigateTab(StudioTab.PEDALBOARD) }
                    }
                }

                SignalNodeId.AMP -> {
                    Text(
                        text = "${config.ampModel.displayName} — ${config.ampModel.subtitle}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AmpKnob(
                            label = "Gain",
                            value = config.gain,
                            onValueChange = { v -> onUpdateRig { it.copy(gain = v) } },
                            testTag = "inspector_knob_amp_gain",
                            accentColor = CrimsonLed,
                        )
                        AmpKnob(
                            label = "Mid",
                            value = config.mid,
                            onValueChange = { v -> onUpdateRig { it.copy(mid = v) } },
                            testTag = "inspector_knob_amp_mid",
                            accentColor = TubeAmber,
                        )
                        AmpKnob(
                            label = "Master",
                            value = config.master,
                            onValueChange = { v -> onUpdateRig { it.copy(master = v) } },
                            testTag = "inspector_knob_amp_master",
                            accentColor = VoltCyan,
                        )
                        OpenEditorButton("AMP & CAB") { onNavigateTab(StudioTab.AMP_CAB) }
                    }
                }

                SignalNodeId.EQ -> {
                    Text(
                        text = "Bands (dB): " + config.eqBands.joinToString(" / ") { "${if (it > 0) "+" else ""}${it.roundToInt()}" },
                        style = MaterialTheme.typography.bodySmall,
                        color = TubeAmberGlow,
                    )
                    OpenEditorButton("OPEN 7-BAND EQ") { onNavigateTab(StudioTab.PEDALBOARD) }
                }

                SignalNodeId.CABINET -> {
                    Text(
                        text = "${config.cabModel.displayName} • ${config.micPosition.displayName} • ${config.cabModel.speakerSpec}",
                        style = MaterialTheme.typography.bodySmall,
                        color = VoltCyan,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AmpKnob(
                            label = "Resonance",
                            value = config.cabResonance,
                            onValueChange = { v -> onUpdateRig { it.copy(cabResonance = v) } },
                            testTag = "inspector_knob_cab_res",
                            accentColor = VoltCyan,
                        )
                        OpenEditorButton("CABINET & IR") { onNavigateTab(StudioTab.AMP_CAB) }
                    }
                }

                SignalNodeId.CHORUS -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AmpKnob(
                            label = "Mix",
                            value = config.chorusMix,
                            onValueChange = { v -> onUpdateRig { it.copy(chorusMix = v) } },
                            testTag = "inspector_knob_chorus",
                            accentColor = SteelBlue,
                        )
                        OpenEditorButton("OPEN PEDALBOARD") { onNavigateTab(StudioTab.PEDALBOARD) }
                    }
                }

                SignalNodeId.DELAY -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AmpKnob(
                            label = "Time",
                            value = ((config.delayTimeMs - 40f) / 760f).coerceIn(0f, 1f),
                            onValueChange = { v ->
                                onUpdateRig { it.copy(delayTimeMs = (40f + v * 760f).coerceIn(40f, 800f)) }
                            },
                            testTag = "inspector_knob_delay_time",
                            accentColor = SteelBlue,
                            valueFormatter = { v -> "${(40f + v * 760f).roundToInt()}ms" },
                        )
                        AmpKnob(
                            label = "Mix",
                            value = config.delayMix,
                            onValueChange = { v -> onUpdateRig { it.copy(delayMix = v) } },
                            testTag = "inspector_knob_delay_mix",
                            accentColor = SteelBlue,
                        )
                        OpenEditorButton("PEDALS") { onNavigateTab(StudioTab.PEDALBOARD) }
                    }
                }

                SignalNodeId.REVERB -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AmpKnob(
                            label = "Decay",
                            value = config.reverbDecay,
                            onValueChange = { v -> onUpdateRig { it.copy(reverbDecay = v) } },
                            testTag = "inspector_knob_reverb_decay",
                            accentColor = SteelBlue,
                        )
                        AmpKnob(
                            label = "Mix",
                            value = config.reverbMix,
                            onValueChange = { v -> onUpdateRig { it.copy(reverbMix = v) } },
                            testTag = "inspector_knob_reverb_mix",
                            accentColor = SteelBlue,
                        )
                        OpenEditorButton("PEDALS") { onNavigateTab(StudioTab.PEDALBOARD) }
                    }
                }

                SignalNodeId.OUTPUT -> {
                    Text(
                        text = "Master Output: ${(config.master * 10).roundToInt()}/10 • Peak: ${telemetry.outputPeakDb.roundToInt()} dBFS • Round-trip latency: ${"%.1f".format(telemetry.estimatedLatencyMs)} ms",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(
                        onClick = onOpenAudioIo,
                        border = BorderStroke(1.dp, VoltCyan),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = VoltCyan),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("inspector_output_io"),
                    ) {
                        Text("OUTPUT ROUTING & BUFFER SIZE")
                    }
                }
            }
        }
    }
}

@Composable
private fun OpenEditorButton(
    label: String,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        border = BorderStroke(1.dp, TubeAmber),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = TubeAmberGlow),
        modifier = Modifier
            .height(48.dp)
            .testTag("inspector_open_editor_button"),
    ) {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.width(4.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun SignalLegendDot(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
        )
    }
}
