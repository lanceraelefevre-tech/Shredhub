package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.AmpModel
import com.example.model.CabModel
import com.example.model.FactoryPreset
import com.example.model.FactoryPresets
import com.example.model.PresetCategory
import com.example.model.RigPresetDoc
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
import com.example.viewmodel.UiState

@Composable
fun PresetsScreen(
    presetsState: UiState<List<RigPresetDoc>>,
    activeCloudPresetId: String?,
    activePresetName: String,
    selectedCategory: PresetCategory?,
    favoritesOnly: Boolean,
    searchQuery: String,
    savingPreset: Boolean,
    onSearchChange: (String) -> Unit,
    onSelectCategory: (PresetCategory?) -> Unit,
    onToggleFavoritesOnly: () -> Unit,
    onOpenSaveDialog: () -> Unit,
    onOverwriteActive: () -> Unit,
    onLoadCloudPreset: (RigPresetDoc) -> Unit,
    onToggleFavorite: (RigPresetDoc) -> Unit,
    onDeleteCloudPreset: (RigPresetDoc) -> Unit,
    onLoadFactoryPreset: (FactoryPreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingDelete by remember { mutableStateOf<RigPresetDoc?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            // Actions & Search Header
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = onOpenSaveDialog,
                        enabled = !savingPreset,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TubeAmber,
                            contentColor = ObsidianBg,
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("save_new_preset_button"),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("SAVE CURRENT RIG", style = MaterialTheme.typography.labelLarge)
                    }

                    if (activeCloudPresetId != null) {
                        OutlinedButton(
                            onClick = onOverwriteActive,
                            enabled = !savingPreset,
                            border = BorderStroke(1.dp, VoltCyan),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = VoltCyan),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .height(50.dp)
                                .testTag("update_active_preset_button"),
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("UPDATE", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    placeholder = { Text("Search presets or amps...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchChange("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TubeAmber,
                        unfocusedBorderColor = BezelBorder,
                        focusedContainerColor = ChassisSurface,
                        unfocusedContainerColor = ChassisSurface,
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("preset_search_input"),
                )

                // Category Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = selectedCategory == null && !favoritesOnly,
                        onClick = {
                            onSelectCategory(null)
                            if (favoritesOnly) onToggleFavoritesOnly()
                        },
                        label = { Text("All") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = TubeAmber,
                            selectedLabelColor = ObsidianBg,
                        ),
                        modifier = Modifier.testTag("cat_all"),
                    )
                    FilterChip(
                        selected = favoritesOnly,
                        onClick = onToggleFavoritesOnly,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                        label = { Text("Favorites") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = TubeAmberGlow,
                            selectedLabelColor = ObsidianBg,
                        ),
                        modifier = Modifier.testTag("cat_favorites"),
                    )
                    PresetCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = {
                                onSelectCategory(if (selectedCategory == cat) null else cat)
                            },
                            label = { Text(cat.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = VoltCyan,
                                selectedLabelColor = ObsidianBg,
                            ),
                            modifier = Modifier.testTag("cat_${cat.id}"),
                        )
                    }
                }
            }
        }

        // User Cloud Presets Section
        item {
            Text(
                text = "YOUR CLOUD PRESETS",
                style = MaterialTheme.typography.labelLarge,
                color = VoltCyan,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        when (presetsState) {
            UiState.Loading -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = TubeAmber)
                    }
                }
            }

            is UiState.Error -> {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = presetsState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(14.dp),
                        )
                    }
                }
            }

            is UiState.Success -> {
                val query = searchQuery.trim().lowercase()
                val filtered = presetsState.data.filter { doc ->
                    val matchesFav = !favoritesOnly || doc.favorite
                    val matchesCat = selectedCategory == null || doc.category == selectedCategory.id
                    val ampName = AmpModel.fromId(doc.ampModelId).displayName.lowercase()
                    val matchesQuery = query.isEmpty() ||
                        doc.name.lowercase().contains(query) ||
                        ampName.contains(query) ||
                        PresetCategory.fromId(doc.category).label.lowercase().contains(query)
                    matchesFav && matchesCat && matchesQuery
                }

                if (filtered.isEmpty()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = ModuleSurface,
                            border = BorderStroke(1.dp, BezelBorder),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LibraryMusic,
                                    contentDescription = null,
                                    tint = TextMuted,
                                    modifier = Modifier.size(34.dp),
                                )
                                Text(
                                    text = if (presetsState.data.isEmpty()) {
                                        "No saved cloud rigs yet. Dial in a tone or run Tone Match, then tap SAVE CURRENT RIG."
                                    } else {
                                        "No presets match your current filter."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                )
                            }
                        }
                    }
                } else {
                    items(filtered, key = { it.id }) { doc ->
                        val isActive = doc.id == activeCloudPresetId
                        CloudPresetCard(
                            doc = doc,
                            isActive = isActive,
                            onLoad = { onLoadCloudPreset(doc) },
                            onToggleFavorite = { onToggleFavorite(doc) },
                            onRequestDelete = { pendingDelete = doc },
                        )
                    }
                }
            }
        }

        // Factory Presets Section
        item {
            Text(
                text = "FACTORY METAL RIGS",
                style = MaterialTheme.typography.labelLarge,
                color = TubeAmberGlow,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        val query = searchQuery.trim().lowercase()
        val filteredFactory = FactoryPresets.all.filter { preset ->
            val matchesCat = selectedCategory == null || preset.category == selectedCategory
            val matchesQuery = query.isEmpty() ||
                preset.name.lowercase().contains(query) ||
                preset.config.ampModel.displayName.lowercase().contains(query) ||
                preset.category.label.lowercase().contains(query)
            !favoritesOnly && matchesCat && matchesQuery
        }

        items(filteredFactory, key = { it.id }) { preset ->
            val isActive = activeCloudPresetId == null && activePresetName == preset.name
            FactoryPresetCard(
                preset = preset,
                isActive = isActive,
                onLoad = { onLoadFactoryPreset(preset) },
            )
        }

        item { Spacer(Modifier.height(16.dp)) }
    }

    pendingDelete?.let { doc ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = ChassisSurface,
            title = { Text("Delete Preset?") },
            text = { Text("Remove \"${doc.name}\" from your cloud rig library? This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteCloudPreset(doc)
                        pendingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonLed),
                    modifier = Modifier.testTag("confirm_delete_preset_button"),
                ) {
                    Text("DELETE")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("CANCEL", color = TextSecondary)
                }
            },
        )
    }
}

