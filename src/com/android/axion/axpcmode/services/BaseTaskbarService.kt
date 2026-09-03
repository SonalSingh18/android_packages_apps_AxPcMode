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

import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.IBinder
import android.os.UserHandle
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import android.view.inputmethod.InputMethodManager
import com.android.axion.axpcmode.activities.MousePadActivity
import com.android.axion.axpcmode.ui.*
import com.android.axion.axpcmode.ui.components.ContextMenuState
import com.android.axion.axpcmode.ui.components.taskbar.TaskPeekState
import com.android.axion.axpcmode.ui.windows.TaskbarIconRegistry
import com.android.axion.axpcmode.utils.TaskbarConstants
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

abstract class BaseTaskbarService : Service(), TaskbarHost {

    protected abstract val tag: String
    protected abstract val notificationId: Int
    protected abstract val startAction: String
    protected abstract val stopAction: String

    protected abstract fun resolveTargetDisplayId(): Int
    protected abstract fun onTaskbarShown()

    protected val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    protected val _isTaskbarVisible = MutableStateFlow(true)

    var targetDisplayId: Int = Display.DEFAULT_DISPLAY
        protected set
    protected val _displayDensityDpi = MutableStateFlow(0)
    val displayDensityDpi: StateFlow<Int> = _displayDensityDpi.asStateFlow()

    protected var autoHideJob: Job? = null
    protected var cleaned = false
    private var created = false

    protected val contextMenuState = ContextMenuState()
    protected val peekState = TaskPeekState()
    protected val taskbarIconRegistry = TaskbarIconRegistry()

    protected lateinit var panelOverlayManager: PanelOverlayManager
    protected lateinit var windowHelper: TaskbarWindowManager
    protected lateinit var interactor: TaskbarInteractor
    protected lateinit var panelController: TaskbarPanelController
    protected lateinit var notificationListener: AxNotificationListener
    protected lateinit var inputMethodManager: InputMethodManager

    abstract val vm: PcModeLauncherViewModel
    abstract val qsViewModel: QuickSettingsViewModel
    abstract val mediaRepository: MediaRepository
    abstract val calendarWeatherRepository: CalendarWeatherRepository
    abstract val notificationHelper: TaskbarNotificationHelper
    abstract val appRepository: TaskbarAppRepository

    private var densityDisplayListener: DisplayManager.DisplayListener? = null

    override fun onCreate() {
        super.onCreate()
        inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

        targetDisplayId = resolveTargetDisplayId()
        panelOverlayManager = PanelOverlayManager(this, targetDisplayId, displayDensityDpi)

        windowHelper = TaskbarWindowManager(this, targetDisplayId)
        interactor = TaskbarInteractor(this, vm, this, inputMethodManager, targetDisplayId)

        registerNotificationListener()

        appRepository.init()

        calendarWeatherRepository.start()

        qsViewModel.onAttach()

        panelController = TaskbarPanelController(
            context = this,
            vm = vm,
            scope = serviceScope,
            panelOverlayManager = panelOverlayManager,
            qsViewModel = qsViewModel,
            mediaRepository = mediaRepository,
            calendarWeatherRepository = calendarWeatherRepository,
            contextMenuState = contextMenuState,
            peekState = peekState,
            displayDensityDpi = displayDensityDpi,
            targetDisplayId = targetDisplayId,
        )
        panelController.init()

        serviceScope.launch {
            appRepository.topTaskPackage.collect { pkg -> resetAutoHideTimer(pkg) }
        }

        setupDensityListener(targetDisplayId)

        notificationHelper.createNotificationChannel()
        created = true
        Log.d(tag, "$tag created on display $targetDisplayId")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == null) {
            // START_STICKY re-delivery after the process was killed: restore the overlay,
            // otherwise the taskbar stays gone until PC mode is restarted.
            if (created) {
                cleaned = false
                startForeground(notificationId, notificationHelper.createForegroundNotification())
                showTaskbarOverlay()
                Log.d(tag, "$tag restarted without an intent, taskbar restored")
            }
            return START_STICKY
        }
        when (intent.action) {
            startAction -> {
                if (cleaned) {
                    cleaned = false
                }
                startForeground(notificationId, notificationHelper.createForegroundNotification())
                showTaskbarOverlay()
                Log.d(tag, "$tag started")
            }
            stopAction -> {
                cleaned = false
                cleanup()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                Log.d(tag, "$tag stopped")
            }
            TaskbarConstants.ACTION_SHOW -> revealTaskbar()
            TaskbarConstants.ACTION_HIDE -> hideTaskbarNow()
            TaskbarConstants.ACTION_TOGGLE_START_MENU -> vm.toggleStartMenu()
            TaskbarConstants.ACTION_TOGGLE_QUICK_SETTINGS -> vm.toggleQuickSettingsPanel()
            TaskbarConstants.ACTION_TOGGLE_NOTIFICATIONS -> vm.toggleNotificationPanel()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        cleanup()
        serviceScope.cancel()
        super.onDestroy()
        Log.d(tag, "$tag destroyed")
    }

