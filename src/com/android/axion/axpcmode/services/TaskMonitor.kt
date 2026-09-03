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

package com.android.axion.axpcmode.services

import android.app.ActivityManager
import android.app.ActivityManager.RunningTaskInfo
import android.app.ActivityTaskManager
import android.app.TaskStackListener
import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.android.axion.axpcmode.di.Background
import com.android.axion.axpcmode.di.IoScope
import com.android.axion.axpcmode.utils.AppInfo
import com.android.axion.axpcmode.utils.AppUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.withContext

@Singleton
class TaskMonitor
@Inject
constructor(
    @ApplicationContext private val context: Context,
    @IoScope private val applicationScope: CoroutineScope,
    @Background private val backgroundDispatcher: CoroutineDispatcher,
) {
    private val activityTaskManager = ActivityTaskManager.getService()
    private val packageManager = context.packageManager
    private val activityManager =
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    val topTaskPackage: Flow<String?> =
        callbackFlow {
                val listener =
                    object : TaskStackListener() {
                        override fun onTaskMovedToFront(taskInfo: RunningTaskInfo?) {
                            trySend(Unit)
                        }

                        override fun onTaskStackChanged() {
                            trySend(Unit)
                        }
                    }
                activityTaskManager.registerTaskStackListener(listener)
                trySend(Unit)
                awaitClose { activityTaskManager.unregisterTaskStackListener(listener) }
            }
            .conflate()
            .map { getTopTaskPackage() }
            .shareIn(applicationScope, SharingStarted.Lazily, replay = 1)

    val runningTasks: Flow<List<AppInfo>> =
        callbackFlow {
                val listener =
                    object : TaskStackListener() {
                        override fun onTaskStackChanged() {
                            trySend(Unit)
                        }

                        override fun onTaskCreated(taskId: Int, componentName: ComponentName?) {
                            trySend(Unit)
                        }

                        override fun onTaskRemoved(taskId: Int) {
                            trySend(Unit)
                        }

                        override fun onTaskMovedToFront(taskInfo: RunningTaskInfo?) {
                            trySend(Unit)
                        }
                    }
                activityTaskManager.registerTaskStackListener(listener)
                trySend(Unit)
                awaitClose { activityTaskManager.unregisterTaskStackListener(listener) }
            }
            .conflate()
            .map { getRunningApps() }
            .shareIn(applicationScope, SharingStarted.Lazily, replay = 1)

    private suspend fun getTopTaskPackage(): String? =
        withContext(backgroundDispatcher) {
            try {
                val tasks = activityManager.getRunningTasks(1)
                tasks.firstOrNull()?.topActivity?.packageName
            } catch (e: Exception) {
                null
            }
        }

    private suspend fun getRunningApps(): List<AppInfo> =
        withContext(backgroundDispatcher) {
            try {
                val tasks = activityManager.getRunningTasks(50)
                tasks.mapNotNull { task ->
                    val pkg = task.baseActivity?.packageName ?: return@mapNotNull null
                    if (pkg == context.packageName || pkg in AppUtils.getHomePackages(context))
                        return@mapNotNull null

                    try {
                        val appInfo = packageManager.getApplicationInfo(pkg, 0)
                        AppInfo(
                            packageName = pkg,
                            className = task.baseActivity?.className ?: "",
                            label = packageManager.getApplicationLabel(appInfo).toString(),
                            icon = packageManager.getApplicationIcon(appInfo),
                            taskId = task.id,
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
            } catch (e: Exception) {
                Log.e("TaskMonitor", "Error getting running apps", e)
                emptyList()
            }
        }
}
