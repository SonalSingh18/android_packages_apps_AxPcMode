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

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.axion.axpcmode.R
import com.android.axion.axpcmode.services.CalendarEvent
import com.android.axion.axpcmode.services.CalendarWeatherRepository
import com.android.axion.quicklook.WeatherData
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

/** Footprint of the panel; the overlay window has to be sized around it. */
val CalendarWeatherPanelWidth = 280.dp

private val DAY_LABELS = listOf("S", "M", "T", "W", "T", "F", "S")
private val MONTH_FORMATTER = DateTimeFormatter.ofPattern("MMMM yyyy")
private val TIME_FORMAT = SimpleDateFormat("h:mm a", Locale.getDefault())

@Composable
fun CalendarWeatherPanel(
    repository: CalendarWeatherRepository,
    modifier: Modifier = Modifier,
) {
    val weather by repository.weather.collectAsState()
    val weatherIcon by repository.weatherIcon.collectAsState()
    val todayEvents by repository.todayEvents.collectAsState()

    var displayedMonth by remember { mutableStateOf(YearMonth.now()) }
    val today = remember { LocalDate.now() }

    Surface(
        modifier = modifier.width(CalendarWeatherPanelWidth),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 8.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            weather?.let { w ->
                WeatherRow(w, weatherIcon)
                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { displayedMonth = displayedMonth.minusMonths(1) },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        Icons.Filled.KeyboardArrowLeft,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Text(
                    text = displayedMonth.format(MONTH_FORMATTER),
                    style = MaterialTheme.typography.titleSmall,
                )
                IconButton(
                    onClick = { displayedMonth = displayedMonth.plusMonths(1) },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        Icons.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                DAY_LABELS.forEach { label ->
                    Text(
                        text = label,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            CalendarGrid(displayedMonth, today)

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

            Text(
                text = stringResource(R.string.calendar_today),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(6.dp))

            if (todayEvents.isEmpty()) {
                Text(
                    text = stringResource(R.string.calendar_no_events),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    todayEvents.take(4).forEach { event ->
                        EventRow(event)
                    }
                }
            }
        }
    }
}

@Composable
private fun WeatherRow(weather: WeatherData, iconBitmap: android.graphics.Bitmap?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val image = remember(iconBitmap) { iconBitmap?.asImageBitmap() }
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${weather.temp}${weather.tempUnit ?: "\u00b0C"}",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = listOfNotNull(
                    weather.condition.takeIf { it.isNotBlank() },
                    weather.city?.takeIf { it.isNotBlank() },
                ).joinToString(" • "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CalendarGrid(displayedMonth: YearMonth, today: LocalDate) {
    val firstDay = displayedMonth.atDay(1)
    val firstDayOfWeek = if (firstDay.dayOfWeek.value == 7) 0 else firstDay.dayOfWeek.value
    val daysInMonth = displayedMonth.lengthOfMonth()
    val rows = (firstDayOfWeek + daysInMonth + 6) / 7

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(rows) { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                repeat(7) { col ->
                    val day = row * 7 + col - firstDayOfWeek + 1
                    val isValidDay = day in 1..daysInMonth
                    val isToday = isValidDay && displayedMonth.atDay(day) == today

                    Box(
                        modifier = Modifier.weight(1f).height(28.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isValidDay) {
                            if (isToday) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = day.toString(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                    )
                                }
                            } else {
                                Text(
                                    text = day.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EventRow(event: CalendarEvent) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    if (event.color != 0) Color(event.color)
                    else MaterialTheme.colorScheme.primary
                ),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = event.title,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!event.allDay) {
                Text(
                    text = TIME_FORMAT.format(Date(event.startMs)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