@Composable
private fun CloudPresetCard(
    doc: RigPresetDoc,
    isActive: Boolean,
    onLoad: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRequestDelete: () -> Unit,
) {
    val amp = AmpModel.fromId(doc.ampModelId)
    val cab = CabModel.fromId(doc.cabModelId)
    val cat = PresetCategory.fromId(doc.category)

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isActive) ModuleElevated else ModuleSurface,
        border = BorderStroke(1.5.dp, if (isActive) VoltCyan else BezelBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onLoad)
            .testTag("cloud_preset_card_${doc.id}"),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = doc.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (doc.toneMatched) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Text(
                                text = "MATCHED",
                                style = MaterialTheme.typography.labelSmall,
                                color = VoltCyan,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                Text(
                    text = "${cat.label} • ${amp.displayName} • ${cab.displayName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isActive) VoltCyan else TextSecondary,
                )
                if (!doc.toneMatchSummary.isNullOrBlank()) {
                    Text(
                        text = doc.toneMatchSummary,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.testTag("fav_preset_${doc.id}"),
                ) {
                    Icon(
                        imageVector = if (doc.favorite) Icons.Default.Star else Icons.Outlined.StarOutline,
                        contentDescription = "Toggle Favorite",
                        tint = if (doc.favorite) TubeAmberGlow else TextMuted,
                    )
                }
                IconButton(
                    onClick = onRequestDelete,
                    modifier = Modifier.testTag("delete_preset_${doc.id}"),
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Preset",
                        tint = CrimsonLed,
                    )
                }
            }
        }
    }
}

@Composable
private fun FactoryPresetCard(
    preset: FactoryPreset,
    isActive: Boolean,
    onLoad: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isActive) ModuleElevated else ChassisSurface,
        border = BorderStroke(1.dp, if (isActive) TubeAmber else BezelBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onLoad)
            .testTag("factory_preset_${preset.id}"),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = preset.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isActive) TubeAmberGlow else MaterialTheme.colorScheme.onSurface,
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = ModuleSurface,
                    border = BorderStroke(1.dp, BezelBorder),
                ) {
                    Text(
                        text = preset.category.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = TubeAmber,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
            Text(
                text = "${preset.config.ampModel.displayName} • ${preset.config.cabModel.displayName}",
                style = MaterialTheme.typography.labelMedium,
                color = VoltCyan,
            )
            Text(
                text = preset.description,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }
    }
}

@Composable
fun SavePresetDialog(
    initialName: String,
    initialCategory: PresetCategory,
    onDismiss: () -> Unit,
    onConfirmSave: (name: String, category: PresetCategory, favorite: Boolean) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var category by remember { mutableStateOf(initialCategory) }
    var favorite by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ChassisSurface,
        title = {
            Text(
                text = "SAVE RIG PRESET",
                style = MaterialTheme.typography.titleLarge,
                color = TubeAmberGlow,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 50) name = it },
                    label = { Text("Preset Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TubeAmber,
                        unfocusedBorderColor = BezelBorder,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("preset_name_input"),
                )

                Text("CATEGORY", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    PresetCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = VoltCyan,
                                selectedLabelColor = ObsidianBg,
                            ),
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Mark as Favorite", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = favorite,
                        onCheckedChange = { favorite = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = TubeAmber),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.trim().isNotEmpty()) {
                        onConfirmSave(name.trim(), category, favorite)
                    }
                },
                enabled = name.trim().isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TubeAmber,
                    contentColor = ObsidianBg,
                ),
                modifier = Modifier.testTag("confirm_save_preset_button"),
            ) {
                Text("SAVE PRESET")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = TextSecondary)
            }
        },
    )
}
