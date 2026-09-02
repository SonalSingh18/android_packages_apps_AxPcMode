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

import android.app.WallpaperManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.util.Log
import android.window.TaskSnapshotManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.res.stringResource
import com.android.axion.axpcmode.ui.windows.WindowState
import com.android.axion.axpcmode.utils.AppInfo
import com.android.axion.axpcmode.R

@Composable
fun TasksOverviewPanel(
    windows: List<WindowState>,
    runningTasks: List<AppInfo> = emptyList(),
    onTaskClick: (WindowState) -> Unit,
    onRunningTaskClick: (AppInfo) -> Unit = {},
    onTaskClose: (AppInfo) -> Unit = {},
    onClearAllTasks: () -> Unit = {},
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp.dp
    val screenHeightDp = configuration.screenHeightDp.dp

    var wallpaperBitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(Unit) {
        val wallpaperManager = WallpaperManager.getInstance(context)
        val drawable = wallpaperManager.drawable
        if (drawable != null) {
            wallpaperBitmap = drawable.toBitmap()
        }
    }

    val taskSnapshots = remember { mutableStateMapOf<Int, Bitmap?>() }
    LaunchedEffect(runningTasks) {
        val snapshots = TaskSnapshotManager.getInstance()
        runningTasks.forEach { task ->
            if (task.taskId != -1 && !taskSnapshots.containsKey(task.taskId)) {
                try {
                    val snapshot = snapshots.getTaskSnapshot(
                        task.taskId,
                        TaskSnapshotManager.convertRetrieveFlag(false),
                    )
                    val bitmap =
                        snapshot?.let { s ->
                            try {
                                Bitmap.wrapHardwareBuffer(s.hardwareBuffer, s.colorSpace)
                                    ?.copy(Bitmap.Config.ARGB_8888, false)
                            } catch (e: Exception) {
                                null
                            }
                        }
                    taskSnapshots[task.taskId] = bitmap
                } catch (e: Exception) {
                    Log.e("TasksOverview", "Failed to get snapshot for task ${task.taskId}", e)
                    taskSnapshots[task.taskId] = null
                }
            }
        }
    }

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val animatedScale by
        animateFloatAsState(
            targetValue = if (visible) 1f else 0.9f,
            animationSpec = tween(durationMillis = 300),
            label = "overviewScale",
        )
    val animatedAlpha by
        animateFloatAsState(
            targetValue = if (visible) 1f else 0f,
            animationSpec = tween(durationMillis = 250),
            label = "overviewAlpha",
        )

    val wallpaperPadding = 24.dp
    val wallpaperCornerRadius = 20.dp
    val scaleFactor = 0.28f

    val hasContent = runningTasks.isNotEmpty()

    Box(modifier = modifier.fillMaxSize().background(Color(0xFF1A1A2E))) {
        Box(
            modifier =
                Modifier.fillMaxSize()
                    .padding(wallpaperPadding)
                    .clip(RoundedCornerShape(wallpaperCornerRadius))
                    .background(Color(0xFF0F0F23))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    )
        ) {
            if (wallpaperBitmap != null) {
                Image(
                    bitmap = wallpaperBitmap!!.asImageBitmap(),
                    contentDescription = stringResource(R.string.desktop_wallpaper_desc),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    alpha = animatedAlpha * 0.85f,
                )
            }

            Box(
                modifier =
                    Modifier.fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.25f * animatedAlpha))
            )

            if (!hasContent) {

                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Surface(
                            shape = CircleShape,
                            color =
                                MaterialTheme.colorScheme.surfaceContainerHighest.copy(
                                    alpha = 0.5f
                                ),
                            modifier = Modifier.size(80.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.ClearAll,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier.size(40.dp),
                                )
                            }
                        }
                        Text(
                            text = stringResource(R.string.task_overview_no_active_tasks),
                            color = Color.White.copy(alpha = 0.7f * animatedAlpha),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                }
            } else {

                var dismissedTasks by remember { mutableStateOf(setOf<Int>()) }

                Box(modifier = Modifier.fillMaxSize()) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 220.dp),
                        modifier =
                            Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 24.dp),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement =
                            Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        items(
                            items = runningTasks.filter { it.taskId !in dismissedTasks },
                            key = { it.taskId },
                        ) { task ->
                            val itemIndex = runningTasks.indexOf(task)
                            var isVisible by remember { mutableStateOf(false) }

                            LaunchedEffect(task.taskId) {
                                kotlinx.coroutines.delay(itemIndex * 50L)
                                isVisible = true
                            }

                            AnimatedVisibility(
                                visible = isVisible,
                                enter =
                                    scaleIn(
                                        initialScale = 0.8f,
                                        animationSpec =
                                            spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessMedium,
                                            ),
                                    ) + fadeIn(animationSpec = tween(200)),
                                exit =
                                    scaleOut(
                                        targetScale = 0.6f,
                                        animationSpec =
                                            spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessMedium,
                                            ),
                                    ) + fadeOut(animationSpec = tween(200)),
                            ) {
                                val taskSnapshot = taskSnapshots[task.taskId]
                                val freeformWindow =
                                    windows.find { it.packageName == task.packageName }

                                val isPcModeApp = task.packageName == context.packageName
                                val snapshot =
                                    if (isPcModeApp) wallpaperBitmap
                                    else (taskSnapshot ?: freeformWindow?.snapshot)

                                TaskGridItem(
                                    task = task,
                                    snapshot = snapshot,
                                    alpha = animatedAlpha,
                                    scale = animatedScale,
                                    onClick = { onRunningTaskClick(task) },
                                    onClose = {
                                        dismissedTasks = dismissedTasks + task.taskId
                                        onTaskClose(task)
                                    },
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp)) {
                        val clearInteractionSource = remember { MutableInteractionSource() }
                        val clearPressed by clearInteractionSource.collectIsPressedAsState()
                        val clearScale by
                            animateFloatAsState(
                                targetValue = if (clearPressed) 0.95f else 1f,
                                animationSpec =
                                    spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                label = "clear_scale",
                            )

                        FilledTonalButton(
                            onClick = onClearAllTasks,
                            interactionSource = clearInteractionSource,
                            colors =
                                ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceBright,
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                ),
                            shape = RoundedCornerShape(24.dp),
                            modifier =
                                Modifier.graphicsLayer {
                                    scaleX = clearScale
                                    scaleY = clearScale
                                },
                        ) {
                            Icon(
                                imageVector = Icons.Default.ClearAll,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.task_overview_clear_all), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskGridItem(
    task: AppInfo,
    snapshot: Bitmap? = null,
    alpha: Float,
    scale: Float,
    onClick: () -> Unit,
    onClose: () -> Unit = {},
) {
    val iconBitmap = remember(task.icon) { task.icon.toBitmap() }
    val iconSize = 44.dp
    val previewWidth = 200.dp
    val previewHeight = 125.dp

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by
        animateFloatAsState(
            targetValue = if (isPressed) 0.95f else 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
            label = "task_press_scale",
        )

    Column(
        modifier =
            Modifier.graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                }
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                )
                .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.width(previewWidth).height(previewHeight)) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                if (snapshot != null) {

                    Image(
                        bitmap = snapshot.asImageBitmap(),
                        contentDescription = task.label,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        alpha = alpha,
                    )
                } else {

                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Image(
                            bitmap = iconBitmap.asImageBitmap(),
                            contentDescription = task.label,
                            modifier = Modifier.size(56.dp),
                            alpha = 0.5f * alpha,
                        )
                    }
                }
            }

            val closeInteractionSource = remember { MutableInteractionSource() }
            val closePressed by closeInteractionSource.collectIsPressedAsState()
            val closeScale by
                animateFloatAsState(
                    targetValue = if (closePressed) 0.85f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "close_scale",
                )

            FilledIconButton(
                onClick = onClose,
                interactionSource = closeInteractionSource,
                modifier =
                    Modifier.align(Alignment.TopEnd)
                        .offset(x = 8.dp, y = (-8).dp)
                        .size(28.dp)
                        .graphicsLayer {
                            scaleX = closeScale
                            scaleY = closeScale
                        },
                colors =
                    IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceBright,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.task_overview_close_task_format, task.label),
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Surface(
            modifier = Modifier.size(iconSize),
            shape = RoundedCornerShape(12.dp),
            color = Color.Transparent,
        ) {
            Image(
                bitmap = iconBitmap.asImageBitmap(),
                contentDescription = task.label,
                modifier = Modifier.fillMaxSize(),
                alpha = alpha,
            )
        }
    }
}