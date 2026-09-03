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

import android.app.Notification
import android.app.PendingIntent
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Notifications
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.graphics.drawable.toBitmap
import com.android.axion.axpcmode.services.NotificationGroup
import com.android.axion.axpcmode.services.NotificationRepository
import com.android.compose.animation.scene.ContentScope
import com.android.compose.animation.scene.SceneKey
import com.android.compose.animation.scene.SceneTransitionLayout
import com.android.compose.animation.scene.rememberMutableSceneTransitionLayoutState
import com.android.compose.animation.scene.transitions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private object NotifScenes {
    val Collapsed = SceneKey("NotifCollapsed")
    val Expanded = SceneKey("NotifExpanded")
}

/** Footprint of the panel, trailing padding included; the overlay window is sized around it. */
val NotificationPanelWidth = 360.dp

private const val NOTIF_SPRING_STIFFNESS = 500f
private const val NOTIF_SPRING_DAMPING = 0.9f

private val notifTransitions = transitions {
    from(NotifScenes.Collapsed, to = NotifScenes.Expanded) {
        spec = spring(stiffness = NOTIF_SPRING_STIFFNESS, dampingRatio = NOTIF_SPRING_DAMPING)
    }
}

@Composable
fun NotificationPanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val safeMaxHeight = (configuration.screenHeightDp - 80).dp

    val groups =
        remember { derivedStateOf { NotificationRepository.getGroupedNotifications() } }.value

    Surface(
        modifier =
            modifier
                .width(NotificationPanelWidth)
                .heightIn(max = safeMaxHeight)
                .padding(top = 12.dp, bottom = 12.dp, end = 12.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 8.dp,
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Notifications",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                val clearInteractionSource = remember { MutableInteractionSource() }
                val clearPressed by clearInteractionSource.collectIsPressedAsState()
                val clearScale by
                    animateFloatAsState(
                        if (clearPressed) 0.9f else 1f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label = "clear_scale",
                    )

                Box(
                    modifier =
                        Modifier.size(32.dp)
                            .graphicsLayer {
                                scaleX = clearScale
                                scaleY = clearScale
                            }
                            .clickable(
                                interactionSource = clearInteractionSource,
                                indication = null,
                            ) {
                                NotificationRepository.clearAll()
                            },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "Clear All",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            if (groups.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "No notifications",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(items = groups, key = { it.groupKey }) { group ->
                        NotificationGroupItem(group = group, modifier = Modifier.animateItem())
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationGroupItem(group: NotificationGroup, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var isExpanded by remember { mutableStateOf(group.children.size <= 2) }

    val hasClearableNotifications =
        remember(group.children) { group.children.any { it.isClearable } }

    val appInfo =
        remember(group.packageName) {
            try {
                val pm = context.packageManager
                val appInfo = pm.getApplicationInfo(group.packageName, 0)
                Pair(
                    pm.getApplicationLabel(appInfo).toString(),
                    pm.getApplicationIcon(appInfo).toBitmap().asImageBitmap(),
                )
            } catch (e: Exception) {
                Pair(group.packageName, null)
            }
        }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceBright),
        modifier =
            modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (group.children.size > 1) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier.fillMaxWidth()
                            .clickable { isExpanded = !isExpanded }
                            .padding(bottom = 8.dp),
                ) {
                    if (appInfo.second != null) {
                        Image(
                            bitmap = appInfo.second!!,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp).clip(CircleShape),
                        )
                    } else {
                        Box(
                            modifier =
                                Modifier.size(20.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.Notifications,
                                null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }

                    Spacer(Modifier.width(8.dp))

                    Text(
                        text = appInfo.first,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )

                    Text(
                        text = "${group.children.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(Modifier.width(4.dp))

                    Icon(
                        imageVector =
                            if (isExpanded) Icons.Default.KeyboardArrowUp
                            else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )

                    if (hasClearableNotifications) {
                        Spacer(Modifier.width(4.dp))

                        val dismissInteractionSource = remember { MutableInteractionSource() }
                        val dismissPressed by dismissInteractionSource.collectIsPressedAsState()
                        val dismissScale by
                            animateFloatAsState(
                                targetValue = if (dismissPressed) 0.8f else 1f,
                                animationSpec =
                                    spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                label = "dismiss_scale",
                            )

                        Box(
                            modifier =
                                Modifier.size(20.dp)
                                    .graphicsLayer {
                                        scaleX = dismissScale
                                        scaleY = dismissScale
                                    }
                                    .clickable(
                                        interactionSource = dismissInteractionSource,
                                        indication = null,
                                    ) {
                                        NotificationRepository.dismissGroup(group.groupKey)
                                    },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.Close,
                                null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(bottom = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
            }

            AnimatedVisibility(
                visible = isExpanded || group.children.size == 1,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    group.children.forEach { sbn ->
                        NotificationChildItem(sbn = sbn, showAppIcon = group.children.size == 1)
                    }
                }
            }

            if (!isExpanded && group.children.size > 1) {
                val latestNotif = group.children.first()
                val title =
                    latestNotif.notification.extras.getString(Notification.EXTRA_TITLE)
                        ?: ""
                Text(
                    text = "$title and ${group.children.size - 1} more",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private data class NotifContent(
    val appName: String,
    val launcherIcon: androidx.compose.ui.graphics.ImageBitmap?,
    val rawTitle: String,
    val senderName: String?,
    val displayText: String,
    val senderAvatar: androidx.compose.ui.graphics.ImageBitmap?,
    val largeIcon: androidx.compose.ui.graphics.ImageBitmap?,
    val actions: List<Notification.Action>,
    val timestamp: String,
    val progress: Int,
    val progressMax: Int,
    val progressIndeterminate: Boolean,
    val isClearable: Boolean,
)

@Composable
private fun rememberNotifContent(sbn: StatusBarNotification): NotifContent {
    val context = LocalContext.current
    return remember(sbn) {
        val extras = sbn.notification.extras
        val appName = try {
            val info = context.packageManager.getApplicationInfo(sbn.packageName, 0)
            context.packageManager.getApplicationLabel(info).toString()
        } catch (_: Exception) { sbn.packageName.substringAfterLast('.') }

        val launcherIcon = try {
            context.packageManager.getApplicationIcon(sbn.packageName).toBitmap().asImageBitmap()
        } catch (_: Exception) { null }

        val isMessaging = sbn.notification.isStyle(Notification.MessagingStyle::class.java)
        val messages = if (isMessaging) {
            try {
                val arr = extras.getParcelableArray(Notification.EXTRA_MESSAGES, android.os.Parcelable::class.java)
                arr?.let { Notification.MessagingStyle.Message.getMessagesFromBundleArray(it) }
            } catch (_: Exception) { null }
        } else null
        val lastMessage = messages?.maxByOrNull { it.timestamp }

        val senderName = lastMessage?.senderPerson?.name?.toString()
        val senderAvatar = try {
            lastMessage?.senderPerson?.icon?.loadDrawable(context)?.toBitmap()?.asImageBitmap()
        } catch (_: Exception) { null }

        val displayText = lastMessage?.text?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.takeIf { it.isNotEmpty() }
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: ""

        val largeIcon = try {
            val icon = extras.getParcelable(Notification.EXTRA_LARGE_ICON, Bitmap::class.java)
                ?: extras.getParcelable(Notification.EXTRA_LARGE_ICON, Icon::class.java)
                    ?.loadDrawable(context)?.toBitmap()
            icon?.asImageBitmap()
        } catch (_: Exception) { null }

        val actions = sbn.notification.actions
            ?.filter { a -> a.remoteInputs.isNullOrEmpty() && a.actionIntent != null && a.title != null }
            ?.take(3)
            ?: emptyList()

        NotifContent(
            appName = appName,
            launcherIcon = launcherIcon,
            rawTitle = extras.getString(Notification.EXTRA_TITLE) ?: "",
            senderName = senderName,
            displayText = displayText,
            senderAvatar = senderAvatar,
            largeIcon = largeIcon,
            actions = actions,
            timestamp = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(sbn.postTime)),
            progress = extras.getInt(Notification.EXTRA_PROGRESS, -1),
            progressMax = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0),
            progressIndeterminate = extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false),
            isClearable = sbn.isClearable,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationChildItem(
    sbn: StatusBarNotification,
    showAppIcon: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val data = rememberNotifContent(sbn)
    val scope = rememberCoroutineScope()
    val hasExpandableContent = data.actions.isNotEmpty() || data.displayText.length > 60

    val stlState = rememberMutableSceneTransitionLayoutState(
        initialScene = NotifScenes.Collapsed,
        transitions = notifTransitions,
    )

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (data.isClearable && (value == SwipeToDismissBoxValue.EndToStart || value == SwipeToDismissBoxValue.StartToEnd)) {
                NotificationRepository.dismiss(sbn)
                true
            } else false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            if (data.isClearable) {
                val backgroundColor by animateColorAsState(
                    targetValue = when (dismissState.targetValue) {
                        SwipeToDismissBoxValue.EndToStart,
                        SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.errorContainer
                        else -> Color.Transparent
                    },
                    label = "swipe_bg_color",
                )
                Box(
                    modifier = Modifier.fillMaxSize()
                        .background(backgroundColor, RoundedCornerShape(8.dp))
                        .padding(horizontal = 16.dp),
                    contentAlignment = if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd)
                        Alignment.CenterStart else Alignment.CenterEnd,
                ) {
                    Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        },
        enableDismissFromStartToEnd = data.isClearable,
        enableDismissFromEndToStart = data.isClearable,
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceBright),
        ) {
            val lifecycleOwner = LocalLifecycleOwner.current
            val noOpBackDispatcherOwner = remember(lifecycleOwner) {
                object : OnBackPressedDispatcherOwner {
                    override val lifecycle = lifecycleOwner.lifecycle
                    override val onBackPressedDispatcher = OnBackPressedDispatcher()
                }
            }
            CompositionLocalProvider(LocalOnBackPressedDispatcherOwner provides noOpBackDispatcherOwner) {
                SceneTransitionLayout(state = stlState) {
                    scene(NotifScenes.Collapsed) {
                        NotifScene(
                            sbn = sbn,
                            data = data,
                            showAppIcon = showAppIcon,
                            expanded = false,
                            hasExpandableContent = hasExpandableContent,
                            onToggle = { stlState.setTargetScene(NotifScenes.Expanded, scope) },
                        )
                    }
                    scene(NotifScenes.Expanded) {
                        NotifScene(
                            sbn = sbn,
                            data = data,
                            showAppIcon = showAppIcon,
                            expanded = true,
                            hasExpandableContent = hasExpandableContent,
                            onToggle = { stlState.setTargetScene(NotifScenes.Collapsed, scope) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContentScope.NotifScene(
    sbn: StatusBarNotification,
    data: NotifContent,
    showAppIcon: Boolean,
    expanded: Boolean,
    hasExpandableContent: Boolean,
    onToggle: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .clickable {
                try {
                    sbn.notification.contentIntent?.send()
                } catch (e: PendingIntent.CanceledException) {
                    Log.w("NotificationPanel", "Content intent canceled", e)
                } catch (e: Exception) {
                    Log.e("NotificationPanel", "Failed to open notification", e)
                }
            }
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        if (showAppIcon) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (data.launcherIcon != null) {
                    Image(
                        bitmap = data.launcherIcon,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp).clip(RoundedCornerShape(3.dp)),
                    )
                } else {
                    Icon(Icons.Default.Notifications, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp))
                }
                Text(
                    text = data.appName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = data.timestamp,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                if (data.isClearable) {
                    NotifDismissButton { NotificationRepository.dismiss(sbn) }
                }
            }
            Spacer(Modifier.height(6.dp))
        }

        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            val avatar = data.senderAvatar ?: data.launcherIcon
            val isRound = data.senderAvatar != null
            Box {
                if (avatar != null) {
                    Image(
                        bitmap = avatar,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp)
                            .clip(if (isRound) CircleShape else RoundedCornerShape(6.dp)),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(
                        modifier = Modifier.size(32.dp).clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.Notifications, null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp))
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val displayTitle = data.senderName ?: data.rawTitle.ifEmpty { data.appName }
                    Text(
                        text = displayTitle,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (!showAppIcon) {
                        Text(
                            text = " · ${data.timestamp}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                        )
                    }
                }

                if (data.displayText.isNotEmpty()) {
                    Text(
                        text = data.displayText,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = if (expanded) 6 else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (data.progressIndeterminate) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                } else if (data.progress >= 0 && data.progressMax > 0) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { data.progress.toFloat() / data.progressMax },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
            }

            if (data.largeIcon != null && data.senderAvatar == null) {
                Image(
                    bitmap = data.largeIcon,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Crop,
                )
            }

            Box {
                if (hasExpandableContent) {
                    NotifExpandButton(
                        expanded = expanded,
                        onClick = onToggle,
                    )
                } else if (!showAppIcon && data.isClearable) {
                    NotifDismissButton { NotificationRepository.dismiss(sbn) }
                }
            }
        }

        if (expanded && data.actions.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                data.actions.take(3).forEach { action ->
                    Surface(
                        onClick = {
                            try { action.actionIntent?.send() }
                            catch (_: PendingIntent.CanceledException) {}
                            catch (_: Exception) {}
                        },
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.weight(1f).height(28.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = action.title.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotifExpandButton(expanded: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "expand_scale",
    )
    Box(
        modifier = Modifier.size(20.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun NotifDismissButton(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.8f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "dismiss_scale",
    )
    Box(
        modifier = Modifier.size(20.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.Close, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp))
    }
}
