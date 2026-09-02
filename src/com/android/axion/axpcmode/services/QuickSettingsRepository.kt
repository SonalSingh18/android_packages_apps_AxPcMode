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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.BrightnessInfo
import android.hardware.display.DisplayManager
import android.media.AudioManager
import android.provider.Settings
import com.android.axion.compose.preferences.SettingsFlow
import com.android.axion.compose.preferences.SettingsType
import com.android.axion.platform.AxFeatureState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

data class QSTileData(
    val spec: String,
    val label: String,
    val secondaryLabel: String,
    val state: Int,
    val isTransient: Boolean,
)

data class AvailableTileData(
    val spec: String,
    val label: String,
)

class QuickSettingsRepository(
    val context: Context,
    private val platformRepo: AxPlatformRepository,
) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val displayManager =
        context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val systemSettings = SettingsFlow(context.contentResolver, SettingsType.SYSTEM)

    private val _brightnessInfo = MutableStateFlow<BrightnessInfo?>(null)
    val brightnessInfo = _brightnessInfo.asStateFlow()

    val isAutoBrightness = systemSettings
        .observeInt(Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        .map { it == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC }
        .onEach { _brightnessInfo.value = context.display?.brightnessInfo }
        .stateIn(
            scope,
            SharingStarted.Eagerly,
            systemSettings.getInt(
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
            ) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC,
        )

    private val _volume = MutableStateFlow(0f)
    val volume = _volume.asStateFlow()

    private val _maxVolume = MutableStateFlow(1f)
    val maxVolume = _maxVolume.asStateFlow()

    private val _qsTiles = MutableStateFlow<List<QSTileData>>(emptyList())
    val qsTiles = _qsTiles.asStateFlow()

    private val _availableTiles = MutableStateFlow<List<AvailableTileData>>(emptyList())
    val availableTiles = _availableTiles.asStateFlow()

    private var savedSpecs = platformRepo.getSavedTileSpecs()
    private val tileCollectorJobs = mutableListOf<Job>()

    private var userSlidingVolume = false
    private var volumeDebounceJob: Job? = null

    private var attachCount = 0
    private var started = false

    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action != "android.media.VOLUME_CHANGED_ACTION") return
            if (userSlidingVolume) return
            val stream = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1)
            if (stream == AudioManager.STREAM_MUSIC) {
                _volume.value = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
            }
        }
    }

    /**
     * Starts listening for state, if nobody is listening yet.
     *
     * This repository is a process wide singleton shared by the primary and secondary taskbars,
     * while the taskbars themselves are created and destroyed constantly (display changes, PC mode
     * toggles). Tying the subscriptions to a single taskbar's lifetime meant the first teardown
     * unregistered the platform callback for good: tile taps kept working, because toggling is a
     * plain binder call, but no state ever came back, so every tile stayed frozen at whatever it
     * last showed. Reference count the listeners instead so they come back with the next taskbar.
     */
    @Synchronized
    fun attach() {
        attachCount++
        if (started) return
        started = true

        _maxVolume.value = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()
        _volume.value = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
        _brightnessInfo.value = context.display?.brightnessInfo

        context.registerReceiver(
            volumeReceiver,
            IntentFilter("android.media.VOLUME_CHANGED_ACTION"),
            Context.RECEIVER_NOT_EXPORTED,
        )

        savedSpecs = platformRepo.getSavedTileSpecs()
        refreshTiles()
        collectTileStates()
    }

    /** Stops listening once the last taskbar hosting the panel is gone. */
    @Synchronized
    fun detach() {
        if (attachCount > 0) attachCount--
        if (attachCount > 0 || !started) return
        started = false

        volumeDebounceJob?.cancel()
        userSlidingVolume = false
        tileCollectorJobs.forEach { it.cancel() }
        tileCollectorJobs.clear()
        runCatching { context.unregisterReceiver(volumeReceiver) }
        platformRepo.disconnect()
    }

    private fun refreshTilesFromState() {
        val previous = _qsTiles.value.associateBy { it.spec }
        _qsTiles.value = savedSpecs.map { spec ->
            val state = platformRepo.getState(spec)
            if (state.isEmpty) {
                previous[spec] ?: stateToTileData(spec, state)
            } else {
                stateToTileData(spec, state)
            }
        }
    }

    fun refreshTiles() {
        refreshTilesFromState()
        refreshAvailableTiles()
    }

    private fun refreshAvailableTiles() {
        val supported = platformRepo.getSupportedFeatures().toSet()
        val activeSpecs = savedSpecs.toSet()
        _availableTiles.value = supported
            .filter { it !in activeSpecs }
            .map { spec ->
                val state = platformRepo.getState(spec)
                AvailableTileData(
                    spec = spec,
                    label = state.label ?: spec,
                )
            }
    }

    fun clickTile(spec: String) {
        platformRepo.toggle(spec)
    }

    fun saveTiles(specs: List<String>) {
        savedSpecs = specs
        platformRepo.saveTileSpecs(specs)
        collectTileStates()
        refreshTiles()
    }

    private fun collectTileStates() {
        tileCollectorJobs.forEach { it.cancel() }
        tileCollectorJobs.clear()
        savedSpecs.forEach { spec ->
            val job = platformRepo.stateFlow(spec)
                .onEach { refreshTilesFromState() }
                .launchIn(scope)
            tileCollectorJobs.add(job)
        }
    }

    fun setBrightness(linear: Float) {
        displayManager.setBrightness(context.display?.displayId ?: 0, linear)
    }

    fun setAutoBrightness(enabled: Boolean) {
        systemSettings.putInt(
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            if (enabled) Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
            else Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
        )
    }

    fun setVolume(volume: Float) {
        userSlidingVolume = true
        volumeDebounceJob?.cancel()
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume.toInt(), 0)
        _volume.value = volume
        volumeDebounceJob = scope.launch {
            delay(300)
            userSlidingVolume = false
        }
    }

    companion object {
        private fun stateToTileData(spec: String, state: AxFeatureState): QSTileData =
            QSTileData(
                spec = spec,
                label = state.label ?: spec,
                secondaryLabel = state.secondaryLabel ?: "",
                state = state.tileState,
                isTransient = false,
            )
    }
}
