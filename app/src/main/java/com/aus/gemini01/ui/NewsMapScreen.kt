package com.aus.gemini01.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aus.gemini01.BuildConfig
import com.aus.gemini01.ui.components.EmptyStateView
import com.aus.gemini01.ui.theme.Dimens
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsMapScreen(
    locations: List<NewsLocation>,
    onBack: () -> Unit,
    onArticleClick: (String) -> Unit
) {
    val initialPos = if (locations.isNotEmpty()) {
        LatLng(locations[0].latitude, locations[0].longitude)
    } else {
        LatLng(20.0, 0.0)
    }
    
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(initialPos, 2f)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceM)
                    ) {
                        Text(
                            text = if (BuildConfig.MAPS_API_KEY_CONFIGURED) "News Map" else "News Locations",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (locations.isNotEmpty()) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "${locations.size} locations",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = Dimens.spaceM, vertical = 4.dp),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (locations.isEmpty()) {
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
            ) {
                EmptyStateView(
                    icon = Icons.Default.Public,
                    title = "No Locations Detected",
                    message = "Gemini could not identify specific geographical coordinates from the current headlines."
                )
            }
        } else if (!BuildConfig.MAPS_API_KEY_CONFIGURED) {
            LazyColumn(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .padding(Dimens.spaceM),
                verticalArrangement = Arrangement.spacedBy(Dimens.spaceM)
            ) {
                item {
                    Text(
                        text = "Map view is unavailable in this build. These approximate locations were inferred from the headlines.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(locations) { location ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onArticleClick(location.articleUrl) }
                    ) {
                        Column(
                            modifier = Modifier.padding(Dimens.spaceM),
                            verticalArrangement = Arrangement.spacedBy(Dimens.spaceS)
                        ) {
                            Text(
                                text = location.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = location.articleTitle,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "${location.latitude}, ${location.longitude}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Open article",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
            ) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState
                ) {
                    locations.forEach { location ->
                        Marker(
                            state = rememberUpdatedMarkerState(position = LatLng(location.latitude, location.longitude)),
                            title = location.name,
                            snippet = location.articleTitle,
                            onInfoWindowClick = {
                                onArticleClick(location.articleUrl)
                            }
                        )
                    }
                }
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(Dimens.spaceM),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    tonalElevation = 3.dp
                ) {
                    Text(
                        text = "AI-inferred mentions • approximate locations",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Dimens.spaceM, vertical = Dimens.spaceS)
                    )
                }
            }
        }
    }
}

