/*
 * Copyright (C) 2025-2026 AxionOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.axion.axpcmode.ui.components

import android.app.ActivityOptions
import android.content.Intent
import android.provider.Settings
import android.view.Display
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.draganddrop.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draganddrop.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.*
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.android.axion.axpcmode.R
import com.android.axion.axpcmode.services.AvailableTileData
import com.android.axion.axpcmode.services.QSTileData
import com.android.axion.axpcmode.ui.QuickSettingsViewModel
import com.android.axion.axpcmode.ui.components.qs.*
import com.android.axion.axpcmode.ui.tiles.TileIconMapping

/** Footprint of the panel, trailing padding included; the overlay window is sized around it. */
val QuickSettingsPanelWidth = 420.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QuickSettingsPanel(viewModel: QuickSettingsViewModel, modifier: Modifier = Modifier, targetDisplayId: Int = Display.DEFAULT_DISPLAY) {
    val context = LocalContext.current
    val isAutoBrightness by viewModel.isAutoBrightness.collectAsState()
    val currentVolume by viewModel.volume.collectAsState()
    val maxVolume by viewModel.maxVolume.collectAsState()
    val sliderPosition by viewModel.sliderPosition.collectAsState()
    val activeDetailId by viewModel.activeDetailId.collectAsState()
    val isEditMode by viewModel.isEditMode.collectAsState()

    val configuration = LocalConfiguration.current
    val safeMaxHeight = (configuration.screenHeightDp - 80).dp

    Surface(
        modifier =
            modifier
                .width(QuickSettingsPanelWidth)
                .heightIn(max = safeMaxHeight)
                .padding(top = 12.dp, bottom = 12.dp, end = 12.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 8.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            BrightnessAndVolumeSliders(
                sliderPosition = sliderPosition,
                onSliderChange = { viewModel.updateBrightness(it) },
                onSliderChangeFinished = { viewModel.finishBrightnessAdjustment() },
                isAutoBrightness = isAutoBrightness,
                onAutoBrightnessToggle = { viewModel.toggleAutoBrightness() },
                currentVolume = currentVolume,
                maxVolume = maxVolume,
                onVolumeChange = { viewModel.updateVolume(it) },
            )

            Spacer(modifier = Modifier.height(12.dp))

            val qsTiles by viewModel.qsTiles.collectAsState()

            if (qsTiles.isNotEmpty()) {
                val tileChunks = remember(qsTiles) { qsTiles.chunked(8) }
                val pagerState = rememberPagerState(pageCount = { tileChunks.size })

                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxWidth(),
                        beyondViewportPageCount = 1,
                    ) { pageIndex ->
                        val pageTiles = tileChunks[pageIndex]
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(4),
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            userScrollEnabled = false,
                        ) {
                            items(8) { index ->
                                if (index < pageTiles.size) {
                                    val tile = pageTiles[index]
                                    QsTile(
                                        tileData = tile,
                                        modifier =
                                            Modifier.animateItem(
                                                fadeInSpec = tween(500),
                                                placementSpec =
                                                    spring(stiffness = Spring.StiffnessLow),
                                            ),
                                        onClick = { viewModel.clickQSTile(tile.spec) },
                                    )
                                } else {

                                    Box(Modifier.fillMaxWidth().height(80.dp))
                                }
                            }
                        }
                    }

                    if (tileChunks.size > 1) {
                        PageIndicator(
                            pageCount = tileChunks.size,
                            currentPage = pagerState.currentPage,
                            modifier =
                                Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp),
                        )
                    }
                }
            } else {
                Box(Modifier.fillMaxWidth().height(100.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { viewModel.toggleEditMode() }
                ) {
                    Icon(
                        Icons.Rounded.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }

                IconButton(
                    onClick = {
                        try {
                            val intent = Intent(Settings.ACTION_SETTINGS)
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            val opts = if (targetDisplayId != Display.DEFAULT_DISPLAY) {
                                ActivityOptions.makeBasic().apply {
                                    launchDisplayId = targetDisplayId
                                }.toBundle()
                            } else null
                            context.startActivity(intent, opts)
                        } catch (_: Exception) {
                        }
                    }
                ) {
                    Icon(
                        Icons.Rounded.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QsTileEditor(
    viewModel: QuickSettingsViewModel,
    qsTiles: List<QSTileData>,
    availableTiles: List<AvailableTileData>,
) {
    val dragDropState = remember(qsTiles) { QsTileDragDropState(qsTiles, columns = 4) }

    val availableList = remember(availableTiles, qsTiles) {
        val activeSpecs = qsTiles.map { it.spec }.toSet()
        availableTiles.filter { it.spec !in activeSpecs }.toMutableStateList()
    }

    var activeGridOffset by remember { mutableStateOf(Offset.Zero) }

    Row(
        modifier = Modifier.fillMaxWidth().height(500.dp).padding(top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.active_tiles),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                modifier = Modifier.padding(bottom = 12.dp).basicMarquee(),
            )

            Column(
                modifier =
                    Modifier.weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .onGloballyPositioned { coords ->
                            activeGridOffset = coords.positionInRoot()
                        }
                        .dragAndDropActiveGrid(
                            contentOffset = { activeGridOffset },
                            dragDropState = dragDropState,
                        ) { specs ->
                            dragDropState.draggedTile?.let { dragged ->
                                if (dragDropState.dragType == DragType.Add) {
                                    availableList.removeAll { it.spec == dragged.spec }
                                }
                            }
                            viewModel.saveTiles(specs)
                        },
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                val tiles = dragDropState.tiles
                val rows = tiles.chunked(4)
                rows.forEachIndexed { rowIndex, rowTiles ->
                    val paddingTop = if (rowIndex == 0) 4.dp else 0.dp
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = paddingTop),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        rowTiles.forEachIndexed { colIndex, tile ->
                            val index = rowIndex * 4 + colIndex
                            val isBeingDragged = dragDropState.isMoving(tile.spec)

                            Box(
                                modifier =
                                    Modifier.width(88.dp)
                                        .height(96.dp)
                                        .onGloballyPositioned { coords ->
                                            val posInRoot = coords.positionInRoot()
                                            val relativeOffset = posInRoot - activeGridOffset
                                            dragDropState.updateItemPosition(
                                                tile.spec,
                                                index,
                                                IntOffset(
                                                    relativeOffset.x.toInt(),
                                                    relativeOffset.y.toInt()
                                                ),
                                                coords.size,
                                            )
                                        }
                            ) {
                                if (isBeingDragged) {
                                    Box(
                                        modifier =
                                            Modifier.fillMaxSize()
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(
                                                    MaterialTheme.colorScheme.secondary.copy(
                                                        alpha = 0.3f
                                                    )
                                                )
                                    )
                                } else {
                                    Box(
                                        modifier =
                                            Modifier.fillMaxSize()
                                                .dragAndDropTileSource(
                                                    tile,
                                                    dragDropState,
                                                    DragType.Move,
                                                )
                                    ) {
                                        EditorQsTile(
                                            label = tile.label,
                                            spec = tile.spec,
                                            isAdded = true,
                                            onAction = {
                                                val removed = dragDropState.removeTile(tile.spec)
                                                if (removed != null) {
                                                    availableList.add(
                                                        AvailableTileData(
                                                            spec = removed.spec,
                                                            label = removed.label,
                                                        )
                                                    )
                                                    viewModel.saveTiles(dragDropState.tileSpecs())
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                        repeat(4 - rowTiles.size) {
                            Spacer(modifier = Modifier.width(88.dp).height(96.dp))
                        }
                    }
                }
                if (tiles.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(96.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(R.string.drag_tiles_here),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Box(
            modifier =
                Modifier.width(1.dp)
                    .fillMaxHeight()
                    .padding(vertical = 8.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.available_tiles),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                modifier = Modifier.padding(bottom = 12.dp).basicMarquee(),
            )

            Column(
                modifier =
                    Modifier.weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .dragAndDropAvailableZone(dragDropState) { spec: String ->
                            val removed = dragDropState.removeTile(spec)
                            if (removed != null) {
                                availableList.add(
                                    AvailableTileData(
                                        spec = removed.spec,
                                        label = removed.label,
                                    )
                                )
                                viewModel.saveTiles(dragDropState.tileSpecs())
                            }
                        },
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                val rows = availableList.chunked(4)
                rows.forEachIndexed { rowIndex, rowTiles ->
                    val paddingTop = if (rowIndex == 0) 4.dp else 0.dp
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = paddingTop),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        rowTiles.forEach { tile: AvailableTileData ->
                            val isBeingDragged = dragDropState.isMoving(tile.spec)

                            Box(
                                modifier =
                                    Modifier.width(88.dp)
                                        .height(96.dp)
                                        .graphicsLayer { alpha = if (isBeingDragged) 0.3f else 1f }
                                        .dragAndDropAvailableTileSource(tile, dragDropState)
                            ) {
                                EditorQsTile(
                                    label = tile.label,
                                    spec = tile.spec,
                                    isAdded = false,
                                    onAction = {
                                        availableList.remove(tile)
                                        dragDropState.addTile(
                                            QSTileData(
                                                spec = tile.spec,
                                                label = tile.label,
                                                secondaryLabel = "",
                                                state = 1,
                                                isTransient = false,
                                            )
                                        )
                                        viewModel.saveTiles(dragDropState.tileSpecs())
                                    },
                                )
                            }
                        }
                        repeat(4 - rowTiles.size) {
                            Spacer(modifier = Modifier.width(88.dp).height(96.dp))
                        }
                    }
                }
                if (availableList.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(96.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(R.string.all_tiles_added),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EditorQsTile(label: String, spec: String, isAdded: Boolean, onAction: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(modifier = Modifier.size(56.dp), contentAlignment = Alignment.Center) {
            Box(
                modifier =
                    Modifier.fillMaxSize()
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceBright)
                        .clickable { onAction() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    TileIconMapping.getIcon(spec),
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp),
                )
            }

            Box(
                modifier =
                    Modifier.size(20.dp)
                        .align(Alignment.TopEnd)
                        .offset(x = 2.dp, y = (-2).dp)
                        .clip(CircleShape)
                        .background(
                            if (isAdded) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary
                        )
                        .clickable { onAction() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (isAdded) Icons.Rounded.Remove else Icons.Rounded.Add,
                    contentDescription = if (isAdded) "Remove" else "Add",
                    tint =
                        if (isAdded) MaterialTheme.colorScheme.onError
                        else MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }

        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(80.dp).basicMarquee(),
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun PageIndicator(pageCount: Int, currentPage: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            val isSelected = index == currentPage
            val width by
                animateDpAsState(
                    targetValue = if (isSelected) 16.dp else 6.dp,
                    label = "indicator_width",
                )
            val alpha by
                animateFloatAsState(
                    targetValue = if (isSelected) 1f else 0.4f,
                    label = "indicator_alpha",
                )

            Box(
                modifier =
                    Modifier.width(width)
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
            )
        }
    }
}
