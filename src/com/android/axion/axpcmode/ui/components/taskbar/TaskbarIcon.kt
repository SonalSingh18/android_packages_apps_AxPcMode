package com.android.axion.axpcmode.ui.components.taskbar

import android.app.ActivityManager
import android.app.ActivityTaskManager
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.delay
import com.android.axion.axpcmode.ui.components.ArrowDirection
import com.android.axion.axpcmode.ui.components.ContextMenuAction
import com.android.axion.axpcmode.ui.components.ContextMenuParams
import com.android.axion.axpcmode.ui.components.LocalContextMenuState
import com.android.axion.axpcmode.ui.components.LocalTargetDisplayId
import com.android.axion.axpcmode.ui.components.LocalWindowScreenOffset
import com.android.axion.axpcmode.ui.windows.LocalTaskbarIconRegistry
import com.android.axion.axpcmode.utils.AppInfo
import com.android.axion.axpcmode.utils.AppUtils
import com.android.axion.axpcmode.utils.TaskbarConstants
import com.android.axion.axpcmode.R

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TaskbarIcon(
    app: AppInfo,
    isRunning: Boolean,
    isPinned: Boolean = false,
    pinnedCount: Int = 0,
    onClick: () -> Unit,
) {
    val launcherContext = LocalContext.current
    val targetDisplayId = LocalTargetDisplayId.current
    val appIconBitmap = remember(app) { app.icon.toBitmap().asImageBitmap() }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.85f else 1f, label = "icon_scale")
    var iconCenterLocal by remember { mutableStateOf(Offset.Zero) }
    val contextMenuState = LocalContextMenuState.current
    val windowOffset = LocalWindowScreenOffset.current
    var isFocused by remember { mutableStateOf(false) }
    val focusColor = MaterialTheme.colorScheme.primary
    val focusBorderWidth by animateDpAsState(
        targetValue = if (isFocused) 2.dp else 0.dp,
        animationSpec = tween(150),
        label = "focus_border"
    )

    val registry = LocalTaskbarIconRegistry.current
    val peekState = LocalTaskPeekState.current
    var isHovered by remember { mutableStateOf(false) }

    LaunchedEffect(isHovered, app.taskId) {
        if (isHovered && app.taskId > 0) {
            delay(400)
            val bmp = fetchTaskThumbnail(app.taskId)
            peekState.show(app, iconCenterLocal.x, bmp)
        } else if (!isHovered) {
            peekState.scheduleDismiss()
        }
    }

    val strFloatingWindow = stringResource(R.string.action_floating_window)
    val strClose = stringResource(R.string.action_close_app)
    val strPin = stringResource(R.string.action_pin_to_taskbar)
    val strUnpin = stringResource(R.string.action_unpin_from_taskbar)
    val strAppInfo = stringResource(R.string.action_app_info)

    val actions =
        remember(app, isRunning, isPinned, pinnedCount, strFloatingWindow, strClose, strPin, strUnpin, strAppInfo) {
            buildList {
                add(
                    ContextMenuAction(label = strFloatingWindow, icon = Icons.Filled.Fullscreen) {
                        AppUtils.launchAppInFreeform(launcherContext, app.packageName, app.className, targetDisplayId)
                    }
                )

                if (isRunning) {
                    add(
                        ContextMenuAction(label = strClose, icon = Icons.Filled.Close) {
                            try {
                                if (app.taskId > 0) {
                                    ActivityTaskManager.getService().removeTask(app.taskId)
                                } else {
                                    val am =
                                        launcherContext.getSystemService(Context.ACTIVITY_SERVICE)
                                            as ActivityManager
                                    am.forceStopPackage(app.packageName)
                                }
                            } catch (e: Exception) {
                                Log.e("Taskbar", "Failed to close app", e)
                            }
                        }
                    )
                }

                val label = if (isPinned) strUnpin else strPin
                val icon = if (isPinned) Icons.Filled.Clear else Icons.Filled.PushPin

                add(
                    ContextMenuAction(label = label, icon = icon) {
                        if (isPinned) {
                            AppUtils.unpinApp(launcherContext, app.packageName)
                        } else {
                            AppUtils.pinApp(launcherContext, app.packageName)
                        }
                        launcherContext.sendBroadcast(
                            Intent(TaskbarConstants.ACTION_REFRESH_PINNED_APPS)
                        )
                    }
                )

                add(
                    ContextMenuAction(label = strAppInfo, icon = Icons.Filled.Info) {
                        AppUtils.launchAppInfo(launcherContext, app.packageName, launcherContext.displayId)
                    }
                )
            }
        }

    val showContextMenu: () -> Unit = {
        val screenPosition = Offset(
            iconCenterLocal.x + windowOffset.x,
            iconCenterLocal.y + windowOffset.y,
        )
        contextMenuState.show(
            ContextMenuParams(
                anchorPosition = screenPosition,
                direction = ArrowDirection.DOWN,
                actions = actions,
            )
        )
        Log.d("AxPcMode_UI", "TaskbarIcon: ${app.label} onLongClick triggered")
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxHeight()
            .width(48.dp)
            .pointerInput(app.packageName) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        when (event.type) {
                            PointerEventType.Enter -> isHovered = true
                            PointerEventType.Exit -> isHovered = false
                        }
                    }
                }
            }
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.Enter, Key.DirectionCenter, Key.ButtonA -> {
                            onClick()
                            true
                        }
                        Key.Menu, Key.ButtonX -> {
                            showContextMenu()
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .focusable()
            .then(
                if (isFocused) {
                    Modifier
                        .border(focusBorderWidth, focusColor, RoundedCornerShape(8.dp))
                        .padding(2.dp)
                } else {
                    Modifier.padding(2.dp)
                }
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = showContextMenu,
                interactionSource = interactionSource,
                indication = ripple(bounded = false, radius = 24.dp),
            )
            .onGloballyPositioned { coords ->
                val pos = coords.positionInWindow()
                val anchorX = pos.x + coords.size.width / 2f
                val anchorY = pos.y
                iconCenterLocal = Offset(anchorX, anchorY)
                registry.updatePosition(
                    app.packageName,
                    Offset(anchorX, pos.y + coords.size.height / 2f),
                )
            },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier =
                    Modifier.size(36.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .clip(RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = BitmapPainter(appIconBitmap),
                    contentDescription = app.label,
                    modifier = Modifier.size(26.dp),
                )
            }

            if (isRunning) {
                Spacer(modifier = Modifier.height(1.dp))
                Box(
                    modifier =
                        Modifier.width(4.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}
