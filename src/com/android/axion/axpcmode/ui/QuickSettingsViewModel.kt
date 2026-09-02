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

package com.android.axion.axpcmode.ui

import android.os.SystemProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.axion.axpcmode.services.QuickSettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.*

@Singleton
class QuickSettingsViewModel @Inject constructor(
    private val repository: QuickSettingsRepository,
) : ViewModel() {

    val isAutoBrightness = repository.isAutoBrightness
    val volume = repository.volume
    val maxVolume = repository.maxVolume

    val qsTiles = repository.qsTiles

    fun clickQSTile(spec: String) = repository.clickTile(spec)

    private val _activeDetailId = MutableStateFlow<String?>(null)
    val activeDetailId = _activeDetailId.asStateFlow()

    private val _isEditMode = MutableStateFlow(false)
    val isEditMode = _isEditMode.asStateFlow()

    val availableTiles = repository.availableTiles

    private val _sliderPosition = MutableStateFlow(0.5f)
    val sliderPosition = _sliderPosition.asStateFlow()

    private var userIsAdjustingBrightness = false

    private val disableGammaConversion =
        SystemProperties.getBoolean("sys.brightness.disable_gamma_conversion", false)

    init {
        repository.brightnessInfo
            .onEach { info ->
                if (!userIsAdjustingBrightness) {
                    info?.let {
                        _sliderPosition.value =
                            convertLinearToGammaFloat(
                                    it.brightness,
                                    it.brightnessMinimum,
                                    it.brightnessMaximum,
                                )
                                .toFloat() / 65535f
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    fun toggleEditMode() {
        if (!_isEditMode.value) {
            repository.refreshTiles()
        }
        _isEditMode.value = !_isEditMode.value
    }

    fun addTile(spec: String) {
        val current = qsTiles.value.map { it.spec }.toMutableList()
        if (!current.contains(spec)) {
            current.add(spec)
            repository.saveTiles(current)
        }
    }

    fun removeTile(spec: String) {
        val current = qsTiles.value.map { it.spec }.toMutableList()
        if (current.remove(spec)) {
            repository.saveTiles(current)
        }
    }

    fun moveTile(fromSpec: String, toSpec: String) {
        val current = qsTiles.value.map { it.spec }.toMutableList()
        val fromIndex = current.indexOf(fromSpec)
        val toIndex = current.indexOf(toSpec)

        if (fromIndex != -1) {
            if (toIndex != -1 && fromIndex != toIndex) {
                current.removeAt(fromIndex)
                current.add(toIndex, fromSpec)
                repository.saveTiles(current)
            }
        } else if (toIndex != -1) {
            if (!current.contains(fromSpec)) {
                current.add(toIndex, fromSpec)
                repository.saveTiles(current)
            }
        }
    }

    fun saveTiles(specs: List<String>) {
        repository.saveTiles(specs)
    }

    fun updateBrightness(position: Float) {
        _sliderPosition.value = position
        userIsAdjustingBrightness = true

        val info = repository.brightnessInfo.value ?: return
        val linear =
            convertGammaToLinearFloat(
                    (position * 65535f).toInt(),
                    info.brightnessMinimum,
                    info.brightnessMaximum,
                )
                .coerceIn(0f, 1f)

        repository.setBrightness(linear)
    }

    fun finishBrightnessAdjustment() {
        userIsAdjustingBrightness = false
    }

    fun toggleAutoBrightness() {
        repository.setAutoBrightness(!isAutoBrightness.value)
    }

    fun updateVolume(newVol: Float) {
        repository.setVolume(newVol)
    }

    private fun convertLinearToGammaFloat(valFloat: Float, min: Float, max: Float): Int {
        if (min == max) return 0
        val normalizedVal = ((valFloat - min) / (max - min)).coerceIn(0f, 1f)
        if (disableGammaConversion) return Math.round(normalizedVal * 65535f)
        val r = 0.5f
        val a = 0.17883277f
        val b = 0.28466892f
        val c = 0.55991073f
        val hlgVal = normalizedVal * 12
        val ret =
            if (hlgVal <= 1f) kotlin.math.sqrt(hlgVal) * r else a * kotlin.math.ln(hlgVal - b) + c
        return Math.round(ret * 65535f)
    }

    private fun convertGammaToLinearFloat(valInt: Int, min: Float, max: Float): Float {
        if (min == max) return min
        val normalizedVal = valInt.toFloat() / 65535f
        if (disableGammaConversion) return min + (max - min) * normalizedVal
        val r = 0.5f
        val a = 0.17883277f
        val b = 0.28466892f
        val c = 0.55991073f
        val ret =
            if (normalizedVal <= r) (normalizedVal / r) * (normalizedVal / r)
            else kotlin.math.exp((normalizedVal - c) / a) + b
        val normalizedRet = ret.coerceIn(0f, 12f)
        val linear = normalizedRet / 12
        return min + (max - min) * linear
    }

    fun onAttach() {
        repository.attach()
    }

    fun onCleanup() {
        repository.detach()
    }

    override fun onCleared() {
        super.onCleared()
        repository.detach()
    }
}
