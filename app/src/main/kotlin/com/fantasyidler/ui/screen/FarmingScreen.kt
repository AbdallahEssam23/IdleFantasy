package com.fantasyidler.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fantasyidler.BuildConfig
import com.fantasyidler.R
import com.fantasyidler.data.json.CropData
import com.fantasyidler.data.model.FarmingPatch
import com.fantasyidler.repository.FarmingRepository
import com.fantasyidler.simulator.XpTable
import com.fantasyidler.ui.screen.skills.QuestIndicatorIcons
import com.fantasyidler.ui.theme.ScaledSheetContent
import com.fantasyidler.ui.viewmodel.FarmingUiState
import com.fantasyidler.ui.viewmodel.FarmingViewModel
import com.fantasyidler.ui.viewmodel.QuestIndicator
import com.fantasyidler.ui.viewmodel.remainingMs
import com.fantasyidler.util.GameStrings
import com.fantasyidler.util.formatDurationMs
import com.fantasyidler.util.formatXp
import com.fantasyidler.util.toClockTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FarmingScreen(
    onBack: () -> Unit,
    viewModel: FarmingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    AppBannerEffect(state.snackbarMessage, viewModel::snackbarConsumed)

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top),
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.skill_farming_name))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            contentPadding = padding,
            modifier        = Modifier.fillMaxSize(),
        ) {
            // XP bar
            item { FarmingXpBar(state) }

            item {
                Button(
                    onClick = viewModel::harvestAndPlantAll,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(stringResource(R.string.farming_harvest_and_plant))
                }
            }

            // Patch cards
            val patches = state.patches.associateBy { it.patchNumber }
            items(state.patchCount) { index ->
                val patchNumber = index + 1
                val patch       = patches[patchNumber]
                PatchCard(
                    patchNumber = patchNumber,
                    patch       = patch,
                    crops       = state.allCrops,
                    now         = state.now,
                    ashKey      = state.fertilizer[patchNumber.toString()],
                    showEndTime = state.showSessionEndTime,
                    onPlant     = { viewModel.openPlantSheet(patchNumber) },
                    onHarvest   = { viewModel.harvestPatch(patchNumber) },
                    onClimb     = { viewModel.climbBeanstalk(patchNumber) },
                    onClear     = { viewModel.clearPatch(patchNumber) },
                )
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    // Plant crop bottom sheet
    state.plantingPatchNumber?.let { patchNum ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = viewModel::closePlantSheet,
            sheetState       = sheetState,
            dragHandle       = { BottomSheetDefaults.DragHandle() },
        ) {
            ScaledSheetContent {
            PlantSheet(
                crops           = state.availableCrops,
                inventory       = state.inventory,
                questIndicators = state.questIndicators,
                onPlant         = { crop, ashKey -> viewModel.plantCrop(patchNum, crop, ashKey) },
                onDismiss       = viewModel::closePlantSheet,
            )
            }
        }
    }

    // Harvest result dialog
    state.harvestResult?.let { result ->
        AlertDialog(
            onDismissRequest = viewModel::harvestResultConsumed,
            title = {
                Text(
                    if (result.isBulk) stringResource(R.string.farming_harvest_all_complete)
                    else stringResource(R.string.farming_harvested, result.cropName)
                )
            },
            text = {
                Column {
                    Text(
                        text  = "+${result.xpGained.formatXp()} XP",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (result.itemsGained.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        result.itemsGained.forEach { (key, qty) ->
                            Text(
                                text  = "${GameStrings.itemName(context, key)}: ×$qty",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = viewModel::harvestResultConsumed) {
                    Text(stringResource(R.string.btn_close))
                }
            },
        )
    }

    if (state.showBeanClimbDialog) {
        BeanClimbDialog(onDismiss = viewModel::beanClimbDialogConsumed)
    }
}

// The beanstalk unlock stays up until dismissed; a transient banner was too easy
// to miss after the bean's month-long grow.
@Composable
private fun BeanClimbDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.farming_bean_climbed_title)) },
        text = {
            Text(
                stringResource(R.string.farming_bean_climbed) + "\n\n" +
                    stringResource(
                        R.string.farming_bean_unlock_hint,
                        GameStrings.dungeonName(context, "cloud_kingdom"),
                    )
            )
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(R.string.btn_close))
            }
        },
    )
}

