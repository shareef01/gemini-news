package com.aus.gemini01.ui

import androidx.compose.foundation.layout.*
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
                            text = "News Map",
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
        } else {
            GoogleMap(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
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
        }
    }
}

