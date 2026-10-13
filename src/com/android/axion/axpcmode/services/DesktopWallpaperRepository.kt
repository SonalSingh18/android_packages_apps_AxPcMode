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

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.android.axion.axpcmode.R
import com.android.axion.axpcmode.di.IoScope
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val Context.pcWallpaperDataStore by preferencesDataStore(name = "pc_wallpaper")

@Singleton
class DesktopWallpaperRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoScope private val ioScope: CoroutineScope,
) {
    companion object {
        private const val TAG = "DesktopWallpaperRepo"
        private const val PC_WALLPAPER_FILE = "pc_wallpaper.jpg"
        private const val KEY_SCALE_MODE = "wallpaper_scale_mode"
    }

    enum class ScaleMode { FILL, FIT, STRETCH, CENTER }

    private val dataStore: DataStore<Preferences> = context.pcWallpaperDataStore
    private val _wallpaperBitmap = MutableStateFlow<Bitmap?>(null)
    private val _scaleMode = MutableStateFlow(ScaleMode.FILL)
    private val _hasCustomWallpaper = MutableStateFlow(false)

    val wallpaperBitmap: StateFlow<Bitmap?> = _wallpaperBitmap.asStateFlow()
    val scaleMode: StateFlow<ScaleMode> = _scaleMode.asStateFlow()
    val hasCustomWallpaper: StateFlow<Boolean> = _hasCustomWallpaper.asStateFlow()

    init {
        ioScope.launch {
            dataStore.data.first().let { prefs ->
                _scaleMode.value = parseScaleMode(
                    prefs[stringPreferencesKey(KEY_SCALE_MODE)]
                )
            }
            loadWallpaper()
        }
    }

    private fun parseScaleMode(value: String?): ScaleMode = when (value) {
        ScaleMode.FIT.name -> ScaleMode.FIT
        ScaleMode.STRETCH.name -> ScaleMode.STRETCH
        ScaleMode.CENTER.name -> ScaleMode.CENTER
        ScaleMode.FILL.name,
        "CENTER_CROP",
        null -> ScaleMode.FILL
        else -> ScaleMode.FILL
    }

    private fun loadWallpaper() {
        if (File(context.filesDir, PC_WALLPAPER_FILE).exists()) {
            try {
                BitmapFactory.decodeFile(
                    File(context.filesDir, PC_WALLPAPER_FILE).absolutePath
                )?.let { bitmap ->
                    _wallpaperBitmap.value = bitmap
                    _hasCustomWallpaper.value = true
                    return
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load custom wallpaper", e)
            }
        }
        loadDefaultWallpaper()
    }

    private fun loadDefaultWallpaper() {
        try {
            BitmapFactory.decodeResource(
                context.resources,
                R.drawable.pc_default_wallpaper,
                BitmapFactory.Options().apply { inScaled = false },
            )?.let { bitmap ->
                _wallpaperBitmap.value = bitmap
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load default wallpaper", e)
        }
        _hasCustomWallpaper.value = false
    }

    fun setWallpaper(uri: Uri) {
        ioScope.launch {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.readBytes().run {
                        BitmapFactory.decodeByteArray(this, 0, size)?.let { bitmap ->
                            File(context.filesDir, PC_WALLPAPER_FILE).outputStream().use { out ->
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                            }
                            _wallpaperBitmap.value = bitmap
                            _hasCustomWallpaper.value = true
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set wallpaper", e)
            }
        }
    }

    fun removeWallpaper() {
        ioScope.launch {
            try {
                File(context.filesDir, PC_WALLPAPER_FILE).delete()
                loadDefaultWallpaper()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove wallpaper", e)
            }
        }
    }

    fun setScaleMode(mode: ScaleMode) {
        _scaleMode.value = mode
        ioScope.launch {
            dataStore.edit { prefs ->
                prefs[stringPreferencesKey(KEY_SCALE_MODE)] = mode.name
            }
        }
    }
}