// ---------------------------------------------------------------------------
// Sheet-mode entry point (used when shown inside a ModalBottomSheet)
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FarmingSheetContent(
    onDismiss: () -> Unit = {},
    viewModel: FarmingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    AppBannerEffect(state.snackbarMessage, viewModel::snackbarConsumed)

    // Full height even while loading, so the sheet's anchors don't shift when content arrives
    Box(Modifier.fillMaxSize()) {
        if (state.isLoading) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        } else LazyColumn(
            contentPadding = PaddingValues(bottom = 32.dp),
            modifier        = Modifier.fillMaxSize(),
        ) {
            item { FarmingXpBar(state) }
            item {
                Button(
                    onClick  = viewModel::harvestAndPlantAll,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                ) { Text(stringResource(R.string.farming_harvest_and_plant)) }
            }
            val patches = state.patches.associateBy { it.patchNumber }
            items(state.patchCount) { index ->
                val patchNumber = index + 1
                PatchCard(
                    patchNumber = patchNumber,
                    patch       = patches[patchNumber],
                    crops       = state.allCrops,
                    now         = state.now,
                    ashKey      = state.fertilizer[patchNumber.toString()],
                    showEndTime = state.showSessionEndTime,
                    onPlant     = { viewModel.openPlantSheet(patchNumber) },
                    onHarvest   = { viewModel.harvestPatch(patchNumber) },
                    onClimb     = { viewModel.climbBeanstalk(patchNumber) },
                    onClear     = { viewModel.clearPatch(patchNumber) },
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    state.plantingPatchNumber?.let { patchNum ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = viewModel::closePlantSheet,
            sheetState       = sheetState,
            dragHandle       = { BottomSheetDefaults.DragHandle() },
        ) {
            ScaledSheetContent {
            PlantSheet(
                crops           = state.availableCrops,
                inventory       = state.inventory,
                questIndicators = state.questIndicators,
                onPlant         = { crop, ashKey -> viewModel.plantCrop(patchNum, crop, ashKey) },
                onDismiss       = viewModel::closePlantSheet,
            )
            }
        }
    }

    state.harvestResult?.let { result ->
        AlertDialog(
            onDismissRequest = viewModel::harvestResultConsumed,
            title = {
                Text(
                    if (result.isBulk) stringResource(R.string.farming_harvest_all_complete)
                    else stringResource(R.string.farming_harvested, result.cropName)
                )
            },
            text = {
                Column {
                    Text(
                        text  = "+${result.xpGained.formatXp()} XP",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (result.itemsGained.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        result.itemsGained.forEach { (key, qty) ->
                            Text(
                                text  = "${GameStrings.itemName(context, key)}: ×$qty",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = viewModel::harvestResultConsumed) {
                    Text(stringResource(R.string.btn_close))
                }
            },
        )
    }

    if (state.showBeanClimbDialog) {
        BeanClimbDialog(onDismiss = viewModel::beanClimbDialogConsumed)
    }
}

// ---------------------------------------------------------------------------
// XP bar
// ---------------------------------------------------------------------------

@Composable
private fun FarmingXpBar(state: FarmingUiState) {
    val level    = state.farmingLevel
    val xp       = state.farmingXp
    val progress = XpTable.progressFraction(xp)

    Surface(
        color    = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        shape    = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier             = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text       = "🌱 " + stringResource(R.string.farming_title, level),
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text  = "${xp.formatXp()} XP",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                gapSize = 0.dp,
                drawStopIndicator = {},
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color    = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text  = stringResource(R.string.farming_patches, state.patchCount),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Patch card
// ---------------------------------------------------------------------------

@Composable
private fun PatchCard(
    patchNumber: Int,
    patch: FarmingPatch?,
    crops: Map<String, CropData>,
    now: Long,
    ashKey: String? = null,
    showEndTime: Boolean = true,
    onPlant: () -> Unit,
    onHarvest: () -> Unit,
    onClimb: () -> Unit,
    onClear: () -> Unit,
) {
    val context = LocalContext.current
    var showClearConfirm by remember { mutableStateOf(false) }

    val isEmpty   = patch == null || patch.cropType == null
    val cropData  = patch?.cropType?.let { crops[it] }
    val remaining = if (isEmpty) Long.MAX_VALUE else patch.remainingMs(crops, now)
    val isReady   = !isEmpty && remaining <= 0
    val isGrowing = !isEmpty && !isReady
    val isBean    = patch?.cropType == "magic_bean"
    val progress  = if (isGrowing) {
        val growthMs = cropData?.growthTimeMs ?: 1L
        ((growthMs - remaining).toFloat() / growthMs).coerceIn(0f, 1f)
    } else 0f

    // Plain clickable text: TextButton enforces a min size that can't be removed via contentPadding.
    val action: @Composable (String, Color, () -> Unit) -> Unit = { label, color, onClick ->
        Text(
            text     = label,
            style    = MaterialTheme.typography.labelLarge,
            color    = color,
            modifier = Modifier.clickable(onClick = onClick),
        )
    }
    val clearButton: @Composable () -> Unit = {
        action(stringResource(R.string.btn_clear), MaterialTheme.colorScheme.error) { showClearConfirm = true }
    }
    val statusText: @Composable RowScope.(String) -> Unit = { text ->
        Text(
            text     = text,
            style    = MaterialTheme.typography.bodySmall,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        elevation = CardDefaults.cardElevation(2.dp),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    if (isEmpty) {
                        Text(
                            text  = stringResource(R.string.label_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            text       = "${cropData?.emoji ?: "🌱"} ${GameStrings.cropName(context, patch?.cropType ?: "")}",
                            style      = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier   = Modifier.weight(1f, fill = false),
                        )
                        if (ashKey != null && !isBean) {
                            val pct = ((FarmingRepository.ashYieldMultiplier(ashKey) - 1f) * 100).toInt()
                            Text(
                                text  = " (🌿 ${stringResource(R.string.farming_fertilizer_yield, pct)})",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Text(
                    text  = stringResource(R.string.format_patch_number, patchNumber),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (isGrowing) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color    = MaterialTheme.colorScheme.primary,
                )
            }

            // Footer: status on the left, actions on the right
            Spacer(Modifier.height(6.dp))
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                when {
                    isEmpty -> {
                        Spacer(Modifier.weight(1f))
                        action(stringResource(R.string.btn_plant), MaterialTheme.colorScheme.primary, onPlant)
                    }
                    isGrowing && isBean -> {
                        // Magic bean — cryptic progress note instead of a time
                        statusText(when {
                            progress < 0.12f -> stringResource(R.string.farming_bean_note_1)
                            progress < 0.25f -> stringResource(R.string.farming_bean_note_2)
                            progress < 0.37f -> stringResource(R.string.farming_bean_note_3)
                            progress < 0.50f -> stringResource(R.string.farming_bean_note_4)
                            progress < 0.62f -> stringResource(R.string.farming_bean_note_5)
                            progress < 0.75f -> stringResource(R.string.farming_bean_note_6)
                            progress < 0.87f -> stringResource(R.string.farming_bean_note_7)
                            else             -> stringResource(R.string.farming_bean_note_8)
                        })
                        clearButton()
                    }
                    isGrowing -> {
                        val duration = remaining.formatDurationMs(context)
                        statusText(stringResource(
                            R.string.farming_ready_in,
                            if (showEndTime) "$duration (${(now + remaining).toClockTime(context)})" else duration,
                        ))
                        clearButton()
                    }
                    isBean -> {
                        // Magic bean ready — Climb instead of Harvest, no Clear
                        statusText(stringResource(R.string.farming_bean_ready))
                        action(stringResource(R.string.farming_bean_climb), MaterialTheme.colorScheme.primary, onClimb)
                    }
                    else -> {
                        clearButton()
                        action(stringResource(R.string.btn_harvest), MaterialTheme.colorScheme.primary, onHarvest)
                    }
                }
            }

            if (false && BuildConfig.DEBUG && isGrowing) { // TODO: debug buttons temporarily hidden
                TextButton(onClick = if (isBean) onClimb else onHarvest) {
                    Text(if (isBean) "[Debug] Instant Climb" else "[Debug] Instant Harvest")
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(stringResource(R.string.farming_clear_patch)) },
            text  = {
                Text(
                    if (patch?.cropType == "magic_bean") stringResource(R.string.farming_clear_desc_bean)
                    else stringResource(R.string.farming_clear_desc_named, GameStrings.cropName(context, patch?.cropType ?: ""))
                )
            },
            confirmButton = {
                Button(
                    onClick = { showClearConfirm = false; onClear() },
                    colors  = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.btn_clear)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            },
        )
    }
}

// ---------------------------------------------------------------------------
// Plant sheet
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlantSheet(
    crops: List<CropData>,
    inventory: Map<String, Int>,
    questIndicators: Map<String, List<QuestIndicator>> = emptyMap(),
    onPlant: (CropData, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val ashTiers = listOf("ashes","oak_ashes","willow_ashes","maple_ashes","yew_ashes","magic_ashes","redwood_ashes")
    val availableAshes = ashTiers.filter { (inventory[it] ?: 0) > 0 }
    var selectedAshKey by remember { mutableStateOf(availableAshes.lastOrNull()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp),
    ) {
        Text(
            text     = stringResource(R.string.btn_plant),
            style    = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        if (availableAshes.isNotEmpty()) {
            HorizontalDivider()
            Text(
                text     = stringResource(R.string.farming_fertilizer_optional),
                style    = MaterialTheme.typography.labelMedium,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            val ashLabel: (String?) -> String = { key ->
                if (key == null) context.getString(R.string.catalyst_none)
                else GameStrings.itemName(context, key)
            }
            var ashExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded         = ashExpanded,
                onExpandedChange = { ashExpanded = it },
                modifier         = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                OutlinedTextField(
                    value         = ashLabel(selectedAshKey),
                    onValueChange = {},
                    readOnly      = true,
                    trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = ashExpanded) },
                    modifier      = Modifier.menuAnchor().fillMaxWidth(),
                    textStyle     = MaterialTheme.typography.bodyMedium,
                    singleLine    = true,
                )
                ExposedDropdownMenu(
                    expanded         = ashExpanded,
                    onDismissRequest = { ashExpanded = false },
                ) {
                    (listOf(null) + availableAshes.reversed()).forEach { ashKey ->
                        DropdownMenuItem(
                            text    = {
                                if (ashKey == null) Text(ashLabel(null))
                                else Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(ashLabel(ashKey), modifier = Modifier.weight(1f))
                                        Text("x ${inventory[ashKey] ?: 0}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text(
                                        text  = stringResource(R.string.farming_fertilizer_yield, ((FarmingRepository.ashYieldMultiplier(ashKey) - 1f) * 100).toInt()),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            onClick = { selectedAshKey = ashKey; ashExpanded = false },
                        )
                    }
                }
            }
        }
        HorizontalDivider()
        Column(Modifier.verticalScroll(rememberScrollState())) {
            crops.forEach { crop ->
                val seedCount  = inventory[crop.seedName] ?: 0
                val ownedCount = inventory[crop.id] ?: 0
                val enabled    = seedCount > 0
                Row(
                    modifier             = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text     = "${crop.emoji} ${GameStrings.cropName(context, crop.id)}",
                                style    = MaterialTheme.typography.bodyLarge,
                                color    = if (enabled) MaterialTheme.colorScheme.onSurface
                                           else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            QuestIndicatorIcons(questIndicators[crop.id] ?: emptyList())
                        }
                        Text(
                            text  = if (crop.id == "magic_bean") stringResource(R.string.farming_bean_picker_stats)
                                    else stringResource(R.string.farming_crop_picker_stats, crop.levelRequired, (crop.growthTimeHours * 3_600_000L).formatDurationMs(context), crop.harvestXp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text  = stringResource(R.string.farming_seeds_owned, seedCount, ownedCount),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick  = { onPlant(crop, selectedAshKey); onDismiss() },
                        enabled  = enabled,
                    ) {
                        Text(stringResource(R.string.btn_plant))
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}
