package com.example.ui

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.PresetCategory
import com.example.ui.components.AudioIoSheet
import com.example.ui.components.StudioHeaderBar
import com.example.ui.screens.AmpCabScreen
import com.example.ui.screens.PedalboardScreen
import com.example.ui.screens.PresetsScreen
import com.example.ui.screens.SavePresetDialog
import com.example.ui.screens.SignalChainScreen
import com.example.ui.screens.ToneMatchScreen
import com.example.ui.theme.ChassisSurface
import com.example.ui.theme.ModuleElevated
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TubeAmber
import com.example.ui.theme.TubeAmberGlow
import com.example.viewmodel.ShredForgeViewModel
import com.example.viewmodel.StudioTab

@Composable
fun MainStudioScreen(
    viewModel: ShredForgeViewModel,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val activeTab by viewModel.activeTab.collectAsStateWithLifecycle()
    val rigConfig by viewModel.rigConfig.collectAsStateWithLifecycle()
    val telemetry by viewModel.audioTelemetry.collectAsStateWithLifecycle()
    val activePresetName by viewModel.activePresetName.collectAsStateWithLifecycle()
    val activeCloudPresetId by viewModel.activeCloudPresetId.collectAsStateWithLifecycle()
    val toneMatchState by viewModel.toneMatchState.collectAsStateWithLifecycle()
    val presetsState by viewModel.presetsState.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val favoritesOnly by viewModel.favoritesOnly.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val savingPreset by viewModel.savingPreset.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()

    var showAudioSheet by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.refreshAudioPermissions()
        if (granted) {
            viewModel.setTestRiffMode(false)
            if (!telemetry.engineRunning) viewModel.startEngine()
        }
    }

    LaunchedEffect(statusBanner) {
        val msg = statusBanner
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusBanner()
        }
    }

    // Back button from secondary studio tabs returns to Signal Chain Dashboard
    BackHandler(enabled = activeTab != StudioTab.SIGNAL_CHAIN) {
        viewModel.selectTab(StudioTab.SIGNAL_CHAIN)
    }

    val tabIcons = mapOf(
        StudioTab.SIGNAL_CHAIN to Icons.Default.AccountTree,
        StudioTab.AMP_CAB to Icons.Default.Speaker,
        StudioTab.PEDALBOARD to Icons.Default.Tune,
        StudioTab.TONE_MATCH to Icons.Default.AutoFixHigh,
        StudioTab.PRESETS to Icons.Default.Bookmarks,
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(ObsidianBg, ChassisSurface.copy(alpha = 0.7f), ObsidianBg)
                )
            )
    ) {
        val isWideScreen = maxWidth >= 680.dp

        Scaffold(
            containerColor = ObsidianBg.copy(alpha = 0f),
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!isWideScreen) {
                    NavigationBar(
                        containerColor = ChassisSurface,
                        windowInsets = WindowInsets.navigationBars,
                    ) {
                        StudioTab.entries.forEach { tab ->
                            val selected = activeTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { viewModel.selectTab(tab) },
                                icon = {
                                    Icon(
                                        imageVector = tabIcons.getValue(tab),
                                        contentDescription = tab.label,
                                    )
                                },
                                label = {
                                    Text(
                                        text = tab.label,
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = TubeAmberGlow,
                                    selectedTextColor = TubeAmberGlow,
                                    indicatorColor = ModuleElevated,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary,
                                ),
                                modifier = Modifier.testTag("nav_${tab.name.lowercase()}"),
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (isWideScreen) {
                    NavigationRail(
                        containerColor = ChassisSurface,
                        modifier = Modifier
                            .fillMaxHeight()
                            .windowInsetsPadding(WindowInsets.navigationBars),
                    ) {
                        StudioTab.entries.forEach { tab ->
                            val selected = activeTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { viewModel.selectTab(tab) },
                                icon = {
                                    Icon(
                                        imageVector = tabIcons.getValue(tab),
                                        contentDescription = tab.label,
                                    )
                                },
                                label = {
                                    Text(
                                        text = tab.label,
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = TubeAmberGlow,
                                    selectedTextColor = TubeAmberGlow,
                                    indicatorColor = ModuleElevated,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary,
                                ),
                                modifier = Modifier.testTag("rail_${tab.name.lowercase()}"),
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 860.dp)
                            .fillMaxSize()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        StudioHeaderBar(
                            telemetry = telemetry,
                            activePresetName = activePresetName,
                            onToggleEngine = viewModel::toggleEngine,
                            onToggleTestRiff = {
                                if (telemetry.testRiffMode) {
                                    if (telemetry.hasRecordPermission) {
                                        viewModel.setTestRiffMode(false)
                                        if (!telemetry.engineRunning) viewModel.startEngine()
                                    } else {
                                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                } else {
                                    viewModel.setTestRiffMode(true)
                                }
                            },
                            onOpenAudioSheet = {
                                viewModel.refreshAudioDevices()
                                showAudioSheet = true
                            },
                        )

                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            when (activeTab) {
                                StudioTab.SIGNAL_CHAIN -> SignalChainScreen(
                                    config = rigConfig,
                                    telemetry = telemetry,
                                    onUpdateRig = viewModel::updateRig,
                                    onNavigateTab = viewModel::selectTab,
                                    onOpenAudioIo = {
                                        viewModel.refreshAudioDevices()
                                        showAudioSheet = true
                                    },
                                )

                                StudioTab.AMP_CAB -> AmpCabScreen(
                                    config = rigConfig,
                                    telemetry = telemetry,
                                    onSelectAmp = viewModel::selectAmpModel,
                                    onUpdateRig = viewModel::updateRig,
                                    onSelectCab = viewModel::selectCabModel,
                                    onSelectMic = viewModel::selectMicPosition,
                                    onLoadWavIr = viewModel::loadCustomWavIr,
                                )

                                StudioTab.PEDALBOARD -> PedalboardScreen(
                                    config = rigConfig,
                                    telemetry = telemetry,
                                    onUpdateRig = viewModel::updateRig,
                                    onUpdateEqBand = viewModel::updateEqBand,
                                )

                                StudioTab.TONE_MATCH -> ToneMatchScreen(
                                    state = toneMatchState,
                                    onUploadSample = viewModel::analyzeToneSample,
                                    onPlaySample = viewModel::playUploadedToneSample,
                                    onApplyToRig = viewModel::applyMatchedToneToRig,
                                    onSaveMatchedPreset = { showSaveDialog = true },
                                )

                                StudioTab.PRESETS -> PresetsScreen(
                                    presetsState = presetsState,
                                    activeCloudPresetId = activeCloudPresetId,
                                    activePresetName = activePresetName,
                                    selectedCategory = selectedCategory,
                                    favoritesOnly = favoritesOnly,
                                    searchQuery = searchQuery,
                                    savingPreset = savingPreset,
                                    onSearchChange = viewModel::setSearchQuery,
                                    onSelectCategory = viewModel::setCategoryFilter,
                                    onToggleFavoritesOnly = viewModel::toggleFavoritesOnly,
                                    onOpenSaveDialog = { showSaveDialog = true },
                                    onOverwriteActive = viewModel::overwriteActiveCloudPreset,
                                    onLoadCloudPreset = viewModel::loadCloudPreset,
                                    onToggleFavorite = viewModel::togglePresetFavorite,
                                    onDeleteCloudPreset = viewModel::deleteCloudPreset,
                                    onLoadFactoryPreset = viewModel::loadFactoryPreset,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAudioSheet) {
        AudioIoSheet(
            telemetry = telemetry,
            onDismiss = { showAudioSheet = false },
            onRequestMicPermission = {
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            },
            onRefreshDevices = viewModel::refreshAudioDevices,
            onSelectInput = viewModel::setSelectedInput,
            onSelectOutput = viewModel::setSelectedOutput,
            onSelectBufferFrames = viewModel::setBufferFrames,
            onToggleBitPerfect = viewModel::setBitPerfectUsb,
            onToggleTestRiff = { enabled ->
                if (!enabled && !telemetry.hasRecordPermission) {
                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                } else {
                    viewModel.setTestRiffMode(enabled)
                }
            },
            onSignOut = {
                showAudioSheet = false
                signOut(credentialManager = credentialManager, scope = scope)
            },
        )
    }

    if (showSaveDialog) {
        val defaultCategory = if (rigConfig.toneMatched) {
            PresetCategory.TONE_MATCHED
        } else {
            PresetCategory.MODERN_METAL
        }
        SavePresetDialog(
            initialName = activePresetName.take(45),
            initialCategory = defaultCategory,
            onDismiss = { showSaveDialog = false },
            onConfirmSave = { name, category, favorite ->
                showSaveDialog = false
                viewModel.saveNewCloudPreset(name, category, favorite)
            },
        )
    }
}
