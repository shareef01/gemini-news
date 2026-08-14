package com.aus.gemini01.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
        LatLng(0.0, 0.0)
    }
    
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(initialPos, 2f)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("News Map") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        GoogleMap(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            cameraPositionState = cameraPositionState
        ) {
            locations.forEach { location ->
                Marker(
                    state = rememberMarkerState(position = LatLng(location.latitude, location.longitude)),
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
