package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.audio.AudioTelemetry
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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun LedIndicator(
    isActive: Boolean,
    activeColor: Color = CrimsonLed,
    size: Dp = 10.dp,
    modifier: Modifier = Modifier,
) {
    val color by animateColorAsState(
        targetValue = if (isActive) activeColor else Color(0xFF282C34),
        label = "led_color",
    )
    Canvas(modifier = modifier.size(size)) {
        val r = this.size.minDimension / 2f
        if (isActive) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(activeColor.copy(alpha = 0.65f), Color.Transparent),
                    center = center,
                    radius = r * 1.9f,
                ),
                radius = r * 1.9f,
            )
        }
        drawCircle(
            color = color,
            radius = r * 0.78f,
        )
        drawCircle(
            color = Color.White.copy(alpha = if (isActive) 0.55f else 0.1f),
            radius = r * 0.24f,
            center = Offset(center.x - r * 0.25f, center.y - r * 0.25f),
        )
    }
}

/**
 * Touch-friendly rotary amplifier/pedal potentiometer knob.
 * Drag vertically to adjust, double-tap to reset to [defaultValue].
 */
@Composable
fun AmpKnob(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
    defaultValue: Float = 0.5f,
    accentColor: Color = TubeAmber,
    knobSize: Dp = 58.dp,
    valueFormatter: (Float) -> String = { "${(it * 10f).roundToInt()}" },
) {
    val currentValue by rememberUpdatedState(value.coerceIn(0f, 1f))
    val currentCallback by rememberUpdatedState(onValueChange)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .testTag(testTag)
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, dragAmount ->
                    change.consume()
                    val delta = -dragAmount / 180f
                    currentCallback((currentValue + delta).coerceIn(0f, 1f))
                }
            }
            .pointerInput(defaultValue) {
                detectTapGestures(
                    onDoubleTap = { currentCallback(defaultValue.coerceIn(0f, 1f)) }
                )
            }
            .padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        Canvas(modifier = Modifier.size(knobSize)) {
            val strokeW = size.minDimension * 0.09f
            val arcPad = strokeW * 0.8f
            val arcSize = Size(size.width - arcPad * 2f, size.height - arcPad * 2f)
            val startAngle = 135f
            val totalSweep = 270f

            // Background track arc
            drawArc(
                color = Color(0xFF282C36),
                startAngle = startAngle,
                sweepAngle = totalSweep,
                useCenter = false,
                topLeft = Offset(arcPad, arcPad),
                size = arcSize,
                style = Stroke(width = strokeW, cap = StrokeCap.Round),
            )

            // Active glow arc
            drawArc(
                color = accentColor,
                startAngle = startAngle,
                sweepAngle = totalSweep * currentValue,
                useCenter = false,
                topLeft = Offset(arcPad, arcPad),
                size = arcSize,
                style = Stroke(width = strokeW, cap = StrokeCap.Round),
            )

            // Metallic dial body
            val bodyRadius = (size.minDimension * 0.35f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF3A3F4B), Color(0xFF171920)),
                    center = Offset(center.x - bodyRadius * 0.25f, center.y - bodyRadius * 0.25f),
                    radius = bodyRadius * 1.4f,
                ),
                radius = bodyRadius,
            )
            drawCircle(
                color = BezelBorder,
                radius = bodyRadius,
                style = Stroke(width = 1.5.dp.toPx()),
            )

            // Pointer line
            val angleDeg = startAngle + totalSweep * currentValue
            val angleRad = angleDeg * (PI / 180.0)
            val innerR = bodyRadius * 0.28f
            val outerR = bodyRadius * 0.88f
            drawLine(
                color = accentColor,
                start = Offset(
                    center.x + (innerR * cos(angleRad)).toFloat(),
                    center.y + (innerR * sin(angleRad)).toFloat(),
                ),
                end = Offset(
                    center.x + (outerR * cos(angleRad)).toFloat(),
                    center.y + (outerR * sin(angleRad)).toFloat(),
                ),
                strokeWidth = 2.6.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }

        Spacer(Modifier.height(2.dp))
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            maxLines = 1,
        )
        Text(
            text = valueFormatter(currentValue),
            style = MaterialTheme.typography.labelMedium,
            color = accentColor,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun StudioHeaderBar(
    telemetry: AudioTelemetry,
    activePresetName: String,
    onToggleEngine: () -> Unit,
    onToggleTestRiff: () -> Unit,
    onOpenAudioSheet: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = ChassisSurface,
        border = BorderStroke(1.dp, BezelBorder),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    // Engine Power button
                    Surface(
                        shape = CircleShape,
                        color = if (telemetry.engineRunning) TubeAmber else ModuleElevated,
                        border = BorderStroke(
                            1.dp,
                            if (telemetry.engineRunning) TubeAmberGlow else BezelBorder
                        ),
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onToggleEngine)
                            .testTag("engine_power_button"),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.PowerSettingsNew,
                                contentDescription = if (telemetry.engineRunning) "Stop DSP Engine" else "Start DSP Engine",
                                tint = if (telemetry.engineRunning) ObsidianBg else TextSecondary,
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = activePresetName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            LedIndicator(
                                isActive = telemetry.engineRunning,
                                activeColor = VoltCyan,
                                size = 8.dp,
                            )
                            Text(
                                text = if (telemetry.engineRunning) {
                                    "${telemetry.sampleRate / 1000}kHz • ${"%.1f".format(telemetry.estimatedLatencyMs)}ms"
                                } else {
                                    "DSP STANDBY"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (telemetry.engineRunning) VoltCyan else TextMuted,
                            )
                        }
                    }
                }

                // DI Riff vs Live Input toggle pill
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (telemetry.testRiffMode) MaterialTheme.colorScheme.primaryContainer else ModuleElevated,
                        border = BorderStroke(
                            1.dp,
                            if (telemetry.testRiffMode) TubeAmber else BezelBorder
                        ),
                        modifier = Modifier
                            .height(48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onToggleTestRiff)
                            .testTag("input_source_toggle"),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Icon(
                                imageVector = if (telemetry.testRiffMode) Icons.Default.GraphicEq else Icons.Default.Mic,
                                contentDescription = "Toggle DI Test Riff or Live Audio Input",
                                tint = if (telemetry.testRiffMode) TubeAmberGlow else VoltCyan,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = if (telemetry.testRiffMode) "DI RIFF" else "LIVE IN",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (telemetry.testRiffMode) TubeAmberGlow else VoltCyan,
                            )
                        }
                    }

                    // USB Audio & I/O button
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (telemetry.usbConnected) MaterialTheme.colorScheme.secondaryContainer else ModuleElevated,
                        border = BorderStroke(
                            1.dp,
                            if (telemetry.usbConnected) VoltCyan else BezelBorder
                        ),
                        modifier = Modifier
                            .height(48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onOpenAudioSheet)
                            .testTag("audio_io_button"),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = if (telemetry.usbConnected) Icons.Default.Usb else Icons.Default.SettingsInputComponent,
                                contentDescription = "Audio Interface and Latency Settings",
                                tint = if (telemetry.usbConnected) VoltCyan else TextSecondary,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = if (telemetry.usbConnected) "USB" else "I/O",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (telemetry.usbConnected) VoltCyan else TextSecondary,
                            )
                        }
                    }
                }
            }

            // Input & Output dB LED meters
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DbMeterBar(
                    label = "IN",
                    db = telemetry.inputPeakDb,
                    isClipping = false,
                    modifier = Modifier.weight(1f),
                )
                DbMeterBar(
                    label = "OUT",
                    db = telemetry.outputPeakDb,
                    isClipping = telemetry.isClipping,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun DbMeterBar(
    label: String,
    db: Float,
    isClipping: Boolean,
    modifier: Modifier = Modifier,
) {
    val norm = ((db + 60f) / 60f).coerceIn(0f, 1f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            modifier = Modifier.width(24.dp),
        )
        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(ObsidianBg)
        ) {
            val segments = 16
            val gap = 2.dp.toPx()
            val segW = (size.width - gap * (segments - 1)) / segments
            val litCount = (norm * segments).roundToInt()
            for (i in 0 until segments) {
                val color = when {
                    i >= litCount -> Color(0xFF1F232D)
                    i >= 14 -> CrimsonLed
                    i >= 11 -> TubeAmber
                    else -> VoltCyan
                }
                drawRect(
                    color = color,
                    topLeft = Offset(i * (segW + gap), 0f),
                    size = Size(segW, size.height),
                )
            }
        }
        if (isClipping) {
            Text(
                text = "CLIP",
                style = MaterialTheme.typography.labelSmall,
                color = CrimsonLed,
                fontWeight = FontWeight.Bold,
            )
        } else {
            Text(
                text = "${db.roundToInt()}dB",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                modifier = Modifier.width(34.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioIoSheet(
    telemetry: AudioTelemetry,
    onDismiss: () -> Unit,
    onRequestMicPermission: () -> Unit,
    onRefreshDevices: () -> Unit,
    onSelectInput: (Int) -> Unit,
    onSelectOutput: (Int) -> Unit,
    onSelectBufferFrames: (Int) -> Unit,
    onToggleBitPerfect: (Boolean) -> Unit,
    onToggleTestRiff: (Boolean) -> Unit,
    onSignOut: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ChassisSurface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    Text(
                        text = "AUDIO I/O & USB INTERFACE",
                        style = MaterialTheme.typography.titleLarge,
                        color = TubeAmberGlow,
                    )
                    Text(
                        text = "${telemetry.sampleRate} Hz • Est. Latency ${"%.1f".format(telemetry.estimatedLatencyMs)} ms",
                        style = MaterialTheme.typography.bodySmall,
                        color = VoltCyan,
                    )
                }
                IconButton(
                    onClick = onRefreshDevices,
                    modifier = Modifier.testTag("refresh_usb_devices_button"),
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Scan Audio Devices",
                        tint = VoltCyan,
                    )
                }
            }

            // USB status banner
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (telemetry.usbConnected) MaterialTheme.colorScheme.secondaryContainer else ModuleSurface,
                border = BorderStroke(1.dp, if (telemetry.usbConnected) VoltCyan else BezelBorder),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Usb,
                        contentDescription = null,
                        tint = if (telemetry.usbConnected) VoltCyan else TextMuted,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (telemetry.usbConnected) {
                                "USB Interface Detected: ${telemetry.activeUsbName ?: "USB Audio"}"
                            } else {
                                "No USB Audio Interface Connected"
                            },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = if (telemetry.usbConnected) {
                                "Plug guitar into Hi-Z input for studio recording."
                            } else {
                                "Connect a class-compliant USB-C guitar interface or use DI Test Riff mode."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                    }
                }
            }

            // Input mode & Mic permission
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = ModuleSurface,
                border = BorderStroke(1.dp, BezelBorder),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Built-in DI Metal Riff Loop", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Audition amps & matched tones without hardware",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                            )
                        }
                        Switch(
                            checked = telemetry.testRiffMode,
                            onCheckedChange = onToggleTestRiff,
                            colors = SwitchDefaults.colors(checkedTrackColor = TubeAmber),
                            modifier = Modifier.testTag("sheet_test_riff_switch"),
                        )
                    }

                    if (!telemetry.hasRecordPermission) {
                        Button(
                            onClick = onRequestMicPermission,
                            colors = ButtonDefaults.buttonColors(containerColor = VoltCyan, contentColor = ObsidianBg),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("grant_audio_permission_button"),
                        ) {
                            Text("ENABLE LIVE GUITAR / USB INPUT", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }

            // Buffer size selector
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("LOW-LATENCY BUFFER SIZE", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(64, 128, 256, 512).forEach { frames ->
                        val selected = telemetry.bufferFrames == frames
                        FilterChip(
                            selected = selected,
                            onClick = { onSelectBufferFrames(frames) },
                            label = { Text("$frames") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = TubeAmber,
                                selectedLabelColor = ObsidianBg,
                            ),
                            modifier = Modifier.testTag("buffer_${frames}_chip"),
                        )
                    }
                }
            }

            // Bit-perfect USB mixer toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Direct USB Bit-Perfect Routing (API 34+)", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (telemetry.bitPerfectUsbActive) "Active on USB DAC" else "Bypasses Android mixer when supported",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
                Switch(
                    checked = telemetry.bitPerfectUsbRequested,
                    onCheckedChange = onToggleBitPerfect,
                    colors = SwitchDefaults.colors(checkedTrackColor = VoltCyan),
                )
            }

            // Input & Output Device lists
            if (telemetry.inputDevices.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("INPUT DEVICE", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                    telemetry.inputDevices.forEach { dev ->
                        DeviceRow(
                            name = dev.name,
                            subtitle = dev.typeLabel,
                            selected = dev.id == telemetry.selectedInputId,
                            isUsb = dev.isUsb,
                            onClick = { onSelectInput(dev.id) },
                        )
                    }
                }
            }

            if (telemetry.outputDevices.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("OUTPUT DEVICE", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                    telemetry.outputDevices.forEach { dev ->
                        DeviceRow(
                            name = dev.name,
                            subtitle = dev.typeLabel,
                            selected = dev.id == telemetry.selectedOutputId,
                            isUsb = dev.isUsb,
                            onClick = { onSelectOutput(dev.id) },
                        )
                    }
                }
            }

            HorizontalDivider(color = BezelBorder)

            OutlinedButton(
                onClick = onSignOut,
                border = BorderStroke(1.dp, CrimsonLed),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = CrimsonLed),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("sign_out_button"),
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("SIGN OUT", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun DeviceRow(
    name: String,
    subtitle: String,
    selected: Boolean,
    isUsb: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) ModuleElevated else ModuleSurface,
        border = BorderStroke(1.dp, if (selected) VoltCyan else BezelBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isUsb) VoltCyan else TextSecondary,
                )
            }
            LedIndicator(isActive = selected, activeColor = VoltCyan, size = 10.dp)
        }
    }
}
