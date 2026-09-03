package com.android.axion.axpcmode.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.axion.axpcmode.services.MediaRepository
import com.android.axion.axpcmode.services.MediaSessionData

/** Footprint of the card; the overlay window is sized around it plus its padding. */
val MediaPlayerCardWidth = 320.dp

@Composable
fun MediaPlayerCard(mediaRepository: MediaRepository, modifier: Modifier = Modifier) {
    val activeSession by mediaRepository.activeSession.collectAsState()

    Surface(
        modifier = modifier.width(MediaPlayerCardWidth).height(120.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 8.dp,
    ) {
        if (activeSession == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No Active Media",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            MediaContent(activeSession!!, mediaRepository)
        }
    }
}

@Composable
private fun MediaContent(session: MediaSessionData, mediaRepository: MediaRepository) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (session.albumArt != null) {
            Image(
                bitmap = session.albumArt.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.2f,
            )
        } else {
            Box(
                modifier =
                    Modifier.fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                    MaterialTheme.colorScheme.surface,
                                )
                            )
                        )
            )
        }

        Row(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                modifier = Modifier.size(80.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 2.dp,
            ) {
                if (session.albumArt != null) {
                    Image(
                        bitmap = session.albumArt.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(
                    text = session.title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = session.artist,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = { mediaRepository.skipToPrevious() },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(Icons.Rounded.SkipPrevious, "Previous", Modifier.size(24.dp))
                    }

                    Surface(
                        onClick = { mediaRepository.togglePlayPause() },
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                        color = session.primaryColor ?: MaterialTheme.colorScheme.primary,
                        contentColor =
                            if (session.primaryColor != null) Color.White
                            else MaterialTheme.colorScheme.onPrimary,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (session.isPlaying) Icons.Rounded.Pause
                                else Icons.Rounded.PlayArrow,
                                "Play/Pause",
                                Modifier.size(24.dp),
                            )
                        }
                    }

                    IconButton(
                        onClick = { mediaRepository.skipToNext() },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(Icons.Rounded.SkipNext, "Next", Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}
