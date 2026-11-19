package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.BezelBorder
import com.example.ui.theme.ChassisSurface
import com.example.ui.theme.CrimsonLed
import com.example.ui.theme.ModuleSurface
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TubeAmber
import com.example.ui.theme.TubeAmberGlow
import com.example.ui.theme.VoltCyan
import com.example.viewmodel.ToneMatchUiState
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ToneMatchScreen(
    state: ToneMatchUiState,
    onUploadSample: (Uri) -> Unit,
    onPlaySample: () -> Unit,
    onApplyToRig: () -> Unit,
    onSaveMatchedPreset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onUploadSample(uri)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Upload & Capture Header
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = ChassisSurface,
            border = BorderStroke(1.dp, BezelBorder),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoFixHigh,
                        contentDescription = null,
                        tint = TubeAmberGlow,
                        modifier = Modifier.size(26.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "HIGH-GAIN TONE MATCHING",
                            style = MaterialTheme.typography.titleLarge,
                            color = TubeAmberGlow,
                        )
                        Text(
                            text = "Upload a short guitar riff sample (.wav, .mp3, .m4a, .flac). ShredForge extracts its FFT spectral curve, mid-scoop, harmonic saturation, and cab profile.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                    }
                }

                Button(
                    onClick = { audioPicker.launch(arrayOf("audio/*")) },
                    enabled = !state.analyzing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TubeAmber,
                        contentColor = ObsidianBg,
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("upload_tone_sample_button"),
                ) {
                    if (state.analyzing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = ObsidianBg,
                            strokeWidth = 2.5.dp,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("ANALYZING FFT & HARMONICS...", style = MaterialTheme.typography.labelLarge)
                    } else {
                        Icon(Icons.Default.AudioFile, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("UPLOAD GUITAR TONE SAMPLE", style = MaterialTheme.typography.labelLarge)
                    }
                }

                if (state.errorMessage != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = state.errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }
        }

        val result = state.result
        if (result == null && !state.analyzing) {
            // Empty State Guide
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = ModuleSurface,
                border = BorderStroke(1.dp, BezelBorder),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = VoltCyan,
                        modifier = Modifier.size(44.dp),
                    )
                    Text(
                        text = "READY TO PROFILE A REFERENCE TONE",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "Tip: Isolated guitar riffs or stems work best. Once analyzed, ShredForge tunes the Preamp, Tube Overdrive, 7-Band EQ, and custom IR Cabinet automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
            }
        } else if (result != null) {
            // Analysis Result & Dual Spectral Curve
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = ChassisSurface,
                border = BorderStroke(1.dp, VoltCyan.copy(alpha = 0.65f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
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
                            Text(
                                text = result.sampleName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "Analyzed ${"%.1f".format(result.sampleDurationSec)}s active riff",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            border = BorderStroke(1.dp, VoltCyan),
                        ) {
                            Text(
                                text = "${result.confidencePercent}% MATCH",
                                style = MaterialTheme.typography.labelLarge,
                                color = VoltCyan,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }

                    // Dual Spectral Curve Plot
                    DualSpectrumChart(
                        targetDb = result.targetSpectrumDb,
                        matchedDb = result.matchedRigSpectrumDb,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        LegendItem(color = TubeAmber, label = "Target Sample")
                        LegendItem(color = VoltCyan, label = "ShredForge Rig")
                    }

                    // Detected Traits
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        result.detectedTraits.forEach { trait ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = ModuleSurface,
                                border = BorderStroke(1.dp, BezelBorder),
                            ) {
                                Text(
                                    text = trait,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TubeAmberGlow,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }

                    // Matched Rig Summary Card
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ModuleSurface,
                        border = BorderStroke(1.dp, BezelBorder),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text("MATCHED SIGNAL CHAIN", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                            val cfg = result.matchedConfig
                            Text(
                                text = "Amp: ${cfg.ampModel.displayName} (Gain ${(cfg.gain * 10).roundToInt()}/10)",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = "Tone Stack: B ${(cfg.bass * 10).roundToInt()} • M ${(cfg.mid * 10).roundToInt()} • T ${(cfg.treble * 10).roundToInt()} • Pres ${(cfg.presence * 10).roundToInt()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                            )
                            Text(
                                text = "Pedals: Gate ${if (cfg.gateEnabled) "ON" else "OFF"} • OD Boost ${if (cfg.odEnabled) "ON" else "OFF"} • 7-Band EQ ${if (cfg.eqEnabled) "ON" else "OFF"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                            )
                            Text(
                                text = "Cabinet: ${cfg.cabModel.displayName} (${cfg.micPosition.displayName})",
                                style = MaterialTheme.typography.bodySmall,
                                color = VoltCyan,
                            )
                        }
                    }

                    // Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedButton(
                            onClick = onPlaySample,
                            enabled = !state.playingSample,
                            border = BorderStroke(1.dp, TubeAmber),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TubeAmberGlow),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("play_uploaded_sample_button"),
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = if (state.playingSample) "PLAYING..." else "A/B SAMPLE",
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }

                        Button(
                            onClick = onApplyToRig,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (state.appliedToRig) VoltCyan else CrimsonLed,
                                contentColor = ObsidianBg,
                            ),
                            modifier = Modifier
                                .weight(1.25f)
                                .height(48.dp)
                                .testTag("apply_tone_match_button"),
                        ) {
                            Icon(
                                imageVector = if (state.appliedToRig) Icons.Default.CheckCircle else Icons.Default.AutoFixHigh,
                                contentDescription = null,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (state.appliedToRig) "APPLIED TO RIG" else "APPLY TO RIG",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    if (state.appliedToRig) {
                        OutlinedButton(
                            onClick = onSaveMatchedPreset,
                            border = BorderStroke(1.dp, VoltCyan),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = VoltCyan),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("save_matched_preset_button"),
                        ) {
                            Icon(Icons.Default.BookmarkAdd, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("SAVE MATCHED RIG AS PRESET", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DualSpectrumChart(
    targetDb: List<Float>,
    matchedDb: List<Float>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(ObsidianBg)
                .padding(10.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Horizontal dB gridlines (-12, -24, -36 dB)
                for (step in 1..3) {
                    val y = size.height * (step / 4f)
                    drawLine(
                        color = BezelBorder.copy(alpha = 0.55f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx(),
                    )
                }

                fun buildPath(pointsDb: List<Float>): Path {
                    val p = Path()
                    if (pointsDb.isEmpty()) return p
                    val count = pointsDb.size
                    pointsDb.forEachIndexed { idx, db ->
                        val x = if (count <= 1) 0f else (idx.toFloat() / (count - 1)) * size.width
                        val normY = ((db + 48f) / 48f).coerceIn(0f, 1f)
                        val y = size.height * (1f - normY)
                        if (idx == 0) p.moveTo(x, y) else p.lineTo(x, y)
                    }
                    return p
                }

                drawPath(
                    path = buildPath(targetDb),
                    color = TubeAmber,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
                drawPath(
                    path = buildPath(matchedDb),
                    color = VoltCyan,
                    style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("60Hz", style = MaterialTheme.typography.labelSmall, color = TextMuted)
            Text("250Hz", style = MaterialTheme.typography.labelSmall, color = TextMuted)
            Text("1kHz", style = MaterialTheme.typography.labelSmall, color = TextMuted)
            Text("4kHz", style = MaterialTheme.typography.labelSmall, color = TextMuted)
            Text("11kHz", style = MaterialTheme.typography.labelSmall, color = TextMuted)
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(12.dp, 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    }
}