    private fun showTaskbarOverlay() {
        if (windowHelper.getTaskbarView() != null) {
            windowHelper.removeTaskbarView()
            windowHelper.removeHintView()
        }

        val factory = TaskbarViewFactory(
            context = this,
            windowHelper = windowHelper,
            contextMenuState = contextMenuState,
            peekState = peekState,
            taskbarIconRegistry = taskbarIconRegistry,
            interactor = interactor,
            displayDensityDpi = displayDensityDpi,
            targetDisplayId = targetDisplayId,
        )

        val taskbarView = factory.createTaskbar(
            isVisibleFlow = _isTaskbarVisible.asStateFlow(),
            pinnedAppsFlow = vm.pinnedApps,
            runningAppsFlow = vm.runningApps,
        )
        val hintView = factory.createHint(isVisibleFlow = _isTaskbarVisible.asStateFlow())

        windowHelper.addTaskbarView(taskbarView)
        windowHelper.addHintView(hintView)

        MousePadActivity.cursorBringToFrontCallback?.invoke()

        serviceScope.launch {
            _isTaskbarVisible.collect { visible ->
                windowHelper.setTaskbarTouchable(visible)
                windowHelper.setHintTouchable(!visible)
            }
        }

        onTaskbarShown()
        resetAutoHideTimer()
    }

    override fun revealTaskbar() {
        _isTaskbarVisible.value = true
        resetAutoHideTimer()
    }

    override fun hideTaskbarImmediately() {
        autoHideJob?.cancel()
        _isTaskbarVisible.value = false
    }

    private fun hideTaskbarNow() {
        autoHideJob?.cancel()
        if (vm.isAnyPanelShowing()) {
            resetAutoHideTimer()
        } else {
            _isTaskbarVisible.value = false
        }
    }

    override fun resetAutoHideTimer() {
        resetAutoHideTimer(appRepository.topTaskPackage.value)
    }

    fun resetAutoHideTimer(topPkg: String?) {
        val isDesktop = topPkg == null || topPkg == packageName

        if (isDesktop) {
            autoHideJob?.cancel()
            _isTaskbarVisible.value = true
        } else {
            autoHideJob?.cancel()
            val timeoutMs = Settings.Secure.getInt(
                applicationContext.contentResolver,
                "ax_pc_mode_taskbar_timeout",
                TaskbarConstants.AUTO_HIDE_DELAY_MS.toInt()
            ).toLong()
            autoHideJob = serviceScope.launch {
                delay(timeoutMs)
                if (vm.isAnyPanelShowing()) {
                    resetAutoHideTimer()
                } else {
                    _isTaskbarVisible.value = false
                }
            }
        }
    }

    private fun registerNotificationListener() {
        notificationListener = AxNotificationListener()
        notificationListener.scope = serviceScope
        notificationListener.mediaRepository = mediaRepository
        notificationListener.registerAsSystemService(
            applicationContext,
            AxNotificationListener.componentName(applicationContext),
            UserHandle.USER_ALL,
        )
    }

    private fun unregisterNotificationListener() {
        try {
            notificationListener.unregisterAsSystemService()
        } catch (e: Exception) {
            Log.e(tag, "Failed to unregister notification listener", e)
        }
    }

    private fun setupDensityListener(targetDisplayId: Int) {
        val dm = getSystemService(DisplayManager::class.java) ?: return
        val display = dm.getDisplay(targetDisplayId) ?: return
        val metrics = DisplayMetrics()
        display.getRealMetrics(metrics)
        _displayDensityDpi.value = metrics.densityDpi

        densityDisplayListener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) {}
            override fun onDisplayRemoved(displayId: Int) {}
            override fun onDisplayChanged(displayId: Int) {
                if (displayId != targetDisplayId) return
                val d = dm.getDisplay(displayId) ?: return
                val m = DisplayMetrics()
                d.getRealMetrics(m)
                _displayDensityDpi.value = m.densityDpi
            }
        }
        dm.registerDisplayListener(densityDisplayListener, null)
    }

    protected open fun cleanup() {
        if (cleaned) return
        try {
            cleaned = true

            serviceScope.coroutineContext.cancelChildren()
            autoHideJob?.cancel()

            runCatching { appRepository.cleanup() }
            runCatching { panelOverlayManager.cleanup() }
            runCatching { mediaRepository.onDestroy() }
            runCatching { calendarWeatherRepository.stop() }
            runCatching { qsViewModel.onCleanup() }
            runCatching { unregisterNotificationListener() }

            val dm = getSystemService(DisplayManager::class.java)
            densityDisplayListener?.let { dm?.unregisterDisplayListener(it) }
            densityDisplayListener = null

            windowHelper.removeTaskbarView()
            windowHelper.removeHintView()
        } catch (e: Exception) {
        }
    }
}
