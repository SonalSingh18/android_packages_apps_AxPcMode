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

package com.android.axion.axpcmode.activities

import android.R
import android.app.ActivityManager
import android.app.ActivityOptions
import android.app.ActivityTaskManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.android.axion.axpcmode.services.DesktopWallpaperRepository
import com.android.axion.axpcmode.services.SecondaryTaskbarService
import com.android.axion.axpcmode.services.TaskbarService
import com.android.axion.axpcmode.ui.components.TasksOverviewPanel
import com.android.axion.axpcmode.ui.theme.AxPcModeTheme
import com.android.axion.axpcmode.utils.AppInfo
import com.android.axion.axpcmode.utils.AppUtils
import com.android.axion.axpcmode.utils.TaskbarConstants
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint(ComponentActivity::class)
class TasksOverviewActivity : Hilt_TasksOverviewActivity() {

    @Inject lateinit var wallpaperRepo: DesktopWallpaperRepository

    private val targetDisplayId: Int
        get() = display?.displayId ?: Display.DEFAULT_DISPLAY

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())

        window.setBackgroundDrawableResource(R.color.transparent)

        setContent {
            val runningTasks = remember { loadRunningTasks() }
            val wallpaperBitmap by wallpaperRepo.wallpaperBitmap.collectAsState()

            val tasksWithDesktop =
                remember(runningTasks) {
                    val desktopIcon =
                        try {
                            packageManager.getApplicationIcon("com.android.axion.axpcmode")
                        } catch (e: Exception) {
                            getDrawable(R.drawable.ic_menu_view)!!
                        }

                    val desktopCard =
                        AppInfo(
                            label = "Desktop",
                            packageName = "com.android.axion.axpcmode",
                            className = "PcModeLauncherActivity",
                            icon = desktopIcon,
                            taskId = -2,
                        )
                    listOf(desktopCard) + runningTasks
                }

            AxPcModeTheme {
                TasksOverviewPanel(
                    windows = emptyList(),
                    runningTasks = tasksWithDesktop,
                    wallpaperBitmap = wallpaperBitmap,
                    onTaskClick = {},
                    onRunningTaskClick = { appInfo ->
                        if (appInfo.taskId == -2) {

                            showDesktop()
                        } else {
                            bringTaskToFront(appInfo)
                        }
                        finish()
                    },
                    onTaskClose = { appInfo ->
                        if (appInfo.taskId > 0) {
                            closeTask(appInfo)
                        }
                    },
                    onClearAllTasks = {
                        clearAllTasks(loadRunningTasks())
                        dismissOverview()
                    },
                    onDismiss = { dismissOverview() },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    private fun loadRunningTasks(): List<AppInfo> {
        val pm = packageManager
        val tasks = mutableListOf<AppInfo>()

        try {
            val atm = ActivityTaskManager.getService()
            val runningTasks = atm.getTasks(20, false, false, targetDisplayId)

            runningTasks.forEach { taskInfo ->
                val componentName = taskInfo.baseActivity ?: taskInfo.topActivity
                if (componentName != null) {

                    if (componentName.packageName == packageName) return@forEach
                    if (isLauncherPackage(componentName.packageName)) return@forEach

                    try {
                        val appInfo = pm.getApplicationInfo(componentName.packageName, 0)
                        val label = pm.getApplicationLabel(appInfo).toString()
                        val icon = pm.getApplicationIcon(appInfo)

                        tasks.add(
                            AppInfo(
                                label = label,
                                packageName = componentName.packageName,
                                className = componentName.className,
                                icon = icon,
                                taskId = taskInfo.taskId,
                            )
                        )
                    } catch (e: PackageManager.NameNotFoundException) {}
                }
            }
        } catch (e: Exception) {
            Log.e("TasksOverviewActivity", "Failed to load running tasks", e)
        }

        return tasks
    }

    private fun isLauncherPackage(packageName: String): Boolean {
        return packageName in AppUtils.getHomePackages(this)
    }

    private fun bringTaskToFront(appInfo: AppInfo) {
        if (appInfo.taskId != -1) {
            try {
                val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                am.moveTaskToFront(appInfo.taskId, 0)
            } catch (e: Exception) {
                AppUtils.launchApp(this, appInfo.packageName, appInfo.className, targetDisplayId)
            }
        } else {
            AppUtils.launchApp(this, appInfo.packageName, appInfo.className, targetDisplayId)
        }
    }

    private fun closeTask(appInfo: AppInfo) {
        removeTask(appInfo.taskId, appInfo.packageName)
    }

    private fun clearAllTasks(tasks: List<AppInfo>) {
        tasks.forEach { task -> removeTask(task.taskId, task.packageName) }
    }

    /**
     * Removes a single recents entry. Only the task is dropped - force stopping the whole
     * package would also mark it stopped and tear down anything else running under it.
     */
    private fun removeTask(taskId: Int, packageName: String) {
        if (taskId <= 0) return
        try {
            ActivityTaskManager.getService().removeTask(taskId)
        } catch (e: Exception) {
            Log.e("TasksOverviewActivity", "Failed to remove task $taskId ($packageName)", e)
        }
    }

    /**
     * Closes the overview. Once the last task on this display is gone the desktop has to be
     * pulled back to the front explicitly, otherwise the system home activity becomes the top
     * task and the PC mode desktop plus its taskbar are left hidden behind it.
     */
    private fun dismissOverview() {
        if (loadRunningTasks().isEmpty()) {
            showDesktop()
        }
        revealTaskbar()
        finish()
    }

    private fun showDesktop() {
        try {
            val activityClass =
                if (targetDisplayId != Display.DEFAULT_DISPLAY)
                    SecondaryPcModeLauncherActivity::class.java
                else PcModeLauncherActivity::class.java
            val intent = Intent(this, activityClass)
            intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            startActivity(intent, displayOptions())
        } catch (e: Exception) {
            Log.e("TasksOverviewActivity", "Failed to show desktop", e)
        }
    }

    /** The taskbar is hidden while the overview is open, so ask it to come back. */
    private fun revealTaskbar() {
        try {
            val serviceClass =
                if (targetDisplayId != Display.DEFAULT_DISPLAY)
                    SecondaryTaskbarService::class.java
                else TaskbarService::class.java
            val intent = Intent(this, serviceClass).apply {
                action = TaskbarConstants.ACTION_SHOW
            }
            startService(intent)
        } catch (e: Exception) {
            Log.e("TasksOverviewActivity", "Failed to reveal taskbar", e)
        }
    }

    private fun displayOptions(): android.os.Bundle? {
        if (targetDisplayId == Display.DEFAULT_DISPLAY) return null
        return ActivityOptions.makeBasic().apply { launchDisplayId = targetDisplayId }.toBundle()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        dismissOverview()
    }
}
