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

package com.android.axion.axpcmode.ui.components.taskbar

import android.graphics.Bitmap
import android.hardware.HardwareBuffer
import android.window.TaskSnapshotManager
import androidx.compose.runtime.*
import com.android.axion.axpcmode.utils.AppInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TaskPeekState {
    var peekedApp by mutableStateOf<AppInfo?>(null)
        private set
    var thumbnail by mutableStateOf<Bitmap?>(null)
        private set
    var anchorXPx by mutableFloatStateOf(0f)
        private set
    var isCardHovered by mutableStateOf(false)
        private set

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var dismissJob: Job? = null

    fun show(app: AppInfo, x: Float, bmp: Bitmap?) {
        dismissJob?.cancel()
        peekedApp = app
        anchorXPx = x
        thumbnail = bmp
    }

    fun enterCard() {
        dismissJob?.cancel()
        isCardHovered = true
    }

    fun leaveCard() {
        isCardHovered = false
        scheduleDismiss()
    }

    fun scheduleDismiss() {
        dismissJob?.cancel()
        dismissJob = scope.launch {
            delay(150)
            dismiss()
        }
    }

    fun dismiss() {
        dismissJob?.cancel()
        peekedApp = null
        thumbnail = null
        isCardHovered = false
    }
}

val LocalTaskPeekState = staticCompositionLocalOf { TaskPeekState() }

suspend fun fetchTaskThumbnail(taskId: Int): Bitmap? = withContext(Dispatchers.IO) {
    try {
        val snapshot = TaskSnapshotManager.getInstance().getTaskSnapshot(
            taskId,
            TaskSnapshotManager.convertRetrieveFlag(true),
        ) ?: return@withContext null
        val buffer = snapshot.hardwareBuffer ?: return@withContext null
        val bitmap = Bitmap.wrapHardwareBuffer(buffer, snapshot.colorSpace)
        buffer.close()
        bitmap
    } catch (e: Exception) {
        null
    }
}
